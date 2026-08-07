package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import kotlinx.coroutines.flow.Flow

interface PlaylistResumeRepository {
    fun observeResume(playlistId: Int): Flow<PlaylistResume?>

    suspend fun record(playlistId: Int, songId: Int)

    suspend fun clear(playlistId: Int)

    /** 当前播放队列来源歌单(非歌单来源为 null)。用于在退出/切换播放源时记录"实际在播"的歌曲。 */
    fun currentSourcePlaylistId(): Int?

    /** 设置当前播放队列来源歌单;null 表示当前队列不来自任何歌单。 */
    suspend fun setSourcePlaylist(playlistId: Int?)

    /**
     * 播放服务退出钩子(阻塞):若当前存在来源歌单,把 [songId] 记录为该歌单的上次播放歌曲,
     * 并清除来源标记。返回是否发生了记录。
     */
    fun recordCurrentSourceBlocking(songId: Int): Boolean
}
