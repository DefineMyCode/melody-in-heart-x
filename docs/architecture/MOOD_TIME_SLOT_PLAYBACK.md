# 功能设计：情境化随心播放增强（时段 × 情绪词条）

| 项目 | 内容 |
|------|------|
| 状态 | 设计评审通过，待排期实施 |
| 提出 | 2026-09-03 |
| 文档性质 | 产品评审结论 + 一期技术方案 |
| 关联模块 | `:feature:user`（配置 UI）、`:feature:home`（随心播放入口）、`:domain`（时段判定 / planner 过滤）、`:core:model`（配置模型） |

---

## 1. 需求背景与原始提案

### 1.1 用户提案原文要点

基于歌曲情绪实现不同**场景、时段**下的随心播放增强：

- 用户在「我的」页面的随心播放设置中添加**场景**（场景名 + 情绪词条）与**时段配置**（时段名 + 时间段 + 聚合场景 + 情绪词条，既可通过场景关联词条也可直接关联词条）；
- 配置好后可通过开关启用增强：随机歌曲只能是具有特定情绪词条的歌曲；
- 增强须与**全局均匀随机**兼容（开启后随心播放仍优先低播放次数歌曲）；
- **最多一个场景生效**；时段配置生效时场景失效，除非当前时刻没有时段生效；
- 时段的时间段**不能有重合部分**（左闭右开区间）；
- 配置词条时展示该词条关联的歌曲数；
- 为所有配置项提供说明。

### 1.2 与现有系统的衔接

该功能是**存量能力的组合创新**，无需要从零建设的基础设施：

| 现有资产 | 位置 | 对本功能的作用 |
|----------|------|----------------|
| 情绪词条体系（10 组 × 4 词，V-A 锚定） | `core/model/EmotionGroup.kt` | 可选词条的封闭集合，含 `auto=false` 组（鬼畜/沙雕不参与自动投票） |
| 展示词条计算（用户校准优先，否则曲线投票） | `core/model/EmotionTags.kt` `emotionTagsOf()` | 歌曲→词条的判定函数，过滤池的构建依据 |
| 随心播放（随机队列 + 无限随机） | `domain/playback/RandomQueuePlanner.kt` + `feature/player/PlayerRandomQueueFacade.kt` | 增强的挂载点 |
| 全局均匀随机（低播放次数分层优先） | `domain/playback/UniformRandomPlanner.kt` | 兼容性目标；分层抢占逻辑在候选池过滤后可直接复用 |
| 情绪数据 | `domain/repository/SongEmotionRepository.getAll(): Map<Int, SongEmotion>` | 词条歌曲数的统计源 |
| 词条筛选 UI 先例 | `feature/playlist/EmotionLibraryView.kt`（词条 chips 过滤 + `tagOptions` 统计） | 配置页词条选择器的交互蓝本 |
| 「我的」页卡片容器 | `feature/user/UserScreen.kt`（现有 4 卡） | 新配置入口卡的位置 |
| 最近播放淘汰 + 批量生成 | `RandomQueuePlanner`（`DEFAULT_BATCH_SIZE=20`、`recentSongIds`、`trimToLimit`） | 增强后仍要正常工作的既有机制 |

---

## 2. 产品评审结论

### 2.1 总评

**方向正确，复杂度超前于价值验证；建议一期砍掉「场景」抽象与「时段聚合场景」，只做「时段 × 情绪词条」的最小闭环。**

### 2.2 判断依据

**（1）场景与时段是嵌套的两套心智模型，一期只该做一套。**

原始提案中时段聚合场景、场景关联词条、时段又可直连词条，构成三层模型加旁路：

```
时段 → 场景 → 词条
时段 → 词条（旁路）
```

这带来：三层配置 UI、四条生效分支、以及最致命的——**归因困难**（用户听到一首歌，无法回答"这是哪条配置决定的"）。归因困难是自动化功能最大的体验杀手。

