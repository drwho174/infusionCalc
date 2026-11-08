package com.drwho174.infusioncalc.data.local

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

@Entity(tableName = "drugs")
data class DrugEntity(
    @PrimaryKey val id: String,
    val nameRu: String,
    val nameEn: String?,
    val drugClass: String,
    val highAlert: Boolean,
    val route: String,
    val defaultSchemeId: String?,
    val isBuiltIn: Boolean,
    val derivedFromId: String?,
    val verification: String,
    val notes: String?,
    val incompatibilities: String?,
    val revision: Int,
    val catalogVersion: Int,
    val updatedAt: Long,
    val archived: Boolean,
)

@Entity(
    tableName = "dose_schemes",
    foreignKeys = [
        ForeignKey(
            entity = DrugEntity::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("drugId")],
)
data class DoseSchemeEntity(
    @PrimaryKey val id: String,
    val drugId: String,
    val amountUnit: String,
    val perKg: Boolean,
    val perTime: String,
    val indication: String?,
    val doseMin: String?,
    val doseMax: String?,
    val titrationStep: String?,
    val maxDose: String?,
    val maxRate: String?,
    val editedByUser: Boolean,
    val schemeOrder: Int,
    val roundingOverride: Int?,
)

@Entity(
    tableName = "concentration_presets",
    foreignKeys = [
        ForeignKey(
            entity = DrugEntity::class,
            parentColumns = ["id"],
            childColumns = ["drugId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("drugId")],
)
data class ConcentrationPresetEntity(
    @PrimaryKey val id: String,
    val drugId: String,
    val mode: String,
    val amount: String?,
    val amountUnit: String?,
    val volumeMl: String?,
    val ampouleConcentration: String?,
    val ampouleVolumeMl: String?,
    val isDefault: Boolean,
    val label: String?,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val drugId: String,
    val createdAt: Long,
)

/** Препарат с вложенными схемами и пресетами концентраций. */
data class DrugWithDetails(
    @Embedded val drug: DrugEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["drugId"])
    val schemes: List<DoseSchemeEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["drugId"])
    val presets: List<ConcentrationPresetEntity>,
)
