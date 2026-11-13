package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.ui.components.AddDrugsToFolderDialog
import com.drwho174.infusioncalc.ui.components.FolderNameField
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.viewmodel.CatalogUiState
import com.drwho174.infusioncalc.ui.viewmodel.CatalogViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onOpenDrug: (String) -> Unit,
    onAddDrug: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showFolderDialog by remember { mutableStateOf(false) }
    var showAddDrugsDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { CatalogTopBar(onOpenSettings) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDrug) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_drug),
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.catalog_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            CatalogChips(
                state = state,
                onSelect = viewModel::onFolderSelected,
                onNewFolder = { showFolderDialog = true },
            )
            CatalogBody(
                state = state,
                onOpenDrug = onOpenDrug,
                onAddDrug = onAddDrug,
                onAddToFolder = { showAddDrugsDialog = true },
            )
        }
    }

    if (showFolderDialog) {
        FolderDialog(
            folders = state.folders,
            onCreate = viewModel::onCreateFolder,
            onDelete = viewModel::onDeleteFolder,
            onDismiss = { showFolderDialog = false },
        )
    }

    val selectedFolder = state.selectedFolderId?.let { id -> state.folders.firstOrNull { it.id == id } }
    if (showAddDrugsDialog && selectedFolder != null) {
        AddDrugsToFolderDialog(
            folderName = selectedFolder.name,
            drugs = state.availableForFolder,
            onDismiss = { showAddDrugsDialog = false },
            onConfirm = { drugIds ->
                viewModel.onAddDrugsToFolder(selectedFolder.id, drugIds)
                showAddDrugsDialog = false
            },
        )
    }
}

@Composable
private fun CatalogChips(
    state: CatalogUiState,
    onSelect: (String?) -> Unit,
    onNewFolder: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.selectedFolderId == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.folder_all)) },
        )
        state.folders.forEach { folder ->
            FilterChip(
                selected = state.selectedFolderId == folder.id,
                onClick = { onSelect(folder.id) },
                label = { Text(folder.name) },
            )
        }
        AssistChip(
            onClick = onNewFolder,
            label = { Text(stringResource(R.string.new_folder)) },
            leadingIcon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
        )
    }
}

@Composable
private fun CatalogBody(
    state: CatalogUiState,
    onOpenDrug: (String) -> Unit,
    onAddDrug: () -> Unit,
    onAddToFolder: () -> Unit,
) {
    val currentFolder = state.selectedFolderId?.let { id -> state.folders.firstOrNull { it.id == id } }
    val folderSelected = currentFolder != null
    if (state.filtered.isEmpty() && !folderSelected) {
        EmptyCatalogState(onAddDrug = onAddDrug)
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(state.filtered, key = { it.id }) { drug ->
                DrugListItem(drug = drug, onOpen = { onOpenDrug(drug.id) })
                HorizontalDivider()
            }
            if (folderSelected) {
                item(key = "add_to_folder") {
                    AddDrugToFolderItem(onClick = onAddToFolder)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTopBar(onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text(stringResource(R.string.catalog_title)) },
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                )
            }
        },
    )
}

@Composable
private fun EmptyCatalogState(onAddDrug: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.empty_all_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.empty_body),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onAddDrug) { Text(stringResource(R.string.add_drug)) }
        }
    }
}

@Composable
private fun AddDrugToFolderItem(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.add_drug),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun FolderDialog(
    folders: List<Folder>,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<Folder?>(null) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.folders_manage_title)) },
        text = {
            Column {
                FolderNameField(
                    name = name,
                    onNameChange = { name = it },
                    existingNames = folders.map { it.name },
                    onCreate = {
                        onCreate(trimmed)
                        name = ""
                    },
                )
                if (folders.isNotEmpty()) {
                    FolderManageList(folders = folders, onDeleteClick = { pendingDelete = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        },
    )

    DeleteFolderConfirm(
        folder = pendingDelete,
        onConfirm = {
            pendingDelete?.let { onDelete(it.id) }
            pendingDelete = null
        },
        onDismiss = { pendingDelete = null },
    )
}

@Composable
private fun FolderManageList(
    folders: List<Folder>,
    onDeleteClick: (Folder) -> Unit,
) {
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    folders.forEach { folder ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(folder.name, modifier = Modifier.weight(1f))
            TextButton(onClick = { onDeleteClick(folder) }) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

@Composable
private fun DeleteFolderConfirm(
    folder: Folder?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (folder == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_folder_title)) },
        text = { Text(stringResource(R.string.delete_folder_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun DrugListItem(
    drug: Drug,
    onOpen: () -> Unit,
) {
    Text(
        text = drug.localizedName(isEnglishUi()),
        style = MaterialTheme.typography.titleMedium,
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
