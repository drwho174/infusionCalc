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

        override suspend fun load(): TciSessionSnapshot? = saved

        override suspend fun save(snapshot: TciSessionSnapshot) {
            saved = snapshot
        }

        override suspend fun clear() {
            saved = null
            cleared = true
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
    fun `pause freezes and resume continues`() {
        var now = 0L
        val viewModel = ready(clock = { now })
        viewModel.start()
        now = 5 * 60_000L
        viewModel.refresh()
        assertEquals(
            300,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )

        viewModel.pause()
        now = 10 * 60_000L
        viewModel.refresh()
        assertEquals(
            300,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )

        viewModel.resume()
        now = 11 * 60_000L
        viewModel.refresh()
        assertEquals(
            360,
            viewModel.state.value.live!!
                .elapsedSeconds,
        )
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
    fun `snapshot round trip keeps form and session`() {
        val form =
            TciForm(
                weightText = "70",
                heightText = "170",
                ageText = "40",
                targetText = "4",
                inductionDone = true,
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
