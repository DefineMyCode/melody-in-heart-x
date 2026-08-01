# Melody in Heart 技术方案（从 0 到 1 构建）

> 本文档是一份面向「由 AI 从零构建本项目」的技术提案，定义音乐播放、播放队列、系统通知三大核心模块的设计、实施路线与验收预期。
> 目标产物：一款纯本地、无广告、无网络依赖的 Android 音乐播放器，具备可靠的离线播放、跨入口一致的队列体验，以及与系统（通知栏/锁屏/耳机/蓝牙）深度集成的媒体控制能力。
>
> 设计基线参考了一版已验证可运行的多模块实现，本文将其固化为**推荐方案**，以降低从零构建的架构风险。

---

## 目录

1. [目标与范围](#1-目标与范围)
2. [技术选型](#2-技术选型)
3. [系统架构](#3-系统架构)
4. [核心功能设计](#4-核心功能设计)
   - 4.1 [音乐播放（Media3 ExoPlayer）](#41-音乐播放media3-exoplayer)
   - 4.2 [播放队列（业务队列 + 窗口化同步）](#42-播放队列业务队列--窗口化同步)
   - 4.3 [系统通知与系统集成](#43-系统通知与系统集成)
5. [数据模型与持久化](#5-数据模型与持久化)
6. [播放状态机](#6-播放状态机)
7. [从 0 到 1 实施路线](#7-从-0-到-1-实施路线)
8. [关键决策与预期](#8-关键决策与预期)
9. [验收标准](#9-验收标准)
10. [风险与对策](#10-风险与对策)

---

## 1. 目标与范围

### 1.1 产品目标

- **离线优先**：所有音乐数据来自用户本地文件（SAF 导入），不依赖任何云端或网络。
- **播放可靠**：支持常见无损/有损格式，后台持续播放，进程恢复后状态可重建。
- **队列一致**：App 内、通知栏、锁屏、蓝牙耳机对「上一首/下一首/添加到下一首」的行为必须完全一致。
- **系统融合**：像系统级播放器一样出现在通知栏、锁屏与控制中心。

### 1.2 范围边界

**核心（本文重点）**：音乐播放内核、播放队列、系统通知与媒体键集成。

**配套（需在核心稳定后补齐，本文给出接口与数据契约但不展开实现细节）**：歌单、歌词、专辑封面、播放统计、同名歌曲多版本管理、秒切检测、无限播放、全局均匀随机、文件导入、搜索多选、主题。

**不在范围**：在线曲库、账号体系、云同步、社交分享。

### 1.3 平台基线

- 最低 SDK：Android 13（API 33）
- 目标 / 编译 SDK：Android 16（API 36）
- 单进程应用（不引入独立 `:codec` 转码进程）

---

## 2. 技术选型

| 类别 | 技术 | 说明 |
|------|------|------|
| 语言 | Kotlin 2.0+ | 协程驱动 |
| UI | Jetpack Compose + Material3 + adaptive-navigation-suite | 自适应大屏/折叠屏 |
| 播放器 | Media3 ExoPlayer 1.9.x | 播放内核 |
| 系统集成 | Media3 `MediaSessionService` + `MediaController` | 通知/锁屏/媒体键统一入口 |
| 解码扩展 | Jellyfin Media3 FFmpeg Decoder | 经 `DefaultRenderersFactory` + `EXTENSION_RENDERER_MODE_PREFER` 反射加载 |
| 依赖注入 | Hilt | 装配与测试解耦 |
| 持久化 | Room + Preferences DataStore | 结构化存储 + 轻量快照 |
| 导航 | Navigation Compose | 路由化页面，避免布尔叠加 |
| 质量 | KSP + Spotless + Macrobenchmark + `verifyProductArchitecture` | 风格/架构/性能门禁 |
| 构建 | Gradle Version Catalog + 稳定 AGP（8.13.x） | 依赖集中治理 |

---

## 3. 系统架构

### 3.1 分层与模块

采用多模块分层，模块在 `settings.gradle.kts` 声明：

```
:app            应用外壳：入口、根导航、权限协调、主题、Hilt 装配
:core:model     纯数据模型（Song / Playlist / PlayQueue / Lyrics / SongInfo）
:core:common    通用能力（日志门面、协程调度、错误类型、时间格式化、性能埋点）
:core:ui        公共 Compose（主题、ToastHost、LyricsView、通用组件）
:domain         repository 接口 + 业务策略（队列/随机/状态同步/版本）
:data           Room + DAO + repository 实现 + 元数据/封面/歌词解析 + DataStore
:player         MediaSessionService + PlaybackController + 窗口化队列 + 蓝牙监听
:feature:*      各页面 Route/Screen/ViewModel/Facade（home/playlist/user/lyrics/player/settings）
:benchmark      Macrobenchmark 冷启动
```

### 3.2 设计原则（必须遵守）

1. **Media3 是传输状态的最终事实来源**；`PlayQueue` 仅保留完整业务队列与 UI 状态。
2. **UI 只渲染状态**：UI 不得直接推断 Media3 状态，所有播放状态必须经 `ControllerPlaybackStateSynchronizer` 回写到 `PlayerUiState`。
3. **依赖倒置**：`feature`/`domain` 依赖 `domain` 中定义的 repository 接口，具体实现在 `:data`。
4. **装配与逻辑分离**：`PlayerViewModel` 是面向 UI 的薄门面，播放逻辑收敛到 `PlayerRuntime` 及其 `Player*Facade` / `*Graph` / `Coordinator`。
5. **队列按项处理**：`PlayQueue.songs` 允许重复歌曲，任何规划/保存/恢复/删除都必须按队列索引或队列项处理，不能假设 `Song.id` 在队列内唯一。

---

## 4. 核心功能设计

### 4.1 音乐播放（Media3 ExoPlayer）

**职责归属**：`AppMediaSessionService`（`player` 模块）持有 ExoPlayer 实例与 `MediaSession`，是后台播放与系统控制的宿主。

**组件设计**

- **`PlayerFactory`**：构造 ExoPlayer 时使用 `DefaultRenderersFactory` 并设置 `EXTENSION_RENDERER_MODE_PREFER`，使 FFmpeg 扩展渲染器优先加载（支持更多格式与 Hi-Res 解码），无需显式 `FfmpegAudioRenderer` 引用。
- **`PlaybackController`**：连接 `MediaController`，将 Media3 回调转为 `ControllerPlaybackSnapshot`，对外暴露播放/暂停/seek/上一首/下一首命令与队列设置接口。UI 与系统控制都经它作用到同一条队列。
- **进度刷新**：播放中通过 `PlayerPlaybackProgressTicker` 以默认 **500ms** 刷新 UI 进度；用户拖动 Slider 时使用本地 UI 状态即时反馈，松手再 `seek`。

**关键不变量**

- `MediaItem.mediaId` 必须为 `Song.id.toString()`，是播放器状态回写业务队列的锚点。
- 服务的 `onTaskRemoved` / `onDestroy` 在系统提供回调时，直接从 ExoPlayer 保存最后的 `mediaId` 与 `currentPosition`。

**预期**

- 后台播放不被系统轻易回收；通知/锁屏可见且可控。
- 切歌延迟在 1000 首队列下可控（依赖 4.2 的窗口化）。
- 支持常见格式（mp3/flac/wav/m4a/ogg/aac/opus/wma）与高采样率音频。

### 4.2 播放队列（业务队列 + 窗口化同步）

这是一致性体验的核心，也是性能瓶颈的解法。

**双层队列模型**

| 层 | 角色 | 内容 |
|----|------|------|
| `PlayQueue`（业务队列） | UI 展示与业务操作的事实来源 | 完整歌曲列表 `songs`、`currentIndex`、`playMode`、`playOrderIds` |
| `MediaController` 队列（传输队列） | 真实播放顺序来源 | 经窗口化规划后的轻量子集 |

**`PlayQueue` 设计**

- `songs`：完整业务队列，`currentIndex` 指向其在 `songs` 中的索引（供 UI 高亮）。
- `playMode`：顺序 / 倒序 / 随机（`PlayMode` 枚举）。
- `playOrderIds`：按播放模式生成的歌曲 ID 顺序；**重复项按出现次数解释**（`currentPlayOrderIndices()` 还原为队列项索引，禁止 `distinct()`/`associateBy(id)`）。
- `PlayQueue` **不持有**「下一首/上一首」导航逻辑；导航完全由 `MediaController` 执行。

**窗口化同步（解决大曲库性能）**

引入 `player/window` 规划层：

- `ControllerQueuePlanner`：先将完整业务队列按播放模式展开为完整可播放队列。
- `PlaybackWindowPlanner`：在完整队列上截取**当前项前 20 首 + 后 50 首**的窗口（窗口 ≤ 71 项），过滤不可播放项（默认 `uri != null`）。
- `ControllerWindowSynchronizer`：缓存最近窗口（`PlaybackWindowState`），窗口失效或显式强制时重新生成 `ControllerQueuePlan`。
- `WindowedControllerQueuePlanner`：实现 `ControllerQueuePlannerPort`，读取 `PlayQueue.currentPlayOrderIndices()` → 按索引映射回 `Song`（保留重复项）→ 计算窗口内 `startIndex`；请求项不可播放时优先取其后可播放项，否则回退前一可播放项。

> 设计目标：完整业务队列可容纳 500+/1000+ 首，而 `MediaController` 仅持有当前项附近的轻量窗口；UI 队列面板展示完整 `songs`；接近窗口边缘时重新规划并同步。

**随机与全局均匀随机**

- `RandomQueuePlanner`：生成随机队列与无限播放补队列。
- `UniformRandomPlanner`：开启（默认）后以原始播放次数作为权重，优先选播放次数较少的歌曲；关闭时退化为普通 `shuffled()`。
- 重建随机顺序必须走 `QueueManager.PlayOrderBuilder` / `UniformRandomPlanner`，**不让 `PlayQueue` 直接依赖仓库**。

**队列操作契约**

- **添加到下一首**：`addSongAsNext(...)` 更新业务队列与 `playOrderIds` → 同步到 `MediaController`；下一首由 `seekToNextMediaItem()` 决定。批量按歌曲 ID 去重并移动到当前项之后，当前项作锚点。
- **添加到队尾**：单首去重；批量允许重复追加，队列面板按队列索引移除具体一项。
- **无限播放**：开启后保留当前队列，接近队尾时从尚未覆盖的本地可播放歌曲中补一批到队尾，尽量循环覆盖全库；开启均匀随机时优先低播放次数候选。
- **切换模式**：重建 `playOrderIds`（随机模式应用均匀随机）→ 经 Planner 重建 Controller 队列。

**预期**

- App 内 / 通知栏 / 锁屏 / 耳机对「下一首/上一首/添加到下一首」行为完全一致。
- 1000 首队列下不出现明显的 `setMediaItems(...)` 全量卡顿。
- 同一首歌在队列中出现多次时，UI 高亮、移除、保存/恢复与 Controller 队列均不丢失队列项。

### 4.3 系统通知与系统集成

**通知栏 / 锁屏控制**

- 采用 Media3 `MediaSessionService` 的**默认通知生命周期**，由框架自动生成带封面/标题/艺术家及上一首/暂停/下一首按钮的媒体通知，无需自定义 `NotificationCompat`。
- `AppMediaSessionService` 仅负责创建 `MediaSession`、日志与播放快照保存；媒体通知的展示与锁屏可见性由 Media3 默认行为保证。

**状态同步**

- UI 经 `MediaController` 连接并同步状态；所有 controller 快照经 `ControllerPlaybackStateSynchronizer` 唯一映射回 `PlayerUiState`（见第 6 节）。
- 系统媒体键与 App 内按钮**走同一条同步路径**，保证行为一致。

**耳机线控**

- 通过 `MediaSession` / `MediaController` 接入播放/暂停/上一首/下一首媒体按键（无需额外广播接收器处理媒体键）。

**蓝牙 / 音频设备**

- `BluetoothStateManager` / `BluetoothPlaybackMonitor` / `BluetoothAudioQualityManager` 监听蓝牙连接状态与音频断开（`ACTION_AUDIO_BECOMING_NOISY` 类事件），断开时自动暂停播放。
- `BLUETOOTH_CONNECT` 权限按需申请。

**顶部 Toast 通知**

- 自定义 `ToastHost`（`:core:ui`），多条堆积、2 秒自动消失、可手动关闭，用于导入完成、复制成功、删除确认等业务提示，替代 Snackbar。

**预期**

- 锁屏/通知栏/控制中心对播放的控制与 App 内完全一致且实时同步。
- 耳机按键、蓝牙断开自动暂停均按预期工作。
- 权限（通知、蓝牙）按需申请，启动不强制弹窗。

---

## 5. 数据模型与持久化

### 5.1 核心模型（`:core:model`）

- `Song`：`id`、`uri`、`title`、`artist`、`album`、`sampleRate`（用于多版本/Hi-Res）、`albumArtUri`、`titleOverride`（自定义分组键），派生 `groupKey = titleOverride ?: title`。
- `Playlist` + 交叉引用（歌单-歌曲多对多）。
- `PlayQueue`：业务队列（见 4.2）。
- `Lyrics` / `LyricLine`：歌词与时间轴。

### 5.2 持久化方案

- **Room**（schema version 起始 1，规划 8 实体：歌曲、歌单、歌单-歌曲交叉引用、播放统计、秒切歌曲、短播放计数、歌曲分组覆盖、迁移状态）。以单一 `MelodyDao` 起步，按聚合根演进。
- **Preferences DataStore**：播放器设置（全局均匀随机、蓝牙/通知开关）、轻量播放状态快照（`PlaybackStateSnapshotSerializer` 结构化 JSON，禁止内联正则解析）。
- **恢复契约**：重启后恢复队列、播放模式、当前歌曲、播放位置（**暂停状态，不自动播放**）；播放中节流保存当前歌曲与位置；系统直接杀进程时回退到最近一次定期保存。
- **备份规则**：排除播放状态、URI 权限、Room、DataStore、封面缓存，避免本地 URI/统计被云备份。

---

## 6. 播放状态机

Media3 负责传输状态，应用层状态机解释 service / controller / UI / 统计 / 持久化如何响应。

**规范状态**：`idle` → `preparing` → `ready` → `playing` ⇄ `paused`，以及 `buffering`、`ended`、`error`。

| 状态 | 触发 | 主要副作用 |
|------|------|-----------|
| `idle` | 无连接/无当前项/队列空 | 保持 UI 选择，不开始统计 |
| `preparing` | `setMediaItems`+`prepare` 未就绪 | 保持业务队列为来源，等快照 |
| `ready` | 有当前项且 `STATE_READY` 未播放 | 暂停/停止路径保存位置 |
| `playing` | `onIsPlayingChanged(true)` | 启动 `PlayDurationTracker`，更新歌曲/索引/时长/同名版本 |
| `paused` | 播放中 `onIsPlayingChanged(false)` 且非缓冲 | 暂停统计并保存状态 |
| `buffering` | `STATE_BUFFERING` | 不当作有意暂停，不保存暂停态 |
| `ended` | `STATE_ENDED`/自动切歌 | 停止统计、补无限队列、必要时恢复 add-next |
| `error` | controller 连接/命令失败 | 仅脱敏日志，UI 暴露可恢复动作 |

**不变量**：`MediaItem.mediaId` 必须为十进制 `Song.id`；`ControllerPlaybackStateSynchronizer` 是唯一将 controller 快照映射回 `PlayerUiState` 的地方；时长仅接受非负快照；系统媒体键必须经由 Media3 回调再进入同一同步路径。

---

## 7. 从 0 到 1 实施路线

> 每阶段产出可独立验证；核心三模块（4.1/4.2/4.3）优先于配套功能。

**Phase 0 — 脚手架**
- 建立 14 模块 Gradle 工程、Version Catalog、稳定 AGP。
- 接入 Hilt、Compose、Material3、Navigation Compose。
- 落地 `verifyProductArchitecture` 与 `spotlessCheck`，`check` 绑定架构门禁。
- *预期*：空壳应用可编译、可导航，CI 门禁通过。

**Phase 1 — 播放内核**
- 实现 `AppMediaSessionService` + `PlayerFactory`（含 FFmpeg 扩展加载）+ `PlaybackController`。
- 暴露播放/暂停/seek/上一首/下一首，进度 500ms 刷新。
- *预期*：能播放单首本地音频，后台不中断。

**Phase 2 — 播放队列**
- 实现 `PlayQueue`、三种 `PlayMode`、`WindowedControllerQueuePlanner` / `ControllerWindowSynchronizer` / `PlaybackWindowPlanner`。
- 实现「添加到下一首 / 添加到队尾 / 切换模式 / 队列面板按索引移除」。
- 补齐 `ControllerQueuePlannerTest`（顺序/倒序/添加到下一首/切换模式/不可播放跳过/重复项）。
- *预期*：1000 首队列下切歌与模式切换不卡顿，行为与 UI 一致。

**Phase 3 — 系统通知与集成**
- 接入 Media3 默认媒体通知（封面/标题/艺术家/三按钮）。
- 耳机媒体键、蓝牙断开自动暂停、ToastHost。
- 权限（`POST_NOTIFICATIONS`/`BLUETOOTH_CONNECT`）按需申请与拒绝文案。
- *预期*：锁屏/通知/控制中心可控且实时同步；耳机与蓝牙行为正确。

**Phase 4 — 持久化与恢复**
- Room（实体与 DAO）+ DataStore + 播放状态快照序列化与恢复。
- *预期*：重启后恢复队列/模式/进度/歌曲（暂停不自动播放）。

**Phase 5 — 产品化功能**
- 歌单、歌词（内嵌+外部 LRC）、封面、统计、多版本管理、秒切、无限播放、全局均匀随机、SAF 导入、搜索多选、主题。
- *预期*：功能闭环，覆盖真实使用场景。

**Phase 6 — 质量与性能**
- 播放器/仓库/序列化单测、Compose 仪表化测试、Macrobenchmark 冷启动。
- 性能埋点（冷启动/导入/切歌/队列同步耗时）。
- *预期*：测试通过；关键指标有基线数据。

---

## 8. 关键决策与预期

| 决策 | 选择 | 预期收益 / 代价 |
|------|------|----------------|
| 队列同步 | 业务队列 + 窗口化 Controller 队列 | 大曲库性能可控；实现复杂度上升 |
| 随机策略位置 | 置于 `domain`（Planner），不污染 `PlayQueue` | 可测试、可复用；需依赖注入装配 |
| 通知实现 | Media3 默认通知 | 开发快、行为统一；自定义样式受限 |
| 解码扩展 | `DefaultRenderersFactory` + `PREFER` 反射加载 | 兼容更多格式；依赖声明但不显式引用 |
| 进程模型 | 单进程 | 简单、状态一致；极端解码崩溃会影响 UI（可接受） |
| 持久化 | Room + DataStore + legacy 迁移 | 可查询/可迁移；需编写迁移与兼容代码 |

---

## 9. 验收标准

**功能验收**
- [ ] 本地音频可播放、暂停、拖动进度、上一首/下一首。
- [ ] 队列支持顺序/倒序/随机；添加到下一首在三端（App/通知/耳机）一致。
- [ ] 通知栏与锁屏显示封面/标题/艺术家并可控；耳机按键生效；蓝牙断开自动暂停。
- [ ] 重启后恢复队列/模式/进度（暂停不自动播放）。
- [ ] 同一首歌在队列中多次出现时，高亮/移除/保存/恢复均正确。

**性能预期**
- 冷启动到首页可交互：目标 < 1.5s（设备/模拟器实测基线）。
- 切歌：1000 首队列下 < 300ms 完成队列同步与播放。
- 进度刷新稳定 500ms，无异常重组与耗电。

**兼容与质量**
- minSdk 33 起所有核心功能可用；Android 12+ 动态取色生效。
- 全量 JVM 单测通过；release 包 R8 + 资源压缩可打包。
- `verifyProductArchitecture` + `spotlessCheck` 通过。

---

## 10. 风险与对策

| 风险 | 影响 | 对策 |
|------|------|------|
| 大曲库全量 `setMediaItems` 卡顿 | 切歌/模式切换卡顿 | 窗口化队列（4.2），仅同步当前窗口 |
| 厂商系统任务管理差异 | 恢复不完整 | 播放中定期快照 + 服务销毁回调兜底保存 |
| 解码兼容性 | 个别格式/采样率失败 | FFmpeg 扩展渲染器 + 不可播放项过滤与 UI 提示 |
| 权限被拒影响功能 | 通知/蓝牙不可用 | 按需申请 + 拒绝文案 + 降级路径 |
| 重复队列项映射错误 | UI 高亮跳回首处 | `mediaId`→`Song.id` 唯一映射 + 当前索引保留规则 |
| 测试不足 | 回归成本高 | 优先纯 Kotlin planner/facade 单测 + 序列化测试 |

---

*本文档为从零构建提案；核心设计（窗口化队列、状态机、Media3 默认通知、依赖倒置）借鉴自已验证的多模块参考实现，可作为 AI 构建时的具体落地蓝图。*
