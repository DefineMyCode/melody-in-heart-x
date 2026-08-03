package cn.com.dcsgo.mihx.app.permissions

import android.Manifest
import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RuntimePermissionPolicyTest {
    @Test
    fun notificationPermissionIsOnlyRequiredOnAndroid13AndLater() {
        assertNull(RuntimePermissionPolicy.notificationPermission(Build.VERSION_CODES.S_V2))

        val spec = RuntimePermissionPolicy.notificationPermission(Build.VERSION_CODES.TIRAMISU)

        assertEquals(Manifest.permission.POST_NOTIFICATIONS, spec?.permission)
        assertEquals("需要通知权限才能在后台显示播放控制", spec?.deniedMessage)
    }

    @Test
    fun bluetoothConnectPermissionIsOnlyRequiredOnAndroid12AndLater() {
        assertNull(RuntimePermissionPolicy.bluetoothConnectPermission(Build.VERSION_CODES.R))

        val spec = RuntimePermissionPolicy.bluetoothConnectPermission(Build.VERSION_CODES.S)

        assertEquals(Manifest.permission.BLUETOOTH_CONNECT, spec?.permission)
        assertEquals("需要蓝牙权限才能识别蓝牙播放设备", spec?.deniedMessage)
    }
}
