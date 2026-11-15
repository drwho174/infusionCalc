package com.drwho174.infusioncalc.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import com.drwho174.infusioncalc.BuildConfig
import com.drwho174.infusioncalc.ui.about.AboutScreen
import com.drwho174.infusioncalc.ui.navigation.AboutKey
import com.drwho174.infusioncalc.ui.navigation.CalculatorKey
import com.drwho174.infusioncalc.ui.navigation.CatalogKey
import com.drwho174.infusioncalc.ui.navigation.DisclaimerKey
import com.drwho174.infusioncalc.ui.navigation.EditorKey
import com.drwho174.infusioncalc.ui.navigation.SettingsKey
import com.drwho174.infusioncalc.ui.navigation.TciKey
import com.drwho174.infusioncalc.ui.navigation.TciModelsKey
import com.drwho174.infusioncalc.ui.navigation.WeightKey
import com.drwho174.infusioncalc.ui.navigation.navSerializersModule
import com.drwho174.infusioncalc.ui.screens.CalculatorScreen
import com.drwho174.infusioncalc.ui.screens.CatalogScreen
import com.drwho174.infusioncalc.ui.screens.DisclaimerScreen
import com.drwho174.infusioncalc.ui.screens.EditorScreen
import com.drwho174.infusioncalc.ui.screens.SettingsScreen
import com.drwho174.infusioncalc.ui.screens.TciModelsScreen
import com.drwho174.infusioncalc.ui.screens.TciScreen
import com.drwho174.infusioncalc.ui.screens.WeightScreen
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private val navigationConfig =
    SavedStateConfiguration {
        serializersModule = navSerializersModule
    }

/** Длительность перехода между экранами — половина стандартных 700 мс. */
private const val TRANSITION_DURATION_MILLIS = 350

private val screenTransition: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
    ContentTransform(
        targetContentEnter = fadeIn(tween(TRANSITION_DURATION_MILLIS)),
        initialContentExit = fadeOut(tween(TRANSITION_DURATION_MILLIS)),
    )
}

@Composable
fun AppNav(
    disclaimerAccepted: Boolean,
    onAcceptDisclaimer: () -> Unit,
) {
    val startKey: NavKey = if (disclaimerAccepted) WeightKey else DisclaimerKey
    val backStack = rememberNavBackStack(navigationConfig, startKey)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        transitionSpec = screenTransition,
        popTransitionSpec = screenTransition,
        entryDecorators =
            listOf<NavEntryDecorator<NavKey>>(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<DisclaimerKey> {
                    DisclaimerScreen(
                        onAccept = {
                            onAcceptDisclaimer()
                            backStack.add(WeightKey)
                        },
                    )
                }
                entry<WeightKey> {
                    WeightScreen(
                        viewModel = koinViewModel(),
                        onContinue = { backStack.add(CatalogKey) },
                    )
                }
                entry<CatalogKey> {
                    CatalogScreen(
                        viewModel = koinViewModel(),
                        onOpenDrug = { drugId -> backStack.add(CalculatorKey(drugId)) },
                        onOpenTci = { backStack.add(TciKey) },
                        onAddDrug = { backStack.add(EditorKey()) },
                        onOpenSettings = { backStack.add(SettingsKey) },
                    )
                }
                entry<CalculatorKey> { key ->
                    CalculatorScreen(
                        viewModel = koinViewModel(key = key.drugId) { parametersOf(key.drugId) },
                        onBack = { backStack.removeLastOrNull() },
                        onEditDrug = { backStack.add(EditorKey(key.drugId)) },
                    )
                }
                entry<EditorKey> { key ->
                    EditorScreen(
                        viewModel =
                            koinViewModel(key = key.drugId ?: "new") {
                                parametersOf(key.drugId ?: "")
                            },
                        onBack = { backStack.removeLastOrNull() },
                        onCalculate = { savedId ->
                            backStack.removeLastOrNull() // закрыть редактор
                            val top = backStack.lastOrNull()
                            if (top !is CalculatorKey || top.drugId != savedId) {
                                backStack.add(CalculatorKey(savedId))
                            }
                        },
                        onSaved = { savedId ->
                            backStack.removeLastOrNull() // закрыть редактор
                            val editedId = key.drugId
                            // Правка встроенного препарата сохраняется копией в «Мои»:
                            // перенаправляем экран расчёта на новую карточку.
                            if (editedId != null && editedId != savedId) {
                                val top = backStack.lastOrNull()
                                if (top is CalculatorKey && top.drugId == editedId) {
                                    backStack.removeLastOrNull()
                                    backStack.add(CalculatorKey(savedId))
                                }
                            }
                        },
                    )
                }
                entry<SettingsKey> {
                    SettingsScreen(
                        viewModel = koinViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                        onOpenAbout = { backStack.add(AboutKey) },
                    )
                }
                entry<AboutKey> {
                    AboutScreen(
                        versionName = BuildConfig.VERSION_NAME,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<TciKey> {
                    TciScreen(
                        viewModel = koinViewModel(),
                        onBack = { backStack.removeLastOrNull() },
                        onOpenHelp = { backStack.add(TciModelsKey) },
                    )
                }
                entry<TciModelsKey> {
                    TciModelsScreen(onBack = { backStack.removeLastOrNull() })
                }
            },
    )
}
