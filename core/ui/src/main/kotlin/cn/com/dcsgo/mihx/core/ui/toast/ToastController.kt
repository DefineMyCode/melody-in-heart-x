package cn.com.dcsgo.mihx.core.ui.toast

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory controller for stacking toasts. */
class ToastController {
    private val _messages = MutableStateFlow<List<ToastMessage>>(emptyList())
    val messages: StateFlow<List<ToastMessage>> = _messages.asStateFlow()

    fun show(text: String, durationMs: Long = 2000L) {
        _messages.update { it + ToastMessage(text = text, durationMs = durationMs) }
    }

    fun dismiss(id: Long) {
        _messages.update { it.filterNot { m -> m.id == id } }
    }
}
