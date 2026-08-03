package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.RestoredPlaybackState

interface PlaybackStateRepository {
    fun save(queue: PlayQueue, positionMs: Long, currentSongId: Int? = null)
    fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long)
    fun clear()
    fun restore(allSongs: List<Song>): RestoredPlaybackState?
}
