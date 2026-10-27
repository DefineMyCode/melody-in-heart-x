package cn.com.dcsgo.mihx.core.common

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.util.concurrent.ConcurrentHashMap

/**
 * 专辑封面 URI 的**跨进程读授权**。
 *
 * 背景：封面 URI 走 FileProvider 的 `content://`（`:data` 的 `AlbumArtProvider` 产出）。
 * 平台的 `MediaSessionRecord.sanitizeMediaMetadata()` 对 artwork URI 只做**校验**
 * （`checkGrantUriPermission`，确认 App 有权授予），**并不负责授权**——AOSP 框架侧
 * 通篇没有对 artwork URI 的 `grantUriPermission` 调用。真正让跨进程消费方
 * （SystemUI 媒体控制卡片 / 平台媒体通知 / 车机 / Android Auto）读得到封面，
 * 得由 App 显式 `grantUriPermission()`，否则对方会拿到：
 *
 * ```
 * SecurityException: Permission Denial: reading ... requires the provider be exported,
 *  or grantUriPermission()
 * ```
 *
 * **两个调用点，缺一不可**：
 * - **铸造时**（`:data` `AlbumArtProvider.toProviderUri`）—— 新封面刚落地就授权，
 *   导入期完成，不占播放路径；
 * - **发布时**（`:player` `PlaybackController` 下发 `setMediaItems` 之前）—— 存量
 *   URI（本进程没铸造过、来自上一次运行写进 Room 的）也必须授权，否则那条路径永远被拒。
 *
 * 发布时的授权**必须早于 metadata 推送**：跨进程消费方一收到推送就去读封面，
 * 等 `onMediaItemTransition` 再授权就晚了（读取先于授权 → 首帧封面空白）。
 *
 * 授权是**运行时授予**（随设备重启消失），由 uid 维度持有、不随本进程死亡而失效，
 * 所以同一 URI 授权一次即可；进程内用 [grantedUris] 去重，避免重复 IPC。
 */
object ArtworkUriAccess {

    private const val TAG = "ArtworkUriAccess"

    /**
     * 专辑封面 FileProvider 的 authority 后缀。
     * 必须与 `:data` 的 `data/src/main/AndroidManifest.xml` 里 provider 声明一致。
     */
    const val AUTHORITY_SUFFIX = ".artworkprovider"

    /**
     * 跨进程消费方：AOSP SystemUI（媒体控制卡片）与框架包（平台媒体通知）。
     * 二者在 AOSP 上都是 uid 1000(system)，平台媒体通知也在 system_server(uid 1000)
     * 里构建封面，所以一次授权同时覆盖媒体控制卡片与通知栏。
     */
    private val GRANT_TARGETS = listOf("com.android.systemui", "android")

    /** 已授权过的 URI（进程内去重，避免重复 IPC）。 */
    private val grantedUris = ConcurrentHashMap.newKeySet<String>()

    /** 已授权的 URI 数量（测试探针）。 */
    val grantedUriCount: Int get() = grantedUris.size

    /** 专辑封面 FileProvider 的 authority = <applicationId> + [AUTHORITY_SUFFIX]。 */
    fun authority(context: Context): String = context.packageName + AUTHORITY_SUFFIX

    /**
     * 该 URI 是否是**本 App 自家**封面 provider 产出的。
     *
     * 只认自家的是有意的：MediaStore 的 `content://media/external/audio/albumart/<id>`
     * 本来就能被跨进程读，给它授权既没意义，又可能因为「非本 App 拥有的 provider」
     * 触发 SecurityException 噪音。
     */
    fun isOwnArtworkUri(context: Context, uri: Uri?): Boolean {
        if (uri == null) return false
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        return uri.authority == authority(context)
    }

    /**
     * 授予单个 URI 的跨进程读权限。同一 URI 在本进程内只授权一次。
     *
     * 非自家 `content://` 的 URI 直接跳过 —— 那类 URI（MediaStore 的 albumart）
     * 本来就能被跨进程读，授权没有意义。
     */
    fun grantRead(context: Context, uri: Uri?) {
        if (!isOwnArtworkUri(context, uri)) return
        val target = uri ?: return
        if (!grantedUris.add(target.toString())) return
        for (grantTarget in GRANT_TARGETS) {
            try {
                context.grantUriPermission(grantTarget, target, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                AppLog.warning(TAG, "grantUriPermission($grantTarget) failed: ${e.message}")
            }
        }
    }

    /** 批量授予（内部按 URI 去重）。 */
    fun grantRead(context: Context, uris: Iterable<Uri?>) {
        for (uri in uris) grantRead(context, uri)
    }

    /** 测试用：清空授权去重表。 */
    fun resetGrants() {
        grantedUris.clear()
    }
}
