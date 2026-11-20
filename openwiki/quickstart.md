---
type: Quickstart
title: Quick start and task routing
description: "Onboarding entry for melody-in-heart at v3.15.0 / versionCode 54 (MelodyDatabase v20): project identity, module layout, build and verification commands, the hard constraints per task area, and a task→page routing map covering audio-spec tiers, storage statistics, bitrate sorting and time-slot exclusions."
tags: [quickstart, onboarding, android, gradle, multi-module, offline-first, audio-quality, storage-stats, room-migrations, task-routing]
verified:
  - by: openwiki/0.5.0
    at: 2026-11-20T09:49:41.934Z
sources:
  - id: openwiki-source-12c927e8bd03798dd5dd4315
    resource: repo://.github/workflows/pr-check.yml
  - id: openwiki-source-8037e2358a2c4f9b2c722a11
    resource: repo://AGENTS.md
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-3e7209a00de8aa3c66577a46
    resource: repo://app/src/androidTest/java/cn/com/dcsgo/mihx/HomeScreenComposeTest.kt
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-c997f8c2a81730fe7eb841ae
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/AppNavHost.kt
  - id: openwiki-source-4ca5f9c29b016f072d8ed57c
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt
  - id: openwiki-source-bb00a8b0f0083ccce829d880
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanScheduler.kt
  - id: openwiki-source-81bdf5bdc16c1ff3afd5bcc8
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanWorker.kt
  - id: openwiki-source-c5e9b6973b445a58e095b661
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/player/NowPlayingSurface.kt
  - id: openwiki-source-2c2546b12c2a5a0327cc383e
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/shell/AppShell.kt
  - id: openwiki-source-254bbd50662077e2ffc872ad
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/MelodyApplication.kt
  - id: openwiki-source-5635d1597a4bd6c4e03aa193
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/navigation/AppRoutes.kt
  - id: openwiki-source-0040b304dd9434d71728bd0c
    resource: repo://app/src/test/java/cn/com/dcsgo/mihx/app/shell/SheetSkeletonTest.kt
  - id: openwiki-source-1c484cbac1560c88d6bddc5f
    resource: repo://benchmark/build.gradle.kts
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
  - id: openwiki-source-18d794740dc3cb6a012e0ccb
    resource: repo://core/model/build/generated/emotionModelSpec/kotlin/cn/com/dcsgo/mihx/core/model/EmotionModelSpec.kt
  - id: openwiki-source-af2fc8f53ed6c2df95c624cd
    resource: repo://core/model/emotion_model_spec.properties
  - id: openwiki-source-c0c4dca2fa4503cee170d1ca
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/AudioQualityTier.kt
  - id: openwiki-source-41abfc4ea70fa1620e2f08cd
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/LocalMusicStorageStats.kt
  - id: openwiki-source-9645009bf4cd7f02b15b26de
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/PlayQueue.kt
  - id: openwiki-source-b8ad601bbc2ce9deae0cf5a6
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/RandomEnhanceMode.kt
  - id: openwiki-source-ad278d499a6d617246596b70
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/Song.kt
  - id: openwiki-source-cd118cd84c11f3bf730445f6
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/TimeSlotConfig.kt
  - id: openwiki-source-c45f629879f8babdcb1f3397
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/WordPreference.kt
  - id: openwiki-source-69570babfa5b1b1809a65b7b
    resource: repo://core/skin/build.gradle.kts
  - id: openwiki-source-452d07c1780ac74346af3f26
    resource: repo://core/skin/src/test/java/cn/com/dcsgo/mihx/core/skin/SkinParserValidationTest.kt
  - id: openwiki-source-68cd28d87a169eb3e96aca51
    resource: repo://data/schemas/cn.com.dcsgo.mihx.data.local.MelodyDatabase/17.json
  - id: openwiki-source-561e2128ae9736ccc7b9d185
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/di/DatabaseModule.kt
  - id: openwiki-source-8afa2e64020072855e89b8ac
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/SongEmotionEntity.kt
  - id: openwiki-source-e1ce80f5abb863f1723b9ef6
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/SongEntity.kt
  - id: openwiki-source-12a1d6d0e5553c5cb1517529
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/WordPreferenceEntity.kt
  - id: openwiki-source-577b7687e8ff177b6a586197
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/MelodyDatabase.kt
  - id: openwiki-source-51949c79e3bf4e421e7ff171
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/MelodyMigrations.kt
  - id: openwiki-source-12d5b8af1aa446b9573e2462
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/EmotionFailureStore.kt
  - id: openwiki-source-40f64973a9ec521eb09bf144
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/MediaStorageStatsRepositoryImpl.kt
  - id: openwiki-source-b90ad5e1e20562c61d70b296
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt
  - id: openwiki-source-99854c1cb5a148365aba8ef6
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsDataStore.kt
  - id: openwiki-source-29a14d2aa8324beadcd09368
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsRepository.kt
  - id: openwiki-source-bccc40bc4f4db30340cd2659
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlaylistResumeDataStore.kt
  - id: openwiki-source-20d823efa2e1a5f403b02d3e
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongEmotionsRepository.kt
  - id: openwiki-source-f8d95aa838cf04a8eb5e5e43
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/TimeSlotConfigStore.kt
  - id: openwiki-source-196170e31ff8ec60a116165b
    resource: repo://docs/README.md
  - id: openwiki-source-5805395d4353318199092fff
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/model/SongSortMode.kt
  - id: openwiki-source-39979cc6e8c7e5b52ecabd5f
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/ExplorePlaybackPolicy.kt
  - id: openwiki-source-ee8d5713acafafdce234f04d
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/MoodSlotResolver.kt
  - id: openwiki-source-15370df42a01365d6f089c6e
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/WordPreferenceRepository.kt
  - id: openwiki-source-24b821db224fd40a8ef00a39
    resource: repo://feature/home/src/main/java/cn/com/dcsgo/mihx/feature/home/HomeScreen.kt
  - id: openwiki-source-0a840abe5bf9183316c47b4e
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerMediaEventFacade.kt
  - id: openwiki-source-8e3d8a1f18f53d37da8744f1
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRandomQueueFacade.kt
  - id: openwiki-source-ff40146bae9715f1a3860309
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt
  - id: openwiki-source-fe80d1b3a38b98c97840e74d
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerViewModel.kt
  - id: openwiki-source-a8c5016cd654f9af00ac6734
    resource: repo://feature/playlist/src/main/java/cn/com/dcsgo/mihx/feature/playlist/SongSorter.kt
  - id: openwiki-source-b68c69ba290f0fee793ec69b
    resource: repo://gradle.properties
  - id: openwiki-source-81d5f1627e19148569f46f81
    resource: repo://gradle/libs.versions.toml
  - id: openwiki-source-1703b70681186dcde3003cfc
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/EmotionAnalyzer.kt
  - id: openwiki-source-e0fe5f5dc62e806494659452
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/FfmpegPcmDecoder.kt
  - id: openwiki-source-b5fe68676096345c29cfbdba
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackStateStore.kt
  - id: openwiki-source-a4b3e7d9ceb362621d621d7e
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/player/SongMediaItemMapper.kt
  - id: openwiki-source-8a4ae96c838986d7eae43292
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/player/window/PlaybackWindowPerformanceShapeTest.kt
  - id: openwiki-source-23775c3de52f3ab95a13cb8b
    resource: repo://README.md
  - id: openwiki-source-e620d7484b72a53c7fa812cd
    resource: repo://settings.gradle.kts
