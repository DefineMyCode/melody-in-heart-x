# 代码评审报告 — melody-in-heart v3.15.0

| 项目 | 内容 |
|------|------|
| 评审日期 | 2026-11-20 |
| 评审模型 | Spark X2.6 |
| 代码版本 | versionName 3.15.0 (versionCode 54)，HEAD `7ddd5e2` |
| 评审方法 | 静态阅读 + grep 规则扫描 + 架构门禁核对 + 对疑点做逻辑模拟。**未运行**构建/测试（本机 shell 无 JDK）。三路子代理并行深度审查 `:player`、`:data`/`:domain`、`:app`/`:feature` 模块全部源码。 |

> **与上一轮评审（2026-10-15）的关系**：上一轮发现并修复的多项问题在本版本中已验证落地——M-1（计时协程泄漏）、M-3（内嵌歌词解析）、M-4（删歌统计清理与 ID 复用）、M-5（runBlocking 同步桥）、M-6（发版流水线无校验）、C-1（loadSongInfo 崩溃）、M-19/M-20（日志脱敏与性能痕迹）、N-1（PlaybackStateStore runBlocking）、M-10（队列规划 O(n²)）、M-13（歌手拆分重复计算）等均有回归测试或架构门禁固化。本报告聚焦**当前版本仍存在的问题**与**新增功能（音质分级、空间音频、多版本管理、存储统计、时段排除词条）引入的潜在风险**。

## 0. 阅读覆盖范围

| 范围 | 深度 |
|------|------|
| 根构建（`build.gradle.kts`）、版本目录（`libs.versions.toml`）、`settings.gradle.kts` | 通读 |
| `.github/workflows/pr-check.yml`、`release.yml` | 通读 |
| `app/build.gradle.kts`、`app/proguard-rules.pro`、`gradle.properties` | 通读 |
| `buildSrc` 架构验证（`ProductArchitectureVerification.kt` + 其测试） | 通读 |
| `:core:model`（Song / PlayQueue / SongEmotion / AudioQualityTier / EmotionModel / Lyrics） | 通读 |
| `:core:common`（AppLogger / PerformanceTrace / ArtworkUriAccess / CoroutineDispatchers） | 通读 |
| `:core:ui`（theme / LyricsView / SongInfoLoader / SongListItem / AudioSpecBadges / EmotionCorrection / AddToPlaylistDialogs） | 通读 |
| `:core:skin`（SkinParser / Skin / DefaultSkin） | 通读 |
| `:benchmark`（build + BaselineProfileGenerator） | 通读 |
| `:player` 全部 26 个源文件 | 子代理逐文件深度审查 |
| `:data`（实体/DAO/数据库/迁移/仓储/工具/DI）+ `:domain`（接口/规划/协调器）+ schema 导出 | 子代理逐文件深度审查 |
| `:app`（导航/外壳/DI/权限/构建/Manifest/备份规则）+ `:feature` 全子模块 | 子代理逐文件深度审查 |
| 架构文档（PLAYBACK_QUEUE_ARCHITECTURE / PRODUCT_REFACTOR_AUDIT / fix-log） | 通读 |

## 1. 总览

| 维度 | 评级 | 结论 |
|------|------|------|
| 架构与分层 | A | 模块边界清晰，Facade/Graph/Coordinator 拆分落地；架构门禁（棘轮式）防回潮；`MusicRepository` 已拆出 `RoomMusicLibraryDataSource`，但仍是内存+落盘一体化大类 |
| 正确性 | B | 上一轮核心缺陷均已修复并有回归测试；当前发现多处并发协议与状态同步缺陷，部分影响播放正确性与数据一致性 |
| 并发/性能 | B | `src/main` 零 `runBlocking`（架构门禁保证）；窄流/StateFlow 避免整壳重组；但播放控制器、时长跟踪、持久化图中存在多处线程安全与协程泄漏问题 |
| 安全/隐私 | A- | 无网络层；日志脱敏；备份排除完整；密钥未入库；CI 按 SHA 固定；服务非导出；`READ_EXTERNAL_STORAGE` 被门禁禁止 |
| 测试 | B+ | 116 个测试文件，核心播放/队列/数据层覆盖好；`EmotionAnalyzer`、`FfmpegPcmDecoder`、蓝牙、部分 UI 模块仍无测试 |
| 国际化 | D | 约 1000+ 处中文字面量，`strings.xml` 仅 2 条；`resConfigs("zh","en")` 与「仅中文界面」自相矛盾 |
| 构建/CI | A- | PR + 发版双工作流，均跑完整校验；Action 按 SHA 固定；版本号快路径设计保守安全 |

