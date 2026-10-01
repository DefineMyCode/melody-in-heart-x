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
    /** sheet 内挂焦点协调的 ToastHost(只让聚焦窗口画,避免双 toast)——见 ToastHost 文档。 */
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
