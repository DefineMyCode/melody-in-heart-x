# Melody in Heart 项目评审与重构建议

> ⚠️ **本文档是重构前的评审快照**：其中的「现状」「项目结构」「依赖配置」等描述（如单模块结构、`PlayerViewModelComponents` 单体、`release.isMinifyEnabled = false`、AGP alpha 版本、SharedPreferences/JSON 持久化等）反映的是**评审当时**的状态，多数建议已在后续产品化重构中落实。当前实现与完成度请以 [PRODUCT_REFACTOR_AUDIT.md](PRODUCT_REFACTOR_AUDIT.md) 为准。

## 一、总体评价

当前项目已经具备较完整的本地音乐播放器能力，技术栈选择清晰，核心功能覆盖了播放控制、播放队列、Media3 后台播放、歌词、歌单、多版本歌曲、播放统计、SAF 文件导入、蓝牙状态监听等多个真实产品场景。

项目最值得肯定的地方有：

- 已采用 Kotlin + Jetpack Compose + Material3 + Media3，方向符合现代 Android 开发实践。
- 播放队列逻辑已经从早期大 ViewModel 中拆出 `ControllerQueuePlanner`、`RandomQueuePlanner`、`PlaybackStateStore`、多个 `Player*Facade`，并配套了较多单元测试。
- [播放队列架构](../architecture/PLAYBACK_QUEUE_ARCHITECTURE.md) 对播放器队列、业务队列、系统控制队列之间的关系写得比较清楚，说明项目已经开始形成架构意识。
- 播放器核心场景测试覆盖较积极，`app/src/test` 下已有 39 个 Kotlin 测试文件，尤其播放器相关逻辑不是纯靠手测维护。

同时，项目也进入了典型的“功能快速增长后需要治理”的阶段。当前主要问题不是功能缺失，而是边界逐渐变厚、单模块压力增加、持久化方案偏轻、UI 与应用装配耦合较高，以及大曲库性能还有明确优化空间。

## 二、项目架构评审

### 现状

当前项目整体更接近单模块 MVVM 架构：

- `ui/*` 承担 Compose 页面、组件和部分交互状态。
- `ui/player/PlayerViewModel.kt` 作为 UI 门面，对外暴露播放器能力。
- `ui/player/PlayerViewModelComponents.kt` 负责组装仓储、播放器控制、播放状态、蓝牙、统计、随机队列等依赖。
- `data/player/*` 承担 Media3、队列规划、播放恢复、蓝牙协调等播放器领域逻辑。
- `data/repository/*` 负责歌曲、歌单、播放统计、设置等数据访问。
- `data/model/*` 放置核心数据模型。

这个结构对当前规模仍可工作，但已经出现几个架构信号：

- `PlayerViewModel` 已经瘦身，但 `PlayerViewModelComponents` 成为新的复杂中枢，承担过多依赖装配和跨模块协调。
- `MusicplayerApp.kt` 同时负责导航、主题、权限、页面切换、Toast、删除歌曲回调、播放队列面板等，根 Composable 体量过大。
- `MusicRepository` 同时负责歌曲持久化、歌单管理、SAF 扫描、封面刷新、文件删除、分组查询，仓储职责偏宽。
- `domain/` 目录目前很薄，真实业务逻辑多数仍散落在 `data/player`、`data/repository` 和 `ui/player`。

### 建议

建议逐步演进为更明确的分层架构：

```text
presentation
  ├── app shell / navigation
  ├── screen state
  └── ViewModel

domain
  ├── usecase
  ├── service / coordinator
  └── pure model / policy

data
  ├── repository implementation
  ├── local datasource
  ├── media datasource
  └── persistence

player
  ├── Media3 service/controller adapter
  ├── playback queue adapter
  └── player event bridge
```

短期不一定要立刻拆多模块，但建议先在单模块内形成稳定包边界：

