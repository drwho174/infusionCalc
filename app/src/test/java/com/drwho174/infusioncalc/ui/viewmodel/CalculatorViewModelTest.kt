package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.domain.WeightStore
import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
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
    fun `dose is converted to infusion rate`() =
        runTest(dispatcher) {
            val viewModel =
                CalculatorViewModel(
                    "norepinephrine",
                    FakeDrugRepository(listOf(drug())),
                    WeightStore(),
                    FakeFolderRepository(),
                )
            val job = launch { viewModel.state.collect {} }

            viewModel.onDoseChange("8")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertTrue(state.isLoaded)
            assertEquals(0, BigDecimal("120").compareTo(state.resultValue))
            assertTrue(state.resultIsRate)
            job.cancel()
        }

    @Test
    fun `adding a concentration saves it and selects it`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(drug()))
            val viewModel =
                CalculatorViewModel(
                    "norepinephrine",
                    repository,
                    WeightStore(),
                    FakeFolderRepository(),
                )
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            viewModel.addPreset(BigDecimal("8"), AmountUnit.MG, BigDecimal("50"))
            advanceUntilIdle()

            val saved = repository.getDrug("norepinephrine")!!
            assertEquals(2, saved.presets.size)
            assertEquals(
                saved.presets.last().id,
                viewModel.state.value.preset
                    ?.id,
            )
            job.cancel()
        }

    @Test
    fun `rate is converted back to dose`() =
        runTest(dispatcher) {
            val viewModel =
                CalculatorViewModel(
                    "norepinephrine",
                    FakeDrugRepository(listOf(drug())),
                    WeightStore(),
                    FakeFolderRepository(),
                )
            val job = launch { viewModel.state.collect {} }

            viewModel.onRateChange("120")
            advanceUntilIdle()

            val state = viewModel.state.value
            assertEquals(0, BigDecimal("8").compareTo(state.resultValue))
            assertFalse(state.resultIsRate)
            job.cancel()
        }

    @Test
    fun `dose above range is flagged`() =
        runTest(dispatcher) {
            val viewModel =
                CalculatorViewModel(
                    "norepinephrine",
                    FakeDrugRepository(listOf(drug())),
                    WeightStore(),
                    FakeFolderRepository(),
                )
            val job = launch { viewModel.state.collect {} }

            viewModel.onDoseChange("5")
            advanceUntilIdle()

            assertTrue(viewModel.state.value.aboveRange)
            job.cancel()
        }

    @Test
    fun `delete removes a user-created drug`() =
        runTest(dispatcher) {
            val userDrug = drug().copy(id = "custom", isBuiltIn = false)
            val repository = FakeDrugRepository(listOf(userDrug))
            val viewModel = CalculatorViewModel("custom", repository, WeightStore(), FakeFolderRepository())
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            var deleted = false
            viewModel.delete { deleted = true }
            advanceUntilIdle()

            assertTrue(deleted)
            assertNull(repository.getDrug("custom"))
            job.cancel()
        }

    @Test
    fun `delete does not remove a built-in drug`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(drug()))
            val viewModel = CalculatorViewModel("norepinephrine", repository, WeightStore(), FakeFolderRepository())
            val job = launch { viewModel.state.collect {} }
            advanceUntilIdle()

            var deleted = false
            viewModel.delete { deleted = true }
            advanceUntilIdle()

            assertFalse(deleted)
            assertNotNull(repository.getDrug("norepinephrine"))
            job.cancel()
        }

    private fun drug() =
        Drug(
            id = "norepinephrine",
            nameRu = "Норадреналин",
            isBuiltIn = true,
            defaultSchemeId = "norepinephrine_scheme",
            schemes =
                listOf(
                    DoseScheme(
                        id = "norepinephrine_scheme",
                        drugId = "norepinephrine",
                        amountUnit = AmountUnit.MCG,
                        perKg = false,
                        perTime = TimeBase.PER_MINUTE,
                        doseMin = BigDecimal("0.01"),
                        doseMax = BigDecimal("3"),
                    ),
                ),
            presets =
                listOf(
                    ConcentrationPreset(
                        id = "norepinephrine_p1",
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
