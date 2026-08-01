package cn.com.dcsgo.mihx.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.com.dcsgo.mihx.data.database.MelodyDatabase
import cn.com.dcsgo.mihx.data.database.entity.PlayStatsEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory Room DAO smoke test (plan P4-9). Robolectric supplies the [android.content.Context]
 * needed by [Room.inMemoryDatabaseBuilder]; [androidx.room.RoomDatabase.close] tears it down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MelodyDaoTest {

    private lateinit var db: MelodyDatabase

    @Before
    fun setup() {
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
    fun `play stats upsert and query round-trips`() = runBlocking {
        val dao = db.melodyDao()
        dao.upsertPlayStats(PlayStatsEntity(songId = 1L, playCount = 3, lastPlayedAt = 100L))
        val stats = dao.getPlayStats(1L)
        assertEquals(3L, stats?.playCount)
        assertEquals(100L, stats?.lastPlayedAt)
    }
}