- 将 `ui/player/PlayerViewModelComponents.kt` 中的装配职责拆成 `PlayerGraph`、`PlaybackGraph`、`QueueGraph`、`PersistenceGraph`、`BluetoothGraph`。
- 将播放策略类从 `data/player` 中区分出来，例如 `domain/playback` 放纯策略和 coordinator，`data/player` 只保留 Media3/系统相关实现。
- 将 `MusicRepository` 拆为 `SongRepository`、`PlaylistRepository`、`MusicImportRepository`、`AlbumArtRepository` 或至少拆出 datasource。
- 为 repository 定义接口，ViewModel/UseCase 依赖接口，具体实现放 data 层。

## 三、项目结构评审

### 现状

项目目前只有 `:app` 一个模块，所有业务、UI、播放器、仓储、工具类都在同一模块内。单模块降低了开发成本，但随着功能增加，构建边界和职责边界会越来越模糊。

部分文件已经明显偏大：

- `MusicplayerApp.kt` 约 29 KB。
- `UserComponents.kt`、`UserDialogs.kt`、`VersionManagementComponents.kt` 均超过 24 KB。
- `MusicRepository.kt` 约 24 KB。
- `PlayerViewModelComponents.kt` 约 20 KB。
- `HomeScreen.kt`、`PlaylistScreen.kt`、`UserScreen.kt` 均超过 20 KB 或接近该规模。

大文件本身不是错误，但它们通常意味着页面状态、组件、业务回调、数据转换和副作用混在一起，后续维护成本会明显升高。

### 建议

建议先做包级拆分，再考虑模块化：

```text
app/src/main/java/cn/com/dcsgo/mihx/
  app/
    MusicplayerApp.kt
    AppNavHost.kt
    AppScaffold.kt
    AppPermissionHandler.kt
  feature/
    home/
    playlist/
    user/
    player/
    settings/
  core/
    model/
    common-ui/
    logging/
    coroutine/
  domain/
    song/
    playlist/
    playback/
    lyrics/
  data/
    local/
    media/
    repository/
  player/
    media3/
    queue/
    service/
```

中期可以考虑拆模块：

- `:core:model`：`Song`、`Playlist`、`PlayQueue`、`Lyrics` 等纯模型。
- `:core:ui`：主题、公共组件、Toast、通用图标/格式化。
- `:domain`：用例、播放策略、纯业务 coordinator。
- `:data`：本地存储、SAF、MediaStore、元数据解析。
- `:player`：Media3 service、controller adapter、session。
- `:app`：应用入口、导航、依赖装配。

拆模块不建议一步到位。优先拆纯 Kotlin、低 Android 依赖、低风险的模型和策略层，收益更稳定。

## 四、代码规范与可维护性

### 现状

项目代码整体可读性尚可，很多关键类有注释，播放队列架构也有文档沉淀。但仍有几类规范问题：

- 中文注释和英文注释混用，风格不统一。
- `Log.d/i/w/e` 分布较多，缺少统一日志门面和 release 策略。
- 有些 UI 文件中的注释偏多，说明组件可能还可以继续拆小。
- 根 Composable 中存在较多局部函数、权限逻辑和导航状态，阅读路径较长。
- 构建脚本中存在历史注释，例如已移除 ffmpeg-kit 的说明，可以清理为更准确的依赖说明。

### 建议

- 引入统一代码格式工具，例如 ktlint 或 Spotless，并在 CI 或本地检查中执行。
- 制定简单命名规范：
  - UI 页面：`XxxScreen`
  - UI 状态：`XxxUiState`
  - UI 事件：`XxxEvent`
  - 业务用例：`XxxUseCase`
  - 数据源：`XxxLocalDataSource`、`XxxMediaDataSource`
  - Media3 适配器：`XxxMediaControllerAdapter`
