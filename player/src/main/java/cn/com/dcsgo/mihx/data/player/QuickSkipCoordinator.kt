package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository

class QuickSkipCoordinator(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val quickSkipRepository: QuickSkipRepository,
) : QuickSkipActions {
    override fun getSongs(): List<Song> {
        val songIds = quickSkipRepository.getSongIds()
        return songRepository.observeSongsSnapshot().filter { it.id in songIds }
    }

    override fun add(songId: Int): Boolean = quickSkipRepository.add(songId)

    override fun remove(songId: Int): Boolean = quickSkipRepository.remove(songId)

    override fun contains(songId: Int): Boolean = quickSkipRepository.contains(songId)

    override fun syncToPlaylist() {
        val quickSkipPlaylist = playlistRepository.getPlaylists()
            .find { it.name == PLAYLIST_NAME }
            ?: playlistRepository.createPlaylist(PLAYLIST_NAME)
            ?: return

        quickSkipPlaylist.songIds.toList().forEach { songId ->
            playlistRepository.removeSongFromPlaylist(quickSkipPlaylist.id, songId)
        }

        quickSkipRepository.getSongIds().forEach { songId ->
            playlistRepository.addSongToPlaylist(quickSkipPlaylist.id, songId)
        }
    }

    companion object {
        const val PLAYLIST_NAME = "秒切歌曲"
    }
}
