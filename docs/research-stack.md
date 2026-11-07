# Исследование Android-стека и архитектуры: «Калькулятор скорости инфузии»

**Статус:** исследование завершено, документ — вход для ТЗ и дизайна.
**Дата проверки версий:** 2026-11-07 (Asia/Yekaterinburg).
**Область:** только документы. Код приложения в рамках этой задачи не пишется; фрагменты конфигурации сборки приведены как справочный каталог версий, а не как реализация.

Легенда источников: `[GM]` — Google Maven (`dl.google.com/dl/android/maven2`), `[MC]` — Maven Central, `[GP]` — Gradle Plugin Portal, `[KB]` — официальная база знаний Android (`developer.android.com`, читалась через `android docs fetch`/`search`), `[GH]` — релизы на GitHub, `[GV]` — `services.gradle.org`. Полные ссылки — в разделе 9.

---

## 0. Краткое решение (TL;DR)

| Решение | Значение |
|---|---|
| Язык / компилятор | Kotlin 2.4.21 (K2) |
| Сборка | AGP 9.5.1 + Gradle 9.8.2 + built-in Kotlin (без плагина `kotlin-android`) |
| UI | Jetpack Compose (BOM 2026.09.00 → Compose UI 1.12.1) + Material 3 1.4.0 |
| Навигация | Navigation 3 **1.2.1** (стабильная), одноактивити + `NavDisplay` |
| Хранение | Room **3.0.3** (`androidx.room3`) + KSP 2.3.13 + `BundledSQLiteDriver` |
| Настройки | DataStore Preferences 1.3.0 |
| DI | **Ручной DI** (композиция в `Application`), Hilt — зарезервированная альтернатива |
| SDK | `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24` |
| JVM | Gradle на JDK 21 LTS, `compileOptions`/`jvmTarget` = 17 |
| Модули | `:app` (Android/Compose/Room) + `:core-engine` (чистый Kotlin/JVM, BigDecimal) |
| Тесты | JUnit4 + Robolectric 4.17 + Compose UI Test + Roborazzi 1.77.0; DAO — instrumented; доступность — `ui-test-junit4-accessibility` |
| CI | GitHub Actions: JDK 21, `testDebugUnitTest`, `verifyRoborazziDebug`, `lint`, `assembleRelease` |

Ключевой приоритет проекта — **предсказуемость вычислений и защиты от ошибки дозирования**, поэтому вычислительный движок вынесен в отдельный чистый модуль на `BigDecimal` и покрывается табличными и property-тестами (раздел 6).

---

## 1. Как проверялись версии (и почему этому можно верить)

Правило работы: **версия попадает в документ только с первоисточником**. Использованные методы проверки:

1. **Реестры артефактов** — прямые запросы к Google Maven и Maven Central (список версий, стабильность, факт существования конкретной версии). Так проверены Kotlin, AGP, Compose BOM, Navigation 3, Room, KSP, Hilt, DataStore, Robolectric, Roborazzi и др.
2. **Метаданные самих артефактов** — для критичных библиотек скачивались AAR и читались `AndroidManifest.xml` (`minSdkVersion`) и `META-INF/.../aar-metadata.properties` (`minCompileSdk`, `minAndroidGradlePluginVersion`). Это дало жёсткие ограничения на `minSdk`/`compileSdk`/AGP, а не «на глазок».
3. **Официальные релиз-ноты** — AGP 9.5.0 `[KB]`, Play target API `[KB]`, built-in Kotlin `[KB]`, таблица «Kotlin ↔ AGP/R8» `[KB]`, миграция Room 2→3 `[KB]`, скриншот-тесты `[KB]`, per-app language `[KB]`.
4. **Диф API по артефактам** — классы Navigation 3 1.2.1 и 1.3.0-alpha04 сравнивались по `classes.jar`, чтобы решить вопрос «нужна ли альфа» фактически, а не по названию версии.
5. **Численный эксперимент на JVM** — погрешности `Double` и поведение `BigDecimal` (раздел 6.3) получены запуском кода на реальной JVM (JBR из Android Studio), а не пересказом.
6. **Референсный проект** — `github.com/drwho174/Calc1` (палитра и стиль кода команды). Его конфигурация сборки изучена как ориентир совместимости: AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, Navigation 3 1.2.0, Robolectric/Roborazzi, `compileSdk 37 / minSdk 24 / targetSdk 36`, Java 17.

**Ограничение:** в документации Google встречаются страницы-превью следующих релизов (например, статья про Room 3.0 существует отдельно от стабильных артефактов). Поэтому приоритет у реестров: если артефакта версии нет — версии нет, что бы ни было написано в тексте. Перед началом кодирования версии нужно перепроверить тем же способом (реестры + метаданные AAR).

---

## 2. Целевой стек: версии и доказательства

