package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.lyrics.EmbeddedLyricsReader
import cn.com.dcsgo.mihx.domain.lyrics.LrcParser
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads lyrics for a song. Resolution order:
 *  1. A sibling `.lrc` file in the same document directory as the audio file (same base name,
 *     case-insensitive). The song URI is a persisted SAF `content://` document URI.
 *  2. Embedded lyrics read from the audio file itself via [EmbeddedLyricsReader] (ID3v2 `USLT` /
 *     FLAC Vorbis `LYRICS`), covering tracks that ship lyrics without a separate `.lrc`.
 *
 * Process-wide memory cache: reading the audio header (or walking a document directory) is an
 * expensive SAF operation, and the lyrics screen enters with an empty state while it runs. First
 * load resolves once and every later entry returns from the cache instantly.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddedLyricsReader: EmbeddedLyricsReader,
) : LyricsRepository {

    private val cache = ConcurrentHashMap<Long, Lyrics>()

    override suspend fun loadForSong(song: Song): Lyrics? {
        cache[song.id]?.let { return it }
        return withContext(Dispatchers.IO) {
            val lyrics = loadExternal(song) ?: embeddedLyricsReader.read(song)
            if (lyrics != null) cache[song.id] = lyrics
            lyrics
        }
    }

    private fun loadExternal(song: Song): Lyrics? {
        val uri = song.uri ?: return null
        val text = readSiblingLrc(uri) ?: return null
        val lines = LrcParser.parse(text)
        return if (lines.isEmpty()) null else Lyrics(songId = song.id, lines = lines)
    }

    private fun readSiblingLrc(uri: String): String? {
        // Try 1: walk the document parent (works when the parent tree is browseable / authorized).
        runCatching {
            val doc = DocumentFile.fromSingleUri(context, uri.toUri())
            val base = doc?.name?.substringBeforeLast('.')
            if (base != null) {
                doc.parentFile?.listFiles()?.firstOrNull { file ->
                    file.name?.equals("$base.lrc", ignoreCase = true) == true
                }?.uri?.let { lrcUri ->
                    return context.contentResolver.openInputStream(lrcUri)?.use { it.bufferedReader().readText() }
                }
            }
        }
        // Try 2: build the sibling document URI directly by swapping the extension
        // (content://.../document/primary:Music/sub/song.mp3 -> .../song.lrc). This covers
        // folder-import authorizations (the whole tree is granted); single-file grants that
        // exclude the .lrc throw SecurityException and fall through to null.
        val dot = uri.lastIndexOf('.')
        if (dot <= uri.lastIndexOf('/')) return null
        val lrcUri = uri.substring(0, dot) + ".lrc"
        return runCatching {
            context.contentResolver.openInputStream(lrcUri.toUri())?.use { it.bufferedReader().readText() }
        }.getOrNull()
    }
}
