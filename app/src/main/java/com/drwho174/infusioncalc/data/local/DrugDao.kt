package com.drwho174.infusioncalc.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface DrugDao {

    @Transaction
    @Query("SELECT * FROM drugs WHERE archived = 0 ORDER BY nameRu COLLATE NOCASE")
    fun observeDrugs(): Flow<List<DrugWithDetails>>

    @Transaction
    @Query("SELECT * FROM drugs WHERE id = :id")
    suspend fun getDrug(id: String): DrugWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDrug(drug: DrugEntity)

    @Query("DELETE FROM dose_schemes WHERE drugId = :drugId")
    suspend fun deleteSchemes(drugId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchemes(schemes: List<DoseSchemeEntity>)

    @Query("DELETE FROM concentration_presets WHERE drugId = :drugId")
    suspend fun deletePresets(drugId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<ConcentrationPresetEntity>)

    @Query("DELETE FROM drugs WHERE id = :id")
    suspend fun deleteDrug(id: String)

    @Query("SELECT drugId FROM favorites")
    fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE drugId = :drugId")
    suspend fun deleteFavorite(drugId: String)
}