| Компонент | Координаты | Версия | Как проверено | Почему именно эта |
|---|---|---|---|---|
| Kotlin | `org.jetbrains.kotlin:kotlin-gradle-plugin` | **2.4.21** | `[MC]` (последняя стабильная; 2.5.0-Beta2 — пререлиз, не берём) | K2, поддержана AGP 9.5 (Kotlin 2.4 требует AGP ≥ 8.5.2 `[KB]`) |
| AGP | `com.android.tools.build:gradle` | **9.5.1** | `[GM]` | Минорный релиз октября 2026; закрывает регрессии 9.4.1, поддерживает API 37 `[KB]` |
| Gradle | wrapper | **9.8.2** | `[GV]` для текущей (9.9.0), `[KB]` для минимума AGP | AGP 9.5 требует Gradle ≥ 9.6.0; 9.8.2 — проверенная связка (Calc1). 9.9.0 вышла 2026-11-06 — берём после патча |
| Compose BOM | `androidx.compose:compose-bom` | **2026.09.00** | `[GM]` (последняя, в т.ч. среди пререлизов) | Единая версия Compose-артефактов; внутри UI 1.12.1, M3 1.4.0, adaptive 1.3.0 |
| Compose UI | (из BOM) | 1.12.1 | `[GM]` + `aar-metadata` | **`minCompileSdk=37`, `minAGP=9.1.0`, `minSdk=23`** — задаёт требования к сборке |
| Material 3 | `androidx.compose.material3:material3` | 1.4.0 | `[GM]` + `aar-metadata` | Последняя стабильная (релиз 2025-09-24); `minSdk 21`, `minCompileSdk 35` |
| M3 Adaptive (опция) | `androidx.compose.material3.adaptive:adaptive-navigation3` | 1.3.0 | `[GM]` | Двухпанельные раскладки для планшетов; не обязательна для MVP |
| Navigation 3 runtime/ui | `androidx.navigation3:navigation3-runtime`, `:navigation3-ui` | **1.2.1** | `[GM]` + `aar-metadata` | Стабильная; `minSdk 24` (жёстко поднимает `minSdk`), `minCompileSdk 36` |
| Navigation 3 VM | `androidx.lifecycle:lifecycle-viewmodel-navigation3` | 2.11.0 | `[GM]` | Декоратор `rememberViewModelStoreNavEntryDecorator` — VM на каждый `NavEntry` |
| Lifecycle | `androidx.lifecycle:lifecycle-runtime-compose`, `:lifecycle-viewmodel-compose` | 2.11.0 | `[GM]` | `collectAsStateWithLifecycle`, `dropUnlessResumed` |
| Room 3 | `androidx.room3:room3-runtime`, `:room3-compiler`, плагин `androidx.room3` | **3.0.3** | `[GM]` + `aar-metadata` (`minSdk 23`, `minCompileSdk 34`, `minAGP 8.1.1`) | Kotlin-first, только KSP, coroutines-first, драйверы SQLite `[KB]` |
| SQLite driver | `androidx.sqlite:sqlite-bundled` | 2.7.1 | `[GM]` | `BundledSQLiteDriver` — единая версия SQLite на всех устройствах |
| KSP | `com.google.devtools.ksp:symbol-processing-api` (плагин `com.google.devtools.ksp`) | **2.3.13** | `[MC]`, `[GP]`, `[GH]` | Независимая от Kotlin нумерация; KSP1 удалён; требует AGP ≥ 8.12.0 (с 2.3.12) |
| kotlinx.serialization | `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.12.0 | `[MC]` | Типизированные ключи навигации (`@Serializable` + `NavKey`) и сохранение back stack |
| Плагин serialization | `org.jetbrains.kotlin.plugin.serialization` | 2.4.21 | `[GP]` | Версия плагина = версия Kotlin |
| Плагин Compose | `org.jetbrains.kotlin.plugin.compose` | 2.4.21 | `[GP]`, `[MC]` (`compose-compiler-gradle-plugin`) | С Compose-компилятором в составе Kotlin отдельный артефакт не нужен |
| DataStore | `androidx.datastore:datastore-preferences` | 1.3.0 | `[GM]` | Настройки (режим безопасности, тема, язык) без БД |
| Core / Activity | `androidx.core:core-ktx` 1.19.1, `androidx.activity:activity-compose` 1.13.0 | — | `[GM]` | `enableEdgeToEdge`, `setContent`, `dropUnlessResumed` |
| Robolectric | `org.robolectric:robolectric` | 4.17 | `[MC]` | UI-тесты Compose в JVM, без эмулятора |
| Roborazzi | `io.github.takahirom.roborazzi:roborazzi(-compose, -junit-rule)` | 1.77.0 | `[MC]` | Скриншот-тесты на JVM, детерминированные в CI |
| Compose a11y test | `androidx.compose.ui:ui-test-junit4-accessibility` | 1.12.1 | `[GM]` | `enableAccessibilityChecks()` c Compose 1.8+ `[KB]` |
| Test core | `junit:junit` 4.13.2, `androidx.test.ext:junit` 1.3.0, `androidx.test:runner` 1.7.0 | — | `[MC]`/`[GM]` | Базовый набор локальных и instrumented тестов |
| Espresso (опция) | `androidx.test.espresso:espresso-core` | 3.7.0 (стабильная) / 3.8.0-alpha01 | `[GM]` | Нужен только для instrumented-тестов на API 37 (см. 3.11) |
| Coroutines test | `org.jetbrains.kotlinx:kotlinx-coroutines-test` | 1.11.0 | `[MC]` | Тесты ViewModel/репозитория |
| Hilt (альтернатива) | `com.google.dagger:hilt-android` 2.60.1, `androidx.hilt:hilt-navigation-compose` 1.4.0 | — | `[MC]`, `[GM]`, `[GH]` | Не выбран (раздел 3.9), но совместим: `minSdk 23`, поддержка AGP 9 с 2.59.2 |

### Справочный каталог версий (конфигурация, не код приложения)

```toml
[versions]
agp = "9.5.1"
kotlin = "2.4.21"
ksp = "2.3.13"
composeBom = "2026.09.00"
navigation3 = "1.2.1"
lifecycle = "2.11.0"
room3 = "3.0.3"
sqlite = "2.7.1"
datastore = "1.3.0"
kotlinxSerialization = "1.12.0"
coreKtx = "1.19.1"
activityCompose = "1.13.0"
junit = "4.13.2"
androidxJunit = "1.3.0"
androidxTestRunner = "1.7.0"
robolectric = "4.17"
roborazzi = "1.77.0"
coroutinesTest = "1.11.0"

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room3 = { id = "androidx.room3", version.ref = "room3" }
```

> Плагина `org.jetbrains.kotlin.android` в каталоге **нет намеренно**: AGP 9 включает built-in Kotlin (раздел 3.1).

---

## 3. Разбор ключевых решений

### 3.1 Kotlin, AGP, Gradle и built-in Kotlin

- **AGP 9.0+ включает built-in Kotlin по умолчанию**: плагин `org.jetbrains.kotlin.android` больше не применяется, его наличие приводит к ошибке `Cannot add extension with name 'kotlin'` `[KB: migrate-to-built-in-kotlin]`. Соответственно в проекте остаются только `kotlin-compose`, `kotlin-serialization`, `ksp` и Room-плагин. Это же сделано в Calc1.
- **`android.kotlinOptions {}` устарел** — используется `kotlin { compilerOptions { ... } }`; `jvmTarget` по умолчанию равен `android.compileOptions.targetCompatibility` `[KB]`, поэтому дублировать его не нужно.
- **Совместимость Kotlin ↔ AGP**: для Kotlin 2.4 требуется AGP ≥ 8.5.2 `[KB: kotlin-support]` — 9.5.1 проходит с запасом.
- **Совместимость AGP ↔ Gradle ↔ JDK**: AGP 9.5 — Gradle ≥ 9.6.0, JDK ≥ 17, Build Tools 36.0.0, максимум API 37 `[KB: agp-9-5-0-release-notes]`.
- **Версия Gradle**: текущая стабильная — 9.9.0 (сборка 2026-11-06) `[GV]`, но она вышла за день до проверки. Берём 9.8.2 (выше минимума AGP 9.5 и уже используется в Calc1), обновление до 9.9.x — отдельная задача после выхода патча.
- **JDK**: Gradle daemon — JDK 21 LTS (Temurin; EOL цикла — 2029-12-31 по `endoflife.date`). JDK 25 формально поддерживается (в AGP 9.5 закрыт warning для JDK 25), но 21 LTS — безопасная середина между минимумом AGP (17) и «свежим» 25. `compileOptions` = 17, как в Calc1.
- **Риск**: KSP 2.3.13 и Room 3.0.3 обязаны работать в паре с built-in Kotlin — в KSP 2.3.10 отдельно закрывали «R-class resolution при AGP 9 built-in Kotlin» `[GH]`. Проверять на первом же «пустом» прогоне сборки.

### 3.2 Уровни SDK: `compileSdk = 37`, `targetSdk = 36`, `minSdk = 24`

| Параметр | Значение | Обоснование |
|---|---|---|
| `compileSdk` | **37** | Compose UI 1.12.1 объявляет `minCompileSdk=37` в `aar-metadata` — с 36 сборка не пройдёт `[GM/AAR]`. AGP 9.5 поддерживает API 37 как максимум `[KB]` |
| `targetSdk` | **36** | С 31 августа 2026 новые приложения и обновления в Google Play обязаны таргетить Android 16 (API 36) `[KB: target-sdk]`. API 37 разрешён, но не требуется; таргет 36 = меньше непроверенных behavior changes |
| `minSdk` | **24** | Нижняя граница задаёт Navigation 3 (`minSdkVersion 24` в AAR 1.2.1) `[GM/AAR]`. Compose UI требует 23, Room 3 — 23, поэтому 24 — фактический минимум стека |
| `buildToolsVersion` | не фиксируем | AGP 9.5 сам ставит 36.0.0 `[KB]` |

Следствие для ТЗ: поддержка Android 7.0+. Более старые версии несовместимы с выбранной навигацией; понижать `minSdk` нельзя без отказа от Navigation 3.

### 3.3 Compose BOM и Material 3

- Один BOM на всё Compose-хозяйство: `2026.09.00` `[GM]`. Внутри: UI/foundation/runtime 1.12.1, `material3` **1.4.0**, `material3.adaptive` 1.3.0, тестовые артефакты 1.12.1.
- `material3` версионируется отдельно от Compose UI, поэтому в каталоге он без версии (из BOM).
- Compose-компилятор — часть Kotlin 2.4.21 через плагин `org.jetbrains.kotlin.plugin.compose`; отдельный `androidx.compose.compiler` не подключается `[KB: shotsuite → requirements]`.
- **Практика**: `buildFeatures { compose = true }` + BOM, `debugImplementation(ui-tooling)` и `ui-test-manifest` для превью/тестов.
- Material 3 Expressive-компоненты доступны в 1.4.0, но для медицинского инструмента важнее стабильность: используем базовые M3-компоненты (`Scaffold`, `TopAppBar`, `OutlinedTextField`, `SegmentedButton`, `Card`, `AlertDialog`), без экспериментальных API.

### 3.4 Navigation 3: почему 1.2.1, а не 1.3.0-alpha04

Проверено фактически: обе версии есть на Google Maven (`1.2.1` — stable, `1.3.0-alpha04` — alpha) `[GM]`. Сравнение `classes.jar`:

| Артефакт | 1.2.1 | 1.3.0-alpha04 | Разница |
|---|---|---|---|
| `navigation3-runtime` | 59 классов | 59 классов | **нет отличий** в наборе классов |
| `navigation3-ui` | 28 классов | 31 класс | добавлены `TitleMetadataKey`, `UrlMetadataKey`, `PredictivePopGestureKt` |

Вывод: альфа добавляет **метаданные заголовка/URL** и **предиктивный pop-жест** — ничего из этого не требуется для MVP (заголовок экрана задаётся в самом экране, deep links не входят в первую версию). Стабильная 1.2.1 содержит всё нужное и подтверждённо имеет:

- `NavKey`, `NavBackStack`, `NavEntry`, `rememberNavBackStack`, `entryProvider`/`EntryProviderScope`;
- сериализацию back stack (`NavBackStackSerializer`, `NavKeySerializer`);
- декораторы `SaveableStateHolderNavEntryDecorator` и `rememberSaveableStateHolderNavEntryDecorator`;
- **Result API**: `androidx.navigation3.runtime.result.{ResultEventBus, LocalResultEventBus, ResultEffect, ResultEventBusNavEntryDecorator}` — проверено наличием классов в AAR;
- deep links (`androidx.navigation3.runtime.deeplink.*`) — на будущее;
- Scenes: `SinglePaneScene`, `DialogScene`, `OverlayScene`, `SceneStrategy`/`SceneStrategyScope` (в `navigation3-ui`).

**Решение:** `1.2.1`. Возврат к 1.3.x — когда выйдет стабильная и появится задача про предиктивный back / метаданные.

### 3.5 Room: 3.0.3 или 2.8.5

В реестре есть **две линейки**: `androidx.room` (последняя стабильная 2.8.5) и `androidx.room3` (последняя стабильная **3.0.3**) `[GM]`. Room 3.0 — мажор с новой точкой входа пакета `androidx.room3`, чтобы не ломать старые приложения `[KB: migration-2-to-3]`.

| Критерий | Room 3.0.3 (`androidx.room3`) | Room 2.8.5 (`androidx.room`) |
|---|---|---|
| Статус | стабильная, 13 версий в линейке | стабильная, 92 версии |
| Генерация кода | **только KSP**, Java-генерации нет | KSP или kapt |
| DAO | `suspend` обязателен для не-observable методов, coroutines-first | допускает блокирующие методы |
| SQLite | драйверы (`BundledSQLiteDriver` / `AndroidSQLiteDriver`), `SupportSQLiteDatabase` вне кора | `SupportSQLiteDatabase` в основе |
| Совместимость | `minSdk 23`, `minCompileSdk 34`, AGP ≥ 8.1.1 `[AAR]` | `minSdk 23` `[AAR]` |
| Риск | молодой мажор: меньше примеров, API-миграции документально новые | зрелый, но требует миграции позже |

**Решение: Room 3.0.3.** Причины: (1) приложение новое, миграция «потом» — лишняя работа; (2) KSP-only идеально сочетается с built-in Kotlin и KSP 2.3.13 — не нужен `legacy-kapt`; (3) coroutines-first и драйверы дают детерминированную версию SQLite через `BundledSQLiteDriver`, что важно для тестов и воспроизводимости; (4) `minSdk`/`compileSdk`-требования ниже наших. Ветка Room 2.8.5 остаётся планом Б, если на первом прогоне всплывёт несовместимость Room 3 с AGP 9.5/KSP 2.3.13.

Практические следствия для ТЗ: классы в `androidx.room3`; `Room.databaseBuilder<AppDatabase>(context, "…").setDriver(BundledSQLiteDriver()).build()` `[KB]`; схема — через `room3-gradle-plugin` (`schemaDirectory`), миграции — обязательные `Migration`-объекты с `suspend`-методом; для БД используем `TEXT` для десятичных значений (см. 6.4), чтобы не терять точность в `REAL`.

### 3.6 KSP

- Версия **2.3.13** (2026-10-30) `[GH]`. С KSP 2.x нумерация отвязана от версии Kotlin, KSP1 удалён полностью `[GH: README]`.
- Требование AGP ≥ 8.12.0 (с 2.3.12) `[GH]` — выполняется.
- KSP используется только для Room (`ksp(libs.androidx.room3.compiler)`), плюс при необходимости — `kspAndroidTest` для тестовой БД. Hilt/KAPT не применяются, поэтому `legacy-kapt` не нужен.

### 3.7 kotlinx.serialization

- `kotlinx-serialization-json 1.12.0` `[MC]`, плагин `org.jetbrains.kotlin.plugin.serialization` той же версии, что Kotlin (2.4.21) `[GP]`.
- Назначение в проекте: (1) типизированные ключи навигации — `@Serializable data object Calculator : NavKey`; (2) сохранение/восстановление back stack (`rememberNavBackStack` требует сериализуемости ключей); (3) экспорт/импорт пользовательских препаратов в JSON (локально, без облака).
- JSON-конфиг: `ignoreUnknownKeys = true`, `encodeDefaults = true` — для совместимости версий формата.

### 3.8 DataStore

- `datastore-preferences 1.3.0` `[GM]` — для небольших настроек: усиленный режим безопасности, тема (светлая/тёмная/системная), выбранный язык (если делаем переключатель в приложении), последний использованный вес (только при явном согласии пользователя).
- Данные препаратов в DataStore **не хранятся** — только Room. Это разделение фиксируем в ТЗ, чтобы не появилось два источника правды.

### 3.9 DI: Hilt или ручной контейнер

| Критерий | Ручной DI (`AppContainer`) | Hilt 2.60.1 |
|---|---|---|
| Сборка | ничего не добавляет | плагин + KSP-процессор + агрегация; чувствительно к AGP 9 (поддержка с 2.59.2 `[GH]`) |
| Время сборки / инкрементальность | без накладных расходов | +кодогенерация на каждый модуль |
| Размер приложения | 0 | заметный вклад в dex |
| Порог входа | конструкторы и фабрики | аннотации, скоупы, правила |
| Тестируемость | fakes подставляются в конструктор контейнера | `@HiltAndroidTest` + правила |
| Масштабирование | ручное связывание при росте модулей | автоматическое |
| Прецедент в команде | так сделан Calc1 | — |

**Решение: ручной DI.** Одно приложение, один модуль `:app` + чистый `:core-engine`, ~5 экранов: граф зависимостей умещается в один `AppContainer`, создаваемый в `Application`. Это убирает кодогенерацию (и без того есть KSP для Room), ускоряет сборку и упрощает тесты: ViewModel получает зависимости через `viewModel(factory = …)`, а в тестах подставляются in-memory фейки репозиториев.

**Условия пересмотра (зафиксировать в ТЗ):** появление ≥ 3 feature-модулей или ≥ 4 уровней вложенности графа, либо необходимость `@HiltWorker`/`@HiltViewModel` с агрегацией по модулям — тогда переход на Hilt (2.60.1, `minSdk 23`, AGP 9-совместим) оформляется отдельным ADR.

### 3.10 JVM toolchain

- Gradle daemon: **JDK 21 LTS**. Минимум AGP 9.5 — JDK 17 `[KB]`; JDK 25 поддерживается, но LTS-21 предсказуемее для CI-образов.
- `compileOptions { sourceCompatibility = VERSION_17; targetCompatibility = VERSION_17 }` — как в Calc1; при built-in Kotlin `kotlin.compilerOptions.jvmTarget` наследуется от `targetCompatibility` `[KB]`.
- `:core-engine` — `kotlin("jvm")`-модуль без Android; `jvmToolchain(21)` там допустим и полезен (чистый JVM-таргет, быстрые тесты).
- Дополнительно: `org.gradle.configuration-cache=true` (в Calc1 включён с `problems=warn`), `android.nonTransitiveRClass=true`, `kotlin.code.style=official`, `file.encoding=UTF-8`.

### 3.11 Тестовая обвязка: что и чем

| Уровень | Инструменты | Где живёт |
|---|---|---|
| Юнит-тесты движка и парсинга | JUnit4 (13.2), `kotlin.test`, `kotlinx-coroutines-test` 1.11.0 | `:core-engine/src/test` (JVM, без Android) |
| ViewModel/репозиторий | JUnit4 + `coroutines-test` + фейки | `:app/src/test` |
| Compose UI (поведение) | `ui-test-junit4` (BOM) + **Robolectric 4.17** | `:app/src/test` (без эмулятора) |
| Скриншоты | **Roborazzi 1.77.0** (JVM+Robolectric) | `:app/src/test` |
| DAO/БД | instrumented, in-memory Room 3 + `BundledSQLiteDriver` | `:app/src/androidTest` |
| Доступность | `ui-test-junit4-accessibility` 1.12.1, `enableAccessibilityChecks()` `[KB]` | `:app/src/test` |
| Smoke на устройстве | Compose test + (при необходимости) Espresso 3.8.0-alpha01 | `:app/src/androidTest` |

**Про скриншоты:** официальный путь в AGP 9.5 — test suites (`testOptions { screenshotTests.create(...) }`, engine ≥ `0.0.1-alpha16`, актуальный `screenshot-validation-api 0.0.1-alpha17`) `[KB]`, но он помечен Experimental, требует Canary-Studio и меняющегося DSL. Для проекта важнее воспроизводимость CI, поэтому базовый инструмент — Roborazzi (как в Calc1), а AGP test suites держим как кандидат на переход, когда фича выйдет из experimental.

**Про Espresso:** стабильная версия — 3.7.0 `[GM]`. В Calc1 зафиксировано, что для устройств API 37 нужен 3.8.0-alpha01 (в 3.7 отсутствует `InputManager.getInstance`). Поэтому: instrumented-набор держим минимальным, а если он нужен на API 37 — берём альфу и фиксируем это в ТЗ как известное отклонение.

---

## 4. Best practices, которые попадают в ТЗ

### 4.1 Одноактивити + `NavDisplay`

Схема навигации проекта:

- **Одна `ComponentActivity`**, `enableEdgeToEdge()` до `setContent`, внутри — `NavDisplay`.
- **Back stack — это список ключей**, которым владеет приложение (не библиотека). Ключи — `@Serializable` объекты/классы, реализующие `NavKey`; параметры передаются в конструкторе ключа, а не через `Bundle`.
- `rememberNavBackStack(Calculator)` — стартовый ключ; переживает пересоздание конфигурации и смерть процесса `[KB: save-state]`.
- **Декораторы** (порядок важен):
  1. `rememberSaveableStateHolderNavEntryDecorator()` — состояние экрана (`rememberSaveable`) сохраняется, пока запись в стеке;
  2. `rememberViewModelStoreNavEntryDecorator()` (`lifecycle-viewmodel-navigation3`) — ViewModel привязана к `NavEntry`, а не к активити;
  3. при необходимости — `rememberResultEventBusNavEntryDecorator()` для Result API.
- **Result API** — для возврата данных «назад»: экран-источник (`CatalogScreen`, `DrugEditorScreen`) отправляет результат через `ResultEventBus.sendResult<T>(...)` и убирает себя из стека; экран-получатель подписывается `ResultEffect<T> { ... }`. Проверено: API присутствует в 1.2.1.
- **Диалоги** — не отдельный экран, а `DialogScene`/`OverlayScene` поверх текущей записи, чтобы форма под диалогом не теряла ввод. Это критично для подтверждений в усиленном режиме.
- **Анимации** — дефолтные переходы `NavDisplay` + при необходимости переопределение на уровне destination; предиктивный back-жест — только после стабильного 1.3.
- **Обработка back** — единственная точка: `onBack = { backStack.removeLastOrNull() }`; дополнительные перехваты (например, «есть несохранённые изменения в препарате») реализуются на экране через `NavigationBackHandler`, а не через зоопарк `BackHandler`.
- **Deep links** — API в 1.2.1 есть (`deeplink`-пакет), но в MVP не используются; в ТЗ фиксируем как «не входит».
- **Состояние навигации как данные**: список ключей — обычный `SnapshotStateList`, поэтому навигацию можно тестировать юнит-тестами (добавили/удалили ключ → проверили содержимое и порядок).

### 4.2 Edge-to-edge

- `targetSdk 36` ⇒ edge-to-edge принудительно (с API 35) `[KB: system-bars]`. Вызываем `enableEdgeToEdge()` явно — для обратной совместимости до API 35.
- Контент получает `innerPadding` от `Scaffold`; списки — `contentPadding`, а не `padding` (иначе обрезка последнего элемента); IME-инсеты — `Modifier.imePadding()` для полей ввода; `navigationBarsPadding()` — для нижних панелей.
- `Scaffold(contentWindowInsets = WindowInsets(0.dp))` — только если панель сама обрабатывает инсеты (как в рецепте multiple back stacks).

### 4.3 Тема Material 3

- Палитра переносится из `github.com/drwho174/Calc1`: в Calc1 она сгенерирована Material Theme Builder и живёт в `ui/theme/Color.kt` (light/dark, primary `#006B5B` в светлой теме), а в `res/values/colors.xml` остаются только цвета окна. Для infusionCalc повторяем структуру: `Color.kt` (light/dark схемы), `Theme.kt` (`lightColorScheme`/`darkColorScheme`), `Type.kt`.
- **Dynamic color (Material You) выключен по умолчанию**: в медицинском инструменте цвет несёт смысл (норма/предупреждение/опасность), и перекрашивание системой ломает предсказуемость. Решение фиксируется в ТЗ; при желании — опция в настройках.
- **Цифры — табличные**: для полей дозы/скорости задаём `TextStyle(fontFeatureSettings = "tnum")` — цифры не «прыгают» при вводе.
- Тёмная тема обязательна (расчёт ночью/в отделении), контраст проверяется для обеих схем.