**发现统计：Critical 0 / Major 12 / Minor 16**

---

## 2. Major

### M-1 `PlaybackController` 连接/释放生命周期存在竞态，重复 `connect()` 与 `release()` 可能泄漏控制器与监听器

`player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackController.kt:167-193, 471-477`。

1. **重复 `connect()` 覆盖 future**（:173）：`controllerFuture = future` 为单字段赋值，未对已存在的 future 做取消或监听器移除。二次调用 `connect()` 后，旧 future 的监听器仍被注册，完成后仍执行 `controller = connectedController`、`addListener(listener)`、`drainPendingActions`——两个 `MediaController` 持有同一 listener，`release()` 只从最后赋值的 controller 移除监听器，旧控制器与监听器泄漏且旧控制器仍会回调。

2. **`release()` 与 future 回调竞态**（:174-192, 471-477）：`release()` 置空 `controller` 并 `releaseFuture(it)`，但若 future 仍在 pending，其监听器回调可能在 `release()` 之后才触发，届时回调再次写入 `controller`、`addListener`、`drainPendingActions`、`onConnected`——把一个"已释放"的控制器重新写回字段并触发回调。

3. **`pendingActions` 与状态字段无线程同步**（:49-61, 227-243, 385-468）：`pendingActions: ArrayDeque`、`controller`、`lastSyncedQueueFingerprint`、`pendingQueueMutation`、`lastCurrentMediaItemIndex` 均为普通字段。listener 回调在主线程触发，但 `runWhenConnected`/`syncQueue` 等可被后台协程调用（`PlayerRuntime.kt:305-306` 注入的 lambda 未约束线程）。`ArrayDeque` 非线程安全，并发 `addLast`/`removeFirst` 可能抛 `ArrayIndexOutOfBoundsException` 或损坏。

建议：`connect()` 开头加守卫——若已有 future，先 `releaseFuture` 并移除监听器或拒绝重复连接；`release()` 先移除 future 监听器再 `releaseFuture`；`pendingActions` 及可变状态收敛到主线程（`withContext(Dispatchers.Main)` 或主 executor），或加锁保护。

### M-2 `playSingle()` 返回值不可靠，未区分"已执行"与"已入队"

`player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackController.kt:372-383`。

`playSingle` 调用 `runWhenConnected { ... }`（:374）但**完全忽略其返回值**，在 `SongMediaItemMapper.toMediaItem(song)` 成功（:373）后**恒定返回 `true`**。控制器未连接时，播放动作仅被压入 `pendingActions` 队列，并未真正执行，返回值仍为 `true`。

领域层 `PlaybackSessionCoordinator.startSinglePlayback()`（`domain/.../PlaybackSessionCoordinator.kt:121-122`）依赖该布尔值判断是否启动时长跟踪并返回"已开始播放"。未连接场景下，上层会为尚未加载/播放的歌曲启动 `PlayDurationTracker.startPlayback`，并对外报告播放已开始，造成统计错乱与 UI 状态不符。

建议：改为 `return runWhenConnected { ... }` 透传执行结果，或引入三态（已执行/已入队/失败）让上层据此决定。

### M-3 `PlayDurationTracker` 的 `scope` 在 `release()` 后未取消，且状态字段缺少可见性保障

`player/src/main/java/cn/com/dcsgo/mihx/player/PlayDurationTracker.kt:47, 50-53, 90-114, 136-162, 300-302`。

1. **作用域泄漏**（:47, 300-302）：`CoroutineScope(SupervisorJob() + Dispatchers.IO)` 从不取消。`release()` 只调 `stopPlayback()`（取消 `trackingJob`），未 `scope.cancel()`。SupervisorJob 与作用域对象在 tracker 存活期内持续保留。

2. **字段可见性/竞态**（:50-53, 111）：`currentSongId`、`currentSongDurationMs` 为普通 `var`，`startPlayback`/`stopPlayback`（可能主线程）写入，`updateDuration`（:111 比较 `currentSongId != songId`）与后台 `settlePlayback` 协程中读取，未用 `@Volatile`/原子量，存在可见性与读-改竞态。

建议：`release()` 末尾增加 `scope.cancel()`；`currentSongId`、`currentSongDurationMs` 改为 `@Volatile` 或 `AtomicReference`，或明确约束方法仅在主线程调用并加断言。

