package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions

/**
 * 秒切歌曲门面。
 * M-5（评审 2026-10-15）：全部 suspend —— 调度交给 PlayerRuntime（scope.launch），
 * 组合期/主线程不再同步查库；UI 通过 PlayerRuntime.quickSkipSongs StateFlow 消费列表。
 */
class PlayerQuickSkipFacade(
    private val quickSkipActions: QuickSkipActions,
    private val refreshPlaylists: () -> Unit,
) {
    suspend fun getQuickSkipSongs(): List<Song> {
        return quickSkipActions.getSongs()
    }

    suspend fun addToQuickSkipSongs(songId: Int) {
        quickSkipActions.add(songId)
    }

    suspend fun removeFromQuickSkipSongs(songId: Int) {
        quickSkipActions.remove(songId)
    }

    suspend fun isInQuickSkipSongs(songId: Int): Boolean {
        return quickSkipActions.contains(songId)
    }

    suspend fun syncQuickSkipSongsToPlaylist() {
        quickSkipActions.syncToPlaylist()
        refreshPlaylists()
    }
}
