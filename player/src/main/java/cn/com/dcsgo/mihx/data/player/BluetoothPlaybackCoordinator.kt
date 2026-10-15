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

        // m-5（评审 2026-10-15）：原在此挂接的 onAudioInterrupted/onAudioResumed 回调
        // 属于音频中断恢复机制（handleAudioInterruption/requestAudioFocus），但该机制
        // 从未被任何代码触发，属死接线，已与 manager 侧一起移除。
        // 蓝牙断连自动暂停由上方 isA2dpConnected 轮询路径负责（活代码）。

        bluetoothStateManager.initialize()
        audioQualityManager.startMonitoring()

        AppLog.debug(TAG, "Bluetooth playback coordinator initialized")
    }

    override fun release() {
        bluetoothStateManager.release()
        audioQualityManager.release()
    }
}
