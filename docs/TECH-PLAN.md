# Технический план: Android-приложение «Калькулятор скорости инфузии»

| Поле | Значение |
|---|---|
| Документ | `docs/TECH-PLAN.md` — план реализации до написания кода |
| Версия | 1.0 |
| Дата | 2026-11-07 |
| Входы | `docs/SPEC.md` (требования, модель данных — **нормативны**), `docs/research-stack.md` (версии и архитектурные решения), `docs/DESIGN.md` (дизайн), `docs/research-clinical.md` (формулы) |
| Приоритет источников | **SPEC выше этого документа и выше research-stack.** Где research-stack расходится с SPEC — действует SPEC (см. §1) |

---

## 0. Как читать

Стек и версии берутся из `research-stack.md` (проверены 2026-11-07 по Google Maven / Maven Central / Gradle / AAR-метаданным). Модель данных и правила расчёта — из `SPEC.md` §5–§6. Этот документ не переопределяет требования, а фиксирует, **как** их реализовать.

Явные места, где SPEC правит research-stack:
- research-stack §5.2 описывает плоскую модель (`doseUnit` + `doseBasis` + `doseTimeBase`); SPEC заменил её на `Drug` + `DoseScheme` с индикацией (D-3). Реализуем **SPEC §5**.
- research-stack §5.4 упоминает «переключатель направления»; SPEC D-1 запрещает переключатели. Направление — состояние формы (какое поле изменено последним), без видимого тумблера.

---

## 1. Целевой стек (версии зафиксированы)

| Группа | Артефакт / версия |
|---|---|
| Язык | Kotlin **2.4.21** |
| Сборка | AGP **9.5.1** (built-in Kotlin — плагин `kotlin-android` не применяется), Gradle **9.8.2**, JDK daemon **21 LTS**, `compileOptions` 17 |
| SDK | `compileSdk 37` (требование Compose UI 1.12.1 `minCompileSdk`), `targetSdk 36` (требование Play с 31.08.2026), `minSdk 24` (требование Navigation 3) |
| Compose | BOM **2026.09.00** → UI 1.12.1, `material3` **1.4.0**, `material3-adaptive` 1.3.0; компилятор — плагин `org.jetbrains.kotlin.plugin.compose` |
| Навигация | Navigation 3 **1.2.1**: `navigation3-runtime`, `navigation3-ui`, `lifecycle-viewmodel-navigation3` |
| БД | Room **3.0.3** (`androidx.room3`) + `room3-gradle-plugin`, драйвер `BundledSQLiteDriver`; план Б — Room 2.8.5 |
| Кодогенерация | KSP **2.3.13** (только для Room) |
| Сериализация | `kotlinx-serialization-json` **1.12.0** (+ плагин версии Kotlin) |
| Настройки | DataStore Preferences **1.3.0** |
| DI | Ручной `AppContainer` (Hilt 2.60.1 — альтернатива, см. §8) |
| Тесты | JUnit 4.13.2, `kotlinx-coroutines-test` 1.11.0, Robolectric **4.17**, Roborazzi **1.77.0**, `ui-test-junit4-accessibility` 1.12.1, Espresso 3.7.0 (3.8.0-alpha01 для API 37) |

Обоснования каждого выбора — `research-stack.md` §3; ключевые: built-in Kotlin в AGP 9, `compileSdk 37` продиктован Compose, `minSdk 24` — Navigation 3, Room 3.0.3 выбран ради KSP-only + `BundledSQLiteDriver`, ручной DI — ради скорости сборки и простоты тестов.

---

## 2. Модули и слои

```
:core-engine   — чистый Kotlin/JVM. Без Android/Compose/Room. BigDecimal-математика,
                 формулы, округление, валидация, safety-правила, парсинг/форматирование чисел.
:app           — Android: Compose UI, Navigation 3, ViewModel, Room 3, DataStore, ручной DI.
```

Пакеты в `:app` (база `com.drwho174.infusioncalc`):

```
app/            Application, AppContainer, MainActivity
ui/             theme (Color.kt, Theme.kt, Type.kt), components, navigation (ключи, NavDisplay),
                screens/{calculator, catalog, drugcard, drugeditor, settings, about, disclaimer}
domain/         модели (Drug, DoseScheme, ConcentrationPreset, Limit, Source…), интерфейсы репозиториев, use cases
data/           Room 3 (entities, dao, converters, migrations, database, seed), DataStore, репозитории, mappers
core/           Result-обёртки, локализованные форматтеры, диспетчеры
```

