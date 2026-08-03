package cn.com.dcsgo.mihx.domain.playback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeekCoordinatorTest {

    private val session = FakeSeekSession()
    private var controllerIsPlaying: Boolean? = null
    private var currentIsPlaying = false
    private val coordinator = SeekCoordinator(
        session = session,
        controllerIsPlaying = { controllerIsPlaying },
        currentIsPlaying = { currentIsPlaying },
    )

    @Test
    fun endSeekingEndsTrackingAndSeeksToPosition() {
        val result = coordinator.endSeeking(42L)

        assertTrue(session.endedSeeking)
        assertEquals(42L, session.seekPosition)
        assertEquals(42L, result.positionMs)
        assertTrue(result.shouldSyncIsPlaying)
    }

    @Test
    fun seekToOnlyMovesPlayerPosition() {
        val result = coordinator.seekTo(99L)

        assertEquals(99L, session.seekPosition)
        assertEquals(99L, result.positionMs)
    }

    @Test
    fun syncedIsPlayingReturnsNullWhenControllerMatchesCurrentState() {
        currentIsPlaying = true
        controllerIsPlaying = true

        assertNull(coordinator.syncedIsPlayingAfterSeek())
    }

    @Test
    fun syncedIsPlayingReturnsActualControllerStateWhenDifferent() {
        currentIsPlaying = false
        controllerIsPlaying = true

        assertEquals(true, coordinator.syncedIsPlayingAfterSeek())
    }

    @Test
    fun syncedIsPlayingUsesCurrentStateWhenControllerIsUnavailable() {
        currentIsPlaying = false
        controllerIsPlaying = null

        assertNull(coordinator.syncedIsPlayingAfterSeek())
    }

    private class FakeSeekSession : SeekPlaybackSession {
        var endedSeeking = false
        var seekPosition: Long? = null

        override fun startSeeking() = Unit

        override fun endSeeking() {
            endedSeeking = true
        }

        override fun seekTo(positionMs: Long) {
            seekPosition = positionMs
        }
    }
}
