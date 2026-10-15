package cn.com.dcsgo.mihx.domain.repository

/**
 * 播放统计仓库接口（域层）。
 * M-5（评审 2026-10-15）：同步方法全部改 suspend —— 实现是 Room runBlocking 桥，
 * 随心播放点击路径（getRawPlayCounts）曾因此阻塞主线程。
 */
interface PlayStatsRepository {
    suspend fun getCounts(songIds: List<Int>): Map<Int, Int>

    suspend fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int>

    suspend fun getRankedCounts(useRawCounts: Boolean = false, descending: Boolean = true): List<Pair<Int, Int>>

    /** 批量取有效播放次数（排序用，未播放的歌不在返回 Map 中） */
    suspend fun getPlayCountsForSort(): Map<Int, Int>

    /** 批量取最近播放时间戳 epoch 毫秒（排序用，从未播放的歌不在返回 Map 中） */
    suspend fun getLastPlayedAtForSort(): Map<Int, Long>

    suspend fun increment(songId: Int): Int

    suspend fun incrementRawPlayCount(songId: Int): Int

    suspend fun recordCompletedPlay(songId: Int)

    /**
     * 记录一次播放会话，供按日/周/月聚合。
     *
     * @param songId        歌曲 ID
     * @param startedAtMs   会话开始时间（epoch 毫秒）
     * @param durationMs    会话累计播放时长（毫秒）
     * @param isEffectivePlay 是否有效播放（完播率达标或长歌超 5 分钟）
     */
    suspend fun recordPlaybackSession(songId: Int, startedAtMs: Long, durationMs: Long, isEffectivePlay: Boolean)

    /** 播放统计中心快照：今日 / 本周逐日 / 本周榜 / 本月榜 */
    suspend fun playbackStatsSnapshot(): PlaybackStatsSnapshot
}
