package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Карточка препарата: реактивное чтение и сохранение списка концентраций. */
class DrugCardViewModel(
    private val drugId: String,
    private val repository: DrugRepository,
) : ViewModel() {
    val drug: StateFlow<Drug?> =
        repository
            .observeDrug(drugId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun savePresets(updatedPresets: List<ConcentrationPreset>) {
        val current = drug.value ?: return
        val withDefault =
            updatedPresets.let { list ->
                if (list.isEmpty() || list.any { it.isDefault }) {
                    list
                } else {
                    list.mapIndexed { index, preset -> if (index == 0) preset.copy(isDefault = true) else preset }
                }
            }
        val updated =
            current.copy(
                presets = withDefault,
                revision = current.revision + 1,
                updatedAt = System.currentTimeMillis(),
            )
        viewModelScope.launch { repository.save(updated) }
    }
}
