package cn.com.dcsgo.mihx.data.mapper

import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.database.entity.SongEntity

fun SongEntity.toSong(): Song = Song(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sampleRate = sampleRate,
    albumArtUri = albumArtUri,
    titleOverride = titleOverride,
    playable = playable,
)

fun Song.toEntity(): SongEntity = SongEntity(
    id = id,
    uri = uri,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    sampleRate = sampleRate,
    albumArtUri = albumArtUri,
    titleOverride = titleOverride,
    playable = playable,
)
