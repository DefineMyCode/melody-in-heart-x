package cn.com.dcsgo.mihx.data.player

import android.annotation.SuppressLint
import cn.com.dcsgo.mihx.core.common.AppLog
import cn.com.dcsgo.mihx.domain.playback.BluetoothPlaybackMonitor

private const val TAG = "BluetoothPlaybackCoordinator"

class BluetoothPlaybackCoordinator(
    private val bluetoothStateManager: BluetoothStateManager,
    private val audioQualityManager: BluetoothAudioQualityManager,
    private val isPlaying: () -> Boolean,
    private val pausePlayback: () -> Unit,
) : BluetoothPlaybackMonitor {
    private var wasPlayingThroughBluetooth = false

    @SuppressLint("UnsafeOptInUsageError")
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun initialize() {
        bluetoothStateManager.onBluetoothStateChanged = { state ->
            AppLog.debug(
                TAG,
                "Bluetooth state changed: isA2dp=${state.isA2dpConnected}, " +
                    "device=${state.connectedDeviceName}, quality=${state.audioQuality}"
            )
            if (wasPlayingThroughBluetooth && !state.isA2dpConnected && isPlaying()) {
                pausePlayback()
                AppLog.info(TAG, "Bluetooth headset disconnected, playback paused")
            }
            wasPlayingThroughBluetooth = state.isA2dpConnected
        }

        bluetoothStateManager.onAudioQualityChanged = { quality ->
            AppLog.debug(TAG, "Audio quality changed: $quality")
        }

        audioQualityManager.onAudioRouteChanged = { route ->
            AppLog.debug(TAG, "Audio route changed: $route")
        }

        audioQualityManager.onAudioInterrupted = {
            if (isPlaying()) {
                pausePlayback()
                AppLog.info(TAG, "Audio interrupted, pausing")
            }
        }

        audioQualityManager.onAudioResumed = {
            AppLog.debug(TAG, "Audio resumed")
        }

        bluetoothStateManager.initialize()
        audioQualityManager.startMonitoring()

        AppLog.debug(TAG, "Bluetooth playback coordinator initialized")
    }

    override fun release() {
        bluetoothStateManager.release()
        audioQualityManager.release()
    }
}
