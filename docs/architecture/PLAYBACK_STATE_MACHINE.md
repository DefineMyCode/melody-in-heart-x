# Melody in Heart Playback State Machine

This document defines the product-level playback state model used around Media3. It is intentionally small: Media3 remains the source of truth for transport state, while the app state machine explains how service, controller, UI state, tracking, and persistence should react to those events.

## Ownership

| Layer | Owner | Responsibility |
| --- | --- | --- |
| Media session | `AppMediaSessionService` | Owns `ExoPlayer` and `MediaSession`; saves the last song/position when the service is destroyed or task is removed. |
| Controller adapter | `PlaybackController` | Connects to `MediaController`, converts Media3 callbacks into `ControllerPlaybackSnapshot`, and applies queue/play/pause/seek commands. |
| Domain synchronizer | `ControllerPlaybackStateSynchronizer` | Maps controller snapshots into app playback state, resolves `mediaId -> Song`, updates queue current index, and decides tracking start/pause transitions. |
| Feature facades | `PlayerControllerStateFacade`, `PlayerMediaEventFacade`, `PlayerPlaybackFacade` | Update `PlayerUiState`, playback statistics, restore-after-add-next state, infinite play refills, and playback persistence. |
| UI | `PlayerUiState` consumers | Render state only; UI must not infer Media3 state directly. |

## Canonical States

| App state | Media3 source | Meaning | Main side effects |
| --- | --- | --- | --- |
| `idle` | No connected controller, no current media item, or empty business queue | Nothing is ready to play. | Keep current UI selection if restoring; do not start tracking. |
| `preparing` | `setMediaItems`/`setMediaItem` followed by `prepare()` before a ready snapshot | A queue or single item has been sent to Media3 but playback is not ready yet. | Keep requested `PlayQueue` as business source; wait for controller snapshot before duration/stat updates. |
| `ready` | Controller has a current item and `STATE_READY`, but `isPlaying=false` | Media is prepared and can resume. | Persist position on pause/stop paths; do not count active play time. |
| `playing` | `onIsPlayingChanged(true)` or snapshot `isPlaying=true` | Current media item is actively playing. | Start or resume `PlayDurationTracker`; update current song, queue index, duration, and same-name versions from snapshot. |
| `paused` | `onIsPlayingChanged(false)` while not buffering and not ended | User/system paused playback. | Pause duration tracking and save playback state. |
| `buffering` | Snapshot reports `STATE_BUFFERING` | Playback is temporarily stalled by media loading. | Keep `isPlaying` transition from being treated as a user pause; do not save pause state just because buffering toggled playback. |
| `ended` | `STATE_ENDED`, end-of-media-item, or automatic item transition | Current item or controller queue reached an end boundary. | Stop current tracking, clear tracked song, restore add-next mode when needed, refill infinite queue near threshold, and reset position at queue end. |
| `error` | Controller connection failure or Media3/player exception surfaced to app code | Playback command or controller connection failed. | Log through `AppLog`/`AppLogger` only; release builds must avoid raw URI/path/device details. UI should expose a recoverable action when the failure affects playback. |

## Transition Rules

| From | Event | To | Notes |
| --- | --- | --- | --- |
| `idle` | App restores a saved queue or user selects a song | `preparing` | `PlaybackController.prepareQueue`, `playQueue`, or `playSingle` sends media to Media3. |
| `preparing` | Controller snapshot has a current `mediaId` and not playing | `ready` | The domain synchronizer resolves the song and aligns `PlayQueue.currentIndex`. |
| `preparing`/`ready`/`paused` | `onIsPlayingChanged(true)` | `playing` | `ControllerPlaybackStateSynchronizer.isPlayingTransition` resumes tracking only when there is a current song. |
| `playing` | `onIsPlayingChanged(false)` with `isBuffering=false` | `paused` | Save playback state and pause play-duration tracking. |
| `playing` | `STATE_BUFFERING` or `onIsPlayingChanged(false)` with `isBuffering=true` | `buffering` | Do not treat this as an intentional pause. |
| `buffering` | `onIsPlayingChanged(true)` | `playing` | Resume normal tracking without resetting the current song. |
| `idle`/`ready`/`paused` | Controller snapshot reports `isPlaying=true`（live session 采用） | `playing` | UI 状态重建（息屏/后台回来、配置变更、ViewModel 重建）但服务端 ExoPlayer 仍在播放时，Media3 不会为新建的 `MediaController` 补发 `onIsPlayingChanged`。该跃变由 `PlayerControllerStateFacade.syncControllerPlaybackState` 检测并补发 ticker 通知，详见下方 invariants。 |
| `playing`/`buffering` | `onMediaItemTransition(..., AUTO/SEEK/REPEAT)` | `playing` or `ended` | 自然结束（AUTO）、手动下一首/上一首（SEEK，含耳机/锁屏/通知栏）、单曲重复（REPEAT）都会进入 `PlayerMediaEventFacade.handleMediaItemEnded`：停止旧曲目跟踪、接近窗口尾部时补无限队列（repeat 回绕——上一首在窗口尾部且新位置回到索引 0——时强制补队列）、必要时恢复 add-next 播放模式。The started media id is used to stop old tracking and begin tracking the new song. |
| `playing`/`paused`/`buffering` | `STATE_ENDED` | `ended` | `PlayerMediaEventFacade.handlePlaybackEnded` sets `isPlaying=false` and position `0`. |
| any | Controller connection failure or command exception | `error` | Pending actions stay isolated; failures are logged and should not corrupt the business queue. |
| `ended`/`error` | User selects a valid item or queue is rebuilt | `preparing` | Recovery always goes through a fresh controller command. |

