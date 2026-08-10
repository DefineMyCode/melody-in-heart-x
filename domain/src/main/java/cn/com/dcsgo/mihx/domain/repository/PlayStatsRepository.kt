package cn.com.dcsgo.mihx.domain.repository

interface PlayStatsRepository {
    fun getCounts(songIds: List<Int>): Map<Int, Int>
    fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int>
    suspend fun getRankedCounts(useRawCounts: Boolean = false, descending: Boolean = true): List<Pair<Int, Int>>
    fun increment(songId: Int): Int
    fun incrementRawPlayCount(songId: Int): Int
    fun recordCompletedPlay(songId: Int)

    /**
     * 记录一次播放会话，供按日/周/月聚合。
     *
     * @param songId        歌曲 ID
     * @param startedAtMs   会话开始时间（epoch 毫秒）
     * @param durationMs    会话累计播放时长（毫秒）
     * @param isEffectivePlay 是否有效播放（完播率达标或长歌超 5 分钟）
     */
    fun recordPlaybackSession(songId: Int, startedAtMs: Long, durationMs: Long, isEffectivePlay: Boolean)

    /** 播放统计中心快照：今日 / 本周逐日 / 本周榜 / 本月榜 */
    suspend fun playbackStatsSnapshot(): PlaybackStatsSnapshot
}
