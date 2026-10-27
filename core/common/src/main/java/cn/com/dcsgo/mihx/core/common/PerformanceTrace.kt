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
 * 因此"release 完全没有性能痕迹"是明确决策，而非意外失效；需要线上可观测性时按操作注册白名单。
 *
 * 评审 2026-10-27 · M-20：原先本类走 [AppLog.info]，而 `AndroidAppLogger.info`
 * 被 `debugLoggingEnabled`（= `BuildConfig.DEBUG`）拦住 —— 于是 **release 下白名单
 * 形同虚设**：`allow()` 只决定"是否计算"，不决定"是否输出"，算完的字符串照样被丢弃。
 * 现在改走 [AppLog.performance]（不受 debug 开关约束），白名单才真正兑现 KDoc 承诺。
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
        logLazy(operation, elapsedMs) { metadata }
    }

    /**
     * 惰性 metadata 版本：metadata 只在**确实要输出**时才构造。
     *
     * 评审 2026-10-27 · N-22：原 `measure` 要求调用方先构造好 Map 再进入，
     * 即使追踪关闭也付了构造成本（`plan.performanceMetadata()` 之类）。
     */
    fun logLazy(
        operation: String,
        elapsedMs: Long,
        metadata: () -> Map<String, Any?>,
    ) {
        if (!shouldTrace(operation)) return
        val suffix = metadata()
            .filterValues { it != null }
            .entries
            .joinToString(prefix = " ", separator = " ") { (key, value) ->
                "$key=${value.toString().redactSensitiveValuesForLog()}"
            }
            .takeIf { it.isNotBlank() }
            .orEmpty()
        AppLog.performance(
            PERFORMANCE_TAG,
            "operation=$operation elapsedMs=${elapsedMs.coerceAtLeast(0L)}$suffix",
        )
    }

    inline fun <T> measure(
        operation: String,
        metadata: Map<String, Any?> = emptyMap(),
        block: () -> T,
    ): T = measure(operation, { metadata }, block)

    /**
     * 惰性 metadata 的 measure：metadata 只在要输出时才构造。
     * 用法：`PerformanceTrace.measure("controller_sync_queue", { plan.performanceMetadata() }) { ... }`
     */
    inline fun <T> measure(
        operation: String,
        noinline metadata: () -> Map<String, Any?>,
        block: () -> T,
    ): T {
        val startedAt = nowMs()
        return try {
            block()
        } finally {
            // metadata 标 noinline：它要传给非 inline 的 logLazy，Kotlin 不允许
            // inline 参数被转交（会失去内联机会，但只是一个小 lambda 的开销）。
            logLazy(operation, nowMs() - startedAt, metadata)
        }
    }
}
