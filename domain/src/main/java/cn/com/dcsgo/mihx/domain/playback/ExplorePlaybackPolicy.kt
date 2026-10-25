package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.EmotionGroup

/**
 * 探索播放的纯策略常量与判定（2026-10-20）。
 * 无 Android 依赖，可独立 JVM 单测。
 */
object ExplorePlaybackPolicy {
    /** 初始随机队列规模（点击「随心播放」生成 10 首） */
    const val INITIAL_BATCH_SIZE = 10

    /** 自动补充规模（当前歌曲是倒数第二首、播完后补 5 首） */
    const val REFILL_COUNT = 5

    /**
     * 计票门槛：词条在整首里占的比例 ≥ 此值才参与计票。
     *
     * **刻意与详情页显示用同一套门槛**（[EmotionGroup.MIN_RATIO] / [EmotionGroup.MAX_TAGS]）——
     * 用户在详情页看到 2 个标签，他跳过这首歌时否定的就该是这 2 个标签。
     *
     * 原来计票走的是**完整分布**（任何组拿到过 1 窗就算），于是：
     * 详情页显示 2 个词，跳过却投 6 个词 —— 全库平均每首 6.26 个词
     * （显示只有 2.30 个），「不喜欢」扩散速度是应有的 2.7 倍，
     * 几十首歌就能把所有词条染黑（实测 9 个 auto 组代表词全部 DISLIKE）。
     *
     * 直接引用展示层的常量，而不是抄一份数字 —— 这样两者**不可能漂移**。
     */
    const val VOTE_MIN_RATIO = EmotionGroup.MIN_RATIO

    /** 计票最多取几个词（与展示层一致） */
    const val VOTE_MAX_TAGS = EmotionGroup.MAX_TAGS

    /** 自动补充触发阈值：**刚结束**的歌曲是倒数第二首（切歌后新当前曲目之后剩余 0 首）时，播完后补 5 首。
     *  注：在 handleMediaItemEnded（切歌后）中测量——剩余 0 首 ⟺ 结束的是倒数第二首；剩余 1 首是倒数第三首，不补。 */
    const val REFILL_THRESHOLD = 0

    /**
     * 判定「没好好听」的最短时长：不足 30 秒 */
    const val MIN_LISTEN_MS = 30_000L

    /** 判定「没好好听」的完播比例：不足总时长的 1/4 */
    const val MIN_LISTEN_RATIO = 0.25f

    /**
     * 票重缩放系数：票重 = 占比 × 该系数（2026-10-25）。
     *
     * 计票原来是无视占比的「每个词 ±1 票」，于是「80% emo」和「20% emo」
     * 对「emo」这个词的贡献完全一样 —— 主次不分，而且「只是蹭到」的词
     * （占比 15%）和「这歌就是这个情绪」的词一样重。
     *
     * 用**整数票 ×100** 而不是把表列改成 REAL：
     * 整数票不需要改 Room schema 与类型判定，只需一条迁移把历史票 ×100
     * 保持尺度一致（否则新票 15~100 票会立刻淹没历史的 6 票）。
     *
     * 归属判定 [cn.com.dcsgo.mihx.core.model.WordPreference.kind] 是**相对**
     * 比较（喜欢票多 vs 不喜欢票多），票的绝对尺度不影响它。
     */
    const val VOTE_WEIGHT_SCALE = 100

    /**
     * 词条占比 → 票重（整数票）。
     *
     * 占比 <0.5% 的词拿到 0 票（等于不投）—— 那不是"这个情绪"，只是碰巧落在附近。
     */
    fun voteWeightOf(ratio: Float): Int =
        Math.round(ratio * VOTE_WEIGHT_SCALE)

    /**
     * 词条占比表 → 票重表（词 -> 整数票），票重 0 的词直接丢掉。
     *
     * 抽成纯函数是为了能单测「哪些词真的投了票、投了多少」——
     * 这正是用户最容易察觉不一致的地方（详情页显示几个词 / 跳过投几个词）。
     */
    fun votesOf(ratios: Map<String, Float>): Map<String, Int> =
        ratios.mapValues { (_, ratio) -> voteWeightOf(ratio) }
            .filterValues { it > 0 }

    /**
     * 是否判定为「快速跳过」（→ 整首歌情绪词条投不喜欢票）。
     * 规则：实际收听时长 < 30 秒 **或** < 歌曲总时长的 1/4。
     * 总时长未知（<=0）时只按 30 秒规则判定。
     */
    fun isSkipped(playedMs: Long, durationMs: Long): Boolean {
        if (playedMs >= MIN_LISTEN_MS && durationMs <= 0L) return false
        if (durationMs <= 0L) return playedMs < MIN_LISTEN_MS
        return playedMs < MIN_LISTEN_MS || playedMs < (durationMs * MIN_LISTEN_RATIO).toLong()
    }
}
