package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.data.settings.AppSettings
import com.drwho174.infusioncalc.ui.viewmodel.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: AppSettings()

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
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleMedium)
            ThemeOption(
                title = stringResource(R.string.theme_system),
                selected = current.theme == AppSettings.THEME_SYSTEM,
                onSelect = { viewModel.setTheme(AppSettings.THEME_SYSTEM) },
            )
            ThemeOption(
                title = stringResource(R.string.theme_light),
                selected = current.theme == AppSettings.THEME_LIGHT,
                onSelect = { viewModel.setTheme(AppSettings.THEME_LIGHT) },
            )
            ThemeOption(
                title = stringResource(R.string.theme_dark),
                selected = current.theme == AppSettings.THEME_DARK,
                onSelect = { viewModel.setTheme(AppSettings.THEME_DARK) },
            )

            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.language_system),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.version), style = MaterialTheme.typography.bodySmall)
            Text(
                stringResource(R.string.settings_offline),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ThemeOption(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}