Правило зависимостей: `ui → domain ← data`; `domain → :core-engine`; `data` не знает про `ui`; `:core-engine` не знает ни про кого. UI не выполняет арифметику.

---

## 3. Модель данных (Room 3.0.3) — по SPEC §5

БД `infusioncalc.db`, драйвер `BundledSQLiteDriver` (единая версия SQLite, детерминизм тестов). Десятичные — только `TEXT` (`"0.1"`), никогда `REAL`. Перечисления — `TEXT`-коды. Даты — `INTEGER` epoch millis UTC. Разрешения `INTERNET` в манифесте нет (D-11, NFR-PRIV-01).

Сущности:

1. **`Drug`** (v1.1, SPEC §5.2): `id` (slug/UUID), `nameRu/nameEn`, `drugClass`, `highAlert`, `route`, `defaultSchemeId` (FK), `isBuiltIn`, `derivedFromId`, `verification` (`VERIFIED/NEEDS_REVIEW/USER` — внутренний признак), `notes`, `incompatibilities`, `revision`, `catalogVersion`, `updatedAt`, `archived`. МНН, торговые названия и источник в карточке/форме не используются (v1.1); `source*`-поля — внутренние метаданные каталога.
2. **`DoseScheme`** (14 полей, SPEC §5.3) — ядро: `id`, `drugId`, `amountUnit` (`MCG/MG/G/UNIT/IU/MEQ`), `perKg`, `perTime` (`PER_MINUTE/PER_HOUR`), `indication`, `doseMin/doseMax`, `titrationStep`, `maxDose`, `maxRate`, `editedByUser`, `schemeOrder`, `roundingOverride`. Индикация — часть ключа дозирования (вазопрессин 0,01 vs 0,03 ЕД/мин; амиодарон нагрузка/поддержка).
3. **`ConcentrationPreset`** (выбор в S-2, редактирование в S-4): `mode` (`READY`/`MASS_VOLUME`/`AMPOULE`), `amount`+`amountUnit`, `volumeMl`, `ampouleConcentration`+`ampouleVolumeMl`, `isDefault`, `label`.
4. **`Limit`**: `kind` (`SOFT_WARN`/`CRITICAL_WARN`/`HARD_BLOCK`), `value`+`valueUnit`, `sourceId`.
5. **`Source`**: `kind` (`LABEL/PROTOCOL/USER`), `title`, `edition`, `date`, `url` — внутренние метаданные каталога, в UI не отображаются.
6. **`Favorite`**: `drugId`, `createdAt`.
7. **DataStore Preferences**: `disclaimerAcceptedAt`, `disclaimerVersion`, `theme`, `language`, `pumpStep`. **Вес сессии** хранится только в памяти (не на диске) и запрашивается при каждом запуске (S-W, FR-WEIGHT-01). Никаких данных пациента.

Инварианты (SPEC §5.5, DATA-INV-01…10) проверяются валидатором и тестами: `verification` — внутренний признак (в UI не редактируется); у препарата ≥1 `DoseScheme` и ≥1 `ConcentrationPreset`; `doseMin < doseMax`; `HARD_BLOCK` ⇒ `sourceId`; каталожный препарат неизменяем (UPDATE/DELETE отклоняются репозиторием); десятичные — `TEXT` + `BigDecimal` из строки; никаких ПДн/веса-профиля.

Каталог: prepopulate + `catalogVersion`; обновление — только с версией приложения (без сети); пользовательские правки сохраняются поверх обновления (FR-DATA-01…05). Миграции обязательны, `schemaDirectory` включён.

---

## 4. Вычислительный движок (`:core-engine`)

Чистые функции без состояния, без `Flow`. Вход/выход — value-классы (`DoseInput`, `Weight`, `Concentration`, `RateResult`).

Формулы (SPEC §6, research-clinical §3): `D` — доза, `W` — вес кг, `C` — концентрация (ед/мл), `Q` — скорость мл/ч, `k = 60` при `/мин`, `1` при `/ч`.

| Направление | Формула |
|---|---|
| Доза → скорость (на кг) | `Q = D × k × W / C` |
| Доза → скорость (абсолютная) | `Q = D × k / C` |
| Скорость → доза (на кг) | `D = Q × C / (k × W)` |
| Скорость → доза (абсолютная) | `D = Q × C / k` |

