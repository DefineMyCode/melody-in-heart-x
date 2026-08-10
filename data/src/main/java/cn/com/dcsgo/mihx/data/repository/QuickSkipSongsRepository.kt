package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipShortPlayEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private const val TAG = "QuickSkipSongsRepo"

/**
 * 秒切歌曲仓库
 *
 * 用于存储播放时经常没放几秒就直接切下一首的歌曲。
 * 这些歌曲的播放次数加1后会自动从列表中移除。
 * 同时记录短时长播放次数，累计超过2次后自动添加到秒切列表。
 *
 * 唯一数据源为 Room，旧版 SharedPreferences 数据由
 * SharedPreferencesLegacyJsonMigration 一次性只读迁移。
 */
class QuickSkipSongsRepository(
    private val melodyDao: MelodyDao,
) : cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository {

    override fun contains(songId: Int): Boolean = runBlocking(Dispatchers.IO) {
        melodyDao.quickSkipSong(songId) != null
    }

    override fun add(songId: Int): Boolean {
        if (contains(songId)) return false
        runBlocking(Dispatchers.IO) {
            melodyDao.upsertQuickSkipSong(
                QuickSkipSongEntity(
                    songId = songId,
                    addedAt = System.currentTimeMillis(),
                )
            )
        }
        AppLog.debug(TAG, "add: songId=$songId added to Room")
        return true
    }

    override fun remove(songId: Int): Boolean {
        if (!contains(songId)) return false
        runBlocking(Dispatchers.IO) {
            melodyDao.deleteQuickSkipSong(songId)
        }
        AppLog.debug(TAG, "remove: songId=$songId removed from Room")
        return true
    }

    override fun getSongIds(): Set<Int> = runBlocking(Dispatchers.IO) {
        melodyDao.quickSkipSongs().map { it.songId }.toSet()
    }

    fun isEmpty(): Boolean = getSongIds().isEmpty()

    fun size(): Int = getSongIds().size

    fun getShortPlayCount(songId: Int): Int = runBlocking(Dispatchers.IO) {
        melodyDao.quickSkipShortPlay(songId)
    }?.count ?: 0

    override fun incrementShortPlayCount(songId: Int): Int {
        val newCount = getShortPlayCount(songId) + 1
        runBlocking(Dispatchers.IO) {
            melodyDao.upsertQuickSkipShortPlay(
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

    override fun resetShortPlayCount(songId: Int) {
        runBlocking(Dispatchers.IO) {
            melodyDao.deleteQuickSkipShortPlay(songId)
        }
        AppLog.debug(TAG, "resetShortPlayCount: songId=$songId in Room")
    }
}
