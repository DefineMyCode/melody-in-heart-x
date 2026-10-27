package cn.com.dcsgo.mihx.data.repository

import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.data.local.entity.QuickSkipSongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 *
 * M-5（评审 2026-10-15）：接口改 suspend，直接挂起查询，不再 runBlocking 桥接。
 */
class QuickSkipSongsRepository(
    private val melodyDao: MelodyDao,
) : cn.com.dcsgo.mihx.domain.repository.QuickSkipRepository {

    override suspend fun contains(songId: Int): Boolean = withContext(Dispatchers.IO) {
        melodyDao.quickSkipSong(songId) != null
    }

    override suspend fun add(songId: Int): Boolean {
        if (contains(songId)) return false
        withContext(Dispatchers.IO) {
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

    override suspend fun remove(songId: Int): Boolean {
        if (!contains(songId)) return false
        withContext(Dispatchers.IO) {
            melodyDao.deleteQuickSkipSong(songId)
        }
        AppLog.debug(TAG, "remove: songId=$songId removed from Room")
        return true
    }

    override suspend fun getSongIds(): Set<Int> = withContext(Dispatchers.IO) {
        melodyDao.quickSkipSongs().map { it.songId }.toSet()
    }

    suspend fun isEmpty(): Boolean = getSongIds().isEmpty()

    suspend fun size(): Int = getSongIds().size

    suspend fun getShortPlayCount(songId: Int): Int = withContext(Dispatchers.IO) {
        melodyDao.quickSkipShortPlay(songId)
    }?.count ?: 0

    override suspend fun incrementShortPlayCount(songId: Int): Int {
        // 评审 2026-10-27 · M-22：改为 SQL 级原子自增。原实现「读 → +1 → 整行 upsert」
        // 在并发下会丢一次计数（后写覆盖先写），推迟「累计 2 次自动加入秒切列表」。
        //
        // 两步：幂等建行（已存在则忽略）→ 语句级原子自增（count = count + 1）。
        val now = System.currentTimeMillis()
        withContext(Dispatchers.IO) {
            melodyDao.ensureQuickSkipShortPlay(songId, now)
            melodyDao.incrementQuickSkipShortPlay(songId, now)
        }
        val newCount = getShortPlayCount(songId)
        AppLog.debug(TAG, "incrementShortPlayCount: songId=$songId, count=$newCount in Room")
        return newCount
    }

    override suspend fun resetShortPlayCount(songId: Int) {
        withContext(Dispatchers.IO) {
            melodyDao.deleteQuickSkipShortPlay(songId)
        }
        AppLog.debug(TAG, "resetShortPlayCount: songId=$songId in Room")
    }
}
