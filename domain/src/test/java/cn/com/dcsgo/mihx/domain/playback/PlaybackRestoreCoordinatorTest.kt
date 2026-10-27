package cn.com.dcsgo.mihx.domain.playback

import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PlaybackRestoreCoordinatorTest {

    private var restoredState: RestoredPlaybackState? = null
    private val coordinator = PlaybackRestoreCoordinator(
        restoreState = { restoredState },
        isPlayable = { it.sampleRate > 0 },
    )

    @Test
    fun restoreReturnsNullWhenStoreHasNoState() = runBlocking {
        restoredState = null

        assertNull(coordinator.restore(songs(1)))
    }

    @Test
    fun restoreReturnsQueueWithoutPlayableSessionWhenCurrentSongIsMissing() = runBlocking {
        val queue = PlayQueue(songs = songs(1), currentIndex = -1)
        restoredState = RestoredPlaybackState(queue, positionMs = 42L)

        val result = coordinator.restore(songs(1))

        assertEquals(queue, result?.queue)
        assertNull(result?.playableSession)
    }

    @Test
    fun restoreReturnsQueueWithoutPlayableSessionWhenCurrentSongIsNotPlayable() = runBlocking {
        val queue = PlayQueue().setQueue(listOf(song(1, sampleRate = 0)), startIndex = 0)
        restoredState = RestoredPlaybackState(queue, positionMs = 42L)

        val result = coordinator.restore(queue.songs)

        assertEquals(queue, result?.queue)
        assertNull(result?.playableSession)
    }

    @Test
    fun restoreBuildsPlayableSessionForCurrentSong() = runBlocking {
        val current = song(1, title = "Song", sampleRate = 44_100)
        val higherRate = song(2, title = "Song", sampleRate = 96_000)
        val songs = listOf(current, higherRate, song(3, title = "Other"))
        val queue = PlayQueue().setQueue(songs.take(2), startIndex = 0)
        restoredState = RestoredPlaybackState(queue, positionMs = 42L)

        val result = coordinator.restore(songs)

        assertEquals(queue, result?.queue)
        assertEquals(current, result?.playableSession?.song)
        assertEquals(42L, result?.playableSession?.positionMs)
        assertEquals(listOf(2, 1), result?.playableSession?.sameNameSongs?.map { it.id })
    }

    @Test
    fun restoreKeepsInfinitePlayState() = runBlocking {
        val queue = PlayQueue().setQueue(songs(1, 2), startIndex = 0)
        restoredState = RestoredPlaybackState(
            queue = queue,
            positionMs = 42L,
            isInfinitePlay = true,
            infinitePlayedSongIds = setOf(1, 2),
        )

        val result = coordinator.restore(queue.songs)

        assertEquals(true, result?.isInfinitePlay)
        assertEquals(setOf(1, 2), result?.infinitePlayedSongIds)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { song(it) }

    private fun song(
        id: Int,
        title: String = "Song $id",
        sampleRate: Int = 44_100,
    ): Song {
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            sampleRate = sampleRate,
        )
    }
}
