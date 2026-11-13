# Ревью реализации TCI-карточки пропофола (повторное, после исправлений)

| Поле | Значение |
|---|---|
| Документ | `docs/REVIEW-TCI.md` |
| Задача | t6 / повторное ревью после исправлений t5 (первичное ревью — t4, версия 1.0) |
| Версия | 1.1 |
| Дата | 2026-11-13 |
| Автор | reviewer (команда `propofol-tci`) |
| Объект ревью | Ветка `feature/propofol-tci`, коммит `650f7b7` поверх `3a7abca` |
| Входные документы | `docs/SPEC-TCI.md` (v1.1, с отступлениями DEV-TCI-1/2), `docs/DESIGN-TCI.md`, `docs/research-tci.md` |
| Способ проверки | Статическое ревью кода + `git diff 3a7abca..650f7b7` + повторный прогон тестов (JVM) |

## Вердикт

**pass** — блокеры компиляции устранены, `:app` собирается, все тесты проходят, все 12 находок первичного ревью (TCI-R1…TCI-R12) закрыты; осознанные отступления зафиксированы в `SPEC-TCI.md`.

## 1. Результат сборки (приложен по требованию)

Команда: `$env:JAVA_HOME="C:\Users\fil\.jdks\jbr-17.0.14"; gradlew.bat --no-daemon :core-engine:test :app:testDebugUnitTest`

**BUILD SUCCESSFUL** (повторный прогон с `--rerun-tasks`, ~1м29с):

- `:core-engine:test` — **31/31** (0 failures / 0 errors / 0 skipped): `InfusionEngineTest` (15, регрессия базового движка «доза ⇄ скорость»), `TciEngineGoldenTest` G1–G10 (10), `TciQualityTest` (1), `TciSimulationTest` (5).
- `:app:testDebugUnitTest` — **48/48** (0 failures / 0 errors / 0 skipped): в т.ч. регрессия базового калькулятора (`CalculatorViewModelTest`, `EditorViewModelTest`, `CatalogViewModelTest`, `WeightViewModelTest`, `NavigationKeysTest`, `FormatTest`, `ConversionTest`, `CatalogSeedTest`, `CatalogSeederTest`, `DrugDaoTest`, `FolderDaoTest`, `DisclaimerScreenTest`) и `TciComputationTest` (в т.ч. новый тест «target warning follows the active phase»).
- `:app:compileDebugKotlin` — успешно (ошибки t4 устранены).

> Примечание: в выводе есть неблокирующие deprecation-предупреждения `createComposeRule` в `CatalogListRoborazziTest`/`DisclaimerScreenTest` — предсуществующие, к TCI не относятся, на сборку не влияют.

## 2. Закрытие находок первичного ревью (TCI-R1…TCI-R12)

| Находка | Статус | Как закрыто |
|---|---|---|
| TCI-R1 (blocker, smart cast `state.blocking`/`state.computed`) | ✅ закрыто | `TciScreen.kt`: `val blocking/computed/warnings` вынесены в локальные `val` (строки 83–85), используются в условиях и передачах |
| TCI-R2 (blocker, `stringResource` в `semantics`) | ✅ закрыто | `chartDescription` вычисляется до `Canvas` (строки 727–728), в `semantics` — готовая строка (738) |
| TCI-R3 (таблица скоростей поддержания) | ✅ закрыто | `BolusSection` рендерит раскрываемую таблицу (строки 555, 618–635), компонент C-TCI-4 |
| TCI-R4 (поле скорости болюса) | ✅ закрыто | `OutlinedTextField` «Скорость болюса, мл/ч» (строки 400–409), подключён `onBolusRateChange`, валидация `> 0` |
| TCI-R5 (предупреждение по фазе) | ✅ закрыто | `targetPhaseRange(form)` (индукция Cp 4–8 / поддержание 3–6; Ce 2.5–4), используется в `computeWarnings` и `WarningCard`; тест добавлен |
| TCI-R6 (тексты MSG-TCI-03/04) | ✅ закрыто | `tci_warn_bmi` + «Выберите Marsh или проверьте данные», `tci_warn_target` + `%2$s` и «Проверьте фазу и цель» (RU/EN) |
| TCI-R7 (баннер с иконкой/warning-цветами) | ✅ закрыто | `Icons.Filled.Warning` + `secondaryContainer` (в `ColorScheme` нет `statusWarningContainer` — осознанно выбран ближайший warning-контейнер) |
| TCI-R8 (подписи осей графика) | ✅ закрыто | `TextMeasurer`/`drawText`: ось Y (max/0, мкг/мл), ось X (0′/15′/30′/45′/60′ мин) |
| TCI-R9 (liveRegion) | ✅ закрыто | `BigValue(live = true)` → `semantics { liveRegion = LiveRegionMode.Polite }` для Cp/Ce и времени пробуждения |
| TCI-R10 (десятичный разделитель) | ✅ закрыто (отступление) | `DEV-TCI-2` в `SPEC-TCI.md` — наследует базовый `Format.display()` («.»); «,» и «.» принимаются на вводе |
| TCI-R11 (время пробуждения всегда) | ✅ закрыто (отступление) | `DEV-TCI-1` в `SPEC-TCI.md` + подпись `tci_wake_prediction` («Прогноз: если остановить инфузию сейчас») |
| TCI-R12 (high-alert маркировка) | ✅ закрыто | бейдж «TCI» в `errorContainer` (каталог и шапка), кнопка «Сверить» в `error`-акценте |

