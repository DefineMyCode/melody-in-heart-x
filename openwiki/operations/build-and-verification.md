---
type: Operations
title: Build, Verification & Release Operations
description: Aggregates the debug/release/benchmark build variants and command matrix, the full verifyProductArchitecture rule checklist (now a buildSrc rule object), the Room schema export flow (v11 incl. word_preferences), Baseline Profile & Macrobenchmark operations, release signing/ABI/R8/largeHeap constraints, and key version-compatibility rules — the pre-commit and pre-release runbook for melody-in-heart.
tags: [android, gradle, build, verification, release, baseline-profile, architecture-gate, room, macrobenchmark]
verified:
  - by: openwiki/0.5.0
    at: 2026-10-21T03:20:08.093Z
sources:
  - id: openwiki-source-12c927e8bd03798dd5dd4315
    resource: repo://.github/workflows/pr-check.yml
  - id: openwiki-source-4d1d392666be6dfdd7a91a2e
    resource: repo://.github/workflows/release.yml
  - id: openwiki-source-ea70eb6c045047448e446296
    resource: repo://.gitignore
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-a107f16d58beac4b84f5c928
    resource: repo://app/proguard-rules.pro
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-16d18359865bd62201dd7887
    resource: repo://app/src/main/baselineProfiles/baseline-prof.txt
  - id: openwiki-source-deeebc2a57b6bfe93567972b
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/di/CoroutineModule.kt
  - id: openwiki-source-254bbd50662077e2ffc872ad
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/MelodyApplication.kt
  - id: openwiki-source-1c484cbac1560c88d6bddc5f
    resource: repo://benchmark/build.gradle.kts
  - id: openwiki-source-56b6a52542b58a12030ef3ee
    resource: repo://benchmark/src/main/java/cn/com/dcsgo/mihx/benchmark/BaselineProfileGenerator.kt
  - id: openwiki-source-4dc68853faeaaf9aef56e78b
    resource: repo://benchmark/src/main/java/cn/com/dcsgo/mihx/benchmark/ScrollBenchmark.kt
  - id: openwiki-source-f90eb2ce767b9455d448efd3
    resource: repo://benchmark/src/main/java/cn/com/dcsgo/mihx/benchmark/StartupBenchmark.kt
  - id: openwiki-source-2a9daaac1604f238ef4c63fb
    resource: repo://build.gradle.kts
  - id: openwiki-source-7d322a65ddca1cf4588fa5e3
    resource: repo://buildSrc/src/main/kotlin/cn/com/dcsgo/mihx/verify/ProductArchitectureVerification.kt
  - id: openwiki-source-cd02b468507d08514a40cb17
    resource: repo://buildSrc/src/test/kotlin/cn/com/dcsgo/mihx/verify/ProductArchitectureVerificationTest.kt
  - id: openwiki-source-a2371d6362e5db4bc834ad03
    resource: repo://CLAUDE.md
  - id: openwiki-source-0fee4e299146d673704acf56
    resource: repo://core/common/build.gradle.kts
  - id: openwiki-source-bb64a898e2c690a687519dc1
    resource: repo://core/common/src/main/java/cn/com/dcsgo/mihx/core/common/AppLogger.kt
  - id: openwiki-source-ff3f9cc463b5c4366c1cc9e6
    resource: repo://core/common/src/main/java/cn/com/dcsgo/mihx/core/common/PerformanceTrace.kt
  - id: openwiki-source-84f63a65c6b0c17aaa234838
    resource: repo://data/.gitignore
  - id: openwiki-source-0f1ea52b4adfffa8391b0e81
    resource: repo://data/build.gradle.kts
  - id: openwiki-source-561e2128ae9736ccc7b9d185
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/di/DatabaseModule.kt
  - id: openwiki-source-12a1d6d0e5553c5cb1517529
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/WordPreferenceEntity.kt
  - id: openwiki-source-577b7687e8ff177b6a586197
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/MelodyDatabase.kt
  - id: openwiki-source-51949c79e3bf4e421e7ff171
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/MelodyMigrations.kt
  - id: openwiki-source-517665524969e49ec1ee1552
    resource: repo://docs/refactor/PRODUCT_REFACTOR_AUDIT.md
  - id: openwiki-source-37ecb8ac896ee055dab0bc51
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerStartupFacade.kt
  - id: openwiki-source-6c62ee9f2720b7d2a8615c18
    resource: repo://feature/settings/src/main/java/cn/com/dcsgo/mihx/feature/settings/SettingsScreen.kt
  - id: openwiki-source-b68c69ba290f0fee793ec69b
    resource: repo://gradle.properties
  - id: openwiki-source-81d5f1627e19148569f46f81
    resource: repo://gradle/libs.versions.toml
  - id: openwiki-source-431155803d48ed8be56e0ba9
    resource: repo://player/build.gradle.kts
  - id: openwiki-source-e0fe5f5dc62e806494659452
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/FfmpegPcmDecoder.kt
  - id: openwiki-source-8a4ae96c838986d7eae43292
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/window/PlaybackWindowPerformanceShapeTest.kt
  - id: openwiki-source-e620d7484b72a53c7fa812cd
    resource: repo://settings.gradle.kts
