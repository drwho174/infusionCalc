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
import java.math.MathContext
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
    val mass: String = "",
    val volume: String = "",
    val massUnit: AmountUnit = AmountUnit.MCG,
    val concentration: BigDecimal? = null,
    val duplicateName: Boolean = false,
    val valid: Boolean = false,
)

/** Редактор карточки препарата. Правка встроенного препарата сохраняется копией в «Мои». */
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

    fun onMassChange(value: String) = update { it.copy(mass = normalizeDecimalInput(value)) }

    fun onVolumeChange(value: String) = update { it.copy(volume = normalizeDecimalInput(value)) }

    fun onMassUnitChange(value: AmountUnit) = update { it.copy(massUnit = value) }

    fun save(onSaved: (String) -> Unit) {
        val current = _state.value
        if (!current.valid) return

        val base = existing
        val isCopyOfBuiltIn = base?.isBuiltIn == true
        val isNewOrCopy = base == null || isCopyOfBuiltIn
        val id = if (isNewOrCopy) UUID.randomUUID().toString() else base.id

        val schemeId = UUID.randomUUID().toString()
        val presetId = UUID.randomUUID().toString()
        val drug =
            Drug(
                id = id,
                nameRu = current.name.trim(),
                nameEn = base?.nameEn,
                drugClass = base?.drugClass ?: DrugClass.OTHER,
                highAlert = base?.highAlert ?: false,
                route = base?.route ?: "",
                isBuiltIn = false,
                derivedFromId = if (isCopyOfBuiltIn) base.id else base?.derivedFromId,
                verification = Verification.USER,
                notes = base?.notes,
                incompatibilities = base?.incompatibilities,
                revision = if (isNewOrCopy) 1 else base.revision + 1,
                catalogVersion = base?.catalogVersion ?: 0,
                updatedAt = System.currentTimeMillis(),
                defaultSchemeId = schemeId,
                schemes =
                    listOf(
                        DoseScheme(
                            id = schemeId,
                            drugId = id,
                            amountUnit = current.unit,
                            perKg = current.perKg,
                            perTime = current.timeBase,
                            doseMin = current.doseMin.toBigDecimalOrNull(),
                            doseMax = current.doseMax.toBigDecimalOrNull(),
                            schemeOrder = 0,
                        ),
                    ),
                presets =
                    listOf(
                        ConcentrationPreset(
                            id = presetId,
                            drugId = id,
                            mode = ConcentrationMode.MASS_VOLUME,
                            amount = current.mass.toBigDecimalOrNull(),
                            amountUnit = current.massUnit,
                            volumeMl = current.volume.toBigDecimalOrNull(),
                            isDefault = true,
                        ),
                    ),
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
        val mass = state.mass.toBigDecimalOrNull()
        val volume = state.volume.toBigDecimalOrNull()
        val concentration =
            if (mass != null && volume != null && volume.signum() > 0) {
                mass.divide(volume, MathContext(34))
            } else {
                null
            }
        val duplicate = isDuplicateName(state.name)
        val valid =
            state.name.isNotBlank() &&
                !duplicate &&
                mass != null &&
                mass.signum() > 0 &&
                volume != null &&
                volume.signum() > 0
        return state.copy(concentration = concentration, duplicateName = duplicate, valid = valid)
    }

    /**
     * Новый препарат не должен совпадать по названию ни с одним существующим.
     * Для правки/копии проверяются только пользовательские препараты: копия
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
                    it.nameRu.trim().equals(name, ignoreCase = true)
            }
        }
    }

    private fun prefilled(drug: Drug): EditorUiState {
        val scheme = drug.defaultScheme
        val preset = drug.defaultPreset
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
                mass = preset?.amount?.toPlainString() ?: "",
                volume = preset?.volumeMl?.toPlainString() ?: "",
                massUnit = preset?.amountUnit ?: AmountUnit.MCG,
            ),
        )
    }
}
