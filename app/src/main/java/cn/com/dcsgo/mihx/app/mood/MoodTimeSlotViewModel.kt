package cn.com.dcsgo.mihx.app.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cn.com.dcsgo.mihx.core.model.RandomEnhanceMode
import cn.com.dcsgo.mihx.core.model.TimeSlotConfig
import cn.com.dcsgo.mihx.core.model.WordPreference
import cn.com.dcsgo.mihx.core.model.emotionTagsOf
import cn.com.dcsgo.mihx.domain.playback.MoodSlotPolicy
import cn.com.dcsgo.mihx.domain.repository.PlayerSettingsRepository
import cn.com.dcsgo.mihx.domain.repository.SongEmotionRepository
import cn.com.dcsgo.mihx.domain.repository.SongRepository
import cn.com.dcsgo.mihx.domain.repository.TimeSlotConfigRepository
import cn.com.dcsgo.mihx.domain.repository.WordPreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
 * M-2（评审 2026-10-15）：:feature:player 侧（PlayerRuntime）同样持续订阅仓库流，
 * 保存/删除后双方即时生效，无需手动回调刷新。
 */
@HiltViewModel
class MoodTimeSlotViewModel @Inject constructor(
    private val timeSlotConfigRepository: TimeSlotConfigRepository,
    private val playerSettingsRepository: PlayerSettingsRepository,
    private val songEmotionRepository: SongEmotionRepository,
    private val songRepository: SongRepository,
    private val wordPreferenceRepository: WordPreferenceRepository,
) : ViewModel() {

    val configs: StateFlow<List<TimeSlotConfig>> = timeSlotConfigRepository.observeConfigs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 随心播放增强运行模式（时段/探索互斥，2026-10-20） */
    val randomEnhanceMode: StateFlow<RandomEnhanceMode> = playerSettingsRepository.randomEnhanceMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RandomEnhanceMode.OFF)

    val moodTimeSlotEnabled: StateFlow<Boolean> = playerSettingsRepository.moodTimeSlotEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** 探索播放：当前喜欢/中立/不喜欢词条（按词条名排序，探索模式展示用） */
    val wordPreferences: StateFlow<List<WordPreference>> = wordPreferenceRepository.observeAll()
        .map { prefs -> prefs.values.sortedBy { it.word } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _tagCounts = MutableStateFlow<List<MoodTagCount>>(emptyList())
    val tagCounts: StateFlow<List<MoodTagCount>> = _tagCounts.asStateFlow()

    private val _librarySize = MutableStateFlow(0)
    val librarySize: StateFlow<Int> = _librarySize.asStateFlow()

    /**
     * 每首歌的词条占比表（词 -> 整曲占比）。
     *
     * 池子预览要用它算**真实**的匹配数：按词条计数相加是错的
     * （多词时同一首歌被重复计数），而且加上占比门槛后
     * "带这个词"与"会匹配上"已经不是一回事了。
     */
    @Volatile
    private var emotionRatios: Map<Int, Map<String, Float>> = emptyMap()

    /**
     * 按选中的词条算出**实际会进入随机池**的歌曲数。
     *
     * 判定与播放时完全一致（同一个 [MoodSlotPolicy.MATCH_MIN_RATIO] 门槛）——
     * 界面显示的数字必须等于实际会播到的歌数。
     */
    fun matchCount(tags: Set<String>): Int {
        if (tags.isEmpty()) return 0
        return emotionRatios.values.count { ratios ->
            ratios.any { (word, ratio) ->
                word in tags && ratio >= MoodSlotPolicy.MATCH_MIN_RATIO
            }
        }
    }

    /** 进入配置页时调用：一次性计算词条歌曲数与曲库规模 */
    fun loadStats() {
        viewModelScope.launch {
            try {
                val (counts, ratios, size) = withContext(Dispatchers.IO) {
                    val emotions = songEmotionRepository.getAll()
                    val ratios = emotions.mapValues { (_, emotion) ->
                        cn.com.dcsgo.mihx.core.model.emotionMatchRatiosOf(emotion)
                    }
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
                    Triple(sorted, ratios, size)
                }
                emotionRatios = ratios
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
        }
    }

    fun setEnabled(enabled: Boolean) {
        viewModelScope.launch {
            playerSettingsRepository.setMoodTimeSlotEnabled(enabled)
        }
    }

    /** 切换随心播放增强模式（时段/探索互斥：选一个自动关闭另一个） */
    fun setMode(mode: RandomEnhanceMode) {
        viewModelScope.launch {
            playerSettingsRepository.setRandomEnhanceMode(mode)
        }
    }

    /** 清空喜欢/不喜欢词条学习统计（「重新探索」），清空后词条流自动重发为空 */
    fun clearWordPreferences(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            wordPreferenceRepository.clearAll()
            onDone()
        }
    }
}
