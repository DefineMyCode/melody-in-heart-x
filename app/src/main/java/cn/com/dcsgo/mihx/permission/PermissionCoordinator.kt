package cn.com.dcsgo.mihx.permission

import android.Manifest
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Central place for on-demand permission requests (plan P3-7).
 *
 * App-level screens request a permission via [request]; [PermissionHost] (mounted once in
 * [cn.com.dcsgo.mihx.ui.MelodyApp]) fulfils the request through an `ActivityResultLauncher` and
 * completes the awaiting [CompletableDeferred].
 *
 * Feature modules cannot depend on `:app` (architecture gate A2), so feature screens request
 * permissions directly via `androidx.activity.compose.rememberLauncherForActivityResult` instead
 * of going through this coordinator — e.g. the player screen gates its library load on
 * [Manifest.permission.READ_MEDIA_AUDIO].
 */
object PermissionCoordinator {

    val REQUIRED_PERMISSIONS: List<String> = buildList {
        add(Manifest.permission.READ_MEDIA_AUDIO)
        add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private val _requests = MutableSharedFlow<PermissionRequest>(extraBufferCapacity = 1)
    val requests: MutableSharedFlow<PermissionRequest> = _requests

    private val rationale: Map<String, String> = mapOf(
        Manifest.permission.READ_MEDIA_AUDIO to "需要读取本地音乐权限才能播放设备上的歌曲",
        Manifest.permission.POST_NOTIFICATIONS to "需要通知权限才能在通知栏显示播放控制",
        Manifest.permission.BLUETOOTH_CONNECT to "需要蓝牙权限以监听蓝牙设备状态并自动暂停",
    )

    /** User-facing reason shown before / after a request (P3-7 decline copy). */
    fun rationaleFor(permission: String): String? = rationale[permission]

    /**
     * Suspends until the permission dialog is resolved, returning whether it was granted.
     * Fire-and-forget callers should launch this inside a coroutine scope.
     */
    suspend fun request(permission: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _requests.tryEmit(PermissionRequest(permission, deferred))
        return deferred.await()
    }
}

data class PermissionRequest(
    val permission: String,
    val deferred: CompletableDeferred<Boolean>,
)
