---
type: "Quickstart"
title: "快速上手"
description: "Onboarding entry: project identity and current version (v3.12.2 / versionCode 50, MelodyDatabase v17, emotion model yamnet-va-v5-vocal), module layering, build/test/verify commands, and a refreshed task-routing map into every wiki page."
tags: [quickstart, onboarding, android, gradle, multi-module, offline-first, skin, architecture-gate, word-preference, explore-play, emotion-model]
sources:
  - id: openwiki-source-3bfcb28142050978edf94754
    resource: repo://app/build.gradle.kts
  - id: openwiki-source-3e7209a00de8aa3c66577a46
    resource: repo://app/src/androidTest/java/cn/com/dcsgo/mihx/HomeScreenComposeTest.kt
  - id: openwiki-source-186e96b8d6739f3745947903
    resource: repo://app/src/main/AndroidManifest.xml
  - id: openwiki-source-4ca5f9c29b016f072d8ed57c
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt
  - id: openwiki-source-bb00a8b0f0083ccce829d880
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanScheduler.kt
  - id: openwiki-source-81bdf5bdc16c1ff3afd5bcc8
    resource: repo://app/src/main/java/cn/com/dcsgo/mihx/app/emotion/EmotionScanWorker.kt
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
  - id: openwiki-source-9645009bf4cd7f02b15b26de
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/PlayQueue.kt
  - id: openwiki-source-b8ad601bbc2ce9deae0cf5a6
    resource: repo://core/model/src/main/java/cn/com/dcsgo/mihx/core/model/RandomEnhanceMode.kt
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
  - id: openwiki-source-12a1d6d0e5553c5cb1517529
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/entity/WordPreferenceEntity.kt
  - id: openwiki-source-577b7687e8ff177b6a586197
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/MelodyDatabase.kt
  - id: openwiki-source-51949c79e3bf4e421e7ff171
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/MelodyMigrations.kt
  - id: openwiki-source-c75fdeafeee7cf621f200c0f
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/local/migration/SharedPreferencesLegacyJsonMigration.kt
  - id: openwiki-source-12d5b8af1aa446b9573e2462
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/EmotionFailureStore.kt
  - id: openwiki-source-99854c1cb5a148365aba8ef6
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlayerSettingsDataStore.kt
  - id: openwiki-source-bccc40bc4f4db30340cd2659
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/PlaylistResumeDataStore.kt
  - id: openwiki-source-20d823efa2e1a5f403b02d3e
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongEmotionsRepository.kt
  - id: openwiki-source-f8d95aa838cf04a8eb5e5e43
    resource: repo://data/src/main/java/cn/com/dcsgo/mihx/data/repository/TimeSlotConfigStore.kt
  - id: openwiki-source-39979cc6e8c7e5b52ecabd5f
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/ExplorePlaybackPolicy.kt
  - id: openwiki-source-ee8d5713acafafdce234f04d
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/MoodSlotResolver.kt
  - id: openwiki-source-15370df42a01365d6f089c6e
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/repository/WordPreferenceRepository.kt
  - id: openwiki-source-0a840abe5bf9183316c47b4e
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerMediaEventFacade.kt
  - id: openwiki-source-8e3d8a1f18f53d37da8744f1
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRandomQueueFacade.kt
  - id: openwiki-source-ff40146bae9715f1a3860309
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt
  - id: openwiki-source-fe80d1b3a38b98c97840e74d
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerViewModel.kt
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
  - id: openwiki-source-e620d7484b72a53c7fa812cd
    resource: repo://settings.gradle.kts
generated: { by: "openwiki/0.5.0", at: "2026-10-27T08:49:20.803Z" }
verified:
  - by: openwiki/0.5.0
    at: 2026-10-27T08:49:20.803Z
---

# Quick Start

