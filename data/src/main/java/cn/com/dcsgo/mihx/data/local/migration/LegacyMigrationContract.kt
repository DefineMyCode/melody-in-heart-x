package cn.com.dcsgo.mihx.data.local.migration

const val LEGACY_PREFS_NAME = "music_player_prefs"
const val LEGACY_MIGRATION_NAME = "shared_preferences_json_v1"

interface LegacyJsonMigration {
    suspend fun migrateIfNeeded()
}
