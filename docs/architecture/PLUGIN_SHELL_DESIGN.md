# 插件重构应用界面（应用骨架契约）设计方案

日期：2026-09-29　状态：设计待评审
关联：`PLUGIN_SYSTEM_DESIGN.md`（T2 代码插件）、`PLUGIN_SYSTEM_SPIKE_REPORT.md`（实测）

---

## 一、需求

用户 2026-09-29 提出：「可调节的参数比较少，我想做到的是插件能够完全重构应用的界面。例如：只保留两个页面曲库和我的，播放页弄成一个全局抽屉可通过迷你播放条调出来。我希望插件能做到这种程度。」

**这个需求与阶段 0 不是一个量级。** 阶段 0 改的是"某个像素值"，这里要改的是**应用的一级结构**：

| 维度 | 阶段 0（令牌） | 本次需求（骨架） |
|---|---|---|
| 改什么 | 某个 dp / 颜色 | 有几个 Tab、每个 Tab 挂什么页、播放页以什么形态存在、如何被唤起 |
| 谁的职责 | 宿主页面内部 | 宿主导航层（`AppNavHost` / `AppScaffold` / `AppDestinations`） |
| 能否靠调参实现 | 能 | **不能**——底栏项目数是编译期常量 |

---

## 二、三条可选路线（必须先拍板）

### 路线 A：纯参数令牌（已做，阶段 0）

只能调整尺寸/颜色。**明确做不到本需求**，列在这里只为划清边界。

### 路线 B：结构声明 —— 宿主提供零件库，插件用声明式描述重新装配 ★推荐

插件不写 Compose 代码，而是交出一份**应用骨架描述（JSON）**，声明：
- 底部有几个 Tab、各自的标签/图标/指向哪个页面
- 播放页以什么形态存在（全局抽屉 / 全屏页 / Tab / 常驻悬浮）
- 迷你播放条的位置与点击行为
- 每个页面的内容类型与参数

宿主按描述**装配**现有页面。它本质是**引用 + 组合**，不是生成新 UI。

**为什么这条路最适合这个需求：**

| 优势 | 说明 |
|---|---|
| 零崩溃风险 | 组合期没有插件代码，spike 发现的"组合期异常直接崩进程"问题不存在 |
| **改完即时生效** | 不编译、不装 APK——这一点连阶段 0 都做不到（阶段 0 还是要构建） |
| 可单文件分享 | 一个 JSON 文件发群里就能装，比发 APK 轻得多 |
| 无编译器版本耦合 | 没有 dex，不需要 `compilerVersion` 闸门 |
| 天然安全 | 插件无法执行任意代码，只能引用宿主白名单内的零件 |

**能力上限（必须诚实说明）＝ 宿主零件库的丰富度。** 插件能重新排列组合宿主的页面与组件，但**造不出宿主没有的新组件**。想要新组件，仍需要改宿主代码（或走路线 C）。

### 路线 C：代码插件（Compose 写在插件 dex 里）

spike 已证明可行，但代价明确：
- 组合期异常**无法隔离**，用户 App 崩一次（只能靠重启自愈）
- Kotlin/Compose 升级后插件集体失效
- 插件必须用 Android Studio + JDK 编写，普通人写不了
- 调试体验差（无断点/热重载）

**它对"新组件"是必需的，对"重新装配"则是杀鸡用牛刀。**

### 建议组合

**B 做主干（覆盖 90% 的"换界面"愿望），C 保留为扩展**（当确实需要宿主没有的新组件时才用）。

---

## 三、路线 B 的契约草案

### 3.1 骨架描述文件 `shell.json`

```json
{
  "schemaVersion": 1,
  "id": "dcsgo.shell.minimal",
  "name": "极简双页 · 播放抽屉",
  "theme": { "variant": "VERMILION", "tokens": "builtin:VermilionNight" },

  "shell": {
    "bottomBar": {
      "type": "tabBar",
      "items": [
        { "id": "library", "label": "曲库", "icon": "queue_music", "target": "libraryPage" },
        { "id": "mine",    "label": "我的", "icon": "person",      "target": "myPage" }
      ]
    },
    "miniPlayer": {
      "visible": true,
      "position": "aboveBottomBar",
      "tap": { "action": "openSheet", "sheet": "playerSheet" }
    }
  },

  "pages": {
    "libraryPage": {
      "type": "libraryTabs",
      "tabs": ["playlists", "artists", "albums", "emotions"],
      "rowTemplate": "default"
    },
    "myPage": {
      "type": "myOverview",
      "sections": ["playStats", "moodSlot", "emotionScan", "fileCheck"]
    },
    "playerSheet": {
      "type": "sheet",
      "peekHeightDp": 72,
      "heightPercent": 88,
      "content": { "type": "nowPlaying", "showLyrics": true, "showQueue": true }
    }
  },

  "startPage": "libraryPage"
}
```

**这份文件完整表达用户举的例子**：两个 Tab（曲库/我的）+ 播放页作为全局抽屉 + 迷你条唤起。

### 3.2 宿主零件库（可引用的 `type` 白名单）

骨架描述里的每个 `type` 都必须是宿主已实现并登记的类型。首期白名单：

