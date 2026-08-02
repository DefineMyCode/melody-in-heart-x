# Melody in Heart — 项目开发计划（Phase 0–6 全量路线）

> 输入依据：`d:\Devs\Codes\Android\melody-in-heart-x\technical-proposal.md`
> 当前仓库状态：空工程（仅 `LICENSE` / `.gitignore` / `technical-proposal.md`）
> 开发环境：Windows（构建命令统一使用 `gradlew.bat`），目标平台 Android
> 包名约束：`applicationId` / 根包 = `cn.com.dcsgo.mihx`，所有模块代码包名统一挂在其下（见第 2、3 节）。
> **执行交付物**：本计划经批准后，将写入仓库 `docs/development-plan.md` 作为正式文档，并从 **Phase 0 脚手架** 开始落地（搭建 14 模块 Gradle 工程 + 质量门禁）。

---

## 1. 项目概述与目标

「Melody in Heart」是一款**纯本地、无广告、无网络依赖**的 Android 音乐播放器。本计划将技术方案落地为可执行的从 0 到 1 开发路线：以 **Media3 ExoPlayer + MediaSessionService** 为播放内核，以 **业务队列（`PlayQueue`）+ 窗口化 Controller 队列** 解决大曲库（1000+ 首）切歌性能，以 **Media3 默认媒体通知 + 媒体键 + 蓝牙监听** 实现系统级融合；在 14 个 Gradle 模块的分层架构下，用 `verifyProductArchitecture` + `spotlessCheck` 保证架构与风格不腐化。全部代码归属统一根包 `cn.com.dcsgo.mihx`。交付目标为方案第 9 节全部验收项通过：功能闭环、冷启动 < 1.5s、1000 首队列切歌 < 300ms、进度刷新稳定 500ms、release 可 R8 打包。

---

## 2. 模块与目录清单

根包统一为 `cn.com.dcsgo.mihx`；每个模块的 `namespace` = 其包路径；`applicationId` = `cn.com.dcsgo.mihx`。

| # | 模块路径 | 包路径 | 职责 | 关键初始文件 / 目录 |
|---|---------|--------|------|-------------------|
| 1 | `:app` | `cn.com.dcsgo.mihx` | 应用外壳：入口、根导航、权限协调、主题装配、Hilt 聚合 | `MelodyApplication.kt`(@HiltAndroidApp)、`MainActivity.kt`、`ui/MelodyApp.kt`(NavigationSuiteScaffold)、`navigation/MelodyNavHost.kt`、`navigation/MelodyDestination.kt`、`permission/PermissionCoordinator.kt`、`di/AppModule.kt`、`src/main/AndroidManifest.xml`、`src/main/res/xml/{backup_rules,data_extraction_rules}.xml`、`proguard-rules.pro` |
| 2 | `:core:model` | `cn.com.dcsgo.mihx.core.model` | 纯 Kotlin 数据模型，零 Android 依赖 | `Song.kt`、`SongInfo.kt`、`Playlist.kt`、`PlaylistSongRef.kt`、`PlayQueue.kt`、`PlayMode.kt`、`Lyrics.kt`(含 `LyricLine`) |
| 3 | `:core:common` | `cn.com.dcsgo.mihx.core.common` | 日志门面、协程调度、错误类型、时间格式化、性能埋点 | `log/AppLogger.kt`、`dispatcher/AppDispatchers.kt` + `dispatcher/Dispatcher.kt`(Qualifier)、`error/AppError.kt`、`result/AppResult.kt`、`time/DurationFormatter.kt`、`perf/PerfTracer.kt`、`di/CommonModule.kt` |
| 4 | `:core:ui` | `cn.com.dcsgo.mihx.core.ui` | 公共 Compose：主题、ToastHost、LyricsView、通用组件 | `theme/MelodyTheme.kt`、`theme/{Color,Type,Shape}.kt`、`toast/ToastHost.kt`、`toast/ToastController.kt`、`toast/ToastMessage.kt`、`lyrics/LyricsView.kt`、`component/{SongRow,SeekSlider,EmptyState,SectionHeader,PermissionRationaleDialog}.kt` |
| 5 | `:domain` | `cn.com.dcsgo.mihx.domain` | repository 接口 + 业务策略（队列 / 随机 / 状态同步 / 版本） | `repository/{SongRepository,PlaylistRepository,LyricsRepository,PlayStatsRepository,PlaybackStateRepository,PlayerSettingsRepository}.kt`、`queue/{ControllerQueuePlannerPort,ControllerQueuePlan,PlaybackWindowState,QueueManager,RandomQueuePlanner,UniformRandomPlanner}.kt`、`playback/{ControllerPlaybackSnapshot,PlayerUiState,PlaybackState,ControllerPlaybackStateSynchronizer}.kt`、`version/SongVersionResolver.kt` |
| 6 | `:data` | `cn.com.dcsgo.mihx.data` | Room + DAO + repository 实现 + 元数据/封面/歌词解析 + DataStore | `database/MelodyDatabase.kt`、`database/dao/MelodyDao.kt`、`database/entity/*.kt`(8 实体)、`repository/*RepositoryImpl.kt`、`metadata/MetadataExtractor.kt`、`artwork/ArtworkStore.kt`、`lyrics/{LrcParser,EmbeddedLyricsReader}.kt`、`datastore/PlayerSettingsDataStore.kt`、`datastore/PlaybackStateSnapshotSerializer.kt`、`saf/SafImporter.kt`、`di/DataModule.kt`、`schemas/`(Room exportSchema) |
| 7 | `:player` | `cn.com.dcsgo.mihx.player` | MediaSessionService + PlaybackController + 窗口化队列 + 蓝牙监听 | `service/AppMediaSessionService.kt`、`PlayerFactory.kt`、`PlaybackController.kt`、`PlayerPlaybackProgressTicker.kt`、`mapper/SongMediaItemMapper.kt`、`window/{ControllerQueuePlanner,PlaybackWindowPlanner,ControllerWindowSynchronizer,WindowedControllerQueuePlanner}.kt`、`bluetooth/{BluetoothStateManager,BluetoothPlaybackMonitor,BluetoothAudioQualityManager}.kt`、`stats/PlayDurationTracker.kt`、`di/PlayerModule.kt` |
| 8 | `:feature:home` | `cn.com.dcsgo.mihx.feature.home` | 首页曲库：列表、搜索多选、导入入口 | `HomeRoute.kt`、`HomeScreen.kt`、`HomeViewModel.kt`、`HomeFacade.kt`、`HomeUiState.kt`、`component/{SongListSection,SearchBar,MultiSelectBar}.kt` |
| 9 | `:feature:playlist` | `cn.com.dcsgo.mihx.feature.playlist` | 歌单列表与详情、歌单增删改 | `PlaylistRoute.kt`、`PlaylistScreen.kt`、`PlaylistDetailRoute.kt`、`PlaylistDetailScreen.kt`、`PlaylistViewModel.kt`、`PlaylistFacade.kt`、`PlaylistUiState.kt` |
| 10 | `:feature:user` | `cn.com.dcsgo.mihx.feature.user` | 播放统计、秒切记录、同名多版本管理 | `UserRoute.kt`、`UserScreen.kt`、`UserViewModel.kt`、`UserFacade.kt`、`component/{PlayStatsSection,SongVersionSection}.kt` |
| 11 | `:feature:lyrics` | `cn.com.dcsgo.mihx.feature.lyrics` | 歌词页：内嵌 + 外部 LRC 展示与滚动 | `LyricsRoute.kt`、`LyricsScreen.kt`、`LyricsViewModel.kt`、`LyricsFacade.kt`、`LyricsUiState.kt` |
| 12 | `:feature:player` | `cn.com.dcsgo.mihx.feature.player` | 播放页 + 队列面板；`PlayerRuntime` 承载播放逻辑 | `PlayerRoute.kt`、`PlayerScreen.kt`、`PlayerViewModel.kt`(薄门面)、`runtime/PlayerRuntime.kt`、`facade/{PlayerTransportFacade,PlayerQueueFacade,PlayerLyricsFacade}.kt`、`graph/PlayerGraph.kt`、`coordinator/PlayerStateCoordinator.kt`、`component/{QueuePanel,TransportBar,ProgressSlider}.kt` |
| 13 | `:feature:settings` | `cn.com.dcsgo.mihx.feature.settings` | 设置：均匀随机 / 无限播放 / 蓝牙 / 通知 / 主题 | `SettingsRoute.kt`、`SettingsScreen.kt`、`SettingsViewModel.kt`、`SettingsFacade.kt`、`SettingsUiState.kt` |
| 14 | `:benchmark` | `cn.com.dcsgo.mihx.benchmark` | Macrobenchmark 冷启动基准 | `ColdStartBenchmark.kt`、`BaselineProfileGenerator.kt`（可选）、`src/main/AndroidManifest.xml` |