### M-4 单曲结束存在多路"结束"信号，易被上层重复结算

`player/src/main/java/cn/com/dcsgo/mihx/player/PlaybackController.kt:70-74, 107-132, 143-147`。

同一次媒体项结束可能同时触发三条回调：`onPlayWhenReadyChanged(false, END_OF_MEDIA_ITEM)` → `onMediaItemEnded(null, false)`（:70-74）；`onMediaItemTransition(nextItem, AUTO)` → `onMediaItemEnded(nextId, wrapped)`（:107-132）；`onPlaybackStateChanged(STATE_ENDED)` → `onPlaybackEnded()`（:143-147）。三条不同回调对应同一结束事件，上层若同时监听 `onMediaItemEnded` 与 `onPlaybackEnded`，存在重复累计播放次数/重复移出秒切列表的风险（具体是否重复取决于 Media3 在 `REPEAT_MODE_ALL` 下的回调时序，需真机验证，但接口层面未做去重）。

建议：明确职责划分——仅由 `onMediaItemTransition`（AUTO/SEEK/REPEAT）承担"切歌结算"，`onPlayWhenReadyChanged` 的 END_OF_MEDIA_ITEM 仅在队列真正耗尽时上报；或增加去重标记（如以 `mediaItemIndex + positionMs` 作为结算令牌）。

### M-5 `PlayerPersistenceGraph.persistScope` 从未取消，协程泄漏

`feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerPersistenceGraph.kt:46-62`。

`persistScope = CoroutineScope(SupervisorJob() + dispatchers.io)` 在 `init` 中启动 channel 消费者协程（`for (command in persistCommands)`），该协程永远挂起在 channel 上等待命令。`PlayerRuntime.onCleared()` → `PlayerLifecycleFacade.onCleared()` 的清理路径**不取消该 scope**。注释称"不挂在 ViewModel 生命周期上"是为让 `onCleared` 排队的最后一次写入落盘，但消费者写完最后一条命令后仍永久挂起，scope、SupervisorJob 和协程全部泄漏。`PlayerViewModel` 在 `AppRoot` 顶层创建，每次 ViewModel 重建（进程存活时配置变更）都新增一个泄漏的 scope。

建议：增加 `release()` 方法——先 `persistCommands.close()`，再 `persistScope.launch { persistCommands.consumeEach {}; persistScope.cancel() }` 排空后取消；在 `onCleared()` 中调用。

### M-6 跨线程可变状态缺乏同步保护

- `feature/player/.../PlayerRuntime.kt:123`：`trackedSongId`（`Int?` 普通 `var`）被 `controllerStateFacade.syncControllerPlaybackState`（经 `onPlaybackSnapshot` 回调，可能来自 Media3 binder 线程）写入，同时被 `mediaEventFacade.handleMediaItemEnded`（经 `onMediaItemEnded` 回调，同样可能来自 binder 线程）读取和清空。两个回调可能在不同线程交错执行，无 `@Volatile` 或 `Mutex` 保护。
- `feature/player/.../PlayerPersistenceGraph.kt:94, 97`：`controllerReady`、`pendingRestore` 为普通 `var`。`controllerReady` 由 `onControllerReady()`（经 `connect` 回调，可能来自 binder 线程）设置 `true`；`pendingRestore` 由 `restorePlaybackState()` 在 `withContext(dispatchers.main)` 中设置。`maybeApplyRestore()` 在 Main 线程读取 `controllerReady` 时，若更新发生在另一线程且无 `@Volatile`，可能看不到最新值，导致恢复决策延迟或错误。

建议：对 `trackedSongId`、`controllerReady`、`pendingRestore` 加 `@Volatile`；或明确"仅 Main 线程访问"约束并添加 `check(Thread.currentThread() == Dispatchers.Main.immediate)` 断言。

### M-7 `updateSongTitleOverride` 修改分组后未通知 UI，分组视图不刷新

`data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt:1119-1126`。

`updateSongTitleOverride` 调用 `persistSongs()` 落盘但**未调用 `notifySongsChanged()`**。其他所有会改变歌曲状态的方法（`deleteSong`、`deduplicateSongs`、`validateAndCleanupLocalFiles`、`refreshMetadata`、`refreshAllAlbumArtInternal`）都会调用 `notifySongsChanged()`，唯独本方法没有。`titleOverride` 直接决定 `Song.groupKey`（`core/model/Song.kt:113`），分组变更后订阅 `songsChanged` 的 UI（多版本管理页）不会收到通知，分组结果需等下一次任意变更或重启才刷新。

