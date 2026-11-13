package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.data.session.TciSessionStore
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.SimStep
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciModel
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
@Suppress("TooManyFunctions")
class TciViewModel(
    private val store: TciSessionStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : ViewModel() {
    private val _state = MutableStateFlow(buildTciState(TciForm()))
    val state: StateFlow<TciUiState> = _state.asStateFlow()

    private var session = TciSession()
    private var manualCurve: List<SimStep>? = null
    private var lastSavedBucket = -1
    private var restoring = true

    init {
        viewModelScope.launch { restore() }
    }

    /** Перерисовка по часам; фон не останавливает сессию. */
    fun refresh() {
        if (restoring || !session.isActive) return
        val computed = _state.value.computed ?: return
        val now = clock()
        _state.value =
            _state.value.copy(
                session = session,
                live = buildLive(computed.plan, _state.value.form, session, now, manualCurve),
            )
        val bucket = session.elapsedSeconds(now) / SAVE_INTERVAL_SECONDS
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
        _state.value =
            _state.value.copy(
                session = session,
                live = buildLive(computed.plan, _state.value.form, session, now, null),
            )
        lastSavedBucket = 0
        persist()
    }

    fun pause() {
        if (session.status != TciSessionStatus.RUNNING) return
        session = session.pause(clock())
        publish()
        persist()
    }

    fun resume() {
        if (session.status != TciSessionStatus.PAUSED) return
        session = session.resume(clock())
        publish()
        persist()
    }

    fun stop() {
        session = TciSession()
        manualCurve = null
        lastSavedBucket = -1
        _state.value = buildTciState(_state.value.form)
        viewModelScope.launch { runCatching { store.clear() } }
    }

    /** Отметка «уставка выставлена на насосе» (D-LIVE-3). */
    fun confirmCurrentRate() {
        val offset = _state.value.live?.stepOffsetSeconds ?: return
        session = session.confirm(offset)
        publish()
        persist()
    }

    /** Ручной режим: врач задаёт скорость сам, прогноз считается от неё (D-LIVE-3). */
    fun setManualRate(raw: String) {
        val rate = raw.toBigDecimalOrNull()?.takeIf { it.signum() > 0 } ?: return
        if (!session.isActive) return
        session = session.setManual(rate, clock())
        manualCurve = buildManualCurve(_state.value, session)
        publish()
        persist()
    }

    // ------------------------------------------------------------------ ввод формы

    private fun update(transform: (TciForm) -> TciForm) {
        if (restoring || session.isActive) return
        _state.value = buildTciState(transform(_state.value.form))
    }

    fun onWeightChange(raw: String) = update { it.copy(weightText = normalizeDecimalInput(raw)) }

    fun onHeightChange(raw: String) = update { it.copy(heightText = normalizeDecimalInput(raw)) }

    fun onAgeChange(raw: String) = update { it.copy(ageText = raw.filter(Char::isDigit)) }

    fun onTargetChange(raw: String) = update { it.copy(targetText = normalizeDecimalInput(raw)) }

    fun onThresholdChange(raw: String) = update { it.copy(wakeThresholdText = normalizeDecimalInput(raw)) }

    fun onBolusRateChange(raw: String) = update { it.copy(bolusRateText = normalizeDecimalInput(raw)) }

    fun onConcentrationSelected(conc: BigDecimal) = update { it.copy(concentrationMgPerMl = conc) }

    fun onSexSelected(sex: Sex) = update { it.copy(sex = sex) }

    fun onStageToggle(
        induction: Boolean? = null,
        maintenance: Boolean? = null,
        emergence: Boolean? = null,
    ) {
        if (restoring) return
        val form =
            _state.value.form.copy(
                inductionDone = induction ?: _state.value.form.inductionDone,
                maintenanceDone = maintenance ?: _state.value.form.maintenanceDone,
                emergenceDone = emergence ?: _state.value.form.emergenceDone,
            )
        val base = buildTciState(form)
        if (session.isActive) {
            val computed = base.computed ?: return
            _state.value =
                base.copy(
                    session = session,
                    live = buildLive(computed.plan, form, session, clock(), manualCurve),
                )
        } else {
            _state.value = base
        }
    }

    /** Смена модели очищает зависящие от модели поля и сбрасывает расчёт/чек-лист (FR-TCI-MDL-04). */
    fun changeModel(model: TciModel) {
        if (model == _state.value.form.model) return
        update {
            it.copy(
                model = model,
                heightText = "",
                ageText = "",
                inductionDone = false,
                maintenanceDone = false,
                emergenceDone = false,
            )
        }
    }

    /** Смена режима сбрасывает расчёт/чек-лист (FR-TCI-TGT-03). */
    fun changeMode(mode: TargetMode) {
        if (mode == _state.value.form.mode) return
        update {
            it.copy(
                mode = mode,
                inductionDone = false,
                maintenanceDone = false,
                emergenceDone = false,
            )
        }
    }

    // ------------------------------------------------------------------ служебное

    private fun publish() {
        val computed = _state.value.computed ?: return
        _state.value =
            _state.value.copy(
                session = session,
                live = buildLive(computed.plan, _state.value.form, session, clock(), manualCurve),
            )
    }

    private suspend fun restore() {
        val snapshot = runCatching { store.load() }.getOrNull()
        restoring = false
        if (snapshot == null) return
        val form = snapshot.toForm()
        val base = buildTciState(form)
        if (base.computed == null) {
            session = TciSession()
            _state.value = base
            return
        }
        session = snapshot.toSession()
        manualCurve = if (session.manual) buildManualCurve(base, session) else null
        val now = clock()
        val computed = base.computed
        _state.value =
            base.copy(
                session = session,
                live = buildLive(computed.plan, form, session, now, manualCurve),
            )
        lastSavedBucket = session.elapsedSeconds(now) / SAVE_INTERVAL_SECONDS
    }

    @Suppress("ReturnCount")
    private fun buildManualCurve(
        base: TciUiState,
        current: TciSession,
    ): List<SimStep>? {
        val plan = base.computed?.plan ?: return null
        val from = current.manualFromSeconds ?: return null
        val rate = current.manualRateMlPerHour ?: return null
        val point = plan.predicted.lastOrNull { it.timeSeconds <= from } ?: plan.predicted.firstOrNull() ?: return null
        val params = parametersOf(base.form) ?: return null
        return TciEngine.simulateConstantRate(
            params = params,
            fromState = point.state,
            fromOffsetSeconds = from,
            rateMlPerHour = rate,
            concentrationMgPerMl = base.form.concentrationMgPerMl,
            horizonSeconds = TciForm.HORIZON_SECONDS,
        )
    }

    private fun persist() {
        val snapshot = _state.value.form.toSnapshot(session)
        viewModelScope.launch { runCatching { store.save(snapshot) } }
    }

    private companion object {
        /** Как часто обновлять снимок, пока сессия идёт. */
        const val SAVE_INTERVAL_SECONDS = 15
    }
}
