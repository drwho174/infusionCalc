package com.drwho174.infusioncalc.tci

import java.math.BigDecimal

/**
 * Одна уставка обычного шприцевого насоса: с [offsetSeconds] после старта
 * держать [rateMlPerHour]. Болюс помечен [bolus].
 */
data class PumpStep(
    val offsetSeconds: Int,
    val rateMlPerHour: BigDecimal,
    val bolus: Boolean = false,
)

/**
 * Шаговая программа TCI для насоса без режима TCI (D-LIVE-2, BET-эмуляция):
 * болюс и ступени поддержания, а также прогноз Cp/Ce при исполнении этой программы.
 */
data class PumpPlan(
    val bolusMg: BigDecimal,
    val bolusMl: BigDecimal,
    val bolusTimeSeconds: Int,
    val bolusRateMlPerHour: BigDecimal,
    val maintenanceStartMlPerHour: BigDecimal,
    val maintenanceSteadyMlPerHour: BigDecimal,
    val steps: List<PumpStep>,
    /** Прогноз под фактическим исполнением ступеней (то, что увидит врач на экране). */
    val predicted: List<SimStep>,
)
