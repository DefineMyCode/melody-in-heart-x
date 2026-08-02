package cn.com.dcsgo.mihx.domain.repository

/**
 * Imports audio tracks into the local library through the Storage Access Framework (SAF).
 *
 * URIs are passed as plain strings so the domain layer stays Android-free (architecture gate A3);
 * the [cn.com.dcsgo.mihx.data] module supplies the concrete implementation that touches SAF,
 * [android.media.MediaMetadataRetriever] and Room.
 *
 * Progress is reported via [onProgress] `(done, total)` so the UI can render an import bar.
 */
interface LibraryImporter {
    /** Import every audio file found under the SAF tree [treeUri]. */
    suspend fun importTree(treeUri: String, onProgress: suspend (done: Int, total: Int) -> Unit)

    /** Import an explicit list of audio file [uris]. */
    suspend fun importFiles(uris: List<String>, onProgress: suspend (done: Int, total: Int) -> Unit)
}
