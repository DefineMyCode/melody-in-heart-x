package cn.com.dcsgo.mihx.data

import cn.com.dcsgo.mihx.data.artwork.ArtworkStore
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.SongEntity
import cn.com.dcsgo.mihx.data.mapper.toEntity
import cn.com.dcsgo.mihx.data.metadata.MetadataExtractor
import cn.com.dcsgo.mihx.data.saf.SafImporter
import cn.com.dcsgo.mihx.domain.repository.LibraryImporter
import javax.inject.Inject

/**
 * Concrete [LibraryImporter] (plan P5-A). Orchestrates SAF enumeration → metadata extraction →
 * deduplicated Room upsert. Songs whose metadata cannot be read are still recorded but flagged
 * `playable = false`, matching the P2 window filter (uri/sampleRate aware). Embedded album art is
 * extracted once per song (plan 5B) and cached to a `content://` URI stored on the entity.
 */
class LibraryImporterImpl @Inject constructor(
    private val dao: MelodyDao,
    private val safImporter: SafImporter,
    private val metadataExtractor: MetadataExtractor,
    private val artworkStore: ArtworkStore,
) : LibraryImporter {

    override suspend fun importTree(treeUri: String, onProgress: suspend (done: Int, total: Int) -> Unit) {
        safImporter.takePersistablePermission(treeUri)
        val uris = safImporter.listAudioUris(treeUri)
        ingest(uris, onProgress)
    }

    override suspend fun importFiles(uris: List<String>, onProgress: suspend (done: Int, total: Int) -> Unit) {
        ingest(uris, onProgress)
    }

    private suspend fun ingest(uris: List<String>, onProgress: suspend (done: Int, total: Int) -> Unit) {
        uris.forEachIndexed { index, uri ->
            if (dao.getSongByUri(uri) == null) {
                val meta = metadataExtractor.extract(uri)
                val entity = if (meta != null) {
                    meta.toEntity()
                } else {
                    SongEntity(uri = uri, title = "", artist = "", album = "", playable = false)
                }
                val id = dao.upsertSong(entity)
                val artUri = artworkStore.extractAndCache(uri, id)
                if (artUri != null) dao.updateAlbumArtUri(id, artUri)
            }
            onProgress(index + 1, uris.size)
        }
    }
}
