package com.drwho174.infusioncalc.ui

import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FormatTest {
    @Test
    fun `scheme label russian`() {
        assertEquals(
            "мкг/кг/мин",
            schemeLabel(AmountUnit.MCG, perKg = true, perTime = TimeBase.PER_MINUTE, english = false),
        )
        assertEquals(
            "мг/ч",
            schemeLabel(AmountUnit.MG, perKg = false, perTime = TimeBase.PER_HOUR, english = false),
        )
    }

    @Test
    fun `scheme label english`() {
        assertEquals(
            "mcg/kg/min",
            schemeLabel(AmountUnit.MCG, perKg = true, perTime = TimeBase.PER_MINUTE, english = true),
        )
        assertEquals(
            "units/h",
            schemeLabel(AmountUnit.UNIT, perKg = false, perTime = TimeBase.PER_HOUR, english = true),
        )
    }

    @Test
    fun `display strips trailing zeros`() {
        assertEquals("120", BigDecimal("120.00").display())
        assertEquals("2.6", BigDecimal("2.60").display())
        assertEquals("0.005", BigDecimal("0.005").display())
        assertEquals("—", (null as BigDecimal?).display())
    }

    @Test
    fun `normalize decimal input keeps one separator`() {
        assertEquals("0.5", normalizeDecimalInput("0,5"))
        assertEquals("1.25", normalizeDecimalInput("1.2.5"))
        assertEquals("70", normalizeDecimalInput("70"))
        assertEquals("", normalizeDecimalInput("abc"))
    }
}
