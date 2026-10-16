package cn.com.dcsgo.mihx.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.util.AudioFileUtils
import cn.com.dcsgo.mihx.data.util.SongPathResolver
import java.io.File

/** 扫描到的单个音频文件（uri + 展示名 + 同名 LRC 匹配键） */
internal data class ScannedAudioFile(
    val uri: Uri,
    val displayName: String,
    val matchKey: String,
)

/**
 * m-3（评审 2026-10-15）：曲库导入的**纯扫描逻辑**，从 MusicRepository 抽出——
 * 这组函数不触碰仓库状态（lock/songs/persist），只做 SAF/File 枚举与音频/LRC 配对，
 * 独立可测。导入编排（进度/写库/歌单归属）仍由 MusicRepository.addFolder 负责。
 */
internal object LibraryImportScanner {

    private const val TAG = "LibraryImportScanner"

    private const val MAX_SCAN_DEPTH = 3

    /** 递归收集 DocumentFile 树中的音频和 LRC 文件，最多 3 层深度 */
    fun collectLibraryFiles(
        dir: DocumentFile,
        audioFiles: MutableList<ScannedAudioFile>,
        lrcFilesByKey: MutableMap<String, Uri>,
        depth: Int,
        relativeDir: String,
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        dir.listFiles().forEach { file ->
            val fileName = file.name
            when {
                file.isDirectory -> {
                    val childDir = listOf(relativeDir, fileName.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString("/")
                    collectLibraryFiles(file, audioFiles, lrcFilesByKey, depth + 1, childDir)
                }
                file.isFile && AudioFileUtils.isAudioFile(file.type, fileName) -> {
                    audioFiles += ScannedAudioFile(file.uri, file.name ?: "未知", lrcMatchKey(relativeDir, fileName))
                }
                file.isFile && fileName?.endsWith(".lrc", ignoreCase = true) == true -> {
                    lrcFilesByKey.putIfAbsent(lrcMatchKey(relativeDir, fileName), file.uri)
                }
            }
        }
    }

    /**
     * 尝试用真实路径 + java.io.File 直接递归枚举（替代 SAF 逐目录 DocumentsProvider 查询）。
     * 仅当：已授权 READ_MEDIA_AUDIO、目录位于主外部存储且可被 File API 访问。
     * @return true 表示已走 File 快扫（结果可能为空，调用方需在为空时回退 SAF）
     */
    fun tryFastFileScan(
        ctx: Context,
        treeUri: Uri,
        audioFiles: MutableList<ScannedAudioFile>,
        lrcFilesByKey: MutableMap<String, Uri>,
    ): Boolean {
        if (ctx.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            AppLog.info(TAG, "tryFastFileScan: skip, READ_MEDIA_AUDIO not granted")
            return false
        }
        val rootReal = SongPathResolver.resolveRealPath(ctx, treeUri)
        if (rootReal == null) {
            AppLog.info(TAG, "tryFastFileScan: skip, cannot resolve real path for $treeUri")
            return false
        }
        val rootFile = File(rootReal)
        if (!rootFile.isDirectory) {
            AppLog.info(TAG, "tryFastFileScan: skip, not a directory: $rootReal")
            return false
        }
        val docId = try {
            // tree URI 的 docId 要用 getTreeDocumentId（getDocumentId 只接受 document URI）
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: Exception) {
            AppLog.info(TAG, "tryFastFileScan: skip, cannot get tree docId: ${e.message}")
            return false
        }
        val storageRel = docId.substringAfter("primary:", "").trimEnd('/')
        if (storageRel.isEmpty()) {
            AppLog.info(TAG, "tryFastFileScan: skip, not primary storage: docId=$docId")
            return false
        }
        AppLog.info(TAG, "addFolder: fast File scan enabled at $rootReal")
        collectLibraryFilesFromRealPath(
            dir = rootFile,
            treeUri = treeUri,
            audioFiles = audioFiles,
            lrcFilesByKey = lrcFilesByKey,
            depth = 0,
            relativeDir = "",
            relFromStorage = storageRel,
        )
        return true
    }

    /**
     * 用 java.io.File 递归收集真实路径下的音频与 LRC，最多 3 层。
     * 每个文件仍用 tree URI + docId 构造 document URI（与 SAF 路径产出的 URI 完全一致，
     * 去重/路径归一/播放均无需改动）；lrc 读取走 ContentResolver，依赖 tree 持久化权限而非媒体权限。
     */
    private fun collectLibraryFilesFromRealPath(
        dir: File,
        treeUri: Uri,
        audioFiles: MutableList<ScannedAudioFile>,
        lrcFilesByKey: MutableMap<String, Uri>,
        depth: Int,
        relativeDir: String,
        relFromStorage: String,
    ) {
        if (depth > MAX_SCAN_DEPTH) return
        dir.listFiles()?.forEach { file ->
            val fileName = file.name ?: return@forEach
            when {
                file.isDirectory -> {
                    val childDir = listOf(relativeDir, fileName).filter { it.isNotBlank() }.joinToString("/")
                    val childRel = listOf(relFromStorage, fileName).filter { it.isNotBlank() }.joinToString("/")
                    collectLibraryFilesFromRealPath(file, treeUri, audioFiles, lrcFilesByKey, depth + 1, childDir, childRel)
                }
                file.isFile && AudioFileUtils.isAudioFile(null, fileName) -> {
                    val docUri = treeDocumentUri(treeUri, relFromStorage, fileName)
                    audioFiles += ScannedAudioFile(docUri, fileName, lrcMatchKey(relativeDir, fileName))
                }
                file.isFile && fileName.endsWith(".lrc", ignoreCase = true) -> {
                    lrcFilesByKey.putIfAbsent(
                        lrcMatchKey(relativeDir, fileName),
                        treeDocumentUri(treeUri, relFromStorage, fileName),
                    )
                }
            }
        }
    }

    /** 用 tree URI + 相对存储路径构造 document URI（纯字符串拼接，不触发 provider 查询） */
    private fun treeDocumentUri(treeUri: Uri, relFromStorage: String, fileName: String): Uri {
        val rel = if (relFromStorage.isBlank()) fileName else "$relFromStorage/$fileName"
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, "primary:$rel")
    }

    private fun lrcMatchKey(relativeDir: String, fileName: String?): String {
        val baseName = fileName.orEmpty().substringBeforeLast(".").lowercase()
        return "$relativeDir/$baseName"
    }
}