generated: { by: "openwiki/0.5.0", at: "2026-11-20T09:49:41.934Z" }
---

# Quick Start

This repository is **melody-in-heart** (applicationId `cn.com.dcsgo.mihx`): a fully local, offline-first, ad-free, network-free Android music player. Current release is **`3.15.0` / versionCode 54**; `minSdk 33`, `targetSdk 36`, `compileSdk 36`, Java 11 bytecode. Stack: Kotlin 2.0.21 + Jetpack Compose (BOM 2025.09.01) + Media3 1.9.0 (with the Jellyfin FFmpeg decoder extension) + Hilt 2.52 + Room 2.6.1 + DataStore 1.1.1 + WorkManager 2.9.1 + LiteRT 1.4.1 (on-device emotion inference through the `org.tensorflow.lite.Interpreter` API). The build is split into **15 Gradle modules** (`:app`, `:core:model` / `:core:common` / `:core:ui` / `:core:skin`, `:domain`, `:data`, `:player`, six `:feature:*`, `:benchmark`), and module boundaries are enforced by the custom `verifyProductArchitecture` gate.

Persistence at this release: **`MelodyDatabase` v20 with 14 entity tables**, `exportSchema = true`, exported snapshots **`1.json` … `20.json`** under `data/schemas/cn.com.dcsgo.mihx.data.local.MelodyDatabase/`, and **19 registered migrations** (`MIGRATION_1_2` … `MIGRATION_19_20` in `MelodyMigrations.ALL`). Plus five DataStore preference files (`player_settings`, `playback_state`, `playlist_resume`, `mood_time_slot`, `emotion_failures`). Before editing code, find your area in the routing table below; run `./gradlew check` before committing.

