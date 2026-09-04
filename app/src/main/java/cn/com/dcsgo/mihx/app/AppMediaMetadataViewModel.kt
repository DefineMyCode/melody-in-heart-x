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

    /**
     * 保存用户情绪校准(词条 -> 组锚点平均坐标). 返回 false 表示词表非法.
     * 2026-09-04: 分析失败的歌曲也允许手动标记——无分析行时仓库会创建
     * 仅含 user 字段的记录(词条即用户结论, 曲线留空待重扫)。
     */
    suspend fun saveEmotionCorrection(songId: Int, words: Set<String>): Boolean {
        // 空词 = 恢复自动词条: 清掉用户标记列(值置 NULL), 展示层回落到曲线投票。
        // user-only 行清空后是全空行会被误判已分析, 仓库侧已处理(删行)。
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
            emotionRepository.saveCorrection(songId, avg.first, avg.second, words.toList())
            true
        }
    }
}
