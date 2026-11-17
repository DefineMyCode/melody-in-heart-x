package cn.com.dcsgo.mihx.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMusicStorageStatsTest {

    private fun song(
        id: Int,
        codec: String = "",
        bitRateKbps: Int = 0,
        bitDepth: Int = 0,
        sampleRate: Int = 0,
        channelCount: Int = 0,
        spatialAudio: Boolean = false,
    ): Song = Song(
        id = id,
        title = "song-$id",
        artist = "artist",
        audioCodec = codec,
        bitRateKbps = bitRateKbps,
        bitDepth = bitDepth,
        sampleRate = sampleRate,
        channelCount = channelCount,
        spatialAudio = spatialAudio,
    )

    @Test
    fun `from aggregates quality tiers and ratios`() {
        val songs = listOf(
            song(id = 1, codec = "audio/mpeg", bitRateKbps = 128),
            song(id = 2, codec = "audio/mpeg", bitRateKbps = 256),
            song(id = 3, codec = "audio/mpeg", bitRateKbps = 320),
            song(id = 4, codec = "audio/flac", bitDepth = 16, sampleRate = 44_100),
            song(id = 5, codec = "audio/flac", bitDepth = 24, sampleRate = 48_000),
            song(id = 6),
        )

        val stats = LocalMusicStorageStats.from(songs, emptyMap())

        assertEquals(6, stats.totalSongs)
        assertEquals(1, stats.shareOf(AudioQualityTier.STANDARD).count)
        assertEquals(1, stats.shareOf(AudioQualityTier.HIGH).count)
        assertEquals(1, stats.shareOf(AudioQualityTier.VERY_HIGH).count)
        assertEquals(1, stats.shareOf(AudioQualityTier.LOSSLESS).count)
        assertEquals(1, stats.shareOf(AudioQualityTier.HI_RES).count)
        assertEquals(1, stats.shareOf(AudioQualityTier.UNKNOWN).count)
        assertEquals(1f / 6f, stats.shareOf(AudioQualityTier.STANDARD).ratio, 0.0001f)
    }

    @Test
    fun `from sums measured sizes and marks missing files as unknown`() {
        val songs = listOf(song(id = 1), song(id = 2), song(id = 3))
        val sizes = mapOf(1 to 1_000L, 2 to 2_500L, 3 to 0L)

        val stats = LocalMusicStorageStats.from(songs, sizes)

        assertEquals(3_500L, stats.totalBytes)
        assertEquals(2, stats.measuredSizeSongs)
        assertEquals(1, stats.unknownSizeSongs)
        assertEquals(2f / 3f, stats.sizeCoverageRatio, 0.0001f)
    }

    @Test
    fun `from returns all tiers in enum order when library is empty`() {
        val stats = LocalMusicStorageStats.from(emptyList(), emptyMap())

        assertEquals(0, stats.totalSongs)
        assertEquals(0, stats.spatialAudioSongs)
        assertEquals(AudioQualityTier.entries, stats.qualityShares.map { it.tier })
        assertTrue(stats.qualityShares.all { it.count == 0 && it.ratio == 0f })
    }

    @Test
    fun `from counts spatial audio with latest rules instead of persisted flag`() {
        val songs = listOf(
            // 最新规则：E-AC-3 JOC 直接算；AC-4 仅多声道算；普通 E-AC-3 / 杜比数字+ 不算
            song(id = 1, codec = "audio/eac3-joc", channelCount = 6),
            song(id = 2, codec = "audio/ac4", channelCount = 6),
            song(id = 3, codec = "audio/ac4", channelCount = 2),
            song(id = 4, codec = "audio/ec+3", channelCount = 6),
            song(id = 5, codec = "audio/eac3", channelCount = 6),
            // 旧规则落库的脏值不应影响统计
            song(id = 6, codec = "audio/ec+3", channelCount = 6, spatialAudio = true),
        )

        val stats = LocalMusicStorageStats.from(songs, emptyMap())

        assertEquals(2, stats.spatialAudioSongs)
        assertEquals(2f / 6f, stats.spatialAudioRatio, 0.0001f)
    }

    @Test
    fun `flac with unknown bit depth but cd sample rate is still lossless`() {
        val songs = listOf(song(id = 1, codec = "audio/flac", sampleRate = 44_100))

        val stats = LocalMusicStorageStats.from(songs, emptyMap())

        assertEquals(1, stats.shareOf(AudioQualityTier.LOSSLESS).count)
        assertEquals(0, stats.shareOf(AudioQualityTier.UNKNOWN).count)
    }
}