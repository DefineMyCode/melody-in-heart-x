package cn.com.dcsgo.mihx.core.common

import org.junit.Assert.assertEquals
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

        assertEquals("PerformanceTrace", logger.tag)
        assertTrue(logger.message.contains("operation=queue_sync"))
        assertTrue(logger.message.contains("elapsedMs=12"))
        assertTrue(logger.message.contains("songCount=70"))
        assertTrue(logger.message.contains("uri=content://<redacted>"))
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
            assertTrue(logger.message.contains("operation=critical_path"))
        } finally {
            PerformanceTrace.disallow("critical_path")
            PerformanceTrace.isEnabled = previousEnabled
            AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
        }
    }

    private class RecordingLogger : AppLogger {
        var tag: String = ""
        var message: String = ""

        override fun debug(tag: String, message: String) = record(tag, message)
        override fun info(tag: String, message: String) = record(tag, message)
        override fun warning(tag: String, message: String, throwable: Throwable?) = record(tag, message)
        override fun error(tag: String, message: String, throwable: Throwable?) = record(tag, message)

        private fun record(tag: String, message: String) {
            this.tag = tag
            this.message = message
        }
    }
}