This repository is **melody-in-heart** (applicationId `cn.com.dcsgo.mihx`): a fully local, offline-first, ad-free, network-free Android music player. Current version is `3.12.2` (versionCode 50), `minSdk 33` / `targetSdk 36`, Java 11 bytecode. The stack is Kotlin 2.0.21 + Jetpack Compose (BOM 2025.09.01) + Media3 1.9.0 (with the Jellyfin FFmpeg decoder extension) + Hilt 2.52 + Room 2.6.1 (`MelodyDatabase` **v17**, **14 entity tables**) + DataStore 1.1.1 + WorkManager 2.9.1 + LiteRT 1.4.1 / TFLite (via the `org.tensorflow.lite.Interpreter` API for on-device emotion inference). The project is split into **15 Gradle modules** (`:app`, five `:core:*`, `:domain`, `:data`, `:player`, six `:feature:*`, `:benchmark`), and module boundaries are enforced by the custom `verifyProductArchitecture` gate. Before editing any code read the routing table on this page to find the matching topic page; run `./gradlew check` before committing.

## What to read, what to trust

- **Source and tests are the only authority.** Every wiki conclusion carries `repo://` evidence, but implementation keeps evolving — when you act, trust the current code above any doc.
- `CLAUDE.md` and `docs/` are high-value historical references (build conventions, queue/state-machine design docs), but **they may lag the code** and must be cross-checked. `CLAUDE.md` mostly tracks current versions but its Room status comments historically drifted — verify against `MelodyDatabase` (now **v17 / 14 entities**) and the exported snapshots under `data/schemas/` (`1.json..17.json`). Its version table otherwise matches the catalog (Compose BOM `2025.09.01`, Kotlin `2.0.21` with the built-in `org.jetbrains.kotlin.plugin.compose` compiler — never the old `composeOptions { kotlinCompilerExtensionVersion }` block).
- The repository root `AGENTS.md` convention: OpenWiki pages are refreshed by scheduled workflows; do not hand-edit pages under `openwiki/` unless explicitly asked.

## Module overview

```mermaid
flowchart TD
    APP[":app shell - navigation, Hilt assembly"] --> FEAT[":feature:* six UI modules"]
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

*Legal dependency direction: only `:app` may see every module; dependencies between features, or from a feature to `:data`/`:player`, are forbidden by `verifyProductArchitecture`. As of v3.11.0, `:player` implementation classes were moved from the legacy `cn.com.dcsgo.mihx.data.player` package into `cn.com.dcsgo.mihx.player` (matching the module namespace); the gate now fails on any residual reference to the old package.*

| Layer | Module | One-line responsibility |
| --- | --- | --- |
| App shell | `:app` | `MelodyApplication` (Hilt `@HiltAndroidApp` + Coil `ImageLoaderFactory`) / `MainActivity` / `AppRoot` / `AppNavHost`, route table `AppRoutes`, bottom-bar shell, permission coordinator, emotion-scan worker, skin-shell resolver, Hilt assembly |
| Feature | `:feature:home/playlist/user/lyrics/player/settings` | Each feature exposes `XxxRoute` + `XxxScreen` (the Route/Screen pattern is enforced by the architecture task); `:feature:player` additionally hosts the `PlayerRuntime` facade composition root |
| Domain | `:domain` | Repository interfaces + pure playback policy (planner/synchronizer/coordinator, incl. `MoodSlotResolver`, `ExplorePlaybackPolicy`), no Android dependencies |
| Data | `:data` | Room `MelodyDatabase` (v17, 14 entity tables incl. `word_preferences` + `song_emotions`) + migrations, DataStore wrappers, narrow adapters binding domain interfaces |
| Player | `:player` | `AppMediaSessionService` (ExoPlayer + MediaSession), `PlaybackController` (MediaController client), windowed queue planning, Bluetooth coordination, `EmotionAnalyzer` + `FfmpegPcmDecoder` inference support |
| Core | `:core:model` / `:core:common` / `:core:ui` / `:core:skin` | Pure data types (`WordPreference`, `RandomEnhanceMode`, emotion model coordinates) / `AppLog` + `PerformanceTrace` / theme and shared Compose components / skin description model, parser and fail-closed validation |

Layering detail and Hilt binding points: [/openwiki/architecture/module-graph.md](/openwiki/architecture/module-graph.md).

**Offline and privacy invariants**: the app's own manifest declares `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `BLUETOOTH_CONNECT` — there is **no `INTERNET` permission**. `READ_MEDIA_AUDIO` is accepted solely to accelerate the import scan (a fast direct `java.io.File` traversal when already granted), but SAF document-tree access remains the primary and fallback path and `READ_EXTERNAL_STORAGE` is still banned by the architecture task; a denied `READ_MEDIA_AUDIO` silently falls back to SAF. Backup/data-extraction rules must keep excluding playback state, Room files, DataStore files and the album-art cache. Notification and Bluetooth permissions may only be requested from user-triggered Settings paths, never on the startup path (startup-path requests fail the gate). `MelodyApplication` additionally installs the Coil `ImageLoaderFactory` and configures a bounded memory (20% max) / 64 MB disk image cache deferred until first image load (shortens the cold-start path).

