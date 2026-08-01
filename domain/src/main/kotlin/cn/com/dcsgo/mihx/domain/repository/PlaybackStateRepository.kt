package cn.com.dcsgo.mihx.domain.repository

interface PlaybackStateRepository {
    suspend fun saveCurrentMediaId(mediaId: String)
    suspend fun savePosition(positionMs: Long)
}