## What to read, what to trust

- **Source and tests are the only authority.** Every wiki conclusion carries `repo://` evidence, but the implementation keeps moving — when you act, trust the current code above any document.
- `AGENTS.md` (repository root) is the working convention file: the generated `openwiki/` tree is optional just-in-time context, changes must be proven by the narrowest quiet validation, and generated wiki pages must not be hand-edited (the scheduled workflow refreshes them).
- `README.md` is the **user-visible feature contract** in Chinese — the source of truth for UI labels such as `本地音乐`, `文件管理`, `存储与音质统计`, `深度校验`, `音质档位`, `随心播放`, `探索播放`, `多版本管理`. When you rename or move a surface, update the label here.
- `docs/README.md` is the index into `docs/`: queue architecture, playback state machine, mood time-slot design, the product-refactor audit, and the UI design references. These are design records, so cross-check them against code before relying on a detail.
- `CLAUDE.md` is now a **5-line OPENWIKI pointer stub** that only forwards to `AGENTS.md`; do not cite its old version tables or line numbers. Version truth lives in `app/build.gradle.kts` + `gradle/libs.versions.toml`, schema truth in `MelodyDatabase` + `data/schemas/`.

## Module overview

```mermaid
flowchart TD
    APP[":app shell - navigation, Hilt assembly, route wiring"] --> FEAT[":feature:* six UI modules"]
    APP --> PLAYER[":player Media3 service and controller"]
    APP --> DATA[":data Room and DataStore"]
    APP --> DOMAIN[":domain interfaces and playback policy"]
    APP --> CORE[":core:model / common / ui / skin"]
    FEAT --> DOMAIN
    FEAT --> CORE
    PLAYER --> DOMAIN
    PLAYER --> CORE
    DATA --> DOMAIN
    DATA --> CORE
    DOMAIN --> CORE
```

*Legal dependency direction: only `:app` may see every module. Cross-feature dependencies and feature→`:data`/`:player` dependencies fail `verifyProductArchitecture`. `:player` implementation classes live in `cn.com.dcsgo.mihx.player` (the legacy `cn.com.dcsgo.mihx.data.player` package must not reappear).*

