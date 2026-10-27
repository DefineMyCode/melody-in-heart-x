package cn.com.dcsgo.mihx.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.com.dcsgo.mihx.data.local.MelodyDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 原子增量的**真实 Room 回归**（不用假 DAO —— 原子性来自 SQL，必须由真库证明）。
 *
 * 评审 2026-10-27 · M-22：两处读改写（read-modify-write）会丢更新——
 *  - `QuickSkipSongsRepository.incrementShortPlayCount`：读 count → +1 → 整行 upsert，
 *    两个并发调用读到同一基数、后写覆盖先写 → 丢一次计数（推迟「累计 2 次自动进秒切」）；
 *  - `WordPreferencesRepository.recordSongOutcome`：读整表 → 算新票数 → 整表 upsert，
 *    两个并发结算互相覆盖 → 票数丢失，探索播放的偏好归属静默漂移。
 *
 * 修复后改为 SQL 语句级原子（`INSERT ... ON CONFLICT DO UPDATE SET x = x + delta`），
 * 与同模块 `play_stats` 的既有范式一致。本测试用**真实并发**验证：N 个并发自增之后，
 * 计数必须正好是 N（旧实现会是 1 或个位数）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AtomicIncrementRoomTest {

    private lateinit var db: MelodyDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MelodyDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun concurrentShortPlayCountIncrementsDoNotLoseUpdates() = runBlocking {
        val repository = QuickSkipSongsRepository(melodyDao = db.melodyDao())
        val increments = 50

        coroutineScope {
            (1..increments).map {
                async(Dispatchers.IO) { repository.incrementShortPlayCount(7) }
            }.awaitAll()
        }

        assertEquals(increments, repository.getShortPlayCount(7))
    }

    @Test
    fun concurrentWordVotesDoNotLoseUpdates() = runBlocking {
        val repository = WordPreferencesRepository(db.melodyDao())
        val votes = 50

        coroutineScope {
            (1..votes).map {
                async(Dispatchers.IO) {
                    repository.recordSongOutcome(mapOf("燃" to 1), liked = true)
                }
            }.awaitAll()
        }

        val preference = repository.getAll()["燃"]
        assertEquals(votes, preference?.likeVotes)
        assertEquals(0, preference?.dislikeVotes)
    }

    @Test
    fun concurrentLikeAndDislikeVotesAccumulateInSeparateColumns() = runBlocking {
        val repository = WordPreferencesRepository(db.melodyDao())
        val rounds = 20

        coroutineScope {
            (1..rounds).map {
                async(Dispatchers.IO) {
                    repository.recordSongOutcome(mapOf("治愈" to 1), liked = true)
                }
            }.awaitAll()
            (1..rounds).map {
                async(Dispatchers.IO) {
                    repository.recordSongOutcome(mapOf("治愈" to 1), liked = false)
                }
            }.awaitAll()
        }

        val preference = repository.getAll()["治愈"]
        assertEquals(rounds, preference?.likeVotes)
        assertEquals(rounds, preference?.dislikeVotes)
    }
}
