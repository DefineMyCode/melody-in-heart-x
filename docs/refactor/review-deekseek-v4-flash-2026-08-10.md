# Melody in Heart 代码评审报告

> 评审日期：2026-08-10 ｜ 基准版本：`5c50508`（v3.3.0 / versionCode 22）
> 评审方式：静态代码审查（272 个 Kotlin 文件 / 约 34.5K 行）。本环境无 JDK 与 Android SDK，
> 无法重跑 Gradle 门禁；构建/测试通过状态以仓库内 [PRODUCT_REFACTOR_AUDIT.md](PRODUCT_REFACTOR_AUDIT.md)
> 记录为准。本报告侧重**代码级问题定位**，与既有审计文档互补。

## 一、总体评价

项目经过上一轮产品化重构后，已经从「单模块功能型应用」进化为「多模块、有架构门禁、测试覆盖积极的产品型应用」，整体质量显著高于同类规模项目。以下结论值得首先明确：

- **架构纪律好**：模块边界由 `verifyProductArchitecture`（根 `build.gradle.kts`，566 行）强制约束，feature 不依赖 `:data`/`:player`、无跨 feature 依赖、Route/Screen 模式统一、Room schema 不可变、LazyColumn key 强制、日志统一走 `AppLog`。
- **播放器核心成熟**：窗口化队列（±20/50 有界窗口 + fingerprint 短路）、`ControllerPlaybackStateSynchronizer` 单点状态同步、窄流 `positionMs` 驱动进度、`@Stable` 模型减少重组，配套测试完整。
- **工程化完整**：release 开启 R8 + 资源压缩、arm64-only、Baseline Profile + Macrobenchmark、日志脱敏、备份规则排除、权限按需申请、56 个单测文件 + 4 个仪器测试。
- **主要遗留问题集中在数据层**：Room/DataStore 迁移保留了旧同步接口风格，生产代码中仍存在 **40 处 `runBlocking`**，其中多处在**主线程热路径**上（切歌、随机队列构建、统计页组合、设置开关、服务销毁），是本轮评审最需要优先治理的问题；另有少量 N+1 查询、静默吞异常与职责边界残留。

结论先行：**当前代码「可维护性」已达标，需要关注的是「运行期流畅性」与「故障可观测性」**。

## 二、问题摘要表

| # | 严重度 | 问题 | 位置 |
|---|--------|------|------|
| 1 | P1 | 统计页组合期间主线程阻塞执行 8 个 Room 查询 | `AppNavHost.kt:692/768/800` → `PlayStatsRepository.playbackStatsSnapshot()` |
| 2 | P1 | 每次切歌主线程阻塞执行 3~6 次 Room 事务 | `PlayDurationTracker.stopPlayback` → `PlayStatsRepository`/`QuickSkipSongsRepository` |
| 3 | P1 | 随机队列构建按歌逐首 `runBlocking` 查询（N+1） | `PlayerRuntime.kt:316` → `PlayStatsRepository.getRawPlayCounts` |
| 4 | P1 | 服务 onDestroy/onTaskRemoved 主线程 `runBlocking` DataStore | `AppMediaSessionService.kt:117/122` |
| 5 | P1 | 仓库构造器内 `runBlocking` 查询（启动主线程） | `QuickSkipSongsRepository.kt:36-49` |
| 6 | P2 | `syncLibraryCatalog` 循环内全表查询 + 逐首 upsert（N+1） | `MelodyDao.kt:325-334` |
| 7 | P2 | 持久化异常被静默吞掉，无日志 | `PlaybackStateStore.kt:85-87/103-105/120-122` |
| 8 | P2 | 歌单续播"读来源→写记录"非原子，存在竞态 | `PlaylistResumeDataStore.kt:80-89` |
| 9 | P2 | `AppNavHost.kt` 834 行：导航装配与业务逻辑混合 | `AppNavHost.kt` |
| 10 | P2 | `core:model` 非纯：领域模型携带 drawable 资源 id | `PlayQueue.kt:14/17/20`、`Playlist.kt:16` |
| 11 | P2 | ViewModel 直接暴露 Repository，UI 装配层直连数据层 | `PlayerViewModel.kt:32` |
| 12 | P2 | `MusicRepository` 854 行 + JSON/Room 双路径残留 | `MusicRepository.kt` |
| 13 | P2 | 封面刷新持写锁串行磁盘 IO | `MusicRepository.kt:348-366` |
| 14 | P2 | `pendingActions` 无界队列，连接失败动作静默丢弃 | `PlaybackController.kt:153-160/284-292` |
| 15 | P3 | `VersionComparisonComponents.kt` 1035 行单体 | `feature/user/.../VersionComparisonComponents.kt` |
| 16 | P3 | UI 字符串全部硬编码 + `resConfigs("zh","en")` 名不副实 | 各 feature 文件 + `app/build.gradle.kts:38` |
| 17 | P3 | `PerformanceTrace` 经 `AppLog.info` 输出，release 被整体禁用 | `AppLogger.kt:46-49`、`PerformanceTrace.kt:21` |

