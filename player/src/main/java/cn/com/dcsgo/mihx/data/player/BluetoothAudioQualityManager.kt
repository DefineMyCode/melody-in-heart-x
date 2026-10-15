package cn.com.dcsgo.mihx.data.player

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import cn.com.dcsgo.mihx.core.common.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 蓝牙音频质量监控与恢复管理器
 *
 * 监控蓝牙音频输出的质量，并在检测到问题时提供平滑的错误恢复机制。
 *
 * 功能：
 * 1. 监控音频设备路由变化
 * 2. 提供连接状态变化的平滑过渡
 *
 * 使用方式：
 * 1. 在 PlayerViewModel 初始化时创建实例
 * 2. 在 onDestroy 时调用 [release]
 */
class BluetoothAudioQualityManager(private val context: Context) {

    companion object {
        private const val TAG = "BtAudioQualityManager"
    }

    /** 音频路由类型 */
    enum class AudioRoute {
        SPEAKER,      // 手机扬声器
        WIRED_HEADSET, // 有线耳机
        WIRED_HEADPHONES, // 有线耳机（无麦克风）
        BLUETOOTH_A2DP, // 蓝牙 A2DP
        BLUETOOTH_HFP, // 蓝牙 HFP
        USB,          // USB 音频
        UNKNOWN
    }

    /** 音频路由状态 */
    data class RouteState(
        val currentRoute: AudioRoute = AudioRoute.SPEAKER,
        val isBluetoothConnected: Boolean = false,
        val isHeadsetConnected: Boolean = false,
        val lastChangeTime: Long = 0L
    )

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _routeState = MutableStateFlow(RouteState())
    val routeState: StateFlow<RouteState> = _routeState.asStateFlow()

    /** 音频路由变化回调 */
    var onAudioRouteChanged: ((AudioRoute) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var isMonitoring = false

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            handleAudioDevicesChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            handleAudioDevicesChanged()
        }
    }

    /**
     * 开始音频路由监控
     */
    fun startMonitoring() {
        if (isMonitoring) return

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)

        // 更新初始路由状态
        updateCurrentRoute()
        isMonitoring = true
        AppLog.debug(TAG, "Audio route monitoring started, current route: ${_routeState.value.currentRoute}")
    }

    /**
     * 停止音频路由监控
     */
    fun stopMonitoring() {
        if (!isMonitoring) return

        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to unregister audio device callback", e)
        }

        isMonitoring = false
        AppLog.debug(TAG, "Audio route monitoring stopped")
    }

    /**
     * 获取当前音频路由
     */
    fun getCurrentRoute(): AudioRoute = _routeState.value.currentRoute

    /**
     * 检查当前是否通过蓝牙播放
     */
    fun isBluetoothAudioRoute(): Boolean {
        return _routeState.value.currentRoute == AudioRoute.BLUETOOTH_A2DP ||
               _routeState.value.currentRoute == AudioRoute.BLUETOOTH_HFP
    }

    private fun handleAudioDevicesChanged() {
        val previousRoute = _routeState.value.currentRoute
        updateCurrentRoute()
        val newRoute = _routeState.value.currentRoute

        AppLog.debug(TAG, "Audio device route changed: $previousRoute -> $newRoute")

        if (newRoute != previousRoute) {
            onAudioRouteChanged?.invoke(newRoute)

            // 路由变为非蓝牙时，如果之前是蓝牙播放，可能需要调整
            if (previousRoute == AudioRoute.BLUETOOTH_A2DP && newRoute != AudioRoute.BLUETOOTH_A2DP) {
                AppLog.info(TAG, "Switched from Bluetooth to $newRoute")
            }
        }
    }

    private fun updateCurrentRoute() {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var currentRoute = AudioRoute.SPEAKER
        var isBluetooth = false
        var isHeadset = false

        for (device in devices) {
            val route = convertDeviceTypeToRoute(device)
            when (route) {
                AudioRoute.BLUETOOTH_A2DP, AudioRoute.BLUETOOTH_HFP -> {
                    isBluetooth = true
                    currentRoute = route
                }
                AudioRoute.WIRED_HEADSET, AudioRoute.WIRED_HEADPHONES -> {
                    isHeadset = true
                    if (!isBluetooth) currentRoute = route
                }
                AudioRoute.SPEAKER -> {
                    if (!isBluetooth && !isHeadset) currentRoute = route
                }
                else -> {}
            }
        }

        _routeState.value = RouteState(
            currentRoute = currentRoute,
            isBluetoothConnected = isBluetooth,
            isHeadsetConnected = isHeadset,
            lastChangeTime = System.currentTimeMillis()
        )
    }

    private fun convertDeviceTypeToRoute(device: AudioDeviceInfo): AudioRoute {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioRoute.BLUETOOTH_A2DP
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRoute.BLUETOOTH_HFP
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioRoute.WIRED_HEADPHONES
            AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRoute.USB
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioRoute.SPEAKER
            else -> AudioRoute.UNKNOWN
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        stopMonitoring()
        onAudioRouteChanged = null
    }
}