**Оставшиеся находки (незакрытые):** нет.

## 3. Осознанные отступления (зафиксированы в `docs/SPEC-TCI.md` § «Отступления v1.0»)

- **DEV-TCI-1** — время до пробуждения показывается постоянно (прогноз «если остановить сейчас»), а не только после отметки этапа «Пробуждение» (FR-TCI-STAGE-03). Обосновано D-TCI-12/DTC-3.
- **DEV-TCI-2** — десятичный разделитель в выводе наследует базовое приложение (всегда «.»), не локализуется в «,» для RU (NFR-TCI-I18N-01, DESIGN-TCI §5). Обосновано общей задачей продукта.

Оба отступления — осознанные, с обоснованием и владельцем; не блокируют выпуск.

## 4. Соответствие SPEC-TCI / DESIGN-TCI и макету экрана

- **Математика** (CALC-TCI-01…09): подтверждена golden G1–G10 и свойствами симуляции (31/31). ✅
- **Модель данных**: `TciModel{MARSH,SCHNIDER}`, `TargetMode{PLASMA,EFFECT_SITE}`, `Sex{MALE,FEMALE}`; поля пациента зависят от модели; данные не персистятся. ✅
- **Макет S-TCI** (DESIGN-TCI §2, 7 блоков сверху вниз): шапка с «Сверить» → баннер-дисклеймер → модель/режим → пациент → цель/концентрация (+скорость болюса) → чек-лист этапов → болюс/поддержание (+таблица скоростей) → график Cp/Ce + текущие значения → время до пробуждения. ✅
- **Безопасность**: high-alert маркировка + экран сверки (модель/режим/пациент/концентрация/цель/болюс/скорость); постоянный дисклеймер (не закрывается); предупреждения не модальные и не блокируют в технических пределах; рекомендаций по титрации нет («проверьте фазу и цель», без «увеличьте/уменьшите»). ✅ (⛔-TCI-1…7 соблюдены; запрет титрации — `⛔-TCI-4` — выполнен)
- **Локализация RU/EN**: полный набор строк в `values/strings.xml` и `values-ru/strings.xml`; единицы и этапы локализованы. ✅
- **Тёмная тема**: наследуется `InfusionCalcTheme` (`isSystemInDarkTheme`), `TciScreen` использует `MaterialTheme.colorScheme`. ✅
- **Доступность WCAG 2.2 AA**: крупные цифры `tnum`/`headlineSmall/Medium`; статусы этапов и легенда графика — не только цветом; график дублируется числами (`contentDescription` + крупные Cp/Ce); `liveRegion=Polite` для Cp/Ce и времени пробуждения; тач-цели и fontScale наследуются из базового приложения. ✅

## 5. Отсутствие регрессии базового калькулятора

`git diff --stat 3a7abca..650f7b7` затрагивает только TCI-файлы (`TciScreen`, `TciComputation`, `CatalogScreen` — 4 строки бейджа), строки и `docs/SPEC-TCI.md`. Базовый движок `InfusionEngine` и экраны калькулятора не изменены. `InfusionEngineTest` (15) и app-тесты базового калькулятора (в составе 48/48) проходят. ✅

## 6. Итог

Реализация TCI-карточки соответствует `SPEC-TCI`/`DESIGN-TCI`, движок проверен golden-тестами, `:app` собирается, регрессии базового калькулятора нет. Все находки первичного ревью закрыты. **Вердикт: pass.**
