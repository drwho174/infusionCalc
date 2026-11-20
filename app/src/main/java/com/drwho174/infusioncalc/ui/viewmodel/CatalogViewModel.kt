package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class CatalogUiState(
    val filtered: List<Drug> = emptyList(),
    /** Препараты, которых ещё нет в выбранной папке (для диалога добавления). */
    val availableForFolder: List<Drug> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null,
    val query: String = "",
    val weight: BigDecimal? = null,
)

/** Список препаратов с поиском и папками (null — «Все»). */
class CatalogViewModel(
    private val repository: DrugRepository,
    private val folderRepository: FolderRepository,
    private val weightStore: WeightStore = WeightStore(),
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedFolderId = MutableStateFlow<String?>(null)

    private val baseState: Flow<CatalogUiState> =
        combine(
            repository.observeDrugs(),
            folderRepository.observeFolders(),
            folderRepository.observeMemberships(),
            query,
            selectedFolderId,
        ) { drugs, folders, memberships, currentQuery, currentFolder ->
            val filtered =
                drugs.filter { drug ->
                    val matchesQuery =
                        currentQuery.isBlank() ||
                            drug.nameRu.contains(currentQuery, ignoreCase = true) ||
                            (drug.nameEn?.contains(currentQuery, ignoreCase = true) == true)
                    val matchesFolder =
                        currentFolder == null || memberships[drug.id]?.contains(currentFolder) == true
                    matchesQuery && matchesFolder
                }
            val availableForFolder =
                if (currentFolder == null) {
                    emptyList()
                } else {
                    drugs.filter { memberships[it.id]?.contains(currentFolder) != true }
                }
            CatalogUiState(
                filtered = filtered,
                availableForFolder = availableForFolder,
                folders = folders,
                selectedFolderId = currentFolder,
                query = currentQuery,
            )
        }

    val state: StateFlow<CatalogUiState> =
        combine(baseState, weightStore.weight) { current, weight ->
            current.copy(weight = weight)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), CatalogUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFolderSelected(folderId: String?) {
        selectedFolderId.value = folderId
    }

    fun onAddDrugsToFolder(
        folderId: String,
        drugIds: Set<String>,
    ) {
        if (drugIds.isEmpty()) return
        viewModelScope.launch { folderRepository.addDrugsToFolder(folderId, drugIds) }
    }

    fun onCreateFolder(name: String) {
        viewModelScope.launch {
            folderRepository.create(name)?.let { selectedFolderId.value = it.id }
        }
    }

    fun onRenameFolder(
        folderId: String,
        name: String,
    ) {
        viewModelScope.launch { folderRepository.rename(folderId, name) }
    }

    fun onDeleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.delete(folderId)
            if (selectedFolderId.value == folderId) selectedFolderId.value = null
        }
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MS = 5_000L
    }
}