### 4.4 Локализация RU + EN

- Ресурсы: `res/values/strings.xml` (EN — базовая локаль) и `res/values-ru/strings.xml`. Никаких строк в коде; единицы измерения — тоже ресурсы (`мг`, `мкг`, `мл/ч`).
- **Автоматический `LocaleConfig`**: `androidResources { generateLocaleConfig = true }` (compileSdk ≥ 33) — AGP сам генерирует конфиг и добавляет языки в системные настройки `[KB: app-languages]`. Ручной `res/xml/locale_config.xml` при этом создавать нельзя (конфликт сборки).
- **Переключатель языка в приложении**: `AppCompatDelegate.setApplicationLocales` требует, чтобы активити наследовалась от `AppCompatActivity` `[KB]`. Развилка для ТЗ: (а) MVP — язык следует системному + системные per-app language (Android 13+); (б) полноценный переключатель — тогда `AppCompatActivity` (не `ComponentActivity`) и `androidx.appcompat` 1.8.0. Рекомендация: (а) в MVP, (б) — если продукт требует выбор языка внутри приложения.
- **Форматирование чисел локалезависимо**: разделитель дробной части (`0,5` в RU, `0.5` в EN), разделитель групп разрядов, единица после числа через неразрывный пробел. Ввод принимает и точку, и запятую (нормализация перед парсингом), а хранит/считает — `BigDecimal`.
- **Плюрализация** — ICU `plurals` (`капель/капли/капель`), а не склейка строк.
- Названия препаратов: встроенный каталог хранит локализованное имя как пару полей (`nameRu`/`nameEn`) с фолбэком; пользовательские препараты — одно имя (как ввёл пользователь).

