package com.drwho174.infusioncalc.tci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Golden-кейсы G1–G10 из docs/SPEC-TCI.md §6.5 / docs/research-tci.md §7. */
class TciEngineGoldenTest {

    private fun params(
        model: TciModel,
        weight: String,
        height: String? = null,
        age: Int? = null,
        sex: Sex? = null,
    ): TciParameters = (TciEngine.parameters(model, BigDecimal(weight), height?.let { BigDecimal(it) }, age, sex) as TciResult.Success).value

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    private fun assertClose(expected: String, actual: BigDecimal, tolerance: String) {
        val diff = actual.subtract(BigDecimal(expected)).abs()
        assertTrue("expected $expected±$tolerance but was $actual", diff <= BigDecimal(tolerance))
    }

    @Test
    fun `G1 Marsh 70 kg Cp 4 at 1 percent`() {
        val p = params(TciModel.MARSH, "70")
        assertDecimal("63.84", TciEngine.bolusMg(BigDecimal("4"), p))
        assertDecimal("6.384", TciEngine.bolusMl(TciEngine.bolusMg(BigDecimal("4"), p), BigDecimal("10")))
        assertDecimal(
            "104.16",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceStartMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
        )
        assertDecimal(
            "45.36",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
        )
        assertClose("0.916", TciEngine.ceAfterStep(BigDecimal("4"), p.ke0, BigDecimal("1")), "0.001")
        assertClose("2.910", TciEngine.ceAfterStep(BigDecimal("4"), p.ke0, BigDecimal("5")), "0.001")
    }

