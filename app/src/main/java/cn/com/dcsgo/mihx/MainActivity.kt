package cn.com.dcsgo.mihx

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.ui.MelodyApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: PlayerSettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        // P5-UI: transparent system bars; the status/navigation bar ICON color is driven from
        // Compose (MelodyApp SideEffect) so it follows the APP theme (ThemeMode), not the system
        // night mode — SystemBarStyle.auto can't see the in-app theme override.
        enableEdgeToEdge()
        // Android 13+: 媒体播放需要通知权限才能展示"正在播放"通知。无通知时 OEM 省电策略
        // （MIUI 等）把前台播放服务当普通后台回收，后台播放"过一段时间就停"。启动即请求，
        // 拒绝后系统不再打扰（静默降级，仍有后台播放能力）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationPermissionLauncher =
                registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        super.onCreate(savedInstanceState)
        // P5-UI: 请求电池优化豁免（"不受电池优化限制"）。MIUI 等 OEM 的省电策略会在后台
        // 播放中强制清理前台播放服务；加入白名单后豁免。Android 标准 API，系统自带授权弹窗。
        // 用户拒绝也不致命（只是后台仍可能被回收）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                // 直接用 action 字符串（PowerManager.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                // 在某些 compileSdk 不导出符号），值为 android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS。
                val batteryIntent = Intent(
                    "android.settings.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                    Uri.parse("package:$packageName"),
                )
                runCatching { startActivity(batteryIntent) }
            }
        }
        setContent {
            // P5-C4: theme mode / dynamic color are read live from Preferences DataStore so the
            // 设置 screen toggles take effect immediately app-wide.
            MelodyApp(settings = settingsRepository)
        }
    }
}