严重度说明：P1 = 影响运行时流畅性/ANR 风险，建议近期修复；P2 = 性能或健壮性隐患；P3 = 结构或一致性改进。

## 三、架构与模块化

### 亮点

- 模块图（`:app` → `:feature:*` + `:core:*` + `:domain` + `:data` + `:player`）清晰，`verifyProductArchitecture` 将关键约束固化为构建门禁，防止回归。
- `:domain` 已承担播放策略（planner / synchronizer / coordinator），纯 Kotlin 无 Android 依赖，测试成本低。
- Hilt 装配通过窄适配器绑定领域接口，`MusicRepository` 不再直接实现多个领域接口。
- feature 全部采用 Route（公开，收 State+Actions）+ Screen（internal）模式，跨页路由集中在 `:app`。

### 残余问题

- **`AppNavHost.kt`（834 行）重新成为"大装配文件"**。它同时承担：路由表、转场动画、每个页面的 State/Actions 装配、以及 `rankedStatsContent`/`resolveResumeSong`/`userRouteState`/`playbackStatsRouteState` 等**业务派生逻辑**（`AppNavHost.kt:687-834`）。建议将后者的纯派生函数下沉到 feature 内部或专用 mapper，保持 NavHost 只做路由。
- **`PlayerViewModel` 公开了 `playStatsRepository`**（`PlayerViewModel.kt:32`），`AppNavHost` 直接调用 `playerViewModel.playStatsRepository.playbackStatsSnapshot()`。这破坏了"UI 只面向 facade"的既定约束（`CLAUDE.md` 主张 ViewModel 是薄门面）。且该调用是同步阻塞的（见问题 1）。建议改为 `StateFlow<PlaybackStatsSnapshot>` 或 suspend API。
- `core:model` 并非文档所称的"零依赖纯数据"：`PlayMode` 携带 `R.drawable.*`（`PlayQueue.kt:14/17/20`），`Playlist.coverArt` 使用 `android.R.drawable`（`Playlist.kt:16`），模块内还有 `res/drawable` 与 compose-runtime 依赖。领域枚举绑定 UI 资源 id 是分层倒退，建议把"模式→图标/文案"映射移到 `core:ui` 或 feature。

## 四、数据层与持久化（本轮重点）

### 4.1 `runBlocking` 主线程阻塞（P1，40 处）

Room/DataStore 迁移保留了旧 SharedPreferences 时代的**同步接口风格**：领域接口仍是 `fun`，实现内部用 `runBlocking(Dispatchers.IO)` 包住挂起操作。部分调用点位于**主线程热路径**：

