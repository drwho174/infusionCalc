package com.drwho174.infusioncalc.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

/** Полиморфный модуль для ключей навигации (сохранение back stack, SavedState). */
internal val navSerializersModule =
    SerializersModule {
        polymorphic(NavKey::class) {
            subclass(DisclaimerKey::class)
            subclass(WeightKey::class)
            subclass(CatalogKey::class)
            subclass(CalculatorKey::class)
            subclass(DrugCardKey::class)
            subclass(EditorKey::class)
            subclass(SettingsKey::class)
            subclass(TciKey::class)
            subclass(TciModelsKey::class)
        }
    }