核心洞察：**用户真正的痛点是"晚上想听安静的"，不是"先建一个场景再配时段引用它"。** 场景是时段的子组件而非独立功能。一期让词条直接挂在时段上；当用户发现多个时段共享同一组词条、重复配置变烦时，场景抽象会被需求自然"长出来"——那时再做抽象，迁移只是时段表加一列 `sceneId`，成本极低。先建抽象再验证需求，是本末倒置。

**（2）与均匀随机的兼容只完成了一半，小候选池才是最危险的边界。**

"过滤后交给 `UniformRandomPlanner`"确实能天然兼容低播放优先（见 §4.2），但 PRD 没提的是：**情绪过滤后的候选池可能太小甚至为空**。曲库里只有 3 首"静谧"歌时，候选池小于 `batchSize(20)`，且现有 `recentSongIds` 淘汰机制会与之正面冲突——池子全进 recent → `resetHistory` → 永远循环同一批歌。这必须前置定义规则（§4.3），并借"词条歌曲数"展示做成配置时的预警闭环。

**（3）词条歌曲数有隐性成本，实现层要选对。**

词条来自 `emotionTagsOf()`（运行时计算，非数据库列）。好在 `getAll()` 全量接口与 `EmotionLibraryView` 的 `tagOptions` 统计先例都已存在，**复用即可**；但必须放 ViewModel 层做 `stateIn` 派生流，不能在组合期现算（参照本项目评审 M-7 的教训）。

**（4）生效开关语义必须收紧为「全局一个」。**

时段互斥（左闭右开）已保证同一时刻最多一个配置生效；开关只需回答"这套自动化整体开不开"。多开关会制造"三个配置开了两个，现在谁生效"的解读负担。且**默认必须关闭、空配置不改变现有行为**——这是功能安全的底线。

### 2.3 交互陷阱（需求评审时已识别，实现必须处理）

| 陷阱 | 说明 | 对策 |
|------|------|------|
| 跨午夜时段 | 22:00–06:00 存储上 `end < start`，重叠校验须按环形时间轴 | 见 §3.1 / §4.4 |
| 重叠校验时机 | 保存后才报错体验极差 | 时间选择器上直接禁用已占用区间 / 选完即标红，校验前置到输入时 |
| `auto=false` 词条 | 鬼畜/沙雕不参与自动投票，仅手动标记可用；用户选了该词但从未手动标记 → 候选池为 0 | 词条选择器单独分组 / 置灰提示"仅手动标记的歌曲会计入" |
| 时钟选择 | 时段判定依据 | `System.currentTimeMillis()`，与睡眠定时器等既有时间逻辑一致 |

### 2.4 一期明确不做的

- **12/24 小时制双轨输入**：系统时间选择器本身跟随系统设置，自造双制式输入是负体验。存储统一用"当日分钟数"（0–1439），展示交给格式化层。
- **场景抽象 / 时段聚合场景**：留待二期，由真实使用反馈决定（见 §6）。
- **候选不足时自动放宽到相邻 V-A 象限**：二期候选增强，一期只做降级提示。

---

## 3. 一期功能定义

### 3.1 用户故事

```
作为听众，
我希望随心播放能感知当前时段（如深夜、通勤、午后），
只从我指定情绪词条的歌曲中随机，
让"随手一点"更贴合此刻的氛围。
```

### 3.2 数据模型（一期）

```kotlin
// core/model/TimeSlotConfig.kt
data class TimeSlotConfig(
    val id: Long,
    val name: String,                 // 时段名，如「深夜静谧」
    val startMinutes: Int,            // 当日分钟数 0–1439（含）
    val endMinutes: Int,              // 当日分钟数 0–1439（不含）；end < start 表示跨午夜
    val tags: List<String>,           // 情绪词条（EmotionGroup 词表内的词，多选）
)
```

- 存储：**DataStore JSON 序列化**（配置量级为个位数～十几条，不值得 Room schema +1；项目已有 `PlaybackStateSnapshotSerializer` 同款先例）。持久化键建议 `time_slot_configs`。
- 全局开关：复用现有 `PlayerSettingsRepository` 模式，新增 `moodTimeSlotEnabled`（默认 `false`）。
- 模型放 `:core:model`，不携带任何 Android / Compose 依赖以外的类型。