1. **统计页组合期间阻塞**（问题 1）
   `AppNavHost.kt:692/768/800` 在 Compose 组合阶段调用 `playbackStatsSnapshot()`，其内部以 `runBlocking(Dispatchers.IO)` 串行执行 **8 个 DAO 查询**（`PlayStatsRepository.kt:146-184`，含 4 次 `totalDurationBetween` + `playCountsBetween` 等）。进入「我的」Tab、统计页、TOP 榜都会在主线程冻结等待，播放事件多时明显卡顿。
   建议：`playbackStatsSnapshot` 改为 `suspend`，由 ViewModel 在 IO 上加载并以 StateFlow 输出；或至少包一层 `produceState`/`LaunchedEffect`。

2. **每次切歌主线程阻塞写**（问题 2）
   Media3 监听器注册在主执行器上（`PlaybackController.kt:117`），`PlayerMediaEventFacade`/`PlayerControllerStateFacade` 随之在主线程调用 `PlayDurationTracker.stopPlayback()`，其中串行执行：
   - `playStatsRepository.increment()` → `writeStat` → `runBlocking { dao.upsertPlayStat }`（`PlayStatsRepository.kt:235-250`）
   - `playStatsRepository.recordPlaybackSession()` → `runBlocking { dao.insertPlaybackEvent }`（`:121-131`）
   - `quickSkipRepository.contains/remove/incrementShortPlayCount/resetShortPlayCount` → 每个都是独立 `runBlocking` Room 事务（`QuickSkipSongsRepository.kt:75-184`）

   一首歌切换可累积 3~6 次主线程阻塞事务。建议把统计结算整体搬到后台（`PlayDurationTracker` 已有自己的 IO scope，可在其中 `launch`），接口改为 suspend。

3. **随机队列构建 N+1**（问题 3）
   `playRandomQueue()`/`refillInfinitePlayQueue()`（UI 点击 → `PlayerRandomQueueFacade.kt:30/97`）在主线程调用 `getRawPlayCounts(songs.map{id})`，实现为 `songIds.associateWith { getRawPlayCount(it) }`，每首歌一次 `runBlocking { dao.playStat(songId) }`（`PlayStatsRepository.kt:107-108` + `225-233`）。千首曲库 = 千次串行 Room 往返，点击「随心播放」可冻结数秒。建议改为单条 `SELECT * FROM play_stats WHERE songId IN (...)` 批量查询。

4. **服务销毁路径主线程阻塞**（问题 4）
   `AppMediaSessionService.onDestroy()`/`onTaskRemoved()`（`AppMediaSessionService.kt:114-126`）在主线程执行：
   - `PlaybackStateStore.saveCurrentPlaybackSnapshot()` → `runBlocking` + `store.data.first()`（`PlaybackStateStore.kt:90-106`）
   - `PlaylistResumeDataStore.recordCurrentSourceBlocking()` → 两次 `runBlocking`（读来源 + 写记录，`PlaylistResumeDataStore.kt:80-89`）

   后台服务被系统回收时阻塞主线程有 ANR 风险。建议 fire-and-forget（application-scope 协程）或先取快照后异步落盘。

5. **构造器阻塞**（问题 5）
   `QuickSkipSongsRepository` 是 `@Singleton`，`init` 中 `runBlocking { dao.quickSkipSongs() }`（`QuickSkipSongsRepository.kt:36-49`）。首次注入发生在 `PlayerViewModel` 创建（主线程）时，给启动路径额外增加一次阻塞查询。建议惰性加载或 suspend 初始化。

6. **设置开关与定时器阻塞**（P2 级同类问题）
   `PlayerRuntime.setGlobalUniformRandomEnabled` 等（`PlayerRuntime.kt:660/665/670/676`）与 `PlayerSleepTimerCoordinator.start/cancel`（`:54-55/70`）在 UI 点击路径上调用 `*Blocking` DataStore 写。单次通常很快，但仍是主线程阻塞；建议统一改为 suspend + `viewModelScope.launch`。

