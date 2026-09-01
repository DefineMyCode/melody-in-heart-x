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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltViewModel
class AppMediaMetadataViewModel @Inject constructor(
    private val lyricsRepository: LyricsRepository,
    private val songMetadataRepository: SongMetadataRepository,
    private val emotionRepository: SongEmotionRepository,
) : ViewModel() {
    suspend fun lyricsFor(song: Song): Lyrics = lyricsRepository.lyricsFor(song)

    suspend fun songInfo(song: Song): SongInfo? = songMetadataRepository.songInfo(song)

    /** 保存用户情绪校准(词条 -> 组锚点平均坐标). 返回 false 表示该歌未分析/词表非法. */
    suspend fun saveEmotionCorrection(songId: Int, words: Set<String>): Boolean {
        // 空词 = 恢复自动词条: 清掉用户标记列(值置 NULL), 展示层回落到曲线投票
        if (words.isEmpty()) {
            return withContext(Dispatchers.Default) {
                emotionRepository.get(songId) ?: return@withContext false
                emotionRepository.clearCorrection(songId)
                true
            }
        }
        val avg = EmotionGroup.avgOfWords(words.toList()) ?: return false
        // 仓库读写是 runBlocking(IO) 桥, 搬到 Default 线程避免主线程直调卡 UI
        return withContext(Dispatchers.Default) {
            emotionRepository.get(songId) ?: return@withContext false // 未分析的歌不给校准
            emotionRepository.saveCorrection(songId, avg.first, avg.second, words.toList())
            true
        }
    }
}
