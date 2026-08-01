package cn.com.dcsgo.mihx.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.com.dcsgo.mihx.data.database.dao.MelodyDao
import cn.com.dcsgo.mihx.data.database.entity.SongEntity

/**
 * Room database. Phase 4 expands to the 8 planned entities; schema version starts at 1.
 */
@Database(
    entities = [SongEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class MelodyDatabase : RoomDatabase() {
    abstract fun melodyDao(): MelodyDao

    companion object {
        const val DATABASE_NAME = "melody"
    }
}
