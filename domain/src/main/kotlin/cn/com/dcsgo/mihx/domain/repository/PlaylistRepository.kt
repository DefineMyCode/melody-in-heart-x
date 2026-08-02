package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    /** Live playlist list stream. */
    fun observeAll(): Flow<List<Playlist>>

    suspend fun getById(id: Long): Playlist?

    /** Creates a playlist, returning its new row id. */
    suspend fun create(name: String): Long

    suspend fun rename(id: Long, name: String)

    /** Deletes a playlist together with its song membership. */
    suspend fun delete(id: Long)

    /** Ordered songs belonging to a playlist. */
    suspend fun getSongs(playlistId: Long): List<Song>

    /** Appends a song to the end of a playlist (idempotent per composite key). */
    suspend fun addSong(playlistId: Long, songId: Long)

    suspend fun removeSong(playlistId: Long, songId: Long)

    /** Persists a new manual ordering for the playlist's songs. */
    suspend fun reorder(playlistId: Long, orderedSongIds: List<Long>)
}
