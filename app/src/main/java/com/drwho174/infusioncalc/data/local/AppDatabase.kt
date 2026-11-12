package com.drwho174.infusioncalc.data.local

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.RoomDatabase

@Database(
    entities = [
        DrugEntity::class,
        DoseSchemeEntity::class,
        ConcentrationPresetEntity::class,
        FavoriteEntity::class,
        FolderEntity::class,
        DrugFolderEntity::class,
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao

    abstract fun folderDao(): FolderDao
}
