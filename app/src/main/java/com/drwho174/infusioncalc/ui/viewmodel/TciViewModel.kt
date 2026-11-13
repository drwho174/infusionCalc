package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal

/**
 * Состояние карточки пропофол-TCI. Все данные пациента — только в памяти сессии,
 * не сохраняются на диск (D-TCI-13, FR-TCI-PAT-04).
 */
class TciViewModel : ViewModel() {
    private val _state = MutableStateFlow(buildTciState(TciForm()))
    val state: StateFlow<TciUiState> = _state.asStateFlow()

    private fun update(transform: (TciForm) -> TciForm) {
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

    fun onNowSecondsChange(seconds: Int) = update { it.copy(nowSeconds = seconds) }

    fun onStageToggle(
        induction: Boolean? = null,
        maintenance: Boolean? = null,
        emergence: Boolean? = null,
    ) = update {
        it.copy(
            inductionDone = induction ?: it.inductionDone,
            maintenanceDone = maintenance ?: it.maintenanceDone,
            emergenceDone = emergence ?: it.emergenceDone,
        )
    }

    /** Смена модели очищает зависящие от модели поля и сбрасывает расчёт/чек-лист (FR-TCI-MDL-04). */
    fun changeModel(model: TciModel) {
        if (model == _state.value.form.model) return
        update {
            it.copy(
                model = model,
                heightText = "",
                ageText = "",
                nowSeconds = TciForm.DEFAULT_NOW_SECONDS,
                inductionDone = false,
                maintenanceDone = false,
                emergenceDone = false,
            )
        }
    }

    /** Смена режима сбрасывает симуляцию/чек-лист (FR-TCI-TGT-03). */
    fun changeMode(mode: TargetMode) {
        if (mode == _state.value.form.mode) return
        update {
            it.copy(
                mode = mode,
                nowSeconds = TciForm.DEFAULT_NOW_SECONDS,
                inductionDone = false,
                maintenanceDone = false,
                emergenceDone = false,
            )
        }
    }
}
