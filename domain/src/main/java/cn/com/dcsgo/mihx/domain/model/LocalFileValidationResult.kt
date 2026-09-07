package cn.com.dcsgo.mihx.domain.model

/**
 * 本地歌曲文件校验汇总结果。
 *
 * 扫描曲库中每首歌曲对应的本地文件是否存在；对文件已缺失的歌曲，
 * 将其从歌曲列表与所有歌单中移除，并清理其在播放统计、秒切、播放事件等
 * 关联持久化数据中的记录，保证数据库与磁盘一致。
 *
 * 元数据刷新（v3.7.0 新增）：URI 仍存活的歌曲可按指纹（大小+修改时间）
 * 预筛或全量重新提取元数据，变化则就地更新（保留 songId，统计/情绪/歌单关联不断）。
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
    /** 元数据发生变化并已就地更新的歌曲数（快速/深度校验时可能 >0） */
    val metadataUpdatedCount: Int = 0,
    /** 本次校验模式；null 表示旧版本结果（未区分模式） */
    val mode: FileCheckMode? = null,
) {
    val hasMissingFiles: Boolean get() = missingCount > 0
    val hasMetadataUpdates: Boolean get() = metadataUpdatedCount > 0
}
