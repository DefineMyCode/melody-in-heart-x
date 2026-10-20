package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 情绪词条的收听偏好计票（2026-10-20 探索播放新增，DB version 11）。
 * 每词条一行，主键为词条名（EmotionGroup 词表内的词）。
 * 归属判定在域层 [cn.com.dcsgo.mihx.core.model.WordPreference.kind]：票多者胜，平票中立。
 */
@Entity(tableName = "word_preferences")
data class WordPreferenceEntity(
    @PrimaryKey val word: String,
    val likeVotes: Int,
    val dislikeVotes: Int,
    val updatedAt: Long,
)
