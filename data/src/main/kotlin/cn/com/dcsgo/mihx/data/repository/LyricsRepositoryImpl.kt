package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.domain.lyrics.LrcParser
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads lyrics for a song by locating a sibling `.lrc` file in the same document directory as the
 * audio file (same base name, case-insensitive). The song URI is a persisted SAF `content://`
 * document URI, so [DocumentFile] walks the tree without touching file paths.
 *
 * Embedded lyrics (a future reader for ID3 USLT / Vorbis LYRICS tags) are a follow-up; this
 * covers the far more common external `.lrc` case with zero extra dependencies.
 */
@Singleton
class LyricsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : LyricsRepository {
    override suspend fun loadForSong(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val uri = song.uri ?: return@withContext null
        val doc = DocumentFile.fromSingleUri(context, uri.toUri()) ?: return@withContext null
        val parent = doc.parentFile ?: return@withContext null
        val base = doc.name?.substringBeforeLast('.') ?: return@withContext null
        val lrcDoc = parent.listFiles().firstOrNull { file ->
            file.name?.equals("$base.lrc", ignoreCase = true) == true
        } ?: return@withContext null
        val text = context.contentResolver.openInputStream(lrcDoc.uri)?.use { it.bufferedReader().readText() }
            ?: return@withContext null
        val lines = LrcParser.parse(text)
        if (lines.isEmpty()) null else Lyrics(songId = song.id, lines = lines)
    }
}
