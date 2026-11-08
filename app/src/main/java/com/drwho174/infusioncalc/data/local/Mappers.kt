package com.drwho174.infusioncalc.data.local

import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.DrugClass
import com.drwho174.infusioncalc.domain.model.Verification
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import java.math.BigDecimal
import com.drwho174.infusioncalc.domain.model.DoseScheme as DomainScheme

private fun String?.toDecimalOrNull(): BigDecimal? = this?.toBigDecimalOrNull()

private fun BigDecimal?.toText(): String? = this?.toPlainString()

fun DrugWithDetails.toDomain(): Drug =
    Drug(
        id = drug.id,
        nameRu = drug.nameRu,
        nameEn = drug.nameEn,
        drugClass = runCatching { DrugClass.valueOf(drug.drugClass) }.getOrDefault(DrugClass.OTHER),
        highAlert = drug.highAlert,
        route = drug.route,
        defaultSchemeId = drug.defaultSchemeId,
        isBuiltIn = drug.isBuiltIn,
        derivedFromId = drug.derivedFromId,
        verification = runCatching { Verification.valueOf(drug.verification) }.getOrDefault(Verification.USER),
        notes = drug.notes,
        incompatibilities = drug.incompatibilities,
        revision = drug.revision,
        catalogVersion = drug.catalogVersion,
        updatedAt = drug.updatedAt,
        archived = drug.archived,
        schemes = schemes.sortedBy { it.schemeOrder }.map { it.toDomain() },
        presets = presets.map { it.toDomain() },
    )

fun DoseSchemeEntity.toDomain(): DomainScheme =
    DomainScheme(
        id = id,
        drugId = drugId,
        amountUnit = runCatching { AmountUnit.valueOf(amountUnit) }.getOrDefault(AmountUnit.MG),
        perKg = perKg,
        perTime = runCatching { TimeBase.valueOf(perTime) }.getOrDefault(TimeBase.PER_HOUR),
        indication = indication,
        doseMin = doseMin.toDecimalOrNull(),
        doseMax = doseMax.toDecimalOrNull(),
        titrationStep = titrationStep.toDecimalOrNull(),
        maxDose = maxDose.toDecimalOrNull(),
        maxRate = maxRate.toDecimalOrNull(),
        editedByUser = editedByUser,
        schemeOrder = schemeOrder,
        roundingOverride = roundingOverride,
    )

fun ConcentrationPresetEntity.toDomain(): ConcentrationPreset =
    ConcentrationPreset(
        id = id,
        drugId = drugId,
        mode = runCatching { ConcentrationMode.valueOf(mode) }.getOrDefault(ConcentrationMode.MASS_VOLUME),
        amount = amount.toDecimalOrNull(),
        amountUnit = amountUnit?.let { runCatching { AmountUnit.valueOf(it) }.getOrNull() },
        volumeMl = volumeMl.toDecimalOrNull(),
        ampouleConcentration = ampouleConcentration.toDecimalOrNull(),
        ampouleVolumeMl = ampouleVolumeMl.toDecimalOrNull(),
        isDefault = isDefault,
        label = label,
    )

fun Drug.toEntity(): DrugEntity =
    DrugEntity(
        id = id,
        nameRu = nameRu,
        nameEn = nameEn,
        drugClass = drugClass.name,
        highAlert = highAlert,
        route = route,
        defaultSchemeId = defaultSchemeId,
        isBuiltIn = isBuiltIn,
        derivedFromId = derivedFromId,
        verification = verification.name,
        notes = notes,
        incompatibilities = incompatibilities,
        revision = revision,
        catalogVersion = catalogVersion,
        updatedAt = updatedAt,
        archived = archived,
    )

fun DomainScheme.toEntity(): DoseSchemeEntity =
    DoseSchemeEntity(
        id = id,
        drugId = drugId,
        amountUnit = amountUnit.name,
        perKg = perKg,
        perTime = perTime.name,
        indication = indication,
        doseMin = doseMin.toText(),
        doseMax = doseMax.toText(),
        titrationStep = titrationStep.toText(),
        maxDose = maxDose.toText(),
        maxRate = maxRate.toText(),
        editedByUser = editedByUser,
        schemeOrder = schemeOrder,
        roundingOverride = roundingOverride,
    )

fun ConcentrationPreset.toEntity(): ConcentrationPresetEntity =
    ConcentrationPresetEntity(
        id = id,
        drugId = drugId,
        mode = mode.name,
        amount = amount.toText(),
        amountUnit = amountUnit?.name,
        volumeMl = volumeMl.toText(),
        ampouleConcentration = ampouleConcentration.toText(),
        ampouleVolumeMl = ampouleVolumeMl.toText(),
        isDefault = isDefault,
        label = label,
    )
