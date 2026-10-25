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
     * 记录一首歌的收听结果（**按词条占比加权**，2026-10-25）。
     *
     * @param votes 词条 → 票重。该歌每个情绪词条按它在整首里占的比例投票
     *   （见 [cn.com.dcsgo.mihx.domain.playback.ExplorePlaybackPolicy.voteWeightOf]）。
     *   原来是无视占比的「每个词 ±1 票」—— 那让「80% emo」和「20% emo」
     *   对「emo」这个词的贡献完全一样，主次不分。
     * @param liked true = 完整收听（≥30s 且 ≥1/4 总时长）→ 词条喜欢票 += 票重；
     *              false = 快速跳过 → 词条不喜欢票 += 票重。
     */
    suspend fun recordSongOutcome(votes: Map<String, Int>, liked: Boolean)

    /** 清空全部词条偏好（「重新探索」：重置喜欢/不喜欢学习统计） */
    suspend fun clearAll()
}