## Common commands

All commands run from the repository root; use `.\gradlew.bat` on Windows and `./gradlew` on macOS/Linux.

```bash
# Fast single-module compile (quickest feedback after an edit)
./gradlew :data:compileDebugKotlin

# Full JVM unit-test run (no device needed)
./gradlew test
# Single module / single test class
./gradlew :player:test --tests "cn.com.dcsgo.mihx.player.window.ControllerQueuePlannerTest"

# Need a device/emulator
./gradlew :app:connectedAndroidTest      # Compose instrumentation tests
./gradlew :benchmark:connectedCheck      # Macrobenchmark (cold start / scrolling frame rate)

# Format and architecture gates
./gradlew spotlessCheck                  # or spotlessApply to auto-fix
./gradlew verifyProductArchitecture

# Pre-commit full gate: root spotlessCheck + verifyProductArchitecture + check/test across all 15 subprojects
./gradlew check
```

### `verifyProductArchitecture` gate at a glance

A custom validation task wired into the root `check` lifecycle. Since m-10 (2026-10-15) the rule body lives in `buildSrc` (`ProductArchitectureVerification`) as pure, unit-tested logic over the file tree; the root task only collects files and raises, collecting **all** violations at once instead of failing on the first. Main rule families:

- **Module boundaries**: features must not depend on `:data`/`:player`/other features; implementation-layer imports (`cn.com.dcsgo.mihx.data.repository/local`) must not leak into feature/domain/player; `MusicRepository` must stay an internal storage coordinator bound through narrow adapters (`SongRepositoryAdapter`, `PlaylistRepositoryAdapter`, `MusicImportRepositoryAdapter`, `AlbumArtRepositoryAdapter`).
- **Module graph completeness**: all 15 product modules listed in `settings.gradle.kts` (`:app`, `:core:model/common/ui/skin`, `:domain`, `:data`, `:player`, the six `:feature:*`, `:benchmark`) must exist, each with a local `.gitignore` covering generated Android/Gradle output.
- **Hilt wiring**: the entry points (`MelodyApplication` `@HiltAndroidApp`, `MainActivity`/`AppMediaSessionService` `@AndroidEntryPoint`, `PlayerViewModel` `@HiltViewModel`) and DI modules (app-side `CoroutineModule`/`LoggerModule`/`PlayerModule`, data-side `DatabaseModule`/`RepositoryModule`/`DataStoreModule`) must exist; the old `PlayerViewModelComponents`/`PlayerViewModelComponentFactory` assembly hub must not reappear.
- **Room schema immutability**: historical JSON under `data/schemas/` (1–17.json now) must not be modified; v1/v2/v3 content assertions lock the migration semantics.
- **UI and code conventions**: `LazyColumn` `items` must carry a stable `key`; direct `android.util.Log` calls are banned (must use `AppLog`); `:core:model` must stay a pure data model without Android resources or UI resource ids.
- **Release and privacy**: release must have `isMinifyEnabled` + `isShrinkResources`; the `:app` manifest must not declare `AppMediaSessionService`; backup rules must contain the agreed privacy exclusions; requests for notification/Bluetooth/`READ_MEDIA_AUDIO` permissions must not appear on the startup path (startup-path requests fail); `READ_EXTERNAL_STORAGE` must never be declared; `com.google.android.things` dependencies/references must not exist.
- **Performance anchors**: `MusicRepository` import/scan and `PlaybackController` hot paths must keep `PerformanceTrace` operation anchors; the `:benchmark` module shape and `StartupBenchmark` terminology are checked.

On failure a `GradleException` report names each violated rule. Full rule list and release constraints: [/openwiki/operations/build-and-verification.md](/openwiki/operations/build-and-verification.md).

