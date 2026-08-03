package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
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
    fun enabledUsesAllSongsWhenLibraryIsSmallerThanMinimumPool() {
        val planner = UniformRandomPlanner(shuffle = { it.asReversed() })

        val selected = planner.selectSongs(
            songs = songs(1, 2, 3),
            neededSize = 2,
            uniformRandomEnabled = true,
            playCounts = mapOf(1 to 10, 2 to 0, 3 to 0),
        )

        assertEquals(listOf(1, 3), selected.map { it.id })
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
