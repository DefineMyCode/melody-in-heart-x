# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Key Versions

| Tool | Version | Notes |
|------|---------|-------|
| Kotlin | 2.0.21 | Compose compiler is built-in — apply `kotlin-compose` plugin, NOT `kotlinCompilerExtensionVersion` |
| AGP | 8.13.2 | |
| KSP | 2.0.21-1.0.28 | Must match Kotlin version exactly |
| Compose BOM | 2024.09.00 | |
| Media3 | 1.9.0 | + Jellyfin FFmpeg decoder extension |
| Room | 2.6.1 | Schema export in `data/schemas/` |
| Hilt | 2.52 | |
| minSdk / targetSdk | 33 / 36 | Java 11 bytecode |
| versionName | 3.3.0 (versionCode 22) | |

## Build / Test / Lint Commands

All commands run from the project root with `.\gradlew.bat` (Windows) or `./gradlew` (macOS/Linux).

### Build Variants

Three build types: **debug** (default, no minification), **release** (R8 minification + resource shrinking, `proguard-android-optimize.txt`), and **benchmark** (inherits release settings, uses debug signing, non-debuggable).

```bash
# Debug APK
.\gradlew.bat :app:assembleDebug

# Release APK
.\gradlew.bat :app:assembleRelease

# Benchmark APK
.\gradlew.bat :app:assembleBenchmark

# Compile a specific module (quick feedback without full APK)
.\gradlew.bat :data:compileDebugKotlin
.\gradlew.bat :player:compileDebugKotlin
```

### Testing

```bash
# All unit tests (JVM, no device needed)
.\gradlew.bat test

# Unit tests for a specific module
.\gradlew.bat :domain:test
.\gradlew.bat :player:test
.\gradlew.bat :data:test
.\gradlew.bat :feature:player:test
.\gradlew.bat :core:common:test

# A single test class (module-qualified)
.\gradlew.bat :player:test --tests "cn.com.dcsgo.mihx.player.window.ControllerQueuePlannerTest"

# Instrumented tests (requires emulator/device)
.\gradlew.bat :app:connectedAndroidTest

# Benchmark (requires emulator/device)
.\gradlew.bat :benchmark:connectedCheck
```

### Lint & Architecture Gates

```bash
# Code formatting check (Kotlin + Gradle + XML)
.\gradlew.bat spotlessCheck

# Auto-format code
.\gradlew.bat spotlessApply

# Full architecture verification
.\gradlew.bat verifyProductArchitecture

# Run everything: spotless + architecture + all subproject tests
.\gradlew.bat check
```

`verifyProductArchitecture` is a **custom Gradle task** (~550 lines in root `build.gradle.kts`) that enforces: module boundary imports, Hilt entry points, Room schema immutability, feature ownership (no overlay routing, no cross-feature deps, no direct `:data`/`:player` deps from features), LazyColumn keys, `AppLog` over `android.util.Log`, privacy/backup excludes, release R8/shrink settings, benchmark module shape, `PerformanceTrace` anchors, and more.

The `check` lifecycle task aggregates `spotlessCheck` + `verifyProductArchitecture` + all subproject `check` tasks. Run this before committing.

### Build Config Notes

- Kotlin compilation runs **in-process** (`kotlin.compiler.execution.strategy=in-process`) for deterministic Windows builds.
- JVM heap is set to 2048 MB.
- `android.nonTransitiveRClass=true` — do not reference resources across module boundaries by R class.
- Compose compiler is the Kotlin 2.0+ **compose compiler plugin** (`org.jetbrains.kotlin.plugin.compose`). Do NOT add the old `composeOptions { kotlinCompilerExtensionVersion }` block — it won't work.
- Release builds are **arm64-v8a only** (`minSdk 33` 全是 64 位设备) with `resConfigs("zh", "en")`; debug/benchmark 保留全 ABI 以兼容模拟器。
- Baseline Profile 位于 `app/src/main/baselineProfiles/baseline-prof.txt`（release 自动打包 + `profileinstaller` 应用）；`:benchmark` 模块含 `StartupBenchmark` / `ScrollBenchmark` / `BaselineProfileGenerator`，跑在真机或模拟器（MIUI 真机可能因 ROM 限制无法跑 macrobenchmark）。

## Architecture Overview

### Module Graph