### 3.3 生效规则

```
生效配置 = moodTimeSlotEnabled && 当前时刻命中某时段 ? 该时段 : null
```

- **互斥**：同一时刻至多命中一个时段（配置时保证区间两两不重叠，见 §4.4）。
- **区间语义**：左闭右开 `[start, end)`；`end <= start` 视为跨午夜（如 22:00–06:00 覆盖 `[22:00, 24:00) ∪ [00:00, 06:00)`）。`start == end` 视为非法（零长度），配置页禁止保存。
- **降级**：开启开关但当前无命中时段（或命中时段候选池不足）时，随心播放回退为现有全库行为，并通过 toast 明确归因（见 §4.5）。

### 3.4 UI 结构

**「我的」页第 5 张入口卡**（`feature/user`，沿用现有卡片视觉）：

```
┌─────────────────────────────┐
│ 随心播放增强                 │
│ 夜间模式 · 生效中 22:00–06:00 │  ← 无配置: 「未配置，点击添加」
│                    [开关]    │  ← 全局开关,默认关
└─────────────────────────────┘
```

**配置列表页**（Route/Screen 模式，`:feature:user`）：

- 顶部：全局开关 + 一句话说明；
- 时段卡片列表（LazyColumn，`key = config.id`）：名称、时间段、词条 chips、该组词条合计歌曲数；
- 「添加时段」入口 + 每卡编辑/删除；
- 空状态说明文案（见 §5 文案表）。

**配置编辑页**：

- 时段名（必填，重复名校验）；
- 时间段：系统 `TimePicker` 两个（开始/结束），跨午夜自动识别（end ≤ start 即跨天）并在下方实时换算为可视区间；与其他时段重叠时**即时标红并禁用保存**；
- 词条选择器：**复用 `EmotionLibraryView` 的词条 chips 交互**——每组词条一个 chip，选中态高亮；每个 chip 带歌曲数角标；`auto=false` 组单独分组并提示"仅手动标记的歌曲会计入"；某词条歌曲数为 0 时置灰仍可选（但卡片上给出"当前 0 首"预警）；
- 每个配置项旁有 info 图标，点开展开说明（文案见 §5）。

---

## 4. 技术方案

### 4.1 模块与文件规划

| 层 | 内容 | 模块 |
|----|------|------|
| 模型 | `TimeSlotConfig`、`MoodSlotDecision` | `:core:model` |
| 域逻辑 | `MoodSlotResolver`（时刻→生效配置，纯函数）、planner 过滤参数 | `:domain` |
| 数据 | `TimeSlotConfigStore`（DataStore JSON）、`PlayerSettingsRepository` 新增开关 | `:data` |
| UI | 我的页入口卡、配置列表/编辑 Route+Screen | `:feature:user` |
| 编排 | `PlayerMoodSlotFacade` 或并入现有 `PlayerRandomQueueFacade` 的调用侧 | `:feature:player` |

架构约束自查：feature 间不互依；`:feature:user` 只依赖 `:core:*` 与 `:domain`；配置存储放 `:data` 经窄接口暴露；全部走 `verifyProductArchitecture` 既有规则，无需放宽。

### 4.2 随机链路增强（关键设计：**过滤发生在 planner 入口，不在 planner 内部**）

```kotlin
// PlayerRandomQueueFacade.playRandomQueue()（伪码）
val moodTags = moodSlotResolver.activeTags(now = System.currentTimeMillis())
// moodTags: Set<String>?  null = 增强未生效（开关关 / 无命中时段）

val candidates = if (moodTags != null) {
    val emotionBySong = songEmotionRepository.getAll()   // ViewModel 层预取,不在组合期算
    state().songs.filter { song ->
        emotionBySong[song.id]?.let { emotionTagsOf(it).any { tag -> tag in moodTags } } == true
    }
} else state().songs

val plan = planner.planRandomQueue(
    songs = candidates,                       // ← 只换候选池,其余机制全部复用
    recentSongIds = recentPlayedSongIds,
    uniformRandomEnabled = state().globalUniformRandomEnabled,
    playCounts = rawPlayCounts(...),
)
```

