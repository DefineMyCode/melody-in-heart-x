package cn.com.dcsgo.mihx.domain.model

/** 歌单上次播放记录:songId = 上次播放的歌曲,updatedAtMs = 记录时间(epoch 毫秒) */
data class PlaylistResume(
    val songId: Int,
    val updatedAtMs: Long,
)
