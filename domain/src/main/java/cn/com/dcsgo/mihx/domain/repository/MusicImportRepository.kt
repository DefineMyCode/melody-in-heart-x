package cn.com.dcsgo.mihx.domain.repository

import android.net.Uri

interface MusicImportRepository {
    suspend fun importFolder(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): Int
}
