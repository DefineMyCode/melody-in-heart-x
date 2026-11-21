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
- Release builds ship only `arm64-v8a` native libs and `resConfigs("zh")`; release APK is ~6.2 MB.
- A Baseline Profile (2425 rules, generated on a Pixel 6 emulator) is bundled in release via `androidx.profileinstaller`, plus a scroll `FrameTimingMetric` benchmark.
- Playback-position UI updates are isolated to a narrow `positionMs` flow, persistence/startup writes are off the main thread, and model/route-state classes carry `@Stable`/`@Immutable` to cut recomposition.
- 评审 2026-11-20（Spark X2.6）修复：`PlaybackController` 连接/释放生命周期竞态（`stateLock` 串行化 + `released` 标志 + future 监听器先移除再释放）、`playSingle` 返回值透传、`PlayDurationTracker` scope 取消 + 字段 `@Volatile`、单曲结束信号去重、`PlayerPersistenceGraph.persistScope` `release()` 排空后取消、跨线程状态 `@Volatile`、`updateSongTitleOverride` 通知 UI、`AlbumArtExtractor` 超时 `ownsHash` 守卫、`maxKnownSongId` 纳入 `song_group_overrides`、`SongEmotionsRepository` 原子 UPSERT 消除 TOCTOU、`LrcParser` 1 位毫秒支持、`EmbeddedLyricsParser` OGG 支持、歌词抽屉窄流重组优化、35 处可点击图标 `contentDescription`。全部行为变更补充了单元测试，详见 [fix-log-2026-11-20](fix-log-2026-11-20.md)。
- 架构门禁新增：中文字面量棘轮规则（`src/main` 下含中文的双引号字符串字面量数基线 900，超过即构建失败，防止国际化负担持续膨胀）。
- 备份规则调整：用户设置 DataStore（`player_settings`/`mood_time_slot`/`emotion_failures`）纳入云备份与设备迁移，便于跨设备恢复用户偏好；曲库数据库与瞬态状态仍排除（文件路径换机后无效）。架构门禁隐私排除清单同步更新。

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
- Macrobenchmark coverage exists for cold startup, home-screen scroll (`FrameTimingMetric`), and Baseline Profile generation through `:benchmark`. Compile/package verification is expected in CI; metric execution has been run on a Pixel 6 emulator (cold start median ≈ 810 ms, software-rendered).

## Still Not Fully Proven

- Compose UI coverage exists for key screens and playlist workflows, and permission denial copy is covered at the policy level. The ActivityResult launcher path and rendered denial toast still need an emulator/device acceptance pass.
- Media3 service/system media key behavior is still mostly covered through planner/facade unit tests rather than an end-to-end device test.
- `MusicRepository` is now hidden behind narrow adapters and delegates Room song/playlist persistence to `RoomMusicLibraryDataSource`, but import scanning, album-art refresh, SAF deletion, and in-memory mutation coordination remain broad enough to split further in a later phase.
- Playback state serialization is now isolated and tested, but it still stores JSON text in Preferences DataStore. A later phase can migrate it to Proto DataStore if stronger schema evolution is needed.
- Performance observability is now explicit and deliberate: `PerformanceTrace` is enabled by default in **debug** builds and **intentionally silent in release** builds (privacy/volume decision, not an accident of `AppLog.info` being gated), with an opt-in allow-list (`PerformanceTrace.allow`) so critical operations can still emit traces in release. Observability also comes from playback-window shape tests, a cold-start Macrobenchmark, a scroll benchmark, and a bundled Baseline Profile. Cold-start timing is now measured on an emulator (software-rendered); physical-device acceptance is still pending, and MIUI devices may block macrobenchmark's automated permission/frame-confirmation steps.