```
:app  ──>  :feature:* , :core:* , :domain , :data , :player

Feature modules ──> :core:* , :domain   (NOT :data or :player, NOT each other)

:player ──> :core:* , :domain
:data   ──> :core:* , :domain
:domain ──> :core:model , :core:common
:core:ui ──> :core:model
:core:common ──> (standalone)
:core:model ──> (zero dependencies, pure data types)
```

These boundaries are enforced by `verifyProductArchitecture`. Features must not import `com.dcsgo.data.*` implementation classes or depend directly on `:data`/`:player` Gradle modules. Feature-to-feature cross-dependencies are also forbidden — routing between features happens in `:app`.

### Layer Responsibilities

| Layer | Module(s) | Contains |
|-------|-----------|----------|
| **App shell** | `:app` | `MelodyApplication`, `MainActivity`, `AppRoot`, `AppScaffold`, `AppNavHost`, `PermissionCoordinator`, Hilt wiring modules |
| **Feature** | `:feature:*` | Route/Screen composables, feature-specific ViewModels, facade objects (for `:feature:player`) |
| **Domain** | `:domain` | Repository **interfaces**, playback policy (planners, synchronizers, coordinators), pure business logic. No Android dependencies. |
| **Data** | `:data` | Room database/DAO/entities/migrations, repository implementations, narrow adapters binding domain interfaces, metadata extractors, DataStore wrappers |
| **Player** | `:player` | `AppMediaSessionService` (ExoPlayer + MediaSession), `PlaybackController`, windowed queue planners, Bluetooth coordinator |
| **Core** | `:core:model`, `:core:common`, `:core:ui` | Data classes (`Song`, `Playlist`, `PlayQueue`, etc.), logging (`AppLog`/`AppLogger`), `PerformanceTrace`, `CoroutineDispatchers`, theme (`MusicplayerTheme`), shared Compose components |

### DI (Hilt)

- `MelodyApplication` → `@HiltAndroidApp`
- `MainActivity` → `@AndroidEntryPoint`
- `AppMediaSessionService` (in `:player`) → `@AndroidEntryPoint`
- `PlayerViewModel` (in `:feature:player`) → `@HiltViewModel`

Singleton Hilt modules are in `app/src/main/java/.../app/di/` (CoroutineModule, LoggerModule, PlayerModule) and `data/src/main/java/.../data/di/` (DatabaseModule, RepositoryModule, DataStoreModule). Repository implementations are bound through narrow adapter classes, not directly by `MusicRepository`.

### The PlayerViewModel / Facade Architecture

`PlayerViewModel` (in `:feature:player`) is a **thin UI facade**. The real player logic lives in ~18 facade objects managed by `PlayerRuntime`. Each facade owns one concern:

- **Queue**: `PlayerQueueFacade`, `PlayerControllerQueueFacade`, `PlayerRandomQueueFacade`
- **Playback**: `PlayerPlaybackFacade`, `PlayerPlaybackBridgeFacade`
- **State sync**: `PlayerControllerStateFacade`, `PlayerMediaEventFacade`
- **Persistence**: `PlayerPersistenceFacade` (+ `PlaybackStateStore` + `PlaybackRestoreCoordinator`)
- **Library/Import**: `PlayerLibraryFacade`, `PlayerImportFacade`, `PlayerPlaylistFacade`
- **Other**: `PlayerVersionFacade`, `PlayerQuickSkipFacade`, `PlayerSongDeletionFacade`, `PlayerSessionFacade`, `PlayerStartupFacade`, `PlayerLifecycleFacade`, `PlayerErrorFacade`

Facades are assembled by graph objects (`PlayerQueueGraph`, `PlayerMediaControllerGraph`, `PlayerPersistenceGraph`, `PlayerBluetoothGraph`, `PlayerPlaybackSessionGraph`). Do not re-introduce the old `PlayerViewModelComponents` monolith — it was deliberately dismantled and is enforced by `verifyProductArchitecture`.

### Feature Route/Screen Pattern

Every feature module exposes its UI through a Route + Screen pair:
- `XxxRoute.kt` — public composable receiving `State` + `Actions` + callback lambdas
- `XxxScreen.kt` — internal composable, not imported across feature boundaries
- `XxxRouteState` / `XxxRouteActions` — data classes for the feature's public API

Routes are wired in `AppNavHost.kt`. No feature should render another feature's UI directly.