- 将日志封装为 `AppLogger`，支持按 build type 控制输出，避免正式包输出过多播放状态、文件路径、设备信息。
- 对复杂类建立“职责头注释”，说明它拥有什么、不拥有什么，避免后续继续膨胀。
- 对 `PlaybackStateStore` 这类持久化类，优先使用结构化 JSON API 或 kotlinx.serialization，避免正则解析 JSON。

## 五、数据持久化与数据层

### 现状

项目大量使用 SharedPreferences + JSON 字符串保存数据：

- 歌曲列表、歌单：`MusicRepository`
- 播放状态：`PlaybackStateStore`
- 播放统计：`PlayStatsRepository`
- 秒切歌曲：`QuickSkipSongsRepository`
- 设置：`PlayerSettingsRepository`
- 主题：`MusicplayerApp.kt` 中直接访问 SharedPreferences

这种方案在早期实现很快，但当数据规模和字段演进增加后，会出现这些问题：

- 没有 schema 和 migration，字段变更容易靠兼容代码堆叠。
- 大列表每次整体序列化/反序列化，性能和可靠性不如数据库。
- 多个 repository 分散使用不同 prefs 文件和 key，缺少统一数据治理。
- JSON 解析失败时目前多为吞掉或仅日志记录，用户侧恢复策略不够明确。
- 播放统计按 key-value 存储，后续做排行榜、排序、筛选、迁移会越来越吃力。

### 建议

优先规划迁移到 Room + DataStore：

- Room：
  - `songs`
  - `playlists`
  - `playlist_song_cross_ref`
  - `play_stats`
  - `quick_skip_songs`
  - `song_group_overrides`
- Proto DataStore 或 Preferences DataStore：
  - 主题
  - 全局均匀随机开关
  - 最近播放状态
  - UI 设置

迁移顺序建议：

1. 新增 Room schema，但保留旧 SharedPreferences 读取。
2. 首次启动时从旧 JSON 迁移到 Room，并写入迁移完成标记。
3. Repository 对外接口不变，内部改为读写 Room。
4. 数据稳定后再删除旧 JSON 写入逻辑。

对音乐文件 URI 建议额外保存：

- `uri`
- `displayName`
- `mimeType`
- `lastModified`
- `size`
- `sourceTreeUri`
- `albumArtCacheUri`
- `importedAt`

这样后续可支持重新扫描、失效检测、重复文件判断、批量修复权限。

## 六、播放器与播放队列

### 现状

播放器队列是当前项目最成熟的部分之一。已有架构文档明确说明：

- `PlayQueue` 是业务队列和 UI 队列。
- `MediaController` 是真实播放顺序来源。
- `ControllerQueuePlanner` 是业务队列到播放器队列的规划层。
- `MediaItem.mediaId` 与 `Song.id` 一一对应。
- 已有多组 planner/facade 测试覆盖。

当前主要风险在于：

- 大曲库时仍全量 `setMediaItems(...)`。
- 添加到下一首、切换播放模式时仍可能全量重建 Controller 队列。
- 播放状态同步链路较长，涉及 `MediaSessionService`、`PlaybackController`、`PlayerMediaControllerGraph`、`PlayerControllerStateFacade`、`PlayerMediaEventFacade`、`PlayerPlaybackBridgeFacade` 等多个节点。
- `PlayerViewModelComponents` 中的播放器依赖装配复杂，理解和修改成本较高。

### 建议

短期优先做两件事：

- 把 `PlayerViewModelComponents` 继续拆成几个 graph，降低单文件复杂度。
- 为播放状态同步建立一张小型状态机文档，明确 `idle/preparing/ready/playing/paused/buffering/ended/error` 的状态来源和转移。

中期实现窗口化 Controller 队列：

- `PlayQueue` 保留完整业务队列。
- `MediaController` 只持有当前项前后固定窗口，例如前 20 首、后 50 首。
- 当播放器接近窗口边缘时，重新规划窗口。
- 添加到下一首时只修正窗口内必要区域。
- UI 队列面板继续展示完整 `PlayQueue.songs`。

