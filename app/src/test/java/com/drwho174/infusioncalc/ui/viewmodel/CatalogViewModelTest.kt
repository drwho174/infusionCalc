package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.Folder
import com.drwho174.infusioncalc.testutil.FakeDrugRepository
import com.drwho174.infusioncalc.testutil.FakeFolderRepository
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
    fun `filters drugs by folder`() =
        runTest(dispatcher) {
            val repository =
                FakeDrugRepository(
                    listOf(
                        Drug(id = "a", nameRu = "Адреналин", isBuiltIn = true),
                        Drug(id = "b", nameRu = "Допамин", isBuiltIn = true),
                    ),
                )
            val folderRepository = FakeFolderRepository(listOf(Folder(id = "f1", name = "ОРИТ")))
            val viewModel = CatalogViewModel(repository, folderRepository)
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.filtered.size)

            folderRepository.setDrugFolders("a", setOf("f1"))
            viewModel.onFolderSelected("f1")
            advanceUntilIdle()
            assertEquals(
                listOf("a"),
                viewModel.state.value.filtered
                    .map { it.id },
            )

            viewModel.onFolderSelected(null)
            advanceUntilIdle()
            assertEquals(2, viewModel.state.value.filtered.size)

            job.cancel()
        }

    @Test
    fun `creates folder and selects it`() =
        runTest(dispatcher) {
            val folderRepository = FakeFolderRepository()
            val viewModel = CatalogViewModel(FakeDrugRepository(), folderRepository)
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            viewModel.onCreateFolder("ОРИТ")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(listOf("ОРИТ"), state.folders.map { it.name })
            assertEquals(state.folders.first().id, state.selectedFolderId)

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
            val viewModel = CatalogViewModel(repository, FakeFolderRepository())
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