### Build variants

| Variant | Highlights |
| --- | --- |
| `debug` | No minification; `applicationIdSuffix=".debug"` so it can coexist with a release install |
| `release` | R8 minify + resource shrink; only `arm64-v8a`; falls back to debug signing when `keystore.properties` is missing, and passes `-PrequireReleaseSigning=true` to fail the build instead (prevents accidentally shipping a debug-signed package) |
| `benchmark` | inherits release settings but uses debug signing, non-debuggable, adds `x86_64` for emulators |

### Room schema change flow (common pitfall)

Change an entity → bump `MelodyDatabase.VERSION` → build to export the new schema JSON to `data/schemas/` → write `Migration(N, N+1)` and register it in `MelodyMigrations.ALL` → add a narrow unit test for complex migrations. **Never modify already-exported historical schema JSON.**

## Task → page-to-read routing table

| What you are changing | Read first | Companion narrow verification |
| --- | --- | --- |
| Explore play (探索播放) / word-preference voting / random & infinite modes | [/openwiki/player/random-and-infinite.md](/openwiki/player/random-and-infinite.md), [/openwiki/concepts/emotion-model.md](/openwiki/concepts/emotion-model.md) | `./gradlew :feature:player:test :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicyTest"`, `./gradlew :data:test --tests "cn.com.dcsgo.mihx.data.repository.WordPreferencesRepositoryTest"` |
| Queue / windowed sync / `PlayQueue` invariants | [/openwiki/player/queue-architecture.md](/openwiki/player/queue-architecture.md) | `./gradlew :player:test :feature:player:test` |
| `PlayerRuntime` facade graph / learning feedback | [/openwiki/player/runtime-facades.md](/openwiki/player/runtime-facades.md), [/openwiki/player/state-machine.md](/openwiki/player/state-machine.md) | `./gradlew :feature:player:test` |
| Playback state machine / process restore / service and Bluetooth | [/openwiki/player/state-machine.md](/openwiki/player/state-machine.md), [/openwiki/player/service-and-controller.md](/openwiki/player/service-and-controller.md) | `./gradlew :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.ControllerPlaybackStateSynchronizerTest"` |
| Emotion analysis or mood time-slot anything-play | [/openwiki/concepts/emotion-model.md](/openwiki/concepts/emotion-model.md), [/openwiki/workflows/emotion-analysis-pipeline.md](/openwiki/workflows/emotion-analysis-pipeline.md) | `./gradlew :domain:test --tests "cn.com.dcsgo.mihx.domain.playback.MoodSlotResolverTest"` plus `./gradlew :core:ui:test` |
| Style switching / multi-theme-color update (skin shell, `ThemeVariant`, tab/menu layout) | [/openwiki/architecture/app-shell-navigation.md](/openwiki/architecture/app-shell-navigation.md), [/openwiki/architecture/module-graph.md](/openwiki/architecture/module-graph.md) | `./gradlew :core:skin:test :app:test --tests "cn.com.dcsgo.mihx.app.shell.*"` |
| Sorting the local-music list / screen orientation / duplicate-song cleanup | [/openwiki/architecture/data-persistence.md](/openwiki/architecture/data-persistence.md) | `./gradlew :data:test :domain:test` |
| Room / DataStore / migration / backup exclusions | [/openwiki/architecture/data-persistence.md](/openwiki/architecture/data-persistence.md), [/openwiki/operations/build-and-verification.md](/openwiki/operations/build-and-verification.md) | `./gradlew :data:test` |
| Navigation / app shell / permissions / theme | [/openwiki/architecture/app-shell-navigation.md](/openwiki/architecture/app-shell-navigation.md) | `./gradlew :app:connectedAndroidTest` |
| Add a module / change dependency boundaries / touch Hilt wiring | [/openwiki/architecture/module-graph.md](/openwiki/architecture/module-graph.md) | `./gradlew verifyProductArchitecture` |
| Full playback session (start → track change → settle → restore) | [/openwiki/workflows/playback-session-lifecycle.md](/openwiki/workflows/playback-session-lifecycle.md) | player-related tests |
| Pre-commit / pre-release overview | [/openwiki/operations/build-and-verification.md](/openwiki/operations/build-and-verification.md), [/openwiki/testing/test-map.md](/openwiki/testing/test-map.md) | `./gradlew check` |