    @Test
    fun `G2 Marsh 100 kg Cp 3 at 1 percent`() {
        val p = params(TciModel.MARSH, "100")
        assertDecimal("68.4", TciEngine.bolusMg(BigDecimal("3"), p))
        assertDecimal("6.84", TciEngine.bolusMl(TciEngine.bolusMg(BigDecimal("3"), p), BigDecimal("10")))
        assertDecimal(
            "111.6",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceStartMgPerMin(BigDecimal("3"), p), BigDecimal("10")),
        )
        assertDecimal(
            "48.6",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("3"), p), BigDecimal("10")),
        )
    }

    @Test
    fun `G3 Schnider male 70 170 40 Cp 4 at 1 percent`() {
        val p = params(TciModel.SCHNIDER, "70", "170", 40, Sex.MALE)
        assertClose("55.30", TciEngine.lbmJames(BigDecimal("70"), BigDecimal("170"), Sex.MALE), "0.01")
        assertDecimal("17.08", TciEngine.bolusMg(BigDecimal("4"), p))
        assertDecimal("1.708", TciEngine.bolusMl(TciEngine.bolusMg(BigDecimal("4"), p), BigDecimal("10")))
        assertClose(
            "97.824",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceStartMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
            "0.05",
        )
        assertClose(
            "39.312",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
            "0.05",
        )
        assertClose("1.465", TciEngine.ceAfterStep(BigDecimal("4"), p.ke0, BigDecimal("1")), "0.001")
        assertClose("2.982", TciEngine.ceAfterStep(BigDecimal("4"), p.ke0, BigDecimal("3")), "0.001")
    }

    @Test
    fun `G4 Schnider male 70 170 80 Cp 3 shows age effect`() {
        val p = params(TciModel.SCHNIDER, "70", "170", 80, Sex.MALE)
        assertDecimal("8.343", p.v2)
        assertDecimal("0.642", p.cl2)
        assertDecimal("12.81", TciEngine.bolusMg(BigDecimal("3"), p))
        assertDecimal("1.281", TciEngine.bolusMl(TciEngine.bolusMg(BigDecimal("3"), p), BigDecimal("10")))
        assertClose(
            "56.088",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceStartMgPerMin(BigDecimal("3"), p), BigDecimal("10")),
            "0.05",
        )
        assertClose(
            "29.484",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("3"), p), BigDecimal("10")),
            "0.05",
        )
    }

    @Test
    fun `G5 Schnider female 60 160 40 Cp 4 shows sex effect through LBM`() {
        val p = params(TciModel.SCHNIDER, "60", "160", 40, Sex.FEMALE)
        assertClose("43.39", TciEngine.lbmJames(BigDecimal("60"), BigDecimal("160"), Sex.FEMALE), "0.01")
        assertDecimal("17.08", TciEngine.bolusMg(BigDecimal("4"), p))
        assertDecimal("1.708", TciEngine.bolusMl(TciEngine.bolusMg(BigDecimal("4"), p), BigDecimal("10")))
        assertClose(
            "100.013",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceStartMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
            "0.05",
        )
        assertClose(
            "41.501",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
            "0.05",
        )
    }

    @Test
    fun `G6 Marsh 70 kg Cp 4 at 2 percent halves volumetric rates`() {
        val p = params(TciModel.MARSH, "70")
        val bolusMg = TciEngine.bolusMg(BigDecimal("4"), p)
        assertDecimal("63.84", bolusMg)
        assertDecimal("3.192", TciEngine.bolusMl(bolusMg, BigDecimal("20")))
        assertDecimal(
            "52.08",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceStartMgPerMin(BigDecimal("4"), p), BigDecimal("20")),
        )
        assertDecimal(
            "22.68",
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("4"), p), BigDecimal("20")),
        )
    }

    @Test
    fun `G7 Ce lag times to fractions of target`() {
        val marshKe0 = BigDecimal("0.26")
        val schniderKe0 = BigDecimal("0.456")
        assertClose("2.67", TciEngine.timeToCeFraction(BigDecimal("0.5"), marshKe0), "0.01")
        assertClose("8.86", TciEngine.timeToCeFraction(BigDecimal("0.9"), marshKe0), "0.01")
        assertClose("11.52", TciEngine.timeToCeFraction(BigDecimal("0.95"), marshKe0), "0.01")
        assertClose("1.52", TciEngine.timeToCeFraction(BigDecimal("0.5"), schniderKe0), "0.01")
        assertClose("5.05", TciEngine.timeToCeFraction(BigDecimal("0.9"), schniderKe0), "0.01")
        assertClose("6.57", TciEngine.timeToCeFraction(BigDecimal("0.95"), schniderKe0), "0.01")
    }

    @Test
    fun `G8 steady rate recovers the target concentration`() {
        val p = params(TciModel.MARSH, "70")
        val steadyMlH = TciEngine.formatRateMlPerHour(
            TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(BigDecimal("4"), p), BigDecimal("10")),
        )
        // C' = R∞_mlh · conc / 60 / CL1
        val recovered =
            steadyMlH
                .multiply(BigDecimal("10"))
                .divide(BigDecimal(60), java.math.MathContext.DECIMAL128)
                .divide(p.cl1, java.math.MathContext.DECIMAL128)
        assertClose("4.0", recovered, "0.05")
    }

    @Test
    fun `G9 BMI guard for Schnider`() {
        assertClose("44.98", TciEngine.bmi(BigDecimal("130"), BigDecimal("170")), "0.01")
        assertTrue(TciEngine.isBmiOutsideSchniderRange(BigDecimal("130"), BigDecimal("170"), Sex.MALE))
        assertClose("36.73", TciEngine.bmi(BigDecimal("100"), BigDecimal("165")), "0.01")
        assertTrue(TciEngine.isBmiOutsideSchniderRange(BigDecimal("100"), BigDecimal("165"), Sex.FEMALE))
    }

    @Test
    fun `G10 guards for age and weight`() {
        assertEquals(
            TciResult.Failure(TciError.AGE_OUT_OF_RANGE),
            TciEngine.parameters(TciModel.SCHNIDER, BigDecimal("70"), BigDecimal("170"), 17, Sex.MALE),
        )
        assertEquals(
            TciResult.Failure(TciError.AGE_OUT_OF_RANGE),
            TciEngine.parameters(TciModel.SCHNIDER, BigDecimal("70"), BigDecimal("170"), 111, Sex.MALE),
        )
        assertEquals(
            TciResult.Failure(TciError.WEIGHT_OUT_OF_RANGE),
            TciEngine.parameters(TciModel.SCHNIDER, BigDecimal("301"), BigDecimal("170"), 40, Sex.MALE),
        )
        assertEquals(
            TciResult.Failure(TciError.WEIGHT_OUT_OF_RANGE),
            TciEngine.parameters(TciModel.MARSH, BigDecimal("0"), null, null, null),
        )
    }
}
