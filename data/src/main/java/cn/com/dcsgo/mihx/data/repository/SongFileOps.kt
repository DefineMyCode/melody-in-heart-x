package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Song

private const val TAG = "SongFileOps"

/**
 * m-3（评审 2026-10-15）：歌曲文件级操作（SAF/file URI 的存在性、删除、指纹），
 * 从 MusicRepository 抽出——这组函数只依赖 Context 与入参 Song，不触碰仓库状态。
 */
internal object SongFileOps {

    /** 判断 SAF / file URI 对应的文件是否可打开（文件存在）。无上下文（纯内存测试）按乐观处理。 */
    fun fileExists(ctx: Context?, uri: Uri): Boolean {
        if (ctx == null) return true
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            AppLog.debug(TAG, "fileExists: $uri -> ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** 快速校验指纹：文件大小 + 最后修改时间。任一变化即视为可能变化。 */
    fun hasFileFingerprintChanged(ctx: Context?, song: Song): Boolean {
        val uri = song.uri ?: return false
        if (ctx == null) return false
        return try {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val size = pfd.statSize
                val mtime = try {
                    ctx.contentResolver.query(
                        uri,
                        arrayOf(android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED),
                        null, null, null,
                    )?.use { c ->
                        if (c.moveToFirst()) c.getLong(0) else 0L
                    } ?: 0L
                } catch (_: Exception) {
                    0L
                }
                // 指纹存 Room SongEntity 的 size + lastModified 列; 两者都为 null(旧数据未记录)视为未变化,
                // 避免 Quick 首跑就对全库重提取
                val stored = song.fileFingerprint
                stored == null || stored != "$size:$mtime"
            } ?: true
        } catch (e: Exception) {
            AppLog.debug(TAG, "fingerprint: ${song.id} -> ${e.javaClass.simpleName}: ${e.message}")
            true
        }
    }

    /** 删除歌曲的后备文件。M-4：无本地文件/无上下文（纯内存测试）视为「文件已缺失」。 */
    fun deleteBackingFile(ctx: Context?, song: Song): BackingFileDeleteResult {
        val uri = song.uri ?: return BackingFileDeleteResult.AlreadyMissing
        if (ctx == null) return BackingFileDeleteResult.AlreadyMissing

        return try {
            val docFile = DocumentFile.fromSingleUri(ctx, uri)
                ?: return BackingFileDeleteResult.Failure("无法定位歌曲文件，未删除")
            if (!docFile.exists()) return BackingFileDeleteResult.AlreadyMissing
            if (!docFile.canWrite()) return BackingFileDeleteResult.Failure("没有权限删除该歌曲文件")

            if (docFile.delete()) {
                AppLog.info(TAG, "deleteBackingFile: deleted backing file")
                BackingFileDeleteResult.Deleted
            } else {
                BackingFileDeleteResult.Failure("系统拒绝删除该歌曲文件")
            }
        } catch (e: SecurityException) {
            AppLog.error(TAG, "deleteBackingFile: missing permission: ${e.message}", e)
            BackingFileDeleteResult.Failure("没有权限删除该歌曲文件")
        } catch (e: Exception) {
            AppLog.error(TAG, "deleteBackingFile: failed: ${e.message}", e)
            BackingFileDeleteResult.Failure("删除歌曲文件失败：${e.message ?: "未知错误"}")
        }
    }
}
