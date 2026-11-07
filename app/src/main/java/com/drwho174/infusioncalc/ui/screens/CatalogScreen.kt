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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.ui.displayLabel
import com.drwho174.infusioncalc.ui.isEnglishUi
import com.drwho174.infusioncalc.ui.rememberAppContainer
import com.drwho174.infusioncalc.ui.schemeLabel

private enum class CatalogFilter(val titleRes: Int) {
    ALL(R.string.filter_all),
    FAVORITES(R.string.filter_favorites),
    MINE(R.string.filter_mine),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onOpenDrug: (String) -> Unit,
    onAddDrug: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = rememberAppContainer()
    val drugs by container.drugRepository.observeDrugs().collectAsState(initial = emptyList())
    val favorites by container.drugRepository.observeFavorites().collectAsState(initial = emptySet())

    var query by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(CatalogFilter.ALL.name) }
    val filter = CatalogFilter.valueOf(filterName)

    val filtered = remember(drugs, favorites, query, filter) {
        drugs.filter { drug ->
            val matchesQuery = query.isBlank() ||
                drug.nameRu.contains(query, ignoreCase = true) ||
                (drug.nameEn?.contains(query, ignoreCase = true) == true)
            val matchesFilter = when (filter) {
                CatalogFilter.ALL -> true
                CatalogFilter.FAVORITES -> drug.id in favorites
                CatalogFilter.MINE -> !drug.isBuiltIn
            }
            matchesQuery && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.catalog_title)) },
                actions = {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings)) }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddDrug) {
                Text(stringResource(R.string.add))
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
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
                        selected = filter == entry,
                        onClick = { filterName = entry.name },
                        label = { Text(stringResource(entry.titleRes)) },
                    )
                }
            }
            if (filtered.isEmpty()) {
                EmptyCatalogState(filter, onAddDrug)
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filtered, key = { it.id }) { drug ->
                        DrugListItem(
                            drug = drug,
                            isFavorite = drug.id in favorites,
                            onOpen = { onOpenDrug(drug.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCatalogState(filter: CatalogFilter, onAddDrug: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(
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
private fun DrugListItem(drug: Drug, isFavorite: Boolean, onOpen: () -> Unit) {
    val scheme = drug.defaultScheme
    val preset = drug.defaultPreset
    val summary = buildString {
        if (scheme != null) {
            append(schemeLabel(scheme.amountUnit, scheme.perKg, scheme.perTime))
        }
        if (preset != null) {
            if (isNotEmpty()) append(" · ")
            append(preset.displayLabel())
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(drug.localizedName(isEnglishUi()), style = MaterialTheme.typography.titleMedium)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