> 根因建议：数据层为领域接口提供 **suspend 版本**作为主路径，阻塞 API 只保留给测试与迁移；所有"写入即忘"场景用一个共享的 application-scope IO 协程封装，避免 `runBlocking` 在主线程扩散。

### 4.2 其他数据层问题

- **`MelodyDao.syncLibraryCatalog` N+1**（问题 6，P2）：`songs.forEach { ... songs().first { it.id == song.id } ... }`（`MelodyDao.kt:325-334`）——每个需要更新 albumId 的歌曲都重新 `SELECT * FROM songs` 全表，且逐首 `upsertSongs`。该函数在每次 `persistSongs()`（导入、封面刷新）与每次启动 `restore()` 时执行，大曲库下是 O(n²) 级开销。建议先在内存建立 `id → entity` 映射，再一次性批量 upsert。
- **静默吞异常**（问题 7，P2）：`PlaybackStateStore.save/saveCurrentPlaybackSnapshot/clear` 的 `catch (e: Exception) { /* Persistence failures should not break playback controls. */ }`（`PlaybackStateStore.kt:85-87/103-105/120-122`）完全不记录日志，播放状态恢复若持续失败将无从排查。建议至少 `AppLog.error`（保留不打断播放的语义）。
- **JSON/Room 双路径残留**（问题 12，P2）：`MusicRepository` 的 `restoreSongs/restorePlaylists/persistSongs/persistPlaylists` JSON 分支（`MusicRepository.kt:147-262`）在 Hilt 装配下不可达（`melodyDao` 必非空），仅测试可达。`PlayStatsRepository`/`QuickSkipSongsRepository` 同样保留 legacy SharedPreferences 分支。这些死路径增加审查与维护成本，建议在迁移验证稳定后删除（保留旧 JSON 的**只读迁移**逻辑即可）。

### 4.3 播放统计正确性观察

- `recordPlaybackSession` 的 `startedAtMs = System.currentTimeMillis() - totalDuration`（`PlayDurationTracker.kt:120`）在**跨天**播放（如 23:50 开始、次日 00:20 切歌）时会把整段时长记到次日，与 `dailyDurationsBetween` 按 `startedAtMs` 聚合的语义不一致；且暂停跨天后恢复不重算 `startedAtMs`。属边缘正确性问题，建议后续按"当日实际播放时长"切片或明确文档化。
- `PlaybackStateSnapshotSerializer.decodeQueue` 中 `(restoredOrderIds + queue.currentPlayOrderIds()).take(matchedSongs.size)`（`PlaybackStateSnapshotSerializer.kt:64-66`）依赖"恢复顺序足够长"的隐式假设，若恢复顺序部分缺失会截断在错误位置；已有序列化测试覆盖主路径，建议补充"playOrderIds 部分缺失"用例。

## 五、播放器与队列

### 亮点

- 窗口化队列落地完整：`PlaybackWindowPlanner`（前 20 / 后 50）、`ControllerWindowSynchronizer.planIfNeeded`（当前歌仍在窗口内则跳过重规划）、`PlaybackController.syncQueue` 的 fingerprint 短路（`PlaybackController.kt:254-274`），配合 `PlaybackWindowPerformanceShapeTest` 覆盖 100/500/1000 队列。
- `isMediaItemWrap`（`PlaybackController.kt:340-349`）对 `REPEAT_MODE_ALL` 回绕的索引判定有详细注释与测试，无限随机播放的补队列逻辑（`PlayerRandomQueueFacade.refillInfinitePlayQueue`）考虑到了媒体键切歌与回绕两种路径。
- 状态同步单点化：`ControllerPlaybackStateSynchronizer` 是 Media3 snapshot → `PlayerUiState` 的唯一映射入口，`PlayerControllerStateFacade`/`PlayerMediaEventFacade` 分层清晰。

### 观察

