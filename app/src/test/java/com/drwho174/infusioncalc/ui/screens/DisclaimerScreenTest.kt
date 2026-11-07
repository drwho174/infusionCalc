package com.drwho174.infusioncalc.ui.screens

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class DisclaimerScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun acceptButtonInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            InfusionCalcTheme {
                DisclaimerScreen(onAccept = { clicked = true })
            }
        }
        val context = RuntimeEnvironment.getApplication()
        composeRule.onNodeWithText(context.getString(com.drwho174.infusioncalc.R.string.disclaimer_accept))
            .performClick()
        assertTrue(clicked)
    }

    @Test
    fun showsTitleAndBody() {
        composeRule.setContent {
            InfusionCalcTheme {
                DisclaimerScreen(onAccept = {})
            }
        }
        val context = RuntimeEnvironment.getApplication()
        composeRule.onNodeWithText(context.getString(com.drwho174.infusioncalc.R.string.disclaimer_title))
            .assertExists()
        composeRule.onNodeWithText(context.getString(com.drwho174.infusioncalc.R.string.disclaimer_body))
            .assertExists()
    }
}
