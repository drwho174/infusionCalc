package com.drwho174.infusioncalc.data

import com.drwho174.infusioncalc.data.local.DrugDao
import com.drwho174.infusioncalc.data.local.FavoriteEntity
import com.drwho174.infusioncalc.data.local.toDomain
import com.drwho174.infusioncalc.data.local.toEntity
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class RoomDrugRepository(private val dao: DrugDao) : DrugRepository {

    override fun observeDrugs(): Flow<List<Drug>> =
        dao.observeDrugs().map { list -> list.map { it.toDomain() } }

    override fun observeFavorites(): Flow<Set<String>> =
        dao.observeFavoriteIds().map { it.toSet() }

    override suspend fun getDrug(id: String): Drug? = withContext(Dispatchers.IO) {
        dao.getDrug(id)?.toDomain()
    }

    override suspend fun save(drug: Drug) = withContext(Dispatchers.IO) {
        dao.insertDrug(drug.toEntity())
        dao.deleteSchemes(drug.id)
        dao.insertSchemes(drug.schemes.map { it.toEntity() })
        dao.deletePresets(drug.id)
        dao.insertPresets(drug.presets.map { it.toEntity() })
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        dao.deleteDrug(id)
    }

    override suspend fun setFavorite(drugId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        if (favorite) {
            dao.insertFavorite(FavoriteEntity(drugId = drugId, createdAt = System.currentTimeMillis()))
        } else {
            dao.deleteFavorite(drugId)
        }
    }
}