- **`pendingActions` 无界队列**（问题 14，P2）：`PlaybackController.runWhenConnected` 在未连接时把动作加入 `ArrayDeque`（`:153-160`），若 `MediaController` 连接失败（`:111-115` 仅记录日志），队列永不清空且动作静默丢弃（调用方拿到的 `Boolean` 常被忽略）。建议：连接失败时清空队列并回调错误；对 pendingActions 设上限（如 64）。
- **窗口规划 `force=true` 全量重算**（观察）：`WindowedControllerQueuePlanner.plan` 每次 `force=true` 使 `lastWindow` 失效后重规划（`WindowedControllerQueuePlanner.kt:10-16`），窗口内容会整体重建，但受 fingerprint 短路与有界窗口保护，`setMediaItems` 规模有界（≤71 项），当前可接受；若后续追求极致可改为增量窗口调整。

## 六、并发与竞态

- **歌单续播读-写竞态**（问题 8，P2）：`PlaylistResumeDataStore.recordCurrentSourceBlocking` 先 `currentSourcePlaylistId()`（一次 DataStore 读）再 `store.edit` 写入（`PlaylistResumeDataStore.kt:80-89`），两次操作非原子；`PlaylistResumeViewModel.switchSource`（`:35-43`）同样"读旧来源→结算→写新来源"分步执行。快速连续切换播放源（如连点歌曲）可能丢失结算或写错歌单。建议把"读来源+结算+清标记"合并为单次 `store.edit` 原子操作。
- **`MusicRepository.refreshAllAlbumArtInternal` 持写锁做磁盘 IO**（问题 13，P2）：全库封面检查在 `lock.write` 内串行执行（`MusicRepository.kt:348-366`），Compose 重组时主线程 `getSongs()` 的读锁会被长时间阻塞。建议：读锁快照 → IO 并发检查 → 写锁合并结果。
- **锁粒度**：`addFolder` 中每首歌 `lock.write { songId = nextId++ }`（`:466`）为短临界区，合理；导入整体已使用 `Semaphore(8)` + 单线程持久化队列（`persistDispatcher`），并发设计值得肯定。

## 七、UI 与 Compose

- **`AppNavHost` 过大**（见问题 9）：834 行中约 150 行是页面装配，其余是业务派生。建议拆分。
- **`VersionComparisonComponents.kt`（1035 行）**（问题 15，P3）：版本对比页组件单体，建议按「差异表 / 文件路径 / 进度条 / 播放控制」拆分为独立组件文件。
- **窄流与稳定性标注**：`positionMs` 独立窄流（`PlayerRuntime.kt:139-171`）、`updateUiState` 仅在离散事件时回写位置、`@Stable`/`@Immutable` 标注，是本项目 UI 性能治理最成功的部分，建议保持并继续推广。
- **硬编码字符串**（问题 16，P3）：除 `app_name`/`app_introduction` 外 UI 文案全部为源码中文硬编码，而 `app/build.gradle.kts:38` 声明 `resConfigs("zh","en")`。若坚持中文单语产品，建议把注释改为"仅中文"，避免误导；若未来要出海，需要补 `strings.xml`。
- **大列表 key**：`verifyProductArchitecture` 已强制 LazyColumn key，抽查未见缺失。

## 八、构建与工具链

- 早期问题（AGP alpha、`isMinifyEnabled=false`、依赖散落）已修复：AGP 8.13.2 稳定版、R8 + 资源压缩、Version Catalog、`benchmark` build type、`check` 聚合 spotless + 架构门禁。
- **release 签名回退**（观察）：`keystore.properties` 缺失时 release 静默回退 debug 签名（`app/build.gradle.kts:59-66`）。对个人项目方便，但 CI 打包时可能产出 debug 签名的"正式包"。建议在 CI/发布脚本中显式要求 `keystore.properties` 存在，否则构建失败。
- **release 性能可观测性缺失**（问题 17，P3）：`AndroidAppLogger.info` 受 `debugLoggingEnabled` 控制（`AppLogger.kt:46-49`），`PerformanceTrace.log` 全部走 `AppLog.info`（`PerformanceTrace.kt:21`），因此 **release 构建完全没有性能痕迹**。若为隐私/体积决策可接受，但应修正审计文档中"性能可观测性存在"的表述，或提供 release 白名单开关。

