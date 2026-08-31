package cn.com.dcsgo.mihx.domain.emotion

import cn.com.dcsgo.mihx.core.model.SongEmotion
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 端侧情绪个性化 (kNN): 用"用户已校准的歌"作为锚点,
 * 按 embedding 余弦距离加权平均, 预测未校准歌的 V/A.
 *
 * 非参数方法——无训练、无模型更新, 每次标注即时生效.
 * 纯函数, 锚点集由调用方注入(仓库 correctedWithEmbedding()).
 */
object EmotionPersonalizer {

    /** 参与投票的最近锚点数 */
    private const val K = 5

    /** 权重衰减指数: 越大越偏向最近邻 */
    private const val GAMMA = 3f

    /**
     * @return 预测后的副本(userValence/Arousal = kNN 加权值);
     *         目标已用户校准 / 锚点不足 / 无 embedding 时返回 null(不干预).
     */
    fun predict(
        target: SongEmotion,
        anchors: List<SongEmotion>,
    ): SongEmotion? {
        if (target.userCorrected) return null
        val t = target.embedding ?: return null
        val usable = anchors.filter {
            it.songId != target.songId &&
                it.embedding != null && it.userValence != null && it.userArousal != null
        }
        if (usable.size < 2) return null

        // 最近 K 邻
        val neighbors = usable
            .map { it to cosineDistance(t, it.embedding!!) }
            .sortedBy { it.second }
            .take(K)

        // 权重 = (1 - 归一化距离)^GAMMA; 距离全等时退化为均值
        val minD = neighbors.minOf { it.second }
        val maxD = neighbors.maxOf { it.second }
        val spread = (maxD - minD).takeIf { it > 1e-6f }
        var wSum = 0f
        var v = 0f
        var a = 0f
        for ((anchor, d) in neighbors) {
            val w = if (spread == null) 1f else {
                val norm = ((d - minD) / spread).coerceIn(0f, 1f)
                (1f - norm).pow(GAMMA) + 0.05f // 保底权重, 防唯一近邻独裁
            }
            wSum += w
            v += w * anchor.userValence!!
            a += w * anchor.userArousal!!
        }
        if (wSum < 1e-6f) return null
        return target.copy(userValence = v / wSum, userArousal = a / wSum)
    }

    /** 1 - 余弦相似度, ∈ [0,2]; 零向量返回 1(中性距离). */
    private fun cosineDistance(x: FloatArray, y: FloatArray): Float {
        if (x.size != y.size) return 1f
        var dot = 0.0
        var nx = 0.0
        var ny = 0.0
        for (i in x.indices) {
            dot += x[i] * y[i]
            nx += x[i] * x[i]
            ny += y[i] * y[i]
        }
        if (nx < 1e-12 || ny < 1e-12) return 1f
        return (1.0 - dot / (sqrt(nx) * sqrt(ny))).toFloat()
    }
}
