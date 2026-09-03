# 代码评审报告 — melody-in-heart-x

| 项目 | 内容 |
|------|------|
| 评审日期 | 2026-09-03 |
| 评审模型 | GLM-5.3-Flash |
| 代码版本 | versionName 3.5.1 (versionCode 29)，main 分支工作区 |
| 评审范围 | 全部 14 个模块（:app / :core:* / :domain / :data / :player / :feature:* / :benchmark），约 303 个 Kotlin 源文件、3.4 万行主源码、58 个测试文件 |
| 评审方法 | 逐文件静态审查 + 并发/生命周期专项排查 + 构建配置与清单审计 + 测试质量抽查（8 个测试文件）+ 对每个候选问题做源码级二次验证 |

---

## 0. 评审总览

| 维度 | 评级 | 一句话结论 |
|------|------|-----------|
| 1. 架构与设计模式 | A- | 多模块 Clean Architecture 落地扎实，`verifyProductArchitecture` 机器强制执行；但 data 层仍有阻塞接口泄漏到 domain 契约 |
| 2. 可维护性与代码风格 | B | 命名与注释质量高，但存在超长 Composable（31/43 参数）、重复实现、少量死代码 |
| 3. 性能与内存 | C+ | **runBlocking 泛滥（约 50 处）**、锁内长 IO、每秒整壳重组、组合期 O(n) 重算是主要债务 |
| 4. 安全性 | A- | 无网络层、密钥管理规范、日志脱敏、备份排除完备；个别 fd 泄漏与 SAf 删除在调用线程执行 |
| 5. 依赖与第三方库 | B+ | 版本目录统一管理良好；但 lifecycle 2.6.1 与 Compose BOM 2025.09 严重脱节，tensorflow-lite 2.12.0 过旧 |
| 6. 测试与健壮性 | B+ | 测试是真实行为断言（非形式测试），密度远超平均项目；缺口在异常路径与并发场景 |
| 7. 国际化与本地化 | D | **约 909 处硬编码中文、全项目仅 4 条 strings.xml 资源**，`resConfigs("en")` 形同虚设 |
| 8. 构建与配置 | A- | R8 + 资源收缩 + 签名安全策略 + ABI 裁剪到位；少量文档漂移 |

**发现统计：Critical 2 项 / Major 14 项 / Minor 22 项**

---

## 1. Critical（必须立即修复）

### C-1 导入流程无异常处理，失败后 UI 永久卡在"导入中"

- **问题描述**：`PlayerImportFacade.importFolderAsyncWith` 直接 `launch { onResult(importAction()) }`，无 try/catch/finally，也无 CoroutineExceptionHandler。一旦 `importAction()` 抛出异常（磁盘满、SAF 权限被系统回收、元数据解析崩溃），`isImporting = true`（`importFolderWith` 首行设置）**永远不会复位**，`onResult` 不会回调，异常被协程静默吞掉，无任何日志。
- **影响范围**：`:feature:player` 导入主路径；用户唯一恢复手段是杀进程。属于用户可稳定触发的功能性死锁。
- **位置**：`feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayerImportFacade.kt:22-25`
- **修复建议**：

```kotlin
internal fun importFolderAsyncWith(
    onResult: (Int) -> Unit,
    importAction: suspend () -> Int,
) {
    launch {
        try {
            onResult(importAction())
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            AppLog.error(TAG, "importFolder failed: ${t.message}")
            updateState {
                it.copy(
                    isImporting = false,
                    importProgress = 0,
                    importTotal = 0,
                    errorMessage = "导入失败：${t.message ?: "未知错误"}，请重试",
                )
            }
            onResult(0)
        }
    }
}
```

同时建议在 `importFolderWith` 内用 `try { ... } finally { 复位 isImporting }` 双保险（finally 是幂等复位，与正常路径不冲突）。

### C-2 `produceState` 中 suspend 调用未捕获异常，数据库异常直接崩溃

- **问题描述**：`:app` 模块 5 处 `produceState` 直接在 producer 协程中调用 `playerViewModel.loadPlaybackStatsSnapshot()` / `loadSongInfo()`，均无 runCatching。这些方法底层桥接 `PlayStatsRepository` 的 `runBlocking` Room 调用，任何 Room 异常（SQLiteFullException、磁盘 IO 错误、损坏的 DB）都会在 producer 协程中未捕获并向上传播，**直接崩溃应用**。
- **影响范围**：播放统计页、按情绪浏览、热门排行等 5 个路由（`AppNavHost.kt:447,487,506,591,606`）+ `HomeRoute.kt:144-147`。
- **位置**：`app/src/main/java/cn/com/dcsgo/mihx/app/AppNavHost.kt:447-449`（典型）
- **修复建议**：