**辅助构建目录（非产品模块，不计入 14 模块）**

```
build-logic/                      # includeBuild，约定插件
  convention/src/main/kotlin/
    mihx.android.application.gradle.kts
    mihx.android.library.gradle.kts
    mihx.android.library.compose.gradle.kts
    mihx.jvm.library.gradle.kts
    mihx.hilt.gradle.kts
    mihx.room.gradle.kts
    ArchitectureVerification.kt    # verifyProductArchitecture 任务实现
gradle/libs.versions.toml          # Version Catalog
```

---

## 3. 全局约定

### 3.1 包与标识

| 项 | 值 |
|----|----|
| 根包 / `applicationId` | `cn.com.dcsgo.mihx` |
| 各模块 `namespace` | 等于该模块包路径（见第 2 节表格） |
| `MediaItem.mediaId` | 必须为 `Song.id.toString()`（十进制），全局不变量 |
| 应用不申请 `INTERNET` 权限 | 由架构门禁强制校验 |

### 3.2 平台与构建基线

| 项 | 值 |
|----|----|
| `minSdk` | 33（Android 13） |
| `targetSdk` / `compileSdk` | 36（Android 16） |
| JDK / `jvmTarget` | 17 |
| 进程模型 | 单进程 |
| Gradle Wrapper | 8.14.x（与 AGP 8.13.x 匹配） |
| 构建配置 | Kotlin DSL + Version Catalog，全部依赖走 `libs.*`，禁止硬编码坐标 |

### 3.3 Version Catalog 关键条目（`gradle/libs.versions.toml`）

> Phase 0 第一项任务即**锁定并跑通版本矩阵**，下表为起始基线，实际以 Phase 0 锁定结果为准。

```toml
[versions]
agp                  = "8.13.0"
kotlin               = "2.0.21"      # 方案要求 Kotlin 2.0+
ksp                  = "2.0.21-1.0.28"   # 必须与 kotlin 严格对齐
media3               = "1.9.0"       # 方案要求 1.9.x
hilt                 = "2.57"
room                 = "2.7.1"
datastore            = "1.1.1"
navigationCompose    = "2.9.0"
composeBom           = "2025.04.01"
material3Adaptive    = "1.3.2"       # adaptive-navigation-suite
kotlinxSerialization = "1.7.3"
coil                 = "2.7.0"
benchmarkMacro       = "1.3.4"
spotless             = "6.25.0"
coreSplashscreen     = "1.0.1"

[libraries]
media3-exoplayer  = { module = "androidx.media3:media3-exoplayer",  version.ref = "media3" }
media3-session    = { module = "androidx.media3:media3-session",    version.ref = "media3" }
media3-common     = { module = "androidx.media3:media3-common",     version.ref = "media3" }
media3-ui-compose = { module = "androidx.media3:media3-ui-compose",  version.ref = "media3" }
# Jellyfin FFmpeg 解码扩展：版本形如 "<media3 版本>+<构建号>"，Phase 0 锁定实际可用版本
jellyfin-ffmpeg   = { module = "org.jellyfin.media3:media3-ffmpeg-decoder", version = "1.9.0+1" }
compose-m3-adaptive-nav-suite = { module = "androidx.compose.material3:material3-adaptive-navigation-suite", version.ref = "material3Adaptive" }
```

**解码扩展约束**：仅声明 `jellyfin-ffmpeg` 依赖，代码中**不得出现 `FfmpegAudioRenderer` 显式引用**；通过 `DefaultRenderersFactory.setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)` 反射加载。R8 需 keep 该扩展渲染器类。

### 3.4 架构门禁 `verifyProductArchitecture`

在 `build-logic` 注册的自定义 Gradle Task，`check.dependsOn(verifyProductArchitecture, spotlessCheck)`。规则集：

| 规则 ID | 内容 |
|---------|------|
| A1 包前缀 | 每个模块的所有 Kotlin 源文件 package 必须以该模块约定包路径开头（第 2 节表格） |
| A2 依赖方向 | `:core:model` 不依赖任何产品模块；`:core:common` 不依赖 `:domain`/`:data`/`:player`/`:feature:*`；`:domain` 仅可依赖 `:core:model`/`:core:common`；`:data`/`:player` 可依赖 `:domain`/`:core:*` 但不得依赖 `:feature:*`；`:feature:*` **不得依赖 `:data`**（依赖倒置）；仅 `:app` 可依赖全部 |
| A3 实现隔离 | `:feature:*` 与 `:domain` 源码不得 import `androidx.room.*`、`androidx.datastore.*` |
| A4 状态同步唯一性 | `PlayerUiState` 的构造/映射只允许出现在 `ControllerPlaybackStateSynchronizer` 中 |
| A5 队列不变量 | `player/window`、`core/model/PlayQueue`、队列相关源文件中禁止出现 `.distinct(`、`.associateBy(`、`.toSet()` 等按 `Song.id` 去重写法 |
| A6 无网络 | 合并后的 Manifest 不得包含 `android.permission.INTERNET` |
| A7 通知实现 | 全仓库禁止出现 `NotificationCompat.Builder`（媒体通知必须走 Media3 默认实现） |

### 3.5 代码风格与提交

- Spotless + ktlint，`*.kt` / `*.kts` 全覆盖，含 licenseHeader。
- 每阶段结束前必须绿灯：`gradlew.bat check`（= `spotlessCheck` + `verifyProductArchitecture` + 全量 JVM 单测）。

---

## 4. 实施路线总览

| Phase | 目标 | 关键交付物 | 估算（人周） | 前置依赖 |
|-------|------|-----------|------------|---------|
| **P0 脚手架** | 14 模块工程可编译可导航，门禁生效 | 空壳可运行 App + Version Catalog + `verifyProductArchitecture`/`spotlessCheck` | 1.0 | 无 |
| **P1 播放内核** | 单首本地音频稳定播放 | `AppMediaSessionService` + `PlayerFactory` + `PlaybackController` + 500ms tick | 1.5 | P0（模块骨架、Hilt、DI 装配） |
| **P2 播放队列** | 大曲库队列一致且不卡顿 | `PlayQueue` + 三种 `PlayMode` + 窗口化四件套 + `ControllerQueuePlannerTest` | 2.5 | P1（`PlaybackController` 队列设置接口、`mediaId` 不变量） |
| **P3 通知与集成** | 系统侧控制与 App 完全一致 | Media3 默认媒体通知 + 媒体键 + 蓝牙自动暂停 + `ToastHost` + 权限协调 | 1.5 | P1（MediaSession）、P2（队列行为一致性验证） |
| **P4 持久化与恢复** | 重启后状态可重建 | Room 8 实体 + `MelodyDao` + DataStore + `PlaybackStateSnapshotSerializer` | 1.5 | P2（队列结构定型，快照才有稳定 schema） |
| **P5 产品化功能** | 真实使用场景闭环 | SAF 导入/歌单/歌词/封面/统计/多版本/秒切/无限播放/均匀随机/搜索多选/主题 | 6.0 | P4（Room/DataStore 就绪）、P2（Planner 扩展点） |
| **P6 质量与性能** | 验收指标有基线数据 | 单测矩阵 + Compose 仪表化测试 + `ColdStartBenchmark` + 性能埋点 + release R8 | 2.0 | P5（功能齐备才能测端到端与冷启动） |
| | | **合计** | **16.0** | |

**阶段依赖图**

