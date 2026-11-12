package com.drwho174.infusioncalc.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.schemeLabel
import com.drwho174.infusioncalc.ui.viewmodel.CatalogFilter
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
            Row(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CatalogFilter.entries.forEach { entry ->
                    FilterChip(
                        selected = state.filter == entry,
                        onClick = { viewModel.onFilterChange(entry) },
                        label = { Text(stringResource(entry.titleRes)) },
                    )
                }
            }
            if (state.filtered.isEmpty()) {
                EmptyCatalogState(state.filter, onAddDrug)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.filtered, key = { it.id }) { drug ->
                        DrugListItem(
                            drug = drug,
                            isFavorite = drug.id in state.favorites,
                            onOpen = { onOpenDrug(drug.id) },
                        )
                        HorizontalDivider()
                    }
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
private fun EmptyCatalogState(
    filter: CatalogFilter,
    onAddDrug: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text =
                    stringResource(
                        when (filter) {
                            CatalogFilter.MINE -> R.string.empty_mine_title
                            CatalogFilter.FAVORITES -> R.string.empty_favorites_title
                            CatalogFilter.ALL -> R.string.empty_all_title
                        },
                    ),
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
private fun DrugListItem(
    drug: Drug,
    isFavorite: Boolean,
    onOpen: () -> Unit,
) {
    val scheme = drug.defaultScheme
    val summary = scheme?.let { schemeLabel(it.amountUnit, it.perKg, it.perTime) }.orEmpty()
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(drug.localizedName(isEnglishUi()), style = MaterialTheme.typography.titleMedium)
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isFavorite) {
            Text(
                stringResource(R.string.badge_favorite),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (!drug.isBuiltIn) {
            Text(
                stringResource(R.string.badge_mine),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
