# Playback Queue Architecture

本文档说明播放队列的职责边界和同步规则，避免后续维护时再次出现 App 内队列、系统控制队列和 UI 高亮状态各走各路的问题。

## 目标

播放顺序以 `MediaController` 中的 `MediaItem` 列表为最终事实来源。`PlayQueue` 保留完整业务队列和 UI 展示状态，窗口化规划层负责把业务队列转换为播放器窗口。

用户侧期望：

- App 内按钮、通知栏按钮、锁屏控制、蓝牙耳机按键的下一首/上一首行为一致。
- 顺序、倒序、随机模式切换后，真实播放顺序和 UI 展示同步。
- 添加到下一首后，手动下一首、耳机下一首、自动播放下一首都进入同一首歌。
- 歌单批量加入队尾允许重复追加；同一首歌在队列中出现多次时，UI、移除、保存/恢复和 Controller 队列都不能丢失队列项。
- 播放器切歌后，底部栏、歌单高亮、队列面板能跟随实际播放项。
- 后续支持大曲库窗口化加载时，UI 仍展示完整队列，播放器只持有轻量播放窗口。

## 核心职责

### PlayQueue

位置：`core/model/src/main/java/cn/com/dcsgo/mihx/core/model/PlayQueue.kt`

`PlayQueue` 是业务队列和 UI 队列状态：

- `songs`：完整业务队列，按 UI 展示顺序保存。
- `currentIndex`：当前歌曲在 `songs` 中的索引，供 UI 高亮和业务操作使用。
- `playMode`：当前播放模式。
- `playOrderIds`：按播放模式生成的歌曲 ID 顺序，用于描述期望播放顺序。

`songs` 允许包含重复歌曲。`playOrderIds` 仍保存歌曲 ID，但必须按出现次数解释：同一个 ID 出现两次表示两个队列项，不能用 `distinct()` 或 `associateBy(id)` 把它们合并。需要把播放顺序还原成队列项时，应使用 `PlayQueue.currentPlayOrderIndices()`。

`PlayQueue` 不再负责“下一首/上一首”导航决策。下一首和上一首由 `MediaController` 对当前 `MediaItem` 队列执行。

`PlayQueue.buildPlayOrderIds(...)` 保留默认顺序/倒序/普通随机规则。需要读取设置或播放次数的随机策略不要放进 `PlayQueue`，而应通过 `QueueManager.PlayOrderBuilder` 从装配层注入。

### Random and Uniform Random Planning

位置：`RandomQueuePlanner`、`UniformRandomPlanner`、`QueueManager.PlayOrderBuilder`

随机相关逻辑分两类：

- `RandomQueuePlanner`：负责“生成随机队列”和“无限播放补队列”的批量选歌。
- `UniformRandomPlanner`：负责全局均匀随机策略。开启后使用原始播放次数作为权重依据，优先选择播放次数较少的歌曲；关闭时调用普通 `shuffled()` 逻辑。

全局均匀随机开关由 `PlayerSettingsRepository` 保存在 Preferences DataStore 中，默认开启。`PlayerRuntime` 读取开关并通过 `PlayerUiState.globalUniformRandomEnabled` 提供给随机规划层。

开启全局均匀随机时：

- 普通随机队列和无限播放补队列先保留原有去重机制，再从低原始播放次数候选池中随机取歌。
- 普通 `PlayMode.SHUFFLE` 重建完整播放顺序时，当前队列项保持第一首，其余队列项按原始播放次数升序分桶，桶内随机；同一歌曲的其他重复队列项仍保留。
- 设置变化不立即重排当前队列，只影响之后生成或重建的随机顺序。
- 已持久化的 `playOrderIds` 按原样恢复，不因为设置变化而重算。

### MediaController

位置：`PlaybackController` 持有 Media3 `MediaController`，`PlayerMediaControllerGraph` 负责连接和事件路由。

`MediaController` 是实际播放顺序来源：

- App 内下一首/上一首调用 `seekToNextMediaItem()` / `seekToPreviousMediaItem()`。
- 通知栏、锁屏、蓝牙耳机等系统控制也通过 MediaSession/MediaController 作用到同一条队列。
- `MediaItem.mediaId` 必须使用 `Song.id.toString()`，用于播放器状态回写到业务队列。

