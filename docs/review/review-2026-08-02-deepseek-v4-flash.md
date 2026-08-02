# Melody in Heart 项目开发符合度审查报告

- 审查对象：`technical-proposal.md`（技术方案）
- 审查范围：仓库当前全部源码（`app` / `core:*` / `domain` / `data` / `player` / `feature:*` / `benchmark` / `build-logic`）
- 审查日期：2026-08-02
- 审查模型：deepseek-v4-flash
- 结论口径：逐条对照技术方案，标注「符合 / 部分符合 / 不符合 / 未验证」

---

## 1. 总体结论

**核心结论：项目开发与 `technical-proposal.md` 高度吻合，核心三大模块（播放内核 4.1、播放队列 4.2、系统通知与集成 4.3）均已按方案落地，架构门禁与 JVM 单测矩阵完整。**

偏差集中在三处：

1. **权限行为与方案冲突**：`POST_NOTIFICATIONS` 在启动时强制弹窗，且启动即跳转「电池优化豁免」设置页，违反方案 §4.3 / §9「权限按需申请，启动不强制弹窗」。
2. **P6 质量收尾未完成**：Compose 仪表化测试缺失、`ColdStartBenchmark` 为纯占位（无 `measureRepeated`）、兼容性矩阵（API 33/36）未跑，导致「冷启动 < 1.5s」「切歌 < 300ms」等性能验收项**无基线数据可验证**。
3. **文档与源码不同步**：`docs/development-plan.md` 中标注 `[x]` 的 `PlayerGraph` / `PlayerStateCoordinator` / `PlayerLyricsFacade` / `TransportBar` / `ProgressSlider` 在仓库中无源码；窗口化四件套实际位于 `:domain`（方案 4.2 与 dev-plan §2 描述为 `:player/window`）。

---

## 2. 评估方法

- 通读 `technical-proposal.md` 全文，建立验收条目清单（§3 架构、§4 核心设计、§5 数据模型、§6 状态机、§7 实施路线、§9 验收标准、§10 风险对策）。
- 对 `settings.gradle.kts`、`gradle/libs.versions.toml`、14 个模块源码、`build-logic` 架构门禁、全部测试文件进行逐条比对。
- 交叉核对 `docs/development-plan.md` 的 Phase 勾选状态与真实源码，识别「文档声称完成但代码缺失」的偏差。
- 核实关键实现细节（窗口常量、刷新间隔、反射加载、播放次数权重、保存节流、权限申请路径等）。

---

## 3. 平台与工程基线对照（§1.3 / §2 / §7 Phase 0）

| 方案要求 | 实现 | 状态 |
|---|---|---|
| 最低 SDK 33（API 33） | `minSdk = 33` | ✅ 符合 |
| 目标 / 编译 SDK 36（API 16） | `targetSdk = 36` / `compileSdk = 36` | ✅ 符合 |
| 单进程（无独立 `:codec` 转码进程） | 未引入任何 `:codec` 模块 | ✅ 符合 |
| Kotlin 2.0+ | `kotlin = 2.0.21` | ✅ 符合 |
| Media3 ExoPlayer 1.9.x | `media3 = 1.9.0` | ✅ 符合 |
| Jellyfin FFmpeg Decoder 经 `EXTENSION_RENDERER_MODE_PREFER` 反射加载 | `PlayerFactory` 设置 `PREFER` + `Class.forName("org.jellyfin.media3.ffmpeg.FfmpegLibrary")` 反射初始化，`implementation` 仅声明依赖、不显式引用渲染器 | ✅ 符合 |
| Hilt 装配 | 全部模块接入，`@Binds` 完成接口装配 | ✅ 符合 |
| Room + Preferences DataStore | Room 2.7.1 + datastore 1.1.1 | ✅ 符合 |
| Navigation Compose 路由化 | 6 个 feature 全部 Route 化 | ✅ 符合 |
| Gradle Version Catalog + 稳定 AGP 8.13.x | AGP `8.13.0` + `libs.versions.toml` 集中治理 | ✅ 符合 |
| 14 模块分层（§3.1） | `settings.gradle.kts` 声明与方案逐一对应（含 `build-logic` includeBuild） | ✅ 符合 |
| `verifyProductArchitecture` + `spotlessCheck` 绑定 `check` | 4 个 convention 插件统一注册，`check` 挂接两门禁 | ✅ 符合 |
| 无 INTERNET 权限（门禁 A6） | Manifest 无 INTERNET，门禁强制校验 | ✅ 符合 |

