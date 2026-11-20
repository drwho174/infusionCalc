package com.drwho174.infusioncalc.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.BuildConfig
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.data.settings.AppSettings
import com.drwho174.infusioncalc.ui.applyAppLanguage
import com.drwho174.infusioncalc.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenFolderSettings: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
        ) {
            SettingsCard(title = stringResource(R.string.theme)) {
                OptionRow(
                    title = stringResource(R.string.theme_system),
                    selected = current.theme == AppSettings.THEME_SYSTEM,
                    onSelect = { viewModel.setTheme(AppSettings.THEME_SYSTEM) },
                )
                OptionRow(
                    title = stringResource(R.string.theme_light),
                    selected = current.theme == AppSettings.THEME_LIGHT,
                    onSelect = { viewModel.setTheme(AppSettings.THEME_LIGHT) },
                )
                OptionRow(
                    title = stringResource(R.string.theme_dark),
                    selected = current.theme == AppSettings.THEME_DARK,
                    onSelect = { viewModel.setTheme(AppSettings.THEME_DARK) },
                )
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard(title = stringResource(R.string.language)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    OptionRow(
                        title = stringResource(R.string.language_system_short),
                        selected = current.language == AppSettings.LANGUAGE_SYSTEM,
                        onSelect = {
                            viewModel.setLanguage(AppSettings.LANGUAGE_SYSTEM)
                            applyAppLanguage(context, AppSettings.LANGUAGE_SYSTEM)
                        },
                    )
                    OptionRow(
                        title = stringResource(R.string.language_ru),
                        selected = current.language == AppSettings.LANGUAGE_RU,
                        onSelect = {
                            viewModel.setLanguage(AppSettings.LANGUAGE_RU)
                            applyAppLanguage(context, AppSettings.LANGUAGE_RU)
                        },
                    )
                    OptionRow(
                        title = stringResource(R.string.language_en),
                        selected = current.language == AppSettings.LANGUAGE_EN,
                        onSelect = {
                            viewModel.setLanguage(AppSettings.LANGUAGE_EN)
                            applyAppLanguage(context, AppSettings.LANGUAGE_EN)
                        },
                    )
                } else {
                    Text(
                        text = stringResource(R.string.language_system),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.language_api_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard(title = stringResource(R.string.folders)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenFolderSettings)
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.folder_settings),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SettingsCard(title = stringResource(R.string.about_title)) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenAbout)
                            .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_offline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            content()
        }
    }
}

@Composable
private fun OptionRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
