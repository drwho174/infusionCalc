package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.data.session.TciSessionSnapshot
import com.drwho174.infusioncalc.data.session.TciSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

/** Сессия TCI: фон, восстановление после kill и ручная скорость. */
@OptIn(ExperimentalCoroutinesApi::class)
class TciViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeStore(
        var saved: TciSessionSnapshot? = null,
    ) : TciSessionStore {
        var cleared = false
        var disclaimerHidden = false
        var bolusRate: BigDecimal? = null

        override suspend fun load(): TciSessionSnapshot? = saved

        override suspend fun save(snapshot: TciSessionSnapshot) {
            saved = snapshot
        }

        override suspend fun clear() {
            saved = null
            cleared = true
        }

        override suspend fun isDisclaimerHidden(): Boolean = disclaimerHidden

        override suspend fun setDisclaimerHidden(hidden: Boolean) {
            disclaimerHidden = hidden
        }

        override suspend fun loadBolusRateMlPerHour(): BigDecimal? = bolusRate

        override suspend fun saveBolusRateMlPerHour(value: BigDecimal) {
            bolusRate = value
        }
    }

    private fun ready(
        store: FakeStore = FakeStore(),
        clock: () -> Long,
    ): TciViewModel =
        TciViewModel(store, clock).apply {
            onWeightChange("70")
            onTargetChange("4")
        }

    @Test
    fun `session keeps running while the screen is closed`() {
        var now = 0L
        val viewModel = ready(clock = { now })
        viewModel.start()
        assertEquals(TciSessionStatus.RUNNING, viewModel.state.value.session.status)

        now = 5 * 60_000L
        viewModel.refresh()
        assertEquals(
            300,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )

        now = 60 * 60_000L
        viewModel.refresh()
        assertEquals(
            3600,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )
    }

    @Test
    fun `restored session catches up after process death`() {
        val start = 1_000_000L
        val snapshot = TciForm(weightText = "70", targetText = "4").toSnapshot(TciSession().start(start))
        val store = FakeStore(snapshot)

        val viewModel = TciViewModel(store, clock = { start + 20 * 60_000L })
        assertEquals(TciSessionStatus.RUNNING, viewModel.state.value.session.status)
        assertEquals(
            1200,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )
        assertEquals("70", viewModel.state.value.form.weightText)
    }

    @Test
    fun `pause keeps the clock running with zero rate and decaying Cp`() {
        var now = 0L
        val viewModel = ready(clock = { now })
        viewModel.start()
        now = 5 * 60_000L
        viewModel.refresh()
        val before =
            viewModel.state.value.live!!
                .cpDisplay
                .toBigDecimal()

        viewModel.pause()
        val paused = viewModel.state.value.live!!
        assertTrue(paused.paused)
        assertEquals(0, BigDecimal.ZERO.compareTo(paused.currentRateMlPerHour))

        now = 6 * 60_000L
        viewModel.refresh()
        val later = viewModel.state.value.live!!
        assertEquals(360, later.elapsedSeconds)
        assertTrue("Cp must decay while paused", later.cpDisplay.toBigDecimal() < before)

        viewModel.resume()
        val resumed = viewModel.state.value.live!!
        assertFalse(resumed.paused)
        assertTrue(resumed.currentRateMlPerHour!! > BigDecimal.ZERO)
    }

    @Test
    fun `manual rate drives the prediction from its moment`() {
        var now = 0L
        val viewModel = ready(clock = { now })
        viewModel.start()
        now = 10 * 60_000L
        viewModel.refresh()

        viewModel.setManualRate("60")
        val manual = viewModel.state.value.live!!
        assertTrue(manual.manual)
        assertEquals(0, BigDecimal("60").compareTo(manual.currentRateMlPerHour))

        now = 15 * 60_000L
        viewModel.refresh()
        assertEquals(
            900,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )
        assertTrue(
            viewModel.state.value.live!!
                .manual,
        )
    }

    @Test
    fun `stop clears the stored session`() {
        val store = FakeStore()
        val viewModel = ready(store, clock = { 0L })
        viewModel.start()
        viewModel.stop()

        assertEquals(TciSessionStatus.IDLE, viewModel.state.value.session.status)
        assertNull(store.saved)
        assertTrue(store.cleared)
    }

    @Test
    fun `changing the target during infusion adds a bolus and replans`() {
        var now = 0L
        val viewModel = ready(clock = { now })
        viewModel.start()
        now = 10 * 60_000L
        viewModel.refresh()
        val before =
            viewModel.state.value.computed!!
                .plan

        viewModel.onTargetChange("6")

        val after =
            viewModel.state.value.computed!!
                .plan
        assertTrue("expected an additional bolus", after.bolusMl > BigDecimal.ZERO)
        assertTrue("the additional bolus is smaller than the loading dose", after.bolusMl < before.bolusMl)
        assertTrue(after.bolusTimeSeconds > 0)
        assertEquals(600, after.steps.first().offsetSeconds)
        assertTrue(after.steps.first().bolus)
        assertEquals(
            600,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )
    }

    @Test
    fun `manual rate can be replaced by the automatic programme`() {
        var now = 0L
        val viewModel = ready(clock = { now })
        viewModel.start()
        now = 10 * 60_000L
        viewModel.refresh()

        viewModel.setManualRate("60")
        assertTrue(
            viewModel.state.value.live!!
                .manual,
        )

        viewModel.returnToAutomatic()
        val live = viewModel.state.value.live!!
        assertFalse(live.manual)
        assertTrue(BigDecimal("60").compareTo(live.currentRateMlPerHour) != 0)
    }

    @Test
    fun `disclaimer can be suppressed forever`() {
        val store = FakeStore()
        val viewModel = ready(store, clock = { 0L })

        viewModel.hideDisclaimerForever()

        assertTrue(viewModel.state.value.disclaimerHidden)
        assertTrue(store.disclaimerHidden)
    }

    @Test
    fun `bolus rate setting is persisted`() {
        val store = FakeStore()
        val viewModel = ready(store, clock = { 0L })

        viewModel.onBolusRateChange("600")

        assertEquals(0, BigDecimal("600").compareTo(store.bolusRate))
        assertEquals("600", viewModel.state.value.form.bolusRateText)
    }

    @Test
    fun `snapshot round trip keeps form and session`() {
        val form =
            TciForm(
                weightText = "70",
                heightText = "170",
                ageText = "40",
                targetText = "4",
            )
        val session =
            TciSession()
                .start(1_000L)
                .setManual(BigDecimal("60"), 5_000L)
                .confirm(300)

        val snapshot = form.toSnapshot(session)

        assertEquals(form, snapshot.toForm())
        assertEquals(session, snapshot.toSession())
    }
}