---

## 4. 核心模块实现对照（§4）

### 4.1 音乐播放

| 方案要求 | 实现位置 | 状态 |
|---|---|---|
| `AppMediaSessionService` 持有 ExoPlayer 与 MediaSession | `player/.../service/AppMediaSessionService.kt` | ✅ 符合 |
| `onTaskRemoved` / `onDestroy` 保存最后 mediaId 与 currentPosition | 两者均调 `saveSnapshotFallback()`（读取 `PlaybackStateBuffer` → `PlaybackStateRepository.saveSnapshot`），`onTaskRemoved` 另读取 `currentMediaItem.mediaId` / `currentPosition` 打日志；播放中划掉任务不 `stopSelf` | ✅ 符合（已升级为整体快照持久化，优于方案初版） |
| `PlayerFactory`：`DefaultRenderersFactory` + `EXTENSION_RENDERER_MODE_PREFER` | `player/.../PlayerFactory.kt` | ✅ 符合 |
| `PlaybackController`：连 MediaController、回调转 `ControllerPlaybackSnapshot`、暴露播放/暂停/seek/上一首/下一首/队列设置 | `player/.../PlaybackController.kt`（含 pending 缓冲、断线重连） | ✅ 符合 |
| 进度 500ms 刷新（`PlayerPlaybackProgressTicker`） | `INTERVAL_MS = 500`，`isPlaying` 播放中 tick | ✅ 符合 |
| `MediaItem.mediaId = Song.id.toString()` 锚点不变量 | `mapper/SongMediaItemMapper.kt` | ✅ 符合 |
| 不可播放项过滤（`uri != null`）与 UI 提示 | `PlaybackWindowPlanner` 默认 `isPlayable = { uri != null }` | ✅ 符合 |

### 4.2 播放队列

| 方案要求 | 实现位置 | 状态 |
|---|---|---|
| `PlayQueue`：`songs` / `currentIndex` / `playMode` / `playOrderIds` | `core/model/.../PlayQueue.kt` | ✅ 符合 |
| 重复项按出现次数解释，`currentPlayOrderIndices()` 还原队列项索引，禁止 `distinct()`/`associateBy(id)` | 算法按「剩余队列逐项匹配」，`PlayQueue` 本体无去重写法；门禁 A5 已启用 | ✅ 符合 |
| `PlayMode`：顺序 / 倒序 / 随机 | `SEQUENTIAL / REVERSE / RANDOM` | ✅ 符合 |
| `PlayQueue` 不持有导航逻辑 | 导航由 `PlaybackController.seekToNextMediaItem()` 执行 | ✅ 符合 |
| 窗口化：当前项前 20 + 后 50、窗口 ≤ 71、过滤不可播放项 | `domain/.../queue/PlaybackWindowPlanner.kt`（`LOOK_BEHIND=20`、`LOOK_AHEAD=50`、`MAX_WINDOW_SIZE=71`） | ✅ 符合 |
| `ControllerWindowSynchronizer`：缓存 `PlaybackWindowState`、失效/强制时重规划、边缘阈值 | 缓存 + `DEFAULT_REPLAN_THRESHOLD = 10` + `resolve`/`resolveDrift`/`forceReplan` | ✅ 符合 |
| `WindowedControllerQueuePlanner` 实现 `ControllerQueuePlannerPort`，读取 `currentPlayOrderIndices()` 保留重复项，不可播放项后补/前回退 | `domain/.../queue/WindowedControllerQueuePlanner.kt`（`snapToPlayable` 先向后再向前） | ✅ 符合 |
| 随机：`RandomQueuePlanner`（无限播放补队列）+ `UniformRandomPlanner`（播放次数权重，默认开，关闭退化 `shuffled()`） | `DefaultRandomQueuePlanner`（Fisher–Yates）+ `DefaultUniformRandomPlanner`（`weight = 1/(count+1)`，得分降序） | ✅ 符合 |
| 重建随机顺序走 `QueueManager.PlayOrderBuilder` / `UniformRandomPlanner`，`PlayQueue` 不直接依赖仓库 | `QueueManager.PlayOrderBuilder` + `QueueOperator`；权重经 `PlayerQueueFacadeImpl` 镜像注入统计 | ✅ 符合 |
| 添加到下一首 / 队尾 / 切换模式 / 按索引移除 | `QueueOperator`（`addSongAsNext` / `addSongsToTail` / `switchPlayMode` / `removeAt`），`QueuePanel` 按索引移除 | ✅ 符合 |
| 无限播放：接近队尾补批、均匀随机优先低播放次数、循环覆盖全库 | `InfiniteQueueExtender`（补批 50、阈值 10、未覆盖优先、全覆盖循环、不可播放剔除） | ✅ 符合 |
| 切换模式重建 `playOrderIds` → 经 Planner 重建 Controller 队列 | `QueueOperator.switchPlayMode` | ✅ 符合 |
| 「添加到下一首」批量按歌曲 ID 去重并移动到当前项之后，当前项作锚点 | `QueueOperatorTest` 覆盖该契约 | ✅ 符合 |

