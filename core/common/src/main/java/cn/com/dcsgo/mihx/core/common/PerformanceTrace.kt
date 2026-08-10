package cn.com.dcsgo.mihx.core.common

private const val PERFORMANCE_TAG = "PerformanceTrace"

/**
 * 性能埋点（见评审文档问题 17 / 路线图 P3-13）。
 *
 * 可观测性策略（显式、刻意，而非依赖 `AppLog.info` 被静默关闭的副作用）：
 * - **debug 构建**：默认开启全部性能痕迹（`isEnabled` 初始值取自 [BuildConfig.DEBUG]）。
 * - **release 构建**：默认关闭——出于隐私与体积考量，release 不产出任何性能日志。
 * - **release 白名单开关**：通过 [allow] 注册的关键操作（如核心播放链路），即使在 release 下仍输出性能数据，
 *   用于线上问题定位；其余操作保持静默。
 *
 * 因此“release 完全没有性能痕迹”是明确决策，而非意外失效；需要线上可观测性时按操作注册白名单。
 */
object PerformanceTrace {
    /** 全局开关。release 默认 false，debug 默认 true。 */
    @Volatile
    var isEnabled: Boolean = BuildConfig.DEBUG

    /** release 下仍要保留性能痕迹的操作白名单。 */
    private val allowList = mutableSetOf<String>()

    /** 注册 release 下也强制追踪的操作（白名单开关）。 */
    fun allow(operation: String) {
        allowList.add(operation)
    }

    /** 取消注册白名单中的操作。 */
    fun disallow(operation: String) {
        allowList.remove(operation)
    }

    /** 是否应输出该操作的性能痕迹。 */
    private fun shouldTrace(operation: String): Boolean =
        isEnabled || allowList.contains(operation)

    fun nowMs(): Long = System.nanoTime() / 1_000_000L

    fun log(
        operation: String,
        elapsedMs: Long,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
        if (!shouldTrace(operation)) return
        val suffix = metadata
            .filterValues { it != null }
            .entries
            .joinToString(prefix = " ", separator = " ") { (key, value) ->
                "$key=${value.toString().redactSensitiveValuesForLog()}"
            }
            .takeIf { it.isNotBlank() }
            .orEmpty()
        AppLog.info(PERFORMANCE_TAG, "operation=$operation elapsedMs=${elapsedMs.coerceAtLeast(0L)}$suffix")
    }

    inline fun <T> measure(
        operation: String,
        metadata: Map<String, Any?> = emptyMap(),
        block: () -> T,
    ): T {
        val startedAt = nowMs()
        return try {
            block()
        } finally {
            log(
                operation = operation,
                elapsedMs = nowMs() - startedAt,
                metadata = metadata,
            )
        }
    }
}
