package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.tci.Sex
import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class TciComputationTest {

    private fun marshForm(target: String, weight: String = "70", concentration: String = "10") =
        TciForm(
            model = TciModel.MARSH,
            mode = TargetMode.PLASMA,
            concentrationMgPerMl = BigDecimal(concentration),
            weightText = weight,
            targetText = target,
        )

    @Test
    fun `Marsh G1 computes bolus and maintenance rates`() {
        val state = buildTciState(marshForm("4"))
        assertNull(state.blocking)
        assertNotNull(state.computed)
        val computed = state.computed!!
        assertEquals("6.38", computed.bolusMlDisplay)
        assertEquals("104.2", computed.maintenanceStartDisplay)
        assertEquals("45.4", computed.maintenanceSteadyDisplay)
        assertTrue(BigDecimal(computed.cpNowDisplay) > BigDecimal("3.9"))
    }

    @Test
    fun `concentration 20 halves volumetric rates and bolus volume`() {
        val state = buildTciState(marshForm("4", concentration = "20"))
        assertNotNull(state.computed)
        val computed = state.computed!!
        assertEquals("3.19", computed.bolusMlDisplay)
        assertEquals("52.1", computed.maintenanceStartDisplay)
        assertEquals("22.7", computed.maintenanceSteadyDisplay)
    }

    @Test
    fun `Schnider blocks without height or age`() {
        val noHeight =
            TciForm(
                model = TciModel.SCHNIDER,
                weightText = "70",
                targetText = "4",
            )
        assertEquals(TciBlocking.HEIGHT, buildTciState(noHeight).blocking)

        val noAge = noHeight.copy(heightText = "170")
        assertEquals(TciBlocking.AGE, buildTciState(noAge).blocking)
    }

    @Test
    fun `Schnider BMI out of range warns but still computes`() {
        val form =
            TciForm(
                model = TciModel.SCHNIDER,
                weightText = "100",
                heightText = "165",
                ageText = "40",
                sex = Sex.FEMALE,
                targetText = "4",
            )
        val state = buildTciState(form)
        assertNull(state.blocking)
        assertTrue(TciWarning.BMI_OUT_OF_RANGE in state.warnings)
        assertNotNull(state.computed)
    }

    @Test
    fun `target outside recommended range warns without blocking`() {
        val state = buildTciState(marshForm("9"))
        assertNull(state.blocking)
        assertTrue(TciWarning.TARGET_OUT_OF_RANGE in state.warnings)
        assertNotNull(state.computed)
    }

    @Test
    fun `weight out of technical range blocks`() {
        assertEquals(TciBlocking.WEIGHT, buildTciState(marshForm("4", weight = "301")).blocking)
        assertEquals(TciBlocking.WEIGHT, buildTciState(marshForm("4", weight = "0")).blocking)
    }
}
