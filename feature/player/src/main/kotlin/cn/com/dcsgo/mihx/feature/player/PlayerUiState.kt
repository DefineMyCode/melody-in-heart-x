package cn.com.dcsgo.mihx.feature.player

/** UI state for the 播放 screen. */
data class PlayerUiState(
    val isLoading: Boolean = false,
) {
    companion object {
        /** Default (empty) state. Construction lives here per architecture gate [A4]. */
        val empty: PlayerUiState = PlayerUiState()
    }
}
