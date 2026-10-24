package cn.com.dcsgo.mihx.app.player


import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRoute
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRouteActions
import cn.com.dcsgo.mihx.feature.lyrics.LyricsRouteState
import cn.com.dcsgo.mihx.ui.components.ToastHost
import cn.com.dcsgo.mihx.ui.components.ToastHostState

/**
 * 全局播放抽屉（P3）。
 *
 * 设计文档：`docs/architecture/PLUGIN_SHELL_DESIGN.md` §六 P3
 *
 * 用途：当骨架描述声明 `playerEntry = "sheet"` 时，播放页不再是底栏 Tab，
 * 而是由迷你播放条拉起的全局抽屉。容器**复用仓库既有写法**
 * （`feature/player/.../PlayQueueSheet.kt:120` 的 `ModalBottomSheet`），不新造控件。
 *
 * ## 2026-09-30：抽屉内嵌歌词（仅 SHEET 骨架生效）
 *
 * 抽屉里点封面=切到歌词（不再关抽屉跳新词条路由）；歌词返回=回抽屉，关抽屉才退出。
 * 实现：调用方管 `showLyrics` 状态，本组件按它切换 content 渲染；
 * 返回键由 ModalBottomSheet 自己吃掉 → onDismissRequest → 上层「先关歌词→再关抽屉」。
 *
 * ## 2026-10-24：抽屉的开合由**返回栈**决定（不再由调用方开关）
 *
 * 抽屉现在登记为一条真实导航 route（`AppRoutes.PLAYER_SHEET`），
 * 调用方只需把 `isShown` 接成「栈顶是不是这条 route」即可：
 *
 *  - 拉起抽屉 = `navigate(PLAYER_SHEET)`；关抽屉 = `popBackStack()`；
 *  - 抽屉里点歌手/专辑 = `navigate(详情页)` → 栈顶换人 → 抽屉自动收起；
 *  - 详情页按返回 → 栈顶回到抽屉 → 抽屉自动复原（**这正是本次要修的行为**）。
 *
 * 因此 `onDismiss` 里的"关抽屉"动作必须走**返回栈**（弹掉那一层），
 * 而不是置一个本地开关——否则抽屉层会留在栈里，返回时行为又对不上了。
 * 另外 `onDismiss` 可能因"被导航卸载"而触发（此时栈顶已不是抽屉），
 * 调用方需自行判断栈顶再决定是否弹（见 `AppRoot` 里的接线）。
 *
 * ## ⚠️ 已核实的坑
 *
 * **1. 回调漏接线会让页面"假死"。** 若 `onDismissRequest` 拿到空 lambda，
 * 模态窗口会残留并继续拦截触摸——表现为"页面无响应但播放继续"。
 * 因此本组件的 `onDismiss` 是**必填参数**（无默认值），从类型层面杜绝漏接线。
 *
 * **2. BACK 键不走 onDismissRequest 的常规路径。** M3 的 `ModalBottomSheet` 渲染在
 * **独立 dialog window**，其返回处理由 sheet 自身消费后再回调 `onDismissRequest`。
 * 只要接线正确即可工作（真机已验证）。**不要**在主 window 另挂 `BackHandler` 抢它
 * ——主 window 根本收不到这个按键（焦点在 sheet 的窗口上）。
 *
 * ⚠️ **2b. 但 M3 那条回调会"先藏抽屉再通知"**，抽屉内有多层内容时它就不对了：
 * M3 在 dialog **初始化时**注册返回回调（早于内容组合）→ 同优先级内 LIFO 时它先跑
 * → 先 `sheetState.hide()`（抽屉滑走）才回调 `onDismissRequest` → 表现为
 * "系统返回 = 关抽屉"（页面内返回按钮正常，因为它不走这条路）。
 * 修法见下面内容里的 `BackHandler`：**在内容里**再注册一个（组合更晚 → 优先级更高），
 * 由它接管"抽屉内还有上一层"时的返回；到播放页那层时交回 M3 正常关闭。
 *
 * **3. ★ 不要覆写 `confirmValueChange` 去拦截 `Hidden`（真机验收抓到的真 bug）。**
 * 使用默认 confirmValueChange（允许 Hidden），关闭意图一律由 `onDismissRequest`
 * 通知上层；上层把 `isShown` 置 false，由 `if (!isShown) return` 卸载整个 sheet。
 *
 * **4. sheet 内可以挂 ToastHost,但必须靠焦点协调防双 toast（2026-10-01 两轮迭代）。**
 * ModalBottomSheet 即使全展开也只占下半屏,主窗口顶部仍可见——sheet 内和主窗口
 * 各挂一个 ToastHost 时,同一份状态会被两个窗口各画一份 toast。解法见
 * [ToastHost] 文档:每个 ToastHost 只在自己窗口聚焦时画(焦点天然互斥)。
 * 注意不要在 sheet 内容 Box 里只包一层(对齐错误会导致 toast 画在 sheet 本体
 * 顶部=屏幕中部)——sheet 内 toast 的位置就是 sheet 内容顶部,可接受。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheetHost(
    isShown: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Toast 状态。sheet 内会挂一个与主窗口共享此状态的 [ToastHost]——通过窗口焦点
     * 协调只让聚焦窗口画(见 [ToastHost] 文档),既不双 toast 也不会被遮住看不见。
     */
    toastHost: ToastHostState? = null,
    /**
     * 抽屉当前是不是停在详情/歌词上（而不是播放页）。
     *
     * 为 true 时，系统返回键应该**退回播放页**，而不是把整个抽屉关掉。
     */
    hasInnerContent: Boolean = false,
    /** 退回播放页（系统返回键用；抽屉内详情/歌词 → 播放页）。 */
    onBackToPreviousContent: () -> Unit = {},
    /**
     * 抽屉内容。**由调用方决定展示什么**——播放页 / 歌词 / 歌手详情 / 专辑详情
     * 都是抽屉内的一层（见 `PlayerSheetContent`），导航不碰主导航栈。
     */
    content: @Composable () -> Unit,
) {
    // 唯一真相是调用方的 isShown：关闭后直接卸载，因此不需要额外同步 sheet 状态。
    if (!isShown) return

    // ⚠️ 这里**不能**用 M3 的两个工厂函数（1.4.0 上都不合适）：
    //
    //  - `rememberModalBottomSheetState(skipPartiallyExpanded = true)`：已废弃；
    //  - `rememberStandardBottomSheetState(initialValue = …)`：
    //    ① `skipHiddenState` 默认 true，而 `initialValue = Hidden` 会直接
    //       `require` 失败抛 IllegalArgumentException（**真机崩溃过**）；
    //    ② 它把 PartiallyExpanded 放进 enabledValues，而 M3 的 `SheetState.show()`
    //       在有 PartiallyExpanded 时会停在**半展开**——抽屉会只弹一半。
    //
    // 所以这里自己造 SheetState：enabledValues 只给 {Hidden, Expanded}
    //（等价于旧 API 的 skipPartiallyExpanded = true，抽屉只有开/关两态）。
    val density = LocalDensity.current
    val sheetState = remember(density) {
        @Suppress("DEPRECATION")
        SheetState(
            // 只给开/关两态（等价旧 API 的 skipPartiallyExpanded = true）：
            // 含 PartiallyExpanded 时 M3 的 show() 会停在半展开。
            skipPartiallyExpanded = true,
            // 拖拽阈值：抽屉 gestureGesturesEnabled = false，本来就用不到；
            // 取 M3 1.4.0 的默认值（BottomSheetDefaults.PositionalThreshold=56dp /
            // VelocityThreshold=125dp）以免行为漂移。
            positionalThreshold = { with(density) { 56.dp.toPx() } },
            velocityThreshold = { with(density) { 125.dp.toPx() } },
            initialValue = SheetValue.Hidden,
            confirmValueChange = { true },
            // Hidden 必须可达，否则抽屉关不掉；
            // 且 skipHiddenState = true 时 M3 会要求初值不能是 Hidden（会抛异常）。
            skipHiddenState = false,
        )
    }

    // 横屏/大屏（窗口宽度 ≥600dp）：M3 默认 sheetMaxWidth=640dp 会把抽屉渲染成居中窄列——
    // 看起来"没全屏"。横屏时放开宽度上限（全宽）并让内容撑满高度（2026-10-21）。
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        sheetGesturesEnabled = false,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
        sheetMaxWidth = if (isLandscape) Dp.Unspecified else BottomSheetDefaults.SheetMaxWidth,
    ) {
        // 抽屉内容：播放页 / 歌词 / 歌手详情 / 专辑详情（由调用方决定）。
        // sheet 内挂 ToastHost(焦点协调只让聚焦窗口画,避免双 toast)——见 ToastHost 文档。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isLandscape) Modifier.fillMaxHeight() else Modifier),
        ) {
            // ⚠️ 抽屉内的系统返回键：**有上一层内容时由我们接管**。
            //
            // M3 的 `ModalBottomSheet` 自己在 **dialog 初始化时**就注册了返回回调
            // （`ModalBottomSheet.android.kt`：`onBackPressedDispatcher.addCallback(...,
            // isEnabled = properties.shouldDismissOnBackPress)`，默认 true），
            // 它比**内容组合更早**注册 → 同优先级内 LIFO 时它反而**先执行** →
            // 先把抽屉藏起来、再回调 `onDismissRequest` → 表现为"系统返回 = 关抽屉"
            // （真机实测：页面内返回按钮正常，系统返回却是关抽屉）。
            //
            // 这里在**内容里**再注册一个：内容组合更晚 → 优先级更高 → 抢在 M3 之前 ✓。
            // 于是"抽屉停在详情/歌词上"时返回由我们接管（退回播放页，抽屉不关）；
            // 在播放页时本回调 disabled → 交回 M3 → 正常关闭抽屉（带滑出动画）✓。
            BackHandler(enabled = hasInnerContent) {
                onBackToPreviousContent()
            }

            content()
            if (toastHost != null) {
                ToastHost(toastHost = toastHost, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
