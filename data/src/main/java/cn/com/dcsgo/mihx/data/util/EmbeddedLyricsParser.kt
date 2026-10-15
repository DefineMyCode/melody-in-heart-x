package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.LyricLine
import cn.com.dcsgo.mihx.core.model.Lyrics
import java.io.InputStream
import java.nio.charset.Charset

private const val TAG = "EmbeddedLyricsParser"

/** 歌词/标题类帧的数据上限：超过视为损坏帧（防损坏 tag 触发超大内存分配） */
private const val MAX_INTERESTING_FRAME_BYTES = 16L * 1024 * 1024

/**
 * 嵌入式歌词解析器
 *
 * 从音频文件的元数据中提取歌词，支持：
 * - MP3: ID3v2 USLT（非同步歌词）/ SYLT（同步歌词）
 * - FLAC: Vorbis Comment LYRICS 字段
 *
 * M-3（评审 2026-10-15）重写要点（均有 [EmbeddedLyricsParserTest] 回归覆盖）：
 * - 逐帧流式读取，不再受「只读前 128KB」窗口限制（大封面 APIC 之后的 USLT 也能读到）；
 * - InputStream.read/skip 循环到读满（真实流允许部分读/部分跳）；
 * - USLT/SYLT 描述符终止符按编码区分（ISO-8859-1/UTF-8 单 0x00，UTF-16 成对 0x00 且 2 字节对齐）；
 * - 编码 3 按 UTF-8 解码（旧实现按 ISO-8859-1，中文必乱码）；UTF-16 文本按 BOM 判定字节序；
 * - ID3v2.4 帧长按 syncsafe 整数读取（旧实现按普通大端，帧行走飞）；
 * - SYLT 先跳过描述符再读时间轴条目。
 *
 * 已知限制（与旧实现一致，真实曲库中罕见）：未实现整帧 unsynchronisation（v2.3 头 flags bit7）
 * 与 v2.4 逐帧 unsync 还原；数据长度指示器（v2.4 帧 flags bit1）未剥离。
 */
object EmbeddedLyricsParser {