### 4.5 Доступность

- Тач-таргеты ≥ 48×48 dp; поля ввода с `label` (не только placeholder); `contentDescription` у иконочных кнопок; семантические заголовки (`Modifier.semantics { heading() }`).
- Состояние «предупреждение/ошибка» передаётся **не только цветом**: иконка + текст + `liveRegion` для озвучивания результата.
- Порядок обхода (`traversalIndex`) для формы расчёта: препарат → доза → вес → концентрация → результат.
- `fontScale` 1.5 и 2.0 — вёрстка не должна ломаться (проверяется скриншот-тестами и `DeviceConfigurationOverride`).
- Автопроверки: `enableAccessibilityChecks()` в Compose-тестах (контраст, размер тач-таргета, порядок обхода, наличие подписи) `[KB: a11y testing]` + ручной чек-лист TalkBack перед релизом.

### 4.6 Тестовая стратегия (пирамида)

1. **Много юнит-тестов движка** (`:core-engine`): табличные клинические кейсы, границы, округление, парсинг, инварианты (раздел 6.5). Это самая дешёвая и самая ценная защита.
2. **Тесты ViewModel** (`:app/src/test`): состояния формы, дебаунс, ошибки валидации, поведение усиленного режима.
3. **Compose UI-тесты на Robolectric** (`:app/src/test`): семантика, ввод, ошибки, пустые состояния, восстановление состояния.
4. **Скриншот-тесты Roborazzi** (`:app/src/test`): экран × 9 размеров (400/610/900 dp по ширине, 400/500/1000 dp по высоте), light/dark, fontScale 1.5, состояния «предупреждение», «ошибка», «препарат не выбран».
5. **Instrumented** (`:app/src/androidTest`): только DAO/БД (in-memory Room 3 + `BundledSQLiteDriver`), миграции, edge-to-edge smoke. Эмулятор — дорого, поэтому держим этот уровень минимальным.
6. **Тесты навигации**: back stack как данные — добавили/убрали ключ, «выход через стартовый экран», возврат результата из каталога, сохранение состояния при смерти процесса.

