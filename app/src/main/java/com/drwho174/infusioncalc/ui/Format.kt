package com.drwho174.infusioncalc.ui

import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import java.math.BigDecimal
import java.util.Locale

/** Язык интерфейса следует системной локали: русский — только для ru. */
fun isEnglishUi(): Boolean = Locale.getDefault().language != "ru"

fun BigDecimal?.display(): String {
    if (this == null) return "—"
    val stripped = this.stripTrailingZeros()
    return if (stripped.scale() < 0) stripped.setScale(0).toPlainString() else stripped.toPlainString()
}

/** Оставляет цифры и один десятичный разделитель (запятая → точка). */
fun normalizeDecimalInput(raw: String): String {
    val sb = StringBuilder()
    var separator = false
    for (ch in raw) {
        when {
            ch.isDigit() -> sb.append(ch)
            (ch == '.' || ch == ',') && !separator && sb.isNotEmpty() -> {
                separator = true
                sb.append('.')
            }
        }
    }
    return sb.toString()
}

fun amountUnitLabel(
    unit: AmountUnit,
    english: Boolean = isEnglishUi(),
): String =
    if (english) {
        when (unit) {
            AmountUnit.MCG -> "mcg"
            AmountUnit.MG -> "mg"
            AmountUnit.G -> "g"
            AmountUnit.UNIT -> "units"
            AmountUnit.IU -> "IU"
            AmountUnit.MEQ -> "mEq"
        }
    } else {
        when (unit) {
            AmountUnit.MCG -> "мкг"
            AmountUnit.MG -> "мг"
            AmountUnit.G -> "г"
            AmountUnit.UNIT -> "ЕД"
            AmountUnit.IU -> "МЕ"
            AmountUnit.MEQ -> "мэкв"
        }
    }

fun schemeLabel(
    unit: AmountUnit,
    perKg: Boolean,
    perTime: TimeBase,
    english: Boolean = isEnglishUi(),
): String {
    val base = amountUnitLabel(unit, english)
    val perKgPart = if (perKg) (if (english) "/kg" else "/кг") else ""
    val timePart =
        when (perTime) {
            TimeBase.PER_MINUTE -> if (english) "/min" else "/мин"
            TimeBase.PER_HOUR -> if (english) "/h" else "/ч"
        }
    return base + perKgPart + timePart
}

fun com.drwho174.infusioncalc.domain.model.ConcentrationPreset.displayLabel(english: Boolean = isEnglishUi()): String {
    val unit = amountUnit?.let { amountUnitLabel(it, english) } ?: ""
    val ml = if (english) "mL" else "мл"
    return when (mode) {
        com.drwho174.infusioncalc.domain.model.ConcentrationMode.READY ->
            "${amount.display()} $unit/$ml"
        com.drwho174.infusioncalc.domain.model.ConcentrationMode.MASS_VOLUME ->
            "${amount.display()} $unit / ${volumeMl.display()} $ml = ${concentrationIn().display()} $unit/$ml"
        com.drwho174.infusioncalc.domain.model.ConcentrationMode.AMPOULE ->
            "${concentrationIn().display()} $unit/$ml"
    }
}
