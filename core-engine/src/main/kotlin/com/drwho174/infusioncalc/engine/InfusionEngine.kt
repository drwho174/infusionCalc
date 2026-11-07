package com.drwho174.infusioncalc.engine

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

enum class AmountUnit { MCG, MG, G, UNIT, IU, MEQ }

enum class TimeBase { PER_MINUTE, PER_HOUR }

/**
 * Схема дозирования: количество препарата за единицу времени, опционально на кг массы.
 * Внутренняя модель ЕДИНАЯ: (amount, perKg, perTime) — см. docs/research-clinical.md §3.2.
 */
data class DoseScheme(
    val amountUnit: AmountUnit,
    val perKg: Boolean,
    val perTime: TimeBase,
)

sealed interface CalcResult {
    data class Success(val value: BigDecimal) : CalcResult
    data class Invalid(val reason: Reason) : CalcResult

    enum class Reason {
        ZERO_WEIGHT,
        ZERO_CONCENTRATION,
        NEGATIVE_INPUT,
        UNIT_MISMATCH,
        TOO_MANY_DECIMALS,
        OUT_OF_SAFE_RANGE,
    }
}

/**
 * Чистый вычислительный движок. Никаких Android/Room зависимостей, только BigDecimal.
 *
 * rate  Q[мл/ч] = D · k · (W, если perKg) / C
 * dose  D        = Q · C / (k · (W, если perKg))
 * где k = 60 для «в минуту», 1 для «в час», C — концентрация в тех же единицах, что и D, на мл.
 */
object InfusionEngine {
    private val MC = MathContext(34, RoundingMode.HALF_UP)

    fun rate(
        dose: BigDecimal,
        scheme: DoseScheme,
        weightKg: BigDecimal?,
        concentrationPerMl: BigDecimal,
    ): CalcResult {
        validateInputs(dose, concentrationPerMl)?.let { return it }
        val weight = weightFor(scheme, weightKg) ?: return CalcResult.Invalid(CalcResult.Reason.ZERO_WEIGHT)

        var numerator = dose.multiply(timeFactor(scheme), MC)
        if (scheme.perKg) numerator = numerator.multiply(weight, MC)
        return CalcResult.Success(numerator.divide(concentrationPerMl, MC))
    }

    fun dose(
        rateMlPerHour: BigDecimal,
        scheme: DoseScheme,
        weightKg: BigDecimal?,
        concentrationPerMl: BigDecimal,
    ): CalcResult {
        validateInputs(rateMlPerHour, concentrationPerMl)?.let { return it }
        val weight = weightFor(scheme, weightKg) ?: return CalcResult.Invalid(CalcResult.Reason.ZERO_WEIGHT)

        var numerator = rateMlPerHour.multiply(concentrationPerMl, MC)
        if (scheme.perKg) numerator = numerator.divide(weight, MC)
        return CalcResult.Success(numerator.divide(timeFactor(scheme), MC))
    }

    /** Скорость для отображения: 1 знак; при значении < 1 — 2 знака. */
    fun formatRate(rate: BigDecimal): BigDecimal {
        val scale = if (rate.abs() < BigDecimal.ONE) 2 else 1
        return rate.setScale(scale, RoundingMode.HALF_UP)
    }

    /** Доза для отображения: до 3 значимых цифр, но не более 4 знаков после разделителя. */
    fun formatDose(dose: BigDecimal): BigDecimal {
        if (dose.signum() == 0) return BigDecimal.ZERO
        var rounded = dose.round(MathContext(3, RoundingMode.HALF_UP))
        if (rounded.scale() > 4) rounded = rounded.setScale(4, RoundingMode.HALF_UP)
        if (rounded.scale() < 0) rounded = rounded.setScale(0)
        return rounded
    }

    /** Доза для экрана расчёта: не более 3 знаков после запятой (v1.2). */
    fun formatDoseDisplay(dose: BigDecimal): BigDecimal =
        dose.setScale(3, RoundingMode.HALF_UP).stripTrailingZeros()

    /** Проверка round-trip: расхождение не больше половины шага отображения. */
    fun isRoundTripConsistent(
        original: BigDecimal,
        restored: BigDecimal,
        displayStep: BigDecimal,
    ): Boolean = original.subtract(restored).abs() <= displayStep.divide(BigDecimal(2), MC)

    private fun validateInputs(value: BigDecimal, concentrationPerMl: BigDecimal): CalcResult.Invalid? {
        if (concentrationPerMl.signum() <= 0) {
            return CalcResult.Invalid(CalcResult.Reason.ZERO_CONCENTRATION)
        }
        if (value.signum() <= 0) {
            return CalcResult.Invalid(CalcResult.Reason.NEGATIVE_INPUT)
        }
        return null
    }

    private fun weightFor(scheme: DoseScheme, weightKg: BigDecimal?): BigDecimal? {
        if (!scheme.perKg) return BigDecimal.ONE
        return weightKg?.takeIf { it.signum() > 0 }
    }

    private fun timeFactor(scheme: DoseScheme): BigDecimal =
        if (scheme.perTime == TimeBase.PER_MINUTE) BigDecimal(60) else BigDecimal.ONE
}