建议：在 `persistSongs()` 之后补 `notifySongsChanged()`，或改用 `persistSongs { notifySongsChanged() }`，与其他变更路径保持一致。

### M-8 `AlbumArtExtractor` 等待超时路径的 `finally` 会误删其他线程持有的 in-flight hash

`data/src/main/java/cn/com/dcsgo/mihx/data/util/AlbumArtExtractor.kt:191-267`（核心在 196-214 与 257-267）。

等待线程在 `while (!inFlightArtHashes.add(hash))` 超时 `break` 时，**从未成功 `add(hash)`**（不持有写入权），但继续执行到写盘块，`finally` 中无条件 `remove(hash)`，会**移除真正持有者线程的 hash 标记**。后果：在持有者仍在写盘期间，新到达的线程 `add(hash)` 成功，也开始写同一缓存文件——同哈希去重失效、重复解码/压缩/写盘。因 `renameTo` 原子不会损坏文件，但去重设计被破坏。

触发条件苛刻（需封面写入超过 5s 超时），但锁协议存在明确缺陷。

建议：用局部布尔量记录本线程是否真正获得写入权，仅在持有时于 `finally` 移除。

### M-9 `maxKnownSongId` 漏查 `song_group_overrides`，存在 ID 复用缺口

`data/src/main/java/cn/com/dcsgo/mihx/data/local/dao/MelodyDao.kt:502-512`。

`maxKnownSongId` 的 `UNION ALL` 跨 6 张表取最大 songId（songs / play_stats / playback_events / quick_skip_songs / quick_skip_short_play_counts / song_emotions），但 `song_group_overrides` 以 `songId` 为主键却未纳入高水位计算。`MusicRepository.restoreFromRoom` 以此值作为 `nextId` 高水位（注释明确说防止被删歌曲 id 被复用继承统计）。若旧版本删除歌曲时遗留了一条 `song_group_overrides`（其 songId 大于其他所有表的 MAX），`maxKnownSongId` 返回较小值，新导入歌曲可能复用该 id，并经 `toOverrideEntity` 继承旧 `titleOverride`，导致**错误分组**。

建议：在 `UNION ALL` 中补一行 `SELECT MAX(songId) AS id FROM song_group_overrides`。

### M-10 `SongEmotionsRepository.upsert` 的插入回退路径存在 TOCTOU，可能覆盖用户校准

`data/src/main/java/cn/com/dcsgo/mihx/data/repository/SongEmotionsRepository.kt:132-164`。

原子 `updateSongEmotionPreservingUserTags` 未命中（0 行）时走 `upsertSongEmotion`（`@Upsert`，对主键冲突做整行 REPLACE）。若两个并发 upsert 针对同一 songId 且都读到 `updated == 0`，两者都执行整行 INSERT/REPLACE，**后写者用其（可能陈旧的）userTags 整行覆写先写者**——正是 `updateSongEmotionPreservingUserTags` 本想消除的"用户校准静默消失"问题在并发窗口下复现。分析 Worker 通常串行，触发概率低，但接口本身未保证原子。

建议：将插入回退也改为原子形式，如单条 `INSERT ... ON CONFLICT(songId) DO UPDATE SET ...`（保留 userTags 的 CASE 表达式），彻底消除读—写窗口。

### M-11 抽屉内歌词页播放位置使用快照 Long，导致抽屉内容每 200ms 重组

`app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt:406-413`。

`LyricsRouteState.currentPositionMs = playerViewModel.positionMs.collectAsStateWithLifecycle().value`——类型为 `Long`（非 `StateFlow`），与 `HomeRouteState` 的 M-14 优化（使用 `StateFlow<Long>` 窄流，由叶子内部订阅）**不一致**。`.value` 读取的是组合时快照，`collectAsStateWithLifecycle()` 建立的订阅让歌词分支在每次 position 发射（200ms 一次）时整体重组，而 `LyricsView` 收到的是普通 `Long` 参数，无法利用窄流将重组范围收缩到歌词高亮叶子节点。在抽屉形态下，歌词、播放页、歌手/专辑详情共用同一 `when` 分支组合槽位，高频重组拖累所有内容。

建议：将 `LyricsRouteState.currentPositionMs` 改为 `StateFlow<Long>`，由 `LyricsView` 叶子内部用 `collectAsStateWithLifecycle` 订阅。

