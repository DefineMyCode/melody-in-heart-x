package cn.com.dcsgo.mihx.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.local.dao.MelodyDao
import cn.com.dcsgo.mihx.domain.repository.MediaStorageStatsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private const val TAG = "MediaStorageStats"

/**
 * 文件大小统计实现。
 *
 * 优先使用 songs 表里已持久化的 size（导入时或文件校验时写入），缺失的再通过
 * SAF URI 实时查询；并发上限 8，避免一次性打开过多文件描述符。
 */
@Singleton
class MediaStorageStatsRepositoryImpl @Inject constructor(
    private val melodyDao: MelodyDao,
    @ApplicationContext private val context: Context,
) : MediaStorageStatsRepository {

    override suspend fun measureFileSizes(songs: List<Song>): Map<Int, Long> =
        withContext(Dispatchers.IO) {
            if (songs.isEmpty()) return@withContext emptyMap()

            val storedSizes = runCatching { melodyDao.songSizes() }
                .onFailure { AppLog.warning(TAG, "read stored song sizes failed: ${it.message}") }
                .getOrDefault(emptyList())
                .associate { row -> row.id to row.size }

            val semaphore = Semaphore(permits = 8)
            val measuredPairs = coroutineScope {
                songs.map { song ->
                    async {
                        val stored = storedSizes[song.id]?.takeIf { it > 0L }
                        if (stored != null) return@async song.id to stored

                        val uri = song.uri ?: return@async song.id to null
                        semaphore.withPermit {
                            song.id to resolveFileSize(uri)
                        }
                    }
                }.awaitAll()
            }

            measuredPairs
                .mapNotNull { (songId, size) ->
                    size?.takeIf { it > 0L }?.let { songId to it }
                }
                .toMap()
        }

    private fun resolveFileSize(uri: Uri): Long? {
        val queried = runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && cursor.moveToFirst() && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
            }
        }.getOrNull()
        if (queried != null && queried > 0L) return queried

        return runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
        }.getOrNull()?.takeIf { it > 0L }
    }
}