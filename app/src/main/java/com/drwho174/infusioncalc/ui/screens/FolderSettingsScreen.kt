package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.ui.components.FolderEditDialog
import com.drwho174.infusioncalc.ui.viewmodel.FolderSettingsViewModel

/** Настройки папок: список и удаление с подтверждением. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod")
fun FolderSettingsScreen(
    viewModel: FolderSettingsViewModel,
    onBack: () -> Unit,
) {
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Folder?>(null) }
    var editingFolder by remember { mutableStateOf<Folder?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.folder_settings)) },
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
        if (folders.isEmpty()) {
            Text(
                text = stringResource(R.string.folders_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(innerPadding).padding(24.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding =
                androidx.compose.foundation.layout
                    .PaddingValues(vertical = 8.dp),
        ) {
            items(folders, key = { it.id }) { folder ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { editingFolder = folder }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = stringResource(R.string.rename_folder),
                        )
                    }
                    IconButton(onClick = { pendingDelete = folder }) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                        )
                    }
                }
            }
        }
    }

    val folderToEdit = editingFolder
    if (folderToEdit != null) {
        FolderEditDialog(
            folder = folderToEdit,
            existingNames = folders.map { it.name },
            onRename = { name ->
                viewModel.rename(folderToEdit.id, name)
                editingFolder = null
            },
            onDelete = {
                editingFolder = null
                pendingDelete = folderToEdit
            },
            onDismiss = { editingFolder = null },
        )
    }

    val folder = pendingDelete
    if (folder != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_folder_title)) },
            text = { Text(stringResource(R.string.delete_folder_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(folder.id)
                        pendingDelete = null
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
