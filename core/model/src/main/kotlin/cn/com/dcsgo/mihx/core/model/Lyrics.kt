package cn.com.dcsgo.mihx.core.model

/** A single timed lyric line. */
data class LyricLine(
    val timeMs: Long,
    val text: String,
)

/** Full lyrics with a time axis. */
data class Lyrics(
    val songId: Long,
    val lines: List<LyricLine>,
) {
    /** Index of the line active at [positionMs], or -1 if none. */
    fun indexAt(positionMs: Long): Int {
        var lo = 0
        var hi = lines.size - 1
        var result = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return result
    }
}
