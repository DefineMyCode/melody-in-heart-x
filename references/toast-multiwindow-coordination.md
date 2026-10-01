# Cross-Window Toast / Sheet 协调与 ToastHost 布局陷阱(2026-10-01 实踩)

## 背景与本节解决的问题

ModalBottomSheet(项目里 = `PlayerSheetHost` 播放抽屉、`PlayQueueSheet` 队列 sheet)
都是独立 dialog 窗口,**即使 `expand()` 全展开也只占下半屏**。当同一个 `ToastHostState`
在主窗口和 sheet 内各挂一份 `ToastHost` Composable,两边会各画一条相同内容的 toast
(用户 2026-10-01 报:双页样式下清空队列会看到两个 toast,一上一下)。

e32e6fb 当时的修法假设"后开 sheet 完全遮住先开窗口",实测是错的。

## ✅ 验证过的方案:LocalWindowInfo 协调

`androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused`
**在不同窗口内拿到的是各自窗口的 focus 状态**,而且会随系统焦点切换互补变化。
实测 logcat(2026-10-01 tfl_smoke)三个窗口的焦点切换:

```
启动                          [main]      isWindowFocused=true,  entries.size=0
拉起播放抽屉                  [main]      isWindowFocused=true,  entries.size=0
                              [playerSheet] isWindowFocused=false, entries.size=0   ← 抽屉初始
                              [main]      isWindowFocused=false, entries.size=0
                              [playerSheet] isWindowFocused=true, entries.size=0   ← 抽屉接管
在抽屉内开队列 sheet          [queueSheet] isWindowFocused=false, entries.size=0
                              [playerSheet] isWindowFocused=false, entries.size=0
                              [queueSheet] isWindowFocused=true,  entries.size=0   ← 队列接管
点"清空队列"                  [main]      isWindowFocused=false, entries.size=1   ← 全 false(AlertDialog 抢)
                              [playerSheet] isWindowFocused=false, entries.size=1
                              [queueSheet] isWindowFocused=false, entries.size=1
AlertDialog 关闭后约 1.7s     [queueSheet] isWindowFocused=true,  entries.size=1   ← queueSheet 接管并画
2s 后自动消失                 [main/playerSheet/queueSheet]      entries.size=0
```

**关键事实**:
- 同一时刻,只有一个窗口 `isWindowFocused=true`
- showToast 后三个 `ToastHost` 都因为订阅 `entries.size` 被通知重组,但只有
  `isWindowFocused=true` 那个绘制 entry(其它 if-return 让出)
- AlertDialog(也是独立 dialog window)打开期间所有窗口都 `isWindowFocused=false`——这是
  单一焦点机制的副作用,意味着 AlertDialog 关闭动画期间**没人画 toast**(短暂消失)
- 焦点回到 sheet 后只有那一个窗口画 toast——**单一 toast ✓**

## 复现配方

### 代码骨架(`ToastHost.kt`)

```kotlin
@Composable
fun ToastHost(
    toastHost: ToastHostState,
    modifier: Modifier = Modifier,
    caller: String = "main",  // DEBUG: 区分窗口,排查阶段用
) {
    val isFocused = LocalWindowInfo.current.isWindowFocused
    // DEBUG: android.util.Log.d("ToastHost", "[$caller] isWindowFocused=$isFocused, ...")

    // ★ 必须用 Column 而非 Box+contentAlignment=TopCenter(详见下节布局陷阱)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ★ 订阅 entries.size 让 showToast 触发重组(详见下节订阅陷阱)
        val currentSize = toastHost.entries.size
        val visibleEntries = if (currentSize > 0) toastHost.entries.takeLast(1) else emptyList()
        visibleEntries.forEach { entry ->
            ToastItem(entry = entry, onDismiss = { toastHost.dismiss(entry.id) }, ...)
        }
    }
}
```

### 接线点

