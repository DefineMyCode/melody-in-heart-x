package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions

class PlayerQuickSkipFacade(
    private val quickSkipActions: QuickSkipActions,
    private val refreshPlaylists: () -> Unit,
    private val launch: (() -> Unit) -> Unit,
) {
    fun getQuickSkipSongs(): List<Song> {
        return quickSkipActions.getSongs()
    }

    fun addToQuickSkipSongs(songId: Int) {
        quickSkipActions.add(songId)
    }

    fun removeFromQuickSkipSongs(songId: Int) {
        quickSkipActions.remove(songId)
    }

    fun isInQuickSkipSongs(songId: Int): Boolean {
        return quickSkipActions.contains(songId)
    }

    fun syncQuickSkipSongsToPlaylist() {
        launch {
            quickSkipActions.syncToPlaylist()
            refreshPlaylists()
        }
    }
}
