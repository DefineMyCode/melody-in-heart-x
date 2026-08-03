package cn.com.dcsgo.mihx.core.model

/**
 * 曲库歌手/专辑聚合。
 *
 * 优先从持久化的 artists / albums / song_artist_cross_ref 表查询；
 * 无 Room 的旧版内存路径下使用 [deriveArtists] / [deriveAlbums] 派生。
 */
object LibraryCatalog {

    /** 从歌曲列表聚合出歌手列表（按歌手名排序，歌手为拆分后的最小单位） */
    fun deriveArtists(songs: List<Song>): List<ArtistEntry> {
        val byArtist = mutableMapOf<String, MutableList<Song>>()
        songs.forEach { song ->
            song.parsedArtists.forEach { artistName ->
                byArtist.getOrPut(artistName) { mutableListOf() }.add(song)
            }
        }
        return byArtist
            .map { (name, list) ->
                ArtistEntry(
                    name = name,
                    songCount = list.size,
                    albumCount = list.map { it.album }.filter { it.isNotBlank() }.distinct().size,
                    coverUri = list.firstNotNullOfOrNull { it.albumArtUri },
                )
            }
            .sortedBy { it.name }
    }

    /** 从歌曲列表聚合出专辑列表（按专辑名排序，跳过空专辑名） */
    fun deriveAlbums(songs: List<Song>): List<AlbumEntry> {
        return songs
            .filter { it.album.isNotBlank() }
            .groupBy { it.album }
            .map { (name, list) ->
                AlbumEntry(
                    name = name,
                    artistNames = list.flatMap { it.parsedArtists }.distinct(),
                    songCount = list.size,
                    coverUri = list.firstNotNullOfOrNull { it.albumArtUri },
                )
            }
            .sortedBy { it.name }
    }
}
