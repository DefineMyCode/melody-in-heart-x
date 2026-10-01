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
    /** 保留接线,但队列 sheet 内不再挂 ToastHost(避免双 toast,2026-10-01)——见 ToastHost.kt 注释。 */
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
