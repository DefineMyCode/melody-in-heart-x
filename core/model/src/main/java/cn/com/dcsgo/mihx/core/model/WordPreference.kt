package cn.com.dcsgo.mihx.core.model

/**
 * 情绪词条的收听偏好（计票制，2026-10-20 探索播放新增）。
 *
 * 每个词条独立累计「喜欢票 / 不喜欢票」：
 * - 一首歌被完整听完（≥30s 且 ≥1/4 总时长）→ 该歌全部情绪词条喜欢票 +1；
 * - 一首歌被快速跳过（<30s 或 <1/4 总时长）→ 该歌全部情绪词条不喜欢票 +1。
 *
 * 归属判定（[kind]）：票数多者胜；平票为中立。词条永远只出现在一侧，
 * 单次跳过不会立刻把已「喜欢」的词条翻成「不喜欢」——需要多次证据累积才翻转，
 * 天然解决「同一首歌 A B 已在喜欢、C D 已在不喜欢」的冲突场景。
 */
enum class WordPreferenceKind {
    LIKE,
    DISLIKE,
    NEUTRAL,
}

/** 单个情绪词条的收听偏好计票结果 */
data class WordPreference(
    val word: String,
    val likeVotes: Int,
    val dislikeVotes: Int,
) {
    val kind: WordPreferenceKind
        get() = when {
            likeVotes > dislikeVotes -> WordPreferenceKind.LIKE
            dislikeVotes > likeVotes -> WordPreferenceKind.DISLIKE
            else -> WordPreferenceKind.NEUTRAL
        }
}
