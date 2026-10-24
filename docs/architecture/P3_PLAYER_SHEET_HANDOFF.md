# P3 交付说明：全局播放抽屉（`sheet` 容器）

日期：2026-09-29　分支：`plugin-ui`　状态：**代码完成，验证链全绿**（真机验收见 §四）
设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md`
前置：`P1_SKIN_MODEL_HANDOFF.md`、`P2_NAV_SHELL_HANDOFF.md`

---

## 一、本阶段做了什么

P3 的目标是让**播放页可以作为全局抽屉**（`playerEntry = "sheet"`），
即用户举的例子：「只保留两个页面曲库和我的，播放页弄成一个全局抽屉可通过迷你播放条调出来」。

| 交付 | 文件 |
|---|---|
| 播放页内容抽成可复用组件 | `app/.../player/NowPlayingSurface.kt` |
| 抽屉容器（复用 M3 `ModalBottomSheet`） | `app/.../player/PlayerSheetHost.kt` |
| 抽屉型骨架样本（2 Tab + 抽屉） | `core/skin/.../DefaultSkin.kt` 的 `MINIMAL_SHEET_JSON` |
| 骨架切换（仅 debug，验收开关） | `app/src/debug/.../tuning/AppTuning.kt` + `UiTuningPanel.kt` |

---

## 二、关键设计决定

### 1. 播放页内容**抽出来共用**，而不是复制一份

原先这段内容（`HomeRoute` + 三个对话框）内联在 `AppNavHost` 的 `composable(AppRoutes.HOME)` 里，
共约 145 行。抽屉形态如果另写一份，两份实现必然漂移——播放页是改动最频繁的页面之一。

现在抽成 `NowPlayingSurface`，**Tab 与抽屉共用同一份**：
- Tab 形态：`AppNavHost` 的 `composable(HOME)` 里调用它；
- 抽屉形态：`AppRoot` 的 `PlayerSheetHost` 内容里调用它。

抽取原则是**逐字搬运、行为不变**：三个对话框（歌曲详情/加入歌单/删除确认）一起搬，
因为它们是播放页"更多"菜单的一部分，抽屉形态同样需要。

### 2. `onDismiss` 是**必填参数**（无默认值）——从类型层面杜绝漏接线

方案D 的教训：M3 sheet 的回调漏接线时会拿到**空 lambda 默认值**，
后果是**模态窗口残留拦截触摸、页面看似无响应**（且不报错，只能靠模拟器复现 + logcat 定位）。

`PlayerSheetHost` 的 `onDismiss` 刻意**不给默认值**，漏传直接编译不过。
这比"记得接线"可靠。

### 3. 迷你条的三个隐藏条件，语义各不相同

| 条件 | 含义 | 由谁判定 |
|---|---|---|
| 有当前歌曲 | 没歌就没有可播的 | `hasCurrentSong` |
| 骨架使用迷你条 | 结构属性（描述里 `shell.miniPlayer`） | `shouldShowMiniPlayer` |
| 当前**不是**播放页 | 避免与播放页自身重复 | `shouldShowMiniPlayer`（按**路由**判定） |
| **抽屉已打开** | 避免两处控制 + 底层条目被遮挡 | `miniPlayerHiddenBySheet` 参数 |

最后一条刻意做成**参数**而非塞进 `AppShell`：它表达的是**瞬时 UI 状态**，
不是骨架的结构属性。混进去会让骨架变成可变对象。

### 4. `ModalBottomSheet` 的关闭路径：**不要覆写 `confirmValueChange`**

M3 的 sheet 渲染在**独立 dialog window**，其返回处理由 sheet 自身消费后再回调
`onDismissRequest`。**真机验收抓到一个我自己引入的 bug**：

本组件初版写成 `confirmValueChange = { it != SheetValue.Hidden }`，注释写着"避免僵尸态"，
实际后果是**下滑手势关闭被彻底禁用**：

- 系统返回键：走 `onDismissRequest` → **仍能关闭** ← 所以粗测/单测发现不了；
- 下滑关闭：需要 sheet 状态转到 `Hidden` → **被拦下，静默失效**。

真机症状：抽屉只能靠返回键关，"从顶部把手下滑"完全无反应。
**正解 = 不覆写 `confirmValueChange`**，用默认值（允许 `Hidden`）；关闭意图一律由
`onDismissRequest` 通知上层，上层把 `isShown` 置 false，`if (!isShown) return` 卸载整个 sheet。
状态只有**一个真相**，因此也不存在僵尸态。

**教训**：这类"只坏了一条交互路径、另一条还正常"的 bug，**必须真机逐条路径验**。
我第一版测试只验了返回键就以为通过，差点漏掉。

### 5. 抽屉型骨架的 `startPage` 必须改

播放页移出底栏后，若仍从播放页启动，**底栏没有任何项可高亮**。
所以 `MINIMAL_SHEET_JSON` 的 `startPage` 是曲库页。这是骨架自身的自洽性问题，
校验器管不到（它只知道 page 存在），属于"描述作者需要理解"的约束。

### 6. 切换骨架需要重建导航图 → 用 SharedPreferences 持久化

切换骨架意味着 `NavHost` 的 `startDestination` 变了，进程内状态会丢，
所以 debug 的骨架选择存盘（`skin_debug` prefs），重启后仍生效。

---

## 三、验证结果（测试）

| 项 | 结果 |
|---|---|
| `:core:skin` + `:app` 单测 | 全绿（新增 `SheetSkeletonTest` 9 条） |
| 新增测试要点 | 2 Tab / 无播放 Tab / 迷你条常驻 / 起点为曲库页 / **所有路由上迷你条都可见**（抽屉才拉得起来）/ 两套骨架互不影响 / 默认骨架行为未被改变 |
| `verifyProductArchitecture` | 通过 |
| `:app:assembleDebug` / `:app:compileReleaseKotlin` | 通过（release 侧 stub 同步更新） |

`SheetSkeletonTest` 里最关键的两条：

1. **`sheet skeleton never accidentally hides the mini player`** —— 逐条断言曲库/我的/设置/
   歌手详情/专辑详情/播放统计上迷你条都可见。这正是 P2 那条"按**路由**而非按**所属 Tab**判定"
   所保护的场景：若按所属 Tab 判定，这些路由都会兜底到 home 而被误判为"在播放页"，
   于是**抽屉再也拉不起来**。
2. **`default skeleton still behaves as before when sheet feature exists`** ——
   新增抽屉能力**不得**改变默认骨架行为（回归保护）。

---

## 四、真机验收（已通过）

模拟器（tfl_smoke / API 36 / x86_64）实测，debug 包 + 骨架切到「极简双页 · 播放抽屉」：

| 验收项 | 结果 |
|---|---|
| 底栏只剩 **曲库/我的**（无播放 Tab） | ✅ |
| 迷你条常驻（作为抽屉把手） | ✅ |
| 点迷你条**拉起播放抽屉** | ✅ 全屏播放内容（封面/歌名/进度/控制/一键歌词/随心播放） |
| 抽屉打开时**迷你条隐藏** | ✅（避免两处控制 + 遮挡） |
| **系统返回键关闭抽屉** | ✅ |
| **从顶部把手下滑关闭** | ✅（**第一版失败，已修**，见 §二·4） |
| 关闭后迷你条恢复、曲库页正常 | ✅ |
| 崩溃 / 卡死 / 触摸残留 | ✅ 零崩溃，`logcat -b crash` 全空 |

### ★ 本次验收抓到的真实 bug（只坏一条交互路径）

抽屉初版**只能用返回键关，下滑手势完全无效**。根因是我自己覆写了
`confirmValueChange` 去拦截 `Hidden`。返回键走的是 `onDismissRequest`（另一条路径），
所以单测和粗测都发现不了——**必须真机把每条关闭路径都试一遍**。

### 验收用的骨架切换方式

debug 面板「骨架（皮肤）」分组（我的页长按「心有乐章」版本行打开），
或用 adb 预置（等价）：

```bash
adb shell "run-as cn.com.dcsgo.mihx.debug sh -c 'printf \"<?xml version=\\x271.0\\x27 encoding=\\x27utf-8\\x27 standalone=\\x27yes\\x27 ?>\\n<map>\\n<string name=\\\"skin_id\\\">dcsgo.skin.minimal</string>\\n</map>\\n\" > /data/data/cn.com.dcsgo.mihx.debug/shared_prefs/skin_debug.xml'"
```

**注意**：`run-as` 的相对路径会落到 `/data/user/0/<pkg>`，写 shared_prefs 必须用
**绝对路径** `/data/data/<pkg>/shared_prefs/...`（实测相对路径报 `No such file or directory`）。

### 无头模拟器的已知干扰（不是 App 问题）

- 会弹 **"System UI isn't responding"** 系统 ANR 弹窗（无头 + 软件 GPU 导致 SystemUI 卡顿），
  挡住 uiautomator dump 与截图。处理：`adb shell input tap` 点 "Wait" 关掉，或忽略。
- **不要用 `adb shell input swipe` 从 y<130 起手**——那是状态栏区域，
  会**拉下通知栏**而不是操作 App。验证抽屉下滑请从 **y≈170** 起手。

---

## 五、本阶段**没有**做的事

- **没有做皮肤导入/管理 UI**（P5）。当前骨架切换是 **debug 验收开关**，
  明确不是产品级功能；release 构建里该入口不存在（`tuningAccess` 恒为默认值）。
- **没有做页面内容装配**（`skin.json` 的 `sections` / `part` → Compose，P4）。
  抽屉型骨架里曲库页/我的页的 `sections` 都是空的——它们仍渲染**现有的页面实现**，
  只是底栏项数与播放页形态变了。
- **没有做 L5 资源包**（P5）。
- **没有做抽屉内的"半展开 peek"形态**。当前 `skipPartiallyExpanded = true`（只有开/关两态），
  与仓库现有 `PlayQueueSheet` 一致。原型里演示过 peek 三态，但那需要自定义
  `SheetState` 的初始值，收益不明确且增加状态机复杂度，留到有明确诉求时再说。


## 六、2026-10-24 追加：抽屉 → 歌手/专辑 → 返回 = 回到播放抽屉

### 1. 最终形态：详情页也放在**抽屉里**（和歌词页同一层机制）

抽屉是**全局浮层**，内部自己维护**当前内容**（`PlayerSheetContent`）：

| 内容 | 说明 |
|---|---|
| `Player` | 播放页（抽屉首页） |
| `Lyrics` | 歌词页 |
| `Artist(name)` | 歌手详情 |
| `Album(name)` | 专辑详情 |

导航**完全不碰主导航栈**：

- 抽屉里点歌手 → `sheetContent = Artist(name)`（抽屉不关、不卸载）；
- 抽屉里点专辑 → `sheetContent = Album(name)`；
- 抽屉里点封面 → `sheetContent = Lyrics`；
- 返回（系统键 / 页面内返回按钮）→ 退回 `Player`；
- 再返回 → 关抽屉。

好处：

- **系统返回与页面内返回天然一致**——两者都只切抽屉内的当前内容；
- ⚠️ 抽屉内容是**单层状态**、不是叠栈：详情与详情之间是**替换**——
  从"其他专辑"点进另一个专辑，返回回的是**播放抽屉**，不回上一个专辑。
- 抽屉**不卸载** → 不会重播"滑起"动画（用户 2026-10-24 反馈"重新拉起抽屉看着不舒服"）；
- 主导航栈**完全不动** → 底下的曲库页始终 composed，关抽屉瞬间、不闪白。

### 2. ⚠️ 走过的四条弯路（都真机翻车，勿重蹈）

**弯路一：把抽屉注册成导航里的 dialog 目的地。**
好处是底下的页面不被销毁。但 dialog 目的地会在系统里生成一个**窗口**——
空内容 + `usePlatformDefaultWidth=false` = 一个**全屏透明窗口**，它把触摸全部吃掉，
**抽屉整个点不动**（真机实测）。而且它与抽屉窗口的 z-order 取决于两个 StateFlow 谁先到，
**不可控**。

> 结论：**导航目的地 = 页面**，不是浮层。抽屉是浮层，就该用浮层的方式接线。

**弯路二：用 M3 的 `rememberStandardBottomSheetState` 造带初值的状态。**
本项目 M3 1.4.0 上：`skipHiddenState` 默认 **true**，而 `initialValue = Hidden` 会让
`SheetState` 构造器的 `require` 直接失败 → **IllegalArgumentException 崩溃**；
而且它把 `PartiallyExpanded` 放进锚点，`SheetState.show()` 会停在**半展开**。

> 正解：自己构造 `SheetState`（公开构造器，参数与内部 `rememberSheetState` 一致）：
> `skipPartiallyExpanded = true` + `skipHiddenState = false` + `initialValue = Hidden`。
> `PlayerSheetStateTest` 用纯 JVM 单测锁住了不再抛异常。

**弯路三：拿路由**模式**去比**填充后**的路由。**
Navigation 的 `destination.route` 给的是**模式**（`artist/{artistName}`），
而 `navigate()` 用的是**填充后**的（`artist/周杰伦`）——两者永远不相等。

**弯路四：用 `BackHandler` 抢系统返回键 / 监听 entry 的 `ON_DESTROY`。**

  补充（关键）：**主 window 挂 `BackHandler` 根本没机会收到这个键**——焦点在 sheet 的
  dialog window 上，按键被那个窗口消费掉了。要抢必须在**抽屉内容里**注册（同一窗口、
  组合更晚 → 同优先级内 LIFO 优先）。

  而 M3 自己那条回调在 **dialog 初始化时**就注册（`ModalBottomSheet.android.kt`：
  `onBackPressedDispatcher.addCallback(..., isEnabled = properties.shouldDismissOnBackPress)`，
  默认 true）→ 它比内容组合更早 → **它先执行** → 先 `sheetState.hide()`（抽屉滑走）
  才回调 `onDismissRequest` → 表现为"系统返回 = 关抽屉"，而页面内返回按钮正常
  （它不走这条路）。
本项目是 `activity-compose 1.12` + `navigation-compose 2.8.5`：返回走**新的
`androidx.navigationevent` 通道**，规则是「**按优先级，同优先级内后注册的先执行**」。
而 Navigation 2.8 的 `NavHost` 内部注册了 `PredictiveBackHandler(currentBackStack.size > 1)`，
且它在**栈变化时会重新注册**（于是变成"最后一个"→ 优先）——把 `BackHandler` 挪到
NavHost 之后注册也**抢不过它**。
监听 `ON_DESTROY` 同样不行：弹栈时若转场动画还在跑，被弹的 entry 只走到 `CREATED`
（`NavControllerImpl`：`if (!transitioning) DESTROYED else CREATED`），
`ON_DESTROY` 在组合还活着时不会触发。

表现很有辨识度：**系统返回没反应、页面内返回按钮正常**（两者是不同链路）。

> 正解：**不抢，也不监听**——把详情页放进抽屉，导航不碰主导航栈（见 §一）。

### 3. 未做真机验证

以上改动只坏一条交互路径的话单测发现不了（见 §二·4 的教训），需真机逐条验：
抽屉→歌手→**系统返回键**、抽屉→歌手→**页面内返回按钮**、抽屉→歌手→歌手页点专辑→返回两级、
抽屉内点歌词→返回、抽屉→歌手后切 Tab、抽屉开着按系统键。

---

## 七、下一步（P4）

P4 = L2 页面分区化 + L3 行模板/网格 + L4 播放页形态可配。

**P4 的最大工作量在 L2 分区化**：曲库页/我的页当前是整块 Composable，
要能被描述裁剪分区，必须先拆成"每个分区一个独立 Composable + 注册"。
建议**与骨架改造解耦、单独成批提交**，并且逐页比对（分区化属于纯重构，
不该有任何视觉变化）。
