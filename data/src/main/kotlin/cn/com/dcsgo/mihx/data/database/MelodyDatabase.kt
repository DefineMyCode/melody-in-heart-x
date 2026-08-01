package cn.com.dcsgo.mihx.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.database.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.database.entity.PlaylistSongCrossRefEntity
import cn.com.dcsgo.mihx.data.database.entity.ShortPlayCountEntity
import cn.com.dcsgo.mihx.data.database.entity.SkipSongEntity
import cn.com.dcsgo.mihx.data.database.entity.SongEntity
import cn.com.dcsgo.mihx.data.database.entity.SongGroupOverrideEntity

/**
 * Room database. Phase 4 ships the 8 planned entities in schema version 1
 * (the SongEntity-only placeholder from Phase 0 is replaced here).
 */
@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRefEntity::class,
        PlayStatsEntity::class,
        SkipSongEntity::class,
        ShortPlayCountEntity::class,
        SongGroupOverrideEntity::class,
        MigrationStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MelodyDatabase : RoomDatabase() {
    abstract fun melodyDao(): MelodyDao

    companion object {
        const val DATABASE_NAME = "melody"
    }
}
