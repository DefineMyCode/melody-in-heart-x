package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Song

/**
 * 本地音乐文件大小统计。
 *
 * 只负责读取文件字节数，不做音质分档或占比计算（那部分由纯函数
 * `LocalMusicStorageStats.from` 统一完成，避免阈值口径分裂）。
 */
interface MediaStorageStatsRepository {
    /**
     * 测量歌曲对应的本地文件大小。
     *
     * @return 歌曲 id → 字节数；读取失败或文件不存在的歌曲不会出现在结果中。
     */
    suspend fun measureFileSizes(songs: List<Song>): Map<Int, Long>
}