| Layer | Module | One-line responsibility |
| --- | --- | --- |
| App shell | `:app` | `MelodyApplication` (Hilt `@HiltAndroidApp` + Coil `ImageLoaderFactory`), `MainActivity`, `AppRoot` / `AppNavHost`, route table `AppRoutes`, skin-driven shell, permission coordinator, emotion-scan worker, application-scoped ViewModels (`MoodTimeSlotViewModel`, playlist resume) |
| Feature | `:feature:home/playlist/user/lyrics/player/settings` | Each feature exposes `XxxRoute` + `XxxScreen` (Route/Screen split enforced by the gate). `:feature:player` hosts the `PlayerRuntime` facade composition root; `:feature:playlist` hosts the library list, `SongSorter`, file-check and `StorageStatsScreen`; `:feature:user` hosts mood time-slot editing, version management and the 音质说明 sheet |
| Domain | `:domain` | Repository interfaces + pure playback/sorting policy (`MoodSlotResolver`/`MoodSlotPolicy`/`MoodPoolStats`, `ExplorePlaybackPolicy`, `SongSortMode`, `SongVersionComparer`), no Android dependencies |
| Data | `:data` | Room `MelodyDatabase` (v20, 14 entity tables) + migrations and legacy-JSON import, DataStore wrappers, `AudioMetadataExtractor` (spec reading), `MediaStorageStatsRepositoryImpl` (byte measurement), narrow adapters binding domain interfaces |
| Player | `:player` | `AppMediaSessionService` (ExoPlayer + MediaSession), `PlaybackController`, windowed queue planning, Bluetooth coordination, `EmotionAnalyzer` + `FfmpegPcmDecoder` inference support, persisted playback state |
| Core | `:core:model` / `:core:common` / `:core:ui` / `:core:skin` | Pure models incl. `AudioQualityTier` + `LocalMusicStorageStats`, `WordPreference`, `RandomEnhanceMode`, emotion coordinates / `AppLog` + `PerformanceTrace` / shared Compose components (`SongEmotionSection`, `SongInfoDialog`) / skin description model, parser and fail-closed validation |

Layering detail and Hilt binding points: [/openwiki/architecture/module-graph.md](/openwiki/architecture/module-graph.md).

**Offline and privacy invariants**: the app manifest declares `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `BLUETOOTH_CONNECT` and `READ_MEDIA_AUDIO` — there is **no `INTERNET` permission**. `READ_MEDIA_AUDIO` only accelerates the import scan (direct `java.io.File` traversal once granted); SAF document-tree access stays the primary and fallback path, and `READ_EXTERNAL_STORAGE` is banned by the gate. Notification/Bluetooth/`READ_MEDIA_AUDIO` requests must never appear on the startup path (only user-triggered Settings paths may ask). Backup/data-extraction rules must keep excluding playback state, Room files, DataStore files and the album-art cache. `MelodyApplication.onCreate` installs `AppLog` (with `performanceLoggingEnabled = true` plus a small `PerformanceTrace.allow(...)` whitelist for release), installs the uncaught-exception handler, and schedules emotion scanning; its Coil loader caps memory at 20% and disk at 64 MB and defers image-stack init to first load.

## Common commands

All commands run from the repository root; use `.\gradlew.bat` on Windows and `./gradlew` on macOS/Linux.

```bash
# Fast single-module compile (quickest feedback after an edit)
./gradlew :data:compileDebugKotlin

# Full JVM unit-test run (no device needed)
./gradlew test
# Single module / single test class
./gradlew :core:model:test --tests "cn.com.dcsgo.mihx.core.model.AudioQualityTierTest"

# Need a device/emulator
./gradlew :app:connectedAndroidTest      # Compose instrumentation tests
./gradlew :benchmark:connectedCheck      # Macrobenchmark (cold start / scrolling frame rate)

# Format and architecture gates
./gradlew spotlessCheck                  # or spotlessApply to auto-fix
./gradlew verifyProductArchitecture
./gradlew :buildSrc:test                 # unit tests for the gate's rule body

