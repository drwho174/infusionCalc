package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.domain.WeightStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class WeightViewModelTest {
    @Test
    fun `valid weight is accepted and committed`() {
        val store = WeightStore()
        val viewModel = WeightViewModel(store)

        viewModel.onTextChange("70")
        assertTrue(viewModel.state.value.valid)

        viewModel.commit()
        assertEquals(0, BigDecimal("70").compareTo(store.weight.value))
    }

    @Test
    fun `current weight is prefilled for editing`() {
        val store = WeightStore().apply { set(BigDecimal("70")) }
        val viewModel = WeightViewModel(store)

        assertEquals("70", viewModel.state.value.text)
        assertTrue(viewModel.state.value.valid)
    }

    @Test
    fun `zero weight is invalid`() {
        val viewModel = WeightViewModel(WeightStore())
        viewModel.onTextChange("0")
        assertFalse(viewModel.state.value.valid)
    }

    @Test
    fun `weight above the technical limit is invalid`() {
        val viewModel = WeightViewModel(WeightStore())
        viewModel.onTextChange("301")
        assertFalse(viewModel.state.value.valid)
    }

    @Test
    fun `invalid input is not committed`() {
        val store = WeightStore()
        val viewModel = WeightViewModel(store)
        viewModel.onTextChange("abc")
        viewModel.commit()
        assertNull(store.weight.value)
    }

    @Test
    fun `comma separator is normalized`() {
        val viewModel = WeightViewModel(WeightStore())
        viewModel.onTextChange("70,5")
        assertEquals("70.5", viewModel.state.value.text)
        assertTrue(viewModel.state.value.valid)
    }
}