Требования к тестам, которые попадут в ТЗ: каждый баг дозирования обязан получить регрессионный тест в `:core-engine`; ни один расчёт не считается реализованным без golden-кейса.

### 4.7 CI

GitHub Actions, два job'а:

- **`build` (обязательный, без эмулятора):** JDK 21 (Temurin), кэш Gradle (`gradle/actions/setup-gradle`), шаги `./gradlew :core-engine:test`, `./gradlew testDebugUnitTest`, `./gradlew verifyRoborazziDebug`, `./gradlew lint`, `./gradlew assembleRelease` (без подписи, `-PskipSigning`).
- **`instrumented` (по расписанию/на PR с меткой):** эмулятор API 36 (x86_64), `connectedDebugAndroidTest`.

Дополнительно: артефакты — HTML-отчёты тестов, скриншоты Roborazzi при падении, `lint-results`; запрет merge при падении `build`. `org.gradle.configuration-cache=true` включён — заодно ловим регрессии совместимости плагинов.

---

## 5. Архитектура: слои, модель препарата, репозитории

### 5.1 Модули и границы

```
:core-engine   — чистый Kotlin/JVM. Никаких Android/Compose/Room зависимостей.
                 Decimal-типы, формулы, округление, валидация, safety-правила, парсинг/форматирование.
:app           — Android: Compose UI, Navigation 3, ViewModel, Room 3, DataStore, ручной DI.
```

