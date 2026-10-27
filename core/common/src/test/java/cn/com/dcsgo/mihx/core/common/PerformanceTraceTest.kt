package cn.com.dcsgo.mihx.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PerformanceTraceTest {
    @Test
    fun `measure returns block value`() {
        val value = PerformanceTrace.measure("unit-test") {
            42
        }

        assertEquals(42, value)
    }

    @Test
    fun `log redacts sensitive metadata`() {
        val logger = RecordingLogger()
        AppLog.install(logger)

        // 性能痕迹默认受 isEnabled 控制（release 下关闭），此处显式开启以验证日志内容。
        val previousEnabled = PerformanceTrace.isEnabled
        PerformanceTrace.isEnabled = true
        try {
            PerformanceTrace.log(
                operation = "queue_sync",
                elapsedMs = 12,
                metadata = mapOf("songCount" to 70, "uri" to "content://media/song/1"),
            )
        } finally {
            PerformanceTrace.isEnabled = previousEnabled
        }

        assertEquals("PerformanceTrace", logger.performanceTag)
        assertTrue(logger.performanceMessage.contains("operation=queue_sync"))
        assertTrue(logger.performanceMessage.contains("elapsedMs=12"))
        assertTrue(logger.performanceMessage.contains("songCount=70"))
        assertTrue(logger.performanceMessage.contains("uri=content://<redacted>"))
        AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
    }

    @Test
    fun `allow registers operation for release tracing`() {
        val previousEnabled = PerformanceTrace.isEnabled
        val logger = RecordingLogger()
        AppLog.install(logger)
        PerformanceTrace.isEnabled = false
        try {
            PerformanceTrace.allow("critical_path")
            PerformanceTrace.log(operation = "critical_path", elapsedMs = 5)
            assertTrue(logger.performanceMessage.contains("operation=critical_path"))
        } finally {
            PerformanceTrace.disallow("critical_path")
            PerformanceTrace.isEnabled = previousEnabled
            AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
        }
    }

    /**
     * 评审 2026-10-27 · M-20 回归：release 白名单必须**真正产出输出**。
     *
     * 原实现走 `AppLog.info`，而 `AndroidAppLogger.info` 被 `debugLoggingEnabled`
     * （= `BuildConfig.DEBUG`）拦住 —— 白名单只决定"是否计算"，不决定"是否输出"，
     * release 下算完的消息照样被丢弃。修复后性能痕迹走独立的 `performance` 通道。
     */
    @Test
    fun `allowlisted operation reaches the performance channel while release is off`() {
        val previousEnabled = PerformanceTrace.isEnabled
        val logger = RecordingLogger()
        AppLog.install(logger)
        PerformanceTrace.isEnabled = false
        try {
            PerformanceTrace.allow("release_visible")
            PerformanceTrace.log(operation = "release_visible", elapsedMs = 7)

            assertEquals("PerformanceTrace", logger.performanceTag)
            assertTrue(logger.performanceMessage.contains("operation=release_visible"))
            // 关键：不能走被 debug 开关拦掉的 info 通道
            assertFalse(logger.infoMessage.contains("release_visible"))
        } finally {
            PerformanceTrace.disallow("release_visible")
            PerformanceTrace.isEnabled = previousEnabled
            AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
        }
    }

    /** 未白名单 + release → 完全静默，且不构造 metadata（无谓开销为零）。 */
    @Test
    fun `non allowlisted operation in release neither logs nor builds metadata`() {
        val previousEnabled = PerformanceTrace.isEnabled
        val logger = RecordingLogger()
        AppLog.install(logger)
        PerformanceTrace.isEnabled = false
        var metadataBuilt = false
        try {
            PerformanceTrace.logLazy(operation = "quiet_op", elapsedMs = 3) {
                metadataBuilt = true
                mapOf("k" to "v")
            }

            assertFalse(metadataBuilt)
            assertEquals("", logger.performanceMessage)
            assertEquals("", logger.infoMessage)
        } finally {
            PerformanceTrace.isEnabled = previousEnabled
            AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
        }
    }

    /** 惰性 metadata：追踪开启时才构造。 */
    @Test
    fun `lazy metadata is built only when tracing is on`() {
        val previousEnabled = PerformanceTrace.isEnabled
        val logger = RecordingLogger()
        AppLog.install(logger)
        PerformanceTrace.isEnabled = true
        var metadataBuilt = false
        try {
            PerformanceTrace.logLazy(operation = "lazy_op", elapsedMs = 9) {
                metadataBuilt = true
                mapOf("songCount" to 3)
            }

            assertTrue(metadataBuilt)
            assertTrue(logger.performanceMessage.contains("songCount=3"))
        } finally {
            PerformanceTrace.isEnabled = previousEnabled
            AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
        }
    }

    private class RecordingLogger : AppLogger {
        var tag: String = ""
        var message: String = ""
        var performanceTag: String = ""
        var performanceMessage: String = ""
        var infoMessage: String = ""

        override fun debug(tag: String, message: String) = record(tag, message)
        override fun info(tag: String, message: String) {
            infoMessage = message
            record(tag, message)
        }
        override fun warning(tag: String, message: String, throwable: Throwable?) = record(tag, message)
        override fun error(tag: String, message: String, throwable: Throwable?) = record(tag, message)
        override fun performance(tag: String, message: String) {
            performanceTag = tag
            performanceMessage = message
        }

        private fun record(tag: String, message: String) {
            this.tag = tag
            this.message = message
        }
    }
}
