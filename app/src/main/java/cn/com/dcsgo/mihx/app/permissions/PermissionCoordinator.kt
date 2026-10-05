package cn.com.dcsgo.mihx.app.permissions

import android.Manifest
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
            if (pendingRequest?.onDenied != null) {
                pendingRequest.onDenied.invoke()
            } else {
                currentOnPermissionDenied.value(pendingRequest?.deniedMessage ?: "权限请求被拒绝")
            }
        }
    }

    fun requestPermissionIfNeeded(
        permission: String,
        deniedMessage: String,
        onGranted: () -> Unit,
        onDenied: (() -> Unit)? = null,
    ) {
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            onGranted()
        } else {
            pendingPermissionRequest = RuntimePermissionRequest(
                deniedMessage = deniedMessage,
                onGranted = onGranted,
                onDenied = onDenied,
            )
            permissionLauncher.launch(permission)
        }
    }

    return remember(context, folderPickerLauncher, permissionLauncher) {
        PermissionCoordinator(
            requestAudioFolderAccess = {
                // READ_MEDIA_AUDIO 仅用于加速扫描，SAF 导入路径不依赖它。故静默请求：
                // 无论是否授权都打开目录选择器；拒绝时不弹"权限被拒"提示，避免打扰。
                requestPermissionIfNeeded(
                    permission = Manifest.permission.READ_MEDIA_AUDIO,
                    deniedMessage = "未授予音频权限，仍可通过文件夹选择导入（扫描较慢）",
                    onGranted = { folderPickerLauncher.launch(null) },
                    onDenied = { folderPickerLauncher.launch(null) },
                )
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
    val onDenied: (() -> Unit)? = null,
)