generated: { by: "openwiki/0.5.0", at: "2026-10-21T03:20:08.093Z" }
---

# Build, Verification & Release Operations

This page is the pre-commit and pre-release runbook for `melody-in-heart` (applicationId `cn.com.dcsgo.mihx`, current `versionName 3.11.0` / `versionCode 46`). Every quality gate is carried by two mechanisms: the **command matrix** (Gradle lifecycle tasks) and **`verifyProductArchitecture`** — a custom verification task whose rule body is the `buildSrc` object `cn.com.dcsgo.mihx.verify.ProductArchitectureVerification`, statically asserting the whole source tree on every `check`. The design rationale for the module boundaries lives in `/openwiki/architecture/module-graph.md`; Room/DataStore details live in `/openwiki/architecture/data-persistence.md`.

## 1. Pre-commit command matrix

All commands run from the project root; use `.\gradlew.bat` on Windows and `./gradlew` on macOS/Linux.

### 1.1 The three build variants

| Variant | Definition | Key differences |
| --- | --- | --- |
| `debug` (default) | `buildTypes { debug { ... } }` | No minification/shrinking; `applicationIdSuffix = ".debug"` + `versionNameSuffix = "-debug"` so it can be installed alongside release on the same device; keeps full ABI for emulator compatibility |
| `release` | `buildTypes { release { ... } }` | `isMinifyEnabled = true` (R8) + `isShrinkResources = true` + `proguard-android-optimize.txt`; `ndk.abiFilters += "arm64-v8a"` ships a 64-bit only package; `resConfigs("zh", "en")` trims dependency-library translation resources |
| `benchmark` | `create("benchmark")` | `initWith(getByName("release"))` inherits all release settings but uses **debug signing** and `isDebuggable = false`; appends `x86_64` on top of the inherited `arm64-v8a` (must run on both real devices and x86_64 emulators); `matchingFallbacks += listOf("release")` |

### 1.2 Common commands

```bash
# Build APK
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleBenchmark

# Single-module fast compile (no APK; fastest feedback after a change)
.\gradlew.bat :data:compileDebugKotlin
.\gradlew.bat :player:compileDebugKotlin
.\gradlew.bat :core:skin:compileDebugKotlin

# Full JVM unit tests (no device; covers debug/release/benchmark test variants)
.\gradlew.bat test

# Single module / single test class
.\gradlew.bat :core:skin:test
.\gradlew.bat :player:test --tests "cn.com.dcsgo.mihx.player.window.ControllerQueuePlannerTest"

# Tests requiring a device or emulator
.\gradlew.bat :app:connectedAndroidTest   # Compose instrumentation tests
.\gradlew.bat :benchmark:connectedCheck   # Macrobenchmark (includes Baseline Profile generation)

# Formatting and architecture gates
.\gradlew.bat spotlessCheck              # format check only
.\gradlew.bat spotlessApply              # auto-format
.\gradlew.bat verifyProductArchitecture  # architecture/privacy/release constraints only
.\gradlew.bat check                      # full pre-commit gate

# Architecture-gate rule body unit tests
.\gradlew.bat :buildSrc:test
```

`:app:generateBaselineProfile` regenerates the Baseline Profile (see §4). `connectedAndroidTest` and `:benchmark:connectedCheck` require a real device/emulator; MIUI real devices may be unable to run Macrobenchmark's automatic authorization/frame-confirmation steps because of ROM limitations (see `docs/refactor/PRODUCT_REFACTOR_AUDIT.md`).

### 1.3 What `check` aggregates