## Queue And State Invariants

- `PlayQueue` is the full business queue and remains the UI queue source.
- `MediaController` may hold only a playback window; UI current item must map by `Song.id`, not controller index alone.
- `MediaItem.mediaId` must be the decimal `Song.id`; non-numeric ids are ignored by `ControllerPlaybackStateSynchronizer`.
- `ControllerPlaybackStateSynchronizer` is the only place that maps controller snapshots back into `PlayerUiState`.
- Duration updates are accepted only from controller snapshots with a non-negative duration.
- Playback tracking starts once per song id and pauses only on a non-buffering transition to not playing.
- Playback state persistence is decoupled from UI progress ticks; progress rendering should not force frequent disk writes.
- System media keys must go through Media3/controller callbacks and then re-enter the same snapshot synchronization path as in-app controls.
- 进度位置的渲染只由 `positionMs` 窄流驱动，该窄流由 `PlayerPlaybackProgressTicker` 每 500ms 直写。`PlayerUiState.currentPositionMs` **故意不从 controller snapshot 刷新**（`applyControllerPlaybackState` 保留原值，避免整壳重组），因此 snapshot 同步本身**不会**推进进度显示。
- 进度 ticker 只在收到 `onIsPlayingChanged` 通知时启停。由此推出一条硬约束：**任何把 `isPlaying` 从 false 改成 true（或反向）的路径，都必须发出该通知**，否则 ticker 不启动、进度恒为 0 而歌曲实际在播。`PlayerControllerStateFacade.syncControllerPlaybackState` 正是为此在检测到本次同步发生播放状态跃变时补发通知——live session 场景下这是唯一能把 ticker 拉起的路径。
- `PlaybackController.listener.onEvents` 将 `onPlaybackSnapshot` 接成了**高频**回调（Media3 事件聚合钩子），故上述补发必须带「是否真的发生跃变」的判断，播放稳态下不得反复调用 `updateRunningState()`。
- 播放状态的恢复（`PlayerPersistenceGraph.restorePlaybackState`）**决策必须收敛到 MediaController 连接成功之后**（`onControllerReady`），以「controller 队列是否为空」为唯一判据：空 → 快照完整恢复（UI + 灌 controller，不自动播放）；非空（live session）→ 快照只回填 UI 队列影子、controller 队列绝不覆盖。读取 DataStore 快照可与连接并行，谁先完成都等另一方再决策——在 controller 状态确定前用 position/queueInfo 猜测 live 会话必然引入竞态。
- **UI 队列影子（`playQueue` 等）在任何情况下都应从快照恢复**：controller 的 snapshot 同步只调整 `currentIndex`、不会填充 `songs`，恢复时跳过 UI 队列会让播放队列重建后恒为空，无限播放状态（`isInfinitePlay`/`infinitePlayedSongIds`）一并丢失。

## Implementation Anchors

- `player/src/main/java/cn/com/dcsgo/mihx/data/player/AppMediaSessionService.kt`
- `player/src/main/java/cn/com/dcsgo/mihx/data/player/PlaybackController.kt`
- `domain/src/main/java/cn/com/dcsgo/mihx/domain/playback/ControllerPlaybackStateSynchronizer.kt`
- `feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerControllerStateFacade.kt`
- `feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerMediaEventFacade.kt`
- `feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPlaybackFacade.kt`
