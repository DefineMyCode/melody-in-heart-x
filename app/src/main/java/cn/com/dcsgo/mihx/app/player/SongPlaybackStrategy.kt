package cn.com.dcsgo.mihx.app.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.feature.player.PlayerViewModel

/**
 * 点击歌曲后决定播放队列范围的策略（策略模式）。
 *
 * 各页面在接线时选择一种策略：展示全曲库/统计类场景用 [single] 只入队被点击歌曲，
 * 歌单/专辑/歌手等分组场景用 [scope] 入队整组展示歌曲。统一由 [playWith] 完成入队与播放。
 */
interface SongPlaybackStrategy {
    /** 返回被点击歌曲所属的入队范围（必须包含被点击歌曲自身）。 */
    fun scopeFor(clicked: Song): List<Song>

    companion object {
        /** 单曲策略：队列仅包含被点击歌曲。 */
        fun single(): SongPlaybackStrategy = SingleSongStrategy

        /** 固定范围策略：队列为给定的整组歌曲（歌单/专辑/歌手等）。 */
        fun scope(songs: List<Song>): SongPlaybackStrategy = FixedScopeStrategy(songs)
    }
}

private data object SingleSongStrategy : SongPlaybackStrategy {
    override fun scopeFor(clicked: Song): List<Song> = listOf(clicked)
}

private class FixedScopeStrategy(private val songs: List<Song>) : SongPlaybackStrategy {
    override fun scopeFor(clicked: Song): List<Song> = songs
}

/**
 * 统一播放入口：按策略构建队列并立即播放（顺序模式，从被点击歌曲开始）。
 * 行为与原各页面自建队列完全等价：替换并清空原队列。
 */
fun PlayerViewModel.playWith(song: Song, strategy: SongPlaybackStrategy) {
    val scope = strategy.scopeFor(song)
    val startIndex = scope.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
    setPlayQueue(scope, startIndex, PlayMode.SEQUENTIAL)
}
