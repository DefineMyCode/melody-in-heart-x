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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads lyrics for a song. Resolution order:
 *  1. A sibling `.lrc` file in the same document directory as the audio file (same base name,
 *     case-insensitive). The song URI is a persisted SAF `content://` document URI, so
 *     [DocumentFile] walks the tree without touching file paths.
 *  2. Embedded lyrics read from the audio file itself via [EmbeddedLyricsReader] (ID3v2 `USLT` /
 *     FLAC Vorbis `LYRICS`), covering tracks that ship lyrics without a separate `.lrc`.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val embeddedLyricsReader: EmbeddedLyricsReader,
) : LyricsRepository {
    override suspend fun loadForSong(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        loadExternal(song) ?: embeddedLyricsReader.read(song)
    }

    private fun loadExternal(song: Song): Lyrics? {
        val uri = song.uri ?: return null
        val doc = DocumentFile.fromSingleUri(context, uri.toUri()) ?: return null
        val parent = doc.parentFile ?: return null
        val base = doc.name?.substringBeforeLast('.') ?: return null
        val lrcDoc = parent.listFiles().firstOrNull { file ->
            file.name?.equals("$base.lrc", ignoreCase = true) == true
        } ?: return null
        val text = context.contentResolver.openInputStream(lrcDoc.uri)?.use { it.bufferedReader().readText() }
            ?: return null
        val lines = LrcParser.parse(text)
        return if (lines.isEmpty()) null else Lyrics(songId = song.id, lines = lines)
    }
}
