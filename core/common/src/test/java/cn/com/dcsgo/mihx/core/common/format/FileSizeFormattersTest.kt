package cn.com.dcsgo.mihx.core.common.format

import org.junit.Assert.assertEquals
import org.junit.Test

class FileSizeFormattersTest {

    @Test
    fun `formats bytes below one kilobyte`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
    }

    @Test
    fun `formats kilobytes megabytes and gigabytes`() {
        assertEquals("1.0 KB", formatFileSize(1024))
        assertEquals("1.5 MB", formatFileSize((1024L * 1024 * 3) / 2))
        assertEquals("2.50 GB", formatFileSize(1024L * 1024 * 1024 * 5 / 2))
    }

    @Test
    fun `negative size falls back to unknown`() {
        assertEquals("未知", formatFileSize(-1))
    }
}