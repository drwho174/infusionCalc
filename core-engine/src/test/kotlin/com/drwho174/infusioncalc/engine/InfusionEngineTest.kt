package com.drwho174.infusioncalc.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class InfusionEngineTest {

    private val norepinephrine = DoseScheme(AmountUnit.MCG, perKg = false, TimeBase.PER_MINUTE)
    private val vasopressin = DoseScheme(AmountUnit.UNIT, perKg = false, TimeBase.PER_MINUTE)
    private val nicardipine = DoseScheme(AmountUnit.MG, perKg = false, TimeBase.PER_HOUR)
    private val amiodarone = DoseScheme(AmountUnit.MG, perKg = false, TimeBase.PER_MINUTE)
    private val dopamine = DoseScheme(AmountUnit.MCG, perKg = true, TimeBase.PER_MINUTE)

    private fun ok(result: CalcResult): BigDecimal = (result as CalcResult.Success).value

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `norepinephrine 8 mcg per min at 4 mcg per ml gives 120 ml per h`() {
        assertDecimal("120", ok(InfusionEngine.rate(BigDecimal("8"), norepinephrine, null, BigDecimal("4"))))
    }

    @Test
    fun `nicardipine 5 mg per h at 0_1 mg per ml gives 50 ml per h`() {
        assertDecimal("50", ok(InfusionEngine.rate(BigDecimal("5"), nicardipine, null, BigDecimal("0.1"))))
    }

    @Test
    fun `vasopressin 0_01 U per min at 0_1 U per ml gives 6 ml per h`() {
        assertDecimal("6", ok(InfusionEngine.rate(BigDecimal("0.01"), vasopressin, null, BigDecimal("0.1"))))
    }

    @Test
    fun `amiodarone 0_5 mg per min at 1 mg per ml gives 30 ml per h`() {
        assertDecimal("30", ok(InfusionEngine.rate(BigDecimal("0.5"), amiodarone, null, BigDecimal("1"))))
    }

    @Test
    fun `amiodarone 0_5 mg per min at 6 mg per ml gives 5 ml per h`() {
        assertDecimal("5", ok(InfusionEngine.rate(BigDecimal("0.5"), amiodarone, null, BigDecimal("6"))))
    }

    @Test
    fun `reverse nicardipine 30 ml per h at 0_1 mg per ml gives 3 mg per h`() {
        assertDecimal("3", ok(InfusionEngine.dose(BigDecimal("30"), nicardipine, null, BigDecimal("0.1"))))
    }

    @Test
    fun `reverse vasopressin 5 ml per h at 0_1 U per ml gives 1 over 120 U per min`() {
        val restored = ok(InfusionEngine.dose(BigDecimal("5"), vasopressin, null, BigDecimal("0.1")))
        assertTrue(restored.subtract(BigDecimal("0.0083333333")).abs() < BigDecimal("0.0000001"))
    }

    @Test
    fun `per kg scheme uses weight`() {
        assertDecimal("2.625", ok(InfusionEngine.rate(BigDecimal("0.1"), dopamine, BigDecimal("70"), BigDecimal("160"))))
        assertDecimal("0.1", ok(InfusionEngine.dose(BigDecimal("2.625"), dopamine, BigDecimal("70"), BigDecimal("160"))))
    }

    @Test
    fun `per kg scheme without weight is invalid`() {
        val result = InfusionEngine.rate(BigDecimal("0.1"), dopamine, null, BigDecimal("160"))
        assertEquals(CalcResult.Invalid(CalcResult.Reason.ZERO_WEIGHT), result)
    }

    @Test
    fun `zero concentration is invalid`() {
        val result = InfusionEngine.rate(BigDecimal("8"), norepinephrine, null, BigDecimal.ZERO)
        assertEquals(CalcResult.Invalid(CalcResult.Reason.ZERO_CONCENTRATION), result)
    }

    @Test
    fun `negative or zero dose is invalid`() {
        assertEquals(
            CalcResult.Invalid(CalcResult.Reason.NEGATIVE_INPUT),
            InfusionEngine.rate(BigDecimal("-1"), norepinephrine, null, BigDecimal("4")),
        )
        assertEquals(
            CalcResult.Invalid(CalcResult.Reason.NEGATIVE_INPUT),
            InfusionEngine.rate(BigDecimal.ZERO, norepinephrine, null, BigDecimal("4")),
        )
    }

    @Test
    fun `rate formatting keeps one decimal and two below one`() {
        assertDecimal("2.6", InfusionEngine.formatRate(BigDecimal("2.625")))
        assertDecimal("0.15", InfusionEngine.formatRate(BigDecimal("0.15")))
    }

    @Test
    fun `dose formatting keeps titration step`() {
        assertDecimal("0.005", InfusionEngine.formatDose(BigDecimal("0.005")))
        assertDecimal("0.063", InfusionEngine.formatDose(BigDecimal("0.063")))
    }

    @Test
    fun `dose display is limited to three decimals`() {
        assertDecimal("0.021", InfusionEngine.formatDoseDisplay(BigDecimal("0.0205128205128")))
        assertDecimal("0.005", InfusionEngine.formatDoseDisplay(BigDecimal("0.005")))
        assertDecimal("120", InfusionEngine.formatDoseDisplay(BigDecimal("120.0000001")))
        assertDecimal("2.675", InfusionEngine.formatDoseDisplay(BigDecimal("2.6749")))
    }

    @Test
    fun `round trip stays within half display step`() {
        val original = BigDecimal("0.1")
        val rate = ok(InfusionEngine.rate(original, dopamine, BigDecimal("70"), BigDecimal("160")))
        val restored = ok(InfusionEngine.dose(rate, dopamine, BigDecimal("70"), BigDecimal("160")))
        assertTrue(InfusionEngine.isRoundTripConsistent(original, restored, BigDecimal("0.01")))
    }
}
