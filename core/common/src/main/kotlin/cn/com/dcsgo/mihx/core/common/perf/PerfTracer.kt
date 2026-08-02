package cn.com.dcsgo.mihx.core.common.perf

import cn.com.dcsgo.mihx.core.common.log.AppLogger

/**
 * Performance tracer (plan P5-A / P6-4). [record] logs a metric sample under a bucket label so
 * callers can correlate timings by workload size (e.g. import duration bucketed by track count).
 * Phase 6 replaces the log sink with the baseline-report collector.
 */
object PerfTracer {
    inline fun <T> trace(label: String, block: () -> T): T {
        val start = System.nanoTime()
        return block().also {
            record(label, (System.nanoTime() - start) / 1_000_000)
        }
    }

    fun record(label: String, valueMs: Long) {
        AppLogger.d(TAG, "perf $label=${valueMs}ms")
    }

    private const val TAG = "PerfTracer"
}
