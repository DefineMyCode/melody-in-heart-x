package cn.com.dcsgo.mihx.domain.model

/**
 * 本地歌曲文件校验汇总结果。
 *
 * 扫描曲库中每首歌曲对应的本地文件是否存在；对文件已缺失的歌曲，
 * 将其从歌曲列表与所有歌单中移除，并清理其在播放统计、秒切、播放事件等
 * 关联持久化数据中的记录，保证数据库与磁盘一致。
 */
data class LocalFileValidationResult(
    /** 扫描时的歌曲总数 */
    val totalSongs: Int,
    /** 文件已缺失的歌曲数 */
    val missingCount: Int,
    /** 从歌单中移除的引用数 */
    val removedPlaylistRefs: Int,
    /** 被清理（移除）的歌曲 id 列表 */
    val removedSongIds: List<Int>,
) {
    val hasMissingFiles: Boolean get() = missingCount > 0
}
