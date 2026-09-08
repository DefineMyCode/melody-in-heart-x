# 方案D「此刻」落地评估

> 评估对象：4 页 HTML 原型（library-redesign-d-moment / extend-d-nowplaying-lyrics-queue / extend-d-mypage / extend-d-settings）
> 基线：心乐 v3.7.1 (34)，feature/* 16,638 行 Kotlin，Compose + Media3 + Room + Hilt
> 日期：2026-09-08

---

## 一、总体判断

**可行，但这是一次"导航骨架级"重构，不是换肤。** 改动核心是把 App 从
「Tab 页框架（曲库为主屏）」翻转为「播放为主屏 + 曲库降级为 BottomSheet 抽屉」。
预估总量 **3-5 个工作日**（纯 UI 层，domain/data 几乎不动），分 4 个独立可合并的
阶段，每阶段出一个可用 debug 包。

风险等级：中。不需要 Room 迁移、不需要动播放内核（PlaybackController/
PlayerRuntime 零改动），但 `AppNavHost` 的 17 个 composable 路由表要重排。

---

## 二、组件级清单：复用 / 改造 / 新做

### ✅ 直接复用（约占 60%，不需要动）

| 组件 | 位置 | 对应设计 |
|---|---|---|
| ThemePalette 双主题体系 | core/ui/theme (Mono/Vermilion × Light/Dark) | 设置页主题选择、全站配色 |
| SongRow/歌曲列表行 | PlaylistComponents.kt | 抽屉内最近添加、队列行 |
| PlayQueueSheet | feature/player/PlayQueueSheet.kt | 方案D队列页（已有 ModalBottomSheet 实体！）|
| LyricsScreen/Route | feature/lyrics | 播放详情页歌词区（已有逐行高亮）|
| EmotionTags 词条组件 | core/ui | 情绪转盘、队列行内词条、情绪构成 |
| 播放统计 8 查询 + 缓存 | PlayStatsRepository + PlayerViewModel（6e947b7 缓存）| 我的页 186小时/Top榜/情绪构成数据源 |
| FileCheckMode.QUICK/DEEP | domain + FileCheckScreen | 设置页文件校验分段选择 |
| 情境化随心播放配置 | user feature（v3.6.0）| 主屏"此刻适合"时段×情绪 |

### 🔧 改造（约占 25%）

| 组件 | 现状 → 目标 | 工作量 |
|---|---|---|
| AppNavHost | 17 个路由的 Tab 框架 → 播放主屏 + 4 个二级路由；`precomputedLibrarySongs` 共享逻辑平移进抽屉 | 0.5 天，风险点：PlayRoute 状态保持 |
| PlaylistScreen → LibraryDrawer | 曲库页（TabRow 4 Tab + 全列表）→ ModalBottomSheet 内嵌套 NavHost 或分段控件；单曲点击从"跳播放页"改为"切歌+收回抽屉" | 1 天，最大单项 |
| 我的页 | 现有统计卡片 → Hero 数字+热力条+Top3+情绪堆叠条；数据全有，纯重排 | 0.5 天 |
| 设置页 | 现有 settings feature → 分组清单+主题行内选择+双滑条；FileCheckMode 控件从页面改行内段控 | 0.5 天 |
| 播放详情 | LyricsScreen 已有 → 加"来自歌单"上下文徽章（需要 PlaybackSession 透传来源，data 层小改）+ 页面指示点 | 0.5 天 |

### 🆕 新做（约占 15%，集中风险区）

| 组件 | 说明 | 工作量 | 降级方案 |
|---|---|---|---|
| 旋转黑胶封面 | `rememberInfiniteTransition` 旋转 + 播放状态机驱动 start/stop；真实封面裁圆 + Mixin 高光弧 | 0.5 天 | 静态圆封面+旋转 CD 纹理贴图，不动动画 |
| 氛围背景 | 封面主色提取（Palette API 已有 AlbumArtExtractor 可扩展）→ radial-gradient 模糊层，`:player` 内自绘 | 0.5 天 | 纯静态深底渐变（去掉动画）|
| 情绪转盘（dial）| 横滑胶囊选择器：LazyRow + snap + 中位放大；选词=标记接口复用 EmotionViewModel.overrideEmotion | 1 天 | 退化为普通横滑 chips（无中位放大），0.5 天 |
| 情境问候 | "晚上好·今晚已听14首"：时段判断 + 今日播放次数（playback_stats 已有今日查询）| 0.25 天 | — |
| 拖拽抽屉手势 | ModalBottomSheet 自带半展开态（Media3 都在用），配置 partialExpand 即可，无需自写手势 | 0.25 天 | — |

---

## 三、分阶段交付（每阶段独立可用、可验收）

**阶段 1：主屏翻转（1-1.5 天）** — 风险最高先行
- AppNavHost 改造：PlayScreen 升为 HOME 路由，PlaylistScreen 包进抽屉
- 黑胶 + 氛围背景 + 情境问候
- 验收：切歌/暂停时唱片停转；进程重建后主屏状态正确；1103 首抽屉展开不掉帧（复用 flatGroupedSongs 共享缓存）

**阶段 2：播放详情 + 队列（0.5-1 天）**
- 歌词页加上下文徽章 + 页面指示点；PlayQueueSheet 样式对齐设计
- 验收：歌词滚动不与 200ms tick 冲突；队列显示情绪词条

**阶段 3：我的页改版（0.5 天）**
- 验收：进页零阻塞（已有快照缓存）；单屏无滚动

**阶段 4：设置页改版（0.5 天）**
- 验收：主题切换即时生效；FileCheckMode 双模式功能不回归

每阶段结束出一个 debug 包（命名 `melody-heart-debug-uiRedesign-pN.apk`），
按老规矩"改动先出 debug 试用再提交"。

---

## 四、边界与退化分析（按你的要求前置）

1. **低配机性能**：氛围层 blur + 唱片旋转 + 歌词高亮三层叠加，Redmi/低端机可能掉帧。
   对策：`LocalAccessibilityManager` 检测或提供"减少动效"开关（设置页加一行），
   关闭后全部退化为静态。
2. **抽屉内列表长度**：1103 首全列表在抽屉里保持 LazyColumn + 已有共享分组缓存，
   展开动画期间避免重组（内容用 `movableContentOf` 或延迟到 settle 后加载）。
3. **播放主屏无返回键语义**：BACK 键在主屏 = 打开/关闭抽屉，而不是退出 App；
   需要 BackHandler 明确语义，否则用户误触直接退出。
4. **歌词来源缺失**：无歌词文件时详情页退化为"封面+信息+队列"混合态，
   不能留白。
5. **widget/通知栏入口**：外部跳转进入时直达主屏，跳过抽屉——与现有
   deep link 行为对齐检查。
6. **平板/横屏**：方案按手机竖屏设计；横屏先锁 portrait（现状即如此），
   后续版本再适配。

## 五、防滥用/合规

- 情绪转盘选词调用现有 overrideEmotion，标记持久化语义不变，无新权限
- 文件校验/情绪分析开关只是现有 WorkManager 配置的 UI 重排，不改调度语义
- 统计数据全部本地 Room，无网络新增

## 六、结论

- **建议做**，按阶段 1→4 推进；阶段 1 完成即出包验收，方向不对可止损（阶段 1 的
  AppNavHost 改动可整体 revert，其余阶段都建立在它之上）
- 预估合计 3-5 天纯 UI + 1 天联调缓冲；不含可能发现的隐藏耦合
- 最大技术风险：AppNavHost 路由重排引发的状态丢失回归（播放中进程重建、
  抽屉内选中态）——阶段 1 验收时重点测
