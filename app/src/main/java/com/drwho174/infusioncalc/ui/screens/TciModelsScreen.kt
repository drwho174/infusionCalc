package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.ui.components.MarkdownText
import com.drwho174.infusioncalc.ui.isEnglishUi

/**
 * Справка по моделям TCI: markdown из `assets/about/<язык>/TCI_Models.md`.
 * Если перевода нет, берётся английский вариант (как в Calc1).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TciModelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val language = if (isEnglishUi()) "en" else "ru"
    val markdown =
        remember(language) {
            val candidates =
                listOf(
                    "about/$language/TCI_Models.md",
                    "about/en/TCI_Models.md",
                    "about/ru/TCI_Models.md",
                )
            candidates
                .firstNotNullOfOrNull { path ->
                    runCatching {
                        context.assets
                            .open(path)
                            .bufferedReader()
                            .use { it.readText() }
                    }.getOrNull()
                }.orEmpty()
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tci_models_title)) },
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
            MarkdownText(markdown = markdown)
        }
    }
}
