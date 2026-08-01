package cn.com.dcsgo.mihx.core.common.perf

/** Performance tracer stub. Phase 6 wires real reporting. */
object PerfTracer {
    inline fun <T> trace(label: String, block: () -> T): T {
        val start = System.nanoTime()
        return block().also {
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            // Phase 6: report elapsedMs under [label].
        }
    }

    fun record(label: String, valueMs: Long) {
        // Phase 6: record a metric sample.
    }
}
