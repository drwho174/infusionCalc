package com.drwho174.infusioncalc.tci

import java.math.BigDecimal

/** PK-модель пропофола для TCI. Только взрослые модели (D-TCI-2). */
enum class TciModel { MARSH, SCHNIDER }

/** Режим таргетирования: плазма (Cp) или место действия (Ce). */
enum class TargetMode { PLASMA, EFFECT_SITE }

/** Пол пациента — нужен только для Schnider (LBM James). */
enum class Sex { MALE, FEMALE }

/**
 * Параметры трёхкамерной модели (л и л/мин) плюс ke0 (мин⁻¹).
 * Микроконстанты k10/k12/k21/k13/k31 выводятся из CL и V (CALC-TCI-02).
 */
data class TciParameters(
    val v1: BigDecimal,
    val v2: BigDecimal,
    val v3: BigDecimal,
    val cl1: BigDecimal,
    val cl2: BigDecimal,
    val cl3: BigDecimal,
    val ke0: BigDecimal,
    val k10: BigDecimal,
    val k12: BigDecimal,
    val k21: BigDecimal,
    val k13: BigDecimal,
    val k31: BigDecimal,
)

/** Мгновенное состояние симуляции (массы в камерах, мг; Ce — мкг/мл). */
data class TciState(
    val a1Mg: BigDecimal,
    val a2Mg: BigDecimal,
    val a3Mg: BigDecimal,
    val ceMcgPerMl: BigDecimal,
)

/** Точка временного ряда симуляции. */
data class SimStep(
    val timeSeconds: Int,
    val cpMcgPerMl: BigDecimal,
    val ceMcgPerMl: BigDecimal,
    /** Объёмная скорость ввода на этом шаге (мл/ч); 0 после остановки. */
    val rateMlPerHour: BigDecimal,
    val state: TciState,
)

/** Результат полного расчёта TCI (болюс, поддержание, временной ряд). */
data class TciSimulation(
    val bolusMg: BigDecimal,
    val bolusMl: BigDecimal,
    val bolusTimeSeconds: Int,
    val bolusRateMlPerHour: BigDecimal,
    val maintenanceStartMlPerHour: BigDecimal,
    val maintenanceSteadyMlPerHour: BigDecimal,
    val steps: List<SimStep>,
)

/** Причина отказа движка (VAL-TCI-07: любой сбой → Invalid, без NaN/Infinity). */
enum class TciError {
    WEIGHT_OUT_OF_RANGE,
    HEIGHT_OUT_OF_RANGE,
    AGE_OUT_OF_RANGE,
    SEX_REQUIRED,
    TARGET_OUT_OF_RANGE,
    INVALID_CONCENTRATION,
    INVALID_BOLUS_RATE,
}

sealed interface TciResult<out T> {
    data class Success<T>(val value: T) : TciResult<T>

    data class Failure(val reason: TciError) : TciResult<Nothing>
}
