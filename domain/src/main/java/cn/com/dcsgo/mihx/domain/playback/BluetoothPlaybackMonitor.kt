package cn.com.dcsgo.mihx.domain.playback

interface BluetoothPlaybackMonitor {
    fun initialize()
    fun release()
}

fun interface BluetoothPlaybackMonitorFactory {
    fun create(
        isPlaying: () -> Boolean,
        pausePlayback: () -> Unit,
    ): BluetoothPlaybackMonitor
}
