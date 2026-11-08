package com.drwho174.infusioncalc.domain.model

import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import java.math.BigDecimal
import java.math.MathContext
import com.drwho174.infusioncalc.engine.DoseScheme as EngineScheme

enum class ConcentrationMode { READY, MASS_VOLUME, AMPOULE }

enum class Verification { VERIFIED, NEEDS_REVIEW, USER }

enum class DrugClass {
    VASOPRESSOR,
    INOTROPE,
    VASODILATOR,
    ANTIARRHYTHMIC,
    SEDATIVE,
    ANTICOAGULANT,
    ELECTROLYTE,
    MUSCLE_RELAXANT,
    OTHER,
}

/** Множитель к микрограммам для «массовых» единиц; для ЕД/МЕ/мэкв конвертации нет (D-14). */
private fun AmountUnit.massFactorToMcg(): BigDecimal? =
    when (this) {
        AmountUnit.MCG -> BigDecimal.ONE
        AmountUnit.MG -> BigDecimal("1000")
        AmountUnit.G -> BigDecimal("1000000")
        else -> null
    }

/**
 * Пересчёт количества между «массовыми» единицами (мкг/мг/г).
 * ЕД ↔ МЕ не конвертируются (разные токены, D-14); мэкв — отдельная размерность.
 */
fun convertAmountUnit(
    value: BigDecimal,
    from: AmountUnit,
    to: AmountUnit,
): BigDecimal? {
    if (from == to) return value
    val fromFactor = from.massFactorToMcg() ?: return null
    val toFactor = to.massFactorToMcg() ?: return null
    return value.multiply(fromFactor).divide(toFactor, MathContext(34))
}

/** Схема дозирования препарата. Индикация — часть ключа дозирования (D-3). */
data class DoseScheme(
    val id: String,
    val drugId: String,
    val amountUnit: AmountUnit,
    val perKg: Boolean,
    val perTime: TimeBase,
    val indication: String? = null,
    val doseMin: BigDecimal? = null,
    val doseMax: BigDecimal? = null,
    val titrationStep: BigDecimal? = null,
    val maxDose: BigDecimal? = null,
    val maxRate: BigDecimal? = null,
    val editedByUser: Boolean = false,
    val schemeOrder: Int = 0,
    val roundingOverride: Int? = null,
) {
    fun toEngine(): EngineScheme = EngineScheme(amountUnit = amountUnit, perKg = perKg, perTime = perTime)
}

/** Предустановленная концентрация препарата (пресет). */
data class ConcentrationPreset(
    val id: String,
    val drugId: String,
    val mode: ConcentrationMode,
    val amount: BigDecimal? = null,
    val amountUnit: AmountUnit? = null,
    val volumeMl: BigDecimal? = null,
    val ampouleConcentration: BigDecimal? = null,
    val ampouleVolumeMl: BigDecimal? = null,
    val isDefault: Boolean = false,
    val label: String? = null,
) {
    /**
     * Концентрация в единицах [target] на мл или null, если данных недостаточно
     * или единицы несовместимы (например, мг и ЕД).
     */
    fun concentrationIn(target: AmountUnit? = null): BigDecimal? {
        val own = amountUnit
        val raw =
            when (mode) {
                ConcentrationMode.READY -> amount
                ConcentrationMode.MASS_VOLUME -> {
                    val mass = amount
                    val volume = volumeMl
                    if (mass != null && volume != null && volume.signum() > 0) {
                        mass.divide(volume, MathContext(34))
                    } else {
                        null
                    }
                }
                ConcentrationMode.AMPOULE -> {
                    val conc = ampouleConcentration
                    val ampouleVolume = ampouleVolumeMl
                    val volume = volumeMl
                    if (conc != null && ampouleVolume != null && volume != null && volume.signum() > 0) {
                        conc.multiply(ampouleVolume).divide(volume, MathContext(34))
                    } else {
                        null
                    }
                }
            } ?: return null
        if (target == null || own == null || own == target) return raw
        return convertAmountUnit(raw, own, target)
    }
}

/** Карточка препарата с его схемами и концентрациями. */
data class Drug(
    val id: String,
    val nameRu: String,
    val nameEn: String? = null,
    val drugClass: DrugClass = DrugClass.OTHER,
    val highAlert: Boolean = false,
    val route: String = "",
    val defaultSchemeId: String? = null,
    val isBuiltIn: Boolean = false,
    val derivedFromId: String? = null,
    val verification: Verification = Verification.USER,
    val notes: String? = null,
    val incompatibilities: String? = null,
    val revision: Int = 1,
    val catalogVersion: Int = 0,
    val updatedAt: Long = 0,
    val archived: Boolean = false,
    val schemes: List<DoseScheme> = emptyList(),
    val presets: List<ConcentrationPreset> = emptyList(),
) {
    val defaultScheme: DoseScheme?
        get() =
            schemes.firstOrNull { it.id == defaultSchemeId }
                ?: schemes.minByOrNull { it.schemeOrder }
                ?: schemes.firstOrNull()

    val defaultPreset: ConcentrationPreset?
        get() = presets.firstOrNull { it.isDefault } ?: presets.firstOrNull()

    fun localizedName(english: Boolean): String = if (english) (nameEn ?: nameRu) else nameRu
}
