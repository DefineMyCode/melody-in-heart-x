# Melody in Heart Product Refactor Audit

This audit tracks the refactor against `review.md` and the productization plan. It is intentionally evidence-based: an item is marked complete only when the current worktree contains code, tests, or verification gates that prove the requirement.

## Verified

- Multi-module product graph exists in `settings.gradle.kts`: `:app`, `:core:model`, `:core:common`, `:core:ui`, `:domain`, `:data`, `:player`, and all feature modules.
- Hilt is the app assembly mechanism. `MelodyApplication`, `MainActivity`, `AppMediaSessionService`, and `PlayerViewModel` are Hilt entry points, with repository, player, database, DataStore, coroutine, and logger modules.
- `:app` is now an app shell: `AppRoot`, `AppScaffold`, `AppNavHost`, `PermissionCoordinator`, theme state, and `PlayerQueueSheetHost` are split from the old root composable.
- Boolean overlay navigation has been removed. Settings, play statistics, quick skip songs, lyrics, playlist detail, and version management are Navigation Compose routes owned by feature modules.
- Domain repository interfaces exist for songs, playlists, import, album art, play statistics, quick skip, player settings, playback state, lyrics, and song metadata.
- `MusicRepository` no longer implements multiple domain repository interfaces directly. Narrow data-layer adapters bind `SongRepository`, `PlaylistRepository`, `MusicImportRepository`, and `AlbumArtRepository` through Hilt.
- `MusicRepository` has started its internal split: Room song/playlist restore and persistence now live in `RoomMusicLibraryDataSource`.
- Room schemas and migrations exist for the product data model, including songs, playlists, playlist-song refs, play stats, quick skip songs, short-play counts, song group overrides, and migration state.
- DataStore backs player settings and playback state, while legacy SharedPreferences readers remain for phased migration.
- Playback-state persistence uses a dedicated `PlaybackStateSnapshotSerializer` with structured JSON parsing and focused serializer tests instead of inline regex parsing inside the store.
- Playback progress ticker defaults to 500 ms, and playback state autosave is separated from UI progress ticking.
- Windowed playback queue classes exist: `PlaybackWindowState`, `PlaybackWindowPlanner`, `ControllerWindowSynchronizer`, and `WindowedControllerQueuePlanner`.
- Logging is routed through `AppLog`/`AppLogger`, with tests covering URI/path redaction.
- Release build enables R8 minification and resource shrinking; a benchmark build type exists and inherits release settings.
- A real `:benchmark` Macrobenchmark module targets `:app` and contains a cold-start `StartupBenchmark`.
- Backup and data extraction rules exclude playback state, legacy preference files, Room database files, DataStore files, and album-art cache data.
- Root and module `.gitignore` files exist and are enforced by `verifyProductArchitecture`.
- Architecture verification is automated by `verifyProductArchitecture`, and `check` depends on `spotlessCheck` plus the architecture gate.
- Kotlin compilation is configured to run in-process for deterministic Windows verification after daemon marker-file permission failures caused unstable incremental builds.
- Full JVM unit tests pass across debug, release, and benchmark unit-test variants.
- Release and benchmark APK variants assemble successfully with R8/resource shrinking enabled.

## Verified By Tests Or Gates

- Latest full unit-test validation passed:

```powershell
.\gradlew.bat test
```

- Latest release/benchmark package validation passed:

```powershell
.\gradlew.bat :app:assembleRelease :app:assembleBenchmark
```

- Architecture and targeted compile validation passed:

```powershell
.\gradlew.bat spotlessCheck verifyProductArchitecture :data:compileDebugKotlin :app:compileDebugKotlin
```

- Earlier broad validation also passed:

```powershell
.\gradlew.bat "-Pksp.incremental=false" spotlessCheck verifyProductArchitecture :app:compileDebugKotlin :feature:user:compileDebugKotlin :feature:home:compileDebugKotlin :feature:player:testDebugUnitTest :data:testDebugUnitTest
```

- Repository tests cover legacy JSON migration, damaged JSON recovery, missing-field compatibility, duplicate URI import, playlist cross references, play-stat ranking, quick-skip persistence, and DataStore-backed settings.
- Player tests cover planner/facade behavior, window boundaries, window sizing for 100/500/1000 song queues, next-song insertion, play-mode mapping, controller-window synchronization, and playback-state serializer compatibility.
- Compose instrumentation tests exist for home, playlist, and settings flows, including playlist create, rename, delete, add-to-playlist, remove-from-playlist, batch playback actions, playback controls, and settings toggles.
- Runtime permission policy is isolated behind JVM tests that verify notification and Bluetooth permission denial messages by Android API level.
- Macrobenchmark coverage now exists for cold startup through `:benchmark`; compile/package verification is expected in CI, while metric execution still requires an emulator or physical device.

## Still Not Fully Proven

- Compose UI coverage exists for key screens and playlist workflows, and permission denial copy is covered at the policy level. The ActivityResult launcher path and rendered denial toast still need an emulator/device acceptance pass.
- Media3 service/system media key behavior is still mostly covered through planner/facade unit tests rather than an end-to-end device test.
- `MusicRepository` is now hidden behind narrow adapters and delegates Room song/playlist persistence to `RoomMusicLibraryDataSource`, but import scanning, album-art refresh, SAF deletion, and in-memory mutation coordination remain broad enough to split further in a later phase.
- Playback state serialization is now isolated and tested, but it still stores JSON text in Preferences DataStore. A later phase can migrate it to Proto DataStore if stronger schema evolution is needed.
- Performance observability exists through `PerformanceTrace`, playback-window shape tests, and a cold-start Macrobenchmark module. Actual startup timing numbers still need a device/emulator acceptance run.