```kotlin
val snapshot by produceState(PlaybackStatsSnapshot.EMPTY) {
    runCatching { playerViewModel.loadPlaybackStatsSnapshot() }
        .onSuccess { value = it }
        .onFailure { AppLog.error("AppNavHost", "loadPlaybackStats failed: ${it.message}") }
}
```

更彻底的做法：让 `loadPlaybackStatsSnapshot` 返回 `Result<PlaybackStatsSnapshot>`，把错误语义上移到 UiState（显示空态 + 重试），而不是 UI 层到处打补丁。

---

## 2. Major（高优先级）

### M-1 runBlocking 泛滥：约 50 处阻塞调用桥接 DataStore/Room，主线程可被磁盘 IO 卡住

- **问题描述**：data/player 层用 `runBlocking(Dispatchers.IO)` 把 suspend 存储层"降级"为同步接口，且**该阻塞签名已写进 domain 契约**：
  - `data/repository/PlayerSettingsRepository.kt` — 14 处（`currentEmotionScanPaused`、`currentGlobalUniformRandomEnabled`、`setSleepTimerEndAtMsBlocking` 等）
  - `data/repository/SongEmotionsRepository.kt` — 11 处
  - `data/repository/QuickSkipSongsRepository.kt` — 8 处（且 `contains`→`add` 是 check-then-act 竞态，L30-39）
  - `data/repository/PlayStatsRepository.kt` — 7 处（`readStat`+`writeStat` 两段式读改写，并发 settle 会丢更新）
  - `player/data/player/PlaybackStateStore.kt` — 7 处（`save()` 约每 5s 被 autosaver 调用；L78-87 为打一行日志专门 `runBlocking` 读一次 DataStore，纯浪费）
  - `domain/repository/PlayerSettingsRepository.kt:16-37` 把这些阻塞方法定义为**接口契约**，等于把阻塞强制传染给所有实现与调用方。
- **影响范围**：设置页、睡眠定时器、播放统计、启动恢复路径。若调用链在主线程，用户可感知卡顿甚至 ANR。
- **修复建议**：分两阶段收敛——
  1. 先改调用点：所有 ViewModel/facade 侧改用 suspend 版本（`PlaybackStateStore.persistCurrentPlaybackSnapshot` 已有 suspend 实现，推广此模式）；
  2. domain 接口全面 suspend 化，`runBlocking` 只允许出现在最外层适配器（如同步回调桥）并加 `@SuppressLint("DiscouragedPrivateApi")` 式注释说明豁免理由：

```kotlin
// domain
interface PlayerSettingsRepository {
    suspend fun currentGlobalUniformRandomEnabled(): Boolean
    suspend fun setSleepTimerEndAtMs(value: Long?)
}

// data — 仅适配器保留同步壳，供无协程上下文的遗留路径
class PlayerSettingsDataStore(...) : PlayerSettingsRepository {
    override suspend fun currentGlobalUniformRandomEnabled(): Boolean =
        context.dataStore.data.map { it[KEY_UNIFORM_RANDOM] ?: false }.first()
}
```

### M-2 `refreshAllAlbumArtInternal` 持写锁执行长 IO，大曲库启动后全量读锁被阻塞数秒

- **问题描述**：`MusicRepository.kt:243-261` 在 `lock.write {}` 内逐首调用 `AlbumArtExtractor.refreshAlbumArtIfNeeded`，后者对缓存失效歌曲做 `MediaMetadataRetriever` 提取 + `BitmapFactory` 解码 + JPEG 落盘，**每首可达几十至几百毫秒**。5000 首曲库若有 100 首缓存失效，所有 `getSongs()` 读操作（主线程 Compose 重组依赖）被写锁阻塞数秒到数十秒。
- **影响范围**：`:data` 全局读锁 → 所有依赖曲库数据的界面启动卡顿/ANR 风险。
- **位置**：`data/src/main/java/cn/com/dcsgo/mihx/data/repository/MusicRepository.kt:243-268`
- **修复建议**：锁外提取、短写锁回写：

```kotlin
fun refreshAllAlbumArt() {
    val snapshot = lock.read { songs.toList() }
    val updates = mutableListOf<Pair<Int, String?>>()
    for (song in snapshot) {
        val newUri = AlbumArtExtractor.refreshAlbumArtIfNeeded(ctx, song) // 锁外长 IO
        if (newUri != song.albumArtUri) updates += song.id to newUri
    }
    if (updates.isNotEmpty()) {
        lock.write {
            updates.forEach { (id, uri) ->
                songs.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { i ->
                    songs[i] = songs[i].copy(albumArtUri = uri)
                }
            }
            persistSongs()
        }
        onSongsChanged?.invoke()
    }
}
```

### M-3 `deleteSong` 在调用线程同步执行 SAF 跨进程删除 —— 主线程 ANR 风险

