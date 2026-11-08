package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.testutil.FakeDrugRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `filters by mine and favorites`() =
        runTest(dispatcher) {
            val repository =
                FakeDrugRepository(
                    listOf(
                        Drug(id = "a", nameRu = "Адреналин", isBuiltIn = true),
                        Drug(id = "b", nameRu = "Мой препарат", isBuiltIn = false),
                    ),
                )
            val viewModel = CatalogViewModel(repository)
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            assertEquals(2, viewModel.state.value.filtered.size)

            viewModel.onFilterChange(CatalogFilter.MINE)
            advanceUntilIdle()
            assertEquals(
                listOf("b"),
                viewModel.state.value.filtered
                    .map { it.id },
            )

            repository.setFavorite("a", true)
            viewModel.onFilterChange(CatalogFilter.FAVORITES)
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                viewModel.state.value.filtered
                    .map { it.id },
            )

            job.cancel()
        }

    @Test
    fun `search matches russian and english names`() =
        runTest(dispatcher) {
            val repository =
                FakeDrugRepository(
                    listOf(
                        Drug(id = "a", nameRu = "Адреналин", nameEn = "Epinephrine", isBuiltIn = true),
                        Drug(id = "b", nameRu = "Допамин", nameEn = "Dopamine", isBuiltIn = true),
                    ),
                )
            val viewModel = CatalogViewModel(repository)
            val job = launch { viewModel.state.collect {} }

            viewModel.onQueryChange("epi")
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                viewModel.state.value.filtered
                    .map { it.id },
            )

            job.cancel()
        }
}
