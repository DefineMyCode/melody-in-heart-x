---
type: Workflow
title: "Playback Session Lifecycle"
description: "End-to-end walk through one complete playback session: cold-start assembly and main-thread discipline, MediaController connect with the snapshot-restore live-session decision, queue setup and playback start, Media3 song-change translation with PlayDurationTracker settlement, infinite refill, in-session bypaths (sleep timer, Bluetooth disconnect, single-item loop rewind), and the recovery loop after process death."
tags: [player, playback-session, cold-start, state-restore, media-controller, duration-settlement, sleep-timer, bluetooth, quick-skip, process-death-recovery]
verified:
  - by: openwiki/0.5.0
    at: 2026-10-10T11:15:45.800Z
sources:
  - id: openwiki-source-daad64f3ba82b5b0ff4a8d94
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/ControllerPlaybackStateSynchronizer.kt
  - id: openwiki-source-eb6f23ed46ad20362e4d05f1
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/PlaybackQueueActionPlanner.kt
  - id: openwiki-source-31721788a95f08f3d7c7f198
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/PlaybackRestoreCoordinator.kt
  - id: openwiki-source-36d7230cd801a03f59027967
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/PlaybackSessionCoordinator.kt
  - id: openwiki-source-5a90dd2522a15d4f982b5d89
    resource: repo://domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/QueueManager.kt
  - id: openwiki-source-7947bd28b3f56e3e0375e61b
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerBluetoothGraph.kt
  - id: openwiki-source-f46d6b225e981a463a42cc94
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerControllerStateFacade.kt
  - id: openwiki-source-de2bd591186ef143f8a8f0e8
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerErrorFacade.kt
  - id: openwiki-source-4790680f6639fb5e9a5ea641
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerLifecycleFacade.kt
  - id: openwiki-source-05628432a0c9eb5223697d07
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerMediaControllerGraph.kt
  - id: openwiki-source-0a840abe5bf9183316c47b4e
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerMediaEventFacade.kt
  - id: openwiki-source-77273795d3d3691caff7ecb8
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceFacade.kt
  - id: openwiki-source-ec996dfca8a06fd74702893d
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceGraph.kt
  - id: openwiki-source-5642ea3e67b9ed0453132bc8
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPlaybackProgressTicker.kt
  - id: openwiki-source-f2a2fbd536b52485da3bdb2e
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPlaybackStateAutosaver.kt
  - id: openwiki-source-b86e2b97e4f71e5af8b2924c
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerQueueFacade.kt
  - id: openwiki-source-ff40146bae9715f1a3860309
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerRuntime.kt
  - id: openwiki-source-54ba4f7acb643769b197e402
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerSleepTimerCoordinator.kt
  - id: openwiki-source-37ecb8ac896ee055dab0bc51
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerStartupFacade.kt
  - id: openwiki-source-fe80d1b3a38b98c97840e74d
    resource: repo://feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerViewModel.kt
  - id: openwiki-source-ca2d7e504c1ad19cbaff9046
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceFacadeTest.kt
  - id: openwiki-source-6cbc32290e81c1e4b344fe2b
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceGraphTest.kt
  - id: openwiki-source-e0c3384d9aa25ad5cfc535e4
    resource: repo://feature/player/src/test/java/cn/com/dcsgo/mihx/feature/player/PlayerStartupFacadeTest.kt
  - id: openwiki-source-7657c9f7c862e7d55acc0b62
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/data/player/AppMediaSessionService.kt
  - id: openwiki-source-da7b859f3e94dd4a2c91f3d4
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/data/player/BluetoothPlaybackCoordinator.kt
  - id: openwiki-source-04a93731443f6ff3e9f66921
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/data/player/PlaybackController.kt
  - id: openwiki-source-35e0261203b78a5159d1723b
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/data/player/PlaybackStateStore.kt
  - id: openwiki-source-7cad1cd30dac214b0af6715e
    resource: repo://player/src/main/java/cn/com/dcsgo/mihx/data/player/PlayDurationTracker.kt
  - id: openwiki-source-f0e058e6091ef385898fb85e
    resource: repo://player/src/test/java/cn/com/dcsgo/mihx/data/player/MediaItemWrapDetectionTest.kt