    /**
     * 从音频文件的元数据中提取歌词
     *
     * @param context 上下文
     * @param songUri 歌曲文件 URI
     * @return 歌词对象，未找到返回 null
     */
    fun extract(context: Context, songUri: Uri): Lyrics? {
        return try {
            val fileName = getFileNameFromUri(context, songUri) ?: ""
            val extension = fileName.substringAfterLast(".").lowercase()

            AppLog.debug(TAG, "File extension: $extension")

            // 根据文件类型选择解析方法
            context.contentResolver.openInputStream(songUri)?.use { inputStream ->
                when (extension) {
                    "flac" -> parseFlacLyricsFromStream(inputStream)
                    else -> parseId3v2LyricsFromStream(inputStream)
                }
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to extract embedded lyrics: ${e.message}")
            null
        }
    }

    // ── 纯解析核心（internal：单元测试直接喂字节流，不经 ContentResolver/Context）──

    /**
     * 从流中解析 ID3v2 标签的歌词（USLT / SYLT）。
     * 逐帧流式走读：感兴趣的帧整帧读入内存解析，其余帧 skip 跳过。
     */
    internal fun parseId3v2LyricsFromStream(inputStream: InputStream): Lyrics? {
        return try {
            val header = ByteArray(10)
            if (!readFully(inputStream, header, 10)) return null

            if (header[0] != 'I'.code.toByte() ||
                header[1] != 'D'.code.toByte() ||
                header[2] != '3'.code.toByte()
            ) {
                AppLog.debug(TAG, "No ID3v2 header found")
                return null
            }

            val version = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            val tagSize = readSyncsafe32(header, 6).toLong()
            AppLog.debug(TAG, "Found ID3v2.$version, flags: $flags, tag size: $tagSize bytes")

            if (version < 2 || version > 4) {
                AppLog.debug(TAG, "Unsupported ID3v2.$version")
                return null
            }

            var consumed = 0L
            // 扩展头（flags bit6）：v2.3 长度字段不含自身，v2.4 含自身。真实文件罕见，跳过即可。
            if (flags and 0x40 != 0) {
                val extSizeBytes = ByteArray(4)
                if (!readFully(inputStream, extSizeBytes, 4)) return null
                consumed += 4
                val skipExtra = if (version == 4) {
                    (readSyncsafe32(extSizeBytes, 0).toLong() - 4).coerceAtLeast(0)
                } else {
                    readUInt32BE(extSizeBytes, 0) // v2.3：除自身外的长度
                }
                if (!skipFully(inputStream, skipExtra)) return null
                consumed += skipExtra
            }

            walkId3v2Frames(inputStream, version, tagSize, consumed)
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to parse ID3v2 lyrics: ${e.message}")
            null
        }
    }

    /** ID3v2 逐帧走读：version 2/3/4 分别为 3 字节帧 ID + 3/4/4 字节长度（v2.4 为 syncsafe） */
    private fun walkId3v2Frames(
        inputStream: InputStream,
        version: Int,
        tagSize: Long,
        startConsumed: Long,
    ): Lyrics? {
        val headerSize = if (version == 2) 6 else 10
        val idSize = if (version == 2) 3 else 4
        val frameHeader = ByteArray(headerSize)
        var consumed = startConsumed
        val lines = mutableListOf<LyricLine>()
        var title = ""
        var artist = ""

        while (consumed + headerSize <= tagSize) {
            if (!readFully(inputStream, frameHeader, headerSize)) break
            consumed += headerSize

            val frameId = String(frameHeader, 0, idSize, Charsets.US_ASCII)
            if (frameId[0] == '\u0000') break // padding：帧区结束

            val frameSize = when (version) {
                2 -> readUInt24BE(frameHeader, 3).toLong()
                4 -> readSyncsafe32(frameHeader, 4).toLong()
                else -> readUInt32BE(frameHeader, 4)
            }
            if (frameSize <= 0L || consumed + frameSize > tagSize) {
                AppLog.debug(TAG, "Corrupt frame $frameId size $frameSize at $consumed, stop")
                break
            }

            // v2.2 用 3 字符帧 ID，映射到 v2.3+ 等价语义
            val canonicalId = if (version == 2) {
                when (frameId) {
                    "ULT" -> "USLT"
                    "SLT" -> "SYLT"
                    "TT2" -> "TIT2"
                    "TP1" -> "TPE1"
                    else -> frameId
                }
            } else {
                frameId
            }

            when (canonicalId) {
                "USLT" -> {
                    // 歌词/文本帧超过 16MB 视为损坏（防损坏 tag 触发超大分配），跳过
                    if (frameSize > MAX_INTERESTING_FRAME_BYTES) break
                    val data = readBytes(inputStream, frameSize.toInt()) ?: break
                    consumed += frameSize
                    parseUsltFrameData(data)?.let { lyricsText ->
                        AppLog.debug(TAG, "Found USLT lyrics: ${lyricsText.length} chars")
                        lyricsText.split("\n").forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) lines.add(LyricLine(0, trimmed))
                        }
                    }
                }
                "SYLT" -> {
                    if (frameSize > MAX_INTERESTING_FRAME_BYTES) break
                    val data = readBytes(inputStream, frameSize.toInt()) ?: break
                    consumed += frameSize
                    val syncLyrics = parseSyltFrameData(data)
                    if (syncLyrics.isNotEmpty()) {
                        AppLog.debug(TAG, "Found SYLT lyrics: ${syncLyrics.size} lines")
                        return Lyrics(syncLyrics, title, artist)
                    }
                }
                "TIT2" -> {
                    if (frameSize > MAX_INTERESTING_FRAME_BYTES) break
                    val data = readBytes(inputStream, frameSize.toInt()) ?: break
                    consumed += frameSize
                    title = parseTextFrameData(data)
                }
                "TPE1" -> {
                    if (frameSize > MAX_INTERESTING_FRAME_BYTES) break
                    val data = readBytes(inputStream, frameSize.toInt()) ?: break
                    consumed += frameSize
                    artist = parseTextFrameData(data)
                }
                else -> {
                    if (!skipFully(inputStream, frameSize)) break
                    consumed += frameSize
                }
            }
        }