The root `build.gradle.kts` wires `check` to three sets of tasks: the root-level `spotlessCheck`, the root-level `verifyProductArchitecture`, and `check` for all 15 subprojects. Meanwhile the `subprojects` block makes each subproject's `check` depend on its own `spotlessCheck` (three formatting rules: Kotlin, Kotlin script, XML). So a `check` failure is one of formatting, architecture assertion, or unit tests; the `GradleException` message in the output names the failing rule. `verifyProductArchitecture` is now pure orchestration: its `doLast` calls `ProductArchitectureVerification.verify(rootDir)` and, non-empty, throws one `GradleException` carrying **all** collected violations at once (collection-style, not fail-fast).

```mermaid
flowchart TD
    CHECK["gradlew check"] --> SPOT["spotlessCheck (root + every subproject)"]
    CHECK --> ARCH["verifyProductArchitecture → buildSrc ProductArchitectureVerification"]
    CHECK --> SUB["each of the 15 module checks"]
    SUB --> MODSPOT["module spotlessCheck"]
    SUB --> MODTEST["module unit tests (all variants)"]
    ARCH --> FAIL["GradleException (all violations at once)"]
```

*Aggregation of the commit gate: formatting, architecture assertions, and per-module unit tests all run in `check`; every `verifyProductArchitecture` rule failure aborts the build with a `GradleException`.* The CI gates (`pr-check.yml`, and `release.yml`'s own `check` job) run `./gradlew check :app:assembleDebugAndroidTest :buildSrc:test`: `assembleDebugAndroidTest` compiles the instrumentation test sources without a device (catching UI signature drift), and `:buildSrc:test` exercises the rule body itself. Both workflows are skipped on purely documentation changes (`docs/**`, `openwiki/**`, `**.md`).

## 2. The `verifyProductArchitecture` rule checklist

The task lives in the root `build.gradle.kts` (`tasks.register("verifyProductArchitecture")`, `group = "verification"`). Since m-10 (2026-10-15 review) the rule body itself lives in `buildSrc` as the object `ProductArchitectureVerification`, which takes the repo `File` tree and returns a list of violation strings (empty = pass). It walks `kt/kts/java/xml/toml` files skipping `build/`, `.gradle/`, `.git/`, `.idea/`, **and `buildSrc/`** (whose own sources contain the rule texts, e.g. legacy-name counterexamples, and would otherwise self-trigger). Behavior is unit-tested by `ProductArchitectureVerificationTest`, which builds temp-directory repo fixtures to prove each rule fires and the message is locatable. **There is no incremental or allowlist mechanism** — any newly added file/module enters the next scan.

| # | Category | Enforced content (summary) |
| --- | --- | --- |
| 1 | Formatting infrastructure | Spotless must stay in the version catalog; the root build declares `alias(libs.plugins.spotless)`; `check` must depend on `spotlessCheck` |
| 2 | Module inventory & `.gitignore` | `settings.gradle.kts` must contain all 15 modules (`:app`, `:core:model`, `:core:common`, `:core:ui`, `:core:skin`, `:domain`, `:data`, `:player`, six `:feature:*`, `:benchmark`); each module directory's `.gitignore` must include `/build/`, `/.cxx/`, `/.externalNativeBuild/`, `/captures/` |
| 3 | Hilt entry points | `MelodyApplication`=`@HiltAndroidApp`, `MainActivity`=`@AndroidEntryPoint`, `AppMediaSessionService` (`:player`)=`@AndroidEntryPoint`, `PlayerViewModel`=`@HiltViewModel`; the four files must exist with the annotations |
| 4 | DI module existence | `app/di/` `CoroutineModule`/`LoggerModule`/`PlayerModule` and `data/di/` `RepositoryModule`/`DatabaseModule`/`DataStoreModule` must exist, each a same-named `@Module` + `@InstallIn(SingletonComponent::class)` |
| 5 | Architecture docs presence | `docs/architecture/PLAYBACK_STATE_MACHINE.md` must exist and contain the 8 states plus `AppMediaSessionService`/`PlaybackController`/`ControllerPlaybackStateSynchronizer`/`PlayerControllerStateFacade`/`PlayerMediaEventFacade`; `docs/refactor/PRODUCT_REFACTOR_AUDIT.md` must contain `Verified`, `Still Not Fully Proven`, `.\gradlew.bat test`, `:app:assembleRelease`, `:app:assembleBenchmark`, `Macrobenchmark`, `:benchmark` |
| 6 | benchmark module shape | `benchmark/build.gradle.kts` + `StartupBenchmark.kt` must exist; the build file must contain `libs.plugins.android.test`, `targetProjectPath = ":app"`, `androidx.benchmark.macro.junit4`; `StartupBenchmark` must contain `MacrobenchmarkRule`, `StartupTimingMetric`, `StartupMode.COLD`, `startActivityAndWait`, `packageName = "cn.com.dcsgo.mihx"` |
| 7 | PerformanceTrace anchors | `core/common/.../PerformanceTrace.kt` must exist; `MusicRepository` must keep `music_import_scan`/`music_import_folder`; `PlaybackController` must keep `controller_play_queue`/`controller_prepare_queue`/`controller_sync_queue`/`play_next_command` |
| 8 | Playback window performance shape test | `player/src/test/.../PlaybackWindowPerformanceShapeTest.kt` must exist and cover `100`, `500`, `1_000`, `71`, `WindowedControllerQueuePlanner` (controller window ≤ 71 items under 100/500/1000-song queues) |
| 9 | Legacy ban | whole repo forbids the `com.dcsgo.data.model` legacy package references; `PlayerViewModelComponents`/`PlayerViewModelComponentFactory` (the old manual-assembly hub, replaced by `PlayerRuntime` + injected facade/graph objects) — optionally skipping any `build.gradle.kts` file that merely mentions the names — and Android Things dependency strings |
| 10 | Module dependency boundaries | `feature/**/build.gradle.kts` forbids `project(":data")`, `project(":player")`, and any `project(":feature:…")`; feature/domain/player sources forbid `import cn.com.dcsgo.mihx.data.(repository|local).*`; `MusicRepository` must not declare `SongRepository`/`PlaylistRepository`/`MusicImportRepository`/`AlbumArtRepository` supertypes, and the four `*RepositoryAdapter.kt` must exist and implement the interfaces; the whole `:player` module forbids the stale `cn.com.dcsgo.mihx.data.player` package (its classes now live in `cn.com.dcsgo.mihx.player`, matching the module namespace — this rule prevents regression); `:core:model` must have no res XML and no `R.(drawable|string|color|dimen|raw)`/`android.R.` references |
| 11 | Feature Route/Screen pattern | `:feature:lyrics`/`:feature:settings` must have `XxxRoute.kt`+`XxxScreen.kt`; play statistics / quick-skip songs (`:feature:home`) and version management (`:feature:user`) likewise must be Route+Screen ized; `AppOverlay.kt`/`AppOverlayHost.kt`/`OverlayRoute.kt` fail immediately if present; `:feature:home` must not directly import `cn.com.dcsgo.mihx.ui.lyrics` |
| 12 | Permission & settings policy | `PlayerStartupFacade` must not contain `Bluetooth`; `SettingsScreen` must expose「蓝牙播放监听」/「申请蓝牙权限」entries; Bluetooth playback listening / playback notification must be persisted `PlayerSettingsRepository` (`bluetoothPlaybackMonitoringEnabled`/`playbackNotificationEnabled`) → DataStore (`BLUETOOTH_PLAYBACK_MONITORING_ENABLED`/`PLAYBACK_NOTIFICATION_ENABLED`) → `PlayerUiState` settings; `AppNavHost`/`AppRoot` must not hold either state in local `remember { }`; `PermissionCoordinator` must offer a notification permission request with an `onGranted` callback; the four startup-path files (`MainActivity`/`AppRoot`/`PlayerRuntime`/`PlayerStartupFacade`) must not contain `requestNotificationPermission(`/`requestBluetoothConnectPermission(`/`POST_NOTIFICATIONS`/`BLUETOOTH_CONNECT` (permissions may only be triggered by the user in Settings) |
| 13 | LazyColumn stable keys | `app/core/feature` Kotlin files must declare `key =` within 160 chars of an `items`/`itemsIndexed` call; bare `item { }` is forbidden |
| 14 | Logging channel | apart from `AppLogger.kt` itself, direct `Log.(d|i|w|e|v|wtf)(` calls are forbidden — all logging goes through `AppLog`/`AppLogger` |
| 15 | Manifest placement | the `:app` manifest must not declare `AppMediaSessionService` (the `:player` manifest must — it declares `cn.com.dcsgo.mihx.player.AppMediaSessionService`); it must not declare `READ_EXTERNAL_STORAGE` (forbidden for SAF folder import); **`READ_MEDIA_AUDIO` IS permitted** — it is declared in the `:app` manifest so an already-granted device can fast-scan via `java.io.File`, with SAF document tree remaining the primary path and fallback |
| 16 | release/benchmark build config | the `release` block must contain `isMinifyEnabled = true` and `isShrinkResources = true`; `create("benchmark")` must exist with `initWith(getByName("release"))` |
| 17 | Privacy/backup exclusions | `backup_rules.xml` and `data_extraction_rules.xml` must each contain all required exclusions (see §3.3) |
| 18 | Room schema immutability | `data/schemas/.../MelodyDatabase/` `1.json`/`2.json`/`3.json` must exist; v1 must not contain `quick_skip_short_play_counts`, v2 must contain it and must not contain `lrcUri`, v3 must contain `lrcUri`; `MelodyMigrations` must register `Migration(1, 2)` and `Migration(2, 3)`, and `DatabaseModule` must call `addMigrations(*MelodyMigrations.ALL)` |

**The `release` block is extracted by brace-pairing, not a regex** (since m-10): the old rule that grabbed up to the first closing brace was a review-named fragility that truncated on nested lambdas, so the rule now uses `extractBracedBlock` which counts `{`/`}` and returns the balanced block. This means nesting inside the `release` block is safe. The signing decision (`releaseSigningConfig`) still deliberately lives outside `buildTypes` so a missing key can gate the build before the release block is ever parsed; the block keeps only flat assignments (`signingConfig = releaseSigningConfig`, `isMinifyEnabled = true`, `isShrinkResources = true`, `proguardFiles(...)`, `ndk { abiFilters += "arm64-v8a" }`).

### 2.1 Skin and "我的" page invariants (enforced by unit tests, not the gate)

The `verifyProductArchitecture` gate does **not** scan `:core:skin` internals; the skin conventions are locked by focused unit tests instead:

- **`LibrarySegmentsMatchSourceTest`** (`:core:skin`) reads the `:feature:playlist` `LibraryTab` enum source and asserts the description's library-page segments match it word-for-word (including order) — a deliberate guard against the P1 "description proves itself" trap.
- **`UserSectionsTest`** (`:feature:user`, 10 cases) asserts `UserSections` keys/`DEFAULT_ORDER` line up with the pre-refactor hardcoded order, that `UserSections.SKIN_SWITCHER == "skinSwitcher"` equals `SkinPartCatalog.SKIN_SWITCHER` literally, and that `parse` is tolerant of unknown/blank input (filtering to known keys).
- **`SkinSwitcherCatalogConsistencyTest`** (`:app`) keeps `UserSections.SKIN_SWITCHER` and `SkinPartCatalog.SKIN_SWITCHER` from drifting apart, since `SkinShellResolver` maps both the `skinSwitcher` and legacy `customSkin` part names to the same `UserSections.SKIN_SWITCHER` partition.

## 3. Release constraints

### 3.1 ABI, `largeHeap`, & resource trimming

Since `minSdk 33`, all real devices are 64-bit, so release ships only `arm64-v8a`; `resConfigs("zh", "en")` trims other languages' translated resources for the dependency libraries, shrinking `resources.arsc`. The `:app` manifest's `<application>` also sets `android:largeHeap="true"` (declared 2026-10 release, v3.11.0) so playback with large playlists and album-art memory stays within a larger heap. debug/benchmark keep multiple ABIs to stay compatible with x86_64 emulators.

### 3.2 R8 & ProGuard rules

Release enables R8 minification + resource shrinking. `app/proguard-rules.pro` manually keeps:

- manifest/session entry points: `MelodyApplication`, `MainActivity`, `AppMediaSessionService`;
- Hilt/Room/Media3 reflection-required attributes (`Signature`, `RuntimeVisibleAnnotations`, etc.);
- `MelodyDatabase` and all entity classes (so release crashes stay diagnosable);
- `FfmpegAudioDecoder`/`FfmpegLibrary` — `:player`'s `FfmpegPcmDecoder` (at `cn.com.dcsgo.mihx.player`) reflectively drives the Jellyfin FFmpeg extension's package-private decoders (`FfmpegLibraryProbe` probes with `Class.forName`); R8 renaming/trimming would break the decode path. (Note: the keep rule for `AppMediaSessionService` still cites the legacy `cn.com.dcsgo.mihx.data.player.AppMediaSessionService` name while the class now lives at `cn.com.dcsgo.mihx.player` — the Media3 decoder keep rules, which are what matter for the decode path, address the actual `androidx.media3.decoder.ffmpeg` classes.)

### 3.3 Signing strategy & privacy exclusions

```mermaid
flowchart TD
    START["assembleRelease"] --> KS{"keystore.properties exists?"}
    KS -->|"yes"| REL["use release signingConfig"]
    KS -->|"no"| REQ{"-PrequireReleaseSigning=true?"}
    REQ -->|"yes"| ERR["GradleException: build fails"]
    REQ -->|"no"| DBG["warn + fall back to debug signing (local only, not publishable)"]
```

*Release signing decision: when `keystore.properties` (gitignored; holds `storeFile/storePassword/keyAlias/keyPassword`) is missing, a local build falls back to debug signing with a warning; releases/CI must pass `-PrequireReleaseSigning=true`, which fails the build without the key so a debug-signed "official" APK can't slip out. The decision is computed once outside `buildTypes` through a `releaseSigningConfig` val; `release.yml` restores the key from the `RELEASE_KEYSTORE_B64` secret into `key/mihx` and writes `keystore.properties` before `:app:assembleRelease`.*

The privacy exclusions are rule 17's checklist; both rule files (`backup_rules.xml`'s `<full-backup-content>` and `data_extraction_rules.xml`'s `cloud-backup` + `device-transfer`) must each contain every entry: the legacy SharedPreferences files (`music_player_prefs.xml`, `play_stats_prefs.xml`, `quick_skip_songs_prefs.xml`); the full Room family (`melody.db`, `melody.db-journal`, `melody.db-shm`, `melody.db-wal`); the DataStore files (`datastore/player_settings.preferences_pb`, `datastore/playback_state.preferences_pb`, and the newer `datastore/playlist_resume.preferences_pb`, `datastore/mood_time_slot.preferences_pb`, `datastore/emotion_failures.preferences_pb`); and the album-art caches (`album_art_cache/`, `album_art/`, `cache/album_art/`). Rationale: library/playback state is device-bound (SAF URIs become invalid off-device), so backup only adds restore load and privacy surface. **When adding a new persisted file, add it to both rule files, or the architecture gate fails** (rule 17's required subset — the three prefs, the four `melody.db` variants, `player_settings` + `playback_state` DataStore files, and `cache/album_art/` — is the hard gate; the other DataStore/cache paths are part of the source rule files).

