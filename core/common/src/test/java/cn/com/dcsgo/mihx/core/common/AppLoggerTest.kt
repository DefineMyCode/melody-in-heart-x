package cn.com.dcsgo.mihx.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLoggerTest {
    @Test
    fun `redacts content and file uris`() {
        val message = "uri=content://media/external/audio/media/42 file=file:///sdcard/Music/song.flac"

        assertEquals(
            "uri=content://<redacted> file=file://<redacted>",
            message.redactSensitiveValuesForLog(),
        )
    }

    @Test
    fun `redacts platform file paths without damaging uri placeholders`() {
        val message = "android=/storage/emulated/0/Music/a.flac windows=D:\\Music\\a.flac content://media/item"

        assertEquals(
            "android=/<path-redacted> windows=<path-redacted> content://<redacted>",
            message.redactSensitiveValuesForLog(),
        )
    }

    @Test
    fun `redacts bluetooth addresses and device name fields`() {
        val message = "deviceName=AliceHeadset bluetoothName=\"Car Audio\" address=AA:BB:CC:DD:EE:FF route=Bluetooth"

        assertEquals(
            "deviceName=<redacted> bluetoothName=<redacted> address=<bluetooth-address-redacted> route=Bluetooth",
            message.redactSensitiveValuesForLog(),
        )
    }

    @Test
    fun `redacts throwable messages and stack traces`() {
        val throwable = IllegalStateException("failed uri=content://media/external/audio/media/42 at /storage/emulated/0/Music/a.flac")

        val redacted = throwable.redactedStackTraceString()

        assertFalse(redacted.contains("content://media/external/audio/media/42"))
        assertFalse(redacted.contains("/storage/emulated/0/Music/a.flac"))
        assertTrue(redacted.contains("content://<redacted>"))
        assertTrue(redacted.contains("/<path-redacted>"))
    }
}