> **位置偏差（非功能缺失）**：方案 4.2 描述窗口化规划层为 `player/window`；实际 `ControllerQueuePlanner` / `PlaybackWindowPlanner` / `ControllerWindowSynchronizer` / `WindowedControllerQueuePlanner` 全部落在 **`:domain`**（纯 Kotlin，可 JVM 单测）。`dev-plan.md` §2 也将其列于 `:player/window`。职责完整、与方案 3.1「domain 含队列/随机/状态同步策略」自洽，但建议同步更新两处文档表述。

### 4.3 系统通知与系统集成

| 方案要求 | 实现位置 | 状态 |
|---|---|---|
| Media3 默认媒体通知（封面/标题/艺术家 + 上一首/暂停/下一首），不自定义 `NotificationCompat` | `DefaultMediaNotificationProvider.Builder` + 自定义 channel `melody_media_playback_v1`（IMPORTANCE_DEFAULT + VISIBILITY_PUBLIC）+ `LockScreenAwareNotificationProvider`；门禁 A7 强制无 `NotificationCompat.Builder` | ✅ 符合 |
| UI 经 `MediaController` 同步，controller 快照经 `ControllerPlaybackStateSynchronizer` 唯一映射 | `ControllerPlaybackStateSynchronizer.synchronize()` 为唯一映射点（门禁 A4） | ✅ 符合 |
| 耳机线控经 MediaSession/MediaController 接入，不新增广播接收器 | 依赖 Media3 默认媒体键处理 | ✅ 符合 |
| 蓝牙三件套 + 断开自动暂停（`ACTION_AUDIO_BECOMING_NOISY` 类事件） | `BluetoothStateManager` / `BluetoothPlaybackMonitor`（noisy + ACL_DISCONNECTED 双通道，与 `setHandleAudioBecomingNoisy(true)` 双保险）/ `BluetoothAudioQualityManager`（`AudioDeviceCallback` 路由信息） | ✅ 符合 |
| `BLUETOOTH_CONNECT` 按需申请 | ⚠️ **未接线**：`PermissionCoordinator`/`PermissionHost` 与拒绝文案已实现，但没有任何调用方触发蓝牙权限申请；蓝牙功能依赖无权限降级路径运行 | ⚠️ 部分符合 |
| ToastHost：多条堆积、2 秒自动消失、可手动关闭 | `core/ui/toast/`（`ToastController` + `ToastHost` + `LocalToastController`），点击整条关闭，2s `LaunchedEffect` 自动 dismiss | ✅ 符合 |
| 权限按需申请，启动不强制弹窗 | ❌ **违反**：`MainActivity.onCreate` 在 Android 13+ 启动即 `launch(POST_NOTIFICATIONS)`；且启动即跳转「电池优化豁免」系统设置页 | ❌ 不符合 |