Почему два модуля, а не один: (1) компилятор физически не даст затащить Android в математику; (2) тесты движка — секунды, без Robolectric; (3) движок можно переиспользовать в будущем (виджет, wear, desktop). Дробить дальше на `:feature:*` на этапе MVP не нужно — это удорожает сборку без выгоды; границы внутри `:app` фиксируются пакетами:

```
com.drwho174.infusioncalc
├── app/            Application, AppContainer (ручной DI), MainActivity
├── ui/             theme, components (общие), navigation (ключи, граф), screens/{calculator,catalog,drugeditor,settings}
├── domain/         модели (Drug, DoseUnit, DoseBasis, Weight, InfusionRate…), интерфейсы репозиториев, use cases
├── data/           Room 3 (entities, dao, converters, migrations, database), DataStore, реализации репозиториев, mappers
└── core/           Result-обёртки, форматтеры локали, диспетчеры
```

Правило зависимостей: `ui → domain ← data`, `domain → :core-engine`; `data` не знает про `ui`; `:core-engine` не знает ни про кого.

### 5.2 Модель препарата

Ключевое требование: **единица дозы — свойство препарата** (не выбор пользователя в форме). Модель (домен):

| Поле | Тип | Смысл / правило |
|---|---|---|
| `id` | `String` (UUID для пользовательских, стабильный slug для встроенных) | первичный ключ |
| `nameRu`, `nameEn` | `String?` | локализованное имя; для пользовательских — только `name` |
| `doseUnit` | `enum DoseUnit { MG, MCG, G, UNIT, ME, MMOL }` | **единица дозы препарата**; определяет подпись и допустимые пределы полей |
| `doseBasis` | `enum DoseBasis { PER_KG, ABSOLUTE }` | доза на кг или абсолютная (например, ЕД/ч) |
| `doseTimeBase` | `enum TimeBase { PER_MINUTE, PER_HOUR }` | типичная база (мкг/кг/мин vs мг/кг/ч) — влияет на UI и подсказки |
| `concentrationAmount` | `Decimal` (хранится как TEXT) | количество единиц дозы во флаконе/ампуле |
| `concentrationVolume` | `Decimal` (TEXT) | объём, мл |
| `concentrationDisplay` | вычисляемое | `amount/volume` + единица (`мг/мл`), отображается всегда |
| `defaultRateUnit` | `RateUnit = ML_PER_HOUR` | пока единственная; задел на `DROPS_PER_MINUTE` (`dropFactor`) |
| `dropFactor` | `Decimal?` | капель/мл, если нужен расчёт капель |
| `safetyMin`, `safetyMax` | `Decimal?` в `doseUnit`/кг/время | границы для предупреждений (данные каталога, не хардкод в UI) |
| `isBuiltIn` | `Boolean` | встроенный каталог нельзя удалить/изменить, можно «клонировать» |
| `notes` | `String?` | примечание (например, «разводить до 250 мл») |
| `updatedAt` | `Long` | версия для синхронизации/LWW при будущем импорте |

Правила: смена препарата в форме **меняет единицу дозы** и требует явного подтверждения, если в поле уже было значение (иначе «0.5» молча превратится из мг в мкг — 1000-кратная ошибка); концентрация всегда показывается рядом с полем дозы; `null` концентрация ⇒ расчёт невозможен, экран показывает «укажите концентрацию», а не 0.

### 5.3 Слои и ответственность

- **domain**: модели + интерфейсы `DrugRepository`, `SettingsRepository`, `WeightRepository` + use cases (`CalculateRate`, `CalculateDose`, `ValidateDose`, `SaveCustomDrug`). Use case возвращает `Result`-подобный тип (`CalcResult.Success(value, warnings)` / `CalcResult.Invalid(errors)`) — без исключений в UI.
- **data**: `DrugEntity` (Room 3) ↔ `Drug` (domain) через маппер; `Decimal` конвертируется `String` ↔ `BigDecimal` (`@ColumnTypeConverter` в Room 3); DAO на `Flow`/`suspend`; реализация репозитория — единственное место, где есть диспетчеры и `Context`.
- **ui**: экран = `@Composable` + `ViewModel` c `StateFlow<UiState>`; `UiState` — иммутабельный data class с явными полями (`drug`, `inputs`, `result`, `warnings`, `isSafetyMode`); события UI — sealed interface (`UiEvent`). Никакой математики в composable: только вызов `viewModel.onEvent(...)`.
- **ViewModel** создаётся на `NavEntry` (декоратор `rememberViewModelStoreNavEntryDecorator`), зависимости — из `AppContainer` через `viewModel(factory = ...)`.

### 5.4 Поток данных расчёта в «единой форме»

Одна форма, оба направления, единый источник истины:

1. Пользователь выбирает препарат → форма получает `doseUnit`, `doseBasis`, `concentration`.
2. Вводятся вес (вручную, обязателен при `PER_KG`), доза, концентрация/объём (если не заданы препаратом).
3. Движок считает **и** скорость (мл/ч), **и** обратную проверку дозы (из полученной скорости).
4. UI показывает результат + единицы + предупреждения; в усиленном режиме — запрос подтверждения с повторным вводом ключевого числа.
5. Обратный режим («известна скорость, найти дозу») — тот же экран, переключатель направления; меняется только то, какое поле ввода, вычисление — общая функция.

