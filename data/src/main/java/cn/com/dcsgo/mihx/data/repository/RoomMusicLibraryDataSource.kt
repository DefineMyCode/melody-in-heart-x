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

/**
 * 播放事件保留期（评审 2026-10-27 · M-6）。
 *
 * 12 个月：覆盖「今日/本周/本月」全部统计窗口（最远 1 个月），
 * 同时把纯追加表 `playback_events` 的规模限制在可接受范围内。
 */
private const val PLAYBACK_EVENT_RETENTION_MS = 365L * 24 * 60 * 60 * 1000

/** 早于该时间戳的播放事件应被清理。 */
internal fun playbackEventRetentionCutoff(now: Long = System.currentTimeMillis()): Long =
    now - PLAYBACK_EVENT_RETENTION_MS

class RoomMusicLibraryDataSource(
    private val dao: MelodyDao,
) {
    suspend fun restore(): RestoredMusicLibrary {
        val songOverrides = dao.songGroupOverrides().associateBy { it.songId }
        // 评审 2026-10-27 · M-7：实体只读一次。
        // 原先 `dao.songs()` 被调了两遍（:22 物化全部行 + :44 再物化一次只为拿
        // albumId 映射），而 restore() 在**启动关键路径**上（「启动中…」页在等它），
        // 每次全表物化都要把每首歌的 uri / albumArtCacheUri / lrcUri 等全部列读进内存。
        val songEntities = dao.songs()
        val restoredSongs = songEntities.map { entity ->
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

        // 首次加载（或旧版本升级后）同步歌手/专辑目录，并将关联写回内存歌曲
        dao.syncLibraryCatalog(restoredSongs)
        val artistIdsBySong = dao.songArtistRefs().groupBy { it.songId }
            .mapValues { (_, refs) -> refs.map { it.artistId } }
        // M-7：albumId 映射只取两列（`songs()` 是 SELECT *，会把每首歌的
        // uri / albumArtCacheUri / lrcUri 等全部列物化进内存）。
        // 注意必须在 syncLibraryCatalog **之后**读 —— 它会重建专辑目录并把
        // albumId 回写到 songs 表。
        val albumIdBySongId = dao.songAlbumIds().associate { it.id to it.albumId }
        val catalogedSongs = restoredSongs.map { song ->
            song.copy(
                albumId = albumIdBySongId[song.id],
                artistIds = artistIdsBySong[song.id].orEmpty(),
            )
        }

        return RestoredMusicLibrary(
            songs = catalogedSongs,
            playlists = restoredPlaylists,
        )
    }

    suspend fun persistSongs(songs: List<Song>, importedAt: Long) {
        dao.replaceSongs(
            songs = songs.map { it.toEntity(importedAt) },
            overrides = songs.mapNotNull { it.toOverrideEntity(importedAt) },
        )
        dao.syncLibraryCatalog(songs)
        // 评审 2026-10-27 · M-6：播放事件保留策略。
        // playback_events 是纯追加表，全项目原本**没有任何清理逻辑**，会随应用
        // 生命周期无限增长；而删歌/去重/文件校验时的「按 songId 清理」每次都要
        // 扫这张表。这里在每次落盘（导入/对账）时顺手删掉超出保留期的事件。
        dao.deletePlaybackEventsBefore(playbackEventRetentionCutoff())
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
    album = album,
    albumId = albumId,
    sampleRate = sampleRate,
    durationMs = durationMs,
    uri = uri?.let(Uri::parse),
    albumArtUri = albumArtCacheUri?.let(Uri::parse),
    lrcUri = lrcUri?.let(Uri::parse),
    titleOverride = titleOverride,
    fileFingerprint = if (size != null || lastModified != null) "$size:$lastModified" else null,
)

private fun Song.toEntity(importedAt: Long): SongEntity = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    albumId = albumId,
    sampleRate = sampleRate,
    durationMs = durationMs,
    uri = uri?.toString(),
    displayName = null,
    mimeType = null,
    lastModified = fileFingerprint?.substringAfterLast(':')?.toLongOrNull(),
    size = fileFingerprint?.substringBeforeLast(':')?.toLongOrNull(),
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