## 4. Baseline Profile & the `:benchmark` module

### 4.1 Baseline Profile flow

- Artifact: `app/src/main/baselineProfiles/baseline-prof.txt` (~2425 rules, generated on a Pixel 6 emulator per the audit doc).
- Packaging: release builds package it automatically; `androidx.profileinstaller` (1.4.1, an `app` dependency) installs it to improve cold start and first-screen scrolling.
- Regeneration: run `:app:generateBaselineProfile` or `:benchmark:connectedCheck`; `BaselineProfileGenerator` covers startup + home/player and playlist-page scrolling paths and rewrites the same file. Regenerate after changing the startup path or home-list implementation.

### 4.2 The `:benchmark` module

| Class | Metric / rule | Behavior |
| --- | --- | --- |
| `StartupBenchmark` | `StartupTimingMetric`, `StartupMode.COLD`, 5 iterations per test | Two cold-start tests: `coldStartupWithoutBaselineProfile` (`CompilationMode.None()`) vs `coldStartupWithBaselineProfile` (`CompilationMode.Partial(baselineProfileMode = Requirement)`); each does `pressHome()` then `startActivityAndWait()` to measure cold start with/without baseline-profile precompilation |
| `ScrollBenchmark` | `FrameTimingMetric` (P50/P90/P95 frame times), cold start, 5 iterations | Swipes the home screen down and up 3 times each, covering LazyColumn item creation/reuse paths |
| `BaselineProfileGenerator` | `BaselineProfileRule.collect` | Startup + scroll collection; writes back to `baseline-prof.txt` |

