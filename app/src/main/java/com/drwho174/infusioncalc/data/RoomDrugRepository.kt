package com.drwho174.infusioncalc.data

import com.drwho174.infusioncalc.data.local.DrugDao
import com.drwho174.infusioncalc.data.local.toDomain
import com.drwho174.infusioncalc.data.local.toEntity
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class RoomDrugRepository(
    private val dao: DrugDao,
) : DrugRepository {
    override fun observeDrugs(): Flow<List<Drug>> = dao.observeDrugs().map { list -> list.map { it.toDomain() } }

    override fun observeDrug(id: String): Flow<Drug?> = dao.observeDrug(id).map { it?.toDomain() }

    override suspend fun getDrug(id: String): Drug? =
        withContext(Dispatchers.IO) {
            dao.getDrug(id)?.toDomain()
        }

    override suspend fun save(drug: Drug) =
        withContext(Dispatchers.IO) {
            dao.saveWithDetails(
                drug = drug.toEntity(),
                schemes = drug.schemes.map { it.toEntity() },
                presets = drug.presets.map { it.toEntity() },
            )
        }

    override suspend fun delete(id: String) =
        withContext(Dispatchers.IO) {
            dao.deleteDrug(id)
        }
}