---

## 5. 数据模型与持久化对照（§5）

| 方案要求 | 实现 | 状态 |
|---|---|---|
| `Song`（含 `sampleRate` / `albumArtUri` / `titleOverride`，派生 `groupKey`） | `core/model/.../Song.kt`（另有 `durationMs` / `playable`） | ✅ 符合 |
| `Playlist` + 歌单-歌曲多对多交叉引用 | `Playlist.kt` + `PlaylistSongRef.kt` / `PlaylistSongCrossRefEntity` | ✅ 符合 |
| `PlayQueue` 业务队列 | 见 4.2 | ✅ 符合 |
| `Lyrics` / `LyricLine` | `core/model/.../Lyrics.kt`（含二分 `indexAt()`） | ✅ 符合 |
| Room 8 实体（歌曲/歌单/交叉引用/统计/秒切/短播放/分组覆盖/迁移状态） | 8 个 `@Entity` 全齐 | ✅ 符合 |
| 单一 `MelodyDao` 起步 | 全库唯一 `@Dao` | ✅ 符合 |
| schema version 起始 1 | `version = 2`（1 → 2 经 `MIGRATION_1_2`），`exportSchema=true`，`schemas/` 已导出 1.json/2.json | ✅ 符合（正常演进一次） |
| DataStore：播放器设置 + 播放状态快照 | `PlayerSettingsDataStore` + `PlaybackStateDataStore` | ✅ 符合 |
| `PlaybackStateSnapshotSerializer` 结构化 JSON，禁止内联正则 | kotlinx.serialization，`runCatching` 容错 | ✅ 符合 |
| 恢复契约：暂停不自动播放、节流保存、进程被杀回退最近保存 | `PlayerRuntime.loadLibrary` → `QueueRestore`（不调 `play()`）；`PlayerViewModel` 5s 节流 + 暂停/非缓冲立即保存；`onTaskRemoved`/`onDestroy` 兜底 | ✅ 符合 |
| 备份排除播放状态/URI 权限/Room/DataStore/封面缓存 | `backup_rules.xml` / `data_extraction_rules.xml` 已配置 | ✅ 符合 |

---

## 6. 播放状态机对照（§6）

| 方案要求 | 实现 | 状态 |
|---|---|---|
| `PlaybackState` 枚举含 8 态 | `IDLE / PREPARING / READY / PLAYING / PAUSED / BUFFERING / ENDED / ERROR` | ✅ 符合 |
| `ControllerPlaybackStateSynchronizer` 为唯一映射点 | 门禁 A4 强制 | ✅ 符合 |
| 时长仅接受非负快照 | `if (snapshot.durationMs >= 0) ... else 0L` | ✅ 符合 |
| 系统媒体键经 Media3 回调进同一同步路径 | 依赖 MediaController 回调 | ✅ 符合 |
| **8 态全部参与映射** | ⚠️ **未完全落地**：`synchronize()` 仅产出 `BUFFERING / ERROR / PLAYING / PAUSED` 四态；`IDLE / PREPARING / READY / ENDED` 从未由同步器产出（`IDLE` 仅作 `PlayerUiState.empty` 默认值，`ENDED` 被归为 `PAUSED`）。方案 §6 状态机与实现存在差距 | ⚠️ 部分符合 |
| `ended` 状态停止统计、补无限队列 | 侧移由 `PlayDurationTracker` + `InfiniteQueueExtender` 承担 | ✅ 符合（以组件行为实现，非状态机驱动） |

---

## 7. 实施路线与文档对齐（§7 + dev-plan）

