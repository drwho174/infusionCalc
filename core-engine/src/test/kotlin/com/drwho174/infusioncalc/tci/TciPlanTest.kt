package com.drwho174.infusioncalc.tci

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Шаговая программа для обычного насоса (D-LIVE-2, BET-эмуляция). */
class TciPlanTest {

    private fun request(
        target: String = "4",
        weight: String = "70",
        concentration: String = "10",
        bolusRate: String = "1200",
    ) = TciSimRequest(
        model = TciModel.MARSH,
        targetMcgPerMl = BigDecimal(target),
        mode = TargetMode.PLASMA,
        concentrationMgPerMl = BigDecimal(concentration),
        bolusRateMlPerHour = BigDecimal(bolusRate),
        weightKg = BigDecimal(weight),
    )

    private fun plan(request: TciSimRequest = request()): PumpPlan =
        (TciEngine.pumpPlan(request) as TciResult.Success).value

    /** Максимальное отклонение Cp от цели после болюса (первые 5 минут — переходный процесс). */
    private fun deviationsFrom(
        target: String,
        steps: List<SimStep>,
        fromSeconds: Int = 300,
    ): BigDecimal {
        val expected = BigDecimal(target)
        return steps.filter { it.timeSeconds >= fromSeconds }.fold(BigDecimal.ZERO) { acc, step ->
            val d = step.cpMcgPerMl.subtract(expected).abs()
            if (d > acc) d else acc
        }
    }

    @Test
    fun `plan starts with the bolus step and continues with maintenance steps`() {
        val plan = plan()
        val first = plan.steps.first()
        assertTrue(first.bolus)
        assertEquals(0, first.offsetSeconds)
        assertDecimal("1200", first.rateMlPerHour)

        assertEquals(19, plan.bolusTimeSeconds)
        assertEquals(19, plan.steps[1].offsetSeconds)
        assertDecimal("6.384", plan.bolusMl)

        val maintenance = plan.steps.drop(1)
        assertTrue("expected maintenance steps", maintenance.size >= 10)
        maintenance.drop(1).forEach { step ->
            assertEquals("steps must stay on the 5-min grid", 0, step.offsetSeconds % 300)
        }
    }

    @Test
    fun `maintenance steps start high and decrease monotonically`() {
        val plan = plan()
        val maintenance = plan.steps.drop(1)
        assertTrue(maintenance.first().rateMlPerHour < BigDecimal("104.16"))
        assertTrue(maintenance.first().rateMlPerHour > BigDecimal("45.36"))
        // за 60 минут контроллер ещё не выходит на установившуюся скорость, но темп только снижается
        assertTrue(maintenance.last().rateMlPerHour > plan.maintenanceSteadyMlPerHour)
        maintenance.zipWithNext { a, b -> assertTrue(b.rateMlPerHour <= a.rateMlPerHour) }
    }

    @Test
    fun `predicted Cp follows the target under the stepped program`() {
        val plan = plan()
        val last = plan.predicted.last()
        assertClose("4.0", last.cpMcgPerMl, "0.5")
        assertClose("4.0", last.ceMcgPerMl, "0.5")
        val deviation = deviationsFrom("4", plan.predicted)
        assertTrue("max Cp deviation after bolus is $deviation", deviation < BigDecimal("0.5"))
    }

    @Test
    fun `two percent concentration halves maintenance steps`() {
        val one = plan(request(concentration = "10"))
        val two = plan(request(concentration = "20"))
        assertClose("3.192", two.bolusMl, "0.001")
        assertEquals(one.steps.size, two.steps.size)
        // болюсная скорость — это ввод пользователя, она не зависит от концентрации
        assertEquals(one.steps.first().rateMlPerHour, two.steps.first().rateMlPerHour)
        one.steps.drop(1).zip(two.steps.drop(1)).forEach { (a, b) ->
            val half = a.rateMlPerHour.divide(BigDecimal("2"), java.math.MathContext.DECIMAL128)
            // болюс при 2 % короче, поэтому траектория и средние скорости чуть отличаются
            assertClose(half.toPlainString(), b.rateMlPerHour, "1.0")
        }
    }

