package cn.com.dcsgo.mihx.data.serialization

import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot
import kotlinx.serialization.json.Json

/**
 * Structured JSON (de)serializer for [PlaybackStateSnapshot] (plan P4-4).
 *
 * Rules (no hand-rolled regex parsing):
 * - [Json.ignoreUnknownKeys] = true → forward-compatible with snapshots written by a newer app
 *   version that added extra fields (version-compat requirement).
 * - decode is wrapped in [runCatching]; a missing essential field or corrupt JSON yields `null`
 *   instead of throwing, so callers fall back to an empty queue (field-missing tolerance).
 */
object PlaybackStateSnapshotSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = false
    }

    fun serialize(snapshot: PlaybackStateSnapshot): String =
        json.encodeToString(PlaybackStateSnapshot.serializer(), snapshot)

    fun deserialize(jsonString: String): PlaybackStateSnapshot? =
        runCatching { json.decodeFromString(PlaybackStateSnapshot.serializer(), jsonString) }.getOrNull()
}
