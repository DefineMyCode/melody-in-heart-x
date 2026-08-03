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

        PerformanceTrace.log(
            operation = "queue_sync",
            elapsedMs = 12,
            metadata = mapOf("songCount" to 70, "uri" to "content://media/song/1"),
        )

        assertEquals("PerformanceTrace", logger.tag)
        assertTrue(logger.message.contains("operation=queue_sync"))
        assertTrue(logger.message.contains("elapsedMs=12"))
        assertTrue(logger.message.contains("songCount=70"))
        assertTrue(logger.message.contains("uri=content://<redacted>"))
        AppLog.install(AndroidAppLogger(debugLoggingEnabled = false))
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
