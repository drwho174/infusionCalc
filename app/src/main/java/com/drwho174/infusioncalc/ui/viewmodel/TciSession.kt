package com.drwho174.infusioncalc.ui.viewmodel

import java.math.BigDecimal

private const val MS_PER_SECOND = 1_000L

/** Состояние сессии TCI: не начата, идёт или на паузе (D-LIVE-1). */
enum class TciSessionStatus { IDLE, RUNNING, PAUSED }

/**
 * Сессия инфузии, привязанная к реальным часам (D-LIVE-1).
 *
 * Время хранится как «накопленные секунды + момент запуска», поэтому сессия
 * продолжает идти при сворачивании приложения и восстанавливается после
 * убийства процесса (сценарий B: снимок в DataStore).
 */
data class TciSession(
    val status: TciSessionStatus = TciSessionStatus.IDLE,
    val accumulatedSeconds: Int = 0,
    val runningSinceEpochMs: Long? = null,
    /** Момент паузы — от него идёт обратный отсчёт до пробуждения. */
    val pausedAtEpochMs: Long? = null,
    val manualFromSeconds: Int? = null,
    val manualRateMlPerHour: BigDecimal? = null,
    val confirmedOffsets: Set<Int> = emptySet(),
) {
    val isActive: Boolean get() = status != TciSessionStatus.IDLE

    val manual: Boolean get() = manualRateMlPerHour != null && manualFromSeconds != null

    /** Прошедшее время; ограничено сутками, чтобы скачок часов не ломал экран. */
    fun elapsedSeconds(nowMs: Long): Int {
        val running =
            if (runningSinceEpochMs != null) {
                val deltaSeconds = (nowMs - runningSinceEpochMs) / MS_PER_SECOND
                deltaSeconds.coerceIn(0L, MAX_SESSION_SECONDS.toLong()).toInt()
            } else {
                accumulatedSeconds
            }
        return running.coerceIn(0, MAX_SESSION_SECONDS)
    }

    fun start(nowMs: Long): TciSession =
        TciSession(
            status = TciSessionStatus.RUNNING,
            accumulatedSeconds = 0,
            runningSinceEpochMs = nowMs,
        )

    /**
     * Пауза: часы сессии продолжают идти (график живой), но насос отдаёт 0 мл/ч.
     * [pausedAtEpochMs] — момент остановки для расчёта выведения.
     */
    fun pause(nowMs: Long): TciSession =
        if (status != TciSessionStatus.RUNNING) {
            this
        } else {
            copy(
                status = TciSessionStatus.PAUSED,
                accumulatedSeconds = elapsedSeconds(nowMs),
                pausedAtEpochMs = nowMs,
            )
        }

    fun resume(): TciSession =
        if (status != TciSessionStatus.PAUSED) {
            this
        } else {
            copy(
                status = TciSessionStatus.RUNNING,
                pausedAtEpochMs = null,
            )
        }

    fun stop(): TciSession = TciSession()

    fun setManual(
        rateMlPerHour: BigDecimal,
        nowMs: Long,
    ): TciSession =
        copy(
            manualFromSeconds = elapsedSeconds(nowMs),
            manualRateMlPerHour = rateMlPerHour,
        )

    /** Возврат к автоматической программе. */
    fun clearManual(): TciSession = copy(manualFromSeconds = null, manualRateMlPerHour = null)

    fun confirm(offsetSeconds: Int): TciSession = copy(confirmedOffsets = confirmedOffsets + offsetSeconds)

    companion object {
        const val MAX_SESSION_SECONDS = 24 * 3600
    }
}
