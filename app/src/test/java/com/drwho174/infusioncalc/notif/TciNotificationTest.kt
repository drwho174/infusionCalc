package com.drwho174.infusioncalc.notif

import com.drwho174.infusioncalc.tci.TargetMode
import com.drwho174.infusioncalc.tci.TciEngine
import com.drwho174.infusioncalc.tci.TciModel
import com.drwho174.infusioncalc.tci.TciResult
import com.drwho174.infusioncalc.tci.TciSimRequest
import com.drwho174.infusioncalc.ui.viewmodel.TciSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/** Уведомление: концентрация по режиму, отсчёт до коррекции и сигнал её времени. */
class TciNotificationTest {
    private val plan =
        (
            TciEngine.pumpPlan(
                TciSimRequest(
                    model = TciModel.MARSH,
                    targetMcgPerMl = BigDecimal("4"),
                    mode = TargetMode.PLASMA,
                    concentrationMgPerMl = BigDecimal("10"),
                    bolusRateMlPerHour = BigDecimal("1200"),
                    weightKg = BigDecimal("70"),
                ),
            ) as TciResult.Success
        ).value

    private fun model(
        session: TciSession,
        mode: TargetMode = TargetMode.PLASMA,
        pauseCurve: List<com.drwho174.infusioncalc.tci.SimStep>? = null,
    ) = TciNotificationModel(
        mode = mode,
        session = session,
        plan = plan,
        curve = plan.predicted,
        pauseCurve = pauseCurve,
    )

    @Test
    fun `plasma mode reports Cp and the next correction`() {
        val content = evaluateTciNotification(model(TciSession().start(0)), 300_000)

        assertNotNull(content.concentrationMcgPerMl)
        assertEquals(TargetMode.PLASMA, content.mode)
        assertNotNull(content.nextInSeconds)
        assertEquals(300, content.nextInSeconds)
        assertNotNull(content.nextRateMlPerHour)
    }

    @Test
    fun `effect site mode reports Ce`() {
        val content =
            evaluateTciNotification(
                model(TciSession().start(0), mode = TargetMode.EFFECT_SITE),
                300_000,
            )

        val point = plan.predicted.last { it.timeSeconds <= 300 }
        assertEquals(0, point.ceMcgPerMl.compareTo(content.concentrationMcgPerMl))
    }

    @Test
    fun `unconfirmed step is reported as due once`() {
        val content = evaluateTciNotification(model(TciSession().start(0)), 300_000)
        assertEquals(300, content.dueOffsetSeconds)
    }

    @Test
    fun `confirmed step is not due anymore`() {
        val session = TciSession().start(0).confirm(300, 300_000)
        val content = evaluateTciNotification(model(session), 300_000)
        assertNull(content.dueOffsetSeconds)
    }

    @Test
    fun `paused model reports zero rate and decayed concentration`() {
        val session = TciSession().start(0).pause(60_000)
        val stateAtPause = plan.predicted.last { it.timeSeconds <= 60 }.state
        val params =
            (
                TciEngine.parameters(TciModel.MARSH, BigDecimal("70"), null, null, null)
                    as TciResult.Success
            ).value
        val pauseCurve =
            TciEngine.simulateConstantRate(
                params = params,
                fromState = stateAtPause,
                fromOffsetSeconds = 60,
                rateMlPerHour = BigDecimal.ZERO,
                concentrationMgPerMl = BigDecimal("10"),
                horizonSeconds = 14_400,
            )

        val content = evaluateTciNotification(model(session, pauseCurve = pauseCurve), 120_000)

        assertTrue(content.paused)
        assertEquals(0, BigDecimal.ZERO.compareTo(content.currentRateMlPerHour))
        assertNull(content.nextInSeconds)
        assertNull(content.dueOffsetSeconds)
    }
}
