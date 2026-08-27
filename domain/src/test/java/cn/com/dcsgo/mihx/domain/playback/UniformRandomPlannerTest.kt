package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UniformRandomPlannerTest {

    @Test
    fun disabledUsesInjectedShuffle() {
        val planner = UniformRandomPlanner(shuffle = { it.asReversed() })

        val selected = planner.selectSongs(
            songs = songs(1, 2, 3),
            neededSize = 2,
            uniformRandomEnabled = false,
            playCounts = mapOf(1 to 0, 2 to 0, 3 to 0),
        )

        assertEquals(listOf(3, 2), selected.map { it.id })
    }

    @Test
    fun enabledPrefersLowestRawPlayCountPool() {
        val planner = UniformRandomPlanner(shuffle = { it })
        val songs = songs(*(1..80).toList().toIntArray())
        val playCounts = songs.associate { song ->
            song.id to if (song.id <= 60) 0 else 10
        }

        val selected = planner.selectSongs(
            songs = songs,
            neededSize = 20,
            uniformRandomEnabled = true,
            playCounts = playCounts,
        )

        assertEquals((1..20).toList(), selected.map { it.id })
    }

    @Test
    fun enabledFillsFromLowestTierOnlyWhenEnoughSongs() {
        // 层0(0次组)恰好满足名额时,完全不碰高次数层
        val planner = UniformRandomPlanner(shuffle = { it.asReversed() })

        val selected = planner.selectSongs(
            songs = songs(1, 2, 3),
            neededSize = 2,
            uniformRandomEnabled = true,
            playCounts = mapOf(1 to 10, 2 to 0, 3 to 0),
        )

        assertEquals(listOf(3, 2), selected.map { it.id })
    }

    @Test
    fun enabledFallsBackToHigherTierWhenLowestTierNotEnough() {
        // 层0 不足名额 → 全取层0,层1 补齐
        val planner = UniformRandomPlanner(shuffle = { it })

        val selected = planner.selectSongs(
            songs = songs(1, 2, 3, 4, 5),
            neededSize = 4,
            uniformRandomEnabled = true,
            playCounts = mapOf(1 to 0, 2 to 0, 3 to 5, 4 to 5, 5 to 5),
        )

        assertEquals(listOf(1, 2, 3, 4), selected.map { it.id })
    }

    @Test
    fun enabledKeepsTieringWhenAllSongsPlayedBeyondFixedThreshold() {
        // 用户场景:全部已播放且都超过固定阈值,动态分层依然有效(最小次数组非空)
        val planner = UniformRandomPlanner(shuffle = { it })
        val songs = songs(*(1..10).toList().toIntArray())
        val playCounts = songs.associate { song ->
            song.id to if (song.id <= 4) 6 else 20
        }

        val selected = planner.selectSongs(
            songs = songs,
            neededSize = 5,
            uniformRandomEnabled = true,
            playCounts = playCounts,
        )

        assertEquals(listOf(1, 2, 3, 4, 5), selected.map { it.id })
    }

    @Test
    fun enabledTreatsEqualPlayCountsAsUniformRandom() {
        // 全体次数相同 → 单层,等价纯随机
        val planner = UniformRandomPlanner(shuffle = { it.asReversed() })

        val selected = planner.selectSongs(
            songs = songs(1, 2, 3, 4),
            neededSize = 3,
            uniformRandomEnabled = true,
            playCounts = mapOf(1 to 5, 2 to 5, 3 to 5, 4 to 5),
        )

        assertEquals(listOf(4, 3, 2), selected.map { it.id })
    }

    @Test
    fun enabledSkewedDistributionFillsFromZeroCountGroup() {
        // 单极分布:1 首高次数 + 大量 0 次 → 全部来自 0 次组
        val planner = UniformRandomPlanner(shuffle = { it })
        val songs = songs(*(1..10).toList().toIntArray())
        val playCounts = songs.associate { song ->
            song.id to if (song.id == 1) 1000 else 0
        }

        val selected = planner.selectSongs(
            songs = songs,
            neededSize = 5,
            uniformRandomEnabled = true,
            playCounts = playCounts,
        )

        assertTrue(selected.none { it.id == 1 })
    }

    @Test
    fun fullOrderGroupsByRawPlayCountAndShufflesWithinEachGroup() {
        val planner = UniformRandomPlanner(shuffle = { it.asReversed() })

        val ordered = planner.orderSongs(
            songs = songs(1, 2, 3, 4),
            uniformRandomEnabled = true,
            playCounts = mapOf(1 to 1, 2 to 0, 3 to 1, 4 to 0),
        )

        assertEquals(listOf(4, 2, 3, 1), ordered.map { it.id })
    }

    @Test
    fun shuffleModeKeepsCurrentFirstAndUniformsTheRest() {
        val planner = UniformRandomPlanner(shuffle = { it.asReversed() })
        val songs = songs(1, 2, 3, 4)
        val playCounts = mapOf(2 to 1, 3 to 0, 4 to 0)

        val order = planner.buildPlayOrderIds(
            songs = songs,
            startIndex = 0,
            mode = PlayMode.SHUFFLE,
            uniformRandomEnabled = true,
            playCounts = playCounts,
        )

        assertEquals(1, order.first())
        assertEquals(listOf(4, 3, 2), order.drop(1))
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
            sampleRate = 44_100,
        )
    }
}
