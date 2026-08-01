package cn.com.dcsgo.mihx.domain.repository

import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot

/**
 * Persists and restores the full playback state (plan P4-2 / P4-4).
 *
 * Replaces the earlier minimal `saveCurrentMediaId` / `savePosition` hooks — those were just
 * reserved connection points from P1-3; the real implementation ships the whole snapshot so the
 * queue / mode / current song / position can all be recovered after a restart (P4-6).
 */
interface PlaybackStateRepository {
    suspend fun saveSnapshot(snapshot: PlaybackStateSnapshot)
    suspend fun loadSnapshot(): PlaybackStateSnapshot?
}
