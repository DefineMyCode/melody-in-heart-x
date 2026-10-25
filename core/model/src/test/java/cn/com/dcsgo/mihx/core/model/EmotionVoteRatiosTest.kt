package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探索计票用的词条分布（2026-10-25 第二次改造）。
 *
 * 背景：详情页显示 2 个标签，跳过这歌却投了 6 个「不喜欢」——
 * 因为**显示卡 15% 门槛**，**计票用的是完整分布**。
 * 完整分布对**匹配**是对的（目标词排第 5 也得匹配得上），
 * 但对**计票**是错的：一首 97% 的时间都不是「治愈」的歌，
 * 不该因为 3% 落在「治愈」附近就被记一笔「不喜欢治愈」。
 *
 * 实测全库 1095 首：完整分布平均每首 6.26 个词，卡 15% 后 2.31 个
 * （正好等于详情页显示数），且**没有一首歌投不出票**。
 */
class EmotionVoteRatiosTest {

    private val current = EmotionGroup.anchorsFor(EmotionGroup.CURRENT_MODEL_VERSION)

    private fun emotion(
        curve: List<Pair<Float, Float>>,
        userTags: List<String> = emptyList(),
    ) = SongEmotion(
        songId = 1,
        valence = 0f,
        arousal = 0f,
        curve = curve,
        peakSec = 0f,
        windowsAnalyzed = curve.size,
        durationSec = 60f,
        modelVersion = EmotionGroup.CURRENT_MODEL_VERSION,
        analyzedAt = 0L,
        userTags = userTags,
    )

    @Test
    fun `only the dominant words get a vote`() {
        // 8 窗元气 + 1 窗燃 + 1 窗静谧 -> 燃/静谧 各 10%，低于 15% 门槛
        val curve = List(8) { current.getValue(EmotionGroup.BRIGHT) } +
            List(1) { current.getValue(EmotionGroup.RAGE) } +
            List(1) { current.getValue(EmotionGroup.STILL) }

        val votes = emotionVoteRatiosOf(emotion(curve))

        // 只投主导词 —— 与详情页显示的那一批完全一致
        assertEquals(setOf("元气"), votes.keys)
        assertEquals(0.8f, votes.getValue("元气"), 1e-6f)
    }

    @Test
    fun `vote set equals what the detail page shows`() {
        val curve = List(6) { current.getValue(EmotionGroup.BRIGHT) } +
            List(3) { current.getValue(EmotionGroup.RAGE) } +
            List(3) { current.getValue(EmotionGroup.STILL) }

        val e = emotion(curve)

        // 计票的词 = 详情页显示的词（这是这次改造要保证的一致性）
        val shown = emotionTagRatiosOf(e).map { it.tag }.toSet()
        val voted = emotionVoteRatiosOf(e).keys
        assertEquals(shown, voted)
    }

    @Test
    fun `a minor word never reaches a vote`() {
        // 真实场景：emo 50% 静谧 35% 窒息 7% 放松 4% 神圣 2% 躁 2%
        // 后 4 个词（占比 <15%）不该被投「不喜欢」票
        val curve = List(50) { current.getValue(EmotionGroup.SAD) } +
            List(35) { current.getValue(EmotionGroup.STILL) } +
            List(7) { current.getValue(EmotionGroup.ABYSS) } +
            List(4) { current.getValue(EmotionGroup.CHILL) } +
            List(2) { current.getValue(EmotionGroup.EPIC) } +
            List(2) { current.getValue(EmotionGroup.ANXIOUS) }

        val votes = emotionVoteRatiosOf(emotion(curve))

        assertEquals(setOf("emo", "静谧"), votes.keys)
        assertTrue(votes.keys.none { it in setOf("窒息", "放松", "神圣", "躁") })
    }

    @Test
    fun `user tagged words all vote with full weight`() {
        // 用户手动标记的词没有占比含义 —— 按 1.0 强匹配处理（用户的判断比模型可信）
        val votes = emotionVoteRatiosOf(
            emotion(List(10) { current.getValue(EmotionGroup.BRIGHT) }, userTags = listOf("emo", "燃"))
        )

        assertEquals(setOf("emo", "燃"), votes.keys)
        assertEquals(1f, votes.getValue("emo"), 1e-6f)
        assertEquals(1f, votes.getValue("燃"), 1e-6f)
    }

    @Test
    fun `empty curve yields no votes`() {
        assertTrue(emotionVoteRatiosOf(emotion(emptyList())).isEmpty())
    }

    @Test
    fun `gate is the display gate by default`() {
        // 计票门槛必须与展示门槛同源，否则两边会悄悄漂移
        val curve = List(8) { current.getValue(EmotionGroup.BRIGHT) } +
            List(1) { current.getValue(EmotionGroup.RAGE) } +
            List(1) { current.getValue(EmotionGroup.STILL) }

        val byDefault = emotionVoteRatiosOf(emotion(curve))
        val byExplicit = emotionVoteRatiosOf(
            emotion(curve),
            minRatio = EmotionGroup.MIN_RATIO,
            topN = EmotionGroup.MAX_TAGS,
        )

        assertEquals(byExplicit, byDefault)
    }
}
