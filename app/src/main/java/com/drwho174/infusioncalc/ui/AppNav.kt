package com.drwho174.infusioncalc.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.drwho174.infusioncalc.ui.navigation.CalculatorKey
import com.drwho174.infusioncalc.ui.navigation.CatalogKey
import com.drwho174.infusioncalc.ui.navigation.DisclaimerKey
import com.drwho174.infusioncalc.ui.navigation.DrugCardKey
import com.drwho174.infusioncalc.ui.navigation.EditorKey
import com.drwho174.infusioncalc.ui.navigation.SettingsKey
import com.drwho174.infusioncalc.ui.navigation.WeightKey
import com.drwho174.infusioncalc.ui.screens.CalculatorScreen
import com.drwho174.infusioncalc.ui.screens.CatalogScreen
import com.drwho174.infusioncalc.ui.screens.DisclaimerScreen
import com.drwho174.infusioncalc.ui.screens.DrugCardScreen
import com.drwho174.infusioncalc.ui.screens.EditorScreen
import com.drwho174.infusioncalc.ui.screens.SettingsScreen
import com.drwho174.infusioncalc.ui.screens.WeightScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass

private val navigationConfig = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(DisclaimerKey::class)
            subclass(WeightKey::class)
            subclass(CatalogKey::class)
            subclass(CalculatorKey::class)
            subclass(DrugCardKey::class)
            subclass(EditorKey::class)
            subclass(SettingsKey::class)
        }
    }
}

@Composable
fun AppNav(disclaimerAccepted: Boolean) {
    val container = rememberAppContainer()
    val scope = rememberCoroutineScope()
    val startKey: NavKey = if (disclaimerAccepted) WeightKey else DisclaimerKey
    val backStack = rememberNavBackStack(navigationConfig, startKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<DisclaimerKey> {
                DisclaimerScreen(
                    onAccept = {
                        scope.launch { container.settingsRepository.setDisclaimerAccepted() }
                        backStack.add(WeightKey)
                    },
                )
            }
            entry<WeightKey> {
                WeightScreen(onContinue = { backStack.add(CatalogKey) })
            }
            entry<CatalogKey> {
                CatalogScreen(
                    onOpenDrug = { drugId -> backStack.add(CalculatorKey(drugId)) },
                    onAddDrug = { backStack.add(EditorKey()) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                )
            }
            entry<CalculatorKey> { key ->
                CalculatorScreen(
                    drugId = key.drugId,
                    onBack = { backStack.removeLastOrNull() },
                    onOpenCard = { backStack.add(DrugCardKey(key.drugId)) },
                    onEditDrug = { backStack.add(EditorKey(key.drugId)) },
                )
            }
            entry<DrugCardKey> { key ->
                DrugCardScreen(
                    drugId = key.drugId,
                    onBack = { backStack.removeLastOrNull() },
                    onCalculate = { backStack.add(CalculatorKey(key.drugId)) },
                )
            }
            entry<EditorKey> { key ->
                EditorScreen(
                    drugId = key.drugId,
                    onBack = { backStack.removeLastOrNull() },
                    onSaved = { backStack.removeLastOrNull() },
                )
            }
            entry<SettingsKey> {
                SettingsScreen(onBack = { backStack.removeLastOrNull() })
            }
        },
    )
}
