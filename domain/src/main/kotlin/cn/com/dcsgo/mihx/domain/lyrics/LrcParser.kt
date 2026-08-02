package cn.com.dcsgo.mihx.domain.lyrics

import cn.com.dcsgo.mihx.core.model.LyricLine

/**
 * Parses standard `.lrc` text into timed [LyricLine]s. Pure Kotlin (no Android dependency) so it
 * can run on the domain/JUnit classpath. A line may carry several `[mm:ss.xx]` timestamps (a
 * repeated lyric); each timestamp is emitted as its own line pointing at the same text.
 *
 * Metadata tags such as `[ti:...]` / `[ar:...]` / `offset:` carry no time axis and are
 * ignored; the `offset:` shift is not applied (line granularity is enough for highlighting).
 */
object LrcParser {
    private val TAG_RE = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(text: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        for (raw in text.lineSequence()) {
            val content = raw.substringAfterLast("]").trim()
            if (content.isEmpty()) continue
            for (m in TAG_RE.findAll(raw)) {
                val min = m.groupValues[1].toLongOrNull() ?: continue
                val sec = m.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = m.groupValues[3]
                val frac = if (fracRaw.isEmpty()) 0L else fracRaw.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
                val timeMs = (min * 60 + sec) * 1000 + frac
                out += LyricLine(timeMs = timeMs, text = content)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
