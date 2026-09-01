package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.data.util.AudioMetadataExtractor
import cn.com.dcsgo.mihx.data.util.LyricsExtractor
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MediaMetadataRepository(
    private val context: Context,
    private val emotionRepository: SongEmotionRepository,
) : LyricsRepository,
    SongMetadataRepository {

    override suspend fun lyricsFor(song: Song): Lyrics = withContext(Dispatchers.IO) {
        song.uri?.let { uri -> LyricsExtractor.getLyrics(context, uri, song.lrcUri) } ?: Lyrics.EMPTY
    }

    override suspend fun songInfo(song: Song): SongInfo? = withContext(Dispatchers.IO) {
        song.uri?.let { uri ->
            AudioMetadataExtractor.extractFullMetadata(context, uri)
                ?.copy(emotion = emotionRepository.get(song.id))
        }
    }
}
