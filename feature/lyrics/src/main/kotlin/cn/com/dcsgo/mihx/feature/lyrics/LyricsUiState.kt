package cn.com.dcsgo.mihx.feature.lyrics

import cn.com.dcsgo.mihx.core.model.Lyrics

/** UI state for the 歌词 screen. */
data class LyricsUiState(
    /** Currently playing song id, or null when the player is empty. */
    val songId: Long? = null,
    /** Loaded lyrics, or null when none are available for the current song. */
    val lyrics: Lyrics? = null,
    /** Index of the line active at the current playback position, or -1. */
    val activeIndex: Int = -1,
    val isLoading: Boolean = false,
)
