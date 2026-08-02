package cn.com.dcsgo.mihx.feature.home

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.repository.LibraryImporter
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Thin boundary between the home screen and the domain (plan P5-A).
 *
 * Implements the import/delete use-cases by delegating to domain ports only — no data module or
 * app module dependency (gate A2). The actual SAF/metadata work lives behind [LibraryImporter]
 * in the data module.
 */
class HomeFacade @Inject constructor(
    private val songRepository: SongRepository,
    private val libraryImporter: LibraryImporter,
) {
    fun library(): Flow<List<Song>> = songRepository.observeAll()

    suspend fun importTree(treeUri: String, onProgress: suspend (done: Int, total: Int) -> Unit) =
        libraryImporter.importTree(treeUri, onProgress)

    suspend fun importFiles(uris: List<String>, onProgress: suspend (done: Int, total: Int) -> Unit) =
        libraryImporter.importFiles(uris, onProgress)

    suspend fun deleteSongs(ids: List<Long>) = ids.forEach { songRepository.delete(it) }
}