```
P0 ──> P1 ──> P2 ──┬──> P3 ──┐
                   └──> P4 ──┴──> P5 ──> P6
```

> P3 与 P4 在 P2 完成后可轻度并行（P3 偏系统集成、P4 偏数据层），单人开发时按 P3 → P4 串行。

---

## 5. 逐阶段详细计划

### Phase 0 — 脚手架（1.0 人周）

**目标**：建立 14 模块 Gradle 工程与质量门禁，产出可编译、可导航的空壳应用。

**任务清单**

- [ ] **P0-1 版本矩阵锁定**：确定 AGP 8.13.x / Gradle 8.14.x / Kotlin 2.0.21 / KSP 对齐版本 / Compose BOM / Media3 1.9.x / Jellyfin FFmpeg 实际可用版本，写入 `gradle/libs.versions.toml`。
- [ ] **P0-2 Wrapper 与根配置**：`gradlew.bat` 初始化；`gradle.properties`（`org.gradle.jvmargs=-Xmx4g`、`android.useAndroidX=true`、`android.nonTransitiveRClass=true`、`org.gradle.caching=true`、`org.gradle.configuration-cache=true`）。
- [ ] **P0-3 `settings.gradle.kts`**：`includeBuild("build-logic")` + 声明全部 14 个模块（`:app`、`:core:model`、`:core:common`、`:core:ui`、`:domain`、`:data`、`:player`、`:feature:home`、`:feature:playlist`、`:feature:user`、`:feature:lyrics`、`:feature:player`、`:feature:settings`、`:benchmark`）。
- [ ] **P0-4 约定插件**：`build-logic` 实现 `mihx.android.application` / `mihx.android.library` / `mihx.android.library.compose` / `mihx.jvm.library` / `mihx.hilt` / `mihx.room`，统一 minSdk 33 / compileSdk 36 / jvmTarget 17。
- [ ] **P0-5 各模块 `build.gradle.kts` + `namespace`**：按第 2 节包路径逐一落地；`:core:model`、`:domain` 使用 `mihx.jvm.library`（纯 Kotlin，加速单测）。
- [ ] **P0-6 `:app` 骨架**：`MelodyApplication`(@HiltAndroidApp)、`MainActivity`(@AndroidEntryPoint + `enableEdgeToEdge`)、`MelodyApp` 使用 `NavigationSuiteScaffold`、`MelodyNavHost` 注册 6 条 feature 路由（各 feature 先给占位 `*Route`/`*Screen`）。
- [ ] **P0-7 `:core:ui` / `:core:common` 最小实现**：`MelodyTheme`（Material3 + 动态取色）、`AppLogger`、`AppDispatchers` + `@Dispatcher` Qualifier、`PerfTracer` 空实现。
- [ ] **P0-8 门禁落地**：实现 `verifyProductArchitecture`（规则 A1/A2/A6/A7 先行，A3/A4/A5 随对应阶段补齐）；接入 Spotless；`check.dependsOn(...)`。
- [ ] **P0-9 release 与备份配置**：`release` buildType 开启 `isMinifyEnabled` + `isShrinkResources`；`backup_rules.xml` / `data_extraction_rules.xml` 排除播放状态、URI 权限、Room、DataStore、封面缓存。
- [ ] **P0-10 CI 脚本占位**：`gradlew.bat check` + `gradlew.bat :app:assembleRelease`。

**验收标准**（对应 §9 兼容与质量）

- `gradlew.bat :app:assembleDebug` 成功；真机/模拟器（API 33 与 API 36）可安装启动。
- 6 条路由可在自适应导航中切换，无崩溃。
- `gradlew.bat check` 通过（`spotlessCheck` + `verifyProductArchitecture`）。
- `gradlew.bat :app:assembleRelease` 通过（R8 + 资源压缩）。
- 故意在 `:feature:home` 加入对 `:data` 的依赖，`verifyProductArchitecture` **必须失败**（门禁反向验证）。

**风险与对策**

| 风险 | 对策 |
|------|------|
| AGP/Kotlin/KSP/Compose 版本矩阵不兼容（新增） | 版本目录集中治理；P0-1 先用空壳全量编译验证；出问题只回退单一维度 |
| Windows 路径过长 / Gradle 文件锁（新增） | 仓库置于短路径；构建前 `gradlew.bat --stop` 释放 daemon；`.gitignore` 已排除 `build/` |
| 门禁形同虚设（映射 §10-6） | 每条规则配一个"应当失败"的反向验证用例 |

---

### Phase 1 — 播放内核（1.5 人周）

**目标**：实现后台可靠播放单首本地音频，建立 Media3 为传输状态唯一事实来源的骨架。

**任务清单**

- [ ] **P1-1 `:player` 依赖接入**：`media3-exoplayer`、`media3-session`、`media3-common` + `jellyfin-ffmpeg`。
- [ ] **P1-2 `PlayerFactory`**：`DefaultRenderersFactory(context).setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)`；配置 `AudioAttributes(USAGE_MEDIA, CONTENT_TYPE_MUSIC)` 与 `setHandleAudioBecomingNoisy(true)`；**不显式引用 `FfmpegAudioRenderer`**。
- [ ] **P1-3 `AppMediaSessionService`**：继承 `MediaSessionService`，`@AndroidEntryPoint`；`onCreate` 构建 `ExoPlayer` + `MediaSession`（`setSessionActivity` 指向 `MainActivity`）；`onGetSession` 返回会话；`onTaskRemoved` / `onDestroy` 中直接从 ExoPlayer 读取最后 `mediaId` 与 `currentPosition`（P1 先落日志 + 预留 `PlaybackStateRepository` 接口调用点，P4 接实现）。
- [ ] **P1-4 Manifest 声明**：`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PLAYBACK`、`READ_MEDIA_AUDIO`；`<service>` 带 `android:foregroundServiceType="mediaPlayback"` 与 `androidx.media3.session.MediaSessionService` intent-filter。
- [ ] **P1-5 `PlaybackController`**：连接 `MediaController`（`MediaController.Builder` + `ListenableFuture`），注册 `Player.Listener`，将回调转为 `ControllerPlaybackSnapshot`（`StateFlow`）；对外暴露 `play/pause/seekTo/seekToPreviousMediaItem/seekToNextMediaItem/setQueue`；连接失败仅输出脱敏日志并暴露可恢复动作。
- [ ] **P1-6 状态同步链路（`:domain`）**：定义 `ControllerPlaybackSnapshot`、`PlayerUiState`、`PlaybackState`（`idle/preparing/ready/playing/paused/buffering/ended/error`）；实现 `ControllerPlaybackStateSynchronizer` 作为**唯一**映射点；时长仅接受非负快照。
- [ ] **P1-7 `PlayerPlaybackProgressTicker`**：默认 500ms；仅在 `isPlaying == true` 且前台可见时 tick；生命周期感知（`repeatOnLifecycle`）。
- [ ] **P1-8 `SongMediaItemMapper`**：`MediaItem.mediaId = Song.id.toString()`；填充 `MediaMetadata`（title/artist/album/artworkUri），为 P3 通知展示打底。
- [ ] **P1-9 `:feature:player` 最小闭环**：`PlayerRoute`/`PlayerScreen`/`PlayerViewModel`（薄门面）+ `PlayerRuntime` 骨架 + `PlayerTransportFacade`；Slider 拖动使用本地 UI 状态即时反馈，松手才 `seek`。
- [ ] **P1-10 临时曲源**：`MediaStore` 读取一批本地音频作为 P1 调试源（正式 SAF 导入在 P5）。
- [ ] **P1-11 FFmpeg 生效验证**：日志打印实际选中的 audio renderer 类名，验证扩展渲染器被优先加载；准备 mp3/flac/wav/m4a/ogg/aac/opus/wma 与高采样率样本各一。

**验收标准**（对应 §9 功能第 1 项）

- 本地音频可播放、暂停、拖动进度、上一首/下一首。
- 息屏 / 切后台 / 切换其他 App 时播放不中断。
- 进度按 500ms 刷新，UI 无跳变。
- 8 类格式样本 + 高采样率样本可播；日志确认 FFmpeg 扩展渲染器优先。
- `PlayerUiState` 构造点全局唯一（门禁规则 A4 启用）。