Module shape: `com.android.test` plugin, `targetProjectPath = ":app"`, `android.experimental.self-instrumenting = true`, only the `benchmark` variant enabled (`androidComponents.beforeVariants` filter), debug signing, and `testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"` (emulator results are flagged as warnings, not aborted). Running requires a real device/emulator; MIUI ROMs may block Macrobenchmark's automatic authorization/frame-confirmation steps; the audit records an emulator cold-start median of ~810 ms (software-rendered) and notes real-device acceptance is still incomplete.

### 4.3 `PerformanceTrace` anchor strategy

`core/common`'s `PerformanceTrace` is a deliberate observability switch, not a logging side effect:

- `isEnabled` initial value = `BuildConfig.DEBUG`: **debug defaults to on, release stays silent** (privacy + volume decision);
- `allow(operation)`/`disallow(operation)` provide a release whitelist so key playback paths can keep emitting in production;
- `measure { }`/`log { }` output through `AppLog.info`, with metadata going through `redactSensitiveValuesForLog()` first;
- `core/common` explicitly turns on `buildFeatures { buildConfig = true }` for this.

Anchors (locked by the architecture gate): `MusicRepository`'s `music_import_scan`/`music_import_folder`, and `PlaybackController`'s `play_next_command`/`controller_play_queue`/`controller_prepare_queue`/`controller_sync_queue`. Deleting or renaming these string literals fails `verifyProductArchitecture`. The companion `PlaybackWindowPerformanceShapeTest` locks the window shape (51–71 items) of 100/500/1000-song queues at the JVM layer.

