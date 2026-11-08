package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.convertAmountUnit
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.engine.CalcResult
import com.drwho174.infusioncalc.engine.InfusionEngine
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.math.BigDecimal

enum class InputSource { DOSE, RATE }

data class CalculatorUiState(
    val isLoaded: Boolean = false,
    val drug: Drug? = null,
    val scheme: DoseScheme? = null,
    val preset: ConcentrationPreset? = null,
    val weight: BigDecimal? = null,
    val source: InputSource = InputSource.DOSE,
    val concentration: BigDecimal? = null,
    val unitMismatch: Boolean = false,
    val displayDose: String = "",
    val displayRate: String = "",
    val resultValue: BigDecimal? = null,
    val resultIsRate: Boolean = false,
    val aboveRange: Boolean = false,
    val belowRange: Boolean = false,
)

private data class CalculatorForm(
    val doseText: String = "",
    val rateText: String = "",
    val source: InputSource = InputSource.DOSE,
    val presetId: String? = null,
)

/** Состояние и расчёт экрана скорости инфузии. */
class CalculatorViewModel(
    private val drugId: String,
    private val repository: DrugRepository,
    private val weightStore: WeightStore,
) : ViewModel() {
    private val form = MutableStateFlow(CalculatorForm())

    val state: StateFlow<CalculatorUiState> =
        combine(
            repository.observeDrug(drugId),
            weightStore.weight,
            form,
        ) { drug, weight, formState -> buildState(drug, weight, formState) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalculatorUiState())

    fun onDoseChange(raw: String) {
        form.update { it.copy(source = InputSource.DOSE, doseText = normalizeDecimalInput(raw)) }
    }

    fun onRateChange(raw: String) {
        form.update { it.copy(source = InputSource.RATE, rateText = normalizeDecimalInput(raw)) }
    }

    fun onPresetSelected(preset: ConcentrationPreset) {
        form.update { it.copy(presetId = preset.id) }
    }

    private fun buildState(
        drug: Drug?,
        weight: BigDecimal?,
        formState: CalculatorForm,
    ): CalculatorUiState {
        if (drug == null) return CalculatorUiState(isLoaded = true)

        val scheme = drug.defaultScheme
        val preset = drug.presets.firstOrNull { it.id == formState.presetId } ?: drug.defaultPreset
        val schemeUnit = scheme?.amountUnit
        val concentration = preset?.concentrationIn(schemeUnit)
        val unitMismatch =
            preset?.amountUnit?.let { presetUnit ->
                schemeUnit?.let { convertAmountUnit(BigDecimal.ONE, presetUnit, it) == null }
            } ?: false
        val engineScheme = scheme?.toEngine()

        val computed: Pair<Boolean, BigDecimal>? =
            if (concentration == null || engineScheme == null) {
                null
            } else if (formState.source == InputSource.DOSE) {
                formState.doseText.toBigDecimalOrNull()?.let { dose ->
                    (InfusionEngine.rate(dose, engineScheme, weight, concentration) as? CalcResult.Success)
                        ?.value
                        ?.let { true to it }
                }
            } else {
                formState.rateText.toBigDecimalOrNull()?.let { rate ->
                    (InfusionEngine.dose(rate, engineScheme, weight, concentration) as? CalcResult.Success)
                        ?.value
                        ?.let { false to it }
                }
            }

        val doseValue =
            if (formState.source == InputSource.DOSE) {
                formState.doseText.toBigDecimalOrNull()
            } else {
                computed?.takeIf { !it.first }?.second
            }
        val displayDose =
            if (formState.source == InputSource.DOSE) {
                formState.doseText
            } else {
                computed
                    ?.takeIf { !it.first }
                    ?.second
                    ?.let { InfusionEngine.formatDoseDisplay(it) }
                    ?.display() ?: ""
            }
        val displayRate =
            if (formState.source == InputSource.RATE) {
                formState.rateText
            } else {
                computed
                    ?.takeIf { it.first }
                    ?.second
                    ?.let { InfusionEngine.formatRate(it) }
                    ?.display() ?: ""
            }
        val resultValue =
            computed?.let {
                if (it.first) InfusionEngine.formatRate(it.second) else InfusionEngine.formatDoseDisplay(it.second)
            }

        val doseMin = scheme?.doseMin
        val doseMax = scheme?.doseMax
        val aboveRange = doseValue != null && doseMax != null && doseValue > doseMax
        val belowRange = doseValue != null && doseMin != null && doseValue < doseMin

        return CalculatorUiState(
            isLoaded = true,
            drug = drug,
            scheme = scheme,
            preset = preset,
            weight = weight,
            source = formState.source,
            concentration = concentration,
            unitMismatch = unitMismatch,
            displayDose = displayDose,
            displayRate = displayRate,
            resultValue = resultValue,
            resultIsRate = computed?.first == true,
            aboveRange = aboveRange,
            belowRange = belowRange,
        )
    }
}
