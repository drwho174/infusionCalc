package com.drwho174.infusioncalc.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FolderDao {
    @Query("SELECT * FROM folders ORDER BY name COLLATE NOCASE")
    abstract fun observeFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM drug_folders")
    abstract fun observeMemberships(): Flow<List<DrugFolderEntity>>

    @Query("SELECT COUNT(*) FROM folders WHERE nameLower = :nameLower")
    abstract suspend fun countByName(nameLower: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    abstract suspend fun deleteFolder(id: String)

    @Query("SELECT folderId FROM drug_folders WHERE drugId = :drugId")
    abstract suspend fun folderIds(drugId: String): List<String>

    @Query("DELETE FROM drug_folders WHERE drugId = :drugId")
    abstract suspend fun clearDrugFolders(drugId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMemberships(rows: List<DrugFolderEntity>)

    @Transaction
    open suspend fun setDrugFolders(
        drugId: String,
        folderIds: Set<String>,
    ) {
        clearDrugFolders(drugId)
        if (folderIds.isNotEmpty()) {
            insertMemberships(folderIds.map { DrugFolderEntity(drugId = drugId, folderId = it) })
        }
    }
}
