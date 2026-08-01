package cn.com.dcsgo.mihx.core.ui.toast

/** A single toast item shown in the top [ToastHost]. */
data class ToastMessage(
    val id: Long = System.nanoTime(),
    val text: String,
    val durationMs: Long = 2000L,
)