### Three hard constraints per task area (know before editing)

- **Player**: `PlayQueue.songs` is the business queue that may contain duplicates; `MediaController` only holds the current ± window (≤71 items after planning: 20 before + current + 50 after); `MediaItem.mediaId` must equal `Song.id.toString()`; `PlayerViewModel` is a thin `@HiltViewModel` facade — real logic lives in the `PlayerRuntime`-composed facade/graph layer, so do not stuff logic into the ViewModel and never reintroduce the `PlayerViewModelComponents` hub.
- **Emotion feature**: batch scans are driven by a WorkManager periodic task scheduled from `MelodyApplication.onCreate` (while charging with sufficient battery, ~6h cycles; manual triggers auto-requeue); `EmotionAnalyzer` decodes to 16 kHz mono PCM (`FfmpegPcmDecoder`, memory-budgeted against `maxMemory()`, matching the `largeHeap` app) and runs a fixed YAMNet embedding (521 AudioSet classes) + a per-window V/A head in `:player` assets to produce per-window V/A curves, whole-song mean, and a `vocalScore`; results go to Room `song_emotions`; failures go to DataStore JSON and songs accumulating `MAX_ATTEMPTS` (=3) failures stop being auto-retried. Mood time-slot anything-play is decided by `MoodSlotResolver` over half-open `[start, end)` intervals (`end <= start` wraps midnight); tag filtering happens at the planner entry, and an empty pool falls back to full-library random.
- **Explore play / word preference (v3.11.0+)**: `RandomEnhanceMode` is `OFF` / `TIME_SLOT` / `EXPLORE` — `TIME_SLOT` and `EXPLORE` are mutually exclusive; `EXPLORE` generates a 10-song batch, disables infinite random, increments like/dislike votes per emotion word when a song settles (full listen ≥30s **and** ≥1/4 of duration → LIKE; quick skip → DISLIKE) via `WordPreferenceRepository.recordSongOutcome`, and auto-refills 5 songs when the just-ended song was second-to-last (queue remaining `== REFILL_THRESHOLD` = 0) via `refillExplorePlayQueue`. Since v3.12 votes are **ratio-weighted**: vote weight = per-word share × 100 (`ExplorePlaybackPolicy.VOTE_WEIGHT_SCALE`), so a skipped track knocks its dominant emotion words harder while incidental words lose almost nothing. Refill pools tier by liked-and-no-dislike → only-no-dislike → full library; the demotion test is whether the pool can fill `REFILL_COUNT` songs, not whether it is non-empty. (Constants `INITIAL_BATCH_SIZE=10`, `REFILL_COUNT=5`, `REFILL_THRESHOLD=0`, `MIN_LISTEN_MS=30_000`, `MIN_LISTEN_RATIO=0.25`, `VOTE_MIN_RATIO`, `VOTE_MAX_TAGS` in `ExplorePlaybackPolicy`.)
- **Data layer**: Room (`melody.db`, 14 tables, immutable schema snapshots 1–17) + five DataStore preference files (`player_settings`, `playback_state`, `playlist_resume`, `mood_time_slot`, `emotion_failures`); v1-era SharedPreferences JSON is migrated into Room on first launch via `SharedPreferencesLegacyJsonMigration`. Sorting is persisted orthogonally as a `SongSortMode` (IMPORT_ORDER / TITLE / ARTIST / ALBUM / DURATION / SAMPLE_RATE / PLAY_COUNT / LAST_PLAYED) plus an ascending flag in `player_settings`. Screen orientation (SENSOR_AUTO / LANDSCAPE / PORTRAIT) is applied to the Activity via `requestedOrientation` from `AppRoot`.

## Recent evolution on this page's core claims

