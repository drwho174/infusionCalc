package com.drwho174.infusioncalc.domain.repository

import com.drwho174.infusioncalc.domain.model.Drug
import kotlinx.coroutines.flow.Flow

interface DrugRepository {
    fun observeDrugs(): Flow<List<Drug>>

    fun observeDrug(id: String): Flow<Drug?>

    fun observeFavorites(): Flow<Set<String>>

    suspend fun getDrug(id: String): Drug?

    suspend fun save(drug: Drug)

    suspend fun delete(id: String)

    suspend fun setFavorite(
        drugId: String,
        favorite: Boolean,
    )
}
