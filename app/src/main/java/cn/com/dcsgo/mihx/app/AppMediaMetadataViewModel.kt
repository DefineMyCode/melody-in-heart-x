package cn.com.dcsgo.mihx.app

import androidx.lifecycle.ViewModel
import cn.com.dcsgo.mihx.core.model.EmotionGroup
import cn.com.dcsgo.mihx.core.model.Lyrics
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.core.model.SongInfo
import cn.com.dcsgo.mihx.domain.repository.LyricsRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongMetadataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppMediaMetadataViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val songMetadataRepository: SongMetadataRepository,
    private val emotionRepository: SongEmotionRepository,
) : ViewModel() {
    suspend fun lyricsFor(song: Song): Lyrics = lyricsRepository.lyricsFor(song)

    suspend fun songInfo(song: Song): SongInfo? = songMetadataRepository.songInfo(song)

    /** 保存用户情绪校准(词条 -> 组锚点平均坐标). 返回 false 表示该歌未分析/词表非法. */
    fun saveEmotionCorrection(songId: Int, words: Set<String>): Boolean {
        val avg = EmotionGroup.avgOfWords(words.toList()) ?: return false
        emotionRepository.get(songId) ?: return false // 未分析的歌不给校准
        emotionRepository.saveCorrection(songId, avg.first, avg.second, words.toList())
        return true
    }
}