# Pre-commit full gate: root spotlessCheck + verifyProductArchitecture + check/test across all 15 subprojects
./gradlew check
```

**PR/push CI** (`.github/workflows/pr-check.yml`, ubuntu-24.04, JDK 17) runs `./gradlew check :app:assembleDebugAndroidTest :buildSrc:test --no-daemon --stacktrace` — the instrumentation sources are compiled without a device so UI signature drift is still caught. Markdown/`docs/`/`openwiki/`-only changes do not trigger it, and a **version-only push** (a diff touching nothing but `versionCode`/`versionName` in `app/build.gradle.kts`) skips the heavy check; the detection is deliberately conservative (any other file, any extra changed line, a new branch, a force push or a non-push event falls back to the full run).

### `verifyProductArchitecture` gate at a glance

A custom validation task registered in the root `build.gradle.kts` `verification` group and wired into the root `check` lifecycle. Since m-10 (2026-10-15) the rule body lives in `buildSrc` as pure logic (`ProductArchitectureVerification.verify(rootDir)` returning **all** violations at once, unit-tested by `ProductArchitectureVerificationTest`); the task only collects files and raises a `GradleException` naming every violated rule. Main rule families:

- **Module boundaries**: features must not depend on `:data`/`:player`/other features; implementation-layer imports (`cn.com.dcsgo.mihx.data.repository/local`) must not leak into feature/domain/player; `MusicRepository` must stay an internal storage coordinator bound through narrow adapters (`SongRepositoryAdapter`, `PlaylistRepositoryAdapter`, `MusicImportRepositoryAdapter`, `AlbumArtRepositoryAdapter`).
- **Module graph completeness**: all 15 product modules listed in `settings.gradle.kts` must exist, each with a local `.gitignore` covering generated Android/Gradle output.
- **Hilt wiring**: the entry points (`MelodyApplication` `@HiltAndroidApp`, `MainActivity`/`AppMediaSessionService` `@AndroidEntryPoint`, `PlayerViewModel` `@HiltViewModel`) and DI modules (app-side `CoroutineModule`/`LoggerModule`/`PlayerModule`, data-side `DatabaseModule`/`RepositoryModule`/`DataStoreModule`) must exist; the retired `PlayerViewModelComponents`/`PlayerViewModelComponentFactory` hub must not reappear.
- **Room schema immutability**: exported JSON under `data/schemas/` is frozen history (currently `1.json`–`20.json`); v1/v2/v3 content assertions lock the earliest migration semantics.
- **UI and code conventions**: `LazyColumn` `items` must carry a stable `key`; direct `android.util.Log` calls are banned (use `AppLog`); `:core:model` must stay a pure data model without Android resources or UI resource ids; Spotless must stay in the version catalog and applied by the root build.
- **Release and privacy**: release must have `isMinifyEnabled` + `isShrinkResources`; `:app` must not declare `AppMediaSessionService`; backup rules must contain the agreed privacy exclusions; startup-path permission requests fail; `READ_EXTERNAL_STORAGE` must never be declared; no Android Things references.
- **Performance anchors**: `MusicRepository` import/scan and `PlaybackController` hot paths must keep `PerformanceTrace` operation anchors; the `:benchmark` module shape and `StartupBenchmark` terminology are checked.

Release signing is decided *outside* the `buildTypes` block (the gate regex truncates at the first closing brace): debug falls back to debug signing with a warning, while `-PrequireReleaseSigning=true` fails the build when `keystore.properties` is missing. Full rule list: [/openwiki/operations/build-and-verification.md](/openwiki/operations/build-and-verification.md).

### Build variants

| Variant | Highlights |
| --- | --- |
| `debug` | No minification; `applicationIdSuffix=".debug"` and `versionNameSuffix="-debug"` so it can coexist with a release install |
| `release` | R8 minify + resource shrink; only `arm64-v8a`; signing config from `keystore.properties` (see above) |
| `benchmark` | inherits release settings but uses debug signing, non-debuggable, adds `x86_64` for emulators |

### Room schema change flow (common pitfall)

Change an entity → bump `MelodyDatabase.VERSION` → build to export the new schema JSON to `data/schemas/` → write `Migration(N, N+1)` and register it in `MelodyMigrations.ALL` → add a narrow unit test for complex migrations (`MelodyDatabaseMigrationTest` runs `runMigrationsAndValidate` against the exported snapshot). **Never modify already-exported historical schema JSON.** When a migration only changes data (like v19→v20), it still deserves its own version: folding it into an unreleased migration means devices already on the intermediate version never execute it.

## Task → page-to-read routing table

| What you are changing | Read first | Companion narrow verification |
| --- | --- | --- |
| Audio quality tiers (标准 / 较高 / 极高 / 无损 / 高解析), spatial audio, bit depth / bitrate / channels / codec fields | [/openwiki/concepts/audio-spec-and-quality-tiers.md](/openwiki/concepts/audio-spec-and-quality-tiers.md), [/openwiki/architecture/data-persistence.md](/openwiki/architecture/data-persistence.md) | `./gradlew :core:model:test --tests "cn.com.dcsgo.mihx.core.model.AudioQualityTierTest"` |
| 存储与音质统计 (library byte total, per-tier shares, spatial count, unknown sizes) | [/openwiki/workflows/storage-and-quality-stats.md](/openwiki/workflows/storage-and-quality-stats.md), [/openwiki/concepts/audio-spec-and-quality-tiers.md](/openwiki/concepts/audio-spec-and-quality-tiers.md) | `./gradlew :core:model:test --tests "cn.com.dcsgo.mihx.core.model.LocalMusicStorageStatsTest"` |
| Local-music sorting (incl. `BIT_RATE`) and 多版本管理 / version comparison | [/openwiki/workflows/library-sorting-and-version-management.md](/openwiki/workflows/library-sorting-and-version-management.md), [/openwiki/architecture/data-persistence.md](/openwiki/architecture/data-persistence.md) | `./gradlew :feature:playlist:test --tests "cn.com.dcsgo.mihx.feature.playlist.SongSorterTest"`, `./gradlew :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.SongVersion*"` |
| File check (快速 / 深度校验) and metadata backfill | [/openwiki/concepts/audio-spec-and-quality-tiers.md](/openwiki/concepts/audio-spec-and-quality-tiers.md), [/openwiki/workflows/library-sorting-and-version-management.md](/openwiki/workflows/library-sorting-and-version-management.md) | `./gradlew :data:test --tests "cn.com.dcsgo.mihx.data.repository.MusicRepository*"` |
| Mood time-slot anything-play incl. **excluded** emotion tags / pool statistics | [/openwiki/concepts/mood-time-slot.md](/openwiki/concepts/mood-time-slot.md), [/openwiki/player/random-and-infinite.md](/openwiki/player/random-and-infinite.md) | `./gradlew :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.MoodSlotResolverTest" --tests "cn.com.dcsgo.mihx.domain.playback.MoodPoolStatsTest"`, `./gradlew :feature:player:test --tests "cn.com.dcsgo.mihx.feature.player.PlayerRandomQueueFacadeTest"` |
| Explore play (探索播放) / word-preference voting / random & infinite modes | [/openwiki/player/random-and-infinite.md](/openwiki/player/random-and-infinite.md), [/openwiki/concepts/emotion-model.md](/openwiki/concepts/emotion-model.md) | `./gradlew :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicyTest"`, `./gradlew :data:test --tests "cn.com.dcsgo.mihx.data.repository.WordPreferencesRepositoryTest"` |
| Queue / windowed sync / `PlayQueue` invariants | [/openwiki/player/queue-architecture.md](/openwiki/player/queue-architecture.md) | `./gradlew :player:test :feature:player:test` |
| `PlayerRuntime` facade graph / caches / learning feedback | [/openwiki/player/runtime-facades.md](/openwiki/player/runtime-facades.md), [/openwiki/player/state-machine.md](/openwiki/player/state-machine.md) | `./gradlew :feature:player:test` |
| Playback state machine / process restore / service and Bluetooth | [/openwiki/player/state-machine.md](/openwiki/player/state-machine.md), [/openwiki/player/service-and-controller.md](/openwiki/player/service-and-controller.md) | `./gradlew :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizerTest"` |
| Emotion analysis, tag ratios, playback-page emotion pills | [/openwiki/concepts/emotion-model.md](/openwiki/concepts/emotion-model.md), [/openwiki/workflows/emotion-analysis-pipeline.md](/openwiki/workflows/emotion-analysis-pipeline.md) | `./gradlew :core:model:test --tests "cn.com.dcsgo.mihx.core.model.*Emotion*"`, `./gradlew :core:ui:test` |
| Style switching / multi-theme-color update (skin shell, `ThemeVariant`, tab/menu layout) | [/openwiki/architecture/app-shell-navigation.md](/openwiki/architecture/app-shell-navigation.md), [/openwiki/architecture/module-graph.md](/openwiki/architecture/module-graph.md) | `./gradlew :core:skin:test :app:test --tests "cn.com.dcsgo.mihx.app.shell.*"` |
| Room / DataStore / migration / backup exclusions | [/openwiki/architecture/data-persistence.md](/openwiki/architecture/data-persistence.md), [/openwiki/operations/build-and-verification.md](/openwiki/operations/build-and-verification.md) | `./gradlew :data:test` |
| Navigation / app shell / permissions / theme | [/openwiki/architecture/app-shell-navigation.md](/openwiki/architecture/app-shell-navigation.md) | `./gradlew :app:connectedAndroidTest` |
| Add a module / change dependency boundaries / touch Hilt wiring | [/openwiki/architecture/module-graph.md](/openwiki/architecture/module-graph.md) | `./gradlew verifyProductArchitecture :buildSrc:test` |
| Full playback session (start → track change → settle → restore) | [/openwiki/workflows/playback-session-lifecycle.md](/openwiki/workflows/playback-session-lifecycle.md) | player-related tests |
| Pre-commit / pre-release overview | [/openwiki/operations/build-and-verification.md](/openwiki/operations/build-and-verification.md), [/openwiki/testing/test-map.md](/openwiki/testing/test-map.md) | `./gradlew check` |

