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

/**
 * Hosts the single [androidx.activity.result.ActivityResultLauncher] that fulfils permission
 * requests emitted by [PermissionCoordinator.request] (plan P3-7). Mount exactly once, high in the
 * composition (see [cn.com.dcsgo.mihx.ui.MelodyApp]).
 *
 * Requests are resolved serially: the launcher only handles one dialog at a time, which matches the
 * on-demand, user-triggered nature of these permission prompts.
 */
@Composable
fun PermissionHost() {
    var pending by remember { mutableStateOf<PermissionRequest?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending?.deferred?.complete(granted)
        pending = null
    }
    LaunchedEffect(Unit) {
        PermissionCoordinator.requests.collect { req ->
            pending = req
            launcher.launch(req.permission)
        }
    }
}
