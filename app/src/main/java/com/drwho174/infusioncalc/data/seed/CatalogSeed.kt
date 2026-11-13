package com.drwho174.infusioncalc.data.seed

import com.drwho174.infusioncalc.domain.model.ConcentrationMode
import com.drwho174.infusioncalc.domain.model.ConcentrationPreset
import com.drwho174.infusioncalc.domain.model.DoseScheme
import com.drwho174.infusioncalc.domain.model.Drug
import com.drwho174.infusioncalc.domain.model.DrugClass
import com.drwho174.infusioncalc.domain.model.Verification
import com.drwho174.infusioncalc.engine.AmountUnit
import com.drwho174.infusioncalc.engine.TimeBase
import java.math.BigDecimal

/**
 * Встроенный каталог препаратов (справочные дозы и типовые разведения).
 * Значения ориентировочные; перед клиническим применением сверяйте с местным протоколом
 * и инструкцией. Пользователь может скопировать карточку в «Мои» и изменить.
 */
object CatalogSeed {
    const val CATALOG_VERSION = 2

    private fun scheme(
        drugId: String,
        unit: AmountUnit,
        perKg: Boolean,
        perTime: TimeBase,
        min: String? = null,
        max: String? = null,
        indication: String? = null,
    ) = DoseScheme(
        id = "${drugId}_scheme",
        drugId = drugId,
        amountUnit = unit,
        perKg = perKg,
        perTime = perTime,
        indication = indication,
        doseMin = min?.let { BigDecimal(it) },
        doseMax = max?.let { BigDecimal(it) },
        schemeOrder = 0,
    )

    private fun massPreset(
        drugId: String,
        index: Int,
        amount: String,
        unit: AmountUnit,
        volume: String,
        default: Boolean = false,
    ) = ConcentrationPreset(
        id = "${drugId}_p$index",
        drugId = drugId,
        mode = ConcentrationMode.MASS_VOLUME,
        amount = BigDecimal(amount),
        amountUnit = unit,
        volumeMl = BigDecimal(volume),
        isDefault = default,
    )

    private fun readyPreset(
        drugId: String,
        index: Int,
        concentration: String,
        unit: AmountUnit,
        default: Boolean = false,
    ) = ConcentrationPreset(
        id = "${drugId}_p$index",
        drugId = drugId,
        mode = ConcentrationMode.READY,
        amount = BigDecimal(concentration),
        amountUnit = unit,
        isDefault = default,
    )

    private fun drug(
        id: String,
        nameRu: String,
        nameEn: String,
        drugClass: DrugClass,
        highAlert: Boolean,
        route: String,
        scheme: DoseScheme,
        presets: List<ConcentrationPreset>,
        notes: String? = null,
    ) = Drug(
        id = id,
        nameRu = nameRu,
        nameEn = nameEn,
        drugClass = drugClass,
        highAlert = highAlert,
        route = route,
        defaultSchemeId = scheme.id,
        isBuiltIn = true,
        verification = Verification.VERIFIED,
        notes = notes,
        revision = 1,
        catalogVersion = CATALOG_VERSION,
        updatedAt = 0L,
        schemes = listOf(scheme),
        presets = presets,
    )

