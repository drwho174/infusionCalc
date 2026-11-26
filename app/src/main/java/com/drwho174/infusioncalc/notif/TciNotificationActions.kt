package com.drwho174.infusioncalc.notif

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drwho174.infusioncalc.data.session.DataStoreTciSessionStore
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciSimRequest
import com.drwho174.infusioncalc.tci.TciState
import com.drwho174.infusioncalc.ui.viewmodel.TciForm
import com.drwho174.infusioncalc.ui.viewmodel.TciSchedule
import com.drwho174.infusioncalc.ui.viewmodel.TciSession
import com.drwho174.infusioncalc.ui.viewmodel.TciSessionStatus
import com.drwho174.infusioncalc.ui.viewmodel.lastStepAt
import com.drwho174.infusioncalc.ui.viewmodel.parametersOf
import com.drwho174.infusioncalc.ui.viewmodel.toSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

/**
 * Действия из уведомления: пауза и подтверждение уставки. Выполняются в сервисе,
 * пишут снимок сессии и сдвигают revision, чтобы открытый экран подхватил изменения.
 */
object TciNotificationActions {
    const val ACTION_PAUSE = "com.drwho174.infusioncalc.action.TCI_PAUSE"
    const val ACTION_RESUME = "com.drwho174.infusioncalc.action.TCI_RESUME"
    const val ACTION_CONFIRM = "com.drwho174.infusioncalc.action.TCI_CONFIRM"
    const val ACTION_STOP = "com.drwho174.infusioncalc.action.TCI_STOP"

    private const val PAUSE_HORIZON_SECONDS = 4 * 3600
    private const val MIN_STEP_MINUTES = 1
    private const val MAX_STEP_MINUTES = 60
    private const val SECONDS_PER_MINUTE = 60
    private val ZERO_STATE = TciState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
    private val mutex = Mutex()

    suspend fun handle(
        context: Context,
        action: String,
    ) {
        mutex.withLock {
            if (action == ACTION_STOP) {
                stop(context)
                return
            }
            val model = TciNotificationBus.model ?: return
            when (action) {
                ACTION_PAUSE -> pause(context, model)
                ACTION_CONFIRM -> confirm(context, model)
            }
        }
    }

    private suspend fun pause(
        context: Context,
        model: TciNotificationModel,
    ) {
        if (model.session.status == com.drwho174.infusioncalc.ui.viewmodel.TciSessionStatus.PAUSED) return
        val now = System.currentTimeMillis()
        val elapsed = model.session.elapsedSeconds(now)
        val state = model.curve.lastStepAt(elapsed)?.state ?: return
        val paused = model.session.pause(now)
        val pauseCurve = simulateHold(model.form, state, elapsed)
        commit(context, model.copy(session = paused, pauseCurve = pauseCurve))
    }

    /**
     * Возобновление из уведомления. Сначала берём фактическое состояние в момент
     * паузы, затем повторяем логику экрана: для ручного режима держим скорость,
     * для автоматического — пересчитываем программу от текущей концентрации.
     */
    @Suppress("ReturnCount")
    private suspend fun resume(
        context: Context,
        model: TciNotificationModel,
    ) {
        if (model.session.status != TciSessionStatus.PAUSED) return
        val now = System.currentTimeMillis()
        val elapsed = model.session.elapsedSeconds(now)
        val state = model.pauseCurve?.lastStepAt(elapsed)?.state ?: return
        val resumed = model.session.resume()
        val base = model.copy(session = resumed, pauseCurve = null)
        val rate = resumed.manualRateMlPerHour
        if (resumed.manual && rate != null) {
            val params = parametersOf(model.form)
            val manualCurve =
                params?.let {
                    TciEngine.simulateConstantRate(
                        params = it,
                        fromState = state,
                        fromOffsetSeconds = elapsed,
                        rateMlPerHour = rate,
                        concentrationMgPerMl = model.form.concentrationMgPerMl,
                        horizonSeconds = PAUSE_HORIZON_SECONDS,
                    )
                }
            commit(context, base.copy(manualCurve = manualCurve))
            return
        }

        val params = parametersOf(model.form)
        val request = requestOf(model.form)
        if (params == null || request == null) {
            commit(context, base)
            return
        }
        val plan =
            runCatching {
                TciEngine.pumpPlanFromState(
                    request = request,
                    params = params,
                    state = state,
                    startSeconds = elapsed,
                    stepSeconds =
                        model.form.tableStepMinutes
                            .coerceIn(MIN_STEP_MINUTES, MAX_STEP_MINUTES) * SECONDS_PER_MINUTE,
                )
            }.getOrNull()
        if (plan == null) {
            commit(context, base)
            return
        }
        val curve = mergeCurve(model.curve, plan.predicted, elapsed)
        commit(
            context,
            base.copy(
                session = resumed.clearManual().copy(confirmedAt = emptyMap()),
                plan = plan,
                curve = curve,
                manualCurve = null,
                planStartSeconds = elapsed,
                planStartState = state,
            ),
        )
    }

