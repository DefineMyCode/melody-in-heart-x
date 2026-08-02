@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.dcsgo.mihx.core.ui.theme.MelodyTheme
import cn.com.dcsgo.mihx.core.ui.toast.LocalToastController
import cn.com.dcsgo.mihx.core.ui.toast.ToastController
import cn.com.dcsgo.mihx.core.ui.toast.ToastHost
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.ThemeMode
import cn.com.dcsgo.mihx.navigation.MelodyNavHost
import cn.com.dcsgo.mihx.permission.PermissionHost

@Composable
fun MelodyApp(settings: PlayerSettingsRepository) {
    // P5-C4: 设置 screen toggles drive the app theme live — dark/light follows ThemeMode (with
    // SYSTEM falling back to the system flag), dynamic color honors the wallpaper scheme toggle.
    val themeMode by settings.observeThemeMode()
        .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val dynamicColor by settings.observeDynamicColorEnabled()
        .collectAsStateWithLifecycle(initialValue = false)
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // P5-UI: status/navigation bar ICON color follows the APP theme (not the system night mode).
    // Dark theme -> light icons (readable on the black bars); light theme -> dark icons. This is
    // driven from Compose so switching 主题 in 设置 takes effect immediately.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MelodyTheme(darkTheme = darkTheme, dynamicColor = dynamicColor) {
        // P3-8: one process-wide ToastController, exposed through CompositionLocal so any screen
        // (including feature modules, which cannot depend on :app) can raise a top toast.
        val toastController = remember { ToastController() }
        CompositionLocalProvider(LocalToastController provides toastController) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MelodyNavHost()
                    // Top-anchored overlay; stacked toasts appear above the nav scaffold.
                    ToastHost(toastController, Modifier.align(Alignment.TopCenter))
                }
            }
            // P3-7: single host that fulfils on-demand permission requests from :app-level screens.
            PermissionHost()
        }
    }
}
