package cn.com.dcsgo.mihx.data.player

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.core.model.PlayQueue
import cn.com.dcsgo.mihx.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackStateSnapshotSerializerTest {
    private val serializer = PlaybackStateSnapshotSerializer()

    @Test
    fun queueRoundTripKeepsDuplicatesCurrentIndexModeAndOrder() {
        val queue = PlayQueue()
            .setQueue(songs(1, 2, 2, 3), startIndex = 2, mode = PlayMode.SHUFFLE)
            .copy(playOrderIds = listOf(2, 3, 1, 2))

        val restored = serializer.decodeQueue(
            json = serializer.encodeQueue(queue),
            allSongs = songs(1, 2, 3),
            allowEmpty = false,
        )

        assertEquals(listOf(1, 2, 2, 3), restored?.songs?.map { it.id })
        assertEquals(2, restored?.currentIndex)
        assertEquals(PlayMode.SHUFFLE, restored?.playMode)
        assertEquals(listOf(2, 3, 1, 2), restored?.currentPlayOrderIds())
    }

    @Test
    fun damagedQueueJsonReturnsNull() {
        assertNull(serializer.decodeQueue("{bad json", songs(1), allowEmpty = false))
    }

    @Test
    fun missingRequiredCurrentIndexReturnsNull() {
        val json = """{"songIds":[1],"playMode":"SEQUENTIAL","playOrderIds":[1]}"""

        assertNull(serializer.decodeQueue(json, songs(1), allowEmpty = false))
    }

    @Test
    fun missingOptionalFieldsUseDefaults() {
        val json = """{"songIds":[1],"currentIndex":0}"""

        val restored = serializer.decodeQueue(json, songs(1), allowEmpty = false)

        assertEquals(PlayMode.DEFAULT, restored?.playMode)
        assertEquals(listOf(1), restored?.currentPlayOrderIds())
    }

    @Test
    fun infinitePlayedIdsIgnoreMalformedAndUnavailableEntries() {
        val ids = serializer.decodeInfinitePlayedIds(
            json = """[1,"2","bad",99]""",
            availableSongIds = setOf(1, 2),
        )

        assertEquals(setOf(1, 2), ids)
    }

    private fun songs(vararg ids: Int): List<Song> = ids.map { id ->
        Song(
            id = id,
            title = "Song $id",
            artist = "Artist",
        )
    }
}