- **问题描述**：`MusicRepository.kt:561-566` 的 `deleteSong` 是非 suspend 接口（domain `SongRepository` 契约同样如此），内部 `deleteBackingFile` 走 `DocumentFile.fromSingleUri(...).exists()/canWrite()/delete()`，是 ContentProvider 跨进程调用，单次可达几十毫秒，SAF 慢时更久。同文件 `validateAndCleanupLocalFiles` 做了 `withContext(Dispatchers.IO)`，唯独 deleteSong 没有——**不一致**。
- **影响范围**：歌曲删除操作（用户高频触发）。
- **修复建议**：接口 suspend 化 + IO 调度；若调用方（SongDeletionCoordinator）已在协程内，直接透传即可：

```kotlin
// domain/repository/SongRepository.kt
suspend fun deleteSong(songId: Int): DeleteSongResult

// data — MusicRepository
override suspend fun deleteSong(songId: Int): DeleteSongResult =
    withContext(Dispatchers.IO) { deleteSongInternal(songId) }
```

### M-4 单例 `MusicRepository` 的 `onSongsChanged` 持有外部回调 —— 经典内存泄漏

- **问题描述**：`MusicRepository.kt:73-77` 暴露 `var onSongsChanged: (() -> Unit)?` + `setSongsChangedListener`。MusicRepository 是 Hilt `@Singleton`；监听器通常由 ViewModel/Composable 注册并捕获其引用，若销毁路径未显式置 null，单例将**长期持有已销毁对象**。且回调调用线程不一致：`refreshAllAlbumArtInternal`（IO 线程 L268）、`addFolder`（IO 线程 L396）与 `deleteSong`（调用线程 L601）都会 invoke。
- **影响范围**：`:data` → 全局。
- **修复建议**：改用 `SharedFlow` 单向广播，消费端用 `repeatOnLifecycle` 收集；若必须保留回调，至少在契约中注明"必须在 onDestroy 中清理"并统一在主线程 invoke：

```kotlin
private val _songsChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val songsChanged: SharedFlow<Unit> = _songsChanged.asSharedFlow()
// 触发处：_songsChanged.tryEmit(Unit)
```

### M-5 `PlayDurationTracker` 协程 scope 无 SupervisorJob，一次异常将永久杀死播放统计

- **问题描述**：`PlayDurationTracker.kt:39` `private val scope = CoroutineScope(Dispatchers.IO)`——没有 SupervisorJob。`settlePlayback()`（L144）在 `scope.launch` 中执行多个 Room runBlocking 操作，任一抛异常（磁盘满、DB 损坏）会沿 launch 传给普通 Job → **整个 scope 被取消**，此后所有 `startPlayback`/`stopPlayback` 的 launch 静默失效：用户"听了不计次、时长不累计"，且无任何日志。
- **影响范围**：播放统计全链路（`:player`）。
- **修复建议**：

```kotlin
private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

private fun launchTracked(block: suspend CoroutineScope.() -> Unit) =
    scope.launch {
        try { block() } catch (ce: CancellationException) { throw ce }
        catch (t: Throwable) { AppLog.error(TAG, "duration tracking failed: ${t.message}") }
    }
```

### M-6 睡眠定时器每秒写主 UiState → 整壳每秒重组

- **问题描述**：`PlayerSleepTimerCoordinator.kt:104` 每秒 `updateState { it.copy(sleepTimerRemainingMs = remainingMs) }` 写入唯一主状态流。倒计时激活期间 `AppRoot.kt:71` 的 `collectAsStateWithLifecycle()` 每秒触发，`AppScaffold` + 当前目的地**全部重组**。项目已专门为 `positionMs` 做了窄流优化（`PlayerRuntime.kt:152-197` 注释明确"只写窄流，避免整壳重组"），sleep timer 绕过了该机制。
- **影响范围**：`:feature:player` + `:app` 整壳；低端机倒计时期间明显掉帧。
- **修复建议**：仿照 `positionMs` 拆窄流：

```kotlin
// PlayerRuntime
private val _sleepTimerRemainingMs = MutableStateFlow<Long?>(null)
val sleepTimerRemainingMs: StateFlow<Long?> = _sleepTimerRemainingMs.asStateFlow()

// PlayerSleepTimerCoordinator 构造参数增加 updateRemaining: (Long?) -> Unit
updateRemaining(remainingMs)   // 只写窄流
// 只有启动/取消/到期等离散事件才走 updateState
```

### M-7 组合期内反复执行 O(n) 全库分组计算，无 remember

- **问题描述**：`AppNavHost.kt:380,415` 与 `AppRouteStateMappers.kt:35,41,75,94,108` 在组合体内直接调用 `playerViewModel.getGroupedSongs(uiState.songs).flatten()`。每次 `uiState` 变化（结合 M-6 即每秒、导入时每个进度 tick）都重新执行全库分组 + flatten。对比同文件 `AppNavHost.kt:517,555` 已正确使用 `remember(uiState.songs)`——**模式已知但未覆盖全部路由**。
- **影响范围**：`:app` 导航层 7 处；大曲库下每帧毫秒级 → 帧预算被吃掉。
- **修复建议**：

