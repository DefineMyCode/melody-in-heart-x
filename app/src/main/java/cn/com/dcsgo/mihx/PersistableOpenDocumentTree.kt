package cn.com.dcsgo.mihx

import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

/**
 * 自定义 OpenDocumentTree contract
 *
 * 在标准的 OpenDocumentTree 基础上增加 [Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION]，
 * 让返回的 folder tree URI 权限在 app 重启后依然有效。
 *
 * 使用方式：
 * ```kotlin
 * val folderPickerLauncher = rememberLauncherForActivityResult(
 *     PersistableOpenDocumentTree
 * ) { uri -> ... }
 * ```
 */
object PersistableOpenDocumentTree : ActivityResultContracts.OpenDocumentTree() {
    override fun createIntent(ctx: android.content.Context, input: android.net.Uri?) =
        super.createIntent(ctx, input).apply {
            addFlags(
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
}
