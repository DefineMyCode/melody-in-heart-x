package cn.com.dcsgo.mihx.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import cn.com.dcsgo.mihx.data.database.entity.SongEntity

/**
 * Single aggregate DAO (Phase 4 evolves per aggregate root).
 * Placeholder for Phase 0 — full queries land in P4.
 */
@Dao
interface MelodyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity): Long

    @Query("SELECT * FROM songs")
    suspend fun getAllSongs(): List<SongEntity>
}
