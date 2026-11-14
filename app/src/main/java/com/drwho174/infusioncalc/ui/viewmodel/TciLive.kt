package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.tci.PumpPlan
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciResult
import com.drwho174.infusioncalc.tci.TciState
import com.drwho174.infusioncalc.ui.display
import java.math.BigDecimal

/** Фаза, которую переживает инфузия прямо сейчас. */
enum class TciPhase { IDLE, BOLUS, MAINTENANCE }

/**
 * Живое состояние сессии: время, текущая уставка насоса, следующие действия
 * и прогноз Cp/Ce на текущий момент (D-LIVE-1…D-LIVE-4).
 */
data class TciLive(
    val elapsedSeconds: Int,
    /** Время для графика и курсора (при паузе продолжает идти). */
    val chartSeconds: Int,
    val phase: TciPhase,
    val currentRateMlPerHour: BigDecimal?,
    val stepOffsetSeconds: Int?,
    val nextChangeInSeconds: Int?,
    val nextRateMlPerHour: BigDecimal?,
    /** Следующая коррекция уже близко — пора готовиться. */
    val warningSoon: Boolean,
    val confirmed: Boolean,
    val overdue: Boolean,
    val manual: Boolean,
    /** Пауза: насос отдаёт 0 мл/ч, концентрации выводятся в реальном времени. */
    val paused: Boolean,
    val cpDisplay: String,
    val ceDisplay: String,
    val wakeDisplay: String?,
    /** Секунды до пробуждения; при паузе — обратный отсчёт от момента паузы. */
    val wakeRemainingSeconds: Int?,
    val curve: List<SimStep>,
    val state: TciState?,
)

/** Порог, после которого неподтверждённая уставка считается просроченной. */
internal const val OVERDUE_SECONDS = 60

private const val SECONDS_PER_MINUTE = 60

/**
 * Собирает живое состояние из плана, формы и сессии. Чистая функция:
 * время передаётся снаружи, поэтому её легко проверять тестами.
 */
@Suppress("CyclomaticComplexMethod", "LongParameterList", "LongMethod")
internal fun buildLive(
    plan: PumpPlan,
    form: TciForm,
    session: TciSession,
    nowMs: Long,
    manualCurve: List<SimStep>?,
    sessionCurve: List<SimStep>? = null,
    /** Кривая выведения от момента паузы (скорость 0), абсолютные времена. */
    pauseCurve: List<SimStep>? = null,
): TciLive? {
    if (!session.isActive) return null
    val elapsed = session.elapsedSeconds(nowMs)
    val pausedAt = session.pausedAtEpochMs
    val paused = session.status == TciSessionStatus.PAUSED && pausedAt != null && pauseCurve != null
    val manualFrom = session.manualFromSeconds
    val manualRate = session.manualRateMlPerHour
    val manualActive =
        !paused &&
            manualRate != null &&
            manualFrom != null &&
            elapsed >= manualFrom &&
            manualCurve != null

    val baseCurve: List<SimStep> =
        if (manualActive && manualCurve != null) {
            manualCurve
        } else {
            sessionCurve ?: plan.predicted
        }
    // На паузе насос отдаёт 0: к кривой до момента паузы добавляется живое выведение.
    val curve: List<SimStep> =
        if (paused && pausedAt != null && pauseCurve != null) {
            val pauseStart = session.elapsedSeconds(pausedAt)
            baseCurve.filter { it.timeSeconds < pauseStart } +
                pauseCurve.filter { it.timeSeconds >= pauseStart }
        } else {
            baseCurve
        }
    val point = curve.lastOrNull { it.timeSeconds <= elapsed } ?: curve.lastOrNull()
    val state = point?.state

    val advice =
        when {
            paused ->
                Advice(
                    phase = TciPhase.MAINTENANCE,
                    rate = BigDecimal.ZERO,
                    offset = null,
                    nextIn = null,
                    nextRate = null,
                    confirmed = true,
                    overdue = false,
                )
            manualActive ->
                Advice(
                    phase = TciPhase.MAINTENANCE,
                    rate = manualRate,
                    offset = manualFrom,
                    nextIn = null,
                    nextRate = null,
                    confirmed = true,
                    overdue = false,
                )
            else -> planAdvice(plan, session, elapsed)
        }

    val wakeSeconds =
        state?.let { current ->
            val params = parametersOf(form) ?: return@let null
            val threshold = form.wakeThresholdText.toBigDecimalOrNull() ?: BigDecimal("1.5")
            TciEngine.decrementTimeSeconds(
                params = params,
                state = current,
                mode = form.mode,
                wakeThresholdMcgPerMl = threshold,
                stepSeconds = 5,
            )
        }

    val warningSoon =
        !paused &&
            !manualActive &&
            advice.nextIn != null &&
            advice.nextIn <= form.warningLeadMinutes.coerceIn(MIN_STEP, MAX_STEP) * SECONDS_PER_MINUTE

    return TciLive(
        elapsedSeconds = elapsed,
        chartSeconds = elapsed,
        phase = advice.phase,
        currentRateMlPerHour = advice.rate?.let { TciEngine.formatRateMlPerHour(it) },
        stepOffsetSeconds = advice.offset,
        nextChangeInSeconds = advice.nextIn,
        nextRateMlPerHour = advice.nextRate?.let { TciEngine.formatRateMlPerHour(it) },
        warningSoon = warningSoon,
        confirmed = advice.confirmed,
        overdue = advice.overdue,
        manual = manualActive,
        paused = paused,
        cpDisplay = point?.cpMcgPerMl?.let { TciEngine.formatConcentrationMcgMl(it).display() } ?: "—",
        ceDisplay = point?.ceMcgPerMl?.let { TciEngine.formatConcentrationMcgMl(it).display() } ?: "—",
        wakeDisplay = formatWakeTime(wakeSeconds),
        wakeRemainingSeconds = wakeSeconds,
        curve = curve,
        state = state,
    )
}

private data class Advice(
    val phase: TciPhase,
    val rate: BigDecimal?,
    val offset: Int?,
    val nextIn: Int?,
    val nextRate: BigDecimal?,
    val confirmed: Boolean,
    val overdue: Boolean,
)

private fun planAdvice(
    plan: PumpPlan,
    session: TciSession,
    elapsed: Int,
): Advice {
    val current = plan.steps.lastOrNull { it.offsetSeconds <= elapsed } ?: plan.steps.first()
    val next = plan.steps.firstOrNull { it.offsetSeconds > elapsed }
    val confirmed = session.confirmedAt.containsKey(current.offsetSeconds)
    return Advice(
        phase = if (current.bolus) TciPhase.BOLUS else TciPhase.MAINTENANCE,
        rate = current.rateMlPerHour,
        offset = current.offsetSeconds,
        nextIn = next?.let { (it.offsetSeconds - elapsed).coerceAtLeast(0) },
        nextRate = next?.rateMlPerHour,
        confirmed = confirmed,
        overdue = !confirmed && elapsed - current.offsetSeconds > OVERDUE_SECONDS,
    )
}

internal fun parametersOf(form: TciForm): com.drwho174.infusioncalc.tci.TciParameters? {
    val weight = form.weightText.toBigDecimalOrNull() ?: return null
    val height = form.heightText.toBigDecimalOrNull()
    val age = form.ageText.trim().toIntOrNull()
    return when (
        val result =
            TciEngine.parameters(
                model = form.model,
                weightKg = weight,
                heightCm = height,
                ageYears = age,
                sex = form.sex,
            )
    ) {
        is TciResult.Success -> result.value
        is TciResult.Failure -> null
    }
}
