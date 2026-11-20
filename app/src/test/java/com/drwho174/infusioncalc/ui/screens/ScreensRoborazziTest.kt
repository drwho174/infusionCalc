package com.drwho174.infusioncalc.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.drwho174.infusioncalc.data.seed.CatalogSeed
import com.drwho174.infusioncalc.data.settings.SettingsRepository
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.notif.NoopTciNotifier
import com.drwho174.infusioncalc.testutil.FakeDrugRepository
import com.drwho174.infusioncalc.testutil.FakeFolderRepository
import com.drwho174.infusioncalc.testutil.FakeTciSessionStore
import com.drwho174.infusioncalc.ui.about.AboutScreen
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.drwho174.infusioncalc.ui.viewmodel.AppViewModel
import com.drwho174.infusioncalc.ui.viewmodel.CalculatorViewModel
import com.drwho174.infusioncalc.ui.viewmodel.FolderSettingsViewModel
import com.drwho174.infusioncalc.ui.viewmodel.TciViewModel
import com.drwho174.infusioncalc.ui.viewmodel.WeightViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/** Скриншоты всех экранов приложения для поиска ошибок верстки. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi", application = android.app.Application::class)
class ScreensRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun disclaimerScreen() {
        composeRule.setContent {
            InfusionCalcTheme { DisclaimerScreen(onAccept = {}) }
        }
        composeRule.waitForIdle()
        capture("disclaimer.png")
    }

    @Test
    fun weightScreen() {
        val viewModel = WeightViewModel(WeightStore())
        composeRule.setContent {
            InfusionCalcTheme {
                WeightScreen(viewModel = viewModel, onContinue = {})
            }
        }
        composeRule.waitForIdle()
        capture("weight.png")
    }

    @Test
    fun settingsScreen() {
        val repository = SettingsRepository(RuntimeEnvironment.getApplication())
        val viewModel = AppViewModel(repository)
        composeRule.setContent {
            InfusionCalcTheme {
                SettingsScreen(viewModel = viewModel, onBack = {}, onOpenAbout = {}, onOpenFolderSettings = {})
            }
        }
        waitFor("Settings")
        capture("settings.png")
    }

    @Test
    fun calculatorScreen() {
        val drug = CatalogSeed.drugs().first()
        val weight = WeightStore().apply { set(BigDecimal("70")) }
        val viewModel =
            CalculatorViewModel(
                drugId = drug.id,
                repository = FakeDrugRepository(listOf(drug)),
                weightStore = weight,
                folderRepository = FakeFolderRepository(),
            )
        composeRule.setContent {
            InfusionCalcTheme {
                CalculatorScreen(viewModel = viewModel, onBack = {}, onEditDrug = {})
            }
        }
        waitFor(drug.nameEn ?: drug.nameRu)
        capture("calculator.png")
    }

    @Test
    fun tciScreen() {
        val weight = WeightStore().apply { set(BigDecimal("70")) }
        val viewModel =
            TciViewModel(
                store = FakeTciSessionStore(disclaimerHidden = true),
                weightStore = weight,
                notifier = NoopTciNotifier,
                clock = { 0L },
            )
        viewModel.onTargetChange("4")
        composeRule.setContent {
            InfusionCalcTheme {
                TciScreen(viewModel = viewModel, onBack = {}, onOpenHelp = {})
            }
        }
        waitFor("Propofol")
        capture("tci.png")
    }

    @Test
    fun tciModelsScreen() {
        composeRule.setContent {
            InfusionCalcTheme { TciModelsScreen(onBack = {}) }
        }
        waitFor("TCI models")
        capture("tci_models.png")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h740dp-xhdpi")
    fun disclaimerScreenRu() {
        composeRule.setContent {
            InfusionCalcTheme { DisclaimerScreen(onAccept = {}) }
        }
        composeRule.waitForIdle()
        capture("disclaimer_ru.png")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h740dp-xhdpi")
    fun calculatorScreenRu() {
        val drug = CatalogSeed.drugs().first()
        val weight = WeightStore().apply { set(BigDecimal("70")) }
        val viewModel =
            CalculatorViewModel(
                drugId = drug.id,
                repository = FakeDrugRepository(listOf(drug)),
                weightStore = weight,
                folderRepository = FakeFolderRepository(),
            )
        composeRule.setContent {
            InfusionCalcTheme {
                CalculatorScreen(viewModel = viewModel, onBack = {}, onEditDrug = {})
            }
        }
        waitFor(drug.nameRu)
        capture("calculator_ru.png")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h740dp-xhdpi")
    fun tciScreenRu() {
        val weight = WeightStore().apply { set(BigDecimal("70")) }
        val viewModel =
            TciViewModel(
                store = FakeTciSessionStore(disclaimerHidden = true),
                weightStore = weight,
                notifier = NoopTciNotifier,
                clock = { 0L },
            )
        viewModel.onTargetChange("4")
        composeRule.setContent {
            InfusionCalcTheme {
                TciScreen(viewModel = viewModel, onBack = {}, onOpenHelp = {})
            }
        }
        waitFor("Пропофол")
        capture("tci_ru.png")
    }

    @Test
    fun aboutScreen() {
        composeRule.setContent {
            InfusionCalcTheme { AboutScreen(versionName = "0.1.0", onBack = {}) }
        }
        waitFor("About")
        capture("about.png")
    }

    @Test
    @Config(qualifiers = "ru-rRU-w360dp-h740dp-xhdpi")
    fun aboutScreenRu() {
        composeRule.setContent {
            InfusionCalcTheme { AboutScreen(versionName = "0.1.0", onBack = {}) }
        }
        waitFor("О программе")
        capture("about_ru.png")
    }

    @Test
    fun folderSettingsScreen() {
        val repository =
            FakeFolderRepository(
                listOf(
                    Folder(id = "1", name = "Инфузии"),
                    Folder(id = "2", name = "Антибиотики"),
                ),
            )
        val viewModel = FolderSettingsViewModel(repository)
        composeRule.setContent {
            InfusionCalcTheme {
                FolderSettingsScreen(viewModel = viewModel, onBack = {})
            }
        }
        waitFor("Folder settings")
        capture("folder_settings.png")
    }

    private fun waitFor(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun capture(name: String) {
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name")
    }
}