**均匀随机兼容自动成立**：过滤后的候选池进入 `UniformRandomPlanner.selectSongs()`，其"按播放次数动态分层、低层优先抢占"的逻辑原样生效——PRD 的兼容性要求不需要改 planner 一行代码。

**无限随机 refill 同样自动成立**：`planInfiniteStart` / `planInfiniteRefill` 接受同一份过滤后候选池，`refillInfinitePlayQueue` 的尾批补货在增强生效期间持续只补情绪内歌曲。

**`recentSongIds` 淘汰机制照常工作**：候选池变小后 `neededSize = minOf(batchSize, candidates.size)` 已有池子收缩保护，淘汰历史仍按池比例 `trimToLimit`。

### 4.3 小候选池策略（必须实现，PRD 补充项）

| 候选池规模 | 行为 |
|-----------|------|
| 为空（该词条组合 0 首） | 增强不生效，回退全库随机 + toast「按词条未找到歌曲，已播放全部歌曲随机」；配置页该词条组合标红 |
| `0 < size < POOL_WARN_THRESHOLD(=10)` | 正常生效 + 启动 toast 归因提示「『xx』词条下歌曲较少（N 首），将循环播放」 |
| `size >= 10` | 正常生效，toast 归因一次 |

阈值常量放 `:domain`（`MoodSlotPolicy`），与 `RandomQueuePlanner.DEFAULT_BATCH_SIZE` 同处可调。

### 4.4 `MoodSlotResolver`（域层纯函数，单测重点）

```kotlin
// domain/playback/MoodSlotResolver.kt
class MoodSlotResolver {
    /** 当前时刻命中的时段词条；null = 增强未生效 */
    fun activeTags(
        configs: List<TimeSlotConfig>,
        enabled: Boolean,
        nowMinutesOfDay: Int,   // 0–1439，由调用方从 System.currentTimeMillis() 换算
    ): Set<String>?
}
```

单测必须覆盖的边角：

- 命中单个普通时段；
- 跨午夜时段（22:00–06:00）：23:00 / 03:00 / 05:59 命中，06:00 不命中，21:59 不命中；
- 边界时刻归属（左闭右开）：`start` 整点命中、`end` 整点不命中；
- 多时段互斥（配置合法前提下任意时刻至多一命中）；
- 开关关闭 → 恒 null；
- 空配置 → 恒 null。

时段重叠校验同样抽为 `:domain` 纯函数（环形时间轴两两判交），供配置编辑页即时校验复用——同一份逻辑，UI 与持久化两侧不重复实现。

### 4.5 归因提示（防"归因困难"）

随心播放启动（含无限随机开启）时，若增强生效：

```
已按「深夜静谧」为你随机播放 · 静谧 空灵 禅
```

降级时（无命中时段但开关开着）不提示——用户没配时段却开着开关属于"准备好未来触发"，打扰无益；仅候选池为空的被动降级才提示。

### 4.6 配置页数据（词条歌曲数）

- ViewModel 持有 `emotionRows` 派生流：`songEmotionRepository.getAll()` → 按 `emotionTagsOf` 拍平为 `Map<String, Int>`（词条→歌曲数），`stateIn(WhileSubscribed)`；
- 与 `EmotionLibraryView` 的 `tagOptions` 同源同算法，保证两处计数一致；
- 组合期零计算（评审 M-7 模式）。

---

## 5. 文案表（配置项说明，即需求中的"为所有配置项提供适当的说明"）