Зафиксировать в ТЗ: **направление расчёта — это состояние формы, а не отдельный экран**; переключение не сбрасывает введённые значения, а пересчитывает их.

### 5.5 Ручной DI

`AppContainer` в `Application`: `AppDatabase` (Room 3 + `BundledSQLiteDriver`), `DataStore`, репозитории, use cases, `EngineConfig` (политика округления). В тестах контейнер собирается с фейками — это единственная точка подмены. Никаких синглтонов-объектов с `Context`.

---

## 6. Вычислительный движок

### 6.1 Где живёт

**`:core-engine`** — чистые функции без состояния, без Android, без `Flow`. Вход/выход — value-классы (`DoseInput`, `Weight`, `Concentration`, `RateResult`). Всё, что можно посчитать неправильно, живёт здесь и только здесь; UI-слой не выполняет арифметику.

### 6.2 Контракты и формулы

Обозначения: `D` — доза (единица препарата на кг или абсолютная) за базу времени; `W` — вес, кг; `C` — концентрация (единица/мл); `Q` — скорость, мл/ч; `k` — множитель приведения базы времени (`PER_MINUTE → 60`, `PER_HOUR → 1`); `F` — капель/мл.

| Направление | Формула |
|---|---|
| Доза → скорость (на кг) | `Q = D × k × W / C` |
| Доза → скорость (абсолютная) | `Q = D × k / C` |
| Скорость → доза (на кг) | `D = Q × C / (k × W)` |
| Скорость → доза (абсолютная) | `D = Q × C / k` |
| Скорость → капли/мин | `drops/min = Q × F / 60` |

Требования к контракту:

- Функции **тотальны по типам**: никакие входы не дают `NaN`/`Infinity`; вместо этого — `Invalid(reason)`.
- Явные причины отказа: `ZERO_WEIGHT`, `ZERO_CONCENTRATION`, `NEGATIVE_INPUT`, `UNIT_MISMATCH`, `TOO_MANY_DECIMALS`, `OUT_OF_SAFE_RANGE`, `REQUIRES_CONFIRMATION`.
- Предупреждения отделены от ошибок: `Success(value, warnings)` vs `Invalid(errors)`.
- Движок возвращает **и** «сырое» значение (для аудита/копирования), **и** отформатированное по политике округления (для UI).

### 6.3 `BigDecimal` против `Double`: решение и доказательство

Прогон на JVM (JDK 25, JBR Android Studio, 2026-11-07):

```
0.1+0.2            = 0.30000000000000004
0.1+0.2 == 0.3     = false
10 x 0.1 (loop)    = 0.9999999999999999
1e20+1 == 1e20     = true
BD 0.1+0.2         = 0.3  (scale=1)
BD 1/3 HALF_UP(2)  = 0.33
BD 2.675 setScale(2, HALF_UP) = 2.68
BD 1e20+1          = 100000000000000000001
dose: 0.5*0.3*30 (Double) = 4.5 ; (BigDecimal) = 4.50
```

Что это значит практически:

- Для «обычных» доз `Double` часто даёт визуально правильный ответ (`4.5`), но **семантика сравнения и округления недетерминирована на границах**: `0.1+0.2 != 0.3`, накопление `0.1` даёт `0.9999999999999999`, `1e20 + 1 == 1e20`. В тестах это превращается в «почти равно» с допусками, а в валидации — в «0.3 ≠ 0.3».
- Дозирование — область, где ошибка в порядке 1e-3 значима, а объяснимость важнее скорости. Арифметика на `Double` требует дисциплины «никогда не сравнивай на равенство, округляй один раз, помни про binary representation» — это ровно тот класс ошибок, который потом ловят в клинике.
- `BigDecimal` даёт **точное** представление десятичного ввода (`new BigDecimal("0.1")`), детерминированное округление (`setScale(2, HALF_UP)` → `2.68`), отсутствие переполнения на больших числах и явную политику масштаба.

**Решение:**

- Парсинг ввода → `BigDecimal` **из строки** (никогда `BigDecimal(0.1)` из `Double` — это уже «грязное» число).
- Промежуточные вычисления — `MathContext.DECIMAL128` (34 значащие цифры) или явный `scale = 10`, `RoundingMode.HALF_UP`; округление — **один раз**, на выходе.
- Отображение: скорость `мл/ч` — scale 1 (0.1 мл/ч); капли/мин — scale 0; доза — scale 3 (или по единице: `MCG` — 0.1, `UNIT/ME` — 0.01); концентрация — scale 3.
- Сравнения — только `compareTo`, никогда `equals`; в домене нет `Double`/`Float` вовсе.
- Персистентность десятичных — `TEXT` в SQLite (`REAL` = binary64, потеря точности на входе).
- Контроль: архитектурный тест/правило ревью — в `:core-engine` запрещены `Double`, `Float`, `Math.round`, `String.format("%f")`.

Кому нужен `Double` — только графике/анимации (прогресс, шкала), и это осознанно выносится в `ui`.

### 6.4 Политика округления и точность (для ТЗ)

1. Ввод: до 4 знаков после запятой; больше — `TOO_MANY_DECIMALS` (не молчаливое усечение). Принимаем `,` и `.` как разделитель.
2. Вес: 0.1–300 кг (границы санитарные, настраиваемые); 0 — ошибка, не «пусто».
3. Концентрация: > 0; если препарат задаёт `amount`+`volume`, концентрация вычисляется и только отображается.
4. Никакого «двойного округления»: сначала точное значение, потом один `setScale` для показа. Проверка round-trip: `rate(dose) → dose'`; `|dose − dose'| ≤ 0.5 × шаг отображения дозы`.
5. В усиленном режиме round-trip проверяется всегда и до показа результата; расхождение → блокирующая ошибка `ROUNDTRIP_MISMATCH` (признак ошибки ввода или бага).
6. Единицы показываются всегда и рядом с числом; переключение препарата с другим `doseUnit` при непустом вводе — только через подтверждение.

### 6.5 Как тестировать движок

| Тип теста | Что проверяет | Пример |
|---|---|---|
| Табличные golden-кейсы | точные значения из клинических примеров и ручного счёта | допамин 5 мкг/кг/мин, 70 кг, 200 мг/250 мл → ожидаемый мл/ч |
| Property: монотонность | `D↑ ⇒ Q↑` при прочих равных | генерация случайных `D`, фиксированные `W`,`C` |
| Property: однородность | `W×m ⇒ Q×m` | `m ∈ {0.5, 2, 10}` |
| Property: round-trip | `dose(rate(d)) ≈ d` в пределах шага отображения | 1000 случайных валидных наборов |
| Границы и отказы | нули, отрицательные, `null`, 5+ знаков | ожидаемые `Invalid(reason)` |
| Единицы | `MG`/`MCG`/`ME` не смешиваются | `UNIT_MISMATCH` |
| Округление | HALF_UP на границах (`2.675 → 2.68`), отсутствие двойного округления | детерминированные кейсы |
| Локализация чисел | парсинг `0,5` и `0.5`, формат RU/EN | `Locale("ru")`/`Locale("en")` |
| Отсутствие `Double` | сканирование исходников модуля | правило в тесте/линте |