- 主窗口 `AppRoot.kt` 末尾挂一份 `ToastHost(toastHost, caller = "main")`
- `PlayerSheetHost.kt` 的 ModalBottomSheet 内容外包 Box,挂一份 `ToastHost(caller = "playerSheet")`
- `PlayQueueSheet.kt` 同上,挂一份 `ToastHost(caller = "queueSheet")`

### 验证步骤

1. 装包启动模拟器,在每个 `ToastHost` 加临时 `android.util.Log.d("ToastHost", "[$caller] isWindowFocused=$isFocused, entries.size=${toastHost.entries.size}")`
2. 走完整流程(加歌 → 拉抽屉 → 开队列 sheet → 清空),抓 logcat
3. 看 `isWindowFocused` 在三个 caller 间是否互补;只有 `true` 时才该有 `[caller] drawing entry` 日志
4. screencap 对比修复前后(修复前两条 toast 各占屏幕一端,修复后只一条)

## ❌ 尝试过但未通过的方案(踩坑记录,避免重蹈)

### 方案 A:全局计数器(`ToastHostState.activeSheetWindows: Int`)

`PlayerSheetHost`/`PlayQueueSheet` 在 `DisposableEffect(isShown)` 里 `acquireSheetWindow`/`releaseSheetWindow`,
ToastHost 读 `activeSheetWindows > 0` 时让出主窗口。

**失败原因**:两个 sheet 都 acquire 后 `activeSheetWindows=2`,**两个 sheet 内的 ToastHost 都
满足"我是 sheet"条件都画**,结果还是双 toast。计数 > 0 不能区分"我是栈顶"和"我是被遮挡的 sheet"。

如果未来要继续这条路,需要让每个 sheet 知道自己是第几个 acquire 的,或者用 `LocalWindowInfo`
判定(最终方案 3 实际上就是简化版的窗口协调)。

### 方案 B:删除 sheet 内的 ToastHost,只在主窗口画

`PlayerSheetHost`/`PlayQueueSheet` 内的 ToastHost 整段删除,让 toast 只走主窗口那条路径。

**问题**:全屏 sheet 弹出时(理论上)主窗口 toast 被完全遮住看不见——但实测 ModalBottomSheet
即使 expand() 也只占下半屏,所以这条方案在实际项目里**碰巧可用**,因为 sheet 半屏时
主窗口顶部始终可见。

不选这条的原因:半屏只是 M3 1.3 ModalBottomSheet 的当前行为,M3 1.4 或其它 dialog
可能全屏遮主窗口,会留下"看不见 toast"的回归坑。LocalWindowInfo 方案对此免疫。

## ★ Compose 布局陷阱:`Box(fillMaxWidth) + contentAlignment` 的 0 高度陷阱

项目旧 ToastHost 用:

```kotlin
Box(
    modifier = modifier.fillMaxWidth(),
    contentAlignment = Alignment.TopCenter
) {
    Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)...) { ... }
}
```

**问题**:`Box` 只设 `fillMaxWidth` 不设 `fillMaxHeight`,Box 高度 = wrap content。
`contentAlignment = TopCenter` 仅在 Box 有明确尺寸时才生效——0 高度时
`TopCenter = 顶部 0 像素`,Column 被压到屏幕顶端 0 像素位置,即使有 `windowInsetsPadding`
也救不回来(inset 把内容再向下推 status bar 高度,但 Box 自己仍然是 0 高度,布局里
**整块 Column 都在屏幕外或被状态栏遮**)。

**实证**:给 Column 加 4dp 红色 border `Modifier.border(4.dp, Color.Red)`,logcat 显示
`[main] drawing entry` 已执行,但 screencap 整张图屏幕顶端到 y=300 范围**完全无红框**——
ToastHost Column 实际不在可视区域。

**正解**:直接用 `Column(fillMaxWidth().windowInsetsPadding(statusBars), horizontalAlignment = CenterHorizontally)`
替代 Box 包裹。Column wrap content 高度自然撑开,windowInsetsPadding 让顶端避开 status bar,
`horizontalAlignment = CenterHorizontally` 直接生效(不需要 Box 的 contentAlignment 介入)。