```kotlin
// AppNavHost.kt:380
val allSongs = remember(uiState.songs) {
    playerViewModel.getGroupedSongs(uiState.songs).flatten()
}
songs = allSongs,
```

更优做法：把分组结果下沉为 ViewModel 的 `stateIn` 派生流，组合期零计算：

```kotlin
val flatGroupedSongs: StateFlow<List<Song>> =
    uiState.map { it.songs }
        .map { songs -> getGroupedSongs(songs).flatten() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
```

### M-8 `MutableStateFlow.update` 的变换 lambda 内执行副作用

- **问题描述**：`PlayerRuntime.kt:161-169` 在 `_uiState.update { ... }` 内同步写 `_positionMs.value`。`update` 是 CAS 重试循环，竞争下 transform 会被执行多次——Kotlin 文档明确要求其应为纯函数。副作用虽幂等，但违背约定，且 `_positionMs` 与 `_uiState` 无原子性保证。
- **影响范围**：`:feature:player` 状态同步核心路径。
- **修复建议**：

```kotlin
private fun updateUiState(transform: (PlayerUiState) -> PlayerUiState) {
    var next: PlayerUiState
    _uiState.update { current ->
        next = transform(current)
        next
    }
    // update 返回后再同步窄流：CAS 重试不重复执行
    if (next.currentPositionMs != _positionMs.value) {
        _positionMs.value = next.currentPositionMs
    }
}
```

### M-9 定位 FAB 潜在 `animateScrollToItem(-1)` 崩溃路径

- **问题描述**：`PlaylistScreen.kt:728-731`：`displaySongs.indexOfFirst { it.id == cs.id }` 的结果未校验即传入 `animateScrollToItem(index)`。`canLocate` 只在重组时求值；点击瞬间 `displaySongs` 可能已被搜索过滤/重排更新，`indexOfFirst` 返回 -1 → `IllegalArgumentException` 崩溃。
- **影响范围**：`:feature:playlist` 播放定位功能。
- **修复建议**：

```kotlin
val index = displaySongs.indexOfFirst { it.id == cs.id }
if (index >= 0) {
    locateHighlight.trigger(cs.id)
    coroutineScope.launch { listState.animateScrollToItem(index) }
}
```

### M-10 domain 层并非"纯 Kotlin"：`android.net.Uri` 泄漏进接口契约

- **问题描述**：`domain/repository/MusicImportRepository.kt:3` 与 `domain/importing/FolderImporter.kt:3` 导入 `android.net.Uri` 并作为接口参数。domain 虽无 Android 依赖（build.gradle.kts 确认），但 Android library 插件自带 framework stub——这些接口在 JVM 单元测试中触发 Uri 会 "not mocked" 崩溃，无法脱离 Robolectric 测试，违背架构文档"Domain 层无 Android 依赖"的承诺。另外 `core/model` 的 `Song.kt`、`AlbumEntry.kt`、`ArtistEntry.kt` 同样使用 `android.net.Uri`，且被 `androidx.compose.runtime.Stable` 注解绑架（model 层依赖 Compose runtime）。
- **影响范围**：`:domain` / `:core:model` 的可测试性与未来跨平台可能性。
- **修复建议**：接口与 model 改用 `String`（treeUri 的字符串形式），或在 `:core:model` 定义自有 `MediaUri` 值类；边界适配在 data 层完成：

```kotlin
// domain
interface FolderImporter {
    suspend fun importFolder(treeUri: String, onProgress: (Int, Int) -> Unit): ImportResult
}

// data — 边界适配
override suspend fun importFolder(treeUri: String, ...) =
    folderImporter.importFolder(Uri.parse(treeUri), ...)
```

若短期内不重构，至少把 Compose 注解从 `:core:model` 移除（`Stable` 对纯数据类收益有限，`Immutable`/data class 已足够）。

### M-11 `updatePlaylistSongCount` 是 public 方法但依赖"调用方已持锁"的隐藏约定

- **问题描述**：`MusicRepository.kt:543-549` 直接**无锁写共享可变列表** `playlists[index] = ...`，注释承认"通常在已持有写锁的上下文中调用"。当前所有调用点恰好在 `lock.write` 内，属于靠注释维持的脆弱不变量——任何外部代码（该方法 public）都可在无锁状态调用造成数据竞争。
- **影响范围**：`:data` 线程安全。
- **修复建议**：改 private；或利用 `ReentrantReadWriteLock` 可重入特性显式补锁（自防御）：

```kotlin
private fun updatePlaylistSongCountLocked(playlistId: Int) { ... }   // 仅锁内调用

// 或保留 public 但自防御：
fun updatePlaylistSongCount(playlistId: Int) = lock.write {
    val index = playlists.indexOfFirst { it.id == playlistId }
    if (index >= 0) playlists[index] = playlists[index].copy(songCount = playlists[index].songIds.size)
}
```

