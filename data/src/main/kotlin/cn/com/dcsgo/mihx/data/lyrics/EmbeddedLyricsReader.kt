package cn.com.dcsgo.mihx.data.lyrics

import android.content.Context
import androidx.core.net.toUri
import cn.com.dcsgo.mihx.core.model.LyricLine
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.lyrics.LrcParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.Charsets

/**
 * Reads embedded lyrics from the audio file itself, so songs without a sibling `.lrc` still show
 * lyrics. Supported: MP3 ID3v2 `USLT` frames and FLAC Vorbis Comments `LYRICS` field.
 *
 * The extracted text is mapped to [Lyrics] as follows: when it carries `[mm:ss.xx]` timestamps it
 * is parsed by [LrcParser] into synced lines; otherwise each line becomes an unsynced
 * [LyricLine] with `timeMs = 0` (the lyrics page then renders it without active-line
 * highlighting). Returns null when no embedded lyrics are found or the container is unsupported.
 *
 * Only the leading portion of the file is read (lyrics metadata always lives at the start), so a
 * full track is never loaded into memory.
 */
@Singleton
class EmbeddedLyricsReader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        // Only the metadata region is needed; 512KB covers virtually all ID3v2 tags / Vorbis
        // comment blocks while keeping the SAF read (8KB chunks over the content bridge) snappy.
        // The process-wide cache in LyricsRepositoryImpl makes this happen at most once per song.
        const val MAX_BYTES = 512 * 1024
    }

    suspend fun read(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val uri = song.uri ?: return@withContext null
        val bytes = context.contentResolver.openInputStream(uri.toUri())?.use { readUpTo(it, MAX_BYTES) }
            ?: return@withContext null
        val text = extractText(bytes) ?: return@withContext null
        val lines = buildLines(text)
        if (lines.isEmpty()) null else Lyrics(songId = song.id, lines = lines)
    }

    private fun readUpTo(stream: InputStream, max: Int): ByteArray {
        val buf = ByteArray(8 * 1024)
        val out = ByteArrayOutputStream()
        var total = 0
        while (total < max) {
            val n = stream.read(buf)
            if (n == -1) break
            out.write(buf, 0, n)
            total += n
        }
        return out.toByteArray()
    }

    private fun extractText(bytes: ByteArray): String? = when {
        byteAt(bytes, 0) == 'I'.code && byteAt(bytes, 1) == 'D'.code && byteAt(bytes, 2) == '3'.code ->
            extractId3v2(bytes)
        byteAt(bytes, 0) == 'f'.code &&
            byteAt(bytes, 1) == 'L'.code &&
            byteAt(bytes, 2) == 'a'.code &&
            byteAt(bytes, 3) == 'C'.code ->
            extractFlac(bytes)
        else -> null
    }

    private fun extractId3v2(bytes: ByteArray): String? {
        if (bytes.size < 10) return null
        val major = byteAt(bytes, 3)
        val tagSize = synchsafe(bytes, 6, 4)
        if (tagSize <= 0) return null
        val end = (10 + tagSize).coerceAtMost(bytes.size)
        val v22 = major == 2
        var pos = 10
        while (pos + (if (v22) 6 else 10) <= bytes.size) {
            val id = String(bytes, pos, if (v22) 3 else 4, Charsets.ISO_8859_1)
            val frameSize = if (v22) {
                intSafe(bytes, pos + 3, 3)
            } else {
                if (major >= 4) synchsafe(bytes, pos + 4, 4) else intSafe(bytes, pos + 4, 4)
            }
            if (frameSize <= 0) break
            val headerLen = if (v22) 6 else 10
            val bodyStart = pos + headerLen
            val isUslt = if (v22) id == "ULT" else id == "USLT"
            if (isUslt && bodyStart + 1 < bytes.size) {
                val frameEnd = (bodyStart + frameSize).coerceAtMost(bytes.size)
                val text = decodeUslt(bytes, bodyStart, frameEnd)
                if (!text.isNullOrBlank()) return text
            }
            pos += headerLen + frameSize
            if (pos > bytes.size) break
        }
        return null
    }

    private fun decodeUslt(bytes: ByteArray, start: Int, endExclusive: Int): String? {
        if (endExclusive - start < 5) return null
        val encoding = byteAt(bytes, start) // 0=Latin1, 1=UTF-16+BOM, 2=UTF-16BE, 3=UTF-8
        val contentStart = start + 4 // skip encoding + 3-byte language
        if (contentStart >= endExclusive) return null
        val slice = bytes.copyOfRange(contentStart, endExclusive)
        val charset = when (encoding) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val decoded = String(slice, charset)
        val nul = decoded.indexOf('\u0000')
        val lyrics = if (nul >= 0) decoded.substring(nul + 1) else decoded
        return lyrics.trim().ifBlank { null }
    }

    private fun extractFlac(bytes: ByteArray): String? {
        var pos = 4
        while (pos + 4 <= bytes.size) {
            val header = byteAt(bytes, pos)
            val isLast = (header and 0x80) != 0
            val type = header and 0x7F
            val blockLen = intSafe(bytes, pos + 1, 3)
            pos += 4
            if (type == 4 && pos + blockLen <= bytes.size) { // VORBIS_COMMENT
                val lyrics = parseVorbisComment(bytes, pos, pos + blockLen)
                if (!lyrics.isNullOrBlank()) return lyrics
            }
            pos += blockLen
            if (pos > bytes.size || isLast) break
        }
        return null
    }

    private fun parseVorbisComment(bytes: ByteArray, start: Int, endExclusive: Int): String? {
        var p = start
        if (endExclusive - p < 4) return null
        val vendorLen = intSafeLE(bytes, p, 4)
        p += 4 + vendorLen
        if (endExclusive - p < 4) return null
        val count = intSafeLE(bytes, p, 4)
        p += 4
        repeat(count) {
            if (endExclusive - p < 4) return@repeat
            val len = intSafeLE(bytes, p, 4)
            p += 4
            if (p + len > endExclusive) return@repeat
            val comment = String(bytes.copyOfRange(p, p + len), Charsets.UTF_8)
            p += len
            val eq = comment.indexOf('=')
            if (eq > 0) {
                val key = comment.substring(0, eq)
                if (key.equals("LYRICS", ignoreCase = true) || key.equals("UNSYNCEDLYRICS", ignoreCase = true)) {
                    val value = comment.substring(eq + 1)
                    if (value.isNotBlank()) return value
                }
            }
        }
        return null
    }

    private fun buildLines(text: String): List<LyricLine> {
        if (text.contains("[")) {
            val parsed = LrcParser.parse(text)
            if (parsed.isNotEmpty()) return parsed
        }
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { LyricLine(timeMs = 0, text = it) }
            .toList()
    }

    private fun byteAt(bytes: ByteArray, i: Int): Int = if (i < bytes.size) bytes[i].toInt() and 0xFF else -1

    private fun synchsafe(bytes: ByteArray, off: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) v = (v shl 7) or (byteAt(bytes, off + i) and 0x7F)
        return v
    }

    private fun intSafe(bytes: ByteArray, off: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) v = (v shl 8) or byteAt(bytes, off + i)
        return v
    }

    private fun intSafeLE(bytes: ByteArray, off: Int, len: Int): Int {
        var v = 0
        for (i in 0 until len) v = v or (byteAt(bytes, off + i) shl (8 * i))
        return v
    }
}
