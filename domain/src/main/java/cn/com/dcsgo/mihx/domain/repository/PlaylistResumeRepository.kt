package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.domain.model.PlaylistResume
import kotlinx.coroutines.flow.Flow

interface PlaylistResumeRepository {
    fun observeResume(playlistId: Int): Flow<PlaylistResume?>

    suspend fun record(playlistId: Int, songId: Int)

    suspend fun clear(playlistId: Int)

    /** 当前播放队列来源歌单(非歌单来源为 null)。用于在退出/切换播放源时记录"实际在播"的歌曲。 */
    suspend fun currentSourcePlaylistId(): Int?

    /** 设置当前播放队列来源歌单;null 表示当前队列不来自任何歌单。 */
    suspend fun setSourcePlaylist(playlistId: Int?)

    /**
     * 切换播放来源歌单。
     *
     * 若旧来源存在且与 [newSource] 不同,先以 [currentSongId](旧来源实际在播的歌曲)记录该歌单,
     * 再写入新的来源标记。读旧值与写新值在单次事务内完成,避免连续切换时把歌曲记到错误的歌单上。
     */
    suspend fun switchSourcePlaylist(newSource: Int?, currentSongId: Int?)

    /**
     * 播放服务退出钩子:若当前存在来源歌单,把 [songId] 记录为该歌单的上次播放歌曲,
     * 并清除来源标记。读与写在单次事务内完成,返回是否发生了记录。
     */
    suspend fun recordCurrentSource(songId: Int): Boolean
}