## 5. Room schema flow

`:data` exports schemas via KSP: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`, with `MelodyDatabase` declaring `version = 11, exportSchema = true`; historical snapshots live in `data/schemas/cn.com.dcsgo.mihx.data.local.MelodyDatabase/1.json…11.json`. Version 11 (2026-10-20) added the `word_preferences` emotion-word listening-preference voting table (`WordPreferenceEntity`, `tableName = "word_preferences"`) via `MIGRATION_10_11`. Workflow for any entity/DAO change:

1. Modify the entity/DAO under `data/src/main/java/cn/com/dcsgo/mihx/data/local/{entity,dao}`.
2. Bump `MelodyDatabase` version by 1 (currently 11).
3. Build `:data` once — KSP auto-generates the new-version JSON in `data/schemas/.../` (don't hand-write).
4. Add `Migration(N, N+1)` in `data/.../local/migration/MelodyMigrations.kt`, appending it to the trailing `ALL: Array<Migration>` array (declared last so object properties initialize in order). The migrations were extracted from `DatabaseModule` (m-4) into the public `MelodyMigrations` object so the migration regression test (`MelodyDatabaseMigrationTest`) references the exact same instances as production — no "test migrations" vs "live migrations" drift.
5. `DatabaseModule` registers them with `addMigrations(*MelodyMigrations.ALL)`.
6. Add a focused unit test for complex data-migration migrations.
7. **Never modify an existing schema JSON** — they are immutable snapshots, and rule 18 detects tampering by the v1/v2/v3 content deltas.

The full migration history (1→2 adds `quick_skip_short_play_counts`, 2→3 adds `lrcUri`, …10→11 adds the `word_preferences` table) is in `/openwiki/architecture/data-persistence.md`.

## 6. Key versions & compatibility constraints

| Item | Value | Constraint |
| --- | --- | --- |
| Kotlin | 2.0.21 | Compose compiler is built into Kotlin 2.0+; modules must apply `org.jetbrains.kotlin.plugin.compose` (the `kotlin-compose` alias); **the old `composeOptions { kotlinCompilerExtensionVersion }` block is forbidden** — it is ineffective under the new plugin |
| KSP | 2.0.21-1.0.28 | Prefix must match the Kotlin version exactly; upgrading Kotlin requires a synchronized KSP upgrade |
| AGP | 8.13.2 | `compileSdk 36`; `:benchmark` uses `com.android.test` at the same version |
| Compose BOM | 2025.09.01 | Compose versions are centralized via the BOM |
| Media3 | 1.9.0 + Jellyfin FFmpeg `1.9.0+1` | FFmpeg decoding is reflective (see §3.2 keep rules); after a Media3 upgrade, verify `FfmpegLibraryProbe` still hits |
| Room / Hilt / DataStore | 2.6.1 / 2.52 / 1.1.1 | Room schema export per §5 (currently v11) |
| minSdk / targetSdk | 33 / 36 | Java 11 bytecode (`compileOptions`/`jvmTarget` set to `VERSION_11` repo-wide) |
| versionName / versionCode | 3.11.0 / 46 | Authoritative in `app/build.gradle.kts` |
| LiteRT / WorkManager | 1.4.1 / 2.9.1 | Emotion-analysis model runtime + background work |

### 6.1 Deterministic Windows build config

`gradle.properties`' three lines are the prerequisite for reproducible verification on Windows:

- `kotlin.compiler.execution.strategy=in-process`: Kotlin compilation runs inside the Gradle daemon. Previously the Kotlin daemon failed on Windows over client-marker file permissions and fell back mid-build, making incremental results unstable; in-process removes that failure source, which also means **`org.gradle.jvmargs` (`-Xmx2048m`) is the heap compilation actually has available** (`kotlin.daemon.jvmargs=-Xmx1536m` only affects out-of-process daemons).
- `org.gradle.parallel=true` + `org.gradle.caching=true`: multi-module parallel build + build cache.
- `android.nonTransitiveRClass=true`: each module's R class holds only its own declared resources, so **r classes must not be used to reference resources across modules** (complementing rule 10's `:core:model` purity check).

**Doc-drift note**: `CLAUDE.md`'s Key Versions table now agrees with the real config (Compose BOM 2025.09.01, versionName 3.11.0 / versionCode 46, Kotlin 2.0.21, AGP 8.13.2, KSP 2.0.21-1.0.28, Room 2.6.1); its "当前 v10" Room line is behind the actual v11 but `app/build.gradle.kts`'s `versionCode` and the `MelodyDatabase.version` field are authoritative.

### 6.2 Skin module boundaries

`:core:skin` deliberately carries **no UI dependency**: it depends only on `org.json` and `:core:model` (for `ThemeVariant`), and holds no routing knowledge — the description must be headlessly parseable and validate on-device without UI (also what makes it unit-testable). Only `:app` and `:feature:user` depend on it; the part → host-artifact mapping (routes, drawable ids, section order) happens in `:app`'s `SkinShellResolver`, and `:feature:user` owns both the `UserSections` partition metadata and the skin-switcher section. `:core:skin`'s `build.gradle.kts` must not add a Compose dependency or `R.*`/`AppRoutes` references, or it defeats the module layering the gate enforces.

## 7. Workflows & OpenWiki conventions

- **Two GitHub Actions workflows exist** in `.github/workflows/`: `pr-check.yml` and `release.yml`.
  - `pr-check.yml` runs on pull requests and pushes to `main` (skipped for `docs/**`, `openwiki/**`, `**.md`): `./gradlew check :app:assembleDebugAndroidTest :buildSrc:test` on `ubuntu-24.04` with JDK 17, uploading test/lint reports on failure.
  - `release.yml` responds to `v*` tags or `workflow_dispatch`: first runs the same `check :app:assembleDebugAndroidTest :buildSrc:test` gate; then, in `build-release` (needs `check`), restores the keystore from `RELEASE_KEYSTORE_B64`, builds `:app:assembleRelease` with `-PrequireReleaseSigning` semantics (key presence implied by the written `keystore.properties`), verifies the APK signature with `apksigner` (build-tools 36.0.0), renames it `melody-heart-<tag>-arm64.apk`, uploads it, and creates/updates a GitHub Release. The tag is validated (`vX.Y.Z` / `vX.Y.Z-alpha`) and injected via an env var to avoid shell interpolation, and the `tag` input for `workflow_dispatch` avoids `${{ }}` injection.
- **Prefer changing source and `docs/`, don't hand-edit generated pages.** `openwiki/` is generated from source via `openwiki code --update` (see `/openwiki/.run.json`/`.last-update.json` for the current/last runs); AGENTS.md instructs treating source and tests as authoritative and letting OpenWiki regenerate. Hand-edits to generated pages will be overwritten on the next run; architecture facts belong in `docs/architecture/*.md` (some of which rule 5 locks), then get re-ingested by OpenWiki.
- **New logging must go through `AppLog`/`AppLogger`** (hard rule 14). `AndroidAppLogger` behavior: `debug/info/warning` only emit when `BuildConfig.DEBUG`, `error` always emits; all channels redact messages and stacks through `redactSensitiveValuesForLog()` (`content://`, `file://`, Bluetooth MACs, device names, Windows/POSIX paths replaced with placeholders). So **in release only the error channel actually emits, and it is redacted** — don't bypass `AppLog` for raw `android.util.Log` just to see logs in release. `AppLog.install(AndroidAppLogger(BuildConfig.DEBUG))` is wired in `MelodyApplication.onCreate`; before install the no-op default is used.
- **Prefer the narrowest quiet validation** (AGENTS.md): compile/test only the module you changed — e.g. `:core:skin:test` — and leave the full `check` for pre-commit; preserve complete failure output without truncation.

## Related pages

- `/openwiki/architecture/module-graph.md` — full derivation of module layering and dependency boundaries (design background for rule 10 and §6.2)
- `/openwiki/architecture/data-persistence.md` — Room/DataStore persistence details and the complete migration history (§5's expansion)
- `/openwiki/quickstart.md`, `/openwiki/testing/test-map.md` — quick start and test map