**风险与对策**

| 风险 | 对策 |
|------|------|
| 解码兼容性（§10-3） | FFmpeg 扩展渲染器 + `PREFER` 模式；无法解码时记录并在 UI 提示（不可播放项过滤在 P2 落地） |
| Android 14+ 前台服务类型校验失败（新增） | 声明 `mediaPlayback` 类型；启动前确保已有活跃 `MediaSession` |
| 反射加载类被 R8 剥离（新增，前置 §10-3） | `proguard-rules.pro` 提前加入 FFmpeg 扩展渲染器 keep 规则，P0 的 release 打包已可验证 |

---

### Phase 2 — 播放队列（2.5 人周）

**目标**：实现业务队列 + 窗口化 Controller 队列，使 1000 首曲库下切歌与模式切换不卡顿，且 App / 通知 / 耳机行为完全一致。

**任务清单**

- [ ] **P2-1 `PlayQueue`（`:core:model`）**：`songs: List<Song>`、`currentIndex: Int`、`playMode: PlayMode`、`playOrderIds: List<Long>`；实现 `currentPlayOrderIndices()`——按 ID 出现次数还原为队列项索引，**严禁 `distinct()` / `associateBy(id)`**；`PlayQueue` 不持有上一首/下一首导航逻辑。
- [ ] **P2-2 `PlayMode` 枚举**：`SEQUENTIAL` / `REVERSE` / `RANDOM`。
- [ ] **P2-3 `:domain` 队列策略层**：`ControllerQueuePlannerPort`（接口）、`ControllerQueuePlan`（数据）、`PlaybackWindowState`、`QueueManager.PlayOrderBuilder`、`RandomQueuePlanner`、`UniformRandomPlanner`（权重 = 原始播放次数，播放次数通过可注入 provider 提供，P2 用 stub，P5 接真实统计）。
- [ ] **P2-4 `ControllerQueuePlanner`（`:player/window`）**：按播放模式将完整业务队列展开为完整可播放顺序。
- [ ] **P2-5 `PlaybackWindowPlanner`**：截取当前项**前 20 首 + 后 50 首**（窗口 ≤ 71 项），过滤不可播放项（默认 `uri != null`）。
- [ ] **P2-6 `ControllerWindowSynchronizer`**：缓存最近 `PlaybackWindowState`；窗口失效或显式 `force` 时重新生成 `ControllerQueuePlan`；接近窗口边缘（剩余项 < 阈值，建议 10）时触发重规划。
- [ ] **P2-7 `WindowedControllerQueuePlanner`**：实现 `ControllerQueuePlannerPort`；读取 `PlayQueue.currentPlayOrderIndices()` → 按索引映射回 `Song`（**保留重复项**）→ 计算窗口内 `startIndex`；请求项不可播放时优先取其后可播放项，否则回退前一可播放项。
- [ ] **P2-8 队列操作契约（`PlayerQueueFacade`）**：
  - `addSongAsNext(...)`：更新业务队列与 `playOrderIds` → 同步 Controller；批量按歌曲 ID 去重并整体移动到当前项之后，当前项作锚点；下一首由 `seekToNextMediaItem()` 决定。
  - `addSongsToTail(...)`：单首去重；批量允许重复追加。
  - `switchPlayMode(mode)`：重建 `playOrderIds`（RANDOM 走 `UniformRandomPlanner`）→ 经 Planner 重建 Controller 队列，保留当前项。
  - `removeAt(queueIndex)`：队列面板**按队列索引**移除具体一项。
- [ ] **P2-9 UI 接线**：`PlayerRuntime` + `PlayerGraph` + `PlayerStateCoordinator` 串联 `PlaybackController` ↔ `ControllerQueuePlannerPort` ↔ `ControllerPlaybackStateSynchronizer`；`QueuePanel` 展示完整 `songs`，按队列索引高亮与移除。
- [ ] **P2-10 单测 `ControllerQueuePlannerTest`**（纯 JVM，6 类用例）：顺序 / 倒序 / 添加到下一首 / 切换模式 / 不可播放跳过 / 重复项；另加 `PlayQueueTest`（`currentPlayOrderIndices` 重复项还原）、`PlaybackWindowPlannerTest`（边界：队首、队尾、窗口不足 71、全不可播放）。
- [ ] **P2-11 门禁 A5 启用**：队列相关源文件禁止按 `Song.id` 去重写法。
- [ ] **P2-12 性能埋点**：`PerfTracer` 记录「队列同步耗时」「切歌耗时」，构造 1000 首数据集实测。

**验收标准**（对应 §9 功能第 2、5 项与性能第 2 项）

- 顺序 / 倒序 / 随机三种模式行为正确；模式切换后当前播放项不中断。
- 「添加到下一首」在 App 内、通知栏、耳机三端表现一致。
- 1000 首队列下切歌 < 300ms 完成队列同步与播放，无 `setMediaItems` 全量卡顿。
- 同一首歌在队列出现多次时，UI 高亮定位到正确的队列项、移除只删该项、Controller 队列不丢项。
- `ControllerQueuePlannerTest` 6 类用例全绿；`gradlew.bat check` 通过。

**风险与对策**

| 风险 | 对策 |
|------|------|
| 大曲库全量 `setMediaItems` 卡顿（§10-1） | 窗口化队列（前 20 后 50，≤71 项）；仅同步当前窗口；`PerfTracer` 埋点持续监测 |
| 重复队列项映射错误导致 UI 高亮跳回首处（§10-5） | `mediaId → Song.id` 唯一映射 + 一切操作按队列索引；当前索引保留规则；门禁 A5 + 重复项专项单测 |
| 窗口边界抖动引发频繁重规划（新增） | `ControllerWindowSynchronizer` 缓存 `PlaybackWindowState` + 边缘阈值滞后，避免每次切歌都重建 |
| 测试不足（§10-6） | Planner 全部为纯 Kotlin，放在 `mihx.jvm.library` 模块，优先补齐单测 |

---

### Phase 3 — 系统通知与集成（1.5 人周）

**目标**：通知栏 / 锁屏 / 控制中心 / 耳机 / 蓝牙与 App 内行为完全一致且实时同步。

**任务清单**

