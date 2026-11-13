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
    val inductionDone: Boolean,
    val maintenanceDone: Boolean,
    val emergenceDone: Boolean,
    val status: String,
    val accumulatedSeconds: Int,
    val runningSinceEpochMs: Long?,
    val manualFromSeconds: Int?,
    val manualRateMlPerHour: String?,
    val confirmedOffsets: List<Int>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
