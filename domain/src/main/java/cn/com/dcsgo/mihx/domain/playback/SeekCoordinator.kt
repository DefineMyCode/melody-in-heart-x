package cn.com.dcsgo.mihx.domain.playback

interface SeekPlaybackSession {
    fun startSeeking()
    fun endSeeking()
    fun seekTo(positionMs: Long)
}

data class SeekResult(
    val positionMs: Long,
    val shouldSyncIsPlaying: Boolean = true,
)

class SeekCoordinator(
    private val session: SeekPlaybackSession,
    private val controllerIsPlaying: () -> Boolean?,
    private val currentIsPlaying: () -> Boolean,
) {
    fun startSeeking() {
        session.startSeeking()
    }

    fun endSeeking(positionMs: Long): SeekResult {
        session.endSeeking()
        return seekTo(positionMs)
    }

    fun seekTo(positionMs: Long): SeekResult {
        session.seekTo(positionMs)
        return SeekResult(positionMs = positionMs)
    }

    fun syncedIsPlayingAfterSeek(): Boolean? {
        val current = currentIsPlaying()
        val actual = controllerIsPlaying() ?: current
        return actual.takeIf { it != current }
    }
}