    @Suppress("ReturnCount")
    private suspend fun confirm(
        context: Context,
        model: TciNotificationModel,
    ) {
        if (model.session.status == com.drwho174.infusioncalc.ui.viewmodel.TciSessionStatus.PAUSED) return
        if (model.session.manual) return
        val now = System.currentTimeMillis()
        val elapsed = model.session.elapsedSeconds(now)
        val current = model.plan.steps.lastOrNull { it.offsetSeconds <= elapsed } ?: return
        if (model.session.confirmedAt.containsKey(current.offsetSeconds)) return

        val session = model.session.confirm(current.offsetSeconds, elapsed)
        val horizon = (elapsed + TciForm.HORIZON_SECONDS).coerceAtMost(TciSession.MAX_SESSION_SECONDS)
        val scheduled =
            TciSchedule.simulate(
                form = model.form,
                plan = model.plan,
                confirmedAt = session.confirmedAt,
                startSeconds = model.planStartSeconds,
                initialState = model.planStartState ?: ZERO_STATE,
                horizonSeconds = horizon,
            )
        val curve = scheduled?.let { mergeCurve(model.curve, it, model.planStartSeconds) } ?: model.curve
        commit(context, model.copy(session = session, curve = curve))
    }

    private suspend fun stop(context: Context) {
        TciNotificationBus.model = null
        TciNotificationBus.revision++
        runCatching { DataStoreTciSessionStore(context.applicationContext).clear() }
        runCatching { context.stopService(Intent(context, TciSessionService::class.java)) }
        runCatching {
            NotificationManagerCompat.from(context).cancel(TciSessionService.NOTIFICATION_ID)
        }
    }

    private suspend fun commit(
        context: Context,
        model: TciNotificationModel,
    ) {
        TciNotificationBus.model = model
        TciNotificationBus.revision++
        runCatching {
            DataStoreTciSessionStore(context.applicationContext).save(
                model.form.toSnapshot(model.session, model.planStartSeconds, model.planStartState),
            )
        }
        runCatching {
            ContextCompat.startForegroundService(context, Intent(context, TciSessionService::class.java))
        }
    }

    @Suppress("ReturnCount")
    private fun requestOf(form: TciForm): TciSimRequest? {
        val weight = form.weightText.toBigDecimalOrNull() ?: return null
        val target = form.targetText.toBigDecimalOrNull() ?: return null
        val bolusRate = form.bolusRateText.toBigDecimalOrNull() ?: return null
        return TciSimRequest(
            model = form.model,
            targetMcgPerMl = target,
            mode = form.mode,
            concentrationMgPerMl = form.concentrationMgPerMl,
            bolusRateMlPerHour = bolusRate,
            weightKg = weight,
            heightCm = form.heightText.toBigDecimalOrNull(),
            ageYears = form.ageText.trim().toIntOrNull(),
            sex = form.sex,
            horizonSeconds = TciForm.HORIZON_SECONDS,
        )
    }

    private fun simulateHold(
        form: TciForm,
        state: TciState,
        elapsedSeconds: Int,
    ): List<SimStep>? {
        val params = parametersOf(form) ?: return null
        return TciEngine.simulateConstantRate(
            params = params,
            fromState = state,
            fromOffsetSeconds = elapsedSeconds,
            rateMlPerHour = BigDecimal.ZERO,
            concentrationMgPerMl = form.concentrationMgPerMl,
            horizonSeconds = PAUSE_HORIZON_SECONDS,
        )
    }

    private fun mergeCurve(
        prefix: List<SimStep>?,
        predicted: List<SimStep>,
        fromSeconds: Int,
    ): List<SimStep> {
        val history = prefix?.filter { it.timeSeconds < fromSeconds } ?: emptyList()
        return history + predicted.filter { it.timeSeconds >= fromSeconds }
    }
}
