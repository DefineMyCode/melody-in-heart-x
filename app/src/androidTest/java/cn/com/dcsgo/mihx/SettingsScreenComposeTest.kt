package cn.com.dcsgo.mihx

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cn.com.dcsgo.mihx.feature.settings.SettingsScreen
import cn.com.dcsgo.mihx.ui.theme.MusicplayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsScreenShowsThemeAndRandomToggles() {
        val darkThemeChanges = mutableListOf<Boolean>()
        val uniformRandomChanges = mutableListOf<Boolean>()
        var bluetoothPermissionRequests = 0
        var notificationPermissionRequests = 0
        var backCount = 0

        composeRule.setContent {
            MusicplayerTheme(dynamicColor = false) {
                SettingsScreen(
                    onBack = { backCount += 1 },
                    darkThemeEnabled = false,
                    onDarkThemeEnabledChange = { darkThemeChanges += it },
                    globalUniformRandomEnabled = true,
                    onGlobalUniformRandomEnabledChange = { uniformRandomChanges += it },
                    onRequestBluetoothPermission = { bluetoothPermissionRequests += 1 },
                    onRequestNotificationPermission = { notificationPermissionRequests += 1 },
                )
            }
        }

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithText("深色主题").assertIsDisplayed()
        composeRule.onNodeWithText("使用深色外观").assertIsDisplayed()
        composeRule.onNodeWithText("全局均匀随机").assertIsDisplayed()
        composeRule.onNodeWithText("随机时优先选择原始播放次数较少的歌曲").assertIsDisplayed()
        composeRule.onNodeWithText("蓝牙播放监听").assertIsDisplayed()
        composeRule.onNodeWithText("连接蓝牙耳机或车载音频时，断开连接会自动暂停播放").assertIsDisplayed()
        composeRule.onNodeWithText("播放通知控制").assertIsDisplayed()
        composeRule.onNodeWithText("在通知栏显示后台播放控制").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("深色主题开关")
            .assertIsOff()
            .performClick()
        composeRule.onNodeWithContentDescription("全局均匀随机开关")
            .assertIsOn()
            .performClick()
        composeRule.onNodeWithContentDescription("申请蓝牙权限").performClick()
        composeRule.onNodeWithContentDescription("申请通知权限").performClick()
        composeRule.onNodeWithContentDescription("返回").performClick()

        assertEquals(listOf(true), darkThemeChanges)
        assertEquals(listOf(false), uniformRandomChanges)
        assertEquals(1, bluetoothPermissionRequests)
        assertEquals(1, notificationPermissionRequests)
        assertEquals(1, backCount)
    }
}
