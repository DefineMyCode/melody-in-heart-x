package cn.com.dcsgo.mihx.domain.importing

import android.net.Uri
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song

interface FolderImporter {
    suspend fun importFolder(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): ImportResult
}

data class ImportResult(
    val addedCount: Int,
    val songs: List<Song>,
    val playlists: List<Playlist>,
)