        return if (lines.isNotEmpty()) Lyrics(lines, title, artist) else null
    }

    /**
     * 从流中解析 FLAC Vorbis Comment 的 LYRICS 字段。
     * FLAC 文件结构: "fLaC" + 若干 metadata blocks（type=4 为 Vorbis Comment）。
     */
    internal fun parseFlacLyricsFromStream(inputStream: InputStream): Lyrics? {
        return try {
            val header = ByteArray(4)
            if (!readFully(inputStream, header, 4)) {
                AppLog.debug(TAG, "File too small for FLAC header")
                return null
            }
            val marker = String(header, 0, 4, Charsets.US_ASCII)
            if (marker != "fLaC") {
                AppLog.debug(TAG, "Not a FLAC file: $marker")
                return null
            }
            AppLog.debug(TAG, "Found FLAC file marker")

            val blockHeader = ByteArray(4)
            while (true) {
                if (!readFully(inputStream, blockHeader, 4)) break
                val isLastBlock = (blockHeader[0].toInt() and 0x80) != 0
                val blockType = blockHeader[0].toInt() and 0x7F
                val blockLength = readUInt24BE(blockHeader, 1)
                AppLog.debug(TAG, "FLAC block type: $blockType, length: $blockLength")

                if (blockType == 4) {
                    AppLog.debug(TAG, "Found Vorbis Comment block, length: $blockLength bytes")
                    val blockData = readBytes(inputStream, blockLength)
                        ?: return null.also { AppLog.warning(TAG, "Incomplete Vorbis Comment block") }
                    return parseVorbisCommentData(blockData)
                }
                if (!skipFully(inputStream, blockLength.toLong())) break
                if (isLastBlock) break
            }

            AppLog.debug(TAG, "No Vorbis Comment block found in FLAC file")
            null
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to parse FLAC file: ${e.message}")
            null
        }
    }

    /**
     * 解析 Vorbis Comment 数据
     * 格式: vendor_length(4 LE) + vendor_string + comment_list_length(4 LE) + [comment_length(4 LE) + comment_string]...
     */
    private fun parseVorbisCommentData(data: ByteArray): Lyrics? {
        var pos = 0

        if (pos + 4 > data.size) return null
        val vendorLen = readUInt32LE(data, pos)
        pos += 4
        if (vendorLen < 0L || pos + vendorLen > data.size) return null
        pos += vendorLen.toInt()

        if (pos + 4 > data.size) return null
        val commentCount = readUInt32LE(data, pos)
        pos += 4

        AppLog.debug(TAG, "Vorbis comments: vendor_len=$vendorLen, comment_count=$commentCount")

        val lyricsLines = mutableListOf<LyricLine>()
        var lyricsText = ""

        for (i in 0 until commentCount.coerceAtMost(10_000)) {
            if (pos + 4 > data.size) break
            val commentLen = readUInt32LE(data, pos)
            pos += 4
            if (commentLen < 0L || pos + commentLen > data.size) break

            val comment = String(data, pos, commentLen.toInt(), Charsets.UTF_8)
            pos += commentLen.toInt()
            AppLog.debug(TAG, "Vorbis comment[$i]: $comment")

            // 查找 LYRICS 字段
            if (comment.startsWith("LYRICS=", ignoreCase = true)) {
                lyricsText = comment.substringAfter("=")
                AppLog.debug(TAG, "Found LYRICS field: ${lyricsText.length} chars")
            }
        }

        if (lyricsText.isNotEmpty()) {
            // Vorbis Comment 中的 LYRICS 可能包含 LRC 时间戳格式，
            // 也可能是纯文本，优先尝试用 LrcParser 解析
            val parsed = LrcParser.parseLrcContent(lyricsText)
            if (parsed != null && parsed.lines.any { it.timeMs > 0 }) {
                AppLog.debug(TAG, "LYRICS parsed as LRC with timestamps: ${parsed.lines.size} lines")
                return parsed
            }
            // 回退：纯文本歌词，每行一个（无时间戳）
            lyricsText.split("\n").forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    lyricsLines.add(LyricLine(0, trimmed))
                }
            }
            return if (lyricsLines.isNotEmpty()) Lyrics(lyricsLines) else null
        }

        return null
    }

    // ── 帧数据解析（encoding: 0=ISO-8859-1, 1=UTF-16 w/ BOM, 2=UTF-16BE, 3=UTF-8）──

    /** 解析 USLT 帧数据：[enc][lang×3][描述符（按编码终止）][歌词文本] */
    private fun parseUsltFrameData(data: ByteArray): String? {
        if (data.size < 5) return null // enc + lang(3) + 至少一个终止符
        val encoding = data[0].toInt() and 0xFF
        val textStart = when (encoding) {
            0x01, 0x02 -> findUtf16Terminator(data, 4)?.plus(2) ?: return null
            else -> findSingleByteTerminator(data, 4)?.plus(1) ?: return null
        }
        val text = decodeText(data, textStart, data.size, encoding) ?: return null
        return normalizeLineEndings(text)
    }

    /**
     * 解析 SYLT 帧数据：[enc][lang×3][时间戳格式][内容类型][描述符（按编码终止）]
     *   之后循环：[4 字节 BE 时间戳][文本（按编码终止）]
     */
    private fun parseSyltFrameData(data: ByteArray): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        if (data.size < 6) return lines // enc + lang(3) + tsFormat + contentType
        val encoding = data[0].toInt() and 0xFF

        var pos = when (encoding) {
            0x01, 0x02 -> findUtf16Terminator(data, 4)?.plus(2) ?: return lines
            else -> findSingleByteTerminator(data, 4)?.plus(1) ?: return lines
        }

        while (pos + 4 <= data.size) {
            val timestamp = readUInt32BE(data, pos)
            pos += 4

            val textStart = pos
            val termStart: Int = when (encoding) {
                0x01, 0x02 -> findUtf16Terminator(data, pos) ?: break
                else -> findSingleByteTerminator(data, pos) ?: break
            }
            if (termStart > textStart) {
                val text = decodeText(data, textStart, termStart, encoding)
                if (!text.isNullOrBlank()) {
                    lines.add(LyricLine(timestamp, text))
                }
            }
            pos = termStart + if (encoding == 0x01 || encoding == 0x02) 2 else 1
        }

        return lines.sortedBy { it.timeMs }
    }

    /** 解析 T*** 文本帧数据：[enc][文本] */
    private fun parseTextFrameData(data: ByteArray): String {
        if (data.isEmpty()) return ""
        val encoding = data[0].toInt() and 0xFF
        return decodeText(data, 1, data.size, encoding)?.trim().orEmpty()
    }

    /**
     * 按帧编码解码 [start, end) 区间的文本；UTF-16（编码 1）自动识别并跳过 BOM。
     * 返回前裁掉 NUL padding。
     */
    private fun decodeText(data: ByteArray, start: Int, end: Int, encoding: Int): String? {
        if (start > end || end > data.size) return null
        var from = start
        val charset: Charset = when (encoding) {
            0x00 -> Charsets.ISO_8859_1
            0x03 -> Charsets.UTF_8
            0x02 -> Charsets.UTF_16BE
            0x01 -> {
                if (from + 1 < end) {
                    val b0 = data[from].toInt() and 0xFF
                    val b1 = data[from + 1].toInt() and 0xFF
                    when {
                        b0 == 0xFF && b1 == 0xFE -> {
                            from += 2
                            Charsets.UTF_16LE
                        }
                        b0 == 0xFE && b1 == 0xFF -> {
                            from += 2
                            Charsets.UTF_16BE
                        }
                        else -> Charsets.UTF_16LE // 缺 BOM：按小端兜底（Windows 生态惯例）
                    }
                } else {
                    Charsets.UTF_16LE
                }
            }
            else -> Charsets.UTF_8 // 非法编码值：按 UTF-8 尽力解码
        }
        return String(data, from, end - from, charset).trimEnd('\u0000')
    }

    private fun normalizeLineEndings(text: String): String? {
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")
        return normalized.takeIf { it.isNotEmpty() }
    }

    // ── 字节/流辅助 ──

    /** 单字节编码（0/3）的描述符终止符：第一个 0x00 的下标 */
    private fun findSingleByteTerminator(data: ByteArray, start: Int): Int? {
        for (i in start until data.size) {
            if (data[i] == 0x00.toByte()) return i
        }
        return null
    }

    /** UTF-16（1/2）的描述符/文本终止符：成对 0x00（从 start 起 2 字节对齐） */
    private fun findUtf16Terminator(data: ByteArray, start: Int): Int? {
        var i = start
        while (i + 1 < data.size) {
            if (data[i] == 0x00.toByte() && data[i + 1] == 0x00.toByte()) return i
            i += 2
        }
        return null
    }

    /** 循环读满 [len] 字节（InputStream.read 允许部分读）；流提前结束返回 false */
    private fun readFully(inputStream: InputStream, target: ByteArray, len: Int): Boolean {
        var off = 0
        while (off < len) {
            val n = inputStream.read(target, off, len - off)
            if (n < 0) return false
            off += n
        }
        return true
    }

    private fun readBytes(inputStream: InputStream, len: Int): ByteArray? {
        val buf = ByteArray(len)
        return if (readFully(inputStream, buf, len)) buf else null
    }

    /** 循环跳过 [count] 字节（InputStream.skip 允许部分跳过/返回 0）；流提前结束返回 false */
    private fun skipFully(inputStream: InputStream, count: Long): Boolean {
        var remaining = count
        val scratch = ByteArray(8192)
        while (remaining > 0) {
            val skipped = inputStream.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                // skip() 允许返回 0：退化为 read 吞字节
                val n = inputStream.read(scratch, 0, minOf(scratch.size.toLong(), remaining).toInt())
                if (n < 0) return false
                remaining -= n
            }
        }
        return true
    }

    private fun readSyncsafe32(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0x7F) shl 21) or
            ((data[offset + 1].toInt() and 0x7F) shl 14) or
            ((data[offset + 2].toInt() and 0x7F) shl 7) or
            (data[offset + 3].toInt() and 0x7F)
    }

    private fun readUInt32BE(data: ByteArray, offset: Int): Long {
        return ((data[offset].toLong() and 0xFF) shl 24) or
            ((data[offset + 1].toLong() and 0xFF) shl 16) or
            ((data[offset + 2].toLong() and 0xFF) shl 8) or
            (data[offset + 3].toLong() and 0xFF)
    }

    private fun readUInt24BE(data: ByteArray, offset: Int): Int {
        return ((data[offset].toInt() and 0xFF) shl 16) or
            ((data[offset + 1].toInt() and 0xFF) shl 8) or
            (data[offset + 2].toInt() and 0xFF)
    }

    private fun readUInt32LE(data: ByteArray, offset: Int): Long {
        return (data[offset].toLong() and 0xFF) or
            ((data[offset + 1].toLong() and 0xFF) shl 8) or
            ((data[offset + 2].toLong() and 0xFF) shl 16) or
            ((data[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to get file name from URI: ${e.message}")
            null
        }
    }
}
