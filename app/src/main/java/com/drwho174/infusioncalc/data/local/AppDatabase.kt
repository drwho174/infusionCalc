package com.drwho174.infusioncalc.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        DrugEntity::class,
        DoseSchemeEntity::class,
        ConcentrationPresetEntity::class,
        FavoriteEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao
}