- [x] **P3-1 Media3 默认媒体通知**：使用 `MediaSessionService` 默认通知生命周期，展示封面 / 标题 / 艺术家 + 上一首 / 暂停 / 下一首三按钮；**不编写任何 `NotificationCompat`**（门禁 A7）。
- [x] **P3-2 元数据补全**：确认 `SongMediaItemMapper` 输出的 `MediaMetadata` 含 `artworkUri`（或 `artworkData`），保证锁屏封面正确显示。
- [x] **P3-3 耳机线控**：经 `MediaSession` / `MediaController` 接入播放/暂停/上一首/下一首媒体按键；**不新增广播接收器**；验证有线耳机单击 / 双击 / 三击。
- [x] **P3-4 `BluetoothStateManager`**：监听蓝牙连接/断开（`BluetoothProfile` 状态 + `ACTION_ACL_DISCONNECTED`），对外暴露连接态 `Flow`。
- [x] **P3-5 `BluetoothPlaybackMonitor`**：`ACTION_AUDIO_BECOMING_NOISY` 类事件 → 自动暂停；与 `PlayerFactory` 的 `setHandleAudioBecomingNoisy(true)` 形成双保险，避免重复暂停。
- [x] **P3-6 `BluetoothAudioQualityManager`**：读取当前音频输出路由（LE Audio / A2DP / SCO / USB / 有线 / 扬声器）及其上报的采样率、声道数，暴露给设置页与播放页信息展示。**范围修正**：蓝牙编解码器（SBC/AAC/aptX/LDAC）对第三方应用不可读——`BluetoothA2dp.getCodecStatus()` 与 `ACTION_CODEC_CONFIG_CHANGED` 均为隐藏 `@SystemApi`，且该广播以 `BLUETOOTH_PRIVILEGED` 发送，普通应用拿不到；改用 `AudioManager` + `AudioDeviceCallback` 的公开路由信息替代。
- [x] **P3-7 `PermissionCoordinator`（`:app`）**：`POST_NOTIFICATIONS`、`BLUETOOTH_CONNECT`、`READ_MEDIA_AUDIO` **按需申请**（触发点才申请，启动不强制弹窗）；提供拒绝文案与降级路径（无通知权限仍可后台播放，仅无通知展示；无蓝牙权限仅关闭蓝牙相关能力）。
- [x] **P3-8 `ToastHost` + `ToastController`（`:core:ui`）**：顶部 Toast，多条堆积、2 秒自动消失、可手动关闭（点击整条关闭）；全局替代 Snackbar。`ToastHost` 经 `LocalToastController` 在 `MelodyApp` 一次性提供，feature 模块无需依赖 `:app` 即可 raise toast（满足门禁 A2）。已接入权限被拒场景：`PermissionHost` 拒绝时显示 `PermissionCoordinator` 的降级文案，`PlayerScreen` 读取权限被拒时显示空曲库降级文案。注：导入完成 / 复制成功 / 删除确认场景待对应功能（P5/P6）落地后再接线。
- [x] **P3-9 音频焦点**：`setAudioAttributes(attrs, handleAudioFocus = true)`；被打断后按系统语义恢复。
- [x] **P3-10 一致性回归**：四端（App UI 按钮 / Media3 通知 / 锁屏 / 耳机媒体键）的 上一首 / 下一首 均经 `PlayerTransportFacade` → `PlaybackController.seekToNextMediaItem()/seekToPreviousMediaItem()`（ExoPlayer 直接跳媒体项，无 3s 回退语义），语义一致；窗口边界由 `ControllerWindowSynchronizer` 自动重规划兜底。`添加到下一首` 经 `PlayerQueueFacade.addSongAsNext` → `QueueOperator.addSongAsNext`。新增 `QueueConsistencyRegressionTest`（domain）覆盖边界 prev/next、add-to-next 命中、重复 id 定位、RANDOM 保留当前项等共享契约，作为四端一致性的回归基线。

**验收标准**（对应 §9 功能第 3 项）

- 通知栏与锁屏显示封面 / 标题 / 艺术家，三按钮可控且与 App 状态实时同步。
- 有线耳机媒体键生效；蓝牙耳机断开后自动暂停（不继续外放）。
- 权限按需申请，冷启动不强制弹窗；拒绝后有明确文案且核心播放不受影响。
- 系统媒体键与 App 内按钮均经过 `ControllerPlaybackStateSynchronizer` 同一路径（代码走查 + 门禁 A4）。

**风险与对策**

| 风险 | 对策 |
|------|------|
| 权限被拒影响功能（§10-4） | 按需申请 + 拒绝文案 + 降级路径（通知不可用不阻断播放） |
| 厂商蓝牙广播行为差异（映射 §10-2 的厂商差异） | `ACTION_AUDIO_BECOMING_NOISY` 与蓝牙 profile 状态双通道；至少覆盖 2 家厂商真机验证 |
| Media3 默认通知样式受限（§8 决策代价） | 接受既定取舍；业务级提示统一走 `ToastHost` |

---

### Phase 4 — 持久化与恢复（1.5 人周）

**目标**：进程重启后可完整恢复队列 / 模式 / 当前歌曲 / 播放位置（暂停态，不自动播放）。

**任务清单**

- [x] **P4-1 Room 数据库**：`MelodyDatabase`（schema version 1，`exportSchema = true`，`schemas/` 纳入 git）；8 实体——`SongEntity`、`PlaylistEntity`、`PlaylistSongCrossRefEntity`、`PlayStatsEntity`、`SkipSongEntity`、`ShortPlayCountEntity`、`SongGroupOverrideEntity`、`MigrationStateEntity`；以单一 `MelodyDao` 起步，后续按聚合根演进。
- [x] **P4-2 Repository 实现（`:data`）**：`SongRepositoryImpl`、`PlaylistRepositoryImpl`、`PlaybackStateRepositoryImpl`、`PlayerSettingsRepositoryImpl`、`PlayStatsRepositoryImpl`；Hilt `@Binds` 到 `:domain` 接口（门禁 A2/A3 保证 `:feature:*` 不直接依赖 `:data`）。
- [x] **P4-3 `PlayerSettingsDataStore`**：Preferences DataStore 存储全局均匀随机（默认开）、蓝牙开关、通知开关、无限播放开关、主题模式。
- [x] **P4-4 `PlaybackStateSnapshotSerializer`**：kotlinx.serialization 结构化 JSON，**禁止内联正则解析**；快照字段包含 `songIds`（按队列项顺序，允许重复）、`currentIndex`、`playMode`、`positionMs`、`currentMediaId`、`savedAt`。
- [x] **P4-5 保存策略**：播放中节流保存（建议 5s 或位置变化超阈值）；`onIsPlayingChanged(false)` 且非缓冲时保存；`AppMediaSessionService.onTaskRemoved` / `onDestroy` 兜底保存（接上 P1-3 预留点）；系统直接杀进程时回退到最近一次定期保存。
- [x] **P4-6 恢复流程**：启动读取快照 → 按 `songIds` 逐项（保留重复）从 Room 还原 `PlayQueue` → 经 `WindowedControllerQueuePlanner` 规划窗口 → `setMediaItems` + `seekTo(positionMs)` + `prepare()`，**不调用 `play()`**（暂停态）。
- [x] **P4-7 状态机副作用接线**：按 §6 表格实现 `ready`/`paused` 保存位置、`buffering` 不视为有意暂停不保存暂停态、`ended` 停止统计。
- [x] **P4-8 备份规则校验**：`backup_rules.xml` / `data_extraction_rules.xml` 排除播放状态、URI 权限、Room 数据库、DataStore、封面缓存。
- [x] **P4-9 单测**：`PlaybackStateSnapshotSerializerTest`（序列化往返、字段缺失容错、版本兼容）、`PlayQueueRestoreTest`（含重复项队列往返一致）、Room DAO 测试（in-memory）。→ `PlaybackStateSnapshotSerializerTest` 已落 `:data/src/test`（JUnit4，AGP unit test harness）；`PlayQueueRestoreTest` 与 Room DAO in-memory 测试随 P4-6 接线一并补（依赖恢复映射与 Room 测试装置）。

**验收标准**（对应 §9 功能第 4、5 项）

- 正常退出并重启后，恢复队列、播放模式、当前歌曲、播放位置，且处于**暂停**状态。
- 从最近任务列表划掉 App 后重启，可恢复到最后一次保存点（误差 ≤ 节流间隔）。
- 队列含重复歌曲时，保存/恢复后队列项数量与顺序完全一致，`currentIndex` 指向正确项。
- 序列化与恢复单测全绿；`gradlew.bat check` 通过。

**风险与对策**

| 风险 | 对策 |
|------|------|
| 厂商系统任务管理差异导致恢复不完整（§10-2） | 播放中定期快照 + `onTaskRemoved`/`onDestroy` 回调兜底；至少 2 家厂商真机验证「划掉任务」场景 |
| 重复队列项在快照往返中被压缩（§10-5） | 快照按队列项序列化（不去重）；专项往返单测 |
| Room schema 演进破坏用户数据（§8 持久化代价） | `exportSchema` + schema 文件入库；每次 version bump 必须配 Migration + 迁移测试 |
| 快照写入过于频繁引起 I/O 与耗电（新增） | 节流 + 仅在位置变化超阈值时写；DataStore 异步写入 |

---

### Phase 5 — 产品化功能（6.0 人周，拆 A/B/C 三块）

**目标**：补齐配套功能，形成「导入 → 播放 → 歌单 → 歌词 → 统计」完整闭环。

#### 5A 曲库与导入（2.0 人周）

