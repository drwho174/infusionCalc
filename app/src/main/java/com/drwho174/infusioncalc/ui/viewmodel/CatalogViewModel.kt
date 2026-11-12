package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CatalogUiState(
    val filtered: List<Drug> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null,
    val query: String = "",
)

/** Список препаратов с поиском и папками (null — «Все»). */
class CatalogViewModel(
    private val repository: DrugRepository,
    private val folderRepository: FolderRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val selectedFolderId = MutableStateFlow<String?>(null)

    val state: StateFlow<CatalogUiState> =
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
            CatalogUiState(
                filtered = filtered,
                folders = folders,
                selectedFolderId = currentFolder,
                query = currentQuery,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFolderSelected(folderId: String?) {
        selectedFolderId.value = folderId
    }

    fun onCreateFolder(name: String) {
        viewModelScope.launch {
            folderRepository.create(name)?.let { selectedFolderId.value = it.id }
        }
    }

    fun onDeleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository.delete(folderId)
            if (selectedFolderId.value == folderId) selectedFolderId.value = null
        }
    }
}
