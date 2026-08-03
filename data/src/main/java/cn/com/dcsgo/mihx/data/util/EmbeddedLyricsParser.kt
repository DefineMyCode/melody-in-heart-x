package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.LyricLine
import cn.com.dcsgo.mihx.core.model.Lyrics
import java.nio.charset.Charset

private const val TAG = "EmbeddedLyricsParser"

/**
 * 嵌入式歌词解析器
 *
 * 从音频文件的元数据中提取歌词，支持：
 * - MP3: ID3v2 USLT（非同步歌词）/ SYLT（同步歌词）
 * - FLAC: Vorbis Comment LYRICS 字段
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
            when (extension) {
                "flac" -> parseFlacVorbisComments(context, songUri)
                else -> parseId3v2Lyrics(context, songUri)
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to extract embedded lyrics: ${e.message}")
            null
        }
    }

    // ── FLAC Vorbis Comment 解析 ──

    /**
     * 解析 FLAC 文件中的 Vorbis Comment，提取 LYRICS 字段
     * FLAC 文件结构: "fLaC" + 若干 metadata blocks
     * Vorbis Comment block (type=4): vendor_length + vendor_string + comment_list_length + [comment_length + comment_string]...
     */
    private fun parseFlacVorbisComments(context: Context, songUri: Uri): Lyrics? {
        return try {
            context.contentResolver.openInputStream(songUri)?.use { inputStream ->
                val header = ByteArray(4)
                val bytesRead = inputStream.read(header)
                if (bytesRead < 4) {
                    AppLog.debug(TAG, "File too small for FLAC header")
                    return null
                }

                // 检查 FLAC 标记
                val marker = String(header, 0, 4, Charsets.US_ASCII)
                if (marker != "fLaC") {
                    AppLog.debug(TAG, "Not a FLAC file: $marker")
                    return null
                }
                AppLog.debug(TAG, "Found FLAC file marker")

                // 遍历 metadata blocks
                while (true) {
                    val blockHeader = ByteArray(4)
                    val hdrRead = inputStream.read(blockHeader)
                    if (hdrRead < 4) break

                    val isLastBlock = (blockHeader[0].toInt() and 0x80) != 0
                    val blockType = blockHeader[0].toInt() and 0x7F
                    val blockLength = ((blockHeader[1].toInt() and 0xFF) shl 16) or
                                     ((blockHeader[2].toInt() and 0xFF) shl 8) or
                                     (blockHeader[3].toInt() and 0xFF)

                    AppLog.debug(TAG, "FLAC block type: $blockType, length: $blockLength")

                    if (blockType == 4) {
                        // Vorbis Comment block
                        AppLog.debug(TAG, "Found Vorbis Comment block, length: $blockLength bytes")

                        val blockData = ByteArray(blockLength)
                        val dataRead = inputStream.read(blockData)
                        if (dataRead < blockLength) {
                            AppLog.warning(TAG, "Incomplete Vorbis Comment block")
                            return null
                        }

                        return parseVorbisCommentData(blockData, dataRead)
                    } else {
                        inputStream.skip(blockLength.toLong())
                    }

                    if (isLastBlock) break
                }

                AppLog.debug(TAG, "No Vorbis Comment block found in FLAC file")
                null
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to parse FLAC file: ${e.message}")
            null
        }
    }

    /**
     * 解析 Vorbis Comment 数据
     * 格式: vendor_length(4 LE) + vendor_string + comment_list_length(4 LE) + [comment_length(4 LE) + comment_string]...
     */
    private fun parseVorbisCommentData(data: ByteArray, length: Int): Lyrics? {
        var pos = 0

        // 读取 vendor string
        if (pos + 4 > length) return null
        val vendorLen = readUInt32LE(data, pos)
        pos += 4
        pos += vendorLen.toInt()

        // 读取 comment list length
        if (pos + 4 > length) return null
        val commentCount = readUInt32LE(data, pos)
        pos += 4

        AppLog.debug(TAG, "Vorbis comments: vendor_len=$vendorLen, comment_count=$commentCount")

        val lyricsLines = mutableListOf<LyricLine>()
        var lyricsText = ""

        for (i in 0 until commentCount) {
            if (pos + 4 > length) break
            val commentLen = readUInt32LE(data, pos)
            pos += 4

            if (pos + commentLen.toInt() > length) break
            val commentBytes = data.copyOfRange(pos, pos + commentLen.toInt())
            pos += commentLen.toInt()

            val comment = String(commentBytes, Charsets.UTF_8)
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

    // ── ID3v2 解析 ──

    /**
     * 解析 ID3v2 标签中的歌词（USLT / SYLT）
     */
    private fun parseId3v2Lyrics(context: Context, songUri: Uri): Lyrics? {
        return try {
            context.contentResolver.openInputStream(songUri)?.use { inputStream ->
                val buffer = ByteArray(128 * 1024) // 读取前 128KB，应该足够包含标签头
                val bytesRead = inputStream.read(buffer)
                if (bytesRead < 10) return null

                // 检查是否有 ID3v2 标签头 "ID3"
                if (buffer[0] != 'I'.code.toByte() ||
                    buffer[1] != 'D'.code.toByte() ||
                    buffer[2] != '3'.code.toByte()) {
                    AppLog.debug(TAG, "No ID3v2 header found")
                    return null
                }

                val version = buffer[3]
                val flags = buffer[5]
                AppLog.debug(TAG, "Found ID3v2.$version, flags: $flags")

                // 计算标签大小（同步整数，7位/字节）
                val size = ((buffer[6].toInt() and 0x7F) shl 21) or
                           ((buffer[7].toInt() and 0x7F) shl 14) or
                           ((buffer[8].toInt() and 0x7F) shl 7) or
                           (buffer[9].toInt() and 0x7F)
                AppLog.debug(TAG, "ID3v2 tag size: $size bytes")

                parseID3v2Frames(buffer, 10, bytesRead, version, size)
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to parse ID3v2 lyrics: ${e.message}")
            null
        }
    }

    /**
     * 解析 ID3v2 帧，查找 USLT 或 SYLT
     */
    private fun parseID3v2Frames(
        buffer: ByteArray,
        offset: Int,
        availableBytes: Int,
        version: Byte,
        totalTagSize: Int
    ): Lyrics? {
        var pos = offset
        val lines = mutableListOf<LyricLine>()
        var title = ""
        var artist = ""

        while (pos < availableBytes - 10) {
            val frameId = String(buffer, pos, 4, Charsets.US_ASCII)
            if (frameId[0] == '\u0000') break

            val frameSize = when (version.toInt()) {
                2 -> {
                    // ID3v2.2: 3 字节
                    ((buffer[pos + 3].toInt() and 0xFF) shl 16) or
                    ((buffer[pos + 4].toInt() and 0xFF) shl 8) or
                    (buffer[pos + 5].toInt() and 0xFF)
                }
                else -> {
                    // ID3v2.3/2.4: 4 字节
                    ((buffer[pos + 4].toInt() and 0xFF) shl 24) or
                    ((buffer[pos + 5].toInt() and 0xFF) shl 16) or
                    ((buffer[pos + 6].toInt() and 0xFF) shl 8) or
                    (buffer[pos + 7].toInt() and 0xFF)
                }
            }

            if (frameSize <= 0 || pos + frameSize > availableBytes) break

            val frameHeaderSize = if (version.toInt() == 2) 6 else 10
            val dataOffset = pos + frameHeaderSize
            val dataLength = frameSize - (frameHeaderSize - 10)

            AppLog.debug(TAG, "Frame: $frameId, size: $frameSize")

            when (frameId) {
                "USLT" -> {
                    val lyricsText = parseUSLTFrame(buffer, dataOffset, dataLength)
                    if (lyricsText != null) {
                        AppLog.debug(TAG, "Found USLT lyrics: ${lyricsText.length} chars")
                        lyricsText.split("\n").forEach { line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty()) {
                                lines.add(LyricLine(0, trimmed))
                            }
                        }
                    }
                }
                "SYLT" -> {
                    val syncLyrics = parseSYLTFrame(buffer, dataOffset, dataLength)
                    if (syncLyrics.isNotEmpty()) {
                        AppLog.debug(TAG, "Found SYLT lyrics: ${syncLyrics.size} lines")
                        return Lyrics(syncLyrics)
                    }
                }
                "TT2", "TIT2" -> title = parseTextFrame(buffer, dataOffset, dataLength)
                "TP1", "TPE1" -> artist = parseTextFrame(buffer, dataOffset, dataLength)
            }

            pos += frameHeaderSize + frameSize
        }

        return if (lines.isNotEmpty()) {
            Lyrics(lines, title, artist)
        } else null
    }

    /**
     * 解析 USLT (Unsynchronized Lyrics) 帧
     */
    private fun parseUSLTFrame(buffer: ByteArray, offset: Int, length: Int): String? {
        if (length < 4) return null

        var pos = offset + 4

        // 查找描述符终止符 (0x00)
        while (pos < offset + length - 1) {
            if (buffer[pos] == 0x00.toByte() && buffer[pos + 1] == 0x00.toByte()) {
                pos += 2
                break
            }
            pos++
        }

        if (pos >= offset + length) return null

        val encoding = buffer[offset]
        val text = try {
            when (encoding.toInt()) {
                0x00, 0x03 -> String(buffer, pos, offset + length - pos, Charsets.ISO_8859_1)
                0x01 -> {
                    val bom = if (offset + 4 < offset + length &&
                                  buffer[offset + 3] == 0x00.toByte()) "UTF-16LE" else "UTF-16BE"
                    String(buffer, pos, offset + length - pos, Charset.forName(bom))
                }
                0x02 -> String(buffer, pos, offset + length - pos, Charset.forName("UTF-16BE"))
                else -> String(buffer, pos, offset + length - pos, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            null
        }

        return text?.replace("\r\n", "\n")?.replace("\r", "\n")
    }

    /**
     * 解析 SYLT (Synchronized Lyrics) 帧
     */
    private fun parseSYLTFrame(buffer: ByteArray, offset: Int, length: Int): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        if (length < 6) return lines

        var pos = offset + 6

        while (pos < offset + length - 6) {
            if (pos + 4 > offset + length) break

            val timestamp = ((buffer[pos].toInt() and 0xFF) shl 24) or
                           ((buffer[pos + 1].toInt() and 0xFF) shl 16) or
                           ((buffer[pos + 2].toInt() and 0xFF) shl 8) or
                           (buffer[pos + 3].toInt() and 0xFF)
            pos += 4

            var textEnd = pos
            while (textEnd < offset + length && buffer[textEnd] != 0x00.toByte()) {
                textEnd++
            }

            if (textEnd > pos) {
                val text = try {
                    String(buffer, pos, textEnd - pos, Charsets.UTF_8)
                } catch (e: Exception) {
                    String(buffer, pos, textEnd - pos, Charsets.ISO_8859_1)
                }
                if (text.isNotBlank()) {
                    lines.add(LyricLine(timestamp.toLong(), text))
                }
            }

            pos = textEnd + 1
        }

        return lines.sortedBy { it.timeMs }
    }

    /**
     * 解析文本帧 (T***)
     */
    private fun parseTextFrame(buffer: ByteArray, offset: Int, length: Int): String {
        if (length < 2) return ""
        val encoding = buffer[offset]
        val textStart = offset + 1

        return try {
            when (encoding.toInt()) {
                0x00, 0x03 -> String(buffer, textStart, length - 1, Charsets.ISO_8859_1)
                0x01 -> {
                    val bom = if (textStart + 1 < offset + length &&
                                  buffer[textStart + 1] == 0x00.toByte()) "UTF-16LE" else "UTF-16BE"
                    String(buffer, textStart, length - 1, Charset.forName(bom))
                }
                else -> String(buffer, textStart, length - 1, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            ""
        }.trim()
    }

    // ── 辅助方法 ──

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
