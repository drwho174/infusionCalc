package com.drwho174.infusioncalc.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class DrugDao {
    @Transaction
    @Query("SELECT * FROM drugs WHERE archived = 0 ORDER BY nameRu COLLATE NOCASE")
    abstract fun observeDrugs(): Flow<List<DrugWithDetails>>

    @Transaction
    @Query("SELECT * FROM drugs WHERE id = :id")
    abstract fun observeDrug(id: String): Flow<DrugWithDetails?>

    @Transaction
    @Query("SELECT * FROM drugs WHERE id = :id")
    abstract suspend fun getDrug(id: String): DrugWithDetails?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDrug(drug: DrugEntity)

    @Query("DELETE FROM dose_schemes WHERE drugId = :drugId")
    abstract suspend fun deleteSchemes(drugId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSchemes(schemes: List<DoseSchemeEntity>)

    @Query("DELETE FROM concentration_presets WHERE drugId = :drugId")
    abstract suspend fun deletePresets(drugId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertPresets(presets: List<ConcentrationPresetEntity>)

    @Query("DELETE FROM drugs WHERE id = :id")
    abstract suspend fun deleteDrug(id: String)

    @Query("SELECT drugId FROM favorites")
    abstract fun observeFavoriteIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE drugId = :drugId")
    abstract suspend fun deleteFavorite(drugId: String)

    /**
     * Атомарно сохраняет карточку: сам препарат, его схемы и пресеты.
     * Без транзакции сбой посередине оставит препарат без схем/пресетов.
     */
    @Transaction
    open suspend fun saveWithDetails(
        drug: DrugEntity,
        schemes: List<DoseSchemeEntity>,
        presets: List<ConcentrationPresetEntity>,
    ) {
        insertDrug(drug)
        deleteSchemes(drug.id)
        insertSchemes(schemes)
        deletePresets(drug.id)
        insertPresets(presets)
    }
}
