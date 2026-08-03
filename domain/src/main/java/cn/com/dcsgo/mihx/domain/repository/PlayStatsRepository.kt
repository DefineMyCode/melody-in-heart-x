package cn.com.dcsgo.mihx.domain.repository

interface PlayStatsRepository {
    fun getCounts(songIds: List<Int>): Map<Int, Int>
    fun getRawPlayCounts(songIds: List<Int>): Map<Int, Int>
    fun getRankedCounts(useRawCounts: Boolean = false, descending: Boolean = true): List<Pair<Int, Int>>
    fun increment(songId: Int): Int
    fun incrementRawPlayCount(songId: Int): Int
    fun recordCompletedPlay(songId: Int)
}
