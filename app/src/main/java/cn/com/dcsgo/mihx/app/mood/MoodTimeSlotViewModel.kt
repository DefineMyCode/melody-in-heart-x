package cn.com.dcsgo.mihx.app.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.core.model.emotionTagsOf
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.domain.repository.TimeSlotConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 词条 → 关联歌曲数（配置页 chip 角标），按展示词条（用户校准优先）统计 */
data class MoodTagCount(val tag: String, val songCount: Int)

/**
 * 情境化随心播放配置（时段 × 情绪词条）。
 *
 * 数据流（设计文档 §4.6）：配置列表直接观察 TimeSlotConfigRepository；
 * 词条歌曲数在进入配置页时一次性快照计算（IO 线程），不阻塞组合期（评审 M-7 模式）。
 * 保存/删除后列表自动更新（DataStore 流），并触发 :feature:player 侧缓存刷新（[onConfigsChanged]）。
 */
@HiltViewModel
class MoodTimeSlotViewModel @Inject constructor(
    private val timeSlotConfigRepository: TimeSlotConfigRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val songEmotionRepository: SongEmotionRepository,
    private val songRepository: SongRepository,
) : ViewModel() {

    val configs: StateFlow<List<TimeSlotConfig>> = timeSlotConfigRepository.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val moodTimeSlotEnabled: StateFlow<Boolean> = playerSettingsRepository.moodTimeSlotEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _tagCounts = MutableStateFlow<List<MoodTagCount>>(emptyList())
    val tagCounts: StateFlow<List<MoodTagCount>> = _tagCounts.asStateFlow()

    private val _librarySize = MutableStateFlow(0)
    val librarySize: StateFlow<Int> = _librarySize.asStateFlow()

    /** 进入配置页时调用：一次性计算词条歌曲数与曲库规模 */
    fun loadStats() {
        viewModelScope.launch {
            try {
                val (counts, size) = withContext(Dispatchers.IO) {
                    val emotions = songEmotionRepository.getAll()
                    val counts = HashMap<String, Int>()
                    emotions.values.forEach { emotion ->
                        emotionTagsOf(emotion).forEach { tag ->
                            counts[tag] = (counts[tag] ?: 0) + 1
                        }
                    }
                    val sorted = counts.entries
                        .map { MoodTagCount(it.key, it.value) }
                        .sortedByDescending { it.songCount }
                    val size = songRepository.countSongs()
                    sorted to size
                }
                _tagCounts.value = counts
                _librarySize.value = size
            } catch (e: Exception) {
                // 统计失败不阻塞配置功能，词条角标退化为 0
                _tagCounts.value = emptyList()
            }
        }
    }

    /** 保存（校验失败时回调 message 非空） */
    fun save(config: TimeSlotConfig, onSaved: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try {
                timeSlotConfigRepository.save(config)
                onConfigsChanged?.invoke()
                onSaved(true, null)
            } catch (e: IllegalArgumentException) {
                // 校验失败（重叠/零长度等）：消息即用户可读文案
                onSaved(false, e.message)
            } catch (e: Exception) {
                onSaved(false, "保存失败：${e.message ?: "未知错误"}")
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            timeSlotConfigRepository.delete(id)
            onConfigsChanged?.invoke()
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            playerSettingsRepository.setMoodTimeSlotEnabled(enabled)
        }
    }

    /** 配置变更回调（由 App 装配层注入，用于刷新 player 侧判定缓存） */
    var onConfigsChanged: (() -> Unit)? = null
}