- **Model coordinate system is now `yamnet-va-v5-vocal`.** `EmotionModelSpec.MODEL_VERSION = "yamnet-va-v5-vocal@edbf6fe5"` (label `yamnet-va-v5-vocal`, params fingerprint `b8a45a93`, fingerprint `edbf6fe5`). Semantics: arousal is a **real RMS loudness envelope** mapped through a `[-30, -5]` dB floor/ceiling window; valence is **calibrated** by bias/scale (`V_CAL_BIAS=0.25`, `V_CAL_SCALE=0.85`); **vocal-compensated anchors** shift instrument-dominant tracks (valence ≤ `VOCAL_COMP_THRESHOLD=0.010`) up by `VOCAL_COMP_SHIFT=0.091` at read time. If the model assets change, the fingerprint in the version string changes and the periodic/scan worker **auto-repicks and rescans** rows whose stored `modelVersion` mismatches, or that are missing a `vocalScore`.
- **Emotion scan pending logic.** The worker treats a song as pending if it was never analyzed with the current `MODEL_VERSION` **or** it has been analyzed but is missing `vocalScore`, provided it has a local file and has not failed `MAX_ATTEMPTS` (=3) times. Manual scans (`batch=300`, `APPEND` chaining, paused/`isStopped`-aware) auto-queue the next batch while a gap remains, and abort instead of requeueing when the whole batch fails.
- **Async random/infinite play.** `PlayerRuntime.playRandomQueue(onResult)` and `startInfinitePlay(onResult)` now run the suspend planner off the main thread and report `(started, moodSlotName)` through the callback (used for the elected-slot toast). Explore-play starts through the same path with `isExplorePlay` set.
- **DB v16 → v17 migration.** `MIGRATION_16_17` adds `index_songs_albumId` and `index_playback_events_songId` (album-directory lookup / orphan-album cleanup and `DELETE ... WHERE songId IN (...)` previously scanned whole tables); schema export is now `17.json`. Cruise highlights of the v11→v16 range in the same object: v12 adds `song_emotions.userTagsEdited`; v14 adds `song_emotions.vocalScore`; v15 relabels stored curves to the v5 coordinate string without a rescan (compensation is read-derived); v15_16 rescales legacy word votes ×100 to match the ratio-weighted scale.
- **Ratio-weighted vote scale migration.** `MIGRATION_15_16` multiplies all historical `word_preferences.likeVotes`/`dislikeVotes` by 100 so old 1-vote-per-word counts stay comparable with new shares×100 weights; because `WordPreference.kind` is a relative (likes-vs-dislikes) comparison, the scaling does not flip any classification.
- **Explore refill near the queue end.** `refillExplorePlayQueue` fires from `PlayerMediaEventFacade.handleMediaItemEnded` when the just-ended song was second-to-last (`remainingMediaItems() <= exploreRefillThreshold`, where `REFILL_THRESHOLD = 0`), and both exploration learning and refill require `isExplorePlay && isExploreModeActive` (a mode switch that left `EXPLORE` exits the session).

## New since the last quickstart write-up

- **v3.12.2 / versionCode 50** (up from 3.11.0 / 46). The app now exposes ratio-weighted word-preference learning and near-end explore refill described above, plus the DB v16→v17 index migration and (M-20) `MelodyApplication` `AppLog.install(..., performanceLoggingEnabled = true)` so `PerformanceTrace` whitelisted operations still log under release while normal debug logging is off.

## All wiki pages

- Entry and operations: this page, [build / verify / release operations](/openwiki/operations/build-and-verification.md), [test map](/openwiki/testing/test-map.md)
- Architecture: [module layering and dependency boundaries](/openwiki/architecture/module-graph.md), [app shell, navigation and cross-module wiring](/openwiki/architecture/app-shell-navigation.md), [data persistence: Room and DataStore](/openwiki/architecture/data-persistence.md)
- Player: [PlayerRuntime and the facade list](/openwiki/player/runtime-facades.md), [queue and windowed sync](/openwiki/player/queue-architecture.md), [random & infinite play (incl. explore play)](/openwiki/player/random-and-infinite.md), [playback state machine and restore](/openwiki/player/state-machine.md), [playback service and MediaController](/openwiki/player/service-and-controller.md)
- Domain concepts: [emotion domain model, word vocabulary and word-preference votes](/openwiki/concepts/emotion-model.md), [mood time-slot and enhance modes](/openwiki/concepts/mood-time-slot.md)
- End-to-end workflows: [emotion analysis pipeline](/openwiki/workflows/emotion-analysis-pipeline.md), [playback session lifecycle](/openwiki/workflows/playback-session-lifecycle.md)
