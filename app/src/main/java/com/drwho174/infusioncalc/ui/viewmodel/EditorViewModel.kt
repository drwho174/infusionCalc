package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.DrugClass
import com.drwho174.infusioncalc.domain.model.Verification
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import com.drwho174.infusioncalc.ui.normalizeDecimalInput
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class EditorUiState(
    val isLoaded: Boolean = false,
    val isEditing: Boolean = false,
    val name: String = "",
    val unit: AmountUnit = AmountUnit.MCG,
    val perKg: Boolean = false,
    val timeBase: TimeBase = TimeBase.PER_MINUTE,
    val doseMin: String = "",
    val doseMax: String = "",
    val notes: String = "",
    val presets: List<ConcentrationPreset> = emptyList(),
    val selectedPresetId: String? = null,
    val duplicateName: Boolean = false,
    val valid: Boolean = false,
)

/**
 * Единый редактор препарата: дозирование, концентрации и заметки.
 * Правка встроенного препарата сохраняется **на месте**: копия с тем же
 * названием создавала бы дубликат в списке (баг FR-CUSTOM-06).
 * От перезаписи сидом правку защищает [revision] > 1 (см. [CatalogSeeder]).
 */
@Suppress("TooManyFunctions")
class EditorViewModel(
    private val initialDrugId: String?,
    private val repository: DrugRepository,
) : ViewModel() {
    private var existing: Drug? = null
    private var existingDrugs: List<Drug> = emptyList()

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val loaded = initialDrugId?.let { repository.getDrug(it) }
            existing = loaded
            existingDrugs = repository.observeDrugs().first()
            _state.value = if (loaded != null) prefilled(loaded) else derive(EditorUiState(isLoaded = true))
        }
    }

    fun onNameChange(value: String) = update { it.copy(name = value) }

    fun onUnitChange(value: AmountUnit) = update { it.copy(unit = value) }

    fun onPerKgChange(value: Boolean) = update { it.copy(perKg = value) }

    fun onTimeBaseChange(value: TimeBase) = update { it.copy(timeBase = value) }

    fun onDoseMinChange(value: String) = update { it.copy(doseMin = normalizeDecimalInput(value)) }

    fun onDoseMaxChange(value: String) = update { it.copy(doseMax = normalizeDecimalInput(value)) }

    fun onNotesChange(value: String) = update { it.copy(notes = value) }

    fun onPresetSelected(id: String) = update { it.copy(selectedPresetId = id) }

    /** Новая концентрация из диалога: чипсы в блоке «Концентрации». */
    fun addPreset(
        amount: BigDecimal,
        amountUnit: AmountUnit,
        volumeMl: BigDecimal,
    ) {
        val preset =
            ConcentrationPreset(
                id = UUID.randomUUID().toString(),
                drugId = existing?.id.orEmpty(),
                mode = ConcentrationMode.MASS_VOLUME,
                amount = amount,
                amountUnit = amountUnit,
                volumeMl = volumeMl,
            )
        update { it.copy(presets = it.presets + preset, selectedPresetId = preset.id) }
    }

    fun updatePreset(
        id: String,
        amount: BigDecimal,
        amountUnit: AmountUnit,
        volumeMl: BigDecimal,
    ) {
        update { state ->
            state.copy(
                presets =
                    state.presets.map {
                        if (it.id == id) {
                            it.copy(
                                mode = ConcentrationMode.MASS_VOLUME,
                                amount = amount,
                                amountUnit = amountUnit,
                                volumeMl = volumeMl,
                            )
                        } else {
                            it
                        }
                    },
            )
        }
    }

    fun deletePreset(id: String) {
        update { state ->
            if (state.presets.size <= 1) {
                state
            } else {
                val remaining = state.presets.filterNot { it.id == id }
                state.copy(
                    presets = remaining,
                    selectedPresetId =
                        if (state.selectedPresetId == id) remaining.firstOrNull()?.id else state.selectedPresetId,
                )
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    fun save(onSaved: (String) -> Unit) {
        val current = _state.value
        if (!current.valid) return

        val base = existing
        val keptBuiltIn = base?.isBuiltIn == true
        val id = base?.id ?: UUID.randomUUID().toString()

        val schemeId = base?.defaultScheme?.id ?: UUID.randomUUID().toString()
        val editedScheme =
            DoseScheme(
                id = schemeId,
                drugId = id,
                amountUnit = current.unit,
                perKg = current.perKg,
                perTime = current.timeBase,
                doseMin = current.doseMin.toBigDecimalOrNull(),
                doseMax = current.doseMax.toBigDecimalOrNull(),
                schemeOrder = base?.defaultScheme?.schemeOrder ?: 0,
            )
        val selectedId = current.selectedPresetId ?: current.presets.firstOrNull()?.id
        val presets =
            current.presets.map {
                it.copy(
                    drugId = id,
                    isDefault = it.id == selectedId,
                )
            }
        // Встроенный препарат правится на месте: остальные схемы сохраняются.
        val schemes =
            if (keptBuiltIn) {
                base!!.schemes.map { if (it.id == schemeId) editedScheme else it }
            } else {
                listOf(editedScheme)
            }
        val drug =
            Drug(
                id = id,
                nameRu = current.name.trim(),
                nameEn = base?.nameEn,
                drugClass = base?.drugClass ?: DrugClass.OTHER,
                highAlert = base?.highAlert ?: false,
                route = base?.route ?: "",
                isBuiltIn = keptBuiltIn,
                derivedFromId = base?.derivedFromId,
                verification = base?.verification ?: Verification.USER,
                notes = current.notes.trim().ifBlank { null },
                incompatibilities = base?.incompatibilities,
                revision = (base?.revision ?: 0) + 1,
                catalogVersion = base?.catalogVersion ?: 0,
                updatedAt = System.currentTimeMillis(),
                defaultSchemeId = schemeId,
                schemes = schemes,
                presets = presets,
            )
        viewModelScope.launch {
            repository.save(drug)
            onSaved(drug.id)
        }
    }

    private fun update(transform: (EditorUiState) -> EditorUiState) {
        _state.value = derive(transform(_state.value))
    }

    private fun derive(state: EditorUiState): EditorUiState {
        val duplicate = isDuplicateName(state.name)
        val valid =
            state.name.isNotBlank() &&
                !duplicate &&
                state.presets.isNotEmpty() &&
                state.presets.all { it.concentrationIn() != null }
        return state.copy(duplicateName = duplicate, valid = valid)
    }

    /**
     * Новый препарат не должен совпадать по названию ни с одним существующим.
     * Для правки проверяются только пользовательские препараты: копия
     * встроенного намеренно носит имя оригинала.
     */
    private fun isDuplicateName(rawName: String): Boolean {
        val name = rawName.trim()
        if (name.isEmpty()) return false
        val current = existing
        return if (current == null) {
            existingDrugs.any { it.nameRu.trim().equals(name, ignoreCase = true) }
        } else {
            existingDrugs.any {
                it.id != current.id &&
                    !it.isBuiltIn &&
                    it.derivedFromId != current.id &&
                    it.nameRu.trim().equals(name, ignoreCase = true)
            }
        }
    }

    private fun prefilled(drug: Drug): EditorUiState {
        val scheme = drug.defaultScheme
        val defaultPreset = drug.defaultPreset
        return derive(
            EditorUiState(
                isLoaded = true,
                isEditing = true,
                name = drug.nameRu,
                unit = scheme?.amountUnit ?: AmountUnit.MCG,
                perKg = scheme?.perKg ?: false,
                timeBase = scheme?.perTime ?: TimeBase.PER_MINUTE,
                doseMin = scheme?.doseMin?.toPlainString() ?: "",
                doseMax = scheme?.doseMax?.toPlainString() ?: "",
                notes = drug.notes.orEmpty(),
                presets = drug.presets,
                selectedPresetId = defaultPreset?.id,
            ),
        )
    }
}