### M-12 国际化战略未定，硬编码中文持续膨胀

`app/src/main/res/values/strings.xml` 仅 2 条字符串（`app_name`、`app_introduction`），全仓库约 1000+ 处中文字面量直接写在 Kotlin 代码中。`app/build.gradle.kts:39` 的 `resConfigs("zh", "en")` 仍包含 `"en"`，与注释「应用仅中文界面」自相矛盾。

上一轮评审（2026-10-15）已标记此问题，fix-log-2026-10-27 明确记录「需产品侧先定是否支持英文的战略决策」。但当前版本中文字面量仍在增加，且 `resConfigs` 的 `"en"` 未清理。

建议：产品侧尽快定调。若确定仅中文，删除 `resConfigs("zh","en")` 中的 `"en"`，并在架构门禁中增加「`src/main` Kotlin 代码中中文字面量数量上限」的棘轮规则，防止继续膨胀。

---

## 3. Minor

- **m-1 `PlaylistRepository` / `SongRepository` 同步变更方法依赖异步持久化，存在内存态与落盘态短暂不一致窗口**：`domain/.../PlaylistRepository.kt:7-12`、`SongRepository.kt:35`，实现 `data/.../PlaylistRepositoryAdapter.kt:11-36`、`SongRepositoryAdapter.kt:32-33`。`getPlaylists()`、`addSongToPlaylist()`、`removeSongFromPlaylist()`、`updateSongTitleOverride()` 等均为非 suspend 同步方法，委托给 `MusicRepository` 内存态操作，持久化通过异步通道完成。方法返回 `true`/`false` 时对应 Room 写操作可能尚未落盘。建议改为 suspend 或在 KDoc 明确标注"仅更新内存态，持久化异步完成"。
- **m-2 proguard 规则中 `AppMediaSessionService` 引用已迁移的旧包名**：`app/proguard-rules.pro:4` 保留 `cn.com.dcsgo.mihx.data.player.AppMediaSessionService`，但该类已迁到 `cn.com.dcsgo.mihx.player`（`player/src/main/AndroidManifest.xml:6`）。规则匹配的类已不存在，属死规则。服务通过 Manifest 声明被 R8 自动保留，功能无影响，但规则与代码脱节。建议更新包名或移除该条。
- **m-3 无障碍：63 处图标缺少 `contentDescription`**：`src/main` 下 188 处 `Icon(` 调用中 63 处 `contentDescription = null`。较上一轮（87 处）有改善，但可点击 `IconButton` 内的图标若设 `null`，屏幕阅读器用户无法感知按钮用途。另有 12 处硬编码 `Color(0x...)`（较上轮 24 处减少），绕开主题系统。建议逐个确认可点击图标补 `contentDescription`，硬编码颜色逐步收敛到 `ThemeTokens`/`LocalVisTokens`。
- **m-4 `PlayStatsRepository.increment` 返回值不保证是"自增后的新值"**：`data/.../PlayStatsRepository.kt:44-51`。SQL 原子自增本身无 lost update，但返回值是在自增后**再发一次独立查询**得到的，期间若有其他自增，返回值会偏高。建议改用 `INSERT ... ON CONFLICT ... RETURNING playCount`（SQLite ≥3.35）或在文档中明确为"近似值"。
- **m-5 DAO 注释声称"避开 UPSERT"与同库多处使用 `ON CONFLICT DO UPDATE` 自相矛盾**：`data/.../MelodyDao.kt:242-270, 281-310`。`ensureQuickSkipShortPlay` 注释称改用两步是为避开 SQLite ≥3.24 的 UPSERT，但 `incrementPlayCount`/`incrementRawPlayCount`/`addPlayDuration` 三处直接使用 `INSERT ... ON CONFLICT DO UPDATE`，且 `@Upsert` 也生成 UPSERT。建议核实测试环境 SQLite 版本，统一说明。
- **m-6 `LrcParser` 时间戳正则不识别 1 位毫秒/非标准格式，整行被静默丢弃**：`data/.../LrcParser.kt:341, 366-388`。正则要求毫秒为 2-3 位数字，对 `[00:12.5]`（1 位毫秒）整个时间戳匹配失败，`matches` 为空，该行歌词被静默丢弃。建议放宽为 `\.(\d{1,3})?` 并增加 1 位毫秒换算分支。
- **m-7 `EmbeddedLyricsParser` 不支持 OGG 等纯 Vorbis 容器的内嵌歌词**：`data/.../EmbeddedLyricsParser.kt:51-56`。OGG 使用 Vorbis Comment（与 FLAC 同构），走 `else` 分支后无 `ID3` 头直接返回 null，OGG 内嵌歌词永远提取不到，只能靠同名 `.lrc` 兜底。建议对 `ogg`/`oga` 扩展名增加 `parseFlacLyricsFromStream` 分支。
- **m-8 `RoomMusicLibraryDataSource` 指纹往返的 `"size:null"` 不稳定**：`data/.../RoomMusicLibraryDataSource.kt:125, 144-145`。当 `size` 非空而 `lastModified` 为空时拼出 `"123:null"`，往返解析后 `lastModified` 仍为 null，`hasFileFingerprintChanged` 与当前 `"$size:$mtime"` 比较时若 mtime 实际为 0 会误判"已变化"。建议改用更安全的编码如 `"$size/$lastModified"` 或对空值显式占位。
- **m-9 `MusicRepository` 多次在写锁内同步触发 `notifySongsChanged` 回调**：`data/.../MusicRepository.kt:404, 761, 835, 937, 1076`。`persistSongs { notifySongsChanged() }` 的 `onPersisted` 回调在**写锁仍持有期间**执行。若回调耗时会延长写锁持有时间，阻塞所有读路径。建议将 `notifySongsChanged()` 移到写锁块之外。
- **m-10 `SharedPreferencesLegacyJsonMigration` 迁移后未清理 `play_stats_prefs`/`quick_skip_songs_prefs`**：`data/.../SharedPreferencesLegacyJsonMigration.kt:19-57`。迁移成功后仅写 `migration_state`，未删除两个旧 SharedPreferences。两者迁移后不再被读取，但长期残留在应用私有目录。建议在 `dao.upsertMigrationState(...)` 成功后调用 `context.deleteSharedPreferences(...)`。
- **m-11 `SongEmotionsRepository.observeAll` 缓存清理条件过窄**：`data/.../SongEmotionsRepository.kt:109-118`。仅当缓存条数严格大于当前行数时才清理。删除一首后又新增一首（总数不变但 id 不同）时，缓存中旧 id 条目不会清除，长期累积。建议改为无条件 `retainAll` 或按容量阈值触发。
- **m-12 `AudioMetadataExtractor.getRealPathFromUri` 的 tree URI 回退分支为死代码**：`data/.../AudioMetadataExtractor.kt:360-425`。`getDocumentId(uri)` 对 tree URI 抛异常被 catch 后直接返回 `uri.toString()`，导致 URL 解码回退分支永远不会执行。仅影响 `SongInfo.filePath` 展示。建议将解码回退逻辑移到 `catch` 分支内。
- **m-13 `AndroidManifest` 设置 `largeHeap="true"`**：`app/src/main/AndroidManifest.xml:23`。`largeHeap` 仅提高 Dalvik 堆上限，不能真正防止 OOM，反而可能掩盖内存泄漏。建议移除，通过 Profiler 定位内存问题。
- **m-14 备份规则排除全部用户数据，无备份/恢复能力**：`app/src/main/res/xml/backup_rules.xml`、`data_extraction_rules.xml`。排除 `melody.db`（曲库/歌单/情绪标注/播放统计）、所有 SharedPreferences、所有 DataStore、封面缓存。用户全部生成数据均不参与云备份与设备迁移，换机或重装即丢失全部曲库、歌单、情绪标注和设置。建议评估将 `melody.db` 元数据或用户设置纳入备份，或在应用内提供"导出/导入曲库元数据"功能。
- **m-15 AppRoot 存在空块与死代码**：`app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt:178-179, 536-549`。`if (uiState.isLoading) {}` 为空块（重构遗留），`LoadingSplash` 组合函数定义但从未使用。建议删除或补充启动画面渲染逻辑。另有 `AppScaffold.kt:219-222` 使用 `currentSong!!` 强制解包，建议改 `?.let`。
- **m-16 `PlaylistScreen` 中 `visiblePlaylists` 计算后未消费**：`feature/playlist/.../PlaylistScreen.kt:359-367, 388-392`。`visiblePlaylists` 通过 `remember` 记忆化计算，但 `PLAYLISTS` 分支实际使用内联 `playlists.filter { ... }`，`visiblePlaylists` 从未被消费，每次重组执行两次相同过滤。建议 `PLAYLISTS` 分支改用 `visiblePlaylists`。

