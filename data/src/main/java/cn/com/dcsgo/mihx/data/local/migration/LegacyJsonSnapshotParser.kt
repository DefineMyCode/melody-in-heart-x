package cn.com.dcsgo.mihx.data.local.migration

import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity
import org.json.JSONArray

class LegacyJsonSnapshotParser {
    fun parse(
        songsJson: String?,
        playlistsJson: String?,
        playStatsPrefs: Map<String, Any?>,
        quickSkipPrefs: Map<String, Any?>,
        nowMs: Long,
    ): LegacyJsonSnapshot {
        val songSnapshot = songsJson
            ?.let { runCatching { parseSongs(it, nowMs) }.getOrDefault(SongSnapshot()) }
            ?: SongSnapshot()
        val availableSongIds = songSnapshot.songs.mapTo(mutableSetOf()) { it.id }
        val playlistSnapshot = playlistsJson
            ?.let { runCatching { parsePlaylists(it, nowMs, availableSongIds) }.getOrDefault(PlaylistSnapshot()) }
            ?: PlaylistSnapshot()
        return LegacyJsonSnapshot(
            songs = songSnapshot.songs,
            songGroupOverrides = songSnapshot.overrides,
            playlists = playlistSnapshot.playlists,
            playlistSongRefs = playlistSnapshot.refs,
            playStats = parsePlayStats(playStatsPrefs),
            quickSkipSongs = runCatching {
                parseQuickSkipSongs(quickSkipPrefs[KEY_QUICK_SKIP_SONG_IDS_JSON] as? String, nowMs)
            }
                .getOrDefault(emptyList()),
            quickSkipShortPlayCounts = parseQuickSkipShortPlayCounts(quickSkipPrefs, nowMs),
        )
    }

