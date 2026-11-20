package com.drwho174.infusioncalc.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.testutil.FakeDrugRepository
import com.drwho174.infusioncalc.testutil.FakeFolderRepository
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.drwho174.infusioncalc.ui.viewmodel.CatalogViewModel
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Удаление папки долгим нажатием на чипс. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi", application = android.app.Application::class)
class CatalogFolderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressFolderOpensDeleteDialogAndDeletes() {
        val folderRepository = FakeFolderRepository(listOf(Folder(id = "f1", name = "Infusions")))
        val viewModel = CatalogViewModel(FakeDrugRepository(), folderRepository, WeightStore())

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
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Infusions").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Infusions").performTouchInput { longClick() }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Delete folder?").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Delete").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Infusions").fetchSemanticsNodes().isEmpty()
        }
        assertTrue(composeRule.onAllNodesWithText("Infusions").fetchSemanticsNodes().isEmpty())
    }
}