    fun drugs(): List<Drug> =
        listOf(
            drug(
                id = "norepinephrine",
                nameRu = "Норадреналин",
                nameEn = "Norepinephrine",
                drugClass = DrugClass.VASOPRESSOR,
                highAlert = true,
                route = "в/в, крупная вена",
                scheme = scheme("norepinephrine", AmountUnit.MCG, perKg = true, TimeBase.PER_MINUTE, "0.01", "3"),
                presets =
                    listOf(
                        massPreset("norepinephrine", 1, "2", AmountUnit.MG, "20", default = true),
                        massPreset("norepinephrine", 2, "4", AmountUnit.MG, "20"),
                        massPreset("norepinephrine", 3, "4", AmountUnit.MG, "50"),
                        massPreset("norepinephrine", 4, "8", AmountUnit.MG, "50"),
                        massPreset("norepinephrine", 5, "16", AmountUnit.MG, "50"),
                    ),
                notes = "Титрация по АД; экстравазация → некроз (антидот фентоламин).",
            ),
            drug(
                id = "epinephrine",
                nameRu = "Адреналин",
                nameEn = "Epinephrine",
                drugClass = DrugClass.VASOPRESSOR,
                highAlert = true,
                route = "в/в",
                scheme = scheme("epinephrine", AmountUnit.MCG, perKg = true, TimeBase.PER_MINUTE, "0.01", "0.5"),
                presets =
                    listOf(
                        massPreset("epinephrine", 1, "1", AmountUnit.MG, "20", default = true),
                        massPreset("epinephrine", 2, "3", AmountUnit.MG, "20"),
                        massPreset("epinephrine", 3, "5", AmountUnit.MG, "20"),
                    ),
                notes = "Инфузия для непрерывного введения; болюсные дозы — по протоколу.",
            ),
            drug(
                id = "dobutamine",
                nameRu = "Добутамин",
                nameEn = "Dobutamine",
                drugClass = DrugClass.INOTROPE,
                highAlert = false,
                route = "в/в",
                scheme = scheme("dobutamine", AmountUnit.MCG, perKg = true, TimeBase.PER_MINUTE, "2", "20"),
                presets =
                    listOf(
                        massPreset("dobutamine", 1, "250", AmountUnit.MG, "20", default = true),
                        massPreset("dobutamine", 2, "250", AmountUnit.MG, "50"),
                    ),
                notes = "Инотропная поддержка; следить за тахикардией и аритмией.",
            ),
            drug(
                id = "levosimendan",
                nameRu = "Левосимендан",
                nameEn = "Levosimendan",
                drugClass = DrugClass.INOTROPE,
                highAlert = false,
                route = "в/в",
                scheme = scheme("levosimendan", AmountUnit.MCG, perKg = true, TimeBase.PER_MINUTE, "0.05", "0.2"),
                presets =
                    listOf(
                        massPreset("levosimendan", 1, "12.5", AmountUnit.MG, "50", default = true),
                    ),
                notes = "Нагрузочная доза — отдельно по протоколу; возможна гипотензия.",
            ),
            drug(
                id = "propofol",
                nameRu = "Пропофол",
                nameEn = "Propofol",
                drugClass = DrugClass.SEDATIVE,
                highAlert = true,
                route = "в/в",
                scheme = scheme("propofol", AmountUnit.MG, perKg = true, TimeBase.PER_HOUR, "0.5", "4"),
                presets =
                    listOf(
                        massPreset("propofol", 1, "200", AmountUnit.MG, "20", default = true),
                        massPreset("propofol", 2, "500", AmountUnit.MG, "50"),
                    ),
                notes = "1% = 10 мг/мл, 2% = 20 мг/мл. Седация 1–4 мг/кг/ч.",
            ),
            drug(
                id = "dexmedetomidine",
                nameRu = "Дексдор (дексмедетомидин)",
                nameEn = "Dexmedetomidine",
                drugClass = DrugClass.SEDATIVE,
                highAlert = true,
                route = "в/в",
                scheme = scheme("dexmedetomidine", AmountUnit.MCG, perKg = true, TimeBase.PER_HOUR, "0.2", "1.4"),
                presets =
                    listOf(
                        massPreset("dexmedetomidine", 1, "200", AmountUnit.MCG, "20", default = true),
                        massPreset("dexmedetomidine", 2, "400", AmountUnit.MCG, "20"),
                    ),
                notes = "Седация без глубокой депрессии дыхания; брадикардия и гипотензия.",
            ),
            drug(
                id = "rocuronium",
                nameRu = "Рокуроний",
                nameEn = "Rocuronium",
                drugClass = DrugClass.MUSCLE_RELAXANT,
                highAlert = true,
                route = "в/в",
                scheme = scheme("rocuronium", AmountUnit.MG, perKg = true, TimeBase.PER_HOUR, "0.3", "0.6"),
                presets =
                    listOf(
                        massPreset("rocuronium", 1, "200", AmountUnit.MG, "20", default = true),
                        massPreset("rocuronium", 2, "100", AmountUnit.MG, "20"),
                    ),
                notes = "Недеполяризующий миорелаксант; инфузия при длительной релаксации.",
            ),
            drug(
                id = "nitroglycerin",
                nameRu = "Нитроглицерин",
                nameEn = "Nitroglycerin",
                drugClass = DrugClass.VASODILATOR,
                highAlert = false,
                route = "в/в",
                scheme = scheme("nitroglycerin", AmountUnit.MCG, perKg = false, TimeBase.PER_MINUTE, "5", "200"),
                presets =
                    listOf(
                        readyPreset("nitroglycerin", 1, "10", AmountUnit.MG, default = true),
                        readyPreset("nitroglycerin", 2, "5", AmountUnit.MG),
                    ),
                notes = "Титрация по АД; толерантность при длительной инфузии.",
            ),
            drug(
                id = "dopamine",
                nameRu = "Допамин",
                nameEn = "Dopamine",
                drugClass = DrugClass.VASOPRESSOR,
                highAlert = true,
                route = "в/в, крупная вена",
                scheme = scheme("dopamine", AmountUnit.MCG, perKg = true, TimeBase.PER_MINUTE, "1", "20"),
                presets =
                    listOf(
                        massPreset("dopamine", 1, "200", AmountUnit.MG, "20", default = true),
                        massPreset("dopamine", 2, "200", AmountUnit.MG, "50"),
                    ),
                notes = "1–3 мкг/кг/мин — «почечная», 2–10 — инотропная, 10–20 — вазопрессорная.",
            ),
            drug(
                id = "midazolam",
                nameRu = "Мидазолам",
                nameEn = "Midazolam",
                drugClass = DrugClass.SEDATIVE,
                highAlert = true,
                route = "в/в",
                scheme = scheme("midazolam", AmountUnit.MG, perKg = true, TimeBase.PER_HOUR, "0.02", "0.1"),
                presets =
                    listOf(
                        massPreset("midazolam", 1, "15", AmountUnit.MG, "20", default = true),
                        massPreset("midazolam", 2, "45", AmountUnit.MG, "20"),
                    ),
                notes = "Седация; риск угнетения дыхания, особенно с опиоидами.",
            ),
            drug(
                id = "fentanyl",
                nameRu = "Фентанил",
                nameEn = "Fentanyl",
                drugClass = DrugClass.OTHER,
                highAlert = true,
                route = "в/в",
                scheme = scheme("fentanyl", AmountUnit.MCG, perKg = true, TimeBase.PER_HOUR),
                presets =
                    listOf(
                        massPreset("fentanyl", 1, "1", AmountUnit.MG, "20", default = true),
                        massPreset("fentanyl", 2, "0.5", AmountUnit.MG, "20"),
                    ),
                notes = "Опиоидный анальгетик; риск угнетения дыхания, особенно с бензодиазепинами.",
            ),
        )
}
