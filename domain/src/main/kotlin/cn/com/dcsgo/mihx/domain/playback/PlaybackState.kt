package cn.com.dcsgo.mihx.domain.playback

/** Canonical playback state machine states. */
enum class PlaybackState {
    IDLE,
    PREPARING,
    READY,
    PLAYING,
    PAUSED,
    BUFFERING,
    ENDED,
    ERROR,
}