Нормализация суточной дозы (CALC-32): суточная доза ÷ 24 → часовая перед расчётом (гепарин T10/T11, лист S-2d).

Концентрация — один из трёх режимов пресета (`READY` / `MASS_VOLUME` = `amount/volume` / `AMPOULE`), нормализуется к ед/мл.

**`BigDecimal`, не `Double`** (research-stack §6.3, прогон на JVM): парсинг из строки `new BigDecimal("0.1")`; вычисления `MathContext.DECIMAL128`; округление `HALF_UP` **один раз на выходе**; сравнения — `compareTo`, никогда `equals`; в домене нет `Double`/`Float`.

Политика точности (SPEC §6): ввод до 4 знаков; вес 20–200 кг (OQ-2, настраивается); скорость — 1 знак (при `Q < 1` — 2); доза — 3 значимые цифры, ≤4 знака после разделителя; концентрация — 3 значимые цифры; `titrationStep` (вазопрессин 0,005) задаёт минимум 3 знака после запятой. Round-trip `dose(rate(d)) ≈ d` в пределах шага отображения — проверяется всегда, в усиленном режиме расхождение = блокирующая ошибка.

Контракт результата: `Success(value, warnings)` / `Invalid(errors)`; причины отказа типизированы (`ZERO_WEIGHT`, `ZERO_CONCENTRATION`, `NEGATIVE_INPUT`, `UNIT_MISMATCH`, `TOO_MANY_DECIMALS`, `OUT_OF_SAFE_RANGE`, `REQUIRES_CONFIRMATION`). Никаких `NaN`/`Infinity`.

Тесты движка (`:core-engine/src/test`, данные в `*.csv`): golden-кейсы T1–T15 (из research-clinical §3.6), property (монотонность, однородность, round-trip), границы/отказы, единицы, округление (`2.675 → 2.68`), локализация (`0,5`/`0.5`), отсутствие `Double` (архитектурный тест/правило).

---

## 5. Навигация (Navigation 3 1.2.1)

- Одна `ComponentActivity`, `enableEdgeToEdge()` до `setContent`, внутри `NavDisplay`.
- Back stack — данные: `rememberNavBackStack(<стартовый ключ>)`; ключи — `@Serializable` (`data object`/`data class : NavKey`), параметры — в конструкторе ключа (не `Bundle`).
- Декораторы (порядок важен): `rememberSaveableStateHolderNavEntryDecorator()` → `rememberViewModelStoreNavEntryDecorator()` (ViewModel привязана к `NavEntry`) → при необходимости `rememberResultEventBusNavEntryDecorator()`.
- Result API — возврат данных «назад» из `DrugEditorScreen`/`CatalogScreen` через `ResultEventBus.sendResult<T>()` + `ResultEffect<T>{}` на получателе (есть в 1.2.1).
- Диалоги/листы — `DialogScene`/`OverlayScene` поверх записи стека (форма под ними не теряет ввод): подтверждение смены схемы, лист **выбора пресета концентрации**, экран сверки high-alert, лист суточной дозы.
- **S-W «Вес пациента»** — отдельный экран при запуске (после дисклеймера, до списка); вес держится в `WeightStore` (in-memory, `Application`-скоуп), не в Room/DataStore. Стартовый ключ back stack: S-0 → S-W → S-1 или (если дисклеймер принят) S-W → S-1.
- Back — единственная точка `onBack = { backStack.removeLastOrNull() }`; перехват «есть несохранённые изменения» — `NavigationBackHandler` на экране.
- Deep links, предиктивный pop-жест, метаданные заголовков — **не в MVP** (1.2.1, D-…).
- Навигация тестируется как данные: добавили/убрали ключ → проверили содержимое и порядок.

---

## 6. UI-слой

