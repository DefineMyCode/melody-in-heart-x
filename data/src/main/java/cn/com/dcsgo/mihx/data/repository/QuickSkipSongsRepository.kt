package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.content.SharedPreferences
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray

private const val TAG = "QuickSkipSongsRepo"
private const val PREFS_NAME = "quick_skip_songs_prefs"
private const val KEY_SONG_IDS_JSON = "quick_skip_song_ids_json"
private const val KEY_SHORT_PLAY_PREFIX = "short_play_count_"

/**
 * 秒切歌曲仓库
 *
 * 用于存储播放时经常没放几秒就直接切下一首的歌曲。
 * 这些歌曲的播放次数加1后会自动从列表中移除。
 * 同时记录短时长播放次数，累计超过2次后自动添加到秒切列表。
 */
class QuickSkipSongsRepository(
    context: Context? = null,
    private val melodyDao: MelodyDao? = null,
) : cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository {

    private val prefs: SharedPreferences? by lazy {
        context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val songIdsSet = mutableSetOf<Int>()

    init {
        refreshSongIds()
    }

    private fun refreshSongIds() {
        val dao = melodyDao
        if (dao != null) {
            val ids = runBlocking(Dispatchers.IO) {
                dao.quickSkipSongs().map { it.songId }
            }
            songIdsSet.clear()
            songIdsSet.addAll(ids)
            AppLog.debug(TAG, "refreshSongIds: loaded ${songIdsSet.size} song ids from Room")
            return
        }
        val json = prefs?.getString(KEY_SONG_IDS_JSON, null) ?: return
        try {
            val jsonArray = JSONArray(json)
            songIdsSet.clear()
            for (i in 0 until jsonArray.length()) {
                songIdsSet.add(jsonArray.getInt(i))
            }
            AppLog.debug(TAG, "loadSongIds: loaded ${songIdsSet.size} song ids")
        } catch (e: Exception) {
            AppLog.error(TAG, "loadSongIds: failed: ${e.message}", e)
        }
    }

    private fun persistSongIds() {
        val jsonArray = JSONArray()
        for (songId in songIdsSet) {
            jsonArray.put(songId)
        }
        requireNotNull(prefs) {
            "QuickSkipSongsRepository requires Context when no MelodyDao is provided."
        }.edit().putString(KEY_SONG_IDS_JSON, jsonArray.toString()).apply()
        AppLog.debug(TAG, "persistSongIds: saved ${songIdsSet.size} song ids")
    }

    override fun contains(songId: Int): Boolean {
        val dao = melodyDao
        if (dao != null) {
            return runBlocking(Dispatchers.IO) {
                dao.quickSkipSong(songId) != null
            }
        }
        return songIdsSet.contains(songId)
    }

    override fun add(songId: Int): Boolean {
        val dao = melodyDao
        if (dao != null) {
            if (contains(songId)) return false
            runBlocking(Dispatchers.IO) {
                dao.upsertQuickSkipSong(
                    QuickSkipSongEntity(
                        songId = songId,
                        addedAt = System.currentTimeMillis(),
                    )
                )
            }
            songIdsSet.add(songId)
            AppLog.debug(TAG, "add: songId=$songId added to Room")
            return true
        }
        if (songIdsSet.add(songId)) {
            persistSongIds()
            AppLog.debug(TAG, "add: songId=$songId added, total=${songIdsSet.size}")
            return true
        }
        return false
    }

    override fun remove(songId: Int): Boolean {
        val dao = melodyDao
        if (dao != null) {
            if (!contains(songId)) return false
            runBlocking(Dispatchers.IO) {
                dao.deleteQuickSkipSong(songId)
            }
            songIdsSet.remove(songId)
            AppLog.debug(TAG, "remove: songId=$songId removed from Room")
            return true
        }
        if (songIdsSet.remove(songId)) {
            persistSongIds()
            AppLog.debug(TAG, "remove: songId=$songId removed, total=${songIdsSet.size}")
            return true
        }
        return false
    }

    override fun getSongIds(): Set<Int> {
        refreshSongIds()
        return songIdsSet.toSet()
    }

    fun isEmpty(): Boolean = getSongIds().isEmpty()

    fun size(): Int = getSongIds().size

    fun getShortPlayCount(songId: Int): Int {
        val dao = melodyDao
        if (dao != null) {
            return runBlocking(Dispatchers.IO) {
                dao.quickSkipShortPlay(songId)
            }?.count ?: legacyShortPlayCount(songId)
        }
        return legacyShortPlayCount(songId)
    }

    override fun incrementShortPlayCount(songId: Int): Int {
        val dao = melodyDao
        if (dao != null) {
            val newCount = getShortPlayCount(songId) + 1
            runBlocking(Dispatchers.IO) {
                dao.upsertQuickSkipShortPlay(
                    QuickSkipShortPlayEntity(
                        songId = songId,
                        count = newCount,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
            }
            AppLog.debug(TAG, "incrementShortPlayCount: songId=$songId, count=$newCount in Room")
            return newCount
        }
        val newCount = legacyShortPlayCount(songId) + 1
        requireNotNull(prefs) {
            "QuickSkipSongsRepository requires Context for short-play count persistence."
        }.edit().putInt("$KEY_SHORT_PLAY_PREFIX$songId", newCount).apply()
        AppLog.debug(TAG, "incrementShortPlayCount: songId=$songId, count=$newCount")
        return newCount
    }

    override fun resetShortPlayCount(songId: Int) {
        val dao = melodyDao
        if (dao != null) {
            runBlocking(Dispatchers.IO) {
                dao.deleteQuickSkipShortPlay(songId)
            }
            AppLog.debug(TAG, "resetShortPlayCount: songId=$songId in Room")
            return
        }
        requireNotNull(prefs) {
            "QuickSkipSongsRepository requires Context for short-play count persistence."
        }.edit().remove("$KEY_SHORT_PLAY_PREFIX$songId").apply()
        AppLog.debug(TAG, "resetShortPlayCount: songId=$songId")
    }

    private fun legacyShortPlayCount(songId: Int): Int {
        return prefs?.getInt("$KEY_SHORT_PLAY_PREFIX$songId", 0) ?: 0
    }
}
