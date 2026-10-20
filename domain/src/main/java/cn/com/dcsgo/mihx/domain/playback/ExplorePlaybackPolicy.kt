package cn.com.dcsgo.mihx.domain.playback

/**
 * 探索播放的纯策略常量与判定（2026-10-20）。
 * 无 Android 依赖，可独立 JVM 单测。
 */
object ExplorePlaybackPolicy {
    /** 初始随机队列规模（点击「随心播放」生成 10 首） */
    const val INITIAL_BATCH_SIZE = 10

    /** 自动补充规模（当前歌曲是倒数第二首、播完后补 5 首） */
    const val REFILL_COUNT = 5

    /** 自动补充触发阈值：**刚结束**的歌曲是倒数第二首（切歌后新当前曲目之后剩余 0 首）时，播完后补 5 首。
     *  注：在 handleMediaItemEnded（切歌后）中测量——剩余 0 首 ⟺ 结束的是倒数第二首；剩余 1 首是倒数第三首，不补。 */
    const val REFILL_THRESHOLD = 0

    /** 判定「没好好听」的最短时长：不足 30 秒 */
    const val MIN_LISTEN_MS = 30_000L

    /** 判定「没好好听」的完播比例：不足总时长的 1/4 */
    const val MIN_LISTEN_RATIO = 0.25f

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
