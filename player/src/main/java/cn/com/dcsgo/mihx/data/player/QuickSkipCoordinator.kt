package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.quickskip.QuickSkipActions
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository

/**
 * 秒切歌曲协调器。
 * M-5（评审 2026-10-15）：QuickSkipActions/QuickSkipRepository 已 suspend 化，方法透传挂起调用。
 */
class QuickSkipCoordinator(
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val quickSkipRepository: QuickSkipRepository,
) : QuickSkipActions {
    override suspend fun getSongs(): List<Song> {
        val songIds = quickSkipRepository.getSongIds()
        return songRepository.observeSongsSnapshot().filter { it.id in songIds }
    }

    override suspend fun add(songId: Int): Boolean = quickSkipRepository.add(songId)

    override suspend fun remove(songId: Int): Boolean = quickSkipRepository.remove(songId)

    override suspend fun contains(songId: Int): Boolean = quickSkipRepository.contains(songId)

    override suspend fun syncToPlaylist() {
        val quickSkipPlaylist = playlistRepository.getPlaylists()
            .find { it.name == PLAYLIST_NAME }
            ?: playlistRepository.createPlaylist(PLAYLIST_NAME)
            ?: return

        // m-9（评审 2026-10-15）：整体替换，单次落盘；不再逐首删+逐首加
        // （旧实现每首歌一次整表落盘，期间歌单短暂为空，1000 首 = 2000 次全量重写）
        playlistRepository.replacePlaylistSongs(quickSkipPlaylist.id, quickSkipRepository.getSongIds().sorted())
    }

    companion object {
        const val PLAYLIST_NAME = "秒切歌曲"
    }
}
