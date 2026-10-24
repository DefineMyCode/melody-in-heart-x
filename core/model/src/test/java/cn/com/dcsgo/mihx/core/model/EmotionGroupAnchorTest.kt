package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 情绪锚点表的**结构性契约**（2026-10-25）。
 *
 * 背景：2026-10-25 落地了 arousal→RMS 响度包络（A）与 valence 标定（B），
 * V-A 坐标系定义变了，原锚点表整体脱节（「神圣」变成万能桶，占 24% 窗口）。
 * 锚点表按全库 1095 首真值重算（见 docs/architecture/EMOTION_VA_UPGRADE.md §10）。
 *
 * 锚点**只影响投票**，不影响已存曲线 —— 所以它可以随时重算、零重扫成本。
 * 但重算必须守住下面这些不变量，否则语义会悄悄崩坏：
 *  - 边界：锚点不能超出 V-A 定义域（越界=模型永远到不了 = 死词条）
 *  - 间距：两组锚点太近，两者在 Voronoi 里抢同一批窗，词条会随机跳
 *  - 语义序：燃必须在最高能量处、窒息必须在最负面处……名字不能张冠李戴
 */
class EmotionGroupAnchorTest {

    private val auto = EmotionGroup.entries.filter { it.auto }

    private fun anchorOf(group: EmotionGroup) = group.anchorV to group.anchorA

    @Test
    fun `all auto anchors stay inside the V-A domain`() {
        for (g in auto) {
            val (v, a) = anchorOf(g)
            assertTrue("${g.name} anchorV=$v 越界", abs(v) <= 0.95f)
            assertTrue("${g.name} anchorA=$a 越界", abs(a) <= 0.95f)
        }
    }

    @Test
    fun `auto anchors keep enough spacing to be distinguishable`() {
        for (i in auto.indices) {
            for (j in i + 1 until auto.size) {
                val (v1, a1) = anchorOf(auto[i])
                val (v2, a2) = anchorOf(auto[j])
                val d = hypot(v1 - v2, a1 - a2)
                assertTrue(
                    "${auto[i].name} 与 ${auto[j].name} 锚点仅相距 $d（< 0.25）",
                    d >= 0.25f,
                )
            }
        }
    }

    @Test
    fun `RAGE stays the highest-arousal group`() {
        // 「燃」= 能量峰值，语义上必须独占最高 arousal
        val rageA = EmotionGroup.RAGE.anchorA
        for (g in auto.filter { it != EmotionGroup.RAGE }) {
            assertTrue(
                "${g.name} 的 arousal(${g.anchorA}) 不应高于「燃」($rageA)",
                g.anchorA <= rageA,
            )
        }
    }

    @Test
    fun `ABYSS stays the lowest-valence group`() {
        // 「窒息」= 最负面
        val abyssV = EmotionGroup.ABYSS.anchorV
        for (g in auto.filter { it != EmotionGroup.ABYSS }) {
            assertTrue(
                "${g.name} 的 valence(${g.anchorV}) 不应低于「窒息」($abyssV)",
                g.anchorV >= abyssV,
            )
        }
    }

    @Test
    fun `BRIGHT and LOVE stay the two most positive groups`() {
        // 「元气」「治愈」并列最高 valence（原设计里两者都是 +0.60）
        val brightV = EmotionGroup.BRIGHT.anchorV
        val loveV = EmotionGroup.LOVE.anchorV
        for (g in auto.filter { it != EmotionGroup.BRIGHT && it != EmotionGroup.LOVE }) {
            assertTrue(
                "${g.name} 的 valence(${g.anchorV}) 不应高于元气($brightV)/治愈($loveV)",
                g.anchorV <= maxOf(brightV, loveV) + 1e-4f,
            )
        }
        assertTrue(
            "元气($brightV) 与 治愈($loveV) 的 valence 分离过大",
            abs(brightV - loveV) <= 0.25f,
        )
    }

    @Test
    fun `STILL stays the lowest-arousal group`() {
        // 「静谧」= 能量最低
        val stillA = EmotionGroup.STILL.anchorA
        for (g in auto.filter { it != EmotionGroup.STILL }) {
            assertTrue(
                "${g.name} 的 arousal(${g.anchorA}) 不应低于「静谧」($stillA)",
                g.anchorA >= stillA,
            )
        }
    }

    @Test
    fun `negative-valence groups are the three negative ones`() {
        val negative = auto.filter { it.anchorV < 0f }.map { it.headline }.toSet()
        assertEquals(
            setOf("emo", "躁", "窒息"),
            negative,
        )
    }

    @Test
    fun `RAGE is reachable and no anchor sits on the origin`() {
        // 「燃」的锚点必须真的够得着高能量区（否则 A 改造白做）
        assertTrue(
            "「燃」锚点 arousal=${EmotionGroup.RAGE.anchorA} 过低，高能量区够不到",
            EmotionGroup.RAGE.anchorA >= 0.6f,
        )
        // 原点上的锚点 = 万能桶磁铁：中庸窗会被整片吸走（当年「神圣」就是这样
        // 独占 23.9% 窗口的）。任何自动组的锚点都必须离原点足够远。
        for (g in auto) {
            val d = hypot(g.anchorV, g.anchorA)
            assertTrue(
                "${g.name} 锚点离原点仅 $d（< 0.25），会成为万能桶磁铁",
                d >= 0.25f,
            )
        }
    }

    @Test
    fun `WITTY stays excluded from automatic voting`() {
        // 鬼畜不参与投票：它的锚点居中，会把中庸窗整片吸走
        assertTrue(!EmotionGroup.WITTY.auto)
        val top = EmotionGroup.categoriesFor(List(20) { 0.2f to 0.3f })
        assertTrue(top.none { it.first == EmotionGroup.WITTY })
    }
}