| Phase | dev-plan 勾选 | 实际源码 | 状态 |
|---|---|---|---|
| P0 脚手架 | 全部 [x] | 14 模块 + 门禁 + Version Catalog 齐 | ✅ 一致 |
| P1 播放内核 | 全部 [x] | `AppMediaSessionService` / `PlayerFactory` / `PlaybackController` / ticker 齐 | ✅ 一致 |
| P2 播放队列 | 全部 [x]，含 **P2-9 `PlayerGraph` + `PlayerStateCoordinator`** | `PlayerRuntime` 已落地，但 `graph/PlayerGraph.kt`、`coordinator/PlayerStateCoordinator.kt` **无源码**；`feature:player` 仅 `runtime/` 与 `component/QueuePanel.kt`（dev-plan 列出的 `TransportBar.kt` / `ProgressSlider.kt` 也不存在） | ⚠️ 文档与实际不同步 |
| P3 通知与集成 | 全部 [x]，含 **P3-7 权限按需申请** | 通知/蓝牙/ToastHost 齐；但 P3-7「按需申请，启动不强制弹窗」与 `MainActivity` 启动强制弹窗 **矛盾** | ❌ 不一致 |
| P4 持久化与恢复 | 全部 [x] | Room 8 实体 + 序列化 + 恢复链路齐 | ✅ 一致 |
| P5 产品化功能 | 5A/5B/5C 全部 [x] | SAF 导入、歌单、歌词（内嵌+外部 LRC）、封面、统计、多版本、秒切、无限播放、均匀随机、搜索多选、主题全齐 | ✅ 一致 |
| P6-1 JVM 单测矩阵 | [x] | 19 个 JVM 测试文件（`:core:model`/`:domain`/`:data`），覆盖方案要求的全部场景（见 §9） | ✅ 一致 |
| P6-2 Compose 仪表化测试 | [ ]（未勾选） | 全仓除 `:benchmark` 外无 `androidTest` 源码目录 | ❌ 未完成 |
| P6-3 Macrobenchmark | [x] | `ColdStartBenchmark.coldStart()` **仅含占位注释，无 `measureRepeated` 调用** | ❌ 文档与实际不同步（骨架在，无实际测量） |
| P6-4 性能埋点 | [x] | `PerfTracer`（冷启动/导入/切歌/队列同步）已接入 | ✅ 一致（但无基线数据报表） |
| P6-5 Release R8 | [x] | `proguard-rules.pro` 含 Media3 / Jellyfin FFmpeg / Hilt / Room / kotlinx.serialization keep 规则 | ✅ 一致 |
| P6-6 门禁固化 | [x] | `check` = spotless + verifyProductArchitecture + JVM 单测 | ✅ 一致 |
| P6-7 兼容性矩阵（API 33/36） | [ ]（未勾选） | 无相关验证记录 | ❌ 未完成 |

---

## 8. 架构门禁与代码质量（§3.2 设计原则）

| 原则 / 门禁 | 实现 | 状态 |
|---|---|---|
| 依赖倒置：feature/domain 依赖 domain 接口，实现在 data | `RepositoryBinder.kt` 完成 `@Binds`；A2 门禁防 feature→data | ✅ 符合 |
| UI 只渲染状态，不推断 Media3 | `PlayerViewModel` 收集 `runtime.snapshot` → `ControllerPlaybackStateSynchronizer` → `PlayerUiState` | ✅ 符合 |
| `PlayerViewModel` 为薄门面 | 112 行，全部委托 `PlayerRuntime` | ✅ 符合 |
| 装配与逻辑分离（`PlayerRuntime` + Facade） | `PlayerTransportFacade` / `PlayerQueueFacade(Impl)` 落地 | ✅ 部分符合（`PlayerFacade` 接口为空占位，职责实际由两个子 Facade 承担） |
| A1–A7 门禁 | `ArchitectureVerification.kt` 实现全部 7 条规则，A5 已生效 | ✅ 符合 |
| Spotless + ktlint | `spotlessCheck` 挂 `check` | ✅ 符合 |
| R8 反射 keep 规则 | `proguard-rules.pro` | ✅ 符合 |

---

## 9. 验收标准逐项核对（§9）

### 功能验收

