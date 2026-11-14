package com.drwho174.infusioncalc.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import com.drwho174.infusioncalc.testutil.FakeDrugRepository
import com.drwho174.infusioncalc.testutil.FakeFolderRepository
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.drwho174.infusioncalc.ui.viewmodel.CatalogViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/**
 * Скриншот-проверка Roborazzi: в списке препаратов видны только названия —
 * без схем дозирования, концентраций и бейджей.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = android.app.Application::class)
class CatalogListRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogListShowsOnlyDrugNames() {
        val repository =
            FakeDrugRepository(
                listOf(
                    drug("a", "Адреналин", builtIn = true),
                    drug("b", "Мой препарат", builtIn = false),
                ),
            )
        val viewModel = CatalogViewModel(repository, FakeFolderRepository())

        composeRule.setContent {
            InfusionCalcTheme {
                CatalogScreen(
                    viewModel = viewModel,
                    onOpenDrug = {},
                    onOpenTci = {},
                    onAddDrug = {},
                    onOpenSettings = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Адреналин").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/catalog_list.png")
    }

    private fun drug(
        id: String,
        name: String,
        builtIn: Boolean,
    ) = Drug(
        id = id,
        nameRu = name,
        isBuiltIn = builtIn,
        defaultSchemeId = "${id}_s",
        schemes =
            listOf(
                DoseScheme(
                    id = "${id}_s",
                    drugId = id,
                    amountUnit = AmountUnit.MCG,
                    perKg = false,
                    perTime = TimeBase.PER_MINUTE,
                    doseMin = BigDecimal("0.1"),
                    doseMax = BigDecimal("1"),
                ),
            ),
        presets =
            listOf(
                ConcentrationPreset(
                    id = "${id}_p",
                    drugId = id,
                    mode = ConcentrationMode.MASS_VOLUME,
                    amount = BigDecimal("4"),
                    amountUnit = AmountUnit.MG,
                    volumeMl = BigDecimal("1000"),
                    isDefault = true,
                ),
            ),
    )
}