### Playback Queue Architecture (see [docs/architecture/PLAYBACK_QUEUE_ARCHITECTURE.md](docs/architecture/PLAYBACK_QUEUE_ARCHITECTURE.md))

Key invariants:
- `PlayQueue.songs` = full business queue (allows duplicate entries). `PlayQueue.currentIndex` indexes into this list.
- `MediaController` holds only a **window** (current ± 20/50 items), managed by `WindowedControllerQueuePlanner` and `ControllerWindowSynchronizer`.
- `MediaItem.mediaId` must equal `Song.id.toString()` — this is how Media3 state maps back to business queue.
- Never re-introduce `PlayQueue.nextIndex()`/`previousIndex()` — navigation is done through MediaController.
- Queue sync, next-song insertion, and play mode changes must go through the windowed planner layer in `player/window/`.

### Playback State Machine (see [docs/architecture/PLAYBACK_STATE_MACHINE.md](docs/architecture/PLAYBACK_STATE_MACHINE.md))

Canonical states: `idle → preparing → ready → playing ↔ paused/buffering → ended/error`. `ControllerPlaybackStateSynchronizer` is the single point that maps Media3 snapshots into `PlayerUiState`.

### Data Persistence

- **Room** (`MelodyDatabase`, v8): songs, playlists, playlist-song refs, play stats, quick-skip songs, short-play counts, song group overrides, migration state, artists, albums. Schema exports in `data/schemas/`.
- **DataStore Preferences**: player settings (theme, random mode, Bluetooth, notifications) and playback state snapshots.
- **Legacy migration**: `SharedPreferencesLegacyJsonMigration` handles v1 JSON → Room migration on first launch. Legacy SharedPreferences readers remain only for that path.

### Adding a New Feature Module

1. Add to `settings.gradle.kts`: `include(":feature:xxx")`
2. Create `feature/xxx/build.gradle.kts` with plugins (`kotlin-android`, `kotlin-compose`, `ksp`, `hilt`), referencing `:core:*` and `:domain` only (NOT `:data` or `:player`).
3. Create `feature/xxx/.gitignore` with required patterns.
4. Create `XxxRoute.kt` (public, receives State + Actions + callbacks) and `XxxScreen.kt` (internal).
5. Wire into `AppNavHost.kt` as a Navigation Compose destination.
6. Run `verifyProductArchitecture` — it will fail if module dependencies leak or the Route/Screen pattern is missing.

### Room Schema Changes

When adding/modifying a Room entity:

1. Make the entity/DAO change.
2. Bump `MelodyDatabase.VERSION` in `data/.../local/MelodyDatabase.kt`.
3. Export the new schema: build with Room's schema export enabled (already on) → the new JSON appears in `data/schemas/`.
4. Add a `Migration(N, N+1)` implementation, register it in `DatabaseModule.kt`.
5. Never modify existing schema JSON files — they are immutable snapshots. `verifyProductArchitecture` enforces this.
6. If the migration involves complex data movement, write a focused unit test for it.

### Code Quality Rules (architecturally enforced by verifyProductArchitecture)

- Use `AppLog` / `AppLogger` instead of `android.util.Log`. The logger redacts URIs/paths in release builds.
- LazyColumn/LazyRow items must use stable `key` parameters.
- Feature modules must not depend on `:data` or `:player` Gradle modules, and must not import `com.dcsgo.data.*` implementation classes.
- Feature-to-feature dependencies are forbidden — cross-feature routing happens in `:app`.
- `player/window/` must depend on domain playback policy, not `data.player` implementations.
- Every module must have a `.gitignore` covering `/build/`, `/.cxx/`, `/.externalNativeBuild/`, `/captures/`.
- Room schema history files (1.json, 2.json, …) must remain immutable.
- Backup/data-extraction rules must exclude playback state, Room files, DataStore files, and album art cache.
- Bluetooth playback monitoring must be user-triggered from Settings, not initialized at startup.
- Permission requests (notification, Bluetooth) must be user-triggered from Settings, not at startup.
- `:app` manifest must not declare `AppMediaSessionService` (it belongs to `:player` manifest).
- Release build must have `isMinifyEnabled = true` and `isShrinkResources = true`.
- Performance-critical paths (`MusicRepository` import/scan, `PlaybackController` queue/prepare/sync/next) must keep their `PerformanceTrace` operation anchors.
