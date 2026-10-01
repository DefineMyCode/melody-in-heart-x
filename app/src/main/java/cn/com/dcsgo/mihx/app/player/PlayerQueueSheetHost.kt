package cn.com.dcsgo.mihx.app.player

import androidx.compose.runtime.Composable
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.feature.player.PlayQueueSheet

@Composable
fun PlayerQueueSheetHost(
    playQueue: PlayQueue,
    isShown: Boolean,
    currentSongId: Int?,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onDismiss: () -> Unit,
    /** 抽屉同款：队列 sheet 是独立 dialog 窗口，需要在本窗口内再画一层 toast（2026-10-01 修）。 */
    toastHost: cn.com.dcsgo.mihx.ui.components.ToastHostState? = null,
) {
    PlayQueueSheet(
        playQueue = playQueue,
        isShown = isShown,
        currentSongId = currentSongId,
        onSongClick = onSongClick,
        onRemoveSong = onRemoveSong,
        onClearQueue = onClearQueue,
        onDismiss = onDismiss,
        toastHost = toastHost,
    )
}