| 验收项 | 证据 | 状态 |
|---|---|---|
| 本地音频可播放/暂停/拖动/上一首/下一首 | P1 全链路 + 单测 | ✅ 已实现 |
| 顺序/倒序/随机；添加下一首三端一致 | `QueueConsistencyRegressionTest` 等回归基线 + 统一走 `PlaybackController` | ✅ 已实现 |
| 通知/锁屏封面可控；耳机按键；蓝牙断开自动暂停 | Media3 默认通知 + 蓝牙双通道 | ✅ 已实现 |
| 重启恢复队列/模式/进度（暂停不自动播放） | `QueueRestore` + 序列化 + 恢复单测 | ✅ 已实现 |
| 重复歌曲高亮/移除/保存/恢复正确 | `PlayQueueTest` / `QueueRestoreTest` / `ControllerQueuePlannerTest` 重复项用例 | ✅ 已实现 |
| **权限按需申请，启动不强制弹窗** | `MainActivity.onCreate` 启动强制申请 + 跳转电池优化页 | ❌ 不符合 |

### 性能预期

| 验收项 | 证据 | 状态 |
|---|---|---|
| 冷启动 < 1.5s | `ColdStartBenchmark` 为占位，**无实测基线** | ⚠️ 未验证 |
| 1000 首队列切歌 < 300ms | 窗口化设计到位 + `PerfTracer` 埋点，但**无实测数据**；`check_run.log` 亦未提供测量结果 | ⚠️ 未验证 |
| 进度刷新稳定 500ms | `PlayerPlaybackProgressTicker(500)` 设计正确，无重组计数证据 | ✅ 实现正确 / ⚠️ 无实证 |

### 兼容与质量

| 验收项 | 证据 | 状态 |
|---|---|---|
| minSdk 33 起核心功能可用；Android 12+ 动态取色 | `minSdk=33`，动态取色开关存在且默认 off（设置页可开）；API 33/36 兼容性矩阵 **未执行** | ⚠️ 部分验证 |
| 全量 JVM 单测通过 | 19 个测试文件；最近一次 `gradlew check` 因 **Windows 文件锁/缓存删除 IO 失败**（`data/build/...` 与 `.gradle/...last-build.bin` 拒绝访问）中止，**非代码失败**，但「全绿」未被本次验证 | ⚠️ 未复验 |
| release 包 R8 + 资源压缩可打包 | release buildType 开启 minify + shrinkResources，keep 规则齐 | ✅ 配置符合（本次未实际执行 assembleRelease） |
| `verifyProductArchitecture` + `spotlessCheck` 通过 | 门禁注册完整；本次未复跑 | ✅ 配置符合（未复验） |

---

## 10. 问题清单（按严重程度）

### P0 — 与方案直接冲突（需修复）

1. **启动强制弹窗申请 `POST_NOTIFICATIONS`**（`app/src/main/java/cn/com/dcsgo/mihx/MainActivity.kt:32-40`）
   - 违反方案 §4.3 预期与 §9「权限按需申请，启动不强制弹窗」，且与 `dev-plan` P3-7 `[x]` 声称矛盾。
   - 建议：删除 onCreate 内的强制申请，接入已就绪的 `PermissionCoordinator`/`PermissionHost` 按需触发；拒绝文案（已写好）在首播/通知需要时展示。

2. **启动即跳转「电池优化豁免」设置页**（`MainActivity.kt:45-56`）
   - 方案未包含该行为；强制跳系统设置页属于比权限弹窗更侵入的启动干扰。
   - 建议：改为设置页内引导（用户主动开启），或至少不阻断首帧。

### P1 — 方案明确项未落地

3. **`BLUETOOTH_CONNECT` 按需申请未接线**（`PermissionCoordinator.kt:26` 声明了该权限与文案，但全仓无触发点）
   - 蓝牙功能完全依赖无权限降级路径，未实现「申请 + 拒绝文案」用户流程。

4. **Compose 仪表化测试缺失**（`dev-plan` P6-2 未勾选）
   - 全仓无任何 `androidTest` 源码（仅 `:benchmark`）。方案 Phase 6 明确要求 `PlayerScreen` 传输控制 / `QueuePanel` 重复项 / `ToastHost` / `HomeScreen` 多选等 UI 测试。

