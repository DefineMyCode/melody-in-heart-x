package cn.com.dcsgo.mihx.permission

import android.Manifest
import android.os.Build

/**
 * Central place for on-demand permission requests.
 * Phase 3 fleshes out the runtime coordination + decline copy + degrade paths.
 */
object PermissionCoordinator {
    val REQUIRED_PERMISSIONS: List<String> =
        buildList {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
}