## 4. 正向观察

- **上一轮核心缺陷均已修复并有回归测试**：M-1 计时协程泄漏（`PlayDurationTracker` 持有 `Job` 显式 `cancel()` + `activeTrackingLoops` 探针 + 回归测试）、M-3 内嵌歌词解析（全面重写，按编码区分终止符、UTF-8 解码、syncsafe 帧长、流式逐帧读取 + 回归测试）、M-4 删歌统计清理与 ID 复用（`deleteSong` 清理关联表 + `nextId` 不复用 + 回归测试）、C-1 `loadSongInfo` 崩溃（实现侧 `readEmotionSafely` + 调用侧 `loadSongInfoSafely` 双层兜底 + 架构门禁固化）。
- **`src/main` 零 `runBlocking`**：架构门禁（`ProductArchitectureVerification.kt:550-580`）以代码级正则（排除注释）强制禁止，`domain` 接口与 `:data`/`:player` 已彻底清零。`runBlocking` 仅出现在测试代码中。
- **CI 流水线大幅加固**：新增 `pr-check.yml`（PR/push 到 main 跑完整 `check` + androidTest 编译），`release.yml` 增加 `check` 作业作为发版前置门禁。所有 Action 按 commit SHA 固定。`workflow_dispatch` 的 tag 输入经 `env` 间接注入，避免 shell 注入面。版本号快路径判断保守安全。
- **架构门禁设计成熟**：收集式违规报告（一次报出全部问题）、棘轮式分层不变量（`:core:model`/`:domain` 禁止 `import android.*`，存量基线白名单、新增违规拦截）、规则本体抽到 `buildSrc` 并由单测覆盖。`extractBracedBlock` 括号配对提取比旧正则稳健。
- **日志体系完善**：`AppLog` 统一封装，release 默认关闭 debug/info 并脱敏 URI/路径/蓝牙地址；6 个脱敏正则提为文件级常量（各编译一次）；`PerformanceTrace` 走独立通道，release 下白名单操作可真正输出（M-20 修复）。
- **播放队列架构严谨**：`PlayQueue` 不可变操作、`playOrderIds` 按出现次数解释（支持重复歌曲）、`MediaController` 为播放顺序事实来源、窗口化规划层（`WindowedControllerQueuePlanner`/`ControllerWindowSynchronizer`）收敛窗口边界。M-10 的 `currentPlayOrderIndices` O(n²) 已修复为 O(n)。窄流（`positionMs`/`sleepTimerRemainingMs`）避免整壳重组。
- **增量队列同步设计精巧**：`planIncrementalQueueSync`/`syncQueue` 增量路径在当前播放项不变时仅做 `removeMediaItems`/`addMediaItems`，避免全量 `setMediaItems` 导致的"开头重播 1-2 秒"，并有专门单测。
- **单项循环回绕识别覆盖 Media3 边界**：`isSingleItemLoopRewind` 与 `isMediaItemWrap` 精准处理 `REPEAT_MODE_ALL` 下单队列索引不变（0→0）不触发 `onMediaItemTransition` 的盲区，逻辑严谨且有单测覆盖。
- **解码策略工程扎实**：渲染器模式 `PREFER → ON`（平台解码器优先，杜比 Atmos 走设备原生解码器）、`MimeExcludingMediaCodecSelector` 排除 ALAC 避开厂商软解 SIGSEGV、关闭浮点输出做兼容性收口，均附实测澄清，不夸大修复效果。
- **封面跨进程可读性修复**：恢复 FileProvider + `content://` URI + `ArtworkUriAccess` 跨进程授权（铸造时 + 发布时两个调用点），修复 SystemUI `FileNotFoundException` 日志噪音，为车机/Android Auto 未来接入铺路。
- **情绪链路性能大幅收敛**：已解析曲线缓存（键 = songId + analyzedAt）、聚合查询（`emotionTableStat`/`emotionRecentAnalyzedTimestamps`）、`tagsBySong` 一次派生全程复用、`EmotionDisplay` 只投票一次、`Song.parsedArtists` 构造期缓存、新增索引（`index_songs_albumId`、`index_playback_events_songId`）、`playback_events` 保留策略（12 个月）。
- **迁移链完整且意图清晰**：`MelodyMigrations.kt` 覆盖 v1→v20 全部步骤，每步都有详尽注释说明迁移理由，并抽出为公开对象供迁移回归测试引用，避免测试与线上迁移漂移。
- **统计与计票的原子性**：`incrementPlayCount`/`incrementRawPlayCount`/`incrementQuickSkipShortPlay`/`addWordVotesDelta` 全部用 SQL 级原子自增或 `INSERT ... ON CONFLICT DO UPDATE`，消除 read-write 间的 lost update，且对 Int 上限做 `MIN(..., 2147483647)` 饱和防溢出翻转。
- **用户校准的 TOCTOU 修复到位**：`updateSongEmotionPreservingUserTags` 把"保留已有 userTags"的合并规则下沉到 SQL 的 CASE 表达式，单条语句完成；`insertUserOnlyCorrectionIfAbsent` 用 `INSERT OR IGNORE` 而非 `@Upsert`，避免用 user-only 空行擦掉已有分析结果。
- **隐私与安全**：无网络层、无账号体系；`READ_EXTERNAL_STORAGE` 被门禁禁止（SAF 文档树为导入主路径）；`AppMediaSessionService` 声明 `exported="false"` + `foregroundServiceType="mediaPlayback"`；备份/数据提取规则覆盖 Room、5 个 DataStore、SharedPreferences、封面缓存；`keystore.properties`/`local.properties` 均在 `.gitignore`。
- **皮肤解析 fail-closed**：`SkinParser` 对用户导入的 JSON 采用 fail-closed 策略——未知 `part` 拒绝、未知字段忽略、数值越界夹紧、结构性错误拒绝，一次返回全部问题。
- **音质分级与空间音频识别规则严谨**：有损按码率、无损按位深+采样率、高解析要求 24bit 且 ≥48kHz、DSD 直接归入高解析；空间音频只识别 E-AC-3 JOC 与多声道 AC-4，2.0 AC-4 不算；数据不足返回 `UNKNOWN` 不猜。
- **待执行动作有界**：`pendingActions` 上限 `MAX_PENDING_ACTIONS=64`，FIFO 丢弃并上报 `onControllerUnavailable`，防止控制器长时间未就绪时内存无限增长。
- **Hilt ViewModel 创建策略正确**：`MoodTimeSlotViewModel`、`EmotionViewModel`、`SettingsViewModel` 等在 `AppRoot` 顶层用 `viewModel()` 创建后经参数传入 `AppNavHost`，避免在 `NavHost composable{}` 内创建导致回落到非 Hilt 工厂而崩溃。
- **路由 State/Actions `@Stable` 标注**：`HomeRouteState`、`PlaylistRouteState`、`UserRouteState` 等均标注 `@Stable`，配合 `NowPlayingSurface` 中 `remember` 固定实例身份，使路由边界真正具备跳过重组的能力。

