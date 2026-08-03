package cn.com.dcsgo.mihx.app.permissions

import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import cn.com.dcsgo.mihx.PersistableOpenDocumentTree

class PermissionCoordinator internal constructor(
    private val requestAudioFolderAccess: () -> Unit,
    private val requestNotificationPermission: (() -> Unit) -> Unit,
    private val requestBluetoothConnectPermission: (() -> Unit) -> Unit,
) {
    fun requestAudioFolderAccess() {
        requestAudioFolderAccess.invoke()
    }

    fun requestNotificationPermission(onGranted: () -> Unit = {}) {
        requestNotificationPermission.invoke(onGranted)
    }

    fun requestBluetoothConnectPermission(onGranted: () -> Unit = {}) {
        requestBluetoothConnectPermission.invoke(onGranted)
    }
}

@Composable
fun rememberPermissionCoordinator(
    onFolderSelected: (Uri) -> Unit,
    onPermissionDenied: (String) -> Unit,
): PermissionCoordinator {
    val context = LocalContext.current
    val currentOnFolderSelected = rememberUpdatedState(onFolderSelected)
    val currentOnPermissionDenied = rememberUpdatedState(onPermissionDenied)
    var pendingPermissionRequest by remember { mutableStateOf<RuntimePermissionRequest?>(null) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = PersistableOpenDocumentTree,
    ) { uri ->
        uri?.let { currentOnFolderSelected.value(it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pendingRequest = pendingPermissionRequest
        pendingPermissionRequest = null
        if (granted) {
            pendingRequest?.onGranted?.invoke()
        } else {
            currentOnPermissionDenied.value(pendingRequest?.deniedMessage ?: "权限请求被拒绝")
        }
    }

    fun requestPermissionIfNeeded(
        permission: String,
        deniedMessage: String,
        onGranted: () -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            pendingPermissionRequest = RuntimePermissionRequest(
                deniedMessage = deniedMessage,
                onGranted = onGranted,
            )
            permissionLauncher.launch(permission)
        }
    }

    return remember(context, folderPickerLauncher, permissionLauncher) {
        PermissionCoordinator(
            requestAudioFolderAccess = {
                folderPickerLauncher.launch(null)
            },
            requestNotificationPermission = { onGranted ->
                RuntimePermissionPolicy.notificationPermission()?.let { spec ->
                    requestPermissionIfNeeded(
                        permission = spec.permission,
                        deniedMessage = spec.deniedMessage,
                        onGranted = onGranted,
                    )
                } ?: onGranted()
            },
            requestBluetoothConnectPermission = { onGranted ->
                RuntimePermissionPolicy.bluetoothConnectPermission()?.let { spec ->
                    requestPermissionIfNeeded(
                        permission = spec.permission,
                        deniedMessage = spec.deniedMessage,
                        onGranted = onGranted,
                    )
                } ?: onGranted()
            },
        )
    }
}

private data class RuntimePermissionRequest(
    val deniedMessage: String,
    val onGranted: () -> Unit,
)
