package com.drwho174.infusioncalc.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Управление папками: список и удаление. */
private const val SUBSCRIPTION_TIMEOUT_MS = 5_000L

class FolderSettingsViewModel(
    private val repository: FolderRepository,
) : ViewModel() {
    val folders: StateFlow<List<Folder>> =
        repository
            .observeFolders()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), emptyList())

    fun rename(
        folderId: String,
        name: String,
    ) {
        viewModelScope.launch { repository.rename(folderId, name) }
    }

    fun delete(folderId: String) {
        viewModelScope.launch { repository.delete(folderId) }
    }
}