## 5. 建议优先级

1. **M-1、M-2、M-3**（`PlaybackController` 生命周期竞态、`playSingle` 返回值、`PlayDurationTracker` scope/可见性）：直接影响播放正确性与稳定性，改动集中在 `:player` 模块，优先修复。
2. **M-5、M-6**（`persistScope` 泄漏、跨线程状态无同步）：协程泄漏与恢复决策竞态，消除资源泄漏与状态错误。
3. **M-7、M-8、M-9、M-10**（`updateSongTitleOverride` 不通知、`AlbumArtExtractor` 超时误删、`maxKnownSongId` 漏查、`SongEmotionsRepository` TOCTOU）：数据一致性与并发协议缺陷，影响分组刷新、封面去重、ID 复用、用户校准。
4. **M-4**（单曲结束多路信号）：需结合真机验证 Media3 回调时序后再做去重，避免误判。
5. **M-11**（抽屉歌词重组）：性能优化，将 `currentPositionMs` 改为 `StateFlow<Long>`。
6. **M-12**（国际化）：产品侧定调后，若仅中文则删 `"en"` + 加门禁防膨胀。
7. **m-1 ~ m-16** 等体验与健壮性类，随相关功能顺手修。

## 6. 局限

未运行构建与测试（本机 shell 无 JDK），所有结论均基于静态阅读、grep 扫描、架构门禁核对与对关键逻辑的独立模拟，未在设备上复现。M-4（单曲结束多路信号）的具体重复结算行为取决于 Media3 在 `REPEAT_MODE_ALL` 下的真实回调时序，需真机验证。`:feature` 各模块 UI 正文与 `core:ui` 部分组件虽由子代理通读，但 Compose 重组/状态问题仍需运行时验证。建议在有 JDK 的环境跑 `./gradlew check` 与 lint 作为补充，并对 `PlaybackController` 的连接/释放生命周期、`PlayDurationTracker` 的并发行为补集成测试。
