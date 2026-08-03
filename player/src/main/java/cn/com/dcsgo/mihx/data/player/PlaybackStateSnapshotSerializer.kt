package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.json.JSONArray
import org.json.JSONObject

data class StoredPlaybackSnapshot(
    val queueJson: String,
    val positionMs: Long,
    val isInfinitePlay: Boolean,
    val currentSongId: Int?,
    val infinitePlayedIdsJson: String?,
)

class PlaybackStateSnapshotSerializer {
    fun encodeQueue(queue: PlayQueue): String {
        return JSONObject()
            .put(KEY_SONG_IDS, queue.songs.map { it.id }.toJsonArray())
            .put(KEY_CURRENT_INDEX, queue.currentIndex)
            .put(KEY_PLAY_MODE, queue.playMode.name)
            .put(KEY_PLAY_ORDER_IDS, queue.currentPlayOrderIds().toJsonArray())
            .toString()
    }

    fun encodeIds(ids: Collection<Int>): String = ids.sorted().toJsonArray().toString()

    fun decodeInfinitePlayedIds(json: String?, availableSongIds: Set<Int>): Set<Int> {
        if (json.isNullOrBlank()) return emptySet()
        return runCatching {
            JSONArray(json)
                .toIntList()
                .filter { it in availableSongIds }
                .toSet()
        }.getOrDefault(emptySet())
    }

    fun decodeQueue(json: String, allSongs: List<Song>, allowEmpty: Boolean): PlayQueue? {
        return runCatching {
            val obj = JSONObject(json)
            val songIds = obj.optJSONArray(KEY_SONG_IDS)?.toIntList().orEmpty()
            val currentIndex = obj.optInt(KEY_CURRENT_INDEX, MISSING_INDEX)
            if (currentIndex == MISSING_INDEX) return null
            val playMode = obj.optString(KEY_PLAY_MODE)
                .takeIf { it.isNotBlank() }
                ?.let { name -> runCatching { PlayMode.valueOf(name) }.getOrDefault(PlayMode.DEFAULT) }
                ?: PlayMode.DEFAULT
            val playOrderIds = obj.optJSONArray(KEY_PLAY_ORDER_IDS)?.toIntList().orEmpty()

            val songMap = allSongs.associateBy { it.id }
            val matchedSongs = songIds.mapNotNull { songMap[it] }
            if (matchedSongs.isEmpty()) {
                return if (allowEmpty) PlayQueue(playMode = playMode) else null
            }

            val safeIndex = currentIndex.coerceIn(0, matchedSongs.lastIndex)
            val matchedIds = matchedSongs.map { it.id }.toSet()
            val restoredOrderIds = playOrderIds.filter { it in matchedIds }

            PlayQueue()
                .setQueue(matchedSongs, safeIndex, playMode)
                .let { queue ->
                    val completeOrderIds = (restoredOrderIds + queue.currentPlayOrderIds())
                        .filter { it in matchedIds }
                    queue.copy(playOrderIds = completeOrderIds.take(matchedSongs.size))
                }
        }.getOrNull()
    }

    private fun Collection<Int>.toJsonArray(): JSONArray {
        return JSONArray().also { array ->
            forEach(array::put)
        }
    }

    private fun JSONArray.toIntList(): List<Int> {
        return buildList {
            for (index in 0 until length()) {
                val value = opt(index)
                when (value) {
                    is Number -> add(value.toInt())
                    is String -> value.toIntOrNull()?.let(::add)
                }
            }
        }
    }

    companion object {
        private const val KEY_SONG_IDS = "songIds"
        private const val KEY_CURRENT_INDEX = "currentIndex"
        private const val KEY_PLAY_MODE = "playMode"
        private const val KEY_PLAY_ORDER_IDS = "playOrderIds"
        private const val MISSING_INDEX = Int.MIN_VALUE
    }
}
