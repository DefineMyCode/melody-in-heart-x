package cn.com.dcsgo.mihx.player

/**
 * 原始 short 缓冲(倍增扩容, 零装箱): 4min@44.1k mono ≈ 21MB.
 *
 * 内存纪律(真机 OOM 教训 2026-08-30): 端侧整曲音频缓冲一律原始类型数组.
 * 曾用 ArrayList<Short> 逐样本装箱, 4min 立体声 ≈240MB 顶穿 256MB 堆崩进程.
 *
 * maxCapacity 由调用方按实际源采样率换算（EmotionAnalyzer.maxSamplesForSrc）——
 * 静态 192kHz 最坏常量在 44.1kHz 下形同虚设（2026-09-04 超长合集 OOM 回归）。
 * ShortAccum 顶到上限后静默丢弃后续样本，调用方据 size 超限弃曲。
 *
 * m-12（评审）：初始容量与写入上限严格对齐 maxCapacity。
 * 旧实现把初始容量下限抬到 1024（coerceAtLeast(1024)），当 maxCapacity < 1024 时
 * 初始数组就已超过 maxCapacity，而 add 仅在 size == data.size 时才检查上限，
 * 导致可写入超过 maxCapacity 的样本。修复：初始容量直接 coerceIn(1, maxCapacity)，
 * add 在写入前判断 size >= maxCapacity。
 */
internal class ShortAccum(
    initialCapacity: Int = EmotionAnalyzer.SR * 30,
    private val maxCapacity: Int,
) {
    var data = ShortArray(initialCapacity.coerceIn(1, maxCapacity))
        private set
    var size = 0
        private set
    /** 是否触顶被截断（到达熔断上限静默丢弃后续样本）：调用方据此弃曲并区分「超长」与「内存超限」 */
    var truncated = false
        private set

    fun add(v: Short) {
        // m-12：写入前先判上限——即使 data 容量大于 maxCapacity（不应发生，但防御性），
        // 也不允许写入超过 maxCapacity 的样本
        if (size >= maxCapacity) {
            truncated = true
            return
        }
        if (size == data.size) {
            val newCap = minOf(data.size * 2, maxCapacity)
            data = data.copyOf(newCap)
        }
        data[size++] = v
    }
}