- [x] `SafImporter`（`:data/saf`）：`ACTION_OPEN_DOCUMENT_TREE` / 多文件选择；`takePersistableUriPermission` 持久化 URI 权限。
- [x] `MetadataExtractor`：`MediaMetadataRetriever` 解析 title/artist/album/duration/sampleRate；解析在 IO dispatcher 分批执行，进度经首页进度条反馈。
- [x] 入库与去重：按 URI 去重写入 `SongEntity`；解析失败项标记为不可播放（`playable = false`），与 P2 的窗口过滤对齐。
- [x] `:feature:home`：曲库列表（LazyColumn + key 为稳定 id）、搜索、多选模式（删除、批量加入歌单、批量加入队列——`HomeViewModel.addSelectedToQueue` 经 `PlayerQueueFacade.addSongsToTail`；`feature:home` 增 `:feature:player` 依赖，feature→feature 无环）。
- [x] `PerfTracer` 埋点：`PerfTracer.record` 落 `AppLogger`（`perf <label>=<ms>ms`）；`HomeViewModel.runImport` 计时并按导入曲目数分档（`import_1_50` / `_51_200` / `_201_500` / `_501_plus`）。

#### 5B 歌单 / 歌词 / 封面（2.0 人周）

- [x] `:feature:playlist`：歌单 CRUD、歌单详情、歌曲增删与排序；基于 `PlaylistEntity` + `PlaylistSongCrossRefEntity` 多对多。
- [x] `EmbeddedLyricsReader`（`:data/lyrics`）：读取内嵌歌词（MP3 ID3v2 `USLT` / FLAC Vorbis `LYRICS`，仅读文件头部，`MAX_BYTES=512KB`）；`LyricsRepositoryImpl` 优先外部 `.lrc`，回退内嵌，**进程级内存缓存**（`ConcurrentHashMap<Long, Lyrics>`，首次读取后二次进入秒开）。外部 LRC 查找：`DocumentFile.parentFile` 走目录 + **回退直接替换扩展名构造 sibling `content://` URI**（覆盖文件夹导入整树授权；单文件授权不覆盖 `.lrc` 时 `SecurityException` 吞掉返回 null）。`LyricsViewModel` 对非同步歌词（无时间戳，`timeMs` 全 0）不做高亮/自动滚动、`onLineClick` 不误 seek。
- [x] `LrcParser`：解析外部 `.lrc`（同名同目录优先），输出 `Lyrics` / `LyricLine` 时间轴。
- [x] `LyricsView`（`:core:ui`）+ `:feature:lyrics`：逐行高亮、自动滚动、点击行 seek（经 `PlaybackController` 实时进度联动）。
- [x] `ArtworkStore`（`:data/artwork`）：封面提取与磁盘缓存（受限尺寸），Coil 加载；缓存目录纳入备份排除。

#### 5C 策略、统计与设置（2.0 人周）

- [x] `PlayDurationTracker`（`:player/stats`）：挂在服务侧 `ExoPlayer` 上（UI 退出后统计仍累加），`playing` 时累计、`paused`/切曲/`ended` 时结算，写入 `PlayStatsEntity`（新增 `totalPlayedMs`，DB 升 v2）。时长算术落在纯 Kotlin 的 `PlaybackDurationAccumulator`（`:domain/stats`，10 条 JVM 单测）。
- [x] 秒切检测：转场 reason 非 `AUTO`/`REPEAT` 即判定用户主动切走 → `SkipSongEntity`；其中播放时长 < 30s 再计 `ShortPlayCountEntity`（秒切）。
- [x] 同名多版本管理：`Song.titleOverride` + 派生 `groupKey = titleOverride ?: title`；`SongVersionResolver`（`:domain/version`，纯函数）按 `preferredSongId` > `sampleRate` 最高 > 首个 选优（6 条 JVM 单测）；`SongGroupOverrideEntity`（v1 已建表）存用户覆盖，`SongGroupOverrideRepository`（domain 接口 + data Impl + `RepositoryBinder` @Binds，DAO 补 `observeGroupOverrides`/`deleteGroupOverride`）；`:feature:user` 提供版本管理 UI（按 `groupKey` 分组展示各版本与采样率，RadioButton 选中有效首选，点击切换/再点取消覆盖回自动选优）。
- [x] 无限播放：接近队尾时（剩余 ≤ 10 首），从尚未覆盖的本地可播放歌曲中补一批到队尾（`InfiniteQueueExtender` 走 `RandomQueuePlanner` 打乱；全库覆盖后循环补），尽量循环覆盖全库。配套补上**播放漂移窗口滑动**：`ControllerWindowSynchronizer.resolveDrift` 输出 `WindowSlide`（None / Incremental 增删 / Rebuild），`PlayerQueueController.slideWindow` 用 `addMediaItems`/`removeMediaItems` 就地滑动（绝不重建、当前歌曲不中断）；`PlayerQueueFacadeImpl` 监听 `currentQueueIndex` 提交实时播放位置并触发补队列（补队列只更新业务队列、不动传输层，避免当前歌从头重播）。
- [x] 全局均匀随机：`PlayerQueueFacadeImpl` 镜像 `PlayStatsRepository.observeStats()` 的播放次数与 `observeUniformRandomEnabled()` 开关，`switchPlayMode(RANDOM)` 传真实权重（默认开启）；关闭时传空权重 → `DefaultUniformRandomPlanner` 自然退化为纯 `shuffled()`。**`PlayQueue` 不直接依赖仓库**（权重经门面镜像注入）。
- [x] `:feature:settings`：均匀随机 / 无限播放 / 蓝牙 / 通知 / 主题（动态取色、深浅色）开关，绑定 `PlayerSettingsDataStore`。`SettingsFacade`（interface + `SettingsFacadeImpl` + `SettingsFeatureModule` @Binds）薄封装 `PlayerSettingsRepository`，`SettingsViewModel` 六个开关各自 collect Flow 进 `SettingsUiState`；`SettingsScreen` 用 `ListItem+Switch` 与 `FilterChip`（跟随系统/浅色/深色）。开关全部**真实生效**：均匀随机/无限播放已由 `PlayerQueueFacadeImpl` 观察（C2/C3）；蓝牙/通知在 `AppMediaSessionService.onCreate` 读开关后条件启动 `BluetoothPlaybackMonitor` / `setMediaNotificationProvider`；主题经 `MainActivity` 注入仓库 → `MelodyApp` collect `ThemeMode`+dynamicColor 后驱动 `MelodyTheme`（SYSTEM 回退系统值）。
- [x] `:feature:user`：播放统计页（总时长、Top 曲目、秒切列表）。`UserFacade`（interface + Impl + `UserFeatureModule` @Binds）组合 `SongRepository.observeAll()` + `PlayStatsRepository.observeStats()` + `SongGroupOverrideRepository.observeOverrides()`；`UserViewModel` 用 `combine` 一次成型：总收听时长 = `sumOf(totalPlayedMs)`、Top 曲目按 playCount 降序取 10、秒切列表按 skipCount 排序（含 shortPlayCount）；`UserScreen` 用 Card 统计卡 + 列表行展示。
- [x] UI 主题体系（黑白灰中性设计定稿，`docs/ui-preview.html` 预览）：`theme/Color.kt` 替换模板紫为 Monochrome 灰阶两套（Light primary `#1D1B20` / Dark primary `#E0E0E0`，surface 灰阶分层，error 保留语义红）；`Shape.kt` 4/8/16 → 6/10/16dp；`Type.kt` 沿用默认字阶。**dynamicColor 默认改 false**（`MelodyTheme` 参数默认、`MelodyApp` initialValue、`PlayerSettingsRepositoryImpl` read/observe 三处，开关保留）。共享组件去重：`SectionHeader` 提取到 `:core:ui`（Settings/User 删私有版）、`SongRow` 合并 core 封面版与 Home 多选版（新增 `selected/selectable/showCover` 参数）、`SeekSlider` 接入 PlayerScreen、`EmptyState` 接入 Home/Playlist/Lyrics 空态；`PlayerScreen` 视觉：Slider→SeekSlider、封面 16dp 圆角、曲名 titleLarge + 艺人 bodyMedium 两行。逐页打磨（本轮）：`LyricsView` 当前行 primary 高亮 + 其余行 onSurfaceVariant 弱化 + 行距 8dp（原仅字阶区分、无颜色）；`LyricsScreen` 空态改 `EmptyState`；全仓扫描确认无硬编码颜色（全部跟随主题）。第二轮：歌词页 TopAppBar 返回箭头（`LyricsRoute(onBack)` + `MelodyNavHost.navigateUp`）、Home 搜索框 `Search` leading 图标、Player 滑块 `mm:ss` 时间标签（拖拽显示拖动位置）。第三轮：**系统栏适配**——`Color.kt` 补 `surfaceContainer*`/`surfaceBright/Dim` 中性覆写（消底部 NavigationBar 紫调）；`MelodyNavHost` 给 NavHost 加 `windowInsetsPadding(statusBars)`（页面内容不延伸到状态栏）；**启动界面**——`Theme.Melody` 改 values(-night) 深浅双 parent（原强制 Light 导致深色启动闪白），`windowBackground` 深色纯黑 `#000000`/浅色 `#FBFBFB`，放弃自定义 Splash（移除 `installSplashScreen`/`Theme.Melody.Starting`/音符图标），Android 12+ 系统默认启动画面直接进应用。第四轮：**底部导航改 3 tab**（曲库 `LibraryMusic` / 播放 `PlayCircle` / 我的 `Person`，图标+文字；`MelodyNavHost` 的 `navItems` 数据类驱动），歌词从播放页进入、**设置入口移至「我的」页 TopAppBar**（`UserRoute(onOpenSettings)` + `UserScreen` 设置图标）；app 与 feature:user 补 `composeMaterialIconsExtended` 依赖。第五轮：**歌曲条目统一封面**——新建 `AlbumArtThumb` 共享组件（有封面显示缩略图、无封面 `surfaceVariant` 底 + `MusicNote` 默认图标），`SongRow`/`QueuePanel`（40dp）/`UserScreen` 统计行（`StatRowUi` 增 `albumArtUri`）全部接入；曲库 `SongRow` 开 `showCover`。**曲库全选**——`HomeViewModel.toggleSelectAll(visibleIds)`（过滤结果或全库，全选/取消全选切换），TopAppBar 全选按钮（曲库为空时隐藏）。**播放页重播修复**——`PlayerRuntime.loadLibrary` 幂等（队列非空直接返回），切换页面不再把队列重置为全库重头播。

