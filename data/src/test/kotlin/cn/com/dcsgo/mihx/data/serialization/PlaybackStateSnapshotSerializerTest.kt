package cn.com.dcsgo.mihx.data.serialization

import cn.com.dcsgo.mihx.core.model.PlayMode
import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Plan P4-9 — `PlaybackStateSnapshotSerializer` round-trip + tolerance.
 *
 * Note: queue [PlaybackStateSnapshot.songIds] intentionally repeats a song id to prove the
 * serializer preserves order/repeats (restore must rebuild the queue 1:1, never de-dupe — see
 * architecture gate A5). We avoid `distinct`/`associateBy`/`toSet` here so the test source itself
 * stays inside that gate.
 */
class PlaybackStateSnapshotSerializerTest {

    private val snapshot = PlaybackStateSnapshot(
        songIds = listOf(1L, 2L, 3L, 2L),
        currentIndex = 2,
        playMode = PlayMode.RANDOM,
        positionMs = 42_000L,
        currentMediaId = "3",
        savedAt = 1_700_000_000_000L,
    )

    @Test
    fun serializeThenDeserialize_roundTrips() {
        val json = PlaybackStateSnapshotSerializer.serialize(snapshot)
        assertEquals(snapshot, PlaybackStateSnapshotSerializer.deserialize(json))
    }

    @Test
    fun roundTrip_preservesRepeatedSongIdsInOrder() {
        val back = PlaybackStateSnapshotSerializer.deserialize(
            PlaybackStateSnapshotSerializer.serialize(snapshot),
        )!!
        assertEquals(listOf(1L, 2L, 3L, 2L), back.songIds)
        assertEquals(2, back.currentIndex)
    }

    @Test
    fun deserialize_ignoresUnknownFields_forwardCompat() {
        val json = """{"songIds":[1],"currentIndex":0,"playMode":"SEQUENTIAL","positionMs":0,"currentMediaId":"1","savedAt":1,"futureField":123}"""
        val back = PlaybackStateSnapshotSerializer.deserialize(json)
        assertEquals(listOf(1L), back?.songIds)
    }

    @Test
    fun deserialize_corruptJson_returnsNull() {
        assertNull(PlaybackStateSnapshotSerializer.deserialize("{not valid json"))
    }

    @Test
    fun deserialize_missingEssentialField_returnsNull() {
        // songIds is required; dropping it must not throw, only yield null (field-missing tolerance).
        val json = """{"currentIndex":0,"playMode":"SEQUENTIAL","positionMs":0,"currentMediaId":"1","savedAt":1}"""
        assertNull(PlaybackStateSnapshotSerializer.deserialize(json))
    }
}