### Hard constraints per task area (know before editing)

- **Player**: `PlayQueue.songs` is the business queue that may contain duplicates; `MediaController` only holds the current ± window (≤71 items after planning: 20 before + current + 50 after); `MediaItem.mediaId` must equal `Song.id.toString()`; `PlayerViewModel` is a thin `@HiltViewModel` facade — real logic lives in the `PlayerRuntime`-composed facade/graph layer, so do not stuff logic into the ViewModel and never reintroduce the `PlayerViewModelComponents` hub.
- **Emotion feature**: batch scans are driven by a WorkManager periodic task (`emotion_periodic_scan`) scheduled from `MelodyApplication.onCreate` — every 6 h, initial delay 30 min, charging + battery-not-low, `NetworkType.NOT_REQUIRED`; manual triggers (`emotion_manual_scan`) analyze up to 300 songs per batch and re-enqueue with `APPEND` unless paused, stopped, or the whole batch failed. `EmotionAnalyzer` decodes to 16 kHz mono PCM (`FfmpegPcmDecoder`, memory-budgeted against `maxMemory()`) and runs a fixed YAMNet embedding + a per-window V/A head in `:player` assets to produce per-window curves, a whole-song mean and a `vocalScore`; results go to Room `song_emotions`; failures go to a DataStore JSON store and songs accumulating `MAX_ATTEMPTS` (=3) failures stop being auto-retried.
- **Explore play / word preference**: `RandomEnhanceMode` is `OFF` / `TIME_SLOT` / `EXPLORE` — `TIME_SLOT` and `EXPLORE` are mutually exclusive. `EXPLORE` generates a 10-song batch, disables infinite random, increments ratio-weighted like/dislike votes per emotion word when a song settles (full listen ≥30 s **and** ≥1/4 of duration → LIKE; quick skip → DISLIKE), and refills 5 songs when the just-ended song was second-to-last (`REFILL_THRESHOLD = 0`); refill pools tier by liked-and-no-dislike → only-no-dislike → full library, and the demotion test is whether a pool can fill `REFILL_COUNT` songs, not whether it is non-empty.
- **Audio spec model**: the raw specs (`sampleRate`, `bitDepth`, `bitRateKbps`, `channelCount`, `audioCodec`, `spatialAudio`) are persisted on `songs`; the tier is **derived on read** by `AudioQualityTier.classify` and never stored, so changing a threshold relabels the whole library with no migration. Lossy tiers read bitrate only (192 / 320 kbps steps), lossless reads bit depth + sample rate, DSD goes straight to 高解析, spatial audio requires `audio/eac3-joc` or multichannel (>2) `audio/ac4`, and `UNKNOWN` renders as empty string instead of a guess. Rows written before v18/v19 have `0`/`null` sentinels until a **深度校验** (`FileCheckMode.DEEP` → `refreshMetadata`) backfills them.
- **Data layer / sorting**: Room (`melody.db`, 14 tables, immutable snapshots 1–20) + five DataStore files; v1-era SharedPreferences JSON is migrated into Room on first launch via `SharedPreferencesLegacyJsonMigration`. Sorting is persisted orthogonally as a `SongSortMode` plus an ascending flag in `player_settings`; the eight modes are `IMPORT_ORDER`, `TITLE`, `ARTIST`, `ALBUM`, `DURATION`, **`BIT_RATE`**, `PLAY_COUNT`, `LAST_PLAYED`. `BIT_RATE` replaced the old `SAMPLE_RATE` mode: legacy persisted values are normalized to `BIT_RATE` on read in `PlayerSettingsRepository`, so **no data migration is needed** (the comparison itself uses `Song.bitRateKbps`). Version grouping uses `titleOverride ?: (title + artist + album)`; v19→v20 wiped `song_group_overrides` because old manual keys no longer map to the new group-key semantics.

