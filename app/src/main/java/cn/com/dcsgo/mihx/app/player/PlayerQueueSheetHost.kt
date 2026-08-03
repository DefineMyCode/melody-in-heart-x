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
) {
    PlayQueueSheet(
        playQueue = playQueue,
        isShown = isShown,
        currentSongId = currentSongId,
        onSongClick = onSongClick,
        onRemoveSong = onRemoveSong,
        onClearQueue = onClearQueue,
        onDismiss = onDismiss,
    )
}
