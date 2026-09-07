package cn.com.dcsgo.mihx.feature.playlist

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.model.SongSortMode
import java.text.Collator
import java.util.Locale

/**
 * 本地音乐列表排序器。
 *
 * 字符串字段（歌名/歌手/专辑）用中文 Collator 拼音序，数值字段直接比较；
 * 播放次数/最近播放缺失时（从未播放）按次级键 id 稳定排序，避免列表抖动。
 */
object SongSorter {

    private val collator: Collator = Collator.getInstance(Locale.CHINA)

    fun sort(
        songs: List<Song>,
        mode: SongSortMode,
        ascending: Boolean,
        playCounts: Map<Int, Int> = emptyMap(),
        lastPlayedAt: Map<Int, Long> = emptyMap(),
    ): List<Song> {
        if (songs.isEmpty()) return songs
        val comparator: Comparator<Song> = when (mode) {
            SongSortMode.IMPORT_ORDER -> compareBy<Song> { it.id }
            SongSortMode.TITLE -> compareBy<Song, String>(collator) { it.groupKey }
            SongSortMode.ARTIST -> compareBy<Song, String>(collator) { it.parsedArtists.firstOrNull().orEmpty() }
                .thenBy(collator) { it.groupKey }
            SongSortMode.ALBUM -> compareBy<Song, String>(collator) { it.album }
                .thenBy(collator) { it.groupKey }
            SongSortMode.DURATION -> compareBy { it.durationMs }
            SongSortMode.SAMPLE_RATE -> compareBy { it.sampleRate }
            SongSortMode.PLAY_COUNT -> compareBy<Song> { playCounts[it.id] ?: 0 }
                .thenBy { it.id }
            SongSortMode.LAST_PLAYED -> compareBy<Song> { lastPlayedAt[it.id] ?: 0L }
                .thenBy { it.id }
        }
        val ordered = if (ascending) {
            songs.sortedWith(comparator)
        } else {
            songs.sortedWith(comparator.reversed())
        }
        return ordered
    }
}
