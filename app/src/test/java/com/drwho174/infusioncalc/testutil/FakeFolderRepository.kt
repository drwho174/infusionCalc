package com.drwho174.infusioncalc.testutil

import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

/** Простой in-memory репозиторий папок для тестов ViewModel. */
class FakeFolderRepository(
    initial: List<Folder> = emptyList(),
) : FolderRepository {
    private val folders = MutableStateFlow(initial)
    private val memberships = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override fun observeFolders(): Flow<List<Folder>> = folders

    override fun observeMemberships(): Flow<Map<String, Set<String>>> = memberships

    override suspend fun folderIdsForDrug(drugId: String): Set<String> = memberships.value[drugId].orEmpty()

    override suspend fun create(name: String): Folder? {
        val trimmed = name.trim()
        val valid =
            trimmed.isNotEmpty() &&
                trimmed.length <= Folder.MAX_NAME_LENGTH &&
                folders.value.none { it.name.equals(trimmed, ignoreCase = true) }
        if (!valid) return null
        val folder = Folder(id = UUID.randomUUID().toString(), name = trimmed)
        folders.value = folders.value + folder
        return folder
    }

    override suspend fun delete(id: String) {
        folders.value = folders.value.filterNot { it.id == id }
        memberships.value = memberships.value.mapValues { (_, ids) -> ids - id }
    }

    override suspend fun setDrugFolders(
        drugId: String,
        folderIds: Set<String>,
    ) {
        memberships.value = memberships.value + (drugId to folderIds)
    }
}
