package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.model.convertAmountUnit
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import com.drwho174.infusioncalc.engine.AmountUnit
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
import kotlinx.coroutines.launch
import java.math.BigDecimal
import com.drwho174.infusioncalc.engine.DoseScheme as EngineDoseScheme

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
    val folders: List<Folder> = emptyList(),
    val drugFolderIds: Set<String> = emptySet(),
)

private data class CalculatorForm(
    val doseText: String = "",
    val rateText: String = "",
    val source: InputSource = InputSource.DOSE,
    val presetId: String? = null,
)

private data class ComputedValues(
    val displayDose: String,
    val displayRate: String,
    val resultValue: BigDecimal?,
    val resultIsRate: Boolean,
    val doseValue: BigDecimal?,
)

private data class RangeFlags(
    val above: Boolean,
    val below: Boolean,
)

/** Состояние и расчёт экрана скорости инфузии. */
class CalculatorViewModel(
    private val drugId: String,
    private val repository: DrugRepository,
    private val weightStore: WeightStore,
    private val folderRepository: FolderRepository,
) : ViewModel() {
    private val form = MutableStateFlow(CalculatorForm())

    val state: StateFlow<CalculatorUiState> =
        combine(
            repository.observeDrug(drugId),
            weightStore.weight,
            form,
            folderRepository.observeFolders(),
            folderRepository.observeMemberships(),
        ) { drug, weight, formState, folders, memberships ->
            buildState(drug, weight, formState, folders, memberships[drugId].orEmpty())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalculatorUiState())

    fun onDoseChange(raw: String) {
        form.update { it.copy(source = InputSource.DOSE, doseText = normalizeDecimalInput(raw)) }
    }

    fun onRateChange(raw: String) {
        form.update { it.copy(source = InputSource.RATE, rateText = normalizeDecimalInput(raw)) }
    }

    fun onPresetSelected(preset: ConcentrationPreset) {
        form.update { it.copy(presetId = preset.id) }
    }

    fun onFoldersChanged(folderIds: Set<String>) {
        viewModelScope.launch {
            folderRepository.setDrugFolders(drugId, folderIds)
        }
    }

    fun onCreateFolder(name: String) {
        viewModelScope.launch {
            val created = folderRepository.create(name) ?: return@launch
            folderRepository.setDrugFolders(drugId, state.value.drugFolderIds + created.id)
        }
    }

    private fun buildState(
        drug: Drug?,
        weight: BigDecimal?,
        formState: CalculatorForm,
        folders: List<Folder>,
        drugFolderIds: Set<String>,
    ): CalculatorUiState {
        if (drug == null) return CalculatorUiState(isLoaded = true)

        val scheme = drug.defaultScheme
        val preset = drug.presets.firstOrNull { it.id == formState.presetId } ?: drug.defaultPreset
        val schemeUnit = scheme?.amountUnit
        val concentration = preset?.concentrationIn(schemeUnit)
        val values = computeValues(formState, scheme, concentration, weight)
        val range = computeRange(scheme, values.doseValue)

        return CalculatorUiState(
            isLoaded = true,
            drug = drug,
            scheme = scheme,
            preset = preset,
            weight = weight,
            source = formState.source,
            concentration = concentration,
            unitMismatch = isUnitMismatch(preset, schemeUnit),
            displayDose = values.displayDose,
            displayRate = values.displayRate,
            resultValue = values.resultValue,
            resultIsRate = values.resultIsRate,
            aboveRange = range.above,
            belowRange = range.below,
            folders = folders,
            drugFolderIds = drugFolderIds,
        )
    }

    private fun isUnitMismatch(
        preset: ConcentrationPreset?,
        schemeUnit: AmountUnit?,
    ): Boolean =
        preset?.amountUnit?.let { presetUnit ->
            schemeUnit?.let { convertAmountUnit(BigDecimal.ONE, presetUnit, it) == null }
        } ?: false

    private fun computeValues(
        formState: CalculatorForm,
        scheme: DoseScheme?,
        concentration: BigDecimal?,
        weight: BigDecimal?,
    ): ComputedValues {
        val computed = computeRaw(formState, scheme?.toEngine(), concentration, weight)
        val fromDose = formState.source == InputSource.DOSE
        val computedDose = computed?.takeIf { !it.first }?.second
        val computedRate = computed?.takeIf { it.first }?.second
        val doseValue = if (fromDose) formState.doseText.toBigDecimalOrNull() else computedDose
        val displayDose =
            if (fromDose) {
                formState.doseText
            } else {
                computedDose?.let { InfusionEngine.formatDoseDisplay(it) }?.display() ?: ""
            }
        val displayRate =
            if (!fromDose) {
                formState.rateText
            } else {
                computedRate?.let { InfusionEngine.formatRate(it) }?.display() ?: ""
            }
        val resultValue =
            computed?.let {
                if (it.first) InfusionEngine.formatRate(it.second) else InfusionEngine.formatDoseDisplay(it.second)
            }
        return ComputedValues(
            displayDose = displayDose,
            displayRate = displayRate,
            resultValue = resultValue,
            resultIsRate = computed?.first == true,
            doseValue = doseValue,
        )
    }

    private fun computeRaw(
        formState: CalculatorForm,
        engineScheme: EngineDoseScheme?,
        concentration: BigDecimal?,
        weight: BigDecimal?,
    ): Pair<Boolean, BigDecimal>? {
        if (concentration == null || engineScheme == null) return null
        return if (formState.source == InputSource.DOSE) {
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
    }

    private fun computeRange(
        scheme: DoseScheme?,
        doseValue: BigDecimal?,
    ): RangeFlags {
        val doseMin = scheme?.doseMin
        val doseMax = scheme?.doseMax
        return RangeFlags(
            above = doseValue != null && doseMax != null && doseValue > doseMax,
            below = doseValue != null && doseMin != null && doseValue < doseMin,
        )
    }
}