重复队列项会共享同一个 `mediaId`。因此从播放器状态回写到 `PlayQueue.currentIndex` 时，如果当前业务索引已经指向相同 ID，应保留当前索引，避免重复歌曲自动跳回第一处出现位置。

当前实现会通过窗口化规划层生成 `ControllerQueuePlan`，再交给 `setMediaItems(...)`。业务队列仍是完整队列，但 Media3 只持有当前窗口。

### WindowedControllerQueuePlanner / ControllerWindowSynchronizer

位置：`player/src/main/java/cn/com/dcsgo/mihx/player/window/WindowedControllerQueuePlanner.kt`

窗口化规划分三层：

- `ControllerQueuePlanner`：先把完整业务队列按播放模式展开为完整可播放 Controller 队列。
- `PlaybackWindowPlanner`：在完整可播放队列上截取窗口，默认保留当前项前 `20` 首、后 `50` 首。
- `ControllerWindowSynchronizer`：缓存最近窗口，并在窗口失效或显式强制同步时生成新的 `ControllerQueuePlan`。

窗口规划仍保持这些规则：

- 读取 `PlayQueue.currentPlayOrderIndices()` 得到期望播放顺序。
- 按队列索引映射回 `Song`，保留重复歌曲的每一次出现。
- 过滤不可播放歌曲，默认规则是 `song.uri != null`。
- 根据请求播放的业务索引，计算窗口内的 `startIndex`。
- 当请求歌曲不可播放时，优先选择其后的可播放歌曲，若没有则回退到前一个可播放项。

不要把窗口计算散落到 UI 或多个 ViewModel 方法中；窗口边界和失效策略都应继续收敛在 `player/window` 这一层。

### SongMediaItemMapper

位置：`player/src/main/java/cn/com/dcsgo/mihx/data/player/SongMediaItemMapper.kt`

负责把 `Song` 转成 `MediaItem`：

- `mediaId` 固定为歌曲 ID 字符串。
- `uri` 来自 `song.uri`。
- 元数据写入标题、艺术家、封面 URI。

不要在队列构建阶段同步读取和压缩大封面数据；大队列下这会拖慢启动。

### PlayerViewModel 与拆分后的协调层

位置：`feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerViewModel.kt`

`PlayerViewModel` 现在是面向 UI 的薄门面，实际播放逻辑主要收敛在 `PlayerRuntime` 及其 facade/graph/coordinator 中：

- `PlayerQueueFacade`：设置队列、添加/移除歌曲、切换播放模式，并在必要时同步 Controller 队列。
- `PlayerPlaybackFacade`：播放歌曲、播放上下文队列、播放/暂停、上一首/下一首命令调度。
- `PlayerControllerQueueFacade`：调用 `WindowedControllerQueuePlanner` 构建和同步窗口化 Controller 队列。
- `PlayerControllerStateFacade` + `ControllerPlaybackStateSynchronizer`：把 Media3 当前 `mediaId`、播放进度、时长回写到 UI 状态和业务队列下标。
- `PlayerMediaEventFacade`：处理自动切歌、播放结束、无限播放补队列和“添加到下一首”后的播放模式恢复。
- `PlayerRandomQueueFacade` + `RandomQueuePlanner` + `UniformRandomPlanner`：随机播放、全局均匀随机和无限播放状态/补队列逻辑。
- `PlayerPersistenceFacade` + `PlaybackStateStore` + `PlaybackRestoreCoordinator`：保存和恢复播放队列、当前歌曲、进度、播放模式和无限播放状态。
- `PlayerPlaybackProgressTicker` + `PlayerPlaybackStateAutosaver`：播放中刷新 UI 进度，并定期保存轻量播放快照。
- `PlayerPlaybackSessionGraph` / `PlayerMediaControllerGraph` / `PlayerQueueGraph` / `PlayerPersistenceGraph` / `PlayerBluetoothGraph`：组装播放会话、MediaController 连接、事件路由、队列服务、持久化和蓝牙监听。

## 同步流程

### 从歌单或本地音乐开始播放

1. UI 传入上下文歌曲列表和目标歌曲。
2. `PlayQueue.setQueue(...)` 保存完整业务队列，并生成 `playOrderIds`；若当前模式为随机且开启全局均匀随机，装配层会通过 `QueueManager.PlayOrderBuilder` 重建低播放次数优先的随机顺序。
3. `WindowedControllerQueuePlanner.plan(...)` 生成可播放窗口化 Controller 队列和起始位置。
4. `MediaController.setMediaItems(...)` 设置真实播放队列。
5. `MediaController.prepare()` + `play()` 开始播放。

