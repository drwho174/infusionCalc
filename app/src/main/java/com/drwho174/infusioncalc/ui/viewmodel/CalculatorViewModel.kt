package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val folders: List<Folder> = emptyList(),
    val drugFolderIds: Set<String> = emptySet(),
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

    /** Удаляет пользовательский препарат; встроенные удалять нельзя. */
    fun delete(onDeleted: () -> Unit) {
        val drug = state.value.drug ?: return
        if (drug.isBuiltIn) return
        viewModelScope.launch {
            repository.delete(drug.id)
            onDeleted()
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
}