## Release history relevant to this page

- **v3.13 → v3.15 deltas.** Audio-spec classification (5 quality tiers + `UNKNOWN`), spatial-audio recognition (E-AC-3 JOC / multichannel AC-4, recognition only — no Atmos rendering promise), bit-depth / bitrate / channel / codec capture at import, and a `深度校验` backfill path that re-reads and writes those columns; a read-only **存储与音质统计** page under `本地音乐 → 文件管理`; the local-music sort mode switching from sample rate to **比特率**; time slots gaining **excluded** emotion tags; and read-only emotion tag pills (with ratios) on the playback page.
- **Schema growth.** v17→v18 adds `songs.bitDepth`; v18→v19 adds `songs.bitRateKbps`, `channelCount`, `audioCodec`, `spatialAudio`; v19→v20 deletes all `song_group_overrides` rows (group-key semantics changed from `titleOverride ?: title` to `titleOverride ?: 歌名 + 歌手 + 专辑`; old "attach to group" / "detach" values cannot be remapped, so users re-link once after upgrade). Earlier in the same range: `MIGRATION_16_17` added `index_songs_albumId` and `index_playback_events_songId`; `MIGRATION_15_16` rescaled legacy word votes ×100 for ratio-weighted learning.
- **Emotion coordinate system is still `yamnet-va-v5-vocal`** (`EmotionModelSpec.MODEL_VERSION = "yamnet-va-v5-vocal@edbf6fe5"`, sourced from `core/model/emotion_model_spec.properties`). Rows whose stored `modelVersion` mismatches — or that lack `vocalScore` — are auto-repicked and rescanned, with a 3-failure ceiling per song.