**验收标准**

- SAF 导入 → 曲库展示 → 播放 → 加入歌单 → 歌词展示 → 统计更新，全链路可走通。
- 外部 LRC 与内嵌歌词均可展示并与进度同步（偏差 ≤ 1 行）。
- 均匀随机默认生效：低播放次数歌曲被明显优先选中（统计验证）。
- 无限播放开启后，接近队尾自动补队列，播放不中断。
- 同名多版本按 `groupKey` 正确分组，`sampleRate` 高者默认选优；用户覆盖可持久化。
- Android 12+ 动态取色生效（§9 兼容项）。

**风险与对策**

| 风险 | 对策 |
|------|------|
| 元数据解析耗时导致导入卡顿（映射 §10-1 性能族） | IO dispatcher 分批解析 + 进度反馈；先入库基础信息，封面与歌词懒解析 |
| SAF URI 权限失效（导入的目录被移除）（新增） | `takePersistableUriPermission` + 播放前 URI 可用性检测；失效项标记不可播放并 Toast 提示，与 P2 窗口过滤联动 |
| 封面缓存导致 OOM / 磁盘膨胀（新增） | 缓存尺寸与总量上限；Coil 内存+磁盘双层缓存；纳入备份排除 |
| 均匀随机权重依赖统计，早期数据稀疏（新增） | 无统计时退化为普通 `shuffled()`；权重函数做平滑处理 |
| 配套功能膨胀挤压核心质量（§10-6） | 5A/5B/5C 三块各自独立可交付，任一块延期不阻塞 P6 的核心测试 |

---

### Phase 6 — 质量与性能（2.0 人周）

**目标**：建立测试矩阵与性能基线，达成 §9 全部性能与质量验收项。

**任务清单**

- [x] **P6-1 JVM 单测矩阵**（优先纯 Kotlin，映射 §10-6）：
  - `ControllerQueuePlannerTest`（P2 已建，此处补边界）
  - `PlaybackWindowPlannerTest`、`ControllerWindowSynchronizerTest`（含 `resolveDrift` 滑动用例）、`WindowedControllerQueuePlannerTest`
  - `UniformRandomPlannerTest`（`DefaultUniformRandomPlannerTest`）、`RandomQueuePlannerTest`（本轮补 `DefaultRandomQueuePlannerTest`：空/单元素/排列/重复 id）、`QueueManagerPlayOrderBuilderTest`
  - `ControllerPlaybackStateSynchronizerTest`（本轮补，8 态映射全覆盖：buffering/error/playing/paused + 负值钳制 + 字段透传）
  - `PlaybackStateSnapshotSerializerTest`、`LrcParserTest`（本轮补 10 例：时间戳/多时间戳/元数据忽略/排序/进位/小数位/冒号毫秒）、`SongVersionResolverTest`
  - Repository 层测试（`MelodyDaoTest`，Robolectric + Room in-memory，P4-9）
- [ ] **P6-2 Compose 仪表化测试**：`PlayerScreen` 传输控制（play/pause/seek/prev/next）、`QueuePanel` 重复项高亮与按索引移除、`ToastHost` 堆积与自动消失、`HomeScreen` 多选。
- [ ] **P6-3 `:benchmark` Macrobenchmark**：`ColdStartBenchmark`（`StartupMode.COLD`，iterations ≥ 5，`compilationMode` 覆盖 `None` 与 `Partial`）；可选 `BaselineProfileGenerator` 生成基线配置文件以逼近 < 1.5s 目标。
- [ ] **P6-4 性能埋点与基线报告**：`PerfTracer` 输出四项指标——冷启动、导入耗时、切歌耗时、队列同步耗时；构造 1000 首标准数据集脚本，记录基线数据表。
- [ ] **P6-5 Release 校验**：R8 + `shrinkResources` 打包；补齐 ProGuard keep 规则（Media3、FFmpeg 扩展渲染器反射类、Hilt、Room、kotlinx.serialization）；release 包全功能冒烟。
- [ ] **P6-6 门禁固化**：`check` = `spotlessCheck` + `verifyProductArchitecture`（A1–A7 全启用）+ 全量 JVM 单测；CI 流水线跑 `gradlew.bat check` 与 `assembleRelease`。
- [ ] **P6-7 兼容性矩阵验证**：API 33（minSdk 下限）与 API 36 各跑一遍全部功能验收项。

**验收标准**（对应 §9 性能与兼容质量全部条目）

- 冷启动到首页可交互 < 1.5s（Macrobenchmark 实测基线，注明设备型号）。
- 1000 首队列下切歌 < 300ms 完成队列同步与播放。
- 进度刷新稳定 500ms，Layout Inspector / Compose 重组计数确认无异常重组。
- minSdk 33 起所有核心功能可用；Android 12+ 动态取色生效。
- 全量 JVM 单测通过；release 包 R8 + 资源压缩可打包且功能正常。
- `verifyProductArchitecture` + `spotlessCheck` 通过。

**风险与对策**

| 风险 | 对策 |
|------|------|
| 测试不足导致回归成本高（§10-6） | 纯 Kotlin planner/facade 单测 + 序列化测试优先；`:core:model`/`:domain` 为 JVM 模块，单测执行快 |
| R8 剥离反射加载的 FFmpeg 类导致 release 无法解码（新增） | keep 规则 + release 包必做解码冒烟（flac/opus/wma 各一） |
| 冷启动未达 1.5s（§9 性能目标） | Baseline Profile + 启动路径延迟初始化（Hilt 依赖懒加载、Room 首帧后打开、`installSplashScreen` 控制首帧） |
| 性能数据不可复现（新增） | 固定基准设备 + 固定 1000 首数据集 + iterations ≥ 5 取中位数 |

---

## 6. 里程碑与交付物