generated: { by: "openwiki/0.5.0", at: "2026-10-10T11:15:45.800Z" }
---

# Workflow: Playback Session Lifecycle

This page stitches one complete playback session start to finish: **app startup → facade initialization and MediaController connect → playback-snapshot restore decision → queue setup and playback start → song change / settlement / auto refill → duration tracking and quick-skip → in-session bypaths → the recovery loop after a process kill**. Per-point detail is not expanded here: dual queue / window synchronization lives in `/openwiki/player/queue-architecture.md`, random and infinite refill in `/openwiki/player/random-and-infinite.md`, the facade wiring catalog in `/openwiki/player/runtime-facades.md`, state-machine invariants and restore-decision rules in `/openwiki/player/state-machine.md`, and the service/controller integration surface in `/openwiki/player/service-and-controller.md`.

## Cold start to playback: timeline overview

```mermaid
sequenceDiagram
    participant App as MelodyApplication
    participant Act as MainActivity
    participant Root as AppRoot
    participant VM as PlayerViewModel
    participant RT as PlayerRuntime
    participant SU as PlayerStartupFacade
    participant SVC as AppMediaSessionService
    participant PC as PlaybackController
    participant PG as PlayerPersistenceGraph
    participant LIB as PlayerLibraryFacade

    App->>App: onCreate installs logging and uncaught-exception fallback
    Act->>Root: setContent AppRoot
    Root->>VM: viewModel created via Hilt
    VM->>RT: runtimeFactory.create viewModelScope wires up facades
    VM->>RT: init calls runtime.start
    RT->>RT: IO thread reads 4 settings and restores the sleep timer
    RT->>SU: startupFacade.start runs on main
    SU->>SVC: startService starts the playback service
    SU->>PC: connectMediaController
    PC->>SVC: SessionToken + buildAsync async binding
    SU->>LIB: loadInitialData passes afterInitialSnapshot callback
    LIB->>LIB: IO reads library and refreshes snapshot
    LIB->>PG: afterInitialSnapshot triggers restorePlaybackState
    PG->>PG: IO reads DataStore snapshot to produce pendingRestore
    PC-->>PG: connect success after first-frame snapshot sync calls onControllerReady
    Note over PC,PG: connect and snapshot read run in parallel; either waits for the other
    PG->>PG: maybeApplyRestore decides live vs full restore from controller queue
    RT->>RT: refreshMoodSlotCache pulls time-slot config and tag snapshot asynchronously
    RT->>RT: initializes Bluetooth monitor only when the setting is on
```

*Cold start to playback: after the UI chain wires up layer by layer, `startupFacade.start` starts the service, connects the controller, and loads the library (the restore read is triggered after the first snapshot); the restore decision waits until the "connect" and "snapshot read" async lines converge in `PlayerPersistenceGraph`; contextual-play time-slot/tag snapshots are pulled asynchronously after startup without blocking any step.*

## Phase 0: process assembly and main-thread discipline

