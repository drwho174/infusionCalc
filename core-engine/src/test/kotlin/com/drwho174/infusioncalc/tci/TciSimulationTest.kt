package com.drwho174.infusioncalc.tci

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Свойства симуляции (TEST-TCI-SIM-01) и базовые инварианты. */
class TciSimulationTest {

    private fun marshSim(horizonSeconds: Int): TciSimulation {
        val result =
            TciEngine.simulate(
                TciSimRequest(
                    model = TciModel.MARSH,
                    targetMcgPerMl = BigDecimal("4"),
                    mode = TargetMode.PLASMA,
                    concentrationMgPerMl = BigDecimal("10"),
                    bolusRateMlPerHour = BigDecimal("1200"),
                    weightKg = BigDecimal("70"),
                    horizonSeconds = horizonSeconds,
                ),
            )
        return (result as TciResult.Success).value
    }

    private fun stepAt(sim: TciSimulation, seconds: Int): SimStep =
        sim.steps.last { it.timeSeconds <= seconds }

    @Test
    fun `bolus and maintenance reference values are present in simulation`() {
        val sim = marshSim(TciEngine.DEFAULT_HORIZON_SECONDS)
        assertTrue(sim.bolusMg.compareTo(BigDecimal("63.84")) == 0)
        assertTrue(sim.bolusMl.compareTo(BigDecimal("6.384")) == 0)
        assertTrue(sim.bolusTimeSeconds == 19)
        assertTrue(sim.maintenanceStartMlPerHour.compareTo(BigDecimal("104.16")) == 0)
        assertTrue(sim.maintenanceSteadyMlPerHour.compareTo(BigDecimal("45.36")) == 0)
    }

    @Test
    fun `Cp rises to target and Ce approaches target by the horizon`() {
        val sim = marshSim(TciEngine.DEFAULT_HORIZON_SECONDS)
        val last = sim.steps.last()
        assertTrue(last.cpMcgPerMl.compareTo(BigDecimal("3.9")) > 0)
        assertTrue(last.ceMcgPerMl.compareTo(BigDecimal("3.9")) > 0)
    }

    @Test
    fun `after stop Cp and Ce decay monotonically and cross the threshold`() {
        val sim = marshSim(7200)
        val params =
            (TciEngine.parameters(TciModel.MARSH, BigDecimal("70"), null, null, null) as TciResult.Success).value
        val stop = stepAt(sim, 7200).state
        val decay = TciEngine.decaySteps(params, stop, 3600)

        var prevCp = decay.first().cpMcgPerMl
        var prevCe = decay.first().ceMcgPerMl
        val eps = BigDecimal("0.000001")
        for (step in decay.drop(1)) {
            assertTrue("Cp must not rise after stop", step.cpMcgPerMl.subtract(prevCp) <= eps)
            assertTrue("Ce must not rise after stop", step.ceMcgPerMl.subtract(prevCe) <= eps)
            prevCp = step.cpMcgPerMl
            prevCe = step.ceMcgPerMl
        }
        assertTrue("Cp must cross the 1.5 threshold", decay.last().cpMcgPerMl <= BigDecimal("1.5"))
        assertTrue("Ce must cross the 1.5 threshold", decay.last().ceMcgPerMl <= BigDecimal("1.5"))
    }

    @Test
    fun `decrement time grows with infusion duration`() {
        val sim = marshSim(7200)
        val params =
            (TciEngine.parameters(TciModel.MARSH, BigDecimal("70"), null, null, null) as TciResult.Success).value
        val short = TciEngine.decrementTimeSeconds(params, stepAt(sim, 1800).state, TargetMode.PLASMA, BigDecimal("1.5"))
        val long = TciEngine.decrementTimeSeconds(params, stepAt(sim, 7200).state, TargetMode.PLASMA, BigDecimal("1.5"))
        assertNotNull(short)
        assertNotNull(long)
        assertTrue("decrement must grow with duration: $short vs $long", long!! > short!!)
    }

    @Test
    fun `Schnider simulation is deterministic and finite`() {
        val request =
            TciSimRequest(
                model = TciModel.SCHNIDER,
                targetMcgPerMl = BigDecimal("4"),
                mode = TargetMode.EFFECT_SITE,
                concentrationMgPerMl = BigDecimal("10"),
                bolusRateMlPerHour = BigDecimal("1200"),
                weightKg = BigDecimal("70"),
                heightCm = BigDecimal("170"),
                ageYears = 40,
                sex = Sex.MALE,
                horizonSeconds = 3600,
            )
        val a = (TciEngine.simulate(request) as TciResult.Success).value
        val b = (TciEngine.simulate(request) as TciResult.Success).value
        assertTrue(a.steps.size == b.steps.size)
        a.steps.zip(b.steps).forEach { (x, y) ->
            assertTrue(x.cpMcgPerMl.compareTo(y.cpMcgPerMl) == 0)
            assertTrue(x.ceMcgPerMl.compareTo(y.ceMcgPerMl) == 0)
        }
        assertTrue(a.steps.all { it.cpMcgPerMl.signum() >= 0 && it.ceMcgPerMl.signum() >= 0 })
    }
}