| 里程碑 | 对应阶段 | 可验证交付物 | 验证方式 |
|--------|---------|------------|---------|
| **M0 工程可构建** | P0 完成 | 14 模块空壳 App + Version Catalog + 门禁 | `gradlew.bat check` 与 `:app:assembleDebug`/`assembleRelease` 全绿；反向依赖用例触发门禁失败；真机导航 6 页 |
| **M1 能播歌** | P1 完成 | `AppMediaSessionService` + `PlayerFactory` + `PlaybackController` + 500ms tick | 真机播放 8 类格式样本，后台/息屏不中断；日志确认 FFmpeg 渲染器优先 |
| **M2 队列一致且不卡** | P2 完成 | `PlayQueue` + 三 `PlayMode` + 窗口化四件套 + `ControllerQueuePlannerTest` | 1000 首数据集切歌 < 300ms（`PerfTracer` 输出）；重复项高亮/移除正确；6 类单测全绿 |
| **M3 系统级融合** | P3 完成 | Media3 默认通知 + 媒体键 + 蓝牙自动暂停 + `ToastHost` + 权限协调 | 锁屏/通知栏三按钮可控且同步；耳机按键生效；蓝牙断开自动暂停；冷启动无强制权限弹窗 |
| **M4 状态可重建** | P4 完成 | Room 8 实体 + `MelodyDao` + DataStore + `PlaybackStateSnapshotSerializer` | 重启恢复队列/模式/进度且为暂停态；划掉任务后回退最近快照；序列化往返单测全绿 |
| **M5 功能闭环** | P5 完成 | SAF 导入 / 歌单 / 歌词 / 封面 / 统计 / 多版本 / 秒切 / 无限播放 / 均匀随机 / 搜索多选 / 主题 | 端到端场景走查清单逐项通过；动态取色生效 |
| **M6 可发布** | P6 完成 | 单测矩阵 + Compose 仪表化测试 + `ColdStartBenchmark` + 性能基线报告 + release 包 | 冷启动 < 1.5s 基线数据；§9 全部验收项打勾；`gradlew.bat check` + `assembleRelease` 全绿 |

---

## 7. 风险登记册

| ID | 风险 | 来源 | 主要阶段 | 影响 | 对策 |
|----|------|------|---------|------|------|
| R1 | 大曲库全量 `setMediaItems` 卡顿 | §10-1 | P2 | 高：切歌/模式切换卡顿，直接违反 §9 性能项 | 窗口化队列（前 20 后 50，≤71）；仅同步当前窗口；`PerfTracer` 持续监测 |
| R2 | 厂商系统任务管理差异导致恢复不完整 | §10-2 | P4 | 中：重启后状态丢失 | 播放中定期快照 + `onTaskRemoved`/`onDestroy` 兜底；≥2 家厂商真机验证 |
| R3 | 解码兼容性（个别格式/采样率失败） | §10-3 | P1、P5 | 中：部分曲目无法播放 | FFmpeg 扩展渲染器（`PREFER` 反射加载）+ 不可播放项过滤 + UI 提示 |
| R4 | 权限被拒影响功能 | §10-4 | P3 | 中：通知/蓝牙能力不可用 | 按需申请 + 拒绝文案 + 降级路径（不阻断核心播放） |
| R5 | 重复队列项映射错误，UI 高亮跳回首处 | §10-5 | P2、P4 | 高：核心体验缺陷，且保存/恢复会丢项 | `mediaId → Song.id` 唯一映射 + 全链路按队列索引；当前索引保留规则；门禁 A5 + 专项单测 |
| R6 | 测试不足导致回归成本高 | §10-6 | P2、P6 | 中：后期改动风险高 | 优先纯 Kotlin planner/facade 单测 + 序列化测试；`:core:model`/`:domain` 设为 JVM 模块 |
| R7 | AGP/Kotlin/KSP/Compose 版本矩阵不兼容 | 新增 | P0 | 中：阻塞开工 | P0-1 集中锁定并用空壳全量编译验证；Version Catalog 单点治理，单维度回退 |
| R8 | R8 剥离反射加载的 FFmpeg 类，release 无法解码 | 新增（R3 延伸） | P0、P6 | 高：debug 正常 release 崩 | 提前写 keep 规则；每阶段末跑 `assembleRelease`；P6 做 release 解码冒烟 |
| R9 | Android 14+ 前台服务类型校验失败 | 新增 | P1 | 中：后台播放被系统终止 | 声明 `mediaPlayback` 类型 + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`；启动前确保活跃 `MediaSession` |
| R10 | SAF 持久化 URI 失效 | 新增 | P5 | 中：导入的歌曲变为不可播放 | `takePersistableUriPermission`；播放前可用性检测；失效项标记 + Toast 提示 |
| R11 | 冷启动未达 < 1.5s 目标 | §9 性能目标 | P6 | 中：验收项不达标 | Baseline Profile + 启动路径延迟初始化 + `installSplashScreen`；必要时调整为设备分档目标 |
| R12 | Windows 构建环境问题（路径长度 / 文件锁） | 新增 | 全程 | 低：偶发构建失败 | 短路径仓库；`gradlew.bat --stop` 释放 daemon；`.gitignore` 已排除构建产物 |
| R13 | Media3 默认通知样式受限 | §8 决策代价 | P3 | 低：定制化不足 | 接受既定取舍；业务提示统一走 `ToastHost` |
| R14 | 配套功能膨胀挤压核心质量 | 新增（R6 延伸） | P5 | 中：P6 时间被侵占 | 5A/5B/5C 三块独立可交付；任一块延期不阻塞 P6 核心测试 |

---

## 8. 建议的迭代 / 排期

**假设**：1 名全职 Android 开发（AI 辅助编码），2 周 / Sprint，1 Sprint ≈ 2.0 人周有效产能。总量 16.0 人周 → **8 个 Sprint ≈ 16 周（约 4 个月）**。

| Sprint | 周次 | 内容 | 产能分配 | Sprint 结束目标 |
|--------|------|------|---------|----------------|
| **S1** | W1–W2 | P0 全部 + P1 前段（`PlayerFactory` / `AppMediaSessionService` / `PlaybackController`） | P0 1.0 + P1 1.0 | **M0 达成**；能播放第一首本地音频 |
| **S2** | W3–W4 | P1 收尾（tick / 状态同步 / 最小播放页）+ P2 前段（`PlayQueue` / `PlayMode` / `:domain` 策略层） | P1 0.5 + P2 1.5 | **M1 达成**；业务队列模型定型 |
| **S3** | W5–W6 | P2 收尾（窗口化四件套 / 队列操作 / 单测 / 1000 首实测）+ P3 前段（默认通知 / 媒体键） | P2 1.0 + P3 1.0 | **M2 达成**；通知栏可控 |
| **S4** | W7–W8 | P3 收尾（蓝牙 / 权限 / `ToastHost`）+ P4 全部（Room / DataStore / 快照序列化与恢复） | P3 0.5 + P4 1.5 | **M3 + M4 达成**；核心三模块全部闭环 |
| **S5** | W9–W10 | P5-A 曲库与导入（SAF / `MetadataExtractor` / 首页列表 / 搜索多选） | 2.0 | 真实曲库可导入并播放 |
| **S6** | W11–W12 | P5-B 歌单 / 歌词 / 封面 | 2.0 | 歌单与歌词页可用 |
| **S7** | W13–W14 | P5-C 统计 / 秒切 / 多版本 / 无限播放 / 均匀随机 / 设置 / 主题 | 2.0 | **M5 达成**；功能闭环 |
| **S8** | W15–W16 | P6 全部（单测矩阵 / Compose 测试 / Macrobenchmark / 性能基线 / release 校验） | 2.0 | **M6 达成**；可发布 |

**并行压缩方案**：若投入 2 名开发者，S5–S7 的 P5-A / P5-B / P5-C 三块可两两并行（依赖较弱），P3 与 P4 也可在 S3 之后并行，总周期可压缩至 **约 10–11 周**。核心三模块（P1/P2/P3）串行依赖强，不建议并行拆分。

**每 Sprint 固定动作**

1. Sprint 开始：确认本 Sprint 涉及的门禁规则是否需要新增（A3/A4/A5 分别在 P4/P1/P2 启用）。
2. Sprint 结束：`gradlew.bat check` + `gradlew.bat :app:assembleRelease` 必须全绿，否则不算完成。
3. 每个里程碑（M0–M6）产出一份验收清单勾选记录，映射到方案第 9 节对应条目。