- **Process entry**: `MelodyApplication` (`@HiltAndroidApp`) installs in `onCreate` `AppLog` (level controlled by `BuildConfig.DEBUG`), a global uncaught-exception fallback (first logs a redacted message, then hands off to the system handler), and schedules the emotion batch scan `EmotionScanScheduler.schedule`. The image stack (Coil `ImageLoaderFactory`) is deferred to first load to shorten the cold-start path.
- **UI assembly**: `MainActivity` (`@AndroidEntryPoint`) only does `setContent { AppRoot() }`; `AppRoot` creates `PlayerViewModel` and other Hilt ViewModels through `viewModel()` in the Activity scope.
- **Runtime assembly**: `PlayerViewModel` is a **pure delegation facade** — its only constructor parameter is `PlayerRuntimeFactory`, the constructor builds `PlayerRuntime` (the feature composition root) with `runtimeFactory.create(viewModelScope)`, and `init { runtime.start() }` ignites it; every public method is a one-line forward.
- **Main-thread discipline**: `PlayerRuntime.start()` moves all blocking sources on the startup path off the main frame — the 4 DataStore-backed settings (global uniform random, Bluetooth monitoring, playback notification, daily listening goal) and the sleep-timer restore (2 reads + possibly 1 write) all finish on `dispatchers.io` before `startupFacade.start()` runs on the main thread; afterwards `refreshMoodSlotCache()` asynchronously pulls the time-slot config and emotion-tag snapshot (`@Volatile` caches, refreshed tactically by `refreshMoodSlots()` after settings-page saves), and the Bluetooth monitor is initialized **only when** the persisted `bluetoothPlaybackMonitoringEnabled` is true via `bluetoothGraph.initialize()`.

