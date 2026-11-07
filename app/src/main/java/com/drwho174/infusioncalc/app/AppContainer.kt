package com.drwho174.infusioncalc.app

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drwho174.infusioncalc.data.RoomDrugRepository
import com.drwho174.infusioncalc.data.local.AppDatabase
import com.drwho174.infusioncalc.data.seed.CatalogSeed
import com.drwho174.infusioncalc.data.settings.SettingsRepository
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Ручной DI-контейнер (TECH-PLAN §8). */
class AppContainer(context: Context) {

    private val database: AppDatabase = Room.databaseBuilder<AppDatabase>(
        context.applicationContext,
        "infusioncalc.db",
    ).setDriver(BundledSQLiteDriver()).build()

    val drugRepository: DrugRepository = RoomDrugRepository(database.drugDao())

    val settingsRepository: SettingsRepository = SettingsRepository(context.applicationContext)

    val weightStore: WeightStore = WeightStore()

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        applicationScope.launch {
            // Добавляем недостающие встроенные препараты, не трогая пользовательские данные.
            val existingBuiltInIds = drugRepository.observeDrugs().first()
                .filter { it.isBuiltIn }
                .map { it.id }
                .toSet()
            CatalogSeed.drugs()
                .filter { it.id !in existingBuiltInIds }
                .forEach { drugRepository.save(it) }
        }
    }
}
