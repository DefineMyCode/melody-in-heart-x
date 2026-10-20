package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.WordPreference
import kotlinx.coroutines.flow.Flow

/**
 * 情绪词条收听偏好仓库（计票制，2026-10-20 探索播放新增）。
 * 实现: :data WordPreferencesRepository (Room word_preferences 表)。
 */
interface WordPreferenceRepository {
    /** 全部词条偏好可观察流（写入即重发）：PlayerRuntime 探索播放缓存由此持续订阅 */
    fun observeAll(): Flow<Map<String, WordPreference>>

    suspend fun getAll(): Map<String, WordPreference>

    /**
     * 记录一首歌的收听结果：
     * @param words 该歌的情绪词条集合
     * @param liked true = 完整收听（≥30s 且 ≥1/4 总时长）→ 全部词条喜欢票 +1；
     *              false = 快速跳过 → 全部词条不喜欢票 +1。
     */
    suspend fun recordSongOutcome(words: List<String>, liked: Boolean)

    /** 清空全部词条偏好（「重新探索」：重置喜欢/不喜欢学习统计） */
    suspend fun clearAll()
}
