package cn.com.dcsgo.mihx.core.model

/**
 * A single local audio track.
 * [id] is the unique row id; [mediaId] used by Media3 equals id.toString().
 */
data class Song(
    val id: Long,
    val uri: String?,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long = 0L,
    val sampleRate: Int = 0,
    val albumArtUri: String? = null,
    val titleOverride: String? = null,
    val playable: Boolean = uri != null,
) {
    /** Grouping key for same-title multi-version management. */
    val groupKey: String get() = titleOverride ?: title
}
