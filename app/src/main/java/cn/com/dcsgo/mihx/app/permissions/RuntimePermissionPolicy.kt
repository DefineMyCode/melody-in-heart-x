package cn.com.dcsgo.mihx.app.permissions

import android.Manifest
import android.os.Build

data class RuntimePermissionSpec(
    val permission: String,
    val deniedMessage: String,
)

object RuntimePermissionPolicy {
    fun notificationPermission(sdkInt: Int = Build.VERSION.SDK_INT): RuntimePermissionSpec? {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            RuntimePermissionSpec(
                permission = Manifest.permission.POST_NOTIFICATIONS,
                deniedMessage = "需要通知权限才能在后台显示播放控制",
            )
        } else {
            null
        }
    }

    fun bluetoothConnectPermission(sdkInt: Int = Build.VERSION.SDK_INT): RuntimePermissionSpec? {
        return if (sdkInt >= Build.VERSION_CODES.S) {
            RuntimePermissionSpec(
                permission = Manifest.permission.BLUETOOTH_CONNECT,
                deniedMessage = "需要蓝牙权限才能识别蓝牙播放设备",
            )
        } else {
            null
        }
    }
}