- Тема — `Color.kt`/`Theme.kt`/`Type.kt` по образцу Calc1: палитра перенесена один-в-один, dynamic color **выключен**; роли `surfaceContainer*` заданы явно (в Calc1 их нет — иначе Compose подставит базовые M3). Сигнальные цвета — парами «контейнер/контент/иконка» (DESIGN §7.4).
- Экран = `@Composable` + `ViewModel` c `StateFlow<UiState>` (иммутабельный `data class`); события — sealed `UiEvent`; математики в composable нет.
- **Единая форма (D-1)**: поля «Доза» и «Скорость» оба редактируемы; источник — последнее изменённое поле, вычисленное помечается «≈»; переключателя направления нет. Пересчёт синхронный, не изменяет поле в фокусе, округляет только для отображения.
- Ввод чисел по N-1…N-14 (DESIGN §5.2), состояние поля `Empty/Partial/Valid/Error`; `tnum` для табличных цифр; IME-инсеты (`imePadding`), edge-to-edge.
- Усиленный режим: предупреждения не модальные и не отключаются; `SOFT_WARN`/`CRITICAL_WARN` из `Limit`; `HARD_BLOCK` — только при источнике.
- Доступность: `enableAccessibilityChecks()`, семантика `heading()`/`stateDescription`/`error()`, live-region результата с задержкой 600 мс, тач-цели 48dp.

---

## 7. Локализация RU / EN

- `values/strings.xml` (базовая локаль EN) + `values-ru/strings.xml`; строк в коде нет; единицы — тоже ресурсы.
- `androidResources { generateLocaleConfig = true }` (compileSdk ≥ 33) — не создавать ручной `res/xml/locale_config.xml`.
- Язык: системный + per-app (Android 13+), **без** `AppCompatActivity` (OQ-13). Если продукт потребует собственный переключатель на всех версиях — `AppCompatActivity` + `androidx.appcompat` 1.8.0 (отдельное решение).
- Форматирование чисел локалезависимо и **не** от `Locale.getDefault()`: локаль передаётся явно из настроек. Парсинг принимает `,` и `.`; хранение/расчёт — `BigDecimal`. Плюрализация — ICU `plurals`.
- Названия препаратов: каталожные — пары полей `nameRu/nameEn`; пользовательские — как ввёл пользователь.

---

## 8. DI

Ручной `AppContainer` в `Application`: `AppDatabase`, `DataStore`, репозитории, use cases, `EngineConfig` (политика округления, пределы). ViewModel — через `viewModel(factory = …)`; в тестах контейнер собирается с фейками. Переход на Hilt (2.60.1) — при ≥3 feature-модулей или потребности в `@HiltViewModel`-агрегации (ADR).

---

## 9. Тестирование и CI

Пирамида: (1) юнит-тесты движка `:core-engine` (golden + property, самые ценные); (2) ViewModel/репозитории `:app/src/test`; (3) Compose UI на Robolectric; (4) скриншоты Roborazzi (экран × размеры × light/dark × fontScale); (5) instrumented только DAO/БД/миграции (in-memory Room 3); (6) тесты навигации как данных. AGP test suites для скриншотов — кандидат, основной инструмент Roborazzi.

CI (GitHub Actions): job `build` без эмулятора — `:core-engine:test`, `testDebugUnitTest`, `verifyRoborazziDebug`, `lint`, `assembleRelease`; job `instrumented` по расписанию/метке — эмулятор API 36. Merge заблокирован при падении `build`. `configuration-cache=true`, `nonTransitiveRClass=true`.

---

## 10. Этапы и риски

Этапы: **M0** пустая сборка (KSP + Room 3 + AGP 9.5 спайк — полдня) → **M1** `:core-engine` + тесты T1–T15 → **M2** Room-схема + сид каталога + миграции → **M3** навигация + экраны (S-1, S-2, S-3/S-4, S-5…S-7) → **M4** усиленный режим + i18n + a11y → **M5** полировка, скриншоты, ревью → релиз (гейт ≥10 верифицированных карточек).

Риски (research-stack §7 + SPEC §12): Room 3.0.3 молодой мажор (спайк M0, план Б 2.8.5); KSP+built-in Kotlin (прогон до фич); `compileSdk 37`/`targetSdk 36` — будущий подъём Play; Espresso на API 37 (альфа); переключатель языка и `AppCompatActivity` (OQ-13); палитра в тёмной теме (закрыто DESIGN §7.4). Каталог фактически <10 верифицированных карточек — отдельная клиническая задача до релиза (RISK-6).

---

## 11. Открытые вопросы

Наследуются из SPEC §11 (OQ-1…OQ-20) со значениями по умолчанию. Для реализации критичны: OQ-1 (шаг насоса 0,1/0,01), OQ-5 (`K_crit=3,0`), OQ-8 (перечень high-alert), OQ-13 (язык). Значения по умолчанию зафиксированы в `EngineConfig`/настройках и не блокируют реализацию.
