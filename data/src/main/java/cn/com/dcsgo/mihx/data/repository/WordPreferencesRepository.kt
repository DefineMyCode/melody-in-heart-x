package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.model.WordPreference
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.WordPreferenceEntity
import cn.com.dcsgo.mihx.domain.repository.WordPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * 情绪词条收听偏好仓库（2026-10-20 探索播放）：Room word_preferences 表唯一数据源。
 *
 * 计票制（域层 [WordPreference.kind] 判定归属）：单曲结果只做票数累计，
 * 不直接覆盖归属——词条可同时存在于喜欢/不喜欢票（如 A B 已在喜欢、C D 已在不喜欢），
 * 票多者胜，单次跳过不会立刻翻转已喜欢的词条。
 *
 * 2026-10-25：票重按**词条在整首里占的比例**给（[ExplorePlaybackPolicy.voteWeightOf]），
 * 不再是每个词一刀切 1 票 —— 「80% emo」比「20% emo」对「emo」这个词的贡献大得多。
 * 票仍是整数（比例 ×100），所以表结构不变，只有一次历史票缩放的迁移。
 */
class WordPreferencesRepository(
    private val melodyDao: MelodyDao,
) : WordPreferenceRepository {

    override fun observeAll(): Flow<Map<String, WordPreference>> =
        melodyDao.observeAllWordPreferences().map { rows ->
            rows.associate { it.word to it.toDomain() }
        }

    override suspend fun getAll(): Map<String, WordPreference> = withContext(Dispatchers.IO) {
        melodyDao.allWordPreferences().associate { it.word to it.toDomain() }
    }

    override suspend fun recordSongOutcome(votes: Map<String, Int>, liked: Boolean) {
        val effective = votes.filterValues { it > 0 }
        if (effective.isEmpty()) return
        withContext(Dispatchers.IO) {
            val existing = melodyDao.allWordPreferences()
                .associateBy { it.word }
            val now = System.currentTimeMillis()
            val updates = effective.map { (word, weight) ->
                val old = existing[word]
                WordPreferenceEntity(
                    word = word,
                    likeVotes = (old?.likeVotes ?: 0) + if (liked) weight else 0,
                    dislikeVotes = (old?.dislikeVotes ?: 0) + if (liked) 0 else weight,
                    updatedAt = now,
                )
            }
            melodyDao.upsertWordPreferences(updates)
        }
    }

    override suspend fun clearAll() = withContext(Dispatchers.IO) {
        melodyDao.clearWordPreferences()
    }

    private fun WordPreferenceEntity.toDomain(): WordPreference =
        WordPreference(word = word, likeVotes = likeVotes, dislikeVotes = dislikeVotes)
}
