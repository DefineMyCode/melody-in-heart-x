package cn.com.dcsgo.mihx.core.model

data class Playlist(
    val id: Int,
    val name: String,
    val songCount: Int,
    val songIds: MutableList<Int> = mutableListOf(),
    val coverArt: Int = android.R.drawable.ic_media_play
)
