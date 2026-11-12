package com.drwho174.infusioncalc.data

import com.drwho174.infusioncalc.data.local.FolderDao
import com.drwho174.infusioncalc.data.local.FolderEntity
import com.drwho174.infusioncalc.data.local.toDomain
import com.drwho174.infusioncalc.data.local.toMembershipMap
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

private typealias MembershipMap = Map<String, Set<String>>

class RoomFolderRepository(
    private val dao: FolderDao,
) : FolderRepository {
    override fun observeFolders(): Flow<List<Folder>> = dao.observeFolders().map { it.toDomain() }

    override fun observeMemberships(): Flow<MembershipMap> = dao.observeMemberships().map { it.toMembershipMap() }

    override suspend fun folderIdsForDrug(drugId: String): Set<String> =
        withContext(Dispatchers.IO) {
            dao.folderIds(drugId).toSet()
        }

    override suspend fun create(name: String): Folder? =
        withContext(Dispatchers.IO) {
            val trimmed = name.trim()
            if (trimmed.isEmpty() || trimmed.length > Folder.MAX_NAME_LENGTH) return@withContext null
            val normalized = trimmed.lowercase()
            if (dao.countByName(normalized) > 0) return@withContext null
            val folder = Folder(id = UUID.randomUUID().toString(), name = trimmed)
            dao.insertFolder(
                FolderEntity(
                    id = folder.id,
                    name = folder.name,
                    nameLower = normalized,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            folder
        }

    override suspend fun delete(id: String) =
        withContext(Dispatchers.IO) {
            dao.deleteFolder(id)
        }

    override suspend fun setDrugFolders(
        drugId: String,
        folderIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        dao.setDrugFolders(drugId, folderIds)
    }
}
