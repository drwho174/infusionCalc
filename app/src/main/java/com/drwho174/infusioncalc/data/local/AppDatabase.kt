package com.drwho174.infusioncalc.data.local

import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.DeleteTable
import androidx.room3.RoomDatabase
import androidx.room3.migration.AutoMigrationSpec

/** Удаление таблицы избранного: в v3 функция избранного удалена. */
@DeleteTable(tableName = "favorites")
class DeleteFavoritesSpec : AutoMigrationSpec

@Database(
    entities = [
        DrugEntity::class,
        DoseSchemeEntity::class,
        ConcentrationPresetEntity::class,
        FolderEntity::class,
        DrugFolderEntity::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3, spec = DeleteFavoritesSpec::class),
    ],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun drugDao(): DrugDao

    abstract fun folderDao(): FolderDao
}
