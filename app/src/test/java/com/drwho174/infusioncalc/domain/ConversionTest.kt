package com.drwho174.infusioncalc.domain

import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.convertAmountUnit
import com.drwho174.infusioncalc.engine.AmountUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class ConversionTest {
    private fun assertDecimal(
        expected: String,
        actual: BigDecimal?,
    ) {
        requireNotNull(actual) { "expected $expected but was null" }
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `mass units convert between each other`() {
        assertDecimal("8", convertAmountUnit(BigDecimal("8000"), AmountUnit.MCG, AmountUnit.MG))
        assertDecimal("1000", convertAmountUnit(BigDecimal.ONE, AmountUnit.MG, AmountUnit.MCG))
        assertDecimal("1", convertAmountUnit(BigDecimal("1000000"), AmountUnit.MCG, AmountUnit.G))
        assertDecimal("0.5", convertAmountUnit(BigDecimal("500000"), AmountUnit.MCG, AmountUnit.G))
    }

    @Test
    fun `incompatible units are not converted`() {
        assertNull(convertAmountUnit(BigDecimal.ONE, AmountUnit.UNIT, AmountUnit.MG))
        assertNull(convertAmountUnit(BigDecimal.ONE, AmountUnit.MEQ, AmountUnit.G))
        assertNull(convertAmountUnit(BigDecimal.ONE, AmountUnit.IU, AmountUnit.UNIT))
    }

    @Test
    fun `preset concentration is converted to the target unit`() {
        val preset =
            ConcentrationPreset(
                id = "p",
                drugId = "d",
                mode = ConcentrationMode.MASS_VOLUME,
                amount = BigDecimal("8000"),
                amountUnit = AmountUnit.MCG,
                volumeMl = BigDecimal("50"),
                isDefault = true,
            )
        assertDecimal("160", preset.concentrationIn(AmountUnit.MCG))
        assertDecimal("0.16", preset.concentrationIn(AmountUnit.MG))
        assertNull(preset.concentrationIn(AmountUnit.UNIT))
    }
}
