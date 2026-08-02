package cn.com.dcsgo.mihx.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Persists the user's preferred version per same-title group (plan P5-C5), keyed by
 * [cn.com.dcsgo.mihx.core.model.Song.groupKey]. Backed by Room table `song_group_overrides`
 * (shipped since DB v1).
 */
interface SongGroupOverrideRepository {
    /** Preferred version id for [groupKey], or null when the group follows automatic selection. */
    suspend fun getPreferredSongId(groupKey: String): Long?

    /** Pins [songId] as the preferred version of [groupKey]. */
    suspend fun setPreferredSongId(groupKey: String, songId: Long)

    /** Drops the override so the group falls back to automatic selection again. */
    suspend fun clearPreferredSongId(groupKey: String)

    /** Live map of groupKey -> preferred song id (empty map when no overrides exist). */
    fun observeOverrides(): Flow<Map<String, Long>>
}
