package cn.com.dcsgo.mihx.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 歌曲-歌手多对多关联。
 *
 * 一首歌可对应多个歌手（拆分后的原子歌手），一个歌手可对应多首歌。
 */
@Entity(
    tableName = "song_artist_cross_ref",
    primaryKeys = ["songId", "artistId"],
    indices = [Index("artistId")],
)
data class SongArtistCrossRef(
    val songId: Int,
    val artistId: Int,
)
