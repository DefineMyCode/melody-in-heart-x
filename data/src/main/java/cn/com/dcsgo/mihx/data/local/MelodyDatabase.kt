package cn.com.dcsgo.mihx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.AlbumEntity
import cn.com.dcsgo.mihx.data.local.entity.ArtistEntity
import cn.com.dcsgo.mihx.data.local.entity.MigrationStateEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaybackEventEntity
import cn.com.dcsgo.mihx.data.local.entity.PlayStatsEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistEntity
import cn.com.dcsgo.mihx.data.local.entity.PlaylistSongCrossRef
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import cn.com.dcsgo.mihx.data.local.entity.SongArtistCrossRef
import cn.com.dcsgo.mihx.data.local.entity.SongEmotionEntity
import cn.com.dcsgo.mihx.data.local.entity.SongEntity
import cn.com.dcsgo.mihx.data.local.entity.SongGroupOverrideEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        PlayStatsEntity::class,
        PlaybackEventEntity::class,
        QuickSkipSongEntity::class,
        QuickSkipShortPlayEntity::class,
        SongGroupOverrideEntity::class,
        MigrationStateEntity::class,
        ArtistEntity::class,
        AlbumEntity::class,
        SongArtistCrossRef::class,
        SongEmotionEntity::class,
    ],
    version = 10,
    exportSchema = true,
)
abstract class MelodyDatabase : RoomDatabase() {
    abstract fun melodyDao(): MelodyDao
}
