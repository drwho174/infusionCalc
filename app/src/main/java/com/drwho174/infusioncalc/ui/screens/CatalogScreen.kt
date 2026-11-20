@file:Suppress("TooManyFunctions")

package com.drwho174.infusioncalc.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.ui.components.AddDrugsToFolderDialog
import com.drwho174.infusioncalc.ui.display
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.viewmodel.CatalogUiState
import com.drwho174.infusioncalc.ui.viewmodel.CatalogViewModel
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("LongMethod", "LongParameterList")
fun CatalogScreen(
    viewModel: CatalogViewModel,
    onOpenDrug: (String) -> Unit,
    onOpenTci: () -> Unit,
    onAddDrug: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFolderSettings: () -> Unit = {},
    onEditWeight: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showAddDrugsDialog by remember { mutableStateOf(false) }
    var pendingDeleteFolder by remember { mutableStateOf<Folder?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    BackHandler { showExitDialog = true }

    Scaffold(
        topBar = { CatalogTopBar(onOpenSettings, onOpenFolderSettings, state.weight, onEditWeight) },
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
                onNewFolder = { showNewFolderDialog = true },
                onFolderLongPress = { pendingDeleteFolder = it },
            )
            CatalogBody(
                state = state,
                onOpenDrug = onOpenDrug,
                onOpenTci = onOpenTci,
                onAddDrug = onAddDrug,
                onAddToFolder = { showAddDrugsDialog = true },
            )
        }
    }

    if (showNewFolderDialog) {
        CreateFolderDialog(
            existingNames = state.folders.map { it.name },
            onCreate = {
                viewModel.onCreateFolder(it)
                showNewFolderDialog = false
            },
            onDismiss = { showNewFolderDialog = false },
        )
    }

    DeleteFolderConfirm(
        folder = pendingDeleteFolder,
        onConfirm = {
            pendingDeleteFolder?.let { viewModel.onDeleteFolder(it.id) }
            pendingDeleteFolder = null
        },
        onDismiss = { pendingDeleteFolder = null },
    )

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(stringResource(R.string.exit_title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        (context as? Activity)?.finish()
                    },
                ) {
                    Text(stringResource(R.string.exit_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
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
    onFolderLongPress: (Folder) -> Unit,
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
            FolderChip(
                folder = folder,
                selected = state.selectedFolderId == folder.id,
                onClick = { onSelect(folder.id) },
                onLongClick = { onFolderLongPress(folder) },
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
private fun FolderChip(
    folder: Folder,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        Text(
            text = folder.name,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun CatalogBody(
    state: CatalogUiState,
    onOpenDrug: (String) -> Unit,
    onOpenTci: () -> Unit,
    onAddDrug: () -> Unit,
    onAddToFolder: () -> Unit,
) {
    val currentFolder = state.selectedFolderId?.let { id -> state.folders.firstOrNull { it.id == id } }
    val folderSelected = currentFolder != null
    val showTci = state.selectedFolderId == null && tciMatchesQuery(state.query)
    if (state.filtered.isEmpty() && !folderSelected && !showTci) {
        EmptyCatalogState(onAddDrug = onAddDrug)
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (showTci) {
                item(key = "tci_entry") {
                    TciCatalogItem(onOpen = onOpenTci)
                    HorizontalDivider()
                }
            }
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

private fun tciMatchesQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim()
    return listOf("tci", "propofol", "пропофол").any { it.contains(q, ignoreCase = true) }
}

@Composable
private fun TciCatalogItem(onOpen: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.tci_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.tci_badge),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
                Modifier
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                    ).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogTopBar(
    onOpenSettings: () -> Unit,
    onOpenFolderSettings: () -> Unit = {},
    weight: BigDecimal? = null,
    onEditWeight: () -> Unit = {},
) {
    var menuExpanded by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.catalog_title)) },
        actions = {
            TextButton(onClick = onEditWeight) {
                Icon(
                    painter = painterResource(R.drawable.ic_weight),
                    contentDescription = stringResource(R.string.edit_weight),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.weight_button_value, weight?.display() ?: "—"),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            IconButton(onClick = onOpenSettings) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.settings),
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.folder_settings),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.folder_settings)) },
                        onClick = {
                            menuExpanded = false
                            onOpenFolderSettings()
                        },
                    )
                }
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
private fun CreateFolderDialog(
    existingNames: List<String>,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()
    val reserved = trimmed.lowercase() in Folder.RESERVED_NAMES
    val duplicate = trimmed.isNotEmpty() && !reserved && existingNames.any { it.equals(trimmed, ignoreCase = true) }
    val tooLong = trimmed.length > Folder.MAX_NAME_LENGTH
    val canCreate = trimmed.isNotEmpty() && !duplicate && !reserved && !tooLong

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_folder)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.folder_name)) },
                isError = duplicate || tooLong || reserved,
                supportingText = {
                    when {
                        reserved -> Text(stringResource(R.string.folder_reserved))
                        duplicate -> Text(stringResource(R.string.folder_exists))
                        tooLong -> Text(stringResource(R.string.folder_name_too_long))
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(trimmed) },
                enabled = canCreate,
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
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