### M-12 `PlaybackController.startService()` 在后台场景抛 IllegalStateException

- **问题描述**：`PlaybackController.kt:133-135` 用 `context.startService(Intent(...))`。API 26+ 应用在后台时调用会抛 `IllegalStateException`。MediaController 连接本身会拉起服务，此显式调用仅在前台场景安全。
- **影响范围**：`:player` 服务启动路径。
- **修复建议**：

```kotlin
override fun startService() {
    ContextCompat.startForegroundService(context, Intent(context, serviceClass))
}
// 或直接移除该调用，统一走 MediaController 连接自启动服务
```

### M-13 国际化：约 909 处硬编码中文，strings.xml 机制形同虚设

- **问题描述**：全项目 `src/main` 中约 **909 处中文字符串字面量，分布在 107 个 Kotlin 文件**。全项目仅 2 个 strings.xml、合计 4 条资源（`app_name`/`app_introduction`），且 `feature/user` 与 `app` 的 strings.xml **完全重复定义**。无 `values-en`、无 plurals、无带占位符的格式串。而 release 配置 `resConfigs("zh", "en")` 声明了 en 却无任何落地资源——出海配置是空架子。更严重的是 **ViewModel/门面层也在拼用户文案**（`PlayerRuntime.kt:579`、`PlayerErrorFacade.kt:20`、`MusicRepository.kt:595-598` 的 DeleteSongResult message），文案留在数据层导致 UI 层无法本地化。
- **影响范围**：全部模块；若确有出海计划则为发布阻断项。
- **修复建议**：
  1. 数据层返回错误**码/类型**而非文案：`DeleteSongResult.Failure(reason: SongDeleteError)`，UI 层映射 `stringResource(R.string.delete_error_file_missing)`；
  2. UI 文案分模块抽到各自 `values/strings.xml`（带 `%1$d` 占位符），删除 `feature/user` 的重复 strings.xml；
  3. 若短期内只面向中文，把 `resConfigs("zh", "en")` 改为 `resConfigs("zh")` 以诚实裁剪，避免"半成品 en"；
  4. 数量太大可先抽 Toast/Dialog 等高可见文案（`AppNavHost.kt` 内约 628 个中文字符），列表内 label 分批跟进。

### M-14 依赖版本断层：lifecycle 2.6.1 / documentfile 1.0.1 / tensorflow-lite 2.12.0 与 2025 年技术栈脱节

- **问题描述**：
  - `androidx-lifecycle-* = 2.6.1`（2023 年初版本），而 Compose BOM 已是 2025.09.01、activity-compose 1.12.2 —— lifecycle 是整个栈中最旧的一环，且 `lifecycle-runtime-compose` 的 `collectAsStateWithLifecycle`、`LifecycleResumeEffect` 等 API 在 2.8+ 才完善；
  - `tensorflow-lite = 2.12.0`（硬编码在 catalog，未走 ref）已停更，官方推荐迁移 LiteRT（`com.google.ai.edge.litert`）；
  - `documentfile = 1.0.1` 极旧（最新 1.1.x）；
  - `workmanager = 2.9.1` 未用 version ref 管理版本（catalog 内联硬编码）。
- **影响范围**：全项目；老 lifecycle 与新 Compose 组合存在已知的重组/lifecycle 时序边缘问题。
- **修复建议**：

```toml
[versions]
lifecycle = "2.8.7"        # 与 BOM 2025.09 同期
documentfile = "1.1.0"
litert = "1.4.0"           # 或保持 tensorflow-lite 至最终版并记录迁移计划
```

迁移 tensorflow 前先在 `EmotionAnalyzer` 增加回归测试锁定输出（现有 YAMNet 模型文件不变时输出应逐位一致）。

---

## 3. Minor（择机修复）

### 3.1 资源与健壮性

