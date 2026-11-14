package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import com.drwho174.infusioncalc.testutil.FakeDrugRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
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
    fun `editing a built-in drug updates it in place without a duplicate`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(builtIn()))
            val viewModel = EditorViewModel("norepinephrine", repository)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.valid)
            var savedId: String? = null
            viewModel.save { savedId = it }
            advanceUntilIdle()

            assertEquals("norepinephrine", savedId)
            val drugs = repository.observeDrugs().first()
            assertEquals("editing must not add a second row", 1, drugs.size)
            val updated = drugs.single()
            assertTrue(updated.isBuiltIn)
            assertNull(updated.derivedFromId)
            assertEquals(2, updated.revision)
        }

    @Test
    fun `editing the same built-in twice keeps a single row`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(builtIn()))

            EditorViewModel("norepinephrine", repository).apply {
                advanceUntilIdle()
                save { }
            }
            advanceUntilIdle()
            EditorViewModel("norepinephrine", repository).apply {
                advanceUntilIdle()
                save { }
            }
            advanceUntilIdle()

            assertEquals(1, repository.observeDrugs().first().size)
        }

    @Test
    fun `new drug is saved with the entered name`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository()
            val viewModel = EditorViewModel(null, repository)
            advanceUntilIdle()

            viewModel.onNameChange("Тестовый")
            viewModel.addPreset(BigDecimal("10"), AmountUnit.MG, BigDecimal("100"))
            advanceUntilIdle()

            var savedId: String? = null
            viewModel.save { savedId = it }
            advanceUntilIdle()

            assertNotNull(savedId)
            assertEquals("Тестовый", repository.getDrug(savedId!!)?.nameRu)
        }

    @Test
    fun `new concentration chip is added selected and saved`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository()
            val viewModel = EditorViewModel(null, repository)
            advanceUntilIdle()

            viewModel.onNameChange("Тестовый")
            viewModel.onNotesChange("Болюс медленно")
            assertFalse(viewModel.state.value.valid)

            viewModel.addPreset(BigDecimal("2"), AmountUnit.MG, BigDecimal("50"))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.valid)
            val preset =
                viewModel.state.value.presets
                    .single()
            assertEquals(preset.id, viewModel.state.value.selectedPresetId)

            var savedId: String? = null
            viewModel.save { savedId = it }
            advanceUntilIdle()

            val saved = repository.getDrug(savedId!!)!!
            assertEquals(1, saved.presets.size)
            assertTrue(saved.presets.single().isDefault)
            assertEquals(BigDecimal("2"), saved.presets.single().amount)
            assertEquals("Болюс медленно", saved.notes)
        }

    @Test
    fun `last concentration cannot be deleted`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository()
            val viewModel = EditorViewModel(null, repository)
            advanceUntilIdle()

            viewModel.addPreset(BigDecimal("2"), AmountUnit.MG, BigDecimal("50"))
            val id =
                viewModel.state.value.presets
                    .single()
                    .id
            viewModel.deletePreset(id)

            assertEquals(1, viewModel.state.value.presets.size)
        }

    @Test
    fun `new drug with an existing name is rejected`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(builtIn()))
            val viewModel = EditorViewModel(null, repository)
            advanceUntilIdle()

            viewModel.onNameChange("Норадреналин")
            viewModel.addPreset(BigDecimal("4"), AmountUnit.MG, BigDecimal("1000"))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.duplicateName)
            assertFalse(viewModel.state.value.valid)

            var savedId: String? = null
            viewModel.save { savedId = it }
            advanceUntilIdle()
            assertNull(savedId)
        }

    private fun builtIn() =
        Drug(
            id = "norepinephrine",
            nameRu = "Норадреналин",
            isBuiltIn = true,
            defaultSchemeId = "s1",
            schemes =
                listOf(
                    DoseScheme(
                        id = "s1",
                        drugId = "norepinephrine",
                        amountUnit = AmountUnit.MCG,
                        perKg = false,
                        perTime = TimeBase.PER_MINUTE,
                    ),
                ),
            presets =
                listOf(
                    ConcentrationPreset(
                        id = "p1",
                        drugId = "norepinephrine",
                        mode = ConcentrationMode.MASS_VOLUME,
                        amount = BigDecimal("4"),
                        amountUnit = AmountUnit.MG,
                        volumeMl = BigDecimal("1000"),
                        isDefault = true,
                    ),
                ),
        )
}
