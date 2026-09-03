package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import cn.com.dcsgo.mihx.core.common.AppLog
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.model.LyricLine
import cn.com.dcsgo.mihx.core.model.Lyrics
import java.io.File
import java.util.concurrent.TimeUnit

private const val TAG = "LrcParser"

/**
 * LRC 歌词解析器
 *
 * 负责查找同名的 .lrc 文件并解析 LRC 格式歌词内容。
 * 支持精确匹配、大小写不敏感匹配、以及文件名模糊匹配。
 */
object LrcParser {

    /**
     * 查找并解析同名的 .lrc 文件
     *
     * 搜索策略：
     * 1. 通过 DocumentFile API 在 SAF URI 的同级目录查找（精确 → 大小写不敏感 → 模糊）
     * 2. 歌曲所在目录（file:// URI，精确 → 大小写不敏感 → 模糊）
     * 3. 下载/音乐/文档目录（重复以上三种匹配）
     *
     * @param context 上下文
     * @param songUri 歌曲文件 URI
     * @return 歌词对象，未找到返回 null
     */
    fun findAndParseLrcFile(context: Context, songUri: Uri): Lyrics? {
        return try {
            val songFileName = getFileNameFromUri(context, songUri)
            if (songFileName == null) {
                AppLog.warning(TAG, "Could not get file name from URI: $songUri")
                return null
            }
            val lrcFileName = songFileName.substringBeforeLast(".") + ".lrc"
            val songNameBase = songFileName.substringBeforeLast(".").lowercase()
            AppLog.debug(TAG, "Looking for LRC file: $lrcFileName, URI scheme: ${songUri.scheme}")

            // ── 优先：通过 DocumentFile 在 SAF 同级目录查找 ──
            if (songUri.scheme == "content") {
                val result = findLrcViaSaf(context, songUri, lrcFileName, songNameBase)
                if (result != null) return result
            }

            // ── 备用：file:// URI 或固定公共目录 ──
            val searchDirs = mutableListOf<String?>()
            searchDirs.add(getParentDirFromUri(songUri))
            searchDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.absolutePath)
            searchDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath)
            searchDirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)?.absolutePath)

            val validDirs = searchDirs.filterNotNull().distinct()
            AppLog.debug(TAG, "Fallback search directories: $validDirs")

            for (dir in validDirs) {
                val result = findLrcInFileDir(dir, lrcFileName, songNameBase)
                if (result != null) return result
            }

            AppLog.debug(TAG, "No .lrc file found for: $songFileName")
            null
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to find .lrc file: ${e.message}")
            null
        }
    }

    /**
     * 通过 SAF URI 的 document ID 直接解析物理路径，在同目录下查找 .lrc 文件。
     *
     * SAF 单文件 document URI（如 content://.../document/primary:path/to/file.flac）
     * 无法通过 DocumentFile.fromSingleUri().parentFile 获取父目录。
     * 因此这里直接从 document URI 解析出物理路径，再构造同名 .lrc 路径。
     */
    private fun findLrcViaSaf(
        context: Context,
        songUri: Uri,
        lrcFileName: String,
        songNameBase: String,
    ): Lyrics? {
        // ── 方法1：从 document URI 直接解析物理路径 ──
        val realPath = extractRealPathFromDocumentUri(context, songUri)
        if (realPath != null) {
            AppLog.debug(TAG, "SAF: resolved real path: $realPath")
            val lrcPath = realPath.substringBeforeLast(".") + ".lrc"
            val lrcFile = File(lrcPath)
            if (lrcFile.exists() && lrcFile.canRead()) {
                AppLog.debug(TAG, "SAF: found LRC via real path: $lrcPath")
                return parseLrcFile(lrcFile)
            }
            // 大小写不敏感
            File(lrcPath.substringBeforeLast(".")).parentFile?.listFiles()?.forEach { f ->
                if (f.name.equals(lrcFileName, ignoreCase = true)) {
                    AppLog.debug(TAG, "SAF: found LRC (case-insensitive) via real path: ${f.absolutePath}")
                    return parseLrcFile(f)
                }
            }
            // 模糊匹配
            File(lrcPath.substringBeforeLast(".")).parentFile?.listFiles()
                ?.filter { it.extension.equals("lrc", ignoreCase = true) }
                ?.forEach { f ->
                    val lrcBase = f.nameWithoutExtension.lowercase()
                    if (lrcBase.contains(songNameBase) || songNameBase.contains(lrcBase)) {
                        AppLog.debug(TAG, "SAF: found LRC (fuzzy) via real path: ${f.absolutePath}")
                        return parseLrcFile(f)
                    }
                }
        }

        // ── 方法2：通过 tree URI + DocumentsContract 查找同目录文件 ──
        val treeUri = buildTreeUriFromDocumentUri(songUri)
        if (treeUri != null) {
            AppLog.debug(TAG, "SAF: derived tree URI: $treeUri")
            val treeDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (treeDoc != null && treeDoc.canRead()) {
                val lrcFiles = treeDoc.listFiles().filter {
                    it.name?.endsWith(".lrc", ignoreCase = true) == true
                }
                if (lrcFiles.isNotEmpty()) {
                    // 1. 精确匹配
                    lrcFiles.firstOrNull { it.name == lrcFileName }?.let { doc ->
                        AppLog.debug(TAG, "SAF: found LRC (exact) via tree: ${doc.name}")
                        return parseLrcUri(context, doc.uri)
                    }
                    // 2. 大小写不敏感
                    lrcFiles.firstOrNull { it.name.equals(lrcFileName, ignoreCase = true) }?.let { doc ->
                        AppLog.debug(TAG, "SAF: found LRC (case-insensitive) via tree: ${doc.name}")
                        return parseLrcUri(context, doc.uri)
                    }
                    // 3. 模糊匹配
                    lrcFiles.firstOrNull { doc ->
                        val lrcBase = doc.name?.substringBeforeLast(".")?.lowercase() ?: return@firstOrNull false
                        lrcBase.contains(songNameBase) || songNameBase.contains(lrcBase)
                    }?.let { doc ->
                        AppLog.debug(TAG, "SAF: found LRC (fuzzy) via tree: ${doc.name}")
                        return parseLrcUri(context, doc.uri)
                    }
                }
            }
        }

        return null
    }

    /**
     * 从 SAF document URI 解析出物理文件路径。
     *
     * document URI 格式：content://com.android.externalstorage.documents/document/primary:path/to/file.flac
     * 解析出 documentId：primary:path/to/file.flac
     * 对于 primary 存储，映射到 /storage/emulated/0/path/to/file.flac
     */
    private fun extractRealPathFromDocumentUri(context: Context, uri: Uri): String? {
        if (uri.scheme != "content") return null
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":", limit = 2)
            if (parts.size == 2) {
                val type = parts[0]
                val relativePath = parts[1]
                when (type) {
                    "primary" -> {
                        val storageDir = Environment.getExternalStorageDirectory().absolutePath
                        val fullPath = "$storageDir/$relativePath"
                        if (File(fullPath).exists()) fullPath else null
                    }
                    else -> {
                        // 其他存储类型（如 SD 卡）
                        val candidates = listOf(
                            "/storage/$type",
                            "/mnt/media_rw/$type",
                            "/mnt/$type"
                        )
                        for (cand in candidates) {
                            val fullPath = "$cand/$relativePath"
                            if (File(fullPath).exists()) return fullPath
                        }
                        null
                    }
                }
            } else null
        } catch (e: Exception) {
            AppLog.warning(TAG, "SAF: failed to extract real path from $uri: ${e.message}")
            null
        }
    }

    /**
     * 从 SAF document URI 反向推导出其所在目录的 tree URI。
     *
     * document URI：  content://.../document/primary:path/to/file.flac
     * tree URI：      content://.../tree/primary:path/to
     *
     * 适用于：album art 提取、LRC 歌词查找等需要同目录其他文件的场景。
     */
    private fun buildTreeUriFromDocumentUri(documentUri: Uri): Uri? {
        return try {
            val docId = DocumentsContract.getDocumentId(documentUri)
            val lastSlash = docId.lastIndexOf('/')
            if (lastSlash < 0) return null
            val parentDocId = docId.substring(0, lastSlash)
            DocumentsContract.buildTreeDocumentUri(
                documentUri.authority ?: return null,
                parentDocId
            )
        } catch (e: Exception) {
            AppLog.warning(TAG, "SAF: failed to build tree URI from $documentUri: ${e.message}")
            null
        }
    }

    /**
     * 通过 ContentResolver 读取 SAF URI 指向的 .lrc 文件内容并解析。
     */
    fun parseLrcUri(context: Context, uri: Uri): Lyrics? {
        return try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                parseLrcContent(reader.readText())
            }
        } catch (e: Exception) {
            AppLog.warning(TAG, "SAF: failed to read LRC from URI $uri: ${e.message}")
            null
        }
    }

    /**
     * 在普通文件目录下按精确 → 大小写不敏感 → 模糊顺序查找 .lrc 文件。
     */
    private fun findLrcInFileDir(dir: String, lrcFileName: String, songNameBase: String): Lyrics? {
        AppLog.debug(TAG, "File search in: $dir")
        val parentDir = File(dir)

        // 1. 精确匹配
        val lrcFile = File(dir, lrcFileName)
        if (lrcFile.exists() && lrcFile.canRead()) {
            AppLog.debug(TAG, "Found LRC file (exact): ${lrcFile.absolutePath}")
            return parseLrcFile(lrcFile)
        }

        // 2. 大小写不敏感匹配
        parentDir.listFiles()?.forEach { file ->
            if (file.name.equals(lrcFileName, ignoreCase = true)) {
                AppLog.debug(TAG, "Found LRC file (case-insensitive): ${file.absolutePath}")
                return parseLrcFile(file)
            }
        }

        // 3. 文件名模糊匹配
        parentDir.listFiles()?.filter { it.extension.equals("lrc", ignoreCase = true) }?.forEach { file ->
            val lrcBase = file.nameWithoutExtension.lowercase()
            if (lrcBase.contains(songNameBase) || songNameBase.contains(lrcBase)) {
                AppLog.debug(TAG, "Found LRC file (fuzzy): ${file.absolutePath}")
                return parseLrcFile(file)
            }
        }

        return null
    }

    /**
     * 解析 LRC 文件
     */
    private fun parseLrcFile(file: File): Lyrics? {
        return try {
            file.readLines().joinToString("\n").let { parseLrcContent(it) }
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to parse LRC file: ${e.message}")
            null
        }
    }

    /**
     * 解析 LRC 格式的歌词内容
     *
     * LRC 格式示例：
     * [ti:歌曲标题]
     * [ar:艺术家]
     * [00:12.34]这是第一句歌词
     * [00:34.56]这是第二句歌词
     *
     * @param content LRC 歌词文本内容
     * @return 歌词对象，解析失败返回 null
     */
    fun parseLrcContent(content: String): Lyrics? {
        if (content.isBlank()) return null

        val lines = mutableListOf<LyricLine>()
        var title = ""
        var artist = ""
        // [offset:] 全局时间偏移（毫秒）：正值表示歌词整体提前（按 LRC 规范，
        // 大多数播放器实现为 timeMs += offset），此前被直接丢弃造成系统性滞后。
        var offsetMs = 0L

        val lrcLines = content.split("\n")
        val timeRegex = Regex("""\[(\d{2}):(\d{2})(?:\.(\d{2,3}))?]""")

        for (line in lrcLines) {
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) continue

            // 解析元数据标签
            when {
                trimmedLine.startsWith("[ti:") -> {
                    title = trimmedLine.substringAfter("[ti:").substringBefore("]")
                }
                trimmedLine.startsWith("[ar:") -> {
                    artist = trimmedLine.substringAfter("[ar:").substringBefore("]")
                }
                trimmedLine.startsWith("[al:") -> {} // 专辑，忽略
                trimmedLine.startsWith("[by:") -> {} // 作者，忽略
                trimmedLine.startsWith("[offset:") -> {
                    // 2026-09-03：解析全局偏移，修正歌词整体滞后/超前
                    offsetMs = trimmedLine
                        .substringAfter("[offset:")
                        .substringBefore("]")
                        .trim()
                        .toLongOrNull() ?: 0L
                }
                else -> {
                    // 解析时间戳
                    val matches = timeRegex.findAll(trimmedLine)
                    val text = trimmedLine.replace(timeRegex, "").trim()

                    if (text.isNotEmpty()) {
                        for (match in matches) {
                            val minutes = match.groupValues[1].toLongOrNull() ?: 0
                            val seconds = match.groupValues[2].toLongOrNull() ?: 0
                            val millisStr = match.groupValues[3]
                            val millis = when (millisStr.length) {
                                2 -> (millisStr.toLongOrNull() ?: 0) * 10 // 两位数，视为厘秒
                                3 -> millisStr.toLongOrNull() ?: 0 // 三位数，毫秒
                                else -> 0
                            }

                            val timeMs = TimeUnit.MINUTES.toMillis(minutes) +
                                    TimeUnit.SECONDS.toMillis(seconds) +
                                    millis +
                                    offsetMs

                            lines.add(LyricLine(timeMs, text))
                        }
                    }
                }
            }
        }

        // 按时间排序
        val sortedLines = lines.sortedBy { it.timeMs }

        return if (sortedLines.isNotEmpty()) {
            Lyrics(sortedLines, title, artist)
        } else {
            null
        }
    }

    // ── 辅助方法 ──

    /**
     * 从 URI 获取文件名
     */
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

    /**
     * 从 file:// URI 获取父目录路径（仅用于备用的文件路径查找）
     */
    private fun getParentDirFromUri(uri: Uri): String? {
        return try {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).parentFile?.absolutePath }
            } else null
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to get parent dir from URI: ${e.message}")
            null
        }
    }
}