Тест-данные держим в `:core-engine/src/test/resources/*.csv` — чтобы клинический рецензент мог добавить кейс, не трогая код.

---

## 7. Риски и точки пересмотра

| Риск | Влияние | Смягчение |
|---|---|---|
| Room 3.0.3 — молодой мажор, меньше примеров | срыв сроков на неожиданностях API | спайк на первом дне кодирования (создать БД, DAO, миграцию, тест); план Б — Room 2.8.5 |
| KSP 2.3.13 + built-in Kotlin + Room-плагин | падение сборки | прогнать «пустую» сборку с KSP до написания фич; держать KSP на 2.3.x |
| Navigation 3 1.2.1 без предиктивного pop | UX-шероховатость back-жеста | осознанно; переход на 1.3.x после стабильной |
| `compileSdk 37`, `targetSdk 36` | в 2027 Play поднимет требование | обновление targetSdk — отдельная задача с прогоном behavior changes |
| Gradle 9.9.0 свежая | неизвестные регрессии с AGP 9.5.1 | остаёмся на 9.8.2; апгрейд после патча |
| Espresso 3.7 не работает на API 37 (по опыту Calc1) | instrumented-тесты на новых устройствах | минимум instrumented; при необходимости Espresso 3.8.0-alpha01 |
| AGP test suites для скриншотов — experimental | нестабильный CI | Roborazzi как основной; test suites — кандидат |
| Переключатель языка требует `AppCompatActivity` | смена базового класса активити | решить в ТЗ: MVP без in-app переключателя, либо `AppCompatActivity` сразу |
| Палитра Calc1 не проверена на контраст AA в тёмной теме | доступность | проверить контрасты и при необходимости скорректировать тона (задача дизайна) |

---

## 8. Что дальше (вход для ТЗ и дизайна)

1. **ТЗ** фиксирует: список экранов (Калькулятор, Каталог, Редактор препарата, Настройки), поля формы и направления расчёта, набор единиц дозы, правила усиленного режима, тексты предупреждений (RU/EN), границы санитарных пределов.
2. **Дизайн** использует палитру Calc1: light/dark схемы, типографика с табличными цифрами, состояния «норма/предупреждение/ошибка», экран для fontScale 1.5, edge-to-edge макеты.
3. **Перед кодированием** — повторная проверка версий из раздела 2 (реестры + `aar-metadata`), спайк Room 3 + KSP + AGP 9.5 (полдня), после чего версии фиксируются в `libs.versions.toml`.

Чек-лист приёмки этого исследования:

- [x] Версии проверены по первоисточникам, каждая — с указанием реестра/документа.
- [x] Обоснован выбор Navigation 3 1.2.1 против 1.3.0-alpha04 (фактический диф API).
- [x] Обоснован выбор Room 3.0.3 против 2.8.5 и зафиксирован план Б.
- [x] Обоснован ручной DI против Hilt, с условиями пересмотра.
- [x] Определены `compileSdk`/`targetSdk`/`minSdk` из требований артефактов и Play.
- [x] Описаны слои, модель препарата, репозитории, место и контракты вычислительного движка.
- [x] Решение `BigDecimal` vs `Double` подкреплено прогоном на JVM.
- [x] Определены стратегия тестов и CI.

---

## 9. Источники

Официальная документация и реестры (проверено 2026-11-07):

1. Navigation 3 — релизы и версии: https://developer.android.com/jetpack/androidx/releases/navigation3
2. Navigation 3 — база: https://developer.android.com/guide/navigation/navigation-3/index
3. Navigation 3 — сохранение состояния и back stack: https://developer.android.com/guide/navigation/navigation-3/save-state
4. Navigation 3 — декораторы `NavEntry`: https://developer.android.com/guide/navigation/navigation-3/naventrydecorators
5. Navigation 3 — Scenes и адаптивные раскладки: https://developer.android.com/guide/navigation/navigation-3/scenes/index
6. Navigation 3 — анимации переходов: https://developer.android.com/guide/navigation/navigation-3/animate-destinations
7. Navigation 3 — возврат результатов: https://developer.android.com/guide/navigation/navigation-3/return-results
8. Рецепты Navigation 3 (эталонные примеры): https://github.com/android/nav3-recipes
9. AGP 9.5.0 — релиз-ноты и матрица совместимости: https://developer.android.com/build/releases/agp-9-5-0-release-notes
10. AGP, D8, R8 — требования к версии Kotlin: https://developer.android.com/build/kotlin-support
11. Миграция на built-in Kotlin (AGP 9): https://developer.android.com/build/migrate-to-built-in-kotlin
12. Google Play — требования к target API: https://developer.android.com/google/play/requirements/target-sdk
13. Room 3.0 — миграция с 2.x: https://developer.android.com/training/data-storage/room/migration-2-to-3
14. Скриншот-тесты Compose через AGP test suites: https://developer.android.com/studio/preview/compose-screenshot-testing-with-testsuites
15. Per-app language preferences: https://developer.android.com/guide/topics/resources/app-languages
16. System bars / edge-to-edge в Compose: https://developer.android.com/develop/ui/compose/system/system-bars
17. Тестирование доступности в Compose: https://developer.android.com/develop/ui/compose/accessibility/testing
18. Gradle — текущая версия: https://services.gradle.org/versions/current
19. KSP — релизы: https://github.com/google/ksp/releases (2.3.13, 2026-10-30); README: https://github.com/google/ksp/blob/main/README.md
20. Dagger/Hilt — релизы: https://github.com/google/dagger/releases (2.60.1)
21. Maven-артефакты и их версии: Google Maven https://dl.google.com/dl/android/maven2, Maven Central https://repo1.maven.org/maven2, Gradle Plugin Portal https://plugins.gradle.org
22. Метаданные AAR (`minSdk`, `minCompileSdk`, `minAndroidGradlePluginVersion`): `AndroidManifest.xml` и `META-INF/com/android/build/gradle/aar-metadata.properties` артефактов `compose-ui 1.12.1`, `material3 1.4.0`, `navigation3-runtime/ui 1.2.1`, `room3-runtime 3.0.3`
23. Референсный проект (палитра, стиль, совместимая связка версий): https://github.com/drwho174/Calc1
24. EOL-статусы Kotlin/Gradle/JDK: https://endoflife.date (Kotlin 2.2 EOL 2025-12-16; Gradle 8 — maintained; Temurin 21 — LTS до 2029-12-31)
