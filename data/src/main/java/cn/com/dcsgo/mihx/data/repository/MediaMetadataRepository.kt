package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongEmotion
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.data.util.AudioMetadataExtractor
import cn.com.dcsgo.mihx.data.util.LyricsExtractor
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongMetadataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "MediaMetadataRepository"

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
                ?.copy(emotion = readEmotionSafely(song.id))
        }
    }

    /**
     * 读情绪行失败不得拖垮整个元数据读取。
     *
     * 评审 2026-10-27 · C-1：`extractFullMetadata` 自己吞异常（解析失败返回 null），
     * 但它**返回之后**才追加情绪行——`.copy(emotion = ...)` 这一步原本裸露在
     * try/catch 之外，Room 异常（SQLiteFull / SQLiteCorrupt / 磁盘 IO）会一路
     * 抛到调用方的 `LaunchedEffect` 协程里 → 未捕获异常 = 进程崩溃。
     *
     * 8 个 `loadSongInfo` 调用点里曾有 7 处裸调（仅 app 壳层 1 处做了防护），
     * 在这里兜底后全部免疫，调用方无需各自 try/catch。
     */
    private suspend fun readEmotionSafely(songId: Int): SongEmotion? = try {
        emotionRepository.get(songId)
    } catch (ce: CancellationException) {
        throw ce
    } catch (t: Throwable) {
        AppLog.warning(TAG, "songInfo: emotion read failed for song=$songId: ${t.message}", t)
        null
    }
}
