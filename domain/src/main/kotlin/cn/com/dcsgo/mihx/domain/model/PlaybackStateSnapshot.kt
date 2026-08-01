package cn.com.dcsgo.mihx.domain.model

import cn.com.dcsgo.mihx.core.model.PlayMode
import kotlinx.serialization.Serializable

/**
 * Persisted playback state (plan P4-4). Serialized to JSON by
 * [cn.com.dcsgo.mihx.data.serialization.PlaybackStateSnapshotSerializer].
 *
 * IMPORTANT: [songIds] preserves queue order and repeats — a queue may legitimately contain
 * the same song twice, so restore MUST rebuild the queue 1:1 from this list (never de-dupe).
 *
 * @param songIds queue item song ids in playback order, repeats allowed
 * @param currentIndex index of the current item within [songIds]
 * @param playMode active play mode at save time
 * @param positionMs resume position inside the current item (milliseconds)
 * @param currentMediaId Media3 media id of the current item (= song id as string); kept for
 *   cross-checking against [songIds] on restore
 * @param savedAt epoch millis when the snapshot was written
 */
@Serializable
data class PlaybackStateSnapshot(
    val songIds: List<Long>,
    val currentIndex: Int,
    val playMode: PlayMode,
    val positionMs: Long,
    val currentMediaId: String?,
    val savedAt: Long,
)
