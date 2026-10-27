package cn.com.dcsgo.mihx.data.util

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.common.ArtworkUriAccess
import java.io.File

/**
 * 专辑封面的跨进程访问契约：本地缓存文件 ↔ FileProvider 的 `content://` URI。
 *
 * 为什么封面必须走 `content://` 而不是 `file://`：
 *
 * 平台 `MediaSessionRecord.sanitizeMediaMetadata()` 对 MediaSession 元数据里的 artwork URI
 * 只处理 `content://` 形态：`file://` 被直接 `continue` 跳过，**不做任何 URI 授权共享**。
 * 于是 file:// 形态的封面 URI 会被原样透传给各控制器（SystemUI 媒体控制卡片、通知栏、
 * Android Auto、车机…），而这些进程读不到 App 私有目录 →
 * `MediaDataManager E Unable to load bitmap` + `FileNotFoundException`。
 *
 * 改成 FileProvider 的 content:// URI 后，平台会自行完成授权共享
 * （`mUgmInternal.checkGrantUriPermission`），无需 App 手工 grantUriPermission。
 * 参考：Media3 官方 issue androidx/media#271 —— 症状与本项目日志逐字相同。
 *
 * 这套 provider 恢复自 b2d6e82（2026-08-02）的原始设计（当时目录名 cacheDir/artwork），
 * 在 53269d3「废弃」旧单模块 App 时被整体删掉，封面遂退回 file:// 私有路径。
 */
internal object AlbumArtProvider {

    private const val TAG = "AlbumArtProvider"

    /** FileProvider 的 authority 后缀。必须与 `data/src/main/AndroidManifest.xml` 的 provider 声明一致。 */
    const val AUTHORITY_SUFFIX = ArtworkUriAccess.AUTHORITY_SUFFIX

    /** 封面缓存子目录名（cacheDir 下）。必须与 `res/xml/artwork_file_paths.xml` 的 `<cache-path>` 一致。 */
    const val CACHE_DIR_NAME = "album_art"

    /** FileProvider authority = <applicationId> + [AUTHORITY_SUFFIX]。 */
    fun authority(ctx: Context): String = ArtworkUriAccess.authority(ctx)

    /** 封面缓存目录。 */
    fun cacheDir(ctx: Context): File = File(ctx.cacheDir, CACHE_DIR_NAME)

    /**
     * 本地缓存文件 → 可跨进程读取的 `content://` URI，**并同时授予跨进程读权限**。
     *
     * 授权由 [ArtworkUriAccess] 完成——平台的 `MediaSessionRecord` 只**校验**该 URI
     * 是否可被授予，**不负责授权**，缺这一步跨进程消费方会拿到
     * `SecurityException: Permission Denial: reading ... requires the provider be exported,
     *  or grantUriPermission()`。
     *
     * **这里只是「铸造时」的授权点**：存量 URI（本进程没铸造过、来自上一次运行写进 Room 的）
     * 由 `:player` 在**发布媒体项之前**补一次授权，两处共用 [ArtworkUriAccess] 的去重表。
     *
     * @return null 表示 FileProvider 不可用（provider 未声明 / 文件不在允许的根目录下），
     *   调用方需自行决定兜底策略。
     */
    fun toProviderUri(ctx: Context, file: File): Uri? {
        val result = runCatching { FileProvider.getUriForFile(ctx, authority(ctx), file) }
        result.exceptionOrNull()?.let {
            AppLog.warning(TAG, "toProviderUri failed for ${file.name}: ${it.message}")
        }
        val uri = result.getOrNull() ?: return null
        ArtworkUriAccess.grantRead(ctx, uri)
        return uri
    }

    /**
     * provider URI → 本地缓存文件。
     *
     * @return null 表示不是本 provider 产出的 URI（例如 MediaStore 的
     *   `content://media/external/audio/albumart/<id>`，它本来就能跨进程读）。
     */
    fun fileOf(ctx: Context, uri: Uri): File? {
        if (uri.authority != authority(ctx)) return null
        val segments = uri.pathSegments ?: return null
        if (segments.size != 2 || segments[0] != CACHE_DIR_NAME) return null
        return File(cacheDir(ctx), segments[1])
    }

    /**
     * 旧版本遗留的 `file://` 缓存 URI → 对应的本地缓存文件。
     *
     * 只认**自家缓存目录**下的 file://，避免把任意本地路径误当封面。
     * 不判断文件是否存在——存在与否由调用方决定（存在则迁移，不存在则摘掉悬空 URI）。
     */
    fun legacyCacheFileOf(ctx: Context, uri: Uri): File? {
        if (uri.scheme != "file") return null
        val path = uri.path ?: return null
        val file = File(path)
        val root = cacheDir(ctx)
        val sameDir = runCatching { file.parentFile?.canonicalPath == root.canonicalPath }.getOrDefault(false)
        return if (sameDir) file else null
    }
}
