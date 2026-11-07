# -*- coding: utf-8 -*-
"""v1.1 техплана: модель без МНН/источника, пресеты, вес сессии, экран S-W."""
import io, sys

PATH = r"C:\StudioProjects\infusionCalc\docs\TECH-PLAN.md"
with io.open(PATH, encoding="utf-8") as fh:
    text = fh.read()

R = []
def add(old, new):
    R.append((old, new))

add("1. **`Drug`** (21 поле, SPEC §5.2): `id` (slug/UUID), `nameRu/nameEn`, `innRu/innEn`, `tradeNames`, `drugClass`, `highAlert`, `route`, `defaultSchemeId` (FK), `isBuiltIn`, `derivedFromId`, `verification` (`VERIFIED/NEEDS_REVIEW/USER`), `sourceTitle/sourceUrl/sourceDate`, `verifiedAt`, `notes`, `incompatibilities`, `revision`, `catalogVersion`, `updatedAt`, `archived`.",
    "1. **`Drug`** (v1.1, SPEC §5.2): `id` (slug/UUID), `nameRu/nameEn`, `drugClass`, `highAlert`, `route`, `defaultSchemeId` (FK), `isBuiltIn`, `derivedFromId`, `verification` (`VERIFIED/NEEDS_REVIEW/USER` — внутренний признак), `notes`, `incompatibilities`, `revision`, `catalogVersion`, `updatedAt`, `archived`. МНН, торговые названия и источник в карточке/форме не используются (v1.1); `source*`-поля — внутренние метаданные каталога.")

add("`sourceUnitsLabel`, `editedByUser`",
    "`editedByUser`")

add("3. **`ConcentrationPreset`**: `mode` (`READY`/`MASS_VOLUME`/`AMPOULE`), `amount`+`amountUnit`, `volumeMl`, `ampouleConcentration`+`ampouleVolumeMl`, `isDefault`, `label`.",
    "3. **`ConcentrationPreset`** (выбор в S-2, редактирование в S-4): `mode` (`READY`/`MASS_VOLUME`/`AMPOULE`), `amount`+`amountUnit`, `volumeMl`, `ampouleConcentration`+`ampouleVolumeMl`, `isDefault`, `label`.")

add("5. **`Source`**: `kind` (`LABEL/PROTOCOL/USER`), `title`, `edition`, `date`, `url`.",
    "5. **`Source`**: `kind` (`LABEL/PROTOCOL/USER`), `title`, `edition`, `date`, `url` — внутренние метаданные каталога, в UI не отображаются.")

add("7. **DataStore Preferences**: `disclaimerAcceptedAt`, `disclaimerVersion`, `theme`, `language`, `pumpStep`. Никаких данных пациента.",
    "7. **DataStore Preferences**: `disclaimerAcceptedAt`, `disclaimerVersion`, `theme`, `language`, `pumpStep`. **Вес сессии** хранится только в памяти (не на диске) и запрашивается при каждом запуске (S-W, FR-WEIGHT-01). Никаких данных пациента.")

add("Инварианты (SPEC §5.5, DATA-INV-01…10) проверяются валидатором и тестами: `VERIFIED` ⇒ `sourceTitle`+`sourceDate`; у препарата ≥1 `DoseScheme` и ≥1 `ConcentrationPreset`;",
    "Инварианты (SPEC §5.5, DATA-INV-01…10) проверяются валидатором и тестами: `verification` — внутренний признак (в UI не редактируется); у препарата ≥1 `DoseScheme` и ≥1 `ConcentrationPreset`;")

add("- Диалоги/листы — `DialogScene`/`OverlayScene` поверх записи стека (форма под ними не теряет ввод): подтверждение смены схемы, лист концентрации, экран сверки high-alert, лист суточной дозы.",
    "- Диалоги/листы — `DialogScene`/`OverlayScene` поверх записи стека (форма под ними не теряет ввод): подтверждение смены схемы, лист **выбора пресета концентрации**, экран сверки high-alert, лист суточной дозы.\n- **S-W «Вес пациента»** — отдельный экран при запуске (после дисклеймера, до списка); вес держится в `WeightStore` (in-memory, `Application`-скоуп), не в Room/DataStore. Стартовый ключ back stack: S-0 → S-W → S-1 или (если дисклеймер принят) S-W → S-1.")

missing = []
for i, (old, new) in enumerate(R):
    if text.count(old) != 1:
        missing.append((i, text.count(old), old[:70].replace("\n", " ")))
if missing:
    print("FAILED:", len(missing))
    for m in missing:
        print("  idx", m[0], "count", m[1], "|", m[2])
    sys.exit(1)

for old, new in R:
    text = text.replace(old, new, 1)

with io.open(PATH, "w", encoding="utf-8") as fh:
    fh.write(text)
print("OK: применено замен:", len(R))
