package cn.com.dcsgo.mihx.core.common

private const val PERFORMANCE_TAG = "PerformanceTrace"

object PerformanceTrace {
    fun nowMs(): Long = System.nanoTime() / 1_000_000L

    fun log(
        operation: String,
        elapsedMs: Long,
        metadata: Map<String, Any?> = emptyMap(),
    ) {
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
