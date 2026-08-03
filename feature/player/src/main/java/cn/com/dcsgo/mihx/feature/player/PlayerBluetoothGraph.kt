package cn.com.dcsgo.mihx.feature.player

import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitorFactory

internal class PlayerBluetoothGraph(
    bluetoothPlaybackMonitorFactory: BluetoothPlaybackMonitorFactory,
    isPlaying: () -> Boolean,
    pausePlayback: () -> Unit,
) {
    private val playbackCoordinator = bluetoothPlaybackMonitorFactory.create(
        isPlaying = isPlaying,
        pausePlayback = pausePlayback,
    )

    fun initialize() {
        playbackCoordinator.initialize()
    }

    fun release() {
        playbackCoordinator.release()
    }
}