**通用规则**:任何 `Box(modifier = Modifier.X().Y(), contentAlignment = ...)`
想让子元素"靠某边对齐",Box 必须至少有**一个轴**的尺寸约束(fillMaxSize / fillMaxWidth /
明确 height),否则 contentAlignment 是空操作。Compose 这点跟传统 Android View 的
`FrameLayout` 不一样(FrameLayout 默认 wrap 也生效),**Compose Box 的 contentAlignment
只在 Box 有约束时才生效**。

## ★ Subscription 陷阱:`mutableStateListOf.takeLast(1).forEach` 在空时不订阅

```kotlin
// ❌ 看似能订阅,实际 entries 为空时不订阅任何状态
val visibleEntries = toastHost.entries.takeLast(1)  // 返回空 List,不读 entries
visibleEntries.forEach { entry -> ToastItem(entry) } // forEach 0 次,无订阅

// ✅ 显式读 entries.size 触发订阅
val currentSize = toastHost.entries.size  // 订阅 entries 列表本身
val visibleEntries = if (currentSize > 0) toastHost.entries.takeLast(1) else emptyList()
visibleEntries.forEach { entry -> ToastItem(entry) }  // 此时子项订阅仍然有效
```

**症状**:showToast 调了(`_entries.add(...)` 已写入),但 ToastHost 函数体不重组,
日志只打印启动时一次,后续 showToast 完全没反应。

**根因**:`mutableStateListOf<T>` 的状态读取需要**有人读它**(state read),`takeLast`
不读(只算),`forEach` 在空 List 上不进入循环、不读 `entry.message`——
整个 `ToastHost` 函数体的重组 scope 没有订阅 `entries` 状态变化,所以 `_entries.add()`
触发的 snapshot 变化不引发本 Composable 重组。只有 ToastItem 内部重组(不依赖 ToastHost 函数体),
但 ToastItem 还没被 forEach 拉进来,自然就**根本没绘制**。

**修法**:在 forEach **之前**显式读一次 `entries.size`——这把 `entries` 这个 snapshot
list 本身加入 ToastHost 函数体的 read 集合,后续 `add/clear` 都会触发重组。

**通用规则**:凡是用 `mutableStateListOf/StateFlow.collectAsState()` 这类集合状态 + 后续
过滤/映射/forEach 时,**显式读一次集合的 size/length 或 any()**,确保函数体本身
订阅了集合的"形状变化"(元素数变化),而不只是依赖子项订阅元素内容。

## 相关代码位置

- `core/ui/src/main/java/cn/com/dcsgo/mihx/ui/components/ToastHost.kt`
  - 协调主体(`LocalWindowInfo.current.isWindowFocused` + `caller` 参数)
- `app/src/main/java/cn/com/dcsgo/mihx/app/AppRoot.kt:385`
  - 主窗口 ToastHost 挂点
- `app/src/main/java/cn/com/dcsgo/mihx/app/player/PlayerSheetHost.kt:99-100`
  - 抽屉内 ToastHost(`caller = "playerSheet"`)
- `feature/player/src/main/java/cn/com/dcsgo/mihx/feature/player/PlayQueueSheet.kt:222-227`
  - 队列 sheet 内 ToastHost(`caller = "queueSheet"`)

## 验收清单

- [ ] `assembleDebug` + 架构门禁 + 全量单测 全绿
- [ ] 真机/模拟器走完"加歌 → 拉抽屉 → 开队列 sheet → 清空"流程,screencap 在
      queueSheet 接管焦点后的帧只看到一条 toast
- [ ] 反向对照:把 `ToastHostState.showToast` 改成空实现,确认 logcat 不再出现
      `drawing entry` 日志(证明 drawing 日志能反映真实渲染)
- [ ] AlertDialog 关闭动画期间**没有 toast 出现**(预期,因所有窗口都 false)——
      评估是否需要在 AlertDialog 里也加 ToastHost(项目目前没这个需求)
