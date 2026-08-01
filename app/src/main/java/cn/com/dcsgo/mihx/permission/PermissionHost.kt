@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.permission

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import cn.com.dcsgo.mihx.core.ui.toast.LocalToastController

/**
 * Hosts the single [androidx.activity.result.ActivityResultLauncher] that fulfils permission
 * requests emitted by [PermissionCoordinator.request] (plan P3-7). Mount exactly once, high in the
 * composition (see [cn.com.dcsgo.mihx.ui.MelodyApp]).
 *
 * Requests are resolved serially: the launcher only handles one dialog at a time, which matches the
 * on-demand, user-triggered nature of these permission prompts.
 *
 * On denial (P3-8) the coordinator's decline copy is surfaced through the global [ToastHost] so the
 * user understands which capability is now unavailable, instead of failing silently.
 */
@Composable
fun PermissionHost() {
    // `LocalToastController.current` must be read in composable scope; hoist it so the
    // non-composable permission callback lambda can call the plain `show(...)` function.
    val toastController = LocalToastController.current
    var pending by remember { mutableStateOf<PermissionRequest?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val req = pending
        if (!granted && req != null) {
            // P3-8: surface the decline copy rather than degrading silently.
            toastController.show(
                PermissionCoordinator.rationaleFor(req.permission) ?: "权限被拒绝，对应功能将不可用",
            )
        }
        req?.deferred?.complete(granted)
        pending = null
    }
    LaunchedEffect(Unit) {
        PermissionCoordinator.requests.collect { req ->
            pending = req
            launcher.launch(req.permission)
        }
    }
}