`PlayerStartupFacade` fixes the boot steps into a **fixed order**: `startService` → `connectMediaController` → `loadInitialData` (passing `restorePlaybackState` as the `afterInitialSnapshot` callback; the restore read is deliberately placed after the library's first snapshot because snapshot decoding uses the just-loaded library to filter out obsolete songs) → `listenForSongChanges`. This order is locked down by `PlayerStartupFacadeTest`.

## Phase 1: service start and MediaController connect

- **startService does not use FGS**: `PlaybackController.startService` uses `context.startService(...)` rather than `startForegroundService(...)`. The reason is recorded in a comment (2026-09-03 ANR regression fix): `startForegroundService` forces `startForeground()` within 5 seconds, but `MediaSessionService` only posts its notification once media items exist / playback is active — under a cold-start empty session the call never fires and the system flags an FGS timeout. The method's only call chain is the foreground UI cold start (Activity foreground), where `startService` is legal; even a future background launch can bind through the `connect()` SessionToken mechanism.
- **connect**: `SessionToken` + `MediaController.Builder.buildAsync()` bind asynchronously; on success, `addListener` and `drainPendingActions` (replays UI commands queued while disconnected, FIFO capped at 64) run first, then `onConnected(snapshot)` fires. `PlayerMediaControllerGraph.connect` uses the first-frame snapshot to sync `controllerStateAdapter` before it triggers `onConnected` → `persistenceGraph.onControllerReady()` — **this is why the restore decision gets a trustworthy controller state**.
- **Connection-failure fallback**: a failed connect aborts pending actions and reports `onControllerUnavailable`; `PlayerRuntime.handleControllerUnavailable` logs, writes the reason and dropped count into `errorMessage`, but **still calls `persistenceGraph.onControllerReady()`** to release the restore decision — otherwise `pendingRestore` would hang forever and the UI queue would never recover.

## Phase 2: snapshot-restore decision (live-session criterion)

The restore question is: **does the server still have a live playing session?** Answering wrong either overwrites a playing session with the DataStore snapshot (progress rollback) or discards a recoverable session. The implementation converges in `PlayerPersistenceGraph`:

1. **Two async lines in parallel**: `restorePlaybackState()` reads and decodes DataStore on `dispatchers.io` (`PlaybackRestoreCoordinator.restore(state().songs)` produces a `PlaybackRestoreResult`), returns to main to write `pendingRestore`; the controller connection success (or failure fallback) sets `controllerReady`. **Whichever finishes first waits for the other** — `maybeApplyRestore()` makes no decision until both flags are ready.
2. **The sole live-session criterion**: `liveSessionActive()` = `controllerQueueInfo()?.mediaItemCount ?: 0 > 0`. It deliberately does not guess with "playback position > 0" — the restore IO read can finish before MediaController connects (`buildAsync` binds asynchronously cross-process), at which point `currentPlaybackPositionMs` degrades to a UI-state value (always 0 after a rebuild) and would misclassify a live session as none.
3. **Two-branch apply** (`PlayerPersistenceFacade.applyRestoreResult`):

| controller queue | branch | UI side | controller side |
| --- | --- | --- | --- |
| empty (cold start / server empty session) | **full restore** `restoreController=true` | restores `playQueue`, infinite-play state, `currentSong`, restore-point position, `isPlaying=false` | `prepareControllerQueue(queue, currentIndex, positionMs)` fills the queue and restore-point position, **no autoplay** |
| non-empty (live session) | **UI queue shadow only** `restoreController=false` | restores the queue shadow plus `currentSong`/`sameNameSongs`; position and playing state are left to the post-connect `syncControllerPlaybackState` | **never touched** — no queue rebuild, no position write |

Two invariants (detailed in `/openwiki/player/state-machine.md` "constraint three"):

- **The UI queue shadow must be restored in both branches**: controller snapshot sync only adjusts `currentIndex`, never fills `songs`; skipping the UI side would leave the playback queue permanently empty after a rebuild and lose the infinite-play state.
- **Rebuilding the queue has two guards**: the decision layer only calls `prepareControllerQueue` when no live session exists; `PlaybackController.prepareQueue` carries a second guard internally (`mediaItemCount > 0` early-returns). It is the only one of the three queue commands (`playQueue`/`prepareQueue`/`syncQueue`) allowed to carry a live-session guard; `playQueue` is explicitly forbidden from adding one (otherwise a user tap-to-play request would be silently swallowed).

The full-restore branch's playable session is produced by `PlaybackRestoreCoordinator`'s upgrade step: when the restored queue's current song is playable it builds `RestoredPlayableSession(song, positionMs, sameNameSongs)`, same-name versions sorted by sample rate descending, sharing the same data as the full restore path.

## Phase 3: setup queue and start playback

The queue-replacement chain (user song tap / "contextual play" / playlist playback):

1. UI → `PlayerRuntime.setPlayQueue` → `PlayerQueueFacade.setPlayQueue`: `PlaybackQueueActionPlanner.replaceQueue` generates a new `PlayQueue` (built by `QueueManager.createQueue` producing `playOrderIds`) + a `PlayQueueIndex(startIndex)` action + the `exitInfinitePlay` flag.
2. `applyPlan` writes the plan into `PlayerUiState` (including exiting infinite play and `nextPlayState`), then executes the playback action `playFromQueue(queue, index)`.
3. `playFromQueue` first passes `PlayerErrorFacade`'s **playability guard**: a target song without a uri does not start, writing `the local file for "«title»" cannot be played`; only playable songs reach `playbackSessionGraph.startQueuePlayback`.
4. `PlaybackSessionCoordinator.startQueuePlayback` plans the windowed controller queue via `controllerQueuePlanner::plan` (business queue → playback window, see the queue-architecture page), and `PlaybackController.playQueue` starts it with `REPEAT_MODE_ALL` + `setMediaItems` + `prepare()` + `play()`; simultaneously `durationTracker.startPlayback(song.id, duration)` starts the timer and returns `trackedSongId`/`sameNameSong`.
5. Single-song playback (e.g. out-of-library single tap) uses `playSingle`: `REPEAT_MODE_OFF` + `setMediaItem`, same tracking start.

The controller's real state from there flows back through high-frequency `onEvents` snapshots: `PlayerControllerStateFacade.syncControllerPlaybackState` maps the snapshot back into `PlayerUiState` (mediaId → Song resolution, queue-index alignment, duration correction) and emits a `PlaybackStart` when "the snapshot is playing, a song resolves, and `trackedSongId != controllerSong.id`" (tracking starts once per song). The `isPlaying` transition must emit a ticker notification, and progress rendering only goes through the `positionMs` narrow flow — see constraints one/two in the state-machine page.

## Phase 4: song change and settlement paths

### Media3 event translation

<!-- openwiki: mermaid parse failed and this diagram was converted to a text fence so it does not break rendering. Fix the diagram source and restore the mermaid fence. Parser error: Heuristic: an unescaped angle bracket inside a label breaks rendering; rephrase the label. -->
```text
flowchart TD
    EV["Media3 Player.Listener"] --> TRANS["onMediaItemTransition reason AUTO SEEK REPEAT"]
    EV --> PWR["onPlayWhenReadyChanged END_OF_MEDIA_ITEM pause"]
    EV --> DISC["onPositionDiscontinuity AUTO discontinuity"]
    EV --> ST["onPlaybackStateChanged STATE_ENDED"]
    TRANS --> W{"isMediaItemWrap index-wrap judgment"}
    W -->|"yes wrapped"| ME["onMediaItemEnded songId wrapped=true"]
    W -->|"no"| ME2["onMediaItemEnded songId wrapped=false"]
    PWR --> ME3["onMediaItemEnded null false"]
    DISC --> RW{"isSingleItemLoopRewind hit"}
    RW -->|"yes"| ME4["onMediaItemEnded songId false"]
    ME --> H["handleMediaItemEnded"]
    ME2 --> H
    ME3 --> H
    ME4 --> H
    ST --> PE["handlePlaybackEnded only sets isPlaying=false position zeroed"]
    H --> STOP["stopPlayback settles old track and clears trackedSongId"]
    STOP --> RE{"infinite play and wrapped or remaining <= 5"}
    RE -->|"yes"| REFILL["refillInfinitePlayQueue"]
    RE -->|"no"| MODE
    REFILL --> MODE["restorePlayModeAfterNextSong"]
    MODE --> SLEEP["onSongEnded wrap up after final song"]
    STOP --> NEXT["next snapshot emits PlaybackStart raw count +1 and new session timer"]
```

*Song change and settlement: all four Media3 signal classes flow into `handleMediaItemEnded` (or the terminal wrap-up); that entry runs the fixed sequence "settle old track → infinite refill → restore add-next mode → sleep-timer hook", then the next snapshot resumes tracking as a new session.*

Translation-rule highlights:

- **Wrap is decided by index, not reason**: `isMediaItemWrap` (new index 0, previous index was the window's last item) — Media3 reports the `REPEAT_MODE_ALL` tail wrap as `SEEK` (manual next) or `AUTO` (natural end), while `REPEAT` only means single-item repeat. The `wrapped` flag is the infinite-refill trigger (after a wrap the remaining count grows large again, so a threshold-only rule would miss the refill).
- **Manual next goes through the same entry**: headset/lock-screen/notification SEEK skips must reach `onMediaItemEnded` or infinite play would never refill; `PLAYLIST_CHANGED` (the app's own window rebuild) does not trigger it.
- **`STATE_ENDED` (natural stop at queue end) only does UI wrap-up**: `handlePlaybackEnded` sets `isPlaying=false`, position 0, and does not settle.

### The fixed side-effect sequence of `handleMediaItemEnded`

`PlayerMediaEventFacade.handleMediaItemEnded(startedSongId, wrapped)` in order:

1. **Stop old-track tracking**: `stopPlaybackTracking()` (triggers `PlayDurationTracker.stopPlayback` → settlement, see next section) + `clearTrackedSong()`. Clearing `trackedSongId` is the other half of "track once per song": only the next song's snapshot can emit another `PlaybackStart`.
2. **Infinite refill**: while in infinite play and (`wrapped` or `remainingMediaItems() <= DEFAULT_REFILL_THRESHOLD = 5`) call `refillInfinitePlayQueue(startedSongId, advanceAfterWrap = wrapped)`. `advanceAfterWrap` jumps the current item to the first newly-refilled song and starts it from 0, avoiding a replay of the old window after a wrap; a normal expansion calls `syncPlayerQueue` to sync the window. Details in `/openwiki/player/random-and-infinite.md`.
3. **Restore the add-next play mode**: `QueueManager.restorePlayModeAfterNextSong` only when "current song == `nextPlaySongId`" restores the queue mode to `playModeBeforeNext`, corrects the business queue's current item with `startedSongId`, clears the temp flags, and `syncPlayerQueue` re-orders the window.
4. **Sleep-timer hook**: `onSleepTimerSongEnded()` handles the "pause after the final song" deadline (see Phase 6).

### Window refill for non-infinite queues

Snapshot sync doubles as a refill trigger point: the hook `PlayerRuntime` wires to `onControllerPlaybackSynced` calls `syncPlayerQueue` when "the business queue is non-empty, not in infinite play, and the controller's remaining media items ≤ 5", re-feeding the business queue to the controller so a sequential play mode auto-tops up its window near the tail.

## Phase 5: duration tracking and quick-skip loop

`PlayDurationTracker` (`:player`, injected via `PlaybackDurationMonitorFactory`) is the settlement executor; all Room writes run on a dedicated `SupervisorJob + Dispatchers.IO` scope (a plain Job would let one Room exception kill the whole scope, silently dropping "play count" without logs — the M-5 fix):

- **Tick granularity**: 1s tick (`UPDATE_INTERVAL_MS = 1000`) accumulates only while `isPlaying && !isSeeking`; seek windows are excluded and the timestamp resets afterward.
- **New session**: `startPlayback(songId, durationMs, initialPlayedMs)` settles the previous track, then `incrementRawPlayCount(+1)` (raw play count) for the new song; `initialPlayedMs` seeds the pre-kill restore-point listening time into the base so "already played before the kill + finished after restore" still meets the completion threshold instead of zeroing the timer.
- **Effective-play judgment** (`stopPlayback` → `settlePlayback`, IO coroutine): accumulated time reaching **90%** of the song duration (`COMPLETION_RATE_THRESHOLD = 0.9`) **or over 5 minutes** (long-song fallback) counts one effective play `increment(songId)`.
- **Quick-skip linkage**:
  - A song on the quick-skip list is **auto-removed after an effective play** and its short-play counter resets — once the user has listened all the way through, it is no longer treated as "wants to skip";
  - a **< 5s** (`SHORT_PLAY_THRESHOLD_MS = 5000`) short play accumulated 2 times (`SHORT_PLAY_COUNT_THRESHOLD = 2`) auto-adds the song to the quick-skip list — songs repeatedly opened and skipped get classified by the system.
- **Session records**: any play with positive duration calls `recordPlaybackSession` (startedAt derived from the duration), feeding today/week/month aggregation.

**Single-item queue loop-wrap is the settlement difference for the quick-skip scenario**: under `REPEAT_MODE_ALL` a queue reduced to one song (quick-skip/playlist/album/artist with one left) wraps back to the same item with window index 0→0 unchanged, so Media3 **never fires `onMediaItemTransition`** — settlement (effective play +1, removal from the quick-skip list) would never run and the timer accumulates across loops. `PlaybackController` uses `isSingleItemLoopRewind` from `onPositionDiscontinuity` as the fallback: `AUTO` discontinuity + single-item window + 0→0 + new position ≤ 2s + old position near the end (precise via `duration − 5s` when duration is known, conservative ≥ 30s otherwise; buffering reconnection does not produce a back-to-0 AUTO discontinuity, and a manual scrub to the start is a SEEK reason, not a false positive). After detecting it, it settles the finished play once, then continues loop timing as a new session.

## Phase 6: in-session bypaths

### Sleep timer (SleepTimerCoordinator)

- **Setup**: `start(minutes, playLastSong)` persists the end timestamp and the "play the last song" flag (DataStore), writes discrete UI state, and starts a per-second ticker.
- **Narrow-flow discipline (M-6)**: the countdown's per-second tick **only writes the `sleepTimerRemainingMs` narrow flow** (subscribed only by the sleep-timer Chip), never the main UiState — otherwise the whole shell recomposes every second during an active countdown. Discrete events (start/cancel/expiry) go through `updateState`.
- **Play-the-last-song mode**: at the deadline, when `playLastSong` and playing, `fire()` only sets `sleepTimerPausePending` and **explicitly resets the narrow flow**, waiting for the current song's natural end — that end signal is exactly the `onSleepTimerSongEnded()` at the tail of Phase 4's `handleMediaItemEnded`: while pending, pause and cancel.
- **Cancel/expiry must explicitly reset the narrow flow**: tick values are not in uiState, so `NarrowFlowSync`'s value comparison cannot observe a zeroing; both cancel and fire call `resetSleepTimerNarrowFlow`, otherwise a stale countdown remains on the Chip (2026-09-03 regression fix).
- **Cross-restart restore**: `restore()` reads the persisted `endAtMs` at startup — an expired one is zeroed directly, an unexpired one restores the active state and ticker. This is the "timer restore" `PlayerRuntime.start()` runs on the IO thread.

### Bluetooth disconnect pause

`BluetoothPlaybackCoordinator` (`:player`, implements `:domain`'s `BluetoothPlaybackMonitor`) collapses two auto-pause rules:

1. **Bluetooth disconnect pause**: `wasPlayingThroughBluetooth && !state.isA2dpConnected && isPlaying()` only then calls `pausePlayback()` — all three conditions ("was playing through Bluetooth, A2DP just dropped, still playing") must hold, protecting sessions that never went through Bluetooth;
2. **Audio-interruption pause**: pause when `onAudioInterrupted` fires while playing.

`isPlaying`/`pausePlayback` are constructor-injected lambdas (`PlayerRuntime` wires them to `_uiState.value.isPlaying` and `playbackBridgeFacade.pausePlayback()`), so the coordinator does not depend on the runtime. **Initialization is user-setting gated**: at startup it initializes only when the persisted switch is true; enabling it from the Settings page goes through `initializeBluetoothPlayback()` (writes the setting + updates uiState + initializes the graph). The architectural gate requires `PlayerStartupFacade` never mention Bluetooth, and the startup path never request Bluetooth permission.

### Session wrap-up (ViewModel destruction)

`PlayerRuntime.onCleared` → `PlayerLifecycleFacade.onCleared` wraps up in a fixed order: sync one controller snapshot → save playback state → release the playback controller → release Bluetooth monitoring → release the duration tracker (internally settles the playing track) → stop the progress ticker. The service-start side is wrapped in try/log so a failure never crashes.

## Phase 7: recovery loop after process death

Three **independent paths** cover the pre-crash/kill write (the 200ms progress-render beat itself never touches disk):

1. **Periodic fallback**: `PlayerPlaybackProgressTicker` (200ms, writes only the `positionMs` narrow flow) feeds the same tick into `persistenceGraph.onPlaybackPosition` → `PlayerPlaybackStateAutosaver` throttles to **every 5 seconds** (`DEFAULT_INTERVAL_MS`, crash-recovery granularity) measured by `SystemClock.elapsedRealtime`, executing `syncPlaybackState()` (pulls one latest controller snapshot to correct state) then `savePlaybackStateAsync`.
2. **Discrete-event save**: pause (only a real pause persists, buffering jitter does not), queue operations, song changes, etc. explicitly trigger `savePlaybackStateAsync` — it first captures the position **and the full UI state snapshot** on the calling thread, then moves the DataStore write to IO.
3. **Server-exit snapshot**: `AppMediaSessionService` in `onDestroy`/`onTaskRemoved` calls `saveCurrentPlaybackSnapshot`: songId and position are read **synchronously** on the calling thread (ExoPlayer is only accessible on its application thread), while the DataStore write is attached to the process-level `ApplicationScope` coroutine (the suspend `persistCurrentPlaybackSnapshot`) — which overwrites only `CURRENT_SONG_ID` and `PLAY_POSITION_MS` and **preserves the queue JSON** — so the write completes after the service instance is destroyed.

`PlaybackStateStore` also has an **empty-session protection** with three sub-branches when the queue is empty and infinite play is off:

- **existing queue JSON present + a current song**: keep the existing queue, update only the current song and position — the transient "all-empty and has currentSong" beat during a live-session reconnect would otherwise write the queue JSON as an empty array over the valid snapshot written 5 seconds earlier, and every later process kill would restore an empty queue;
- **no existing snapshot + a current song**: write an empty queue plus song and position, so restore falls back to a single-song queue by `currentSongId`;
- **all empty + no snapshot**: write nothing (also keeps the legacy migration fallback); explicit clearing (user clears the queue / ends playback) goes through `clearPlaybackState()`.

The post-restart loop returns to Phase 2's two branches:

- **Process dead and service dead**: controller queue empty → full-restore branch: queue and restore-point position are reloaded into the controller without autoplay (waiting for the user to press play), `isPlaying=false`.
- **Service still alive** (screen-off/background return, ViewModel rebuild): non-empty live session → only the UI queue shadow is backfilled; the real position and playing state come back via the first-frame snapshot sync, and the ticker is started by `syncControllerPlaybackState`'s isPlaying transition notification.
- **Stats do not break**: after restore the first `PlaybackStart` passes the snapshot position as `initialPlayedMs` into `PlayDurationTracker`, so pre-kill listening time counts toward settlement.
- **Timer does not break**: an unexpired sleep timer is restored by `restore()` countdown.

## Test anchors

Before changing any part this page covers, run the matching narrow verification (commands in `/openwiki/testing/test-map.md`):

- **`PlayerStartupFacadeTest`** — locks the exact boot-step order, including "restore read runs after the library's first snapshot".
- **`PlayerPersistenceFacadeTest`** — locks "async save captures the snapshot/position before moving to IO", full-restore application of queue and playable session, **the live-session UI-queue-only restore with the controller completely untouched** (position and isPlaying not overwritten), infinite-play state restore, and falling back to currentSong when the queue index is stale.
- **`PlayerPersistenceGraphTest`** — locks the restore handshake in both orders (restore-read-first vs controller-ready-first), live-session UI-only restore, connection-failure fallback releasing the decision, and no-snapshot applying nothing; this logic is the cluster for four past regressions.
- **`PlayerMediaEventFacadeTest`** — locks `handleMediaItemEnded`'s side-effect sequence: stop tracking/clear trackedSongId, tail and wrap triggering refill (including wrap-with-sufficient-remaining), non-infinite no refill, add-next mode restore with queue sync.
- **`PlayerPlaybackStateAutosaverTest`** — locks save throttling: first immediate, then throttled by interval, `reset` allowing the next.
- **`PlayerPlaybackSessionGraphTest`** — locks startQueuePlayback/prepareQueue/single playback session behavior.
- **`PlayerSleepTimerCoordinatorTest`** — locks the narrow-flow interaction regression: cancel zeroing must go through the explicit reset entry, start writes discrete state and persists endAt, play-last-song only sets pending instead of cancelling immediately, `onSongEnded` then pauses.
- **`MediaItemWrapDetectionTest`** (`:player`) — locks every index-wrap boundary: tail→0 hit, sequential-forward/single-back/unknown-previous-index/new-index-not-0 excluded, 2-song small-window backward treated as wrap (the refill planner de-duplicates automatically, so a false positive is harmless), empty/single-item windows can never wrap.
- Companion: `SingleItemLoopRewindDetectionTest` (`:player`) locks the single-item loop-wrap judgment's threshold boundaries and the "manual seek must not count as a finished play" anti-cheat case.

## Related pages

- `/openwiki/player/state-machine.md` — canon invariants for the playback state machine, snapshot mapping, and restore decisions.
- `/openwiki/player/queue-architecture.md` — the dual-queue model (business queue vs controller window) and per-operation sync flow.
- `/openwiki/player/random-and-infinite.md` — random/infinite refill planner semantics and `advanceAfterWrap`.
- `/openwiki/player/runtime-facades.md` — the `PlayerRuntime` composition root and the responsibility catalog of its facades.
- `/openwiki/player/service-and-controller.md` — server lifecycle, pending actions, event translation, and Bluetooth components.
