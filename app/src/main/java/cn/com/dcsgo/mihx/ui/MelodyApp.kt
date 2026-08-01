@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import cn.com.dcsgo.mihx.core.ui.theme.MelodyTheme
import cn.com.dcsgo.mihx.core.ui.toast.LocalToastController
import cn.com.dcsgo.mihx.core.ui.toast.ToastController
import cn.com.dcsgo.mihx.core.ui.toast.ToastHost
import cn.com.dcsgo.mihx.navigation.MelodyNavHost
import cn.com.dcsgo.mihx.permission.PermissionHost

@Composable
fun MelodyApp() {
    MelodyTheme {
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
