package cn.com.dcsgo.mihx.data.repository

import android.net.Uri
import cn.com.dcsgo.mihx.core.model.Playlist
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity

data class RestoredMusicLibrary(
    val songs: List<Song>,
    val playlists: List<Playlist>,
)

class RoomMusicLibraryDataSource(
    private val dao: MelodyDao,
) {
    suspend fun restore(): RestoredMusicLibrary {
        val songOverrides = dao.songGroupOverrides().associateBy { it.songId }
        val restoredSongs = dao.songs().map { entity ->
            entity.toSong(songOverrides[entity.id]?.titleOverride)
        }
        val restoredSongIds = restoredSongs.mapTo(mutableSetOf()) { it.id }
        val refsByPlaylistId = dao.playlistSongRefs().groupBy { it.playlistId }
        val restoredPlaylists = dao.playlists().map { playlist ->
            val songIds = refsByPlaylistId[playlist.id].orEmpty()
                .mapNotNullTo(mutableListOf()) { ref ->
                    ref.songId.takeIf { it in restoredSongIds }
                }
            Playlist(
                id = playlist.id,
                name = playlist.name,
                songCount = songIds.size,
                songIds = songIds,
            )
        }

        return RestoredMusicLibrary(
            songs = restoredSongs,
            playlists = restoredPlaylists,
        )
    }

    suspend fun persistSongs(songs: List<Song>, importedAt: Long) {
        dao.replaceSongs(
            songs = songs.map { it.toEntity(importedAt) },
            overrides = songs.mapNotNull { it.toOverrideEntity(importedAt) },
        )
    }

    suspend fun persistPlaylists(playlists: List<Playlist>, updatedAt: Long) {
        dao.replacePlaylists(
            playlists = playlists.map { it.toEntity(updatedAt) },
            refs = playlists.flatMap { playlist ->
                playlist.songIds.mapIndexed { index, songId ->
                    PlaylistSongCrossRef(
                        playlistId = playlist.id,
                        songId = songId,
                        sortOrder = index,
                    )
                }
            },
        )
    }
}

private fun SongEntity.toSong(titleOverride: String?): Song = Song(
    id = id,
    title = title,
    artist = artist,
    sampleRate = sampleRate,
    uri = uri?.let(Uri::parse),
    albumArtUri = albumArtCacheUri?.let(Uri::parse),
    lrcUri = lrcUri?.let(Uri::parse),
    titleOverride = titleOverride,
)

private fun Song.toEntity(importedAt: Long): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    sampleRate = sampleRate,
    uri = uri?.toString(),
    displayName = null,
    mimeType = null,
    lastModified = null,
    size = null,
    sourceTreeUri = null,
    albumArtCacheUri = albumArtUri?.toString(),
    lrcUri = lrcUri?.toString(),
    importedAt = importedAt,
)

private fun Song.toOverrideEntity(updatedAt: Long): SongGroupOverrideEntity? =
    titleOverride?.let { SongGroupOverrideEntity(songId = id, titleOverride = it, updatedAt = updatedAt) }

private fun Playlist.toEntity(updatedAt: Long): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    createdAt = updatedAt,
    updatedAt = updatedAt,
)