建议新增核心类：

- `PlaybackWindowPlanner`
- `PlaybackWindowState`
- `ControllerWindowSynchronizer`

同时补充测试：

- 当前项在窗口中间、开头、结尾。
- 添加到下一首后目标歌曲进入下一位。
- 随机/倒序模式下窗口映射正确。
- 系统耳机下一首触发后 UI 高亮能映射回完整队列。

## 七、UI 与 Compose 结构

### 现状

项目已使用 Compose 和 Material3，并采用 `NavigationSuiteScaffold` 做自适应导航，这是一个不错的方向。主要问题是 UI 根层和页面层过厚：

- `MusicplayerApp.kt` 同时负责全局导航、主题、权限、页面覆盖态、Toast、删除歌曲行为、播放队列面板等。
- 多个页面文件超过 20 KB，组件拆分粒度还可以继续细化。
- 主题状态在根 Composable 直接读写 SharedPreferences，和设置仓储体系不统一。
- 当前导航以多个 `Boolean` 控制覆盖页面，例如 `showSettings`、`showPlayStats`、`showQuickSkipSongs`、`showVersionManagement`，随着页面增加会变得脆弱。

### 建议

- 引入 Navigation Compose，让页面状态由路由表达，而不是多个 Boolean 叠加。
- 将 `MusicplayerApp.kt` 拆为：
  - `AppRoot`
  - `AppScaffold`
  - `AppNavHost`
  - `PermissionHandler`
  - `ThemeController`
  - `PlayerQueueSheetHost`
- 页面内部采用 `Route + Screen` 模式：
  - `HomeRoute` 负责收集状态和连接 ViewModel。
  - `HomeScreen` 只负责渲染和事件回调。
- 对长页面继续拆组件：
  - `UserScreen`
  - `UserActionSection`
  - `UserStatsSection`
  - `VersionGroupList`
  - `PlaylistSongList`
  - `PlaylistActionBar`
- 对列表类页面使用稳定 key：
  - `LazyColumn(items = songs, key = { it.id })`
  - 对歌单和版本分组也使用稳定 key，减少重组抖动。

## 八、性能优化

### 重点风险

1. 播放进度刷新频率过高  
   README 中提到播放中约 20ms 刷新 UI 进度。对 Compose 来说 50 FPS 的状态刷新非常激进，容易带来重组压力和耗电问题。音乐播放器通常 250ms 到 1000ms 刷新一次已足够，拖动时再提高交互响应。

2. 大队列全量同步  
   当前大曲库仍会一次性设置完整 MediaController 队列。500+ 或 1000+ 歌曲时，MediaItem 构造、队列同步、状态回写都可能造成延迟。

3. SharedPreferences 大 JSON  
   歌曲、歌单、播放队列整体 JSON 持久化在数据变大时会影响 IO 和主线程稳定性。

4. 封面和歌词解析  
   封面刷新、歌词解析、元数据读取都涉及文件 IO。当前已有 IO 调度，但仍建议建立缓存、限流和取消策略。

5. Compose 大列表  
   播放统计、歌单、多版本管理等列表如果搜索、排序、分组都在 Composable 重组路径上执行，数据量上来后会卡顿。

### 建议

- 将播放进度 UI 刷新间隔调整为 250ms 或 500ms；拖动 Slider 时使用本地状态即时反馈。
- 实现窗口化播放队列，避免全量 `setMediaItems(...)`。
- 将歌曲/歌单/统计迁移到 Room，避免大 JSON 反复整体写入。
- 专辑封面：
  - 保留磁盘缓存。
  - 列表页使用缩略图尺寸。
  - 避免在队列构建阶段同步读取封面字节。
- 歌词：
  - 对 `songId + uri + lastModified` 建立解析缓存。
  - 歌曲切换时取消上一首歌词解析任务。