### 切换播放模式

1. `PlayQueue.setPlayMode(...)` 根据当前歌曲和新模式重建 `playOrderIds`；随机模式会通过注入的 `PlayOrderBuilder` 应用全局均匀随机策略。
2. `syncPlayerQueue(...)` 调用 Planner 重建 Controller 队列。
3. `MediaController` 中的 `mediaItems` 顺序成为新的真实播放顺序。

### 添加到下一首

1. `PlayQueue.addSongAsNext(...)` 更新完整业务队列和 `playOrderIds`。
2. `syncPlayerQueue(...)` 同步到 `MediaController`。
3. 下一首行为由 `MediaController.seekToNextMediaItem()` 决定。

批量“下一首播放”使用 `PlayQueue.addSongsAsNext(...)`：输入列表内部按歌曲 ID 去重，队列中已存在的目标歌曲会被移动到当前歌曲后面，当前播放项本身作为锚点保留。若当前队列为空，则用去重后的输入歌曲创建队列并从第一首开始。

当前实现仍会重建当前播放窗口，但目标歌曲会被放入新窗口并成为下一首。

### 添加到队尾

单首“加入播放队列”保持去重：如果歌曲已在队列中，不重复添加。

批量“加入队尾”用于歌单整单追加，允许同一首歌在队列中重复出现。追加后 `PlayQueue.songs`、`playOrderIds`、持久化 JSON 和 Controller 队列都应保留这些重复队列项；队列面板移除操作应按队列索引删除具体一项，而不是按歌曲 ID 删除所有或第一项。

### 系统或耳机触发切歌

1. 系统控制通过 MediaSession/MediaController 修改当前 `MediaItem`。
2. `Player.Listener.onEvents(...)` 触发 `syncControllerPlaybackState(...)`。
3. ViewModel 通过 `mediaId` 找到歌曲 ID。
4. 更新 `currentSong` 和 `PlayQueue.currentIndex`，UI 跟随实际播放项。

### 无限播放补队列

1. 开启无限播放时，不替换当前播放队列，只记录当前队列中已覆盖的可播放歌曲 ID。
2. 自动切歌或手动下一首时，如果 Controller 队列接近尾部，触发 `PlayerRandomQueueFacade.refillInfinitePlayQueue(...)`。
3. 自动切歌路径会传入 Media3 刚开始播放的 `startedSongId`。
4. 补队列前先用 `startedSongId` 校正 `PlayQueue.currentIndex`，避免播放器已经切到 B，但 UI 队列还停在 A 时，补队列同步又跳回 A。
5. `RandomQueuePlanner` 从尚未覆盖的本地可播放歌曲中选取一批追加到队尾；可选歌曲不足时重置覆盖历史，尽量循环覆盖全库。开启全局均匀随机时，候选集合仍保留该覆盖历史，再优先选择原始播放次数较低的歌曲。

### 播放状态持久化

1. 暂停、队列变化、版本切换、ViewModel 清理时会保存播放状态。
2. 播放中 `PlayerPlaybackProgressTicker` 刷新进度，`PlayerPlaybackStateAutosaver` 以节流方式定期保存当前歌曲和位置。
3. `AppMediaSessionService.onTaskRemoved/onDestroy` 会在系统提供回调时，从 ExoPlayer 直接保存最后的 `mediaId` 和 `currentPosition`。
4. 恢复时优先使用持久化的 `currentSongId` 校正 `PlayQueue.currentIndex`；如果当前歌不在旧队列中，会用这首歌构建可恢复队列。
5. 恢复后的播放器只 prepare 到对应位置，不自动播放。
6. 保存/恢复含重复歌曲的队列时，`songIds` 和 `playOrderIds` 都按出现次数保留；恢复时不能对播放顺序做去重。

## 必须保持的规则

