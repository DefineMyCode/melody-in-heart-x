package cn.com.dcsgo.mihx.domain.version

import cn.com.dcsgo.mihx.core.model.Song
import javax.inject.Inject

/**
 * Picks the winning version of a same-title group (plan P5-C5).
 *
 * Selection priority:
 *  1. The user's explicit preference ([preferredSongId]), when it still exists in the group.
 *  2. Otherwise the version with the highest [Song.sampleRate] — higher sample rate means better
 *     audio quality, so it is the sensible automatic default for identical titles.
 *  3. Ties fall back to the first item of [versions] (stable, deterministic).
 *
 * Pure & side-effect free — fully unit-testable.
 */
class SongVersionResolver @Inject constructor() {

    fun resolve(versions: List<Song>, preferredSongId: Long? = null): Song? {
        if (versions.isEmpty()) return null
        preferredSongId?.let { preferred ->
            versions.firstOrNull { it.id == preferred }?.let { return it }
        }
        return versions.maxByOrNull { it.sampleRate } ?: versions.first()
    }
}
