package cn.com.dcsgo.mihx.player

import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes the [SessionToken] produced by
 * [cn.com.dcsgo.mihx.player.service.AppMediaSessionService] so the [PlaybackController] (which
 * lives in the same `:player` module) can build a [androidx.media3.session.MediaController]
 * without the service exposing a binder or the `:player` module referencing `:app`.
 *
 * A [StateFlow] is used so a late subscriber (e.g. the controller connecting after the service
 * already started) still receives the published token.
 */
@Singleton
class SessionTokenProvider @Inject constructor() {

    private val _token = MutableStateFlow<SessionToken?>(null)
    val token: StateFlow<SessionToken?> = _token.asStateFlow()

    fun publish(value: SessionToken) {
        _token.value = value
    }
}
