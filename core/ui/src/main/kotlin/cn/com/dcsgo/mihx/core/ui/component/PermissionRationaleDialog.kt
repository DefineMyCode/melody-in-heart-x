@file:Suppress("ktlint:standard:function-naming")

package cn.com.dcsgo.mihx.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/** Generic permission rationale dialog used by [cn.com.dcsgo.mihx.permission.PermissionCoordinator]. */
@Composable
fun PermissionRationaleDialog(
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onConfirm) { Text("继续") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        title = { Text(title) },
        text = { Text(body) },
    )
}
