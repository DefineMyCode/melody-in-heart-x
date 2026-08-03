package cn.com.dcsgo.mihx.data.player

import android.net.Uri
import cn.com.dcsgo.mihx.domain.importing.FolderImporter
import cn.com.dcsgo.mihx.domain.importing.ImportResult
import cn.com.dcsgo.mihx.domain.repository.MusicImportRepository
import cn.com.dcsgo.mihx.domain.repository.PlaylistRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository

class ImportCoordinator(
    private val importRepository: MusicImportRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
) : FolderImporter {
    override suspend fun importFolder(
        treeUri: Uri,
        onProgress: (processed: Int, total: Int) -> Unit,
    ): ImportResult {
        val addedCount = importRepository.importFolder(treeUri, onProgress)
        return ImportResult(
            addedCount = addedCount,
            songs = songRepository.observeSongsSnapshot(),
            playlists = playlistRepository.getPlaylists(),
        )
    }
}
