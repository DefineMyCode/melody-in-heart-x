package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.playback.RestoredPlaybackState

/**
 * 播放状态持久化仓库（域层）。
 *
 * 评审 2026-10-27 · N-1：全部改为 `suspend`——底层是 DataStore 磁盘写，
 * 同步接口会把磁盘 IO 传染给调用方（主线程可达路径会卡 UI）。
 */
interface PlaybackStateRepository {
    suspend fun save(queue: PlayQueue, positionMs: Long, currentSongId: Int? = null)
    suspend fun saveCurrentPlaybackSnapshot(songId: Int, positionMs: Long)
    suspend fun clear()
    suspend fun restore(allSongs: List<Song>): RestoredPlaybackState?
}
