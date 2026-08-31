package cn.com.dcsgo.mihx.data.player

/**
 * 原始 short 缓冲(倍增扩容, 零装箱): 4min@44.1k mono ≈ 21MB.
 *
 * 内存纪律(真机 OOM 教训 2026-08-30): 端侧整曲音频缓冲一律原始类型数组.
 * 曾用 ArrayList<Short> 逐样本装箱, 4min 立体声 ≈240MB 顶穿 256MB 堆崩进程.
 */
internal class ShortAccum(
    initialCapacity: Int = EmotionAnalyzer.SR * 30,
    private val maxCapacity: Int = EmotionAnalyzer.MAX_SAMPLES,
) {
    var data = ShortArray(initialCapacity.coerceAtMost(maxCapacity).coerceAtLeast(1024))
        private set
    var size = 0
        private set

    fun add(v: Short) {
        if (size == data.size) {
            val newCap = minOf(data.size * 2, maxCapacity)
            // 顶到熔断上限: 静默丢弃后续样本(调用方据 size 超限弃曲), 防越界
            if (newCap <= size) return
            data = data.copyOf(newCap)
        }
        data[size++] = v
    }
}