| # | 位置 | 问题 | 修复建议 |
|---|------|------|---------|
| m1 | `data/util/AlbumArtExtractor.kt:105-108` | `openFileDescriptor` 后未用 `use{}`，`retriever.setDataSource(fd.fileDescriptor)` 抛异常时 **fd 泄漏** | `contentResolver.openFileDescriptor(...)?.use { fd -> retriever.setDataSource(fd.fileDescriptor) }` |
| m2 | `feature/player/.../PlayerRuntime.kt:360` | `playbackBridgeFacade by lazy {` 处缩进/换行损坏，本应被 spotless 拦截 | 跑 `.\gradlew.bat spotlessApply` 并在 CI 强制 spotlessCheck |
| m3 | `feature/player/.../PlayerMediaEventFacade.kt:69` | 日志引用已更新的旧快照值（`current.playModeBeforeNext` 在清空后打印），误导排查 | 先取局部 val 再更新状态、再打日志 |
| m4 | `app/.../PermissionCoordinator.kt:43,71-75` | `pendingPermissionRequest` 用 `remember` 而非 `rememberSaveable`，权限弹窗引发 Activity 重建时回调丢失 | 改 `rememberSaveable`，或把 pending 请求 id 上提为 VM 状态 |
| m5 | `app/.../AppNavHost.kt:133-134` | 返回方向转场完全无动画（`EnterTransition.None`），与前进 300ms 滑动不对称 | 若非刻意设计，补对称转场；若刻意，加注释说明 |
| m6 | `app/.../AppNavHost.kt:144-149` | `LaunchedEffect(songForInfo) { songInfo = loadSongInfo(it) }` 无 runCatching，同 C-2 崩溃路径 | runCatching 包裹 |
| m7 | `data/repository/MediaRepository.kt:95-98` | `loadSongs()` 每次调用都全量重恢复（清空重灌 + `syncLibraryCatalog` 全表重建） | 增加"已加载"短路标志 |
| m8 | `data/repository/MediaRepository.kt:538-541,719-725` | `getSongsByPlaylistId`/`getFavoriteSongs` 用 `mapNotNull { songs.find {...} }`，O(n²) | 先 `songs.associateBy { it.id }` 再映射 |
| m9 | `data/repository/MediaRepository.kt:376-381` | 导入循环内每首歌触发一次全 playlists 表重写（limitedParallelism(1) 串行兜底），1000 首 = 1000 次冗余事务 | 循环内抑制 persist，结束时统一一次（已有） |
| m10 | `player/.../QuickSkipCoordinator.kt:25-38` | `syncToPlaylist` 逐条 remove+add，每次全表重写，n 首 = 2n 次持久化 | 批量替换 songIds 后单次 persist |
| m11 | `player/.../PlayDurationTracker.kt:193` | `startedAtMs = now - totalDuration` 未剔除暂停时段，按日聚合的 playback_events 时段系统性漂移 | 逐段累计 event start，或以首个 startPlayback 时间为锚 |
| m12 | `player/.../PlaybackController.kt:314-324` | `playSingle` 未连接时仍返回 true（动作仅入 pending 队列），调用方可能提前清 UI 状态 | 返回语义改为"已受理"枚举，或未连接时返回 false |
| m13 | `player/.../EmotionAnalyzer.kt:43-49,333` | TFLite 双 Interpreter 在 `@Singleton` 首次注入线程加载（~15MB），可能卡启动帧；`close()` 定义了但无人调用 | 惰性加载 + 后台线程初始化；进程退出前无需 close，但应注释说明 |
| m14 | `player/.../PlaybackController.kt:62-64,110-114` | 单曲自然播完时 `onMediaItemEnded` 可能重复发两次（playWhenReady + transition 双信号），依赖上层去重 | controller 层一拍合并，或补注释固化去重责任方 |
| m15 | `domain/playback/RandomQueuePlanner.kt:57,135-139` | 双重 shuffle（外层无意义）；`trimToLimit` 从无序 Set 取前 N 删除，"最近播放"语义被稀释 | 移除外层 shuffle；改用带时间戳的 LinkedHashMap |
| m16 | `core/ui/.../ToastHost.kt:141-150,200` | `AnimatedVisibility(visible = true)` 恒真，条目被移除时直接离开组合，**退出动画永不播放**（死代码）；`remember(entry.id) { entry.id }` 无意义 | 用 `visible = entryVisible` 状态驱动；删除无效 remember |
| m17 | `feature/home/.../HomeScreen.kt:344-376`、`feature/playlist/.../PlaylistScreen.kt:73-116` | Composable 参数 31 个 / 43 个，Route/Screen 拆分了但 State/Actions 未覆盖到这两处 | 参照 `PlayerRoute` 模式收敛为 State + Actions 数据类 |
| m18 | `feature/user/.../VersionManagementComponents.kt:598` 与 `feature/home/.../HomeComponents.kt:112-114` | `copyToClipboard` 两处重复实现且 label 不一致 | 下沉到 `:core:ui` 统一实现 |
| m19 | `app/.../AppDestinations.kt` 与 `AppRoutes` | 路由前缀字符串（`"artist/"` 等）与模板字面量双份维护 | 从 `AppRoutes` 模板派生 pattern，单点维护 |
| m20 | `feature/player/.../PlayerViewModel.kt:33,197` | VM 直接暴露 runtime 内部仓引用给 `loadPlaybackStatsSnapshot`，破坏"门面只暴露 State+Actions"自洽性 | 该查询下沉到 runtime 内部方法 |
| m21 | `feature/home/.../HomeScreen.kt:441` | 进度条 `localPositionMs` 未 key 到歌曲切换，切歌瞬间可能短暂显示旧歌位置 | `remember(currentSong.id) { mutableStateOf(...) }` 或 `LaunchedEffect(currentSong.id)` 重置 |
| m22 | `app/src/test/.../ExampleUnitTest.kt` | 模板死代码（`assertEquals(4, 2+2)`） | 删除 |

