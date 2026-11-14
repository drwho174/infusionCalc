package com.drwho174.infusioncalc.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Арифметика сессии по реальным часам (D-LIVE-1). */
class TciSessionTest {
    @Test
    fun `start then elapsed grows with the clock`() {
        val session = TciSession().start(1_000L)
        assertEquals(0, session.elapsedSeconds(1_000L))
        assertEquals(60, session.elapsedSeconds(61_000L))
    }

    @Test
    fun `pause freezes time and resume continues`() {
        val paused = TciSession().start(0L).pause(60_000L)
        assertEquals(60, paused.elapsedSeconds(600_000L))
        assertEquals(60_000L, paused.pausedAtEpochMs)

        val resumed = paused.resume(600_000L)
        assertEquals(120, resumed.elapsedSeconds(660_000L))
        assertEquals(null, resumed.pausedAtEpochMs)
    }

    @Test
    fun `clock going backwards does not produce negative time`() {
        val session = TciSession().start(100_000L)
        assertEquals(0, session.elapsedSeconds(0L))
    }

    @Test
    fun `elapsed is capped to one day`() {
        val session = TciSession().start(0L)
        assertEquals(TciSession.MAX_SESSION_SECONDS, session.elapsedSeconds(Long.MAX_VALUE / 2))
    }

    @Test
    fun `manual rate is anchored to the elapsed moment`() {
        val session = TciSession().start(0L).setManual(BigDecimal("60"), 300_000L)
        assertTrue(session.manual)
        assertEquals(300, session.manualFromSeconds)
        assertEquals(0, BigDecimal("60").compareTo(session.manualRateMlPerHour))
    }

    @Test
    fun `stop resets the session`() {
        val session =
            TciSession()
                .start(0L)
                .setManual(BigDecimal("60"), 60_000L)
                .confirm(0)
                .stop()
        assertFalse(session.isActive)
        assertFalse(session.manual)
        assertEquals(0, session.elapsedSeconds(10_000L))
    }
}
