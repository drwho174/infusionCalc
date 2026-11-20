package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.ui.theme.InfusionCalcTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Макеты вариантов для выбора: кнопка веса в appbar и иконка уведомления. */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h740dp-xhdpi", application = android.app.Application::class)
class VariantPreviewRoborazziTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun weightButtonVariants() {
        composeRule.setContent {
            InfusionCalcTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Вариант A — icon button", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        TopAppBar(
                            title = { Text("Препараты") },
                            actions = {
                                IconButton(onClick = {}) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Изменить вес")
                                }
                                IconButton(onClick = {}) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                                }
                            },
                        )

                        Spacer(Modifier.height(24.dp))
                        Text("Вариант B — иконка с текстом", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        TopAppBar(
                            title = { Text("Препараты") },
                            actions = {
                                TextButton(onClick = {}) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(" 70 кг")
                                }
                                IconButton(onClick = {}) {
                                    Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                                }
                            },
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/variant_weight_button.png")
    }

    @Test
    fun notificationIconVariants() {
        composeRule.setContent {
            InfusionCalcTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text("Вариант A — иконка приложения", style = MaterialTheme.typography.labelLarge)
                        NotificationMock(iconRes = R.mipmap.ic_launcher_foreground)

                        Text("Вариант B — значок TCI", style = MaterialTheme.typography.labelLarge)
                        NotificationMock(iconRes = R.drawable.ic_tci_notification)
                    }
                }
            }
        }
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/variant_notification_icons.png")
    }

    @Composable
    private fun NotificationMock(iconRes: Int) {
        Surface(
            color = Color(0xFF1C1B1F),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(Color(0xFF49454F), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(Color.White),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Калькулятор инфузии",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Text(
                        "Cp 5.37 мкг/мл · коррекция через 09:01",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCAC4D0),
                    )
                }
                Text("22:07", style = MaterialTheme.typography.labelSmall, color = Color(0xFFCAC4D0))
            }
        }
    }
}