| 位置 | 文案 |
|------|------|
| 入口卡副标题（未配置） | 未配置，点击添加 |
| 入口卡副标题（生效中） | {时段名} · 生效中 {时间段} |
| 入口卡副标题（已配置但不在时段内） | {时段名} · 未在时段内 |
| 全局开关说明 | 开启后，随心播放与无限随机将优先从当前时段配置的情绪歌曲中随机；未命中任何时段时保持原有随机 |
| 时段名字段说明 | 给这个时段起个名字，如「深夜静谧」「通勤提神」 |
| 时间段字段说明 | 一天内该增强生效的时间范围；结束时间早于开始时间表示跨午夜（如 22:00–06:00）；各时段之间不能重叠 |
| 词条字段说明 | 只有带有这些情绪词条的歌曲会被随机到；词条来自情绪分析与你对歌曲的手动标记 |
| 词条歌曲数角标 | {N} 首 |
| 词条歌曲数为 0 | 该词条下暂无歌曲（0 首） |
| `auto=false` 组提示 | 仅手动标记过该词条的歌曲会计入 |
| 保存校验失败（重叠） | 时间段与「{冲突时段名}」重叠，请调整 |
| 保存校验失败（零长度） | 开始与结束时间不能相同 |
| 随心播放归因 toast | 已按「{时段名}」为你随机播放 |
| 小池子归因 toast | 「{时段名}」词条下歌曲较少（{N} 首），将循环播放 |
| 空池降级 toast | 按词条未找到歌曲，已播放全部歌曲随机 |
| 列表页空状态 | 还没有时段配置。添加一个，让随心播放跟着一天的心情走~ |

---

## 6. 二期展望（一期验证通过后再议）

| 方向 | 触发条件 | 说明 |
|------|----------|------|
| 场景抽象（时段聚合场景） | 用户出现"多个时段配同一组词条"的重复配置 | 时段表加 `sceneId` 列，场景独立 CRUD；迁移成本低 |
| 候选不足自动放宽 | 小池子 toast 高频出现 | 候选 < 阈值时自动并入锚点 V-A 距离最近的相邻组词条，并在归因 toast 中注明"已放宽" |
| 权重/比例增强 | 用户反馈"完全限定太死" | 词条间按比例混合（如 70% 静谧 + 30% 放松），planner 分层逻辑天然支持 |
| 一次性临时场景 | 出差/加班等临时需求 | 与持久时段正交，到期自动失效 |

---

## 7. 实施清单（一期）

1. `:core:model` `TimeSlotConfig`；
2. `:domain` `MoodSlotResolver`（含环形重叠校验纯函数）+ `MoodSlotPolicy` 常量 + 全边角单测；
3. `:data` `TimeSlotConfigStore`（DataStore JSON）+ `PlayerSettingsRepository` 新增 `moodTimeSlotEnabled`（含 DataStore 键、读写、测试）；
4. `:feature:player` 随机链路接过滤（playRandomQueue / infinite start / refill 三处同一候选池来源）+ 归因 toast；
5. `:feature:user` 入口卡 + 配置列表页 + 编辑页（Route/Screen 模式，词条选择器复用 EmotionLibraryView 交互模式，歌曲数派生流走 `stateIn`）；
6. `:app` 导航接线；
7. 回归项：关闭开关后随心播放行为与现状逐位一致（`RandomQueuePlanner` 既有测试全部不动且必须全绿）；`check` 全绿。

## 8. 风险与开放问题

| # | 风险/问题 | 处置 |
|---|-----------|------|
| R1 | 情绪分析覆盖率低（如仅 30% 歌曲已分析）导致增强后池子骤缩 | 配置页展示「词条歌曲数」即覆盖率信号；小池子策略 §4.3 兜底；文档引导用户先跑情绪分析 |
| R2 | `auto=false` 词条误配导致 0 候选 | 词条选择器分组提示 + 空池降级 |
| R3 | 时段边界（整点归属、跨午夜）理解歧义 | `MoodSlotResolver` 单测锁定语义；编辑页实时换算展示 |
| R4 | DataStore JSON 与未来 Room 迁移 | 字段设计保持扁平（id/name/start/end/tags），迁移即建表插入 |
| Q1 | 时段判定是否需要考虑"用户手动改系统时间" | 一期不考虑；判定每次随心播放时即时计算，无持久状态 |
| Q2 | 多设备/备份同步 | 一期不涉及（配置在备份排除清单之外的自然延伸，随发布前核对 `backup_rules`） |