## 九、测试质量

- **强项**：56 个 JVM 测试文件 + 4 个 Compose 仪器测试；planner/facade 纯 Kotlin 测试风格性价比最高；窗口形状、序列化兼容、迁移损坏恢复、权限策略均有覆盖；`verifyProductArchitecture` 与 `spotlessCheck` 纳入 `check`。
- **缺口**：
  1. **无主线程阻塞/性能回归测试**：本次发现的主线程 `runBlocking` 热路径（统计页、切歌结算、随机队列构建）没有任何测试或基准兜底，且重构时极易回归。建议至少为 `playbackStatsSnapshot`/`getRawPlayCounts` 增加"调用耗时/线程"性质的测试，或纳入 Macrobenchmark 场景。
  2. **服务端到端仍靠手测**：Media3 系统媒体键链路、通知栏/锁屏控制、`onTaskRemoved` 路径没有设备级测试（审计文档已承认）。
  3. `resolveResumeSong` 已有测试；`PlaylistResumeDataStore` 竞态场景（并发 switchSource）建议补充。

## 十、安全与隐私

- **好**：`AppLog` 对 `content://`、`file://`、蓝牙 MAC、路径、设备名的正则脱敏（`AppLogger.kt:77-86`）并有测试；备份规则排除 Room/DataStore/封面缓存/播放状态；权限（通知/蓝牙）改为用户从设置页按需触发；`allowBackup` 相关已治理。
- **注意**：物理删除 SAF 文件（`MusicRepository.deleteBackingFile`，`:794-817`）为不可逆高风险操作，当前依赖 UI 文案确认；建议删除对话框明确"同时删除本地文件"并给出失败原因（现有 `DeleteSongResult` 已带消息，UI 侧请确保都展示）。

## 十一、优先级路线图

### 第一阶段：消除主线程阻塞（P1，收益最大）
1. `playbackStatsSnapshot` → suspend/StateFlow，统计页移出组合期阻塞。
2. 切歌结算（播放统计 + 秒切）整体移到后台，领域接口提供 suspend 版本。
3. `getRawPlayCounts`/`getCounts` 改为单条批量 SQL。
4. 服务 `onDestroy/onTaskRemoved` 路径改异步落盘。
5. `QuickSkipSongsRepository` 构造器惰性化。

### 第二阶段：数据层健壮性（P2）
6. `syncLibraryCatalog` 消除 N+1（内存映射 + 批量 upsert）。
7. `PlaybackStateStore` 捕获异常时记录日志。
8. `PlaylistResumeDataStore` 读-写合并为原子操作。
9. 删除 JSON/SharedPreferences 生产死路径（保留只读迁移）。

### 第三阶段：结构与一致性（P2/P3）
10. 拆分 `AppNavHost` 的业务派生逻辑；`PlayerViewModel` 不暴露 repository。
11. `core:model` 去除 UI 资源 id 依赖。
12. 拆分 `VersionComparisonComponents`；`PlaybackController.pendingActions` 加限流与失败回调。
13. 明确 release 性能可观测性策略；发布脚本强制正式签名。

## 十二、结论

这是一个**架构底子好、工程质量高**的项目：模块边界有门禁、播放器核心有测试与文档、性能治理（窄流、窗口队列、Baseline Profile）已系统性落地。当前最值得投入的不是"继续加功能"，而是**把数据层的同步阻塞接口从主线程热路径上彻底清除**（40 处 `runBlocking` 中的 P1 五类），以及**让失败可观测**（静默 catch、release 无性能数据）。完成第一阶段后，本项目在"长期可维护性"与"运行期流畅性"两个维度都将达到较高完成度。