- 搜索和排序：
  - 放到 ViewModel 或 usecase 中计算。
  - 使用 `StateFlow` 输出结果。
  - 对输入搜索关键字加 debounce。
- 使用 Macrobenchmark 或至少添加简单启动耗时、导入耗时、切歌耗时日志埋点。

## 九、依赖与构建配置

### 现状

项目已使用 Version Catalog，这是好的实践。但当前依赖配置还有几个点值得治理：

- AGP 使用 `8.12.0-alpha09`，alpha 版本适合尝鲜，但不适合作为稳定开发基线。
- `compileSdk/targetSdk = 36`，需要确认团队本地环境和 CI 均可稳定支持。
- `release.isMinifyEnabled = false`，正式包未开启混淆和优化。
- 依赖里存在 `com.google.android.things:androidthings:1.0`，需要确认是否真的用于当前普通 Android 音乐播放器场景。
- `org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1` 直接写在 build 文件中，建议纳入 version catalog。

### 建议

- 将 AGP 固定到稳定版本，除非项目明确需要 alpha 特性。
- 所有第三方依赖统一进入 `libs.versions.toml`。
- 清理未使用依赖，尤其确认 `androidthings` 是否必要。
- release 开启：
  - `isMinifyEnabled = true`
  - `isShrinkResources = true`
  - 完整 proguard/r8 规则
- 添加构建类型：
  - `debug`
  - `release`
  - 可选 `benchmark`
- 增加常用质量任务：
  - `test`
  - `lint`
  - `ktlintCheck` 或 `spotlessCheck`

## 十、测试质量

### 现状

项目播放器相关单元测试比较积极，尤其 planner、facade、持久化、同步类测试较多。这是当前项目最有价值的质量资产之一。

仍可补强的地方：

- UI 层 Compose 测试较少。
- Repository 数据迁移、JSON 损坏恢复、权限失败等异常场景测试不足。
- Media3 服务和系统控制链路主要依赖手测。
- 大曲库性能缺少自动化基准。

### 建议

- 保持播放器 planner/facade 纯 Kotlin 测试风格，这是最划算的测试。
- 为 repository 增加测试：
  - 歌曲恢复
  - 歌单恢复
  - JSON 字段缺失
  - 重复 URI 导入
  - 删除歌曲失败
  - 播放统计排序
- 为 Compose 增加关键 UI 测试：
  - 空库状态
  - 播放中状态
  - 歌单增删改
  - 版本管理入口
  - 设置开关状态持久化
- 为性能建立基准数据：
  - 100 首、500 首、1000 首歌曲导入耗时
  - App 冷启动到首页可交互耗时
  - 切歌耗时
  - 队列同步耗时

## 十一、安全、权限与隐私

### 现状

项目使用 SAF 和 Android 13+ 的 `READ_MEDIA_AUDIO`，权限方向正确。需要注意的是：

- 启动时主动请求通知和蓝牙权限，可能让用户在还没理解功能价值前看到权限弹窗。
- 日志中可能输出文件 URI、蓝牙设备名、歌曲信息等隐私相关内容。
- 物理删除 SAF 文件是高风险操作，需要确保 UI 确认、失败提示和撤销预期足够清晰。
- `android:allowBackup="true"`，需要确认本地音乐 URI、播放统计、歌单等是否允许备份到用户云端。

### 建议

- 权限申请按需触发：
  - 用户开启通知控制时申请通知权限。
  - 用户启用蓝牙自动暂停功能时申请蓝牙权限。
  - 用户导入或扫描时申请媒体读取权限。
- 对日志做 release 降噪，避免输出文件路径、URI、蓝牙设备名等敏感信息。
- 检查 backup 规则，明确是否排除播放状态、URI 权限相关数据、缓存封面。
- 删除歌曲前 UI 文案明确说明“会删除本地文件”，并考虑增加二次确认或仅从库移除的选项。

