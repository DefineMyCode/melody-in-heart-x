package cn.com.dcsgo.mihx.player

/**
 * YAMNet(AudioSet) 类别索引表 —— 人声 / 器乐两组。
 *
 * 背景（2026-10-25）：App 里那个 emotion_yamnet.tflite **本来就在输出 521 个
 * AudioSet 类别分数**（输出张量 Identity_1[2,521]），只是分析器一直只取 embedding，
 * 把类别分数扔掉了。于是「这歌有没有人声」这个信息是白拿的。
 *
 * 实测（真机 64 首离线评估）：现网 valence 与「有没有人声」存在系统性偏置 ——
 * 器乐曲被平均判低 0.154（MWU p=0.011），人声概率与 valence 相关系数 +0.530。
 * 人声分数是校正这个偏置的关键变量。
 *
 * 索引来源：AudioSet 官方 yamnet_class_map.csv（521 个具名类，索引即模型输出序号）。
 * 本文件由 tools/ 下的评估脚本从该 CSV 产出后转写，**勿手改**。
 *
 * VOCAL_INDICES      —— 说话/歌唱/呼喊类。人声类分数之和 = 「这首歌有人声」的置信度。
 * INSTRUMENT_INDICES —— 明确乐器类。可用于「纯器乐」侧的佐证。
 */
object YamnetClassMap {

    /** 人声类(说话/歌唱/呼喊)的 AudioSet 索引 */
    val VOCAL_INDICES: IntArray = intArrayOf(
        0, 1, 2, 3, 4, 5, 24, 27, 29, 30, 65,
    )

    /** 器乐类(乐器/打击乐器)的 AudioSet 索引 */
    val INSTRUMENT_INDICES: IntArray = intArrayOf(
        87, 133, 134, 135, 136, 137, 138, 139, 140, 142, 143, 145, 148, 149, 150, 151, 152, 155, 156, 157, 158, 159, 160, 162, 163, 164, 165, 166, 169, 173, 174, 175, 176, 180, 181, 182, 183, 184, 186, 188, 189, 190, 191, 192, 193, 194, 199, 203, 204, 205,
    )

    /** 「Music」总类索引（AudioSet 132）—— 用于区分"这是音乐"与"这是环境音" */
    const val MUSIC_INDEX = 132

    /** 「Silence」索引（AudioSet 494） */
    const val SILENCE_INDEX = 494

    /**
     * 一帧 YAMNet 类别分数 → 该帧的人声分数（人声类 sigmoid 分数之和）。
     *
     * 纯函数，便于单测：给一个类别分数数组，返回人声分量。
     *
     * @param frameScores 长度 521 的类别分数（sigmoid 后）
     */
    fun vocalScoreOf(frameScores: FloatArray): Float {
        var sum = 0f
        for (ci in VOCAL_INDICES) sum += frameScores[ci]
        return sum
    }
}
