package com.drwho174.infusioncalc.domain.repository

import com.drwho174.infusioncalc.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeFolders(): Flow<List<Folder>>

    /** drugId → идентификаторы папок, в которых состоит препарат. */
    fun observeMemberships(): Flow<Map<String, Set<String>>>

    suspend fun folderIdsForDrug(drugId: String): Set<String>

    /** Создаёт папку; null — пустое/слишком длинное/повторяющееся имя. */
    suspend fun create(name: String): Folder?

    suspend fun delete(id: String)

    suspend fun setDrugFolders(
        drugId: String,
        folderIds: Set<String>,
    )

    /** Добавляет препараты в папку; уже существующие связи сохраняются. */
    suspend fun addDrugsToFolder(
        folderId: String,
        drugIds: Set<String>,
    )
}
