package cn.com.dcsgo.mihx.app.player


import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
 * ## ⚠️ 已核实的坑
 *
 * **1. 回调漏接线会让页面"假死"。** 若 `onDismissRequest` 拿到空 lambda，
 * 模态窗口会残留并继续拦截触摸——表现为"页面无响应但播放继续"。
 * 因此本组件的 `onDismiss` 是**必填参数**（无默认值），从类型层面杜绝漏接线。
 *
 * **2. BACK 键不走 onDismissRequest 的常规路径。** M3 的 `ModalBottomSheet` 渲染在
 * **独立 dialog window**，其返回处理由 sheet 自身消费后再回调 `onDismissRequest`。
 * 只要接线正确即可工作（真机已验证）。**不要**在主 window 另挂 `BackHandler` 抢它。
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
    /** 抽屉级 showLyrics 状态（外部管）。点封面=true，返回=true，点击歌词=true 路由同点封面。 */
    showLyrics: Boolean = false,
    onLyricsBack: () -> Unit = {},
    /** 歌词页状态；只在 `showLyrics = true` 时启用。 */
    lyricsState: LyricsRouteState = LyricsRouteState(null, 0L, false),
    lyricsActions: LyricsRouteActions = LyricsRouteActions(onBackClick = {}, onSeekTo = {}),
    loadLyrics: suspend (Song) -> Lyrics = { Lyrics.EMPTY },
    content: @Composable () -> Unit,
) {
    // 唯一真相是调用方的 isShown：关闭后直接卸载，因此不需要额外同步 sheet 状态。
    if (!isShown) return

    // 使用默认 confirmValueChange（允许 Hidden），下滑/点遮罩/返回键都能正常关闭。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = null,
        sheetGesturesEnabled = false,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        // 抽屉内容：歌词或播放页。
        // sheet 内挂 ToastHost(焦点协调只让聚焦窗口画,避免双 toast)——
        // 抽屉全屏展开时若只靠主窗口画,toast 会被完全遮住看不见(2026-10-01 用户反馈)。
        Box(modifier = Modifier.fillMaxWidth()) {
            if (showLyrics) {
                LyricsRoute(
                    state = lyricsState,
                    actions = lyricsActions.copy(onBackClick = onLyricsBack),
                    loadLyrics = loadLyrics,
                )
            } else {
                content()
            }
            if (toastHost != null) {
                ToastHost(toastHost = toastHost, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
