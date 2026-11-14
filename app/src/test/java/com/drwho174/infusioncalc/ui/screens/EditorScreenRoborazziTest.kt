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
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.drwho174.infusioncalc.ui.viewmodel.EditorViewModel
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.math.BigDecimal

/**
 * Скриншот-проверка Roborazzi: список концентраций в редакторе не сжимает
 * подписи до одной буквы и кнопки «Изменить»/«Удалить» помещаются в строку.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi", application = android.app.Application::class)
class EditorScreenRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorConcentrationListFitsTheRow() {
        val drug = drug()
        val viewModel = EditorViewModel(drug.id, FakeDrugRepository(listOf(drug)))

        composeRule.setContent {
            InfusionCalcTheme {
                EditorScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onSaved = {},
                    onCalculate = {},
                )
            }
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Concentrations").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onRoot().captureRoboImage("src/test/screenshots/editor_screen.png")
    }

    private fun drug() =
        Drug(
            id = "dopamine",
            nameRu = "Допамин",
            isBuiltIn = true,
            defaultSchemeId = "s",
            schemes =
                listOf(
                    DoseScheme(
                        id = "s",
                        drugId = "dopamine",
                        amountUnit = AmountUnit.MCG,
                        perKg = true,
                        perTime = TimeBase.PER_MINUTE,
                        doseMin = BigDecimal("2"),
                        doseMax = BigDecimal("20"),
                    ),
                ),
            presets =
                listOf(
                    preset("p1", "1", "20", default = true),
                    preset("p2", "3", "20"),
                    preset("p3", "5", "20"),
                ),
        )

    private fun preset(
        id: String,
        amount: String,
        volume: String,
        default: Boolean = false,
    ) = ConcentrationPreset(
        id = id,
        drugId = "dopamine",
        mode = ConcentrationMode.MASS_VOLUME,
        amount = BigDecimal(amount),
        amountUnit = AmountUnit.MG,
        volumeMl = BigDecimal(volume),
        isDefault = default,
    )
}