### 3.2 文档漂移

| # | 问题 | 建议 |
|---|------|------|
| m23 | `CLAUDE.md` 写 "Room v8 / versionCode 28 / JVM heap 2048MB"，实际代码为 **v10 / versionCode 29 / 3072m** | 更新 CLAUDE.md，或在 CI 加一致性检查 |
| m24 | `AudioMetadataExtractor.kt:161-163` `String.format` 未带 `Locale`（lint 告警级），L229 硬编码 `/storage/emulated/0/`（展示用，风险可控但脆弱） | `String.format(Locale.US, ...)`；路径仅作展示兜底并注释 |
| m25 | `EmbeddedLyricsParser.kt:185`（单次 read 可能截断）、L243-246（ID3v2.4 帧大小未按 syncsafe 7bit/byte 解码）、L254（v2.2 header=6 时 dataLength 计算越界 4 字节） | 循环读满 buffer；按版本分支解析帧大小；越界处 clamp |

---

## 4. 安全性专项结论

- ✅ **签名密钥管理规范**：`key/`、`keystore.properties` 均已 gitignore 且 `git ls-files` 确认未入库；`-PrequireReleaseSigning=true` 开关防止 CI 误出 debug 签名的"正式包"（`app/build.gradle.kts:56-74`）。
- ✅ **无网络层**：全项目零 HTTP 客户端引用（无 OkHttp/Retrofit/HttpURLConnection），HTTPS 问题不适用；纯离线播放器减小了攻击面。
- ✅ **日志脱敏**：`AppLogger.kt:77-86` 对 content://、file://、蓝牙 MAC、路径正则脱敏，release 按 `BuildConfig.DEBUG` 降级；全局未捕获异常钩子已接入。
- ✅ **组件暴露最小化**：`MainActivity` 仅 MAIN/LAUNCHER；`AppMediaSessionService` `exported="false"` + `foregroundServiceType="mediaPlayback"`，且正确声明在 `:player` 清单（架构任务强制）；无 ContentProvider/BroadcastReceiver 暴露。注意：`exported="false"` 意味着 Android Auto/Wear 等外部控制器无法连接，当前纯本地场景可接受，未来接入需改 true 并加权限校验。
- ✅ **备份/隐私排除完备**：backup_rules / data_extraction_rules 覆盖 DB、DataStore、专辑缓存（架构任务强制验证）。
- ✅ **权限最小化**：无 READ_MEDIA_AUDIO/READ_EXTERNAL_STORAGE（走 SAF），POST_NOTIFICATIONS/BLUETOOTH_CONNECT 均为用户触发路径（架构任务强制验证）。
- ⚠️ 见 m1（fd 泄漏）与 M-3（SAF 调用线程）——非漏洞但有健壮性风险。
- ✅ **用户输入**：歌单重命名等来自 UI 的输入进入 Room 均走参数绑定，无 SQL 拼接；无 WebView，无 XSS 面者。

## 5. 构建与配置专项结论

- ✅ R8 minify + resource shrinking 开启，规则文件克制且必要（manifest 入口 keep、Room 边界、反射驱动的 FFmpeg decoder keep）。
- ✅ 版本目录统一管理（workmanager 除外，见 M-14）；KSP 与 Kotlin 版本严格匹配（2.0.21-1.0.28）。
- ✅ Gradle 并行 + 缓存 + in-process Kotlin 编译（Windows 确定性构建）；configuration cache 已用。
- ✅ benchmark 构建类型正确继承 release（`initWith`）+ debug 签名 + 非调试。
- ⚠️ 无多环境 flavor（dev/staging/prod）——对单机播放器可接受，但若未来接网络服务需提前规划。
- ✅ `BuildConfig` 使用正确且克制：仅 `MelodyApplication`/`LoggerModule`/`PerformanceTrace` 三处 `BuildConfig.DEBUG`。

## 6. 测试与健壮性专项结论

- **总量**：58 个测试文件（domain 12 / feature 23 / player 10 / data 7 / core 3 / app 3），远超平均 Android 项目。
- **抽查结论（8 个文件均为真实断言）**：
  - `MusicRepositoryRoomTest.kt`（486 行）：手写完整 FakeMelodyDao，断言孤儿 cross-ref 过滤、落盘顺序、titleOverride 双向校验——高质量；
  - `PlaybackStateStoreTest.kt`：真 DataStore（临时文件 + finally 清理）、legacy 回退、带日期注释的"空会话不清快照"回归（对应 2026-09-03 真机回归）；
  - `ControllerWindowSynchronizerTest.kt` / `PlayerPersistenceFacadeTest.kt`：时序语义与窗口钳位均为行为级断言；
  - `PlayerPlaybackProgressTickerTest.kt`：偏弱（未测 stop 后不再更新、interval 节流）。
