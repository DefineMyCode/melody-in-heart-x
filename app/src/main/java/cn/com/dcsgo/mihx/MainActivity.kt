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
        // Launch goes straight into the app — no custom SplashScreen. Android 12+ renders the
        // system default splash over Theme.Melody's windowBackground (pure black in dark mode).
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            // P5-C4: theme mode / dynamic color are read live from Preferences DataStore so the
            // 设置 screen toggles take effect immediately app-wide.
            MelodyApp(settings = settingsRepository)
        }
    }
}
