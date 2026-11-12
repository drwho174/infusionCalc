package com.drwho174.infusioncalc.di

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.drwho174.infusioncalc.data.RoomDrugRepository
import com.drwho174.infusioncalc.data.RoomFolderRepository
import com.drwho174.infusioncalc.data.local.AppDatabase
import com.drwho174.infusioncalc.data.local.DrugDao
import com.drwho174.infusioncalc.data.local.FolderDao
import com.drwho174.infusioncalc.data.seed.CatalogSeeder
import com.drwho174.infusioncalc.data.settings.SettingsRepository
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import com.drwho174.infusioncalc.domain.repository.FolderRepository
import com.drwho174.infusioncalc.ui.viewmodel.AppViewModel
import com.drwho174.infusioncalc.ui.viewmodel.CalculatorViewModel
import com.drwho174.infusioncalc.ui.viewmodel.CatalogViewModel
import com.drwho174.infusioncalc.ui.viewmodel.DrugCardViewModel
import com.drwho174.infusioncalc.ui.viewmodel.EditorViewModel
import com.drwho174.infusioncalc.ui.viewmodel.WeightViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** Единая точка сборки зависимостей приложения (Koin). */
val appModule =
    module {

        single {
            Room
                .databaseBuilder<AppDatabase>(androidContext(), "infusioncalc.db")
                .setDriver(BundledSQLiteDriver())
                .build()
        }
        single<DrugDao> { get<AppDatabase>().drugDao() }
        single<FolderDao> { get<AppDatabase>().folderDao() }
        single<DrugRepository> { RoomDrugRepository(get()) }
        single<FolderRepository> { RoomFolderRepository(get()) }
        single { SettingsRepository(androidContext()) }
        single { WeightStore() }
        single { CatalogSeeder(get()) }

        viewModel { AppViewModel(get()) }
        viewModel { WeightViewModel(get()) }
        viewModel { CatalogViewModel(get(), get()) }
        viewModel { (drugId: String) -> CalculatorViewModel(drugId, get(), get(), get()) }
        viewModel { (drugId: String) -> DrugCardViewModel(drugId, get()) }
        viewModel { (drugId: String) ->
            EditorViewModel(drugId.takeIf { it.isNotBlank() }, get())
        }
    }
