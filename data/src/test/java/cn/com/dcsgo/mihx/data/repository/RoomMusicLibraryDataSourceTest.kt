package cn.com.dcsgo.mihx.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cn.com.dcsgo.mihx.core.model.Song
import cn.com.dcsgo.mihx.data.local.MelodyDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * m-8（评审）：fileFingerprint 编码往返稳定性回归。
 *
 * 原编码 `"$size:$lastModified"` 在 size 非空、lastModified 为空时拼出 `"123:null"`，
 * 往返解析后可能误判"已变化"。改用 `"$size/$lastModified"` 后：
 * 空值经 Kotlin 插值渲染为 "null"，`toLongOrNull()` 正确解析回 null，往返稳定。
 *
 * 用真实 Room 内存库验证（编码/解码在 `RoomMusicLibraryDataSource` 内部完成，
 * 假 DAO 无法覆盖 `toSong`/`toEntity` 的 SQL 映射逻辑）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomMusicLibraryDataSourceTest {

    private lateinit var db: MelodyDatabase
    private lateinit var dataSource: RoomMusicLibraryDataSource

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MelodyDatabase::class.java,
        ).allowMainThreadQueries().build()
        dataSource = RoomMusicLibraryDataSource(db.melodyDao())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `fingerprint round-trips when size non-null but lastModified null`() = runBlocking {
        // 核心场景：size=123, lastModified=null → 编码 "123/null" → 往返解析后仍为 size=123, lastModified=null
        val song = song(id = 1, fileFingerprint = "123/null")
        dataSource.persistSongs(listOf(song), importedAt = 1L)

        val restored = dataSource.restore()
        val restoredSong = restored.songs.single()

        assertEquals("123/null", restoredSong.fileFingerprint)
        assertEquals(123L, restoredSong.fileFingerprint?.substringBeforeLast('/')?.toLongOrNull())
        assertNull(restoredSong.fileFingerprint?.substringAfterLast('/')?.toLongOrNull())
    }

    @Test
    fun `fingerprint round-trips with both size and lastModified`() = runBlocking {
        val song = song(id = 2, fileFingerprint = "456/789")
        dataSource.persistSongs(listOf(song), importedAt = 1L)

        val restored = dataSource.restore()
        assertEquals("456/789", restored.songs.single().fileFingerprint)
    }

    @Test
    fun `fingerprint round-trips when size null but lastModified non-null`() = runBlocking {
        val song = song(id = 3, fileFingerprint = "null/789")
        dataSource.persistSongs(listOf(song), importedAt = 1L)

        val restored = dataSource.restore()
        val restoredSong = restored.songs.single()

        assertEquals("null/789", restoredSong.fileFingerprint)
        assertNull(restoredSong.fileFingerprint?.substringBeforeLast('/')?.toLongOrNull())
        assertEquals(789L, restoredSong.fileFingerprint?.substringAfterLast('/')?.toLongOrNull())
    }

    @Test
    fun `fingerprint null stays null through round-trip`() = runBlocking {
        val song = song(id = 4, fileFingerprint = null)
        dataSource.persistSongs(listOf(song), importedAt = 1L)

        val restored = dataSource.restore()
        assertNull(restored.songs.single().fileFingerprint)
    }

    private fun song(id: Int, fileFingerprint: String?): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        sampleRate = 44_100,
        uri = null,
        albumArtUri = null,
        lrcUri = null,
        fileFingerprint = fileFingerprint,
    )
}