    @Test
    fun `constant steady rate keeps the steady state`() {
        val params = (TciEngine.parameters(TciModel.MARSH, BigDecimal("70"), null, null, null) as TciResult.Success).value
        val target = BigDecimal("4")
        val a1 = target.multiply(params.v1, java.math.MathContext.DECIMAL128)
        val steadyState =
            TciState(
                a1Mg = a1,
                a2Mg = params.k12.divide(params.k21, java.math.MathContext.DECIMAL128).multiply(a1),
                a3Mg = params.k13.divide(params.k31, java.math.MathContext.DECIMAL128).multiply(a1),
                ceMcgPerMl = target,
            )
        val curve =
            TciEngine.simulateConstantRate(
                params = params,
                fromState = steadyState,
                fromOffsetSeconds = 0,
                rateMlPerHour = TciEngine.mgPerMinToMlPerHour(TciEngine.maintenanceSteadyMgPerMin(target, params), BigDecimal("10")),
                concentrationMgPerMl = BigDecimal("10"),
                horizonSeconds = 1800,
            )
        assertClose("4.0", curve.last().cpMcgPerMl, "0.05")
        assertTrue(curve.first().timeSeconds == 0)
    }

    @Test
    fun `invalid input is rejected by the plan`() {
        val result = TciEngine.pumpPlan(request(target = "0"))
        assertTrue(result is TciResult.Failure)
    }

    @Test
    fun `replan from state adds a bolus when the target grows`() {
        val basePlan = plan()
        val at = 600
        val state = basePlan.predicted.last { it.timeSeconds <= at }.state
        val params =
            (TciEngine.parameters(TciModel.MARSH, BigDecimal("70"), null, null, null) as TciResult.Success).value

        val replan = TciEngine.pumpPlanFromState(request(target = "6"), params, state, at)

        assertTrue("expected an additional bolus", replan.bolusMl > BigDecimal.ZERO)
        assertTrue(replan.bolusTimeSeconds > 0)
        assertEquals(at, replan.steps.first().offsetSeconds)
        assertTrue(replan.steps.first().bolus)
        assertEquals(at, replan.predicted.first().timeSeconds)
        assertClose("6.0", replan.predicted.last().cpMcgPerMl, "0.6")
    }

    @Test
    fun `replan without a bolus when the target drops`() {
        val basePlan = plan()
        val at = 600
        val state = basePlan.predicted.last { it.timeSeconds <= at }.state
        val params =
            (TciEngine.parameters(TciModel.MARSH, BigDecimal("70"), null, null, null) as TciResult.Success).value

        val replan = TciEngine.pumpPlanFromState(request(target = "3"), params, state, at)

        assertEquals(0, replan.bolusMl.signum())
        assertFalse(replan.steps.first().bolus)
        assertEquals(at, replan.predicted.first().timeSeconds)
        assertClose("3.0", replan.predicted.last().cpMcgPerMl, "0.5")
    }

    @Test
    fun `rate formatting keeps one decimal`() {
        assertEquals(0, BigDecimal("0.5").compareTo(TciEngine.formatRateMlPerHour(BigDecimal("0.46"))))
        assertEquals(0, BigDecimal("104.2").compareTo(TciEngine.formatRateMlPerHour(BigDecimal("104.16"))))
        assertEquals(0, BigDecimal("0.0").compareTo(TciEngine.formatRateMlPerHour(BigDecimal("0.04"))))
    }

    private fun assertDecimal(expected: String, actual: BigDecimal) {
        assertEquals("expected $expected but was $actual", 0, BigDecimal(expected).compareTo(actual))
    }

    private fun assertClose(expected: String, actual: BigDecimal, tolerance: String) {
        val diff = actual.subtract(BigDecimal(expected)).abs()
        assertTrue("expected $expected±$tolerance but was $actual", diff <= BigDecimal(tolerance))
    }
}
