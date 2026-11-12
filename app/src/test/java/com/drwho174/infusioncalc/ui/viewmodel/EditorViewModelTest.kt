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
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
    fun `editing a built-in drug saves a copy in mine`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(builtIn()))
            val viewModel = EditorViewModel("norepinephrine", repository)
            advanceUntilIdle()

            assertTrue(viewModel.state.value.valid)
            var savedId: String? = null
            viewModel.save { savedId = it }
            advanceUntilIdle()

            assertNotNull(savedId)
            assertNotEquals("norepinephrine", savedId)

            val copy = repository.getDrug(savedId!!)
            assertNotNull(copy)
            assertFalse(copy!!.isBuiltIn)
            assertEquals("norepinephrine", copy.derivedFromId)

            val original = repository.getDrug("norepinephrine")
            assertTrue(original!!.isBuiltIn)
        }

    @Test
    fun `new drug is saved with the entered name`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository()
            val viewModel = EditorViewModel(null, repository)
            advanceUntilIdle()

            viewModel.onNameChange("Тестовый")
            viewModel.onMassChange("10")
            viewModel.onVolumeChange("100")
            advanceUntilIdle()

            var savedId: String? = null
            viewModel.save { savedId = it }
            advanceUntilIdle()

            assertNotNull(savedId)
            assertEquals("Тестовый", repository.getDrug(savedId!!)?.nameRu)
        }

    @Test
    fun `new drug with an existing name is rejected`() =
        runTest(dispatcher) {
            val repository = FakeDrugRepository(listOf(builtIn()))
            val viewModel = EditorViewModel(null, repository)
            advanceUntilIdle()

            viewModel.onNameChange("Норадреналин")
            viewModel.onMassChange("4")
            viewModel.onVolumeChange("1000")
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
