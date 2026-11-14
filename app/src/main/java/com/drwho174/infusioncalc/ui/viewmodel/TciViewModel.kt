package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.data.session.TciSessionStore
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.notif.NoopTciNotifier
import com.drwho174.infusioncalc.notif.TciNotificationModel
import com.drwho174.infusioncalc.notif.TciNotifier
import com.drwho174.infusioncalc.tci.PumpPlan
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.tci.TciSimRequest
import com.drwho174.infusioncalc.tci.TciState
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

/**
 * Состояние карточки пропофол-TCI.
 *
 * Сессия идёт по реальным часам (D-LIVE-1): экран лишь перерисовывает состояние,
 * поэтому сворачивание приложения не останавливает инфузию, а снимок сессии
 * переживает убийство процесса (сценарий B). Данные пациента при этом хранятся
 * только локально в снимке и стираются при завершении сессии.
 */
@Suppress("TooManyFunctions", "ReturnCount")
class TciViewModel(
    private val store: TciSessionStore,
    private val weightStore: WeightStore = WeightStore(),
    private val notifier: TciNotifier = NoopTciNotifier,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state =
        MutableStateFlow(
            buildTciState(
                TciForm(
                    weightText =
                        weightStore.weight.value
                            ?.toPlainString()
                            .orEmpty(),
                ),
            ),
        )
    val state: StateFlow<TciUiState> = _state.asStateFlow()

    private var session = TciSession()
    private var manualCurve: List<SimStep>? = null

    /** Кривая выведения от момента паузы (скорость 0). */
    private var pauseCurve: List<SimStep>? = null

    /** Действующий план: с нуля либо пересчитанный от состояния на [planStartSeconds]. */
    private var activePlan: PumpPlan? = null
    private var activeCurve: List<SimStep>? = null
    private var activeTarget: BigDecimal? = null
    private var planStartSeconds = 0
    private var planStartState: TciState? = null
    private var lastSavedBucket = -1
    private var restoring = true

    init {
        viewModelScope.launch { restore() }
    }

    /** Перерисовка по часам; фон не останавливает сессию. */
    fun refresh() {
        if (restoring || !session.isActive) return
        publish()
        val bucket = session.elapsedSeconds(clock()) / SAVE_INTERVAL_SECONDS
        if (bucket != lastSavedBucket) {
            lastSavedBucket = bucket
            persist()
        }
    }

    fun start() {
        val computed = _state.value.computed ?: return
        if (_state.value.blocking != null || session.isActive) return
        val now = clock()
        session = TciSession().start(now)
        manualCurve = null
        activePlan = computed.plan
        activeCurve = computed.plan.predicted
        activeTarget =
            _state.value.form.targetText
                .toBigDecimalOrNull()
        planStartSeconds = 0
        planStartState = null
        _state.value =
            _state.value.copy(
                session = session,
                live = buildLive(computed.plan, _state.value.form, session, now, null, activeCurve),
                disclaimerHidden = _state.value.disclaimerHidden,
            )
        syncNotifier()
        lastSavedBucket = 0
        persist()
    }

    fun pause() {
        if (session.status != TciSessionStatus.RUNNING) return
        val now = clock()
        val current = _state.value
        val state = current.live?.state
        val elapsed = session.elapsedSeconds(now)
        session = session.pause(now)
        pauseCurve = state?.let { simulateHold(current.form, it, elapsed) }
        publish()
        syncNotifier()
        persist()
    }

    fun resume() {
        if (session.status != TciSessionStatus.PAUSED) return
        val now = clock()
        val current = _state.value
        val state = current.live?.state
        val elapsed = session.elapsedSeconds(now)
        session = session.resume()
        pauseCurve = null
        if (state != null) {
            val rate = session.manualRateMlPerHour
            if (session.manual && rate != null) {
                manualCurve = simulateManual(current.form, state, elapsed, rate)
                publish()
            } else {
                // После выведения возвращаемся к цели от фактического состояния.
                applyReplan(state, elapsed, current.form.targetText)
            }
        } else {
            publish()
        }
        syncNotifier()
        persist()
    }

    fun stop() {
        session = TciSession()
        manualCurve = null
        pauseCurve = null
        activePlan = null
        activeCurve = null
        activeTarget = null
        planStartSeconds = 0
        planStartState = null
        lastSavedBucket = -1
        _state.value =
            buildTciState(_state.value.form).copy(
                disclaimerHidden = _state.value.disclaimerHidden,
                preferencesLoaded = _state.value.preferencesLoaded,
            )
        notifier.stop()
        viewModelScope.launch { runCatching { store.clear() } }
    }

    /**
     * Отметка «уставка выставлена на насосе» (D-LIVE-3). Фактическое время подтверждения
     * сдвигает модель: если ступень выставили позже, насос дольше держал прежнюю скорость,
     * и кривая пересчитывается по фактическому расписанию.
     */
    fun confirmCurrentRate() {
        val live = _state.value.live ?: return
        val offset = live.stepOffsetSeconds ?: return
        val now = clock()
        val elapsed = session.elapsedSeconds(now)
        session = session.confirm(offset, elapsed)
        val plan = activePlan
        if (plan != null && !session.manual) {
            val horizon = (elapsed + TciForm.HORIZON_SECONDS).coerceAtMost(TciSession.MAX_SESSION_SECONDS)
            activeCurve =
                simulateSchedule(_state.value.form, plan, session.confirmedAt, horizon) ?: activeCurve
        }
        publish()
        syncNotifier()
        persist()
    }

    /** Ручной режим: врач задаёт скорость сам, прогноз считается от неё (D-LIVE-3). */
    fun setManualRate(raw: String) {
        val rate = raw.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return
        if (!session.isActive) return
        val current = _state.value
        val state = current.live?.state ?: return
        val elapsed = session.elapsedSeconds(clock())
        session = session.setManual(rate, clock())
        manualCurve = simulateManual(current.form, state, elapsed, rate)
        publish()
        syncNotifier()
        persist()
    }

    /** Возврат из ручного режима к автоматической программе от текущего состояния. */
    fun returnToAutomatic() {
        if (!session.manual) return
        val live = _state.value.live ?: return
        applyReplan(live.state ?: return, live.elapsedSeconds, _state.value.form.targetText)
    }

    /** Смена цели во время инфузии: дополнительный болюс (если нужен) и новый план. */
    fun onTargetChange(raw: String) {
        val text = normalizeDecimalInput(raw)
        if (!session.isActive) {
            update { it.copy(targetText = text) }
            return
        }
        val current = _state.value
        _state.value = current.copy(form = current.form.copy(targetText = text))
        val target = text.toBigDecimalOrNull() ?: return
        if (target.signum() <= 0 || target > MAX_TARGET_MCG_ML) return
        // На паузе цель только запоминается: план пересчитается при возобновлении.
        if (session.status == TciSessionStatus.PAUSED) return
        if (activeTarget?.compareTo(target) == 0) return
        applyReplan(current.live?.state ?: return, session.elapsedSeconds(clock()), text)
    }

    /** Скорость болюса задаётся в настройках из appbar. */
    fun onBolusRateChange(raw: String) {
        val text = normalizeDecimalInput(raw)
        val base = _state.value
        val form = base.form.copy(bolusRateText = text)
        _state.value =
            if (session.isActive) {
                base.copy(form = form)
            } else {
                buildTciState(form).copy(
                    disclaimerHidden = base.disclaimerHidden,
                    preferencesLoaded = base.preferencesLoaded,
                )
            }
        text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { value ->
            viewModelScope.launch { runCatching { store.saveBolusRateMlPerHour(value) } }
        }
    }

    /**
     * Настройки TCI из appbar: скорость болюса, шаг таблицы скоростей и запас времени
     * для предупреждения о следующей коррекции (1–15 минут).
     */
    fun applyTciSettings(
        bolusRateRaw: String,
        tableStepMinutes: Int,
        warningLeadMinutes: Int,
    ) {
        val base = _state.value
        val bolusText = normalizeDecimalInput(bolusRateRaw)
        val form =
            base.form.copy(
                bolusRateText = bolusText,
                tableStepMinutes = tableStepMinutes.coerceIn(MIN_STEP, MAX_STEP),
                warningLeadMinutes = warningLeadMinutes.coerceIn(MIN_STEP, MAX_STEP),
            )
        val live = base.live
        when {
            session.status == TciSessionStatus.PAUSED -> _state.value = base.copy(form = form)
            session.isActive && live?.state != null ->
                applyReplan(live.state, live.chartSeconds, form.targetText, form)
            else ->
                _state.value =
                    buildTciState(form).copy(
                        disclaimerHidden = base.disclaimerHidden,
                        preferencesLoaded = base.preferencesLoaded,
                    )
        }
        syncNotifier()
        viewModelScope.launch {
            bolusText.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.let { store.saveBolusRateMlPerHour(it) }
            store.saveTableStepMinutes(form.tableStepMinutes)
            store.saveWarningLeadMinutes(form.warningLeadMinutes)
        }
    }

    /** Повторная синхронизация уведомления (например, после выдачи разрешения). */
    fun refreshNotifications() {
        if (session.isActive) syncNotifier()
    }

    /** «Больше не показывать» для дисклеймера. */
    fun hideDisclaimerForever() {
        _state.value = _state.value.copy(disclaimerHidden = true)
        viewModelScope.launch { runCatching { store.setDisclaimerHidden(true) } }
    }

    /** Вернуть дисклеймер (из настроек). */
    fun showDisclaimerAgain() {
        _state.value = _state.value.copy(disclaimerHidden = false)
        viewModelScope.launch { runCatching { store.setDisclaimerHidden(false) } }
    }

    // ------------------------------------------------------------------ ввод формы

    private fun update(transform: (TciForm) -> TciForm) {
        if (restoring || session.isActive) return
        val base = buildTciState(transform(_state.value.form))
        _state.value =
            base.copy(
                disclaimerHidden = _state.value.disclaimerHidden,
                preferencesLoaded = _state.value.preferencesLoaded,
            )
    }

    fun onWeightChange(raw: String) = update { it.copy(weightText = normalizeDecimalInput(raw)) }

    fun onHeightChange(raw: String) = update { it.copy(heightText = normalizeDecimalInput(raw)) }

    fun onAgeChange(raw: String) = update { it.copy(ageText = raw.filter(Char::isDigit)) }

    fun onThresholdChange(raw: String) = update { it.copy(wakeThresholdText = normalizeDecimalInput(raw)) }

    fun onConcentrationSelected(conc: BigDecimal) = update { it.copy(concentrationMgPerMl = conc) }

    fun onSexSelected(sex: Sex) = update { it.copy(sex = sex) }

    /** Смена модели очищает зависящие от модели поля и сбрасывает расчёт (FR-TCI-MDL-04). */
    fun changeModel(model: TciModel) {
        if (model == _state.value.form.model) return
        update {
            it.copy(
                model = model,
                heightText = "",
                ageText = "",
            )
        }
    }

    /** Смена режима сбрасывает расчёт (FR-TCI-TGT-03). */
    fun changeMode(mode: TargetMode) {
        if (mode == _state.value.form.mode) return
        update { it.copy(mode = mode) }
    }

    // ------------------------------------------------------------------ служебное

    private fun publish() {
        val plan = activePlan ?: _state.value.computed?.plan ?: return
        _state.value =
            _state.value.copy(
                session = session,
                live = buildLive(plan, _state.value.form, session, clock(), manualCurve, activeCurve, pauseCurve),
                disclaimerHidden = _state.value.disclaimerHidden,
            )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun restore() {
        val snapshot = runCatching { store.load() }.getOrNull()
        val hidden = runCatching { store.isDisclaimerHidden() }.getOrDefault(false)
        val storedBolusRate = runCatching { store.loadBolusRateMlPerHour() }.getOrNull()
        val storedStep =
            runCatching { store.loadTableStepMinutes() }
                .getOrNull()
                ?.coerceIn(MIN_STEP, MAX_STEP) ?: DEFAULT_TABLE_STEP_MINUTES
        val storedLead =
            runCatching { store.loadWarningLeadMinutes() }
                .getOrNull()
                ?.coerceIn(MIN_STEP, MAX_STEP) ?: DEFAULT_WARNING_LEAD_MINUTES
        restoring = false

        if (snapshot == null) {
            val form =
                TciForm(
                    bolusRateText = (storedBolusRate ?: DEFAULT_BOLUS_RATE).toPlainString(),
                    weightText =
                        weightStore.weight.value
                            ?.toPlainString()
                            .orEmpty(),
                    tableStepMinutes = storedStep,
                    warningLeadMinutes = storedLead,
                )
            val base = buildTciState(form)
            activePlan = base.computed?.plan
            activeCurve = activePlan?.predicted
            _state.value = base.copy(disclaimerHidden = hidden, preferencesLoaded = true)
            return
        }

        val restoredForm = snapshot.toForm()
        val prefillWeight = restoredForm.weightText.isBlank()
        val form =
            restoredForm.copy(
                weightText =
                    if (prefillWeight) {
                        weightStore.weight.value
                            ?.toPlainString()
                            .orEmpty()
                    } else {
                        restoredForm.weightText
                    },
                tableStepMinutes = storedStep,
                warningLeadMinutes = storedLead,
            )
        val base = buildTciState(form)
        val computed = base.computed
        if (computed == null) {
            session = TciSession()
            _state.value = base.copy(disclaimerHidden = hidden, preferencesLoaded = true)
            return
        }
        session = snapshot.toSession()
        planStartSeconds = snapshot.planStartSeconds
        planStartState = snapshot.planStartState()
        val restoredPlan =
            if (planStartSeconds > 0 && planStartState != null) {
                replanFromState(form, planStartState!!, planStartSeconds)
            } else {
                null
            }
        activePlan = restoredPlan ?: computed.plan
        activeCurve = activePlan?.predicted
        val restoredPlanRef = activePlan
        if (!session.manual && session.confirmedAt.isNotEmpty() && restoredPlanRef != null) {
            val horizon =
                (session.elapsedSeconds(clock()) + TciForm.HORIZON_SECONDS)
                    .coerceAtMost(TciSession.MAX_SESSION_SECONDS)
            activeCurve =
                simulateSchedule(form, restoredPlanRef, session.confirmedAt, horizon) ?: activeCurve
        }
        activeTarget = form.targetText.toBigDecimalOrNull()
        val pausedAt = session.pausedAtEpochMs
        pauseCurve =
            if (session.status == TciSessionStatus.PAUSED && pausedAt != null) {
                val pauseStart = session.elapsedSeconds(pausedAt)
                activeCurve?.lastOrNull { it.timeSeconds <= pauseStart }?.state?.let { state ->
                    simulateHold(form, state, pauseStart)
                }
            } else {
                null
            }
        manualCurve =
            if (session.manual) {
                buildManualCurveFrom(activeCurve, form, session)
            } else {
                null
            }
        val now = clock()
        _state.value =
            base.copy(
                computed = computed.withPlan(activePlan!!),
                session = session,
                live = buildLive(activePlan!!, form, session, now, manualCurve, activeCurve, pauseCurve),
                disclaimerHidden = hidden,
                preferencesLoaded = true,
            )
        syncNotifier()
        lastSavedBucket = session.elapsedSeconds(now) / SAVE_INTERVAL_SECONDS
    }

    /**
     * Пересчитывает действующий план от [state] на [elapsedSeconds] под текущую цель
     * и сохраняет сессию. Используется сменой цели и возвратом из ручного режима.
     */
    private fun applyReplan(
        state: TciState,
        elapsedSeconds: Int,
        targetText: String,
        formOverride: TciForm? = null,
    ) {
        val live = _state.value.live
        val form = (formOverride ?: _state.value.form).copy(targetText = targetText)
        val base = buildTciState(form)
        val computed = base.computed ?: return
        val plan = replanFromState(form, state, elapsedSeconds) ?: return

        session = session.clearManual().copy(confirmedAt = emptyMap())
        manualCurve = null
        activePlan = plan
        activeCurve = mergeCurve(live?.curve ?: computed.plan.predicted, plan.predicted, elapsedSeconds)
        activeTarget = targetText.toBigDecimalOrNull()
        planStartSeconds = elapsedSeconds
        planStartState = state
        _state.value =
            base.copy(
                computed = computed.withPlan(plan),
                session = session,
                live = buildLive(plan, form, session, clock(), null, activeCurve, pauseCurve),
                disclaimerHidden = _state.value.disclaimerHidden,
                preferencesLoaded = _state.value.preferencesLoaded,
            )
        syncNotifier()
        persist()
    }

    private fun replanFromState(
        form: TciForm,
        state: TciState,
        startSeconds: Int,
    ): PumpPlan? {
        val params = parametersOf(form) ?: return null
        val request = requestOf(form) ?: return null
        return runCatching {
            TciEngine.pumpPlanFromState(
                request = request,
                params = params,
                state = state,
                startSeconds = startSeconds,
                stepSeconds = form.tableStepMinutes.coerceIn(MIN_STEP, MAX_STEP) * 60,
            )
        }.getOrNull()
    }

    private fun simulateManual(
        form: TciForm,
        state: TciState,
        elapsedSeconds: Int,
        rate: BigDecimal,
    ): List<SimStep>? = simulateConstant(form, state, elapsedSeconds, rate, TciForm.HORIZON_SECONDS)

    /** Кривая выведения при паузе: насос отдаёт 0 мл/ч. */
    private fun simulateHold(
        form: TciForm,
        state: TciState,
        elapsedSeconds: Int,
    ): List<SimStep>? = simulateConstant(form, state, elapsedSeconds, BigDecimal.ZERO, PAUSE_HORIZON_SECONDS)

    private fun simulateConstant(
        form: TciForm,
        state: TciState,
        elapsedSeconds: Int,
        rate: BigDecimal,
        horizonSeconds: Int,
    ): List<SimStep>? {
        val params = parametersOf(form) ?: return null
        return runCatching {
            TciEngine.simulateConstantRate(
                params = params,
                fromState = state,
                fromOffsetSeconds = elapsedSeconds,
                rateMlPerHour = rate,
                concentrationMgPerMl = form.concentrationMgPerMl,
                horizonSeconds = horizonSeconds,
            )
        }.getOrNull()
    }

    /**
     * Пересчитывает кривую по фактическому расписанию: ступень применяется в момент
     * подтверждения, а если его не было — по плановому времени. Так опоздание
     * подтверждения сдвигает модель, а не игнорируется.
     */
    @Suppress("CyclomaticComplexMethod")
    private fun simulateSchedule(
        form: TciForm,
        plan: PumpPlan,
        confirmedAt: Map<Int, Int>,
        horizonSeconds: Int,
    ): List<SimStep>? {
        val params = parametersOf(form) ?: return null
        val changes = ArrayList<Pair<Int, BigDecimal>>()
        changes.add(0 to BigDecimal.ZERO)
        var last = 0
        for (step in plan.steps) {
            val at = (confirmedAt[step.offsetSeconds] ?: step.offsetSeconds).coerceAtLeast(last)
            if (at >= horizonSeconds) break
            last = at
            if (changes.last().first == at) {
                changes[changes.lastIndex] = at to step.rateMlPerHour
            } else {
                changes.add(at to step.rateMlPerHour)
            }
        }

        var state = TciState(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        var t = 0
        var index = 0
        val curve = ArrayList<SimStep>()
        while (t < horizonSeconds) {
            while (index + 1 < changes.size && changes[index + 1].first <= t) index++
            val rate = changes[index].second
            val end = minOf(changes.getOrNull(index + 1)?.first ?: horizonSeconds, horizonSeconds)
            if (end <= t) {
                index++
                continue
            }
            val segment =
                TciEngine.simulateConstantRate(
                    params = params,
                    fromState = state,
                    fromOffsetSeconds = t,
                    rateMlPerHour = rate,
                    concentrationMgPerMl = form.concentrationMgPerMl,
                    horizonSeconds = end - t,
                )
            for (step in segment) {
                if (curve.isEmpty() || step.timeSeconds > curve.last().timeSeconds) curve.add(step)
            }
            state = segment.last().state
            t = end
            index++
        }
        return curve
    }

    private fun buildManualCurveFrom(
        curve: List<SimStep>?,
        form: TciForm,
        current: TciSession,
    ): List<SimStep>? {
        val from = current.manualFromSeconds ?: return null
        val rate = current.manualRateMlPerHour ?: return null
        val point = curve?.lastOrNull { it.timeSeconds <= from } ?: curve?.firstOrNull() ?: return null
        return simulateManual(form, point.state, from, rate)
    }

    private fun mergeCurve(
        prefix: List<SimStep>?,
        predicted: List<SimStep>,
        fromSeconds: Int,
    ): List<SimStep> {
        val history = prefix?.filter { it.timeSeconds < fromSeconds } ?: emptyList()
        return history + predicted.filter { it.timeSeconds >= fromSeconds }
    }

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

    /** Передаёт сервису уведомления актуальную модель (часы и кривые живут в ней). */
    private fun syncNotifier() {
        if (!session.isActive) {
            notifier.stop()
            return
        }
        val plan = activePlan ?: return
        notifier.update(
            TciNotificationModel(
                mode = _state.value.form.mode,
                session = session,
                plan = plan,
                curve = activeCurve ?: plan.predicted,
                pauseCurve = pauseCurve,
                manualCurve = manualCurve,
            ),
        )
    }

    private fun persist() {
        val snapshot = _state.value.form.toSnapshot(session, planStartSeconds, planStartState)
        viewModelScope.launch { runCatching { store.save(snapshot) } }
    }

    private companion object {
        /** Как часто обновлять снимок, пока сессия идёт. */
        const val SAVE_INTERVAL_SECONDS = 15

        /** Горизонт кривой выведения при паузе: 4 часа. */
        const val PAUSE_HORIZON_SECONDS = 4 * 3600

        val DEFAULT_BOLUS_RATE = BigDecimal("1200")
        val MAX_TARGET_MCG_ML = BigDecimal("20")
    }
}