## All wiki pages

- Entry and operations: this page, [build / verify / release operations](/openwiki/operations/build-and-verification.md), [test map](/openwiki/testing/test-map.md)
- Architecture: [module layering and dependency boundaries](/openwiki/architecture/module-graph.md), [app shell, navigation and cross-module wiring](/openwiki/architecture/app-shell-navigation.md), [data persistence: Room v20 and DataStore](/openwiki/architecture/data-persistence.md)
- Player: [PlayerRuntime and the facade list](/openwiki/player/runtime-facades.md), [queue and windowed sync](/openwiki/player/queue-architecture.md), [random & infinite play (incl. explore play)](/openwiki/player/random-and-infinite.md), [playback state machine and restore](/openwiki/player/state-machine.md), [playback service and MediaController](/openwiki/player/service-and-controller.md)
- Domain concepts: [emotion domain model, tags and ratio projections](/openwiki/concepts/emotion-model.md), [mood time slots, excluded tags and pool policy](/openwiki/concepts/mood-time-slot.md), [audio spec model: quality tiers and spatial audio](/openwiki/concepts/audio-spec-and-quality-tiers.md)
- End-to-end workflows: [emotion analysis pipeline](/openwiki/workflows/emotion-analysis-pipeline.md), [playback session lifecycle](/openwiki/workflows/playback-session-lifecycle.md), [library sorting and version management](/openwiki/workflows/library-sorting-and-version-management.md), [storage and quality statistics](/openwiki/workflows/storage-and-quality-stats.md)
