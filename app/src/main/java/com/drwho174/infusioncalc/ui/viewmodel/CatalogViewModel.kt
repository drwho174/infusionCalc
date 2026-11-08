package com.drwho174.infusioncalc.ui.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drwho174.infusioncalc.R
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.repository.DrugRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class CatalogFilter(
    @StringRes val titleRes: Int,
) {
    ALL(R.string.filter_all),
    FAVORITES(R.string.filter_favorites),
    MINE(R.string.filter_mine),
}

data class CatalogUiState(
    val filtered: List<Drug> = emptyList(),
    val favorites: Set<String> = emptySet(),
    val query: String = "",
    val filter: CatalogFilter = CatalogFilter.ALL,
)

/** Список препаратов с поиском и фильтрами (все / избранное / мои). */
class CatalogViewModel(
    private val repository: DrugRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(CatalogFilter.ALL)

    val state: StateFlow<CatalogUiState> =
        combine(
            repository.observeDrugs(),
            repository.observeFavorites(),
            query,
            filter,
        ) { drugs, favorites, currentQuery, currentFilter ->
            val filtered =
                drugs.filter { drug ->
                    val matchesQuery =
                        currentQuery.isBlank() ||
                            drug.nameRu.contains(currentQuery, ignoreCase = true) ||
                            (drug.nameEn?.contains(currentQuery, ignoreCase = true) == true)
                    val matchesFilter =
                        when (currentFilter) {
                            CatalogFilter.ALL -> true
                            CatalogFilter.FAVORITES -> drug.id in favorites
                            CatalogFilter.MINE -> !drug.isBuiltIn
                        }
                    matchesQuery && matchesFilter
                }
            CatalogUiState(
                filtered = filtered,
                favorites = favorites,
                query = currentQuery,
                filter = currentFilter,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilterChange(value: CatalogFilter) {
        filter.value = value
    }
}