**页面级**
- `libraryTabs` — 曲库页（歌单/歌手/专辑/情绪 分页）
- `myOverview` — 我的页（分区可裁剪、可排序）
- `settings` / `playStats` / `emotionAnalysis` / `moodTimeSlot` / `fileCheck` — 二级页
- `songList` — 歌曲列表（数据源：`all` / `playlist:<id>` / `artist:<name>` / `album:<name>`）

**容器级**
- `tabBar` — 底部导航（2~4 项）
- `sheet` — 全局抽屉（peek 高度 + 展开高度）
- `miniPlayer` — 迷你播放条
- `rail` — 大屏侧边导航

**组件级**
- `nowPlaying` — 播放页主体（封面/歌词/队列 可开关）
- `emotionWheel` / `header` / `divider` / `spacer`

未登记的类型一律拒绝加载并给出明确提示（fail-closed）。

### 3.3 插件方视图

- **路线 B 的"插件"= 一个 JSON 文件**（可打进 zip 附图标/资源）
- 作者只需了解零件名字与参数，不需要 JDK / Android Studio
- 分发：单文件、离线、可分享
- 改完在设置里重新导入即生效

---

## 四、必须改造的现有代码（这是真工作量）

现状是"一级结构写死在代码里"，要让它可被描述驱动：

| 现有位置 | 现状 | 改造 |
|---|---|---|
| `AppDestinations`（enum，3 项固定） | 编译期常量 | 改为运行期列表：`List<ShellTab>`，来自骨架描述 |
| `AppRoutes`（路由常量） | 编译期常量 | 保留为内置默认骨架的路由名；插件页面经 `ShellPageRegistry` 解析 |
| `AppScaffold`（底栏 + 迷你条） | 硬编码遍历 `AppDestinations.entries` | 按骨架描述渲染 tab 项；迷你条行为由描述决定 |
| `AppNavHost`（约 900+ 行，路由硬编码） | `composable(route)` 逐条注册 | 引入 `ShellPageRegistry`：`type` → 宿主 Composable 的映射表，NavHost 按骨架注册路由 |
| `PlayerViewModel` / `PlayerUiState` | 全局单例（Activity 级） | **无需改动**——播放状态本就全局，抽屉与 Tab 共用同一份状态 |
| 播放页形态 | 现在是 `HOME` 路由（普通 Tab） | 新增 `sheet` 容器承载同一份 `nowPlaying` 内容 |
| 回收站/返回栈 | `<b>`返回语义与 Tab 绑定 | 抽屉打开时 BACK 先关抽屉（`BackHandler`），已在队列 sheet 有先例可照搬 |

**关键点：页面内容本身几乎不用改**（曲库页/我的页/播放页的 Composable 都还在），改的是**外壳与导航层**。这让改造风险主要集中在导航层，而不是全应用。

---

## 五、分阶段实施

| 阶段 | 内容 | 交付 | 预估 |
|---|---|---|---|
| **P1** | 骨架描述模型 + 解析校验 + 内置"默认骨架"（描述 = 当前 App 结构，视觉零变化） | 描述能 1:1 表达现状 | 1~2 天 |
| **P2** | 导航层改造：`AppDestinations` → 运行期 Tab 列表；`AppScaffold` 按描述渲染 | 用内置描述驱动的 App，行为与现在一致 | 2~3 天 |
| **P3** | 新增 `sheet` 容器 + 迷你条唤起 | 播放页可作为全局抽屉（即用户的例子） | 2 天 |
| **P4** | 零件库扩充 + 骨架文件导入/切换/导出 + 设置页 UI | 可导入分享的骨架文件 | 2~3 天 |

**每阶段独立可验收**：P1+P2 完成后必须证明"用描述驱动的 App 与现在完全一样"，这是后续一切的地基。

版本：新功能 → **v3.8.0**（b+1）。

---

## 六、风险登记

| 风险 | 影响 | 对策 |
|---|---|---|
| 导航层改造面大（`AppNavHost` 900+ 行） | 引入回归 | P1/P2 的验收标准 = 与现状逐页比对无差异（截图 + DOM 尺寸） |
| 零件库设计过窄 | 表达力不足，用户仍不满意 | 先按用户例子（双 Tab + 播放抽屉）反推最小零件集，跑通再扩 |
| 描述文件版本演进 | 旧骨架在新宿主失效 | `schemaVersion` 闸门 + 未知字段忽略 + 未知 `type` 明确报错 |
| "看起来没差别"风险（方案D 前车之鉴） | 白做 | **先用可交互 HTML 原型让用户确认骨架方向，再动代码**（见 `mihx-plugin-shell` 三变体） |
| 零件库沦为"什么都能配"的万能配置 | 复杂度爆炸、难维护 | 只登记真实被用到的零件；每个零件必须对应一个已存在页面/组件 |

---

## 七、与既有文档的关系

- 本文件（路线 B）= **主干**，解决"重建应用骨架"
- `PLUGIN_SYSTEM_DESIGN.md`（路线 C）= **扩展**，解决"宿主没有的新组件"
- 阶段 0 已交付的令牌层 = 两条路线共用的底座（骨架描述里的 `theme.tokens` 直接引用它）

**下一步**：原型方向确认 → P1 骨架描述模型。
