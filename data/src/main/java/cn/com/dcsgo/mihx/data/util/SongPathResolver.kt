package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import cn.com.dcsgo.mihx.core.common.AppLog
import java.io.File
import java.net.URLDecoder

private const val TAG = "SongPathResolver"

/**
 * 将歌曲 URI 解析为磁盘上的真实物理路径（如 /storage/emulated/0/音乐/xxx.flac）。
 *
 * 同一声歌文件若通过不同的 SAF tree（父目录 vs 子目录）导入，其 uri 字符串
 * 前缀不同（content://...tree/<A>/... vs tree/<B>/...），但解析出的真实路径相同。
 * 文件校验去重 与 导入去重（防同一文件从多棵 tree 重复入库）都依赖该路径做归一。
 *
 * 支持：file://、MediaStore content://media、SAF document/tree URI。
 * 解析失败返回 null（调用方应退回到"仅按 uri 去重"，不因归一失败而误删）。
 */
object SongPathResolver {

    /** 解析真实路径；解析不出回退 null。绝不抛异常。 */
    fun resolveRealPath(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        return try {
            // 1. file:// 直取
            if (uri.scheme == "file") return normalize(uri.path)
            // 2. MediaStore
            if (uri.authority == "media") return resolveViaMediaStore(context, uri)
            // 3. SAF DocumentsContract（document 或 tree）
            resolveViaDocumentsContract(context, uri)
        } catch (e: Exception) {
            AppLog.warning(TAG, "resolve failed for $uri: ${e.message}")
            null
        }
    }

    private fun resolveViaMediaStore(ctx: Context, uri: Uri): String? {
        val path = try {
            ctx.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Audio.Media.DATA), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA))
                    else null
                }
        } catch (_: Exception) { null }
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        return normalize(p)
    }

    private fun resolveViaDocumentsContract(ctx: Context, uri: Uri): String? {
        // document URI: content://com.android.externalstorage.documents/document/primary:path
        // tree   URI:  content://com.android.externalstorage.documents/tree/primary:path
        return try {
            val docId = DocumentsContract.getDocumentId(uri) // "primary:Music/song.flac" 或 "primary:Music"
            mapDocIdToPath(ctx, docId) ?: fallbackFromUriPath(uri)
        } catch (_: Exception) {
            fallbackFromUriPath(uri)
        }
    }

    private fun mapDocIdToPath(ctx: Context, docId: String): String? {
        val parts = docId.split(":", limit = 2)
        if (parts.size != 2) return null
        val type = parts[0]
        val relative = parts[1].trimEnd('/')
        if (relative.isEmpty()) return null
        val full = when (type) {
            "primary" -> File(Environment.getExternalStorageDirectory(), relative).absolutePath
            else -> storageRootFor(type)?.let { File(it, relative).absolutePath }
        } ?: return null
        return normalize(full)?.takeIf { File(it).exists() }
    }

    private fun storageRootFor(type: String): String? {
        for (cand in listOf("/storage/$type", "/mnt/media_rw/$type", "/mnt/$type")) {
            if (File(cand).exists()) return cand
        }
        return null
    }

    /** 直接按 URI path 解码抽取（SAF tree 的 lastPathSegment 常编过码，如 primary%3AMusic）。 */
    private fun fallbackFromUriPath(uri: Uri): String? {
        if (uri.scheme != "content") return null
        val raw = uri.path ?: return null
        // 取出 primary 之后的部分并解码
        val decoded = try { URLDecoder.decode(raw, "UTF-8") } catch (_: Exception) { raw }
        val idx = decoded.indexOf("primary:")
        if (idx < 0) return null
        val rel = decoded.substring(idx + "primary:".length).takeIf { it.isNotBlank() } ?: return null
        val full = File(Environment.getExternalStorageDirectory(), rel).absolutePath
        return normalize(full)?.takeIf { File(it).exists() }
    }

    /** 归一：去掉尾部斜杠，统一用 / 。 */
    private fun normalize(path: String?): String? =
        path?.replace('\\', '/')?.trimEnd('/')?.takeIf { it.isNotEmpty() }
}
