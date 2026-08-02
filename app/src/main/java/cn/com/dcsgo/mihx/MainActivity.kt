package cn.com.dcsgo.mihx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        super.onCreate(savedInstanceState)
        setContent {
            // P5-C4: theme mode / dynamic color are read live from Preferences DataStore so the
            // 设置 screen toggles take effect immediately app-wide.
            MelodyApp(settings = settingsRepository)
        }
    }
}