    private fun parseSongs(json: String, importedAt: Long): SongSnapshot {
        val array = JSONArray(json)
        val songs = mutableListOf<SongEntity>()
        val overrides = mutableListOf<SongGroupOverrideEntity>()
        val seenUris = mutableSetOf<String>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            if (!obj.has("id")) continue
            val songId = obj.optInt("id", -1)
            if (songId < 0) continue
            val uri = obj.optString("uri").ifBlank { null }
            if (uri != null && !seenUris.add(uri)) continue
            songs += SongEntity(
                id = songId,
                title = obj.optString("title", "未知歌曲").ifBlank { "未知歌曲" },
                artist = obj.optString("artist", "未知艺术家"),
                album = obj.optString("album"),
                sampleRate = obj.optInt("sampleRate", 0),
                uri = uri,
                displayName = null,
                mimeType = null,
                lastModified = null,
                size = null,
                sourceTreeUri = null,
                albumArtCacheUri = obj.optString("albumArtUri").ifBlank { null },
                lrcUri = obj.optString("lrcUri").ifBlank { null },
                importedAt = importedAt,
            )
            val titleOverride = obj.optString("titleOverride").ifBlank { null }
            if (titleOverride != null) {
                overrides += SongGroupOverrideEntity(
                    songId = songId,
                    titleOverride = titleOverride,
                    updatedAt = importedAt,
                )
            }
        }
        return SongSnapshot(songs, overrides)
    }

    private fun parsePlaylists(
        json: String,
        now: Long,
        availableSongIds: Set<Int>,
    ): PlaylistSnapshot {
        val array = JSONArray(json)
        val playlists = mutableListOf<PlaylistEntity>()
        val refs = mutableListOf<PlaylistSongCrossRef>()
        for (index in 0 until array.length()) {
            val obj = array.optJSONObject(index) ?: continue
            if (!obj.has("id")) continue
            val playlistId = obj.optInt("id", -1)
            if (playlistId < 0) continue
            playlists += PlaylistEntity(
                id = playlistId,
                name = obj.optString("name", "未命名歌单").ifBlank { "未命名歌单" },
                createdAt = now,
                updatedAt = now,
            )
            val songIds = obj.optJSONArray("songIds") ?: JSONArray()
            val seenSongIds = mutableSetOf<Int>()
            var sortOrder = 0
            for (songIndex in 0 until songIds.length()) {
                val songId = songIds.optInt(songIndex, -1)
                if (songId < 0) continue
                if (songId !in availableSongIds) continue
                if (!seenSongIds.add(songId)) continue
                refs += PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = songId,
                    sortOrder = sortOrder++,
                )
            }
        }
        return PlaylistSnapshot(playlists, refs)
    }

    private fun parsePlayStats(prefs: Map<String, Any?>): List<PlayStatsEntity> {
        val songIds = prefs.keys
            .asSequence()
            .mapNotNull { key ->
                when {
                    key.startsWith(KEY_PLAY_COUNT_PREFIX) -> key.removePrefix(KEY_PLAY_COUNT_PREFIX).toIntOrNull()
                    key.startsWith(KEY_RAW_PLAY_COUNT_PREFIX) -> key.removePrefix(KEY_RAW_PLAY_COUNT_PREFIX).toIntOrNull()
                    key.startsWith(KEY_PLAY_DURATION_PREFIX) -> key.removePrefix(KEY_PLAY_DURATION_PREFIX).toIntOrNull()
                    else -> null
                }
            }
            .toSet()

        return songIds.sorted().map { songId ->
            PlayStatsEntity(
                songId = songId,
                playCount = prefs["$KEY_PLAY_COUNT_PREFIX$songId"].asInt(),
                rawPlayCount = prefs["$KEY_RAW_PLAY_COUNT_PREFIX$songId"].asInt(),
                totalDurationMs = prefs["$KEY_PLAY_DURATION_PREFIX$songId"].asLong(),
                lastPlayedAt = null,
            )
        }
    }

    private fun parseQuickSkipSongs(json: String?, addedAt: Long): List<QuickSkipSongEntity> {
        if (json.isNullOrBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val songId = array.optInt(index, -1)
                if (songId >= 0) {
                    add(QuickSkipSongEntity(songId = songId, addedAt = addedAt))
                }
            }
        }
    }

    private fun parseQuickSkipShortPlayCounts(
        prefs: Map<String, Any?>,
        updatedAt: Long,
    ): List<QuickSkipShortPlayEntity> {
        return prefs.keys
            .asSequence()
            .filter { it.startsWith(KEY_SHORT_PLAY_PREFIX) }
            .mapNotNull { key ->
                val songId = key.removePrefix(KEY_SHORT_PLAY_PREFIX).toIntOrNull() ?: return@mapNotNull null
                val count = prefs[key].asInt()
                if (songId >= 0 && count > 0) {
                    QuickSkipShortPlayEntity(songId = songId, count = count, updatedAt = updatedAt)
                } else {
                    null
                }
            }
            .sortedBy { it.songId }
            .toList()
    }

    private fun Any?.asInt(): Int = when (this) {
        is Int -> this
        is Long -> toInt()
        is Number -> toInt()
        is String -> toIntOrNull() ?: 0
        else -> 0
    }

    private fun Any?.asLong(): Long = when (this) {
        is Long -> this
        is Int -> toLong()
        is Number -> toLong()
        is String -> toLongOrNull() ?: 0L
        else -> 0L
    }

    private data class SongSnapshot(
        val songs: List<SongEntity> = emptyList(),
        val overrides: List<SongGroupOverrideEntity> = emptyList(),
    )

    private data class PlaylistSnapshot(
        val playlists: List<PlaylistEntity> = emptyList(),
        val refs: List<PlaylistSongCrossRef> = emptyList(),
    )

    companion object {
        const val KEY_PLAY_COUNT_PREFIX = "play_count_"
        const val KEY_RAW_PLAY_COUNT_PREFIX = "raw_play_count_"
        const val KEY_PLAY_DURATION_PREFIX = "play_duration_"
        const val KEY_QUICK_SKIP_SONG_IDS_JSON = "quick_skip_song_ids_json"
        const val KEY_SHORT_PLAY_PREFIX = "short_play_count_"
    }
}
