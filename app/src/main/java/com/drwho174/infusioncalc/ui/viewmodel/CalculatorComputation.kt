package com.drwho174.infusioncalc.ui.viewmodel

import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.convertAmountUnit
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.CalcResult
import com.drwho174.infusioncalc.engine.InfusionEngine
import com.drwho174.infusioncalc.ui.display
import java.math.BigDecimal
import com.drwho174.infusioncalc.engine.DoseScheme as EngineDoseScheme

internal data class CalculatorForm(
    val doseText: String = "",
    val rateText: String = "",
    val source: InputSource = InputSource.DOSE,
    val presetId: String? = null,
)

internal data class ComputedValues(
    val displayDose: String,
    val displayRate: String,
    val resultValue: BigDecimal?,
    val resultIsRate: Boolean,
    val doseValue: BigDecimal?,
)

internal data class RangeFlags(
    val above: Boolean,
    val below: Boolean,
)

internal fun isUnitMismatch(
    preset: ConcentrationPreset?,
    schemeUnit: AmountUnit?,
): Boolean =
    preset?.amountUnit?.let { presetUnit ->
        schemeUnit?.let { convertAmountUnit(BigDecimal.ONE, presetUnit, it) == null }
    } ?: false

internal fun computeValues(
    formState: CalculatorForm,
    scheme: DoseScheme?,
    concentration: BigDecimal?,
    weight: BigDecimal?,
): ComputedValues {
    val computed = computeRaw(formState, scheme?.toEngine(), concentration, weight)
    val fromDose = formState.source == InputSource.DOSE
    val computedDose = computed?.takeIf { !it.first }?.second
    val computedRate = computed?.takeIf { it.first }?.second
    val doseValue = if (fromDose) formState.doseText.toBigDecimalOrNull() else computedDose
    val displayDose =
        if (fromDose) {
            formState.doseText
        } else {
            computedDose?.let { InfusionEngine.formatDoseDisplay(it) }?.display() ?: ""
        }
    val displayRate =
        if (!fromDose) {
            formState.rateText
        } else {
            computedRate?.let { InfusionEngine.formatRate(it) }?.display() ?: ""
        }
    val resultValue =
        computed?.let {
            if (it.first) InfusionEngine.formatRate(it.second) else InfusionEngine.formatDoseDisplay(it.second)
        }
    return ComputedValues(
        displayDose = displayDose,
        displayRate = displayRate,
        resultValue = resultValue,
        resultIsRate = computed?.first == true,
        doseValue = doseValue,
    )
}

internal fun computeRaw(
    formState: CalculatorForm,
    engineScheme: EngineDoseScheme?,
    concentration: BigDecimal?,
    weight: BigDecimal?,
): Pair<Boolean, BigDecimal>? {
    if (concentration == null || engineScheme == null) return null
    return if (formState.source == InputSource.DOSE) {
        formState.doseText.toBigDecimalOrNull()?.let { dose ->
            (InfusionEngine.rate(dose, engineScheme, weight, concentration) as? CalcResult.Success)
                ?.value
                ?.let { true to it }
        }
    } else {
        formState.rateText.toBigDecimalOrNull()?.let { rate ->
            (InfusionEngine.dose(rate, engineScheme, weight, concentration) as? CalcResult.Success)
                ?.value
                ?.let { false to it }
        }
    }
}

internal fun computeRange(
    scheme: DoseScheme?,
    doseValue: BigDecimal?,
): RangeFlags {
    val doseMin = scheme?.doseMin
    val doseMax = scheme?.doseMax
    return RangeFlags(
        above = doseValue != null && doseMax != null && doseValue > doseMax,
        below = doseValue != null && doseMin != null && doseValue < doseMin,
    )
}
