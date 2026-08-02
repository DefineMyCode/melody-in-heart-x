package cn.com.dcsgo.mihx.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.collections.ArrayDeque

/**
 * Storage Access Framework helper (plan P5-A). Enumerates audio files under a user-picked tree
 * and persists the URI permission so the library survives process death.
 *
 * Kept in the data module (never in a feature module) so the home feature only depends on the
 * LibraryImporter port from the domain module (gate A2).
 */
class SafImporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Recursively collect content-URI strings of every audio file under the given tree URI. */
    suspend fun listAudioUris(treeUri: String): List<String> = withContext(Dispatchers.IO) {
        val root = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return@withContext emptyList()
        val out = mutableListOf<String>()
        val stack = ArrayDeque<DocumentFile>().apply { add(root) }
        while (stack.isNotEmpty()) {
            val file = stack.removeLast()
            if (file.isDirectory) {
                stack.addAll(file.listFiles())
            } else if (file.isFile && file.type?.startsWith("audio/") == true) {
                file.uri?.toString()?.let(out::add)
            }
        }
        out
    }

    /** Persist read/write permission for the given tree URI so it stays accessible after restart. */
    fun takePersistablePermission(treeUri: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { AppLogger.w(TAG, "takePersistableUriPermission failed: ${it.message}") }
    }

    private companion object {
        const val TAG = "SafImporter"
    }
}
