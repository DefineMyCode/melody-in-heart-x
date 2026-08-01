package cn.com.dcsgo.mihx.player

import cn.com.dcsgo.mihx.domain.model.PlaybackStateSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory mirror of the latest [PlaybackStateSnapshot] (plan P4-5). Written by the feature/UI
 * layer on every queue/position change (cheap, no IO) and read by [AppMediaSessionService] as a
 * fallback save target in `onTaskRemoved` / `onDestroy`. Persistence itself is delegated to
 * [cn.com.dcsgo.mihx.domain.repository.PlaybackStateRepository]; this buffer only decouples the
 * `:player` service from the feature layer so neither module depends on the other.
 */
@Singleton
class PlaybackStateBuffer @Inject constructor() {
    private val _current = MutableStateFlow<PlaybackStateSnapshot?>(null)
    val current: StateFlow<PlaybackStateSnapshot?> = _current.asStateFlow()

    fun update(snapshot: PlaybackStateSnapshot) {
        _current.value = snapshot
    }

    fun clear() {
        _current.value = null
    }
}