5. **`ColdStartBenchmark` 为占位**（`benchmark/.../ColdStartBenchmark.kt:17-18`）
   - `dev-plan` P6-3 标 `[x]`，但无 `measureRepeated` 调用，无法产出冷启动基线，「冷启动 < 1.5s」验收无数据支撑。

6. **兼容性矩阵（API 33/36）未执行**（P6-7 未勾选）
   - minSdk 下限与 target 上限双端验证缺失。

### P2 — 文档/实现不一致与设计缝隙

7. **状态机 8 态仅映射 4 态**（`ControllerPlaybackStateSynchronizer.kt:12-17`）
   - `IDLE / PREPARING / READY / ENDED` 未产出；`ENDED` 归入 `PAUSED`。若后续 UI 需要区分「已结束」与「暂停」需补齐映射（`ended` 还关联统计结算与无限播放补队列语义）。

8. **dev-plan 标注 `[x]` 的源码缺失**：`PlayerGraph`、`PlayerStateCoordinator`、`PlayerLyricsFacade`、`component/TransportBar.kt`、`component/ProgressSlider.kt`。
   - 职责实际由 `PlayerRuntime` + 两个 Facade + `SeekSlider`/`QueuePanel` 承担，属文档陈旧；建议将 dev-plan 中这些条目标注更新为「已合并实现」或删除。

9. **窗口化四件套位置**：实际位于 `:domain/.../queue`，方案 4.2 与 dev-plan §2 描述为 `:player/window`。职责完整、设计自洽（利于纯 JVM 单测），建议修正文档表述而非搬移代码。

10. **`PlayerFacade` 空接口占位**（`feature:player`）：无方法声明，实际职责由 `PlayerTransportFacade` / `PlayerQueueFacade` 承担，属接口冗余。

### P3 — 工程卫生

11. **`check_run.log` 显示构建失败**：失败原因是 Windows 文件锁/缓存目录删除被拒（`data/build/intermediates/...`、`.gradle/.../last-build.bin` 拒绝访问），非代码错误；但需确认在干净环境 `gradlew.bat check` 可全绿。
12. **「Kotlin Gradle plugin loaded multiple times」警告**：建议在版本目录中对 KGP 相关坐标 `apply false` 收敛。
13. **`:benchmark` 不走 convention 插件**：未注册 `verifyProductArchitecture`/`spotlessCheck`，与 P6-6「check 全量门禁」存在缝隙（benchmark 代码不受门禁约束）。

---

## 11. 结论与建议

**总体判定：符合技术方案架构与功能要求，核心三模块高质量落地；验收未全绿集中在「权限体验」「P6 质量收尾」「文档一致性」三类。**

建议按以下顺序处置：

1. **修复 P0**：移除 `MainActivity` 启动强制弹窗与电池优化页跳转，接入现有 `PermissionCoordinator` 按需申请；让权限行为回到方案轨道。
2. **补齐 P1**：接线 `BLUETOOTH_CONNECT` 申请触发点；实现至少一个 Compose 仪表化测试（建议 `PlayerScreen` 传输控制或 `QueuePanel` 重复项移除）打通 `androidTest` 链路；让 `ColdStartBenchmark` 跑出真实基线（`measureRepeated` + `StartupMode.COLD`，迭代 ≥5）；执行 API 33/36 兼容性矩阵。
3. **同步文档**：更新 `development-plan.md`，将已合并实现（`PlayerGraph`/`PlayerStateCoordinator`/`PlayerLyricsFacade`/窗口化位置）与真实代码对齐，撤销或修正与代码矛盾的 `[x]` 标注。
4. **补齐状态机映射**：评估是否需要将 `PREPARING/READY/ENDED` 纳入 `ControllerPlaybackStateSynchronizer` 输出，以对齐方案 §6。
5. **工程卫生**：干净环境复跑 `gradlew.bat check` 与 `:app:assembleRelease`，收敛 KGP 重复加载警告，为 `:benchmark` 接入门禁。

---

*本报告由 deepseek-v4-flash 基于源码静态审查生成；性能类结论（冷启动/切歌耗时）因缺少实测基线仅作「未验证」标注，不构成性能不达标的判定。*
