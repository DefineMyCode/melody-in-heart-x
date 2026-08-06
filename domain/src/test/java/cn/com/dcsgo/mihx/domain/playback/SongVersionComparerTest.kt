package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SongVersionComparerTest {

    private val exts = mutableMapOf<Int, String>()

    @Test
    fun compareSortsVersionsBySampleRateDescending() {
        val result = compare(
            version(1, sampleRate = 44_100, ext = "wav"),
            version(2, sampleRate = 96_000, ext = "flac"),
            version(3, sampleRate = 48_000, ext = "mp3"),
        )

        assertEquals(listOf(2, 3, 1), result.versions.map { it.song.id })
        assertEquals(0, result.recommendedIndex)
        assertTrue(result.versions[0].isRecommended)
        assertEquals("FLAC", result.versions[0].format)
        assertTrue(result.versions[0].isLossless)
        assertFalse(result.versions[1].isLossless)
    }

    @Test
    fun compareSeparatesCommonFieldsFromDiffRows() {
        val result = compare(
            version(1, sampleRate = 96_000, ext = "flac", artist = "Beyond", album = "乐与怒"),
            version(2, sampleRate = 44_100, ext = "wav", artist = "Beyond", album = "乐与怒"),
        )

        // 艺术家 / 专辑 / 时长 / 歌词 / 封面 一致 → 归入 common
        val commonLabels = result.commonFields.map { it.first }
        assertTrue(commonLabels.contains("艺术家"))
        assertTrue(commonLabels.contains("专辑"))
        assertTrue(commonLabels.contains("时长"))
        assertEquals("Beyond", result.commonFields.first { it.first == "艺术家" }.second)

        // 格式 / 采样率 存在差异 → 归入 rows
        val rowLabels = result.rows.map { it.label }
        assertTrue(rowLabels.contains("格式"))
        assertTrue(rowLabels.contains("采样率"))
        assertEquals(2, result.diffCount)
        assertEquals(5, result.sameCount)
    }

    @Test
    fun identicalFieldsAreNotShownAsRows() {
        val result = compare(
            version(1, sampleRate = 96_000, ext = "flac", album = "A"),
            version(2, sampleRate = 44_100, ext = "wav", album = "A"),
        )

        // 歌词 / 封面 全部「无」→ 进入 common，不产生行
        assertFalse(result.rows.any { it.label == "歌词" })
        assertFalse(result.rows.any { it.label == "封面" })
        assertTrue(result.commonFields.any { it.first == "歌词" })
        assertTrue(result.commonFields.any { it.first == "封面" })
    }

    @Test
    fun qualityScoreWeightsLosslessFormatsHigher() {
        val result = compare(
            version(1, sampleRate = 44_100, ext = "flac"),
            version(2, sampleRate = 48_000, ext = "mp3"),
        )

        // 采样率 48k 的 MP3 在无损加权后低于 44.1k 的 FLAC
        val flac = result.versions.first { it.song.id == 1 }
        val mp3 = result.versions.first { it.song.id == 2 }
        assertTrue(flac.qualityScore > mp3.qualityScore)
    }

    @Test
    fun sampleRateRowBarIsNormalizedToMaxRate() {
        val result = compare(
            version(1, sampleRate = 96_000, ext = "flac"),
            version(2, sampleRate = 48_000, ext = "mp3"),
        )

        val sampleRow = result.rows.first { it.label == "采样率" }
        assertEquals(1.0f, sampleRow.cells[0].bar ?: 0f, 0.001f)
        assertEquals(0.5f, sampleRow.cells[1].bar ?: 0f, 0.001f)
    }

    @Test
    fun diffHintSummarizesDifferingRows() {
        val songs = listOf(
            version(1, sampleRate = 96_000, ext = "flac"),
            version(2, sampleRate = 44_100, ext = "mp3"),
        )
        assertEquals("格式、采样率 不同", SongVersionComparer.diffHint(songs) { fileNameOf(it) })
    }

    @Test
    fun diffHintReturnsConsistentWhenIdentical() {
        val songs = listOf(
            version(1, sampleRate = 44_100, ext = "flac"),
            version(2, sampleRate = 44_100, ext = "flac"),
        )
        assertEquals("规格一致", SongVersionComparer.diffHint(songs) { fileNameOf(it) })
    }

    @Test
    fun bitRateRowAppearsOnlyWhenDataAvailable() {
        val songs = listOf(
            version(1, sampleRate = 96_000, ext = "flac"),
            version(2, sampleRate = 44_100, ext = "wav"),
        )

        // 无比特率数据 → 不产生「比特率」行
        val noData = SongVersionComparer.compare(songs, fileNameOf = { fileNameOf(it) })
        assertFalse(noData.rows.any { it.label == "比特率" })
        assertFalse(noData.commonFields.any { it.first == "比特率" })

        // 有比特率数据 → 生成行，缺失的显示「未知」
        val bitRates = mapOf(1 to "2304 kbps", 2 to "")
        val withData = SongVersionComparer.compare(songs, fileNameOf = { fileNameOf(it) }, bitRateOf = { song ->
            bitRates[song.id].orEmpty()
        })
        val bitRow = withData.rows.first { it.label == "比特率" }
        assertEquals(listOf("2304 kbps", "未知"), bitRow.cells.map { it.display })
    }

    @Test
    fun bitRateRowMovesToCommonWhenAllIdentical() {
        val songs = listOf(
            version(1, sampleRate = 96_000, ext = "flac"),
            version(2, sampleRate = 44_100, ext = "wav"),
        )
        val withData = SongVersionComparer.compare(
            songs,
            fileNameOf = { fileNameOf(it) },
            bitRateOf = { "1411 kbps" },
        )
        assertTrue(withData.commonFields.any { it.first == "比特率" })
        assertEquals("1411 kbps", withData.commonFields.first { it.first == "比特率" }.second)
    }

    @Test
    fun recommendedFollowsQualityScoreNotRawSampleRate() {
        // MP3 48k 采样率更高，但无损加权后 FLAC 44.1k 评分更高 → 推荐应为 FLAC
        val result = compare(
            version(1, sampleRate = 48_000, ext = "mp3"),
            version(2, sampleRate = 44_100, ext = "flac"),
        )

        val recommended = result.versions.first { it.isRecommended }
        assertEquals(2, recommended.song.id)
        assertEquals(1, result.recommendedIndex)
        assertTrue(result.versions[1].isRecommended)
        assertFalse(result.versions[0].isRecommended)
    }

    @Test
    fun recommendedTieBreaksByHigherBitRate() {
        // 同采样率同格式 → 音质评分相同，推荐比特率更高者
        val songs = listOf(
            version(1, sampleRate = 96_000, ext = "flac"),
            version(2, sampleRate = 96_000, ext = "flac"),
        )
        val bitRates = mapOf(1 to "2304 kbps", 2 to "4608 kbps")
        val result = SongVersionComparer.compare(
            songs,
            fileNameOf = { fileNameOf(it) },
            bitRateOf = { song -> bitRates[song.id].orEmpty() },
        )

        assertEquals(1, result.recommendedIndex)
        assertTrue(result.versions[1].isRecommended)
    }

    @Test
    fun fileSizeRowAppearsOnlyWhenDataAvailable() {
        val songs = listOf(
            version(1, sampleRate = 96_000, ext = "flac"),
            version(2, sampleRate = 44_100, ext = "wav"),
        )

        val noData = SongVersionComparer.compare(songs, fileNameOf = { fileNameOf(it) })
        assertFalse(noData.rows.any { it.label == "文件大小" })
        assertFalse(noData.commonFields.any { it.first == "文件大小" })

        val sizes = mapOf(1 to "98.6 MB", 2 to "")
        val withData = SongVersionComparer.compare(
            songs,
            fileNameOf = { fileNameOf(it) },
            fileSizeOf = { song -> sizes[song.id].orEmpty() },
        )
        val sizeRow = withData.rows.first { it.label == "文件大小" }
        assertEquals(listOf("98.6 MB", "未知"), sizeRow.cells.map { it.display })
    }

    private fun fileNameOf(song: Song): String =
        exts[song.id]?.let { "audio/file.$it" } ?: ""

    private fun compare(vararg songs: Song) =
        SongVersionComparer.compare(songs.toList(), fileNameOf = { fileNameOf(it) })

    private fun version(
        id: Int,
        sampleRate: Int = 44_100,
        ext: String = "flac",
        artist: String = "Artist",
        album: String = "",
    ): Song {
        exts[id] = ext
        return Song(
            id = id,
            title = "Song",
            artist = artist,
            album = album,
            sampleRate = sampleRate,
        )
    }
}