- **缺口**（对应上文问题）：
  1. 导入异常路径（C-1）无测试；
  2. 统计并发（M-1 读改写竞态、M-5 scope 死亡）无测试；
  3. `PlaybackController` pendingActions 上限/丢弃逻辑未测；
  4. `EmotionViewModel` 0 测试；
  5. 无 `MusicRepository` 并发（导入期间读锁阻塞）测试。
- **try-catch 使用**：总体克制，主要问题恰相反——关键路径（导入、produceState、tracker）**缺**异常处理而非过度。

---

## 7. 值得肯定的亮点

1. **架构约束机器化**：`verifyProductArchitecture`（约 590 行）把模块边界、Route/Screen 模式、LazyColumn key、隐私排除、R8 设置等全部变成 CI 可执行的硬门槛——这是本项目最大的工程资产，文档承诺与代码现实高度一致。
2. **PlayerViewModel 名副其实的薄门面**：331 行零业务逻辑；18 个 facade 经 planner 纯函数（`PlaybackQueueActionPlanner` 等）组装，domain 层无依赖纯逻辑可测性极佳。
3. **positionMs 窄流**等高频状态隔离设计（含 live-session 竞态注释）是高质量工程实践——M-6/M-7 应向它对齐。
4. **事故复盘入码**：`ShortAccum`/`EmotionAnalyzer` 的 KDoc 记录了真机 OOM 与 SIGSEGV 复盘，含解码器回退矩阵；`PlaybackStateStore` 回归修复带日期与真机记录。教训文档化罕见地好。
5. **并发有真实思考**：ReadWriteLock 动机注释、`limitedParallelism(1)` 有序落盘、`ControllerQueueFingerprint` 防重复 sync、restore 双条件会合。
6. **回绕检测边角覆盖**：Media3 REPEAT_MODE_ALL 单项队列不触发 transition 等冷门行为有精确注释 + 专门单测。
7. **测试纪律**：fake 完整、清理彻底（scope.cancel + file.delete）、回归测试带业务动机注释。

---

## 8. 整体评分

| 维度 | 评分 | 权重 | 说明 |
|------|------|------|------|
| 架构与设计模式 | A- | 20% | 分层清晰且被强制执行；扣分在阻塞契约泄漏 domain、Uri 绑架 model |
| 可维护性与代码风格 | B | 15% | 注释质量高；超长 Composable、重复实现拉低 |
| 性能与内存 | C+ | 20% | runBlocking 泛滥 + 锁内 IO + 整壳重组是最大债务 |
| 安全性 | A- | 15% | 密钥/清单/日志/备份全对；无网络层天然减负 |
| 依赖与第三方库 | B+ | 10% | catalog 管理好；lifecycle 断层明显 |
| 测试与健壮性 | B+ | 10% | 真实测试密度高；异常路径缺口 |
| 国际化 | D | 5% | 仅 4 条资源，909 处硬编码 |
| 构建与配置 | A- | 5% | R8/签名/benchmark 到位 |

### **综合评级：B+**

架构设计与工程纪律（A- 水平）显著高于行业平均，但**稳定性细节（C-1/C-2）与性能债务（M-1/M-2/M-6/M-7）拖低了整体表现**。修复 Critical 与前 8 项 Major 后可达 A-。

### 改进路线建议

**第一阶段（本周内，稳定性）**：C-1 导入 finally 复位、C-2 produceState runCatching、M-5 SupervisorJob、M-9 scroll 索引钳位、M-12 startForegroundService——均为小改动大收益。

**第二阶段（两周，性能主路径）**：M-6 sleep timer 窄流、M-7 remember/下沉派生流、M-2 锁外提取封面、M-3 deleteSong suspend 化、m7/m8 O(n²) 修复。每项配一个性能基准（已有 :benchmark 模块可复用）。

**第三阶段（一个月，结构性）**：M-1 domain 接口 suspend 化（分批：先 PlayerSettingsRepository → PlayStatsRepository → 其余）、M-4 SharedFlow 替代回调、M-11 锁约定显式化、M-10 Uri/String 边界适配。

**第四阶段（按发布计划）**：M-13 i18n 抽取（先定出海与否，再决定 resConfigs 诚实化还是全量抽取）、M-14 lifecycle 升级（升 2.8.7 需回归 `collectAsStateWithLifecycle` 行为）、m23 文档对齐。

**持续建议**：把 C-1/C-2 类"异常路径必须有测试"写进 code review checklist；为 `PlayerRuntime` 编排层与 `PlaybackController` pendingActions 补测试；CI 已有 `check` 聚合任务，建议再挂 lint（当前未见 `lintOptions`/`lint {}` 配置，`abortOnError` 行为未显式管理）。
