package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Живое состояние сессии: часы, фазы, подсказки и просрочка (D-LIVE-1…D-LIVE-3). */
class TciLiveTest {
    private val form =
        TciForm(
            model = TciModel.MARSH,
            mode = TargetMode.PLASMA,
            weightText = "70",
            targetText = "4",
        )
    private val plan = buildTciState(form).computed!!.plan

    @Test
    fun `idle session has no live state`() {
        assertNull(buildLive(plan, form, TciSession(), 0L, null))
    }

    @Test
    fun `elapsed time follows the clock`() {
        val start = 1_000_000L
        val session = TciSession().start(start)
        assertEquals(0, buildLive(plan, form, session, start, null)!!.elapsedSeconds)
        assertEquals(600, buildLive(plan, form, session, start + 600_000, null)!!.elapsedSeconds)
    }

    @Test
    fun `bolus phase then maintenance with countdown`() {
        val session = TciSession().start(0)
        val bolus = buildLive(plan, form, session, 5_000, null)!!
        assertEquals(TciPhase.BOLUS, bolus.phase)
        assertEquals(0, bolus.stepOffsetSeconds)
        assertEquals(plan.bolusTimeSeconds - 5, bolus.nextChangeInSeconds)

        val maintenance = buildLive(plan, form, session, 300_000, null)!!
        assertEquals(TciPhase.MAINTENANCE, maintenance.phase)
        assertEquals(300, maintenance.stepOffsetSeconds)
        assertEquals(300, maintenance.nextChangeInSeconds)
        assertNotNull(maintenance.nextRateMlPerHour)
        assertTrue(maintenance.cpDisplay.toBigDecimal() > BigDecimal("3.5"))
    }

    @Test
    fun `unconfirmed rate becomes overdue after a minute`() {
        val session = TciSession().start(0)
        assertFalse(buildLive(plan, form, session, 30_000, null)!!.overdue)
        val overdue = buildLive(plan, form, session, 120_000, null)!!
        assertTrue(overdue.overdue)

        val confirmed = session.confirm(overdue.stepOffsetSeconds!!)
        val live = buildLive(plan, form, confirmed, 120_000, null)!!
        assertFalse(live.overdue)
        assertTrue(live.confirmed)
    }

    @Test
    fun `wake prediction is available while running`() {
        val live = buildLive(plan, form, TciSession().start(0), 600_000, null)
        assertNotNull(live)
        assertNotNull(live!!.wakeDisplay)
        assertNotNull(live.state)
    }

    @Test
    fun `pause starts a wake countdown`() {
        val paused = TciSession().start(0).pause(60_000)
        val atPause = buildLive(plan, form, paused, 60_000, null)!!
        val later = buildLive(plan, form, paused, 90_000, null)!!

        assertNotNull(atPause.wakeRemainingSeconds)
        assertEquals(atPause.wakeRemainingSeconds!! - 30, later.wakeRemainingSeconds)
        assertNotNull(later.wakeDisplay)
    }

    @Test
    fun `rate advice is rounded to one decimal`() {
        val live = buildLive(plan, form, TciSession().start(0), 300_000, null)!!
        val rate = live.currentRateMlPerHour!!
        assertTrue("scale must be 1, was $rate", rate.scale() == 1)
    }
}
