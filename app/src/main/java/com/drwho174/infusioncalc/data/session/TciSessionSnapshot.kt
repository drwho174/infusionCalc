package com.drwho174.infusioncalc.data.session

import kotlinx.serialization.Serializable

/**
 * Снимок сессии TCI для восстановления после убийства процесса (сценарий B).
 * Хранит только числа и выбранные режимы — никаких идентификаторов пациента.
 */
@Serializable
data class TciSessionSnapshot(
    val version: Int = CURRENT_VERSION,
    val model: String,
    val mode: String,
    val concentrationMgPerMl: String,
    val bolusRateText: String,
    val weightText: String,
    val heightText: String,
    val ageText: String,
    val sex: String,
    val targetText: String,
    val wakeThresholdText: String,
    val status: String,
    val accumulatedSeconds: Int,
    val runningSinceEpochMs: Long?,
    val manualFromSeconds: Int?,
    val manualRateMlPerHour: String?,
    val confirmedOffsets: List<Int>,
    /** Начало действующего плана (после смены цели или возврата из ручного режима). */
    val planStartSeconds: Int = 0,
    val planStartA1: String? = null,
    val planStartA2: String? = null,
    val planStartA3: String? = null,
    val planStartCe: String? = null,
) {
    companion object {
        const val CURRENT_VERSION = 2
    }
}