## 十二、可观测性与错误处理

### 现状

当前错误处理主要依赖 `Log` 和 UI toast，部分持久化异常会被吞掉以避免影响播放。这对播放器体验是合理的，但长期看会增加定位问题的难度。

### 建议

- 定义统一错误类型，例如：
  - `ImportError`
  - `PlaybackError`
  - `PersistenceError`
  - `PermissionError`
  - `FileDeleteError`
- ViewModel 输出一次性 UI 事件，而不是只靠 `errorMessage` 字符串。
- 为关键链路增加结构化埋点：
  - 启动恢复成功/失败
  - MediaController 连接成功/失败
  - 队列同步耗时
  - 导入扫描数量与耗时
  - 歌词解析来源和耗时
- 对可恢复错误提供用户动作，例如“重新授权文件夹”“重新扫描”“从库中移除失效歌曲”。

## 十三、推荐重构路线图

### 第一阶段：低风险治理

- 清理未使用依赖和历史注释。
- 引入 ktlint/Spotless。
- 将所有依赖纳入 Version Catalog。
- 将 `MusicplayerApp.kt` 拆出导航、权限、主题、队列面板 host。
- 将主题设置从 Composable 直接读写 SharedPreferences 改为 repository/ViewModel 管理。
- 将播放进度刷新间隔降到 250ms 或 500ms。

### 第二阶段：核心结构拆分

- 拆分 `PlayerViewModelComponents` 为多个 graph。
- 拆分 `MusicRepository`：
  - 歌曲数据
  - 歌单数据
  - 文件导入
  - 封面刷新
  - 文件删除
- 将纯业务策略迁移到 `domain`。
- 建立 repository 接口，降低 UI/业务层对具体存储的依赖。

### 第三阶段：数据层升级

- 引入 Room 保存歌曲、歌单、播放统计、秒切列表。
- 引入 DataStore 保存设置和轻量播放状态。
- 编写 SharedPreferences JSON 到 Room 的迁移逻辑。
- 增加损坏数据恢复与迁移测试。

### 第四阶段：播放器性能升级

- 实现窗口化 Controller 队列。
- 将添加到下一首、切换播放模式、无限播放补队列改为轻量同步。
- 增加 500+、1000+ 歌曲队列同步测试和性能日志。
- 梳理 Media3 状态机和系统控制事件链路。

### 第五阶段：产品级质量

- release 开启 R8 和资源压缩。
- 增加 Compose UI 测试和基准测试。
- 完善权限按需申请和隐私日志策略。
- 建立崩溃、错误、性能问题的定位机制。

## 十四、优先级建议

如果只能先做少量重构，建议按这个顺序：

1. 降低播放进度刷新频率，减少 Compose 重组和耗电。
2. 拆分 `MusicplayerApp.kt`，让导航、权限、主题和页面展示分离。
3. 拆分 `PlayerViewModelComponents.kt`，降低播放器装配复杂度。
4. 清理依赖和构建配置，固定稳定 AGP，开启质量检查。
5. 设计 Room/DataStore 迁移方案，但不要贸然一次性替换所有持久化。
6. 实现窗口化播放队列，解决大曲库下的根本性能问题。

## 十五、结论

这个项目已经不是简单 demo，而是一个功能密度较高的本地音乐播放器。当前代码最大的问题不是“写得差”，而是功能成长速度已经超过了原始单模块结构的承载能力。

接下来的重构重点应当是：

- 让 UI 根层变薄。
- 让播放器装配层变清晰。
- 让 repository 职责变窄。
- 让持久化从轻量 key-value 过渡到可迁移、可查询的数据层。
- 让大曲库播放队列从全量同步升级为窗口化同步。

只要沿着这些方向推进，现有测试和播放队列文档会成为很好的安全网，项目可以比较平稳地从“功能型应用”进化到“长期可维护的产品型应用”。
