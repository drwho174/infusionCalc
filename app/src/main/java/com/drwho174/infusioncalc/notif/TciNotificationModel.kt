package com.drwho174.infusioncalc.notif

import com.drwho174.infusioncalc.tci.PumpPlan
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciState
import com.drwho174.infusioncalc.ui.viewmodel.TciForm
import com.drwho174.infusioncalc.ui.viewmodel.TciSession
import com.drwho174.infusioncalc.ui.viewmodel.TciSessionStatus
import com.drwho174.infusioncalc.ui.viewmodel.lastStepAt
import java.math.BigDecimal

/**
 * Состояние, из которого foreground-сервис сам считает уведомление каждую секунду.
 * Часы и кривые передаются ссылками в пределах процесса — сериализация не нужна.
 */
data class TciNotificationModel(
    val mode: TargetMode,
    val session: TciSession,
    val plan: PumpPlan,
    val curve: List<SimStep>,
    val form: TciForm,
    val planStartSeconds: Int = 0,
    val planStartState: TciState? = null,
    val pauseCurve: List<SimStep>? = null,
    val manualCurve: List<SimStep>? = null,
)

/** Значения для строки уведомления. */
data class TciNotificationContent(
    val mode: TargetMode,
    val concentrationMcgPerMl: BigDecimal?,
    val currentRateMlPerHour: BigDecimal,
    val nextInSeconds: Int?,
    val nextRateMlPerHour: BigDecimal?,
    val paused: Boolean,
    /** Ступень, время которой наступило, но подтверждения не было. */
    val dueOffsetSeconds: Int?,
    val elapsedSeconds: Int,
)

@Suppress("CyclomaticComplexMethod", "LongMethod")
fun evaluateTciNotification(
    model: TciNotificationModel,
    nowMs: Long,
): TciNotificationContent {
    val session = model.session
    val elapsed = session.elapsedSeconds(nowMs)
    val pausedAt = session.pausedAtEpochMs
    val pauseCurve = model.pauseCurve
    val paused = session.status == TciSessionStatus.PAUSED && pausedAt != null && pauseCurve != null

    val curve: List<SimStep> =
        when {
            paused && pausedAt != null && pauseCurve != null -> {
                val pauseStart = session.elapsedSeconds(pausedAt)
                model.curve.filter { it.timeSeconds < pauseStart } +
                    pauseCurve.filter { it.timeSeconds >= pauseStart }
            }
            session.manual && model.manualCurve != null -> model.manualCurve
            else -> model.curve
        }
    val point = curve.lastStepAt(elapsed) ?: curve.lastOrNull()
    val concentration = if (model.mode == TargetMode.PLASMA) point?.cpMcgPerMl else point?.ceMcgPerMl

    val current = model.plan.steps.lastOrNull { it.offsetSeconds <= elapsed }
    val next = model.plan.steps.firstOrNull { it.offsetSeconds > elapsed }
    val confirmed = current?.offsetSeconds?.let { session.confirmedAt.containsKey(it) } == true
    val manualRate = session.manualRateMlPerHour

    val dueOffset =
        if (paused || session.manual || confirmed) {
            null
        } else {
            current?.offsetSeconds
        }

    val currentRate =
        when {
            paused -> BigDecimal.ZERO
            session.manual && manualRate != null -> manualRate
            current != null -> current.rateMlPerHour
            else -> BigDecimal.ZERO
        }

    return TciNotificationContent(
        mode = model.mode,
        concentrationMcgPerMl = concentration,
        currentRateMlPerHour = currentRate,
        nextInSeconds = if (paused) null else next?.let { (it.offsetSeconds - elapsed).coerceAtLeast(0) },
        nextRateMlPerHour = next?.rateMlPerHour,
        paused = paused,
        dueOffsetSeconds = dueOffset,
        elapsedSeconds = elapsed,
    )
}
