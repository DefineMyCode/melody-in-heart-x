package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音质档位分级（按文件实际规格）与空间音频识别。
 */
class AudioQualityTierTest {

    @Test
    fun `lossy tiers follow bitrate`() {
        assertEquals(AudioQualityTier.STANDARD, AudioQualityTier.classify("audio/mpeg", 128, 0, 44_100))
        assertEquals(AudioQualityTier.HIGH, AudioQualityTier.classify("audio/mpeg", 192, 0, 44_100))
        assertEquals(AudioQualityTier.VERY_HIGH, AudioQualityTier.classify("audio/mpeg", 320, 0, 44_100))
        assertEquals(AudioQualityTier.UNKNOWN, AudioQualityTier.classify("audio/mpeg", 0, 0, 44_100))
    }

    @Test
    fun `lossless tiers follow depth and sample rate`() {
        assertEquals(AudioQualityTier.LOSSLESS, AudioQualityTier.classify("audio/flac", 900, 16, 44_100))
        assertEquals(AudioQualityTier.HI_RES, AudioQualityTier.classify("audio/flac", 2_300, 24, 48_000))
        assertEquals(AudioQualityTier.HI_RES, AudioQualityTier.classify("audio/flac", 4_600, 24, 96_000))
        assertEquals(AudioQualityTier.LOSSLESS, AudioQualityTier.classify("audio/raw", 1_411, 16, 44_100))
        assertEquals(AudioQualityTier.HI_RES, AudioQualityTier.classify("audio/dsd", 5_645, 1, 2_822_400))
    }

    @Test
    fun `unknown specs stay unknown`() {
        assertEquals(AudioQualityTier.UNKNOWN, AudioQualityTier.classify("", 0, 0, 0))
        assertEquals(AudioQualityTier.UNKNOWN, AudioQualityTier.classify("audio/mpeg", 0, 0, 0))
    }

    @Test
    fun `spatial codecs are recognized`() {
        assertTrue(AudioQualityTier.isSpatial("audio/eac3-joc", 2))
        assertTrue(AudioQualityTier.isSpatial("audio/ec+3", 6))
        assertTrue(AudioQualityTier.isSpatial("audio/ac4", 6))
        assertTrue(AudioQualityTier.isSpatial("audio/ac4", 10))
        // 2.0 AC-4 只是普通立体声编码，不算空间音频
        assertFalse(AudioQualityTier.isSpatial("audio/ac4", 2))
        assertFalse(AudioQualityTier.isSpatial("audio/ac4", 0))
        assertFalse(AudioQualityTier.isSpatial("audio/eac3", 6))
        assertFalse(AudioQualityTier.isSpatial("audio/flac", 2))
    }

    @Test
    fun `song quality derives from persisted specs`() {
        val song = Song(
            id = 1,
            title = "Song",
            artist = "Artist",
            sampleRate = 96_000,
            bitDepth = 24,
            bitRateKbps = 2_300,
            audioCodec = "audio/flac",
        )

        assertEquals(AudioQualityTier.HI_RES, song.qualityTier)
        assertEquals("高解析", song.qualityLabel)
        assertEquals("", Song(id = 2, title = "x", artist = "y").qualityLabel)
    }
}