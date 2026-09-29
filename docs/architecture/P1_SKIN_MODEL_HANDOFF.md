# P1 交付说明：骨架描述模型 + 解析校验 + 内置默认骨架

日期：2026-09-29　分支：`plugin-ui`　状态：**已完成，验证链全绿**
设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md`

---

## 一、本阶段做了什么

按 `PLUGIN_SHELL_DESIGN.md` §六 的 P1 定义交付三件事：

| P1 要求 | 交付 |
|---|---|
| 骨架描述模型 | 新增 `:core:skin` 模块：`Skin.kt`（纯数据模型）+ `SkinPartCatalog.kt`（零件白名单） |
| 解析校验 | `SkinParser.kt`（fail-closed 解析器）+ `SkinIssue.kt`（**结构化**失败原因） |
| 内置"默认骨架"（描述 = 当前 App 结构） | `DefaultSkin.kt`（JSON 字面量，走**同一条**解析链路） |

**验收标准是「描述能 1:1 表达现状」**，见下方 §三 的对照表。

---

## 二、为什么这样设计（三条关键取舍）

### 1. 内置骨架也用 JSON 字面量，不用 Kotlin 构造

`DefaultSkin.JSON` 是一段 JSON 字符串，与用户导入的皮肤走**完全同一条**解析/校验链路。

**理由**：若内置骨架用 Kotlin 直接构造 `Skin(...)`，就存在"内置路径绕过校验"的隐患——
将来零件库改了、内置描述没跟着改，问题会被静默吞掉。现在它在 `DefaultSkin.skin()` 里
一旦校验失败就 `error()`，**构建/测试期立刻炸**，而不是运行期才发现。

### 2. 失败原因是**结构化**的，不是一句话字符串

用户明确要求「安装失败要提示原因」。`SkinIssue` 带 `code` + `message` + `field` 三元组：

- `code` —— 机器可判（单测逐条断言、将来可本地化）
- `message` —— 给人看，且**必须可执行**（"描述版本 99 不受支持，当前心乐支持版本 1"而不是"版本错误"）
- `field` —— 精确定位（`pages.library.sectionsBySegment.情绪`），作者不用盲猜

并且**一次报出全部问题**，不是只报第一条——否则作者要来回改好几轮。

### 3. 严格度是分层的：结构性错误拒绝，数值越界夹紧

| 类别 | 处理 | 理由 |
|---|---|---|
| 未登记的 `part` | **拒绝** | fail-closed 的核心；静默降级会让皮肤"看起来装上了但少了东西" |
| 未知图标 / 主题 / 页面 target | **拒绝** | 都是"作者写错了"，应在导入时报出来 |
| 重复 Tab id | **拒绝** | 会破坏渲染 key（原型期实踩过：脏 tab 残留） |
| 分段有名字却没分区 | **拒绝** | 否则用户点进去是**空白页**，比报错更糟 |
| 未知 `props` 键 | 忽略 + warning | 向前兼容：新皮肤在旧宿主上仍可用 |
| 未知顶层字段 | 忽略 + warning | 同上 |
| 令牌数值越界（如圆角 9999） | 夹紧 + warning | 不致命，没必要拦 |

---

## 三、内置描述 ↔ 现状代码对照（P1 验收证据）

| 描述里的表达 | 对照的源码事实 |
|---|---|
| `bottomBar` 3 项：曲库 / 播放 / 我的 | `AppDestinations` enum 固定 3 项（`PLAYLIST`/`HOME`/`USER`） |
| `playerEntry: "tab"` + 底栏有 player 入口 | 播放页是 `AppRoutes.HOME` 的**普通 Tab**（`AppNavHost:164`） |
| `shell.miniPlayer: true` | 迷你条存在，且只在**非 HOME 页**显示（`AppScaffold:155`） |
| 曲库页 5 分段：歌单/歌手/专辑/情绪/歌曲 | 曲库页现状分段 |
| `PAGE_ME.fixedOneScreen: true` | 用户定的**单屏原则**：固定内容页一屏不滚 |
| `tokens.surface = 0` | 心乐是**零实心块**风格（与网易云"卡片墙"相对） |
| `tokens.artSizeDp = 44` | 已交付 `VisTokens.listCoverSizeDp = 44` |
| `tokens.nowPlayingRadiusDp = 20` | 已交付 `PlaybackPanelTokens.coverCornerDp = 20` |

最后两行尤其重要：**令牌默认值逐一对照已交付的尺寸令牌**，避免"纯重构"变成静默视觉回归
（这是阶段 0 踩过的坑：封面默认值曾写错成 168 而原值是 252）。

---

## 四、零件白名单：范围线画在 L5/L6 之间

`SkinPartCatalog` 共登记 **29 个零件**，按已裁定的五层组织（**L6/L7 一个都不登记**）：

| 层 | 零件 |
|---|---|
| L1 导航外壳 | `tabBar` `miniPlayer` `sheet` `rail` |
| L2 页面与分区 | `libraryTabs` `myOverview` `songList` `settings` `playStats` `emotionAnalysis` `moodTimeSlot` `fileCheck` `header` `search` `segmentChips` `sectionTitle` |
| L3 行与排布 | `playlistShelf` `playlistGrid` `quickGrid` `emotionChips` |
| L4 播放页 | `nowPlaying` `cover` `progress` `controls` `lyrics` `queue` |
| L5 资源包 | `font` `background` `iconSet` |

**扩张纪律已写进代码注释**（四条），核心判据：*若描述里出现宿主不认识的视觉概念，
说明要写代码，不是加字段*。这条能挡住 90% 的"万能配置"膨胀。

**测试反向锁死**：`SkinExpressivenessTest.catalog itself contains no L6 or L7 entries`
断言零件库的层集合**恰好等于** L1–L5——将来有人往库里塞表现层零件会被测试拦下。

---

## 五、验证结果（实测，非声称）

| 项 | 结果 |
|---|---|
| `:core:skin:testDebugUnitTest` | **37 tests, 0 failures, 0 errors** |
| `:domain` / `:data` / `:feature:player` / `:app` 单测 | 全绿（全量合计 390 tests, 0 failures） |
| `verifyProductArchitecture` | **通过**（`:core:skin` 已登记进 requiredModules 与 settings） |
| `spotlessApply` | 通过，未产生额外重排改动 |
| `:app:assembleDebug` | **BUILD SUCCESSFUL**，`app-debug.apk` 时间戳为本次构建 |
| APK 版本核对 | `versionCode=35 versionName=3.7.2-debug`（`aapt2 dump badging`） |

### 测试构成（37 条）

| 测试类 | 条数 | 在证明什么 |
|---|---|---|
| `DefaultSkinTest` | 9 | **内置描述 = 现状**（P1 验收标准）：3 Tab、播放是 Tab 不是抽屉、曲库 5 分段、我的页一屏不滚、`surface=0`、封面 44dp |
| `SkinParserValidationTest` | 23 | **每种坏输入都给出精确原因**：非 JSON / 版本不符 / 未知零件 / 未知图标 / 重复 id / 底栏项数越界 / 分段缺分区 / 未知主题 / 无效行模板 / 无效数据源 / 负 count…… 且断言 `field` 定位非空 |
| `SkinExpressivenessTest` | 5 | **契约能表达"另一个 App"**：网易云式（卡片墙+黑胶）与极简双页（2 Tab + 播放抽屉）都能通过校验；同一个 `songList` 换 `template` 即改变形态；三套骨架均未用到 L6 零件 |

`SkinExpressivenessTest` 是这轮最有价值的一条：它把"契约是否够用"从**口头讨论**变成
**可执行断言**——若将来契约退化到只能表达现状，这些测试会失败。

---

## 六、本阶段**没有**做的事（明确边界）

- **没有动任何 UI/导航代码**。`AppDestinations` / `AppScaffold` / `AppNavHost` 一行未改，
  `:core:skin` 目前**没有任何生产调用方**（只有测试引用）。这是刻意的：
  P1 只交付"描述能被表达与校验"，接线是 P2。
- **没有做导入 UI**（设置页的选文件/报错展示）——属于 P5。
- **没有做资源包**（字体/背景/图标实际生效）——属于 L5，P5 落地。
- **没有做渲染器**（`part` → Compose 的映射）——属于 P2/P4。

因此本阶段是**零视觉变化、零行为变化**的纯增量：`assembleDebug` 产出的 App 与改动前
行为一致，只是多了一个未被调用的库模块。

---

## 七、下一步（P2）

P2 = 导航层改造：`AppDestinations` enum → 运行期 Tab 列表；`AppScaffold` 按描述渲染。

**P2 的验收标准（不可妥协）**：用 `DefaultSkin` 驱动的 App 与现状**逐页无差异**。
这是后续一切的地基——必须真的逐页比对（截图 + DOM/布局尺寸），不能口头声称。

建议 P2 起手先做一件事：**给 `DefaultSkinTest` 补一条"描述覆盖了现状所有页面/路由"的断言**，
把 `AppRoutes` 里的路由清单与描述做交叉校验，避免接线时漏页。
