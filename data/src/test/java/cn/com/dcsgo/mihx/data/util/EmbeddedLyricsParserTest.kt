package cn.com.dcsgo.mihx.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * M-3（评审 2026-10-15）内嵌歌词解析回归：
 *
 * 1. USLT 描述符终止符按编码区分 —— ISO-8859-1/UTF-8 是单个 0x00，UTF-16 是成对 0x00；
 *    旧实现一律找「连续两个 0x00」，空描述符时扫描直达帧尾，只解出最后 1 个字节。
 * 2. 编码 3（UTF-8）必须按 UTF-8 解码，旧实现按 ISO-8859-1，中文必乱码。
 * 3. ID3v2.4 帧长是 syncsafe 整数，旧实现按普通大端读，帧行走飞。
 * 4. 逐帧流式读取：大封面（APIC）在前且超过旧实现的 128KB 上限时，其后的 USLT 也能读到。
 * 5. InputStream.read/skip 必须循环到读满（真实流允许部分读/部分跳）。
 * 6. SYLT 必须先跳过描述符再读时间轴条目。
 */
class EmbeddedLyricsParserTest {

    // ── USLT 描述符/编码 ──

    @Test
    fun `uslt latin-1 with empty descriptor parses full text`() {
        // enc=0, lang=eng, 空描述符（单个 0x00 终止），文本 ISO-8859-1
        val data = byteArrayOf(
            0x00, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            0x00, // 空描述符终止符（单个 0x00）
        ) + "Hello LRC".toByteArray(Charsets.ISO_8859_1)
        val lyrics = parseUsltTag(data)

        assertNotNull(lyrics)
        assertEquals(listOf("Hello LRC"), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `uslt utf-8 with empty descriptor decodes chinese correctly`() {
        // enc=3（UTF-8），空描述符，中文文本
        val data = byteArrayOf(
            0x03, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            0x00,
        ) + "你好世界".toByteArray(Charsets.UTF_8)
        val lyrics = parseUsltTag(data)

        assertNotNull(lyrics)
        assertEquals(listOf("你好世界"), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `uslt latin-1 with named descriptor stops at single terminator`() {
        val data = byteArrayOf(
            0x00, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
        ) + "Desc".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x00) +
            "first line\nsecond line".toByteArray(Charsets.ISO_8859_1)
        val lyrics = parseUsltTag(data)

        assertNotNull(lyrics)
        assertEquals(listOf("first line", "second line"), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `uslt utf-16le with bom decodes chinese correctly`() {
        val text = "中文歌词"
        val descriptor = byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 0x00, 0x00) // BOM + UTF-16 NUL
        val textBytes = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)
        val data = byteArrayOf(0x01, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte()) +
            descriptor + textBytes
        val lyrics = parseUsltTag(data)

        assertNotNull(lyrics)
        assertEquals(listOf(text), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `uslt utf-16be without bom decodes correctly`() {
        val text = "同步歌词"
        val data = byteArrayOf(0x02, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte()) +
            byteArrayOf(0x00, 0x00) + // 空描述符（UTF-16 双字节 NUL）
            text.toByteArray(Charsets.UTF_16BE)
        val lyrics = parseUsltTag(data)

        assertNotNull(lyrics)
        assertEquals(listOf(text), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `uslt crlf is normalized to newline`() {
        val data = byteArrayOf(0x00, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00) +
            "a\r\nb\rc".toByteArray(Charsets.ISO_8859_1)
        val lyrics = parseUsltTag(data)

        assertEquals(listOf("a", "b", "c"), lyrics!!.lines.map { it.text })
    }

    // ── ID3v2.4 syncsafe 帧长 ──

    @Test
    fun `id3v24 syncsafe frame sizes are parsed correctly`() {
        // 帧长 ≥128 时 syncsafe 与普通大端字节不同（300 → 0x00 0x00 0x02 0x2C，大端读成 556）
        val longText = "啊".repeat(150) // UTF-8 450 字节 > 128，确保 syncsafe 字节非平凡
        val usltData = byteArrayOf(0x03, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00) +
            longText.toByteArray(Charsets.UTF_8)
        val tit2Data = byteArrayOf(0x03) + "标题".toByteArray(Charsets.UTF_8)

        val tag = tag(
            version = 4,
            frames = listOf(
                frame("TIT2", tit2Data, syncsafe = true),
                frame("USLT", usltData, syncsafe = true),
            ),
        )

        val lyrics = EmbeddedLyricsParser.parseId3v2LyricsFromStream(ByteArrayInputStream(tag))

        assertNotNull("v2.4 syncsafe 帧长应正确解析出 USLT", lyrics)
        assertEquals("标题", lyrics!!.title)
        assertEquals(longText, lyrics.lines.single().text)
    }

    // ── 大封面在前 ──

    @Test
    fun `uslt after large apic frame is still found`() {
        // 200KB 的 APIC（内嵌封面）在 USLT 之前：旧实现只读前 128KB 会漏掉 USLT
        val apicData = ByteArray(200_000) { 0x11 } // 假封面数据
        val usltData = byteArrayOf(0x00, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00) +
            "late lyrics".toByteArray(Charsets.ISO_8859_1)

        val tag = tag(
            version = 3,
            frames = listOf(frame("APIC", apicData), frame("USLT", usltData)),
        )

        val lyrics = EmbeddedLyricsParser.parseId3v2LyricsFromStream(ByteArrayInputStream(tag))

        assertNotNull("APIC 之后的 USLT 不应因读取窗口被截断而丢失", lyrics)
        assertEquals(listOf("late lyrics"), lyrics!!.lines.map { it.text })
    }

    // ── SYLT ──

    @Test
    fun `sylt with named descriptor skips descriptor before entries`() {
        val entries = byteArrayOf() +
            be32(1_000) + "第一句".toByteArray(Charsets.UTF_8) + byteArrayOf(0x00) +
            be32(2_500) + "第二句".toByteArray(Charsets.UTF_8) + byteArrayOf(0x00)
        val data = byteArrayOf(
            0x03, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(),
            0x01, // 时间戳格式：MPEG tick 约定值（解析器仅透传）
            0x01, // 内容类型：歌词
        ) + "abc".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0x00) + entries

        val tag = tag(version = 3, frames = listOf(frame("SYLT", data)))
        val lyrics = EmbeddedLyricsParser.parseId3v2LyricsFromStream(ByteArrayInputStream(tag))

        assertNotNull(lyrics)
        assertEquals(
            listOf(1_000L to "第一句", 2_500L to "第二句"),
            lyrics!!.lines.map { it.timeMs to it.text },
        )
    }

    // ── 帧后填充 ──

    @Test
    fun `tag padding after frames does not break parsing`() {
        val usltData = byteArrayOf(0x00, 'e'.code.toByte(), 'n'.code.toByte(), 'g'.code.toByte(), 0x00) +
            "padded".toByteArray(Charsets.ISO_8859_1)
        val tag = tag(version = 3, frames = listOf(frame("USLT", usltData)), paddingBytes = 2048)

        val lyrics = EmbeddedLyricsParser.parseId3v2LyricsFromStream(ByteArrayInputStream(tag))

        assertNotNull(lyrics)
        assertEquals(listOf("padded"), lyrics!!.lines.map { it.text })
    }

    // ── 非 ID3 数据 ──

    @Test
    fun `non-id3 stream returns null`() {
        val data = "hello world this is not an id3 tag".toByteArray()
        assertNull(EmbeddedLyricsParser.parseId3v2LyricsFromStream(ByteArrayInputStream(data)))
    }

    // ── FLAC ──

    @Test
    fun `flac vorbis comment lyrics with lrc timestamps parse as timed lines`() {
        val lyricsField = "[00:01.00]hello\n[00:02.50]world"
        val stream = ByteArrayInputStream(flacTag(mapOf("LYRICS" to lyricsField)))

        val lyrics = EmbeddedLyricsParser.parseFlacLyricsFromStream(stream)

        assertNotNull(lyrics)
        assertEquals(listOf(1_000L to "hello", 2_500L to "world"), lyrics!!.lines.map { it.timeMs to it.text })
    }

    @Test
    fun `flac with large picture block before vorbis comment still finds lyrics`() {
        // PICTURE block 300KB：真实流 skip 允许部分跳过，必须循环 skipFully
        val stream = ChunkedStream(flacTag(mapOf("LYRICS" to "[00:01.00]hello"), pictureBlockBytes = 300_000))

        val lyrics = EmbeddedLyricsParser.parseFlacLyricsFromStream(stream)

        assertNotNull("300KB PICTURE 之后的 Vorbis Comment 不应因 skip 不完整而丢失", lyrics)
        assertEquals(listOf("hello"), lyrics!!.lines.map { it.text })
    }

    @Test
    fun `flac without lyrics field returns null`() {
        val stream = ByteArrayInputStream(flacTag(mapOf("TITLE" to "no lyrics here")))
        assertNull(EmbeddedLyricsParser.parseFlacLyricsFromStream(stream))
    }

    // ── 构造辅助 ──

    /** 以 USLT 为唯一歌词帧的 ID3v2.3 tag，返回解析结果 */
    private fun parseUsltTag(usltFrameData: ByteArray): cn.com.dcsgo.mihx.core.model.Lyrics? {
        val tag = tag(version = 3, frames = listOf(frame("USLT", usltFrameData)))
        return EmbeddedLyricsParser.parseId3v2LyricsFromStream(ByteArrayInputStream(tag))
    }

    /** syncsafe 整数（ID3v2 头与 v2.4 帧长）：每字节 7 位有效 */
    private fun syncsafe32(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    /** 普通大端 32 位 */
    private fun be32(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte(),
    )

    /** 普通小端 32 位（Vorbis Comment 长度字段为 LE） */
    private fun le32(value: Int): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 24) and 0xFF).toByte(),
    )

    /** 构造一个 ID3v2.x 帧（v2.3/2.4 为 10 字节头） */
    private fun frame(id: String, data: ByteArray, syncsafe: Boolean = false): ByteArray {
        require(id.length == 4)
        val size = if (syncsafe) syncsafe32(data.size) else be32(data.size)
        return id.toByteArray(Charsets.US_ASCII) + size + byteArrayOf(0x00, 0x00) + data
    }

    /** 构造完整 ID3v2 tag（头 + 帧 + 可选 padding） */
    private fun tag(version: Int, frames: List<ByteArray>, paddingBytes: Int = 0): ByteArray {
        val body = frames.reduce { acc, bytes -> acc + bytes } + ByteArray(paddingBytes)
        val header = byteArrayOf(
            'I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(),
            version.toByte(), 0x00, 0x00,
        ) + syncsafe32(body.size)
        return header + body
    }

    /** 构造 FLAC 文件字节：fLaC + STREAMINFO + [PICTURE] + VORBIS_COMMENT（last） */
    private fun flacTag(fields: Map<String, String>, pictureBlockBytes: Int = 0): ByteArray {
        val streamInfo = blockHeader(type = 0, last = false, size = 34) + ByteArray(34)
        val picture = if (pictureBlockBytes > 0) {
            blockHeader(type = 6, last = false, size = pictureBlockBytes) + ByteArray(pictureBlockBytes)
        } else {
            ByteArray(0)
        }
        val vendor = "test".toByteArray(Charsets.UTF_8)
        val comments = fields.entries.map { (k, v) ->
            val content = "$k=$v".toByteArray(Charsets.UTF_8)
            le32(content.size) + content
        }
        val commentBody = le32(vendor.size) + vendor + le32(comments.size) + comments.reduce { a, b -> a + b }
        val vorbisComment = blockHeader(type = 4, last = true, size = commentBody.size) + commentBody
        return "fLaC".toByteArray(Charsets.US_ASCII) + streamInfo + picture + vorbisComment
    }

    private fun blockHeader(type: Int, last: Boolean, size: Int): ByteArray = byteArrayOf(
        (((if (last) 0x80 else 0x00) or (type and 0x7F))).toByte(),
        ((size shr 16) and 0xFF).toByte(),
        ((size shr 8) and 0xFF).toByte(),
        (size and 0xFF).toByte(),
    )
}

/** 模拟真实流行为：read/skip 每次至多处理 [chunk] 字节（FileInputStream/ContentResolver 均允许部分读写） */
private class ChunkedStream(
    private val data: ByteArray,
    private val chunk: Int = 4096,
) : InputStream() {
    private var position = 0

    override fun read(): Int = if (position < data.size) data[position++].toInt() and 0xFF else -1

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (position >= data.size) return -1
        val n = minOf(len, chunk, data.size - position)
        System.arraycopy(data, position, b, off, n)
        position += n
        return n
    }

    override fun skip(n: Long): Long {
        val s = minOf(n, chunk.toLong(), (data.size - position).toLong())
        position += s.toInt()
        return s
    }

    override fun available(): Int = minOf(chunk, data.size - position)
}
