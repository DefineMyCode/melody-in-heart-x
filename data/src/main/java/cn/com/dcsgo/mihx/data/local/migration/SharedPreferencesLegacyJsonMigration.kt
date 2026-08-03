package cn.com.dcsgo.mihx.data.local.migration

import android.content.Context
import android.content.SharedPreferences
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity

class SharedPreferencesLegacyJsonMigration(
    context: Context? = null,
    private val dao: MelodyDao,
    private val parser: LegacyJsonSnapshotParser = LegacyJsonSnapshotParser(),
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sharedPreferencesProvider: (String) -> SharedPreferences = { name ->
        requireNotNull(context) {
            "Context is required when sharedPreferencesProvider is not supplied"
        }.getSharedPreferences(name, Context.MODE_PRIVATE)
    },
) : LegacyJsonMigration {
    override suspend fun migrateIfNeeded() {
        if (dao.migrationState(LEGACY_MIGRATION_NAME) != null) return

        val musicPrefs = sharedPreferencesProvider(LEGACY_PREFS_NAME)
        val playStatsPrefs = sharedPreferencesProvider(PLAY_STATS_PREFS_NAME)
        val quickSkipPrefs = sharedPreferencesProvider(QUICK_SKIP_PREFS_NAME)
        val now = clockMs()

        val snapshot = parser.parse(
            songsJson = musicPrefs.getString(KEY_SONGS_JSON, null),
            playlistsJson = musicPrefs.getString(KEY_PLAYLISTS_JSON, null),
            playStatsPrefs = playStatsPrefs.all,
            quickSkipPrefs = quickSkipPrefs.all,
            nowMs = now,
        )

        if (snapshot.songs.isNotEmpty()) {
            dao.upsertSongs(snapshot.songs)
        }
        if (snapshot.songGroupOverrides.isNotEmpty()) {
            dao.upsertSongGroupOverrides(snapshot.songGroupOverrides)
        }
        if (snapshot.playlists.isNotEmpty()) {
            dao.upsertPlaylists(snapshot.playlists)
        }
        if (snapshot.playlistSongRefs.isNotEmpty()) {
            dao.upsertPlaylistSongs(snapshot.playlistSongRefs)
        }
        if (snapshot.playStats.isNotEmpty()) {
            dao.upsertPlayStats(snapshot.playStats)
        }
        if (snapshot.quickSkipSongs.isNotEmpty()) {
            dao.upsertQuickSkipSongs(snapshot.quickSkipSongs)
        }
        snapshot.quickSkipShortPlayCounts.forEach { count ->
            dao.upsertQuickSkipShortPlay(count)
        }
        dao.upsertMigrationState(MigrationStateEntity(LEGACY_MIGRATION_NAME, now))
    }

    private companion object {
        const val KEY_PLAYLISTS_JSON = "playlists_json"
        const val KEY_SONGS_JSON = "songs_json"
        const val PLAY_STATS_PREFS_NAME = "play_stats_prefs"
        const val QUICK_SKIP_PREFS_NAME = "quick_skip_songs_prefs"
    }
}