- `PlayQueue.currentIndex` 永远表示 `PlayQueue.songs` 的业务索引，不表示 Controller 队列索引。
- `MediaController.currentMediaItemIndex` 只表示播放器队列索引，不直接用于 UI 业务列表定位。
- `Song.id` 和 `MediaItem.mediaId` 必须一一对应。
- `PlayQueue.songs` 允许重复歌曲；任何队列规划、保存/恢复、UI key、删除操作都必须按队列项或索引处理，不能假设 `Song.id` 在队列内唯一。
- 改变播放顺序后必须经过 `WindowedControllerQueuePlanner` 或同层规划器同步到 `MediaController`。
- 需要参考设置或播放次数重建随机顺序时，必须走 `QueueManager.PlayOrderBuilder` / `UniformRandomPlanner`，不要让 `PlayQueue` 直接依赖仓库。
- 下一首/上一首不要重新引入 `PlayQueue.nextIndex()` / `previousIndex()` 这类领域导航 API。
- 添加、移除、切换播放模式后，如果当前正在播放，应保留当前播放位置并同步 Controller 队列。
- 不可播放歌曲不能进入 Controller 队列，但完整业务队列可以保留它们用于 UI 展示和错误提示。

## 当前已完成

- 播放器队列构建逻辑已从 `PlayerViewModel` 抽到 `WindowedControllerQueuePlanner` / `ControllerWindowSynchronizer`。
- `PlayerViewModel` 的队列播放、队列准备和队列同步都通过 Planner 生成窗口化 Controller 队列。
- `PlayQueue` 中旧的下一首/上一首导航 API 已移除。
- 已补充 `ControllerQueuePlannerTest`，覆盖顺序、倒序、添加到下一首、切换模式、不可播放歌曲跳过等规则。
- `SongMediaItemMapper` 使用 `setArtworkUri(song.albumArtUri)`，避免大队列构建时同步解码封面字节。
- `PlayerViewModel` 已拆分为多个 facade/graph/coordinator，核心播放、队列、随机、持久化、MediaController 同步逻辑均有独立测试。
- 无限播放已改为“保留当前队列，接近队尾时追加补充”，并修复自动切歌补队列后跳回上一首的问题。
- 已新增全局均匀随机设置，支持随机队列、无限播放补队列和 `SHUFFLE` 播放顺序重建时优先选择原始播放次数较少的歌曲。
- 播放状态持久化已包含当前歌曲、播放位置、播放模式、播放顺序、无限播放状态和已覆盖歌曲集合。
- 播放中会定期保存轻量快照，用于应对部分系统直接结束进程而不触发生命周期回调的场景。
- 歌单详情页已支持整单加入队尾和整单下一首播放；批量队尾允许重复追加，队列面板按队列索引移除具体项。

## 尚未完成

- 当前窗口化策略是固定窗口，还没有做更细粒度的边界预取和增量更新。
- 添加到下一首、切换播放模式、无限播放补队列时，当前实现仍会通过 `setMediaItems(...)` 重建整个当前窗口，而不是在窗口内部做更细颗粒度的插拔。
- `MusicRepository` 已经拆出 `RoomMusicLibraryDataSource`，但导入扫描、封面刷新、SAF 删除、内存态协调仍可继续拆薄。
- 真机厂商系统对任务管理清应用的生命周期行为差异较大，播放中恢复仍应继续积累日志和回归测试。

## 后续窗口化方向

当前窗口化队列的设计目标是：

- `PlayQueue` 保存完整 500+ 首业务队列。
- `MediaController` 只保存当前歌曲附近的轻量窗口，例如当前前 20 首、后 50 首。
- UI 队列面板仍展示完整 `PlayQueue.songs`。
- 当播放器接近窗口边缘时，重新规划窗口并同步到 `MediaController`。

窗口化实现需要额外维护：

- Controller 窗口在完整 `playOrderIds` 中的起点。
- Controller 当前项映射回完整业务队列的规则。
- 窗口重建时当前播放位置和播放状态的保留。
- 添加到下一首时确保目标歌曲进入当前窗口下一位。

## 修改播放队列前的检查清单

改动播放队列相关代码前，至少确认：

- 是否改变了 `PlayQueue.songs`、`currentIndex`、`playOrderIds` 三者关系。
- 是否需要同步 `MediaController` 队列。
- 是否会影响通知栏、锁屏或蓝牙耳机控制。
- 是否会影响随机、倒序、添加到下一首、无限播放补队列。
- 是否需要新增或更新 `ControllerQueuePlannerTest`、`RandomQueuePlannerTest`、`UniformRandomPlannerTest`、`Player*FacadeTest` 或持久化相关测试。
- 大队列场景是否会引入新的全量 `setMediaItems(...)` 或同步封面读取。
