# P2 交付说明：导航层改造（外壳由数据驱动）

日期：2026-09-29　分支：`plugin-ui`　状态：**已完成，真机验收通过**
设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md`
前置：`P1_SKIN_MODEL_HANDOFF.md`（描述模型与校验）

---

## 一、本阶段做了什么

P2 的目标是 **`AppDestinations` enum → 运行期 Tab 列表；`AppScaffold` 按描述渲染**。

| 改动 | 前 | 后 |
|---|---|---|
| 底栏来源 | `AppDestinations.entries`（编译期 3 项） | `AppShell.tabs`（运行期列表，2~5 项） |
| 启动页 | `NavHost(startDestination = AppRoutes.HOME)` 写死 | `shell.startRoute` |
| 迷你条条件 | `currentDestination != HOME && currentSong != null` | `shouldShowMiniPlayer(shell, activeRoute, hasCurrentSong)` |
| 底栏高亮 | `var currentDestination` 本地状态 + `LaunchedEffect` 同步 | **派生**自 `activeRoute`（无需同步，故不会陈旧） |
| 转场序号 / 横滑 | `AppDestinations.fromRoute(...).ordinal` | `tabs.indexOfRoute(route)` |
| 子页归属 | `AppDestinations.fromRoute` | `RouteAffinity.owningTopLevelRoute` |
| `AppDestinations.kt` | 41 行 enum | **已删除** |

新增（`app/src/main/java/cn/com/dcsgo/mihx/app/shell/`）：

- `AppShell.kt` —— 运行期外壳模型 + `RouteAffinity` + `shouldShowMiniPlayer`（**纯函数，可 JVM 单测**）
- `SkinShellResolver.kt` —— 描述符号 → 宿主具体物（页面 key → 路由、图标名 → drawable），
  以及 `DefaultShell`（改造前行为的 Kotlin 常量版）

---

## 二、★ 真机验收（P2 的硬标准）

设计文档要求「用内置描述驱动的 App 与现状**逐页无差异**」。已用模拟器（tfl_smoke / API 36 / x86_64）实测：

| 验收项 | 结果 |
|---|---|
| 安装启动 | ✅ `adb install` 成功，`MainActivity` 正常 Displayed（+10s，含首次 dexopt） |
| 崩溃 | ✅ **零崩溃**（`logcat -b crash` 全空，进程稳定存活） |
| 落页 | ✅ **落在「播放」页**（nowPlaying：专辑封面/进度条/播放控制），与改造前一致 |
| 底栏 | ✅ **曲库 / 播放 / 我的** 三项，顺序一致，**播放**高亮加粗 |
| 切 Tab | ✅ 点「曲库」正确切换到曲库页（歌单/歌手/专辑/情绪 分段 + 搜索框） |
| 底部高亮跟随 | ✅ 切到曲库后 **曲库** 变粗，播放页时 **播放** 变粗 |
| 迷你播条 | ✅ 曲库页**显示**（出现在底栏上方，含封面/歌名/歌手/播放按钮/进度线）；播放页**不显示** |
| 播放能力 | ✅ MediaSession 正常建立、曲目可播放（`playbackState=PAUSED`，position 推进） |

**结论：逐页行为与改造前一致，且导航层现在完全由数据驱动。**

### 验收过程中抓到的真实问题（P2 的价值所在）

**P1 的 `startPage` 写错了。** P1 初版把内置骨架的 `startPage` 写成 `"library"`，
但改造前的 `AppNavHost:132` 是 `startDestination = AppRoutes.HOME`——**App 实际落在播放页**。

- 这是"与现状逐页无差异"标准**抓出来的保真 bug**，纯看代码不容易发现；
- 已修正为 `PAGE_PLAYER`，并补测试 `startPage is the player page because the app launches on it` 防复发；
- 另加 `DefaultShellConsistencyTest` 断言**两条路径（Kotlin 常量 / 描述解析）结论一致**——
  否则 P5 上线用户皮肤后行为会与现在不同。

---

## 三、为什么这样设计（关键取舍）

### 1. 底栏高亮改为「派生」而非「同步」

改造前用一个本地状态 + `LaunchedEffect(activeRoute)` 去"修正"高亮，注释里写着
"避免从子页面滑动离开再回来后 currentDestination 停留在旧值"——这是**两处真相**的典型症状。

现在高亮 = `tabs.indexOfRoute(activeRoute)`，**没有副本可陈旧**，该失效模式从根上消失。

### 2. 迷你条必须按**路由**判定，不能按**所属 Tab** 判定

改造前比较"所属 Tab != HOME"。这在默认骨架下等价，但**抽屉型骨架会坏**：
播放页不在底栏 → `indexOfRoute` 找不到 → 兜底到 home → 于是**在其他页面**被误判为
"在播放页"，把迷你条错误隐藏。

所以 `shouldShowMiniPlayer` 比较**路由**。这条差异有专门测试
（`sheet skin hides mini player on player route even if player is not a tab`）。

### 3. 纯策略函数放在 `shell` 包，不放 Compose 文件

`shouldShowMiniPlayer` / `RouteAffinity` / `indexOfRoute` 都是**纯逻辑**，
放在 `app/shell/AppShell.kt` 而不是 `AppScaffold.kt`——这样能直接在 JVM 上单测，
不必为了测一条 if 去起 Compose。（P2 期间踩到过反例：函数留在 Compose 文件里导致
`app` 单测 `Unresolved reference`。）

### 4. 启动路径 fail-safe，导入路径 fail-closed

- **启动**：`DefaultShell.shell` 是编译期常量，不解析 JSON——启动路径上不该有解析失败的可能。
- **导入**（P5）：描述坏了要**明确拒绝并说明原因**（这是用户要求）。
- 二者由 `DefaultShellConsistencyTest` 绑死，保证不会各自漂移。
- `SkinShellResolver.resolveFromJson` 解析失败时**回落内置外壳**而非崩溃
  （描述是用户数据，不能让它把 App 弄起不来）。

---

## 四、验证结果（实测）

| 项 | 结果 |
|---|---|
| 全量单测 | **420 tests, 0 failures, 0 errors**（P1 时 395，新增 25） |
| `verifyProductArchitecture` | 通过 |
| `spotlessApply` | 通过 |
| `:app:assembleDebug` | **BUILD SUCCESSFUL**，APK 重新打包 |
| APK 内容核对 | `core/skin/SkinParser`→classes7、`app/shell/AppShell`+`DefaultShell`→classes21、`app/shell/SkinShellResolver`+`RouteAffinity`→classes19 |
| 真机 | 见 §二 |

### 新增测试构成（25 条）

| 测试类 | 条数 | 在证明什么 |
|---|---|---|
| `AppShellEquivalenceTest` | 15 | **与改造前行为逐条等价**：3 Tab 顺序、启动页、迷你条三种场景、子页归属三分支（我的 9 个 / 曲库 7 个 / 兜底）、Tab 序号 = 原 enum ordinal、同 Tab 子页序号相同（无转场）、横滑左右边界不越界 |
| `DefaultShellConsistencyTest` | 9 | **两条路径同结论**（Kotlin 常量 == 描述解析）；启动页是播放页（防 P1 保真 bug 复发）；描述非法时回落内置外壳；图标名都能解析出资源 |

`AppShellEquivalenceTest` 的每条断言都注明它对照的**改造前源码位置**，
将来有人改坏能一眼看出破坏的是哪条既有行为。

---

## 五、本阶段**没有**做的事

- **没有做播放抽屉（`sheet` 容器）**。`PlayerEntry.SHEET` 已能表达、`shouldShowMiniPlayer`
  已按抽屉语义正确工作，但**抽屉容器本身属于 P3**。当前骨架恒为 `TAB`，
  与改造前完全一致。
- **没有做页面内容装配**（`skin.json` 的 `sections` / `part` → Compose）。这属于 P4。
  当前 `pages` 只用于解析页面 key → 路由的映射。
- **没有做用户皮肤导入/切换 UI**（P5）。`AppRoot` 里外壳是
  `val shell: AppShell = DefaultShell.shell`，把这一行换成"用户选中的皮肤"即可切换整套骨架。
- **没有做 L5 资源包**（字体/背景/图标实际生效），属于 P5。
- **没有改任何页面内部**：曲库页/我的页/播放页的 Composable 一行未动。

---

## 六、下一步（P3）

P3 = 新增 `sheet` 容器 + 迷你条唤起，让播放页可以作为全局抽屉。

**已核实的现状要点**：
- 播放页现在是 `AppRoutes.HOME` 的普通 Tab（`AppNavHost:164`）；
- 迷你条只在非播放页显示（现由 `shouldShowMiniPlayer` 承载）；
- 新骨架下 `HOME` 从 Tab 列表移除，迷你条改为"只要有当前歌曲就常驻"；
- `ModalBottomSheet` 仓库内已有现成用法（`feature/player/.../PlayQueueSheet.kt:120`），
  直接复用，**不必新造容器**。

**⚠️ 必须带上的已知坑**（来自方案D 的教训）：
- M3 sheet 的 BACK 处理发生在**独立 dialog window**，主 window 的 `BackHandler` 收不到
  → 用 `sheetState.confirmValueChange` 拦截；
- 新增 sheet 回调参数若漏接线会拿到**空 lambda 默认值**
  → 模态窗口残留拦截触摸、页面看似无响应（必须模拟器复现 + logcat 定位）。

**P3 验收建议**：抽屉型骨架与默认骨架都要跑真机，
且**抽屉打开时迷你条与底栏的行为要逐项比对**——这正是 P2 里
`shouldShowMiniPlayer` 按路由判定所保护的那部分。
