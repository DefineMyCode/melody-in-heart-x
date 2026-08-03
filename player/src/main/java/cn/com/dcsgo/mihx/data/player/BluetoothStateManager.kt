package cn.com.dcsgo.mihx.data.player

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import cn.com.dcsgo.mihx.core.common.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 蓝牙连接状态管理器
 *
 * 监控蓝牙设备的连接/断开、A2DP 配置文件状态，
 * 并检测连接的蓝牙设备类型（耳机/车载/音箱等）。
 *
 * 使用方式：
 * 1. 在 PlayerViewModel 初始化时创建实例
 * 2. 通过 [stateFlow] 观察状态变化
 * 3. 在 onDestroy 时调用 [release]
 */
class BluetoothStateManager(private val context: Context) {

    companion object {
        private const val TAG = "BluetoothStateManager"
    }

    /** 蓝牙连接状态 */
    data class BtState(
        val isBluetoothEnabled: Boolean = false,
        val isA2dpConnected: Boolean = false,       // A2DP 音频配置文件已连接
        val isHfpConnected: Boolean = false,        // HFP 免提配置文件已连接（通话）
        val connectedDeviceName: String? = null,
        val connectedDeviceType: DeviceType = DeviceType.UNKNOWN,
        val audioQuality: AudioQuality = AudioQuality.NORMAL,
        val signalStrength: SignalStrength = SignalStrength.UNKNOWN
    )

    enum class DeviceType {
        HEADPHONES,      // 蓝牙耳机
        SPEAKER,         // 蓝牙音箱
        CAR_KIT,         // 车载蓝牙
        WATCH,           // 智能手表
        UNKNOWN
    }

    enum class AudioQuality {
        HIGH,   // aptX HD / LDAC High
        NORMAL, // A2DP 标准
        LOW     // SCO 通话模式
    }

    enum class SignalStrength {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR,
        UNKNOWN
    }

    private val _stateFlow = MutableStateFlow(BtState())
    val stateFlow: StateFlow<BtState> = _stateFlow.asStateFlow()

    /** 蓝牙状态变化回调（由 PlayerViewModel 注册） */
    var onBluetoothStateChanged: ((BtState) -> Unit)? = null

    /** A2DP 音频质量变化回调（用于动态调整缓冲区） */
    var onAudioQualityChanged: ((AudioQuality) -> Unit)? = null

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothA2dp: BluetoothA2dp? = null
    private var bluetoothHeadset: BluetoothHeadset? = null

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            when (proxy) {
                is BluetoothA2dp -> {
                    bluetoothA2dp = proxy
                    updateA2dpState()
                }
                is BluetoothHeadset -> {
                    bluetoothHeadset = proxy
                    updateHfpState()
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            when (profile) {
                BluetoothProfile.A2DP -> bluetoothA2dp = null
                BluetoothProfile.HEADSET -> bluetoothHeadset = null
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (!hasBluetoothConnectPermission()) {
                AppLog.info(TAG, "Bluetooth event ignored because permission is missing")
                return
            }
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                    handleBluetoothStateChange(state)
                }
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                    updateProfileStates()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    val device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                    device?.let { handleDeviceConnected(it) }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    val device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE,
                        BluetoothDevice::class.java
                    )
                    device?.let { handleDeviceDisconnected(it) }
                }
            }
        }
    }

    /**
     * 初始化蓝牙管理器
     *
     * 必须在主线程调用。
     */
    @SuppressLint("MissingPermission")
    fun initialize() {
        if (!hasBluetoothConnectPermission()) {
            AppLog.info(TAG, "Bluetooth monitoring skipped until permission is granted")
            return
        }

        bluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter

        if (bluetoothAdapter == null) {
            AppLog.warning(TAG, "Bluetooth not supported on this device")
            return
        }

        // 注册蓝牙状态广播接收器
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

        // 初始化 A2DP 和 Headset profile 监听器
        bluetoothAdapter?.let { adapter ->
            try {
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.A2DP)
                adapter.getProfileProxy(context, profileListener, BluetoothProfile.HEADSET)
            } catch (e: Exception) {
                AppLog.error(TAG, "Failed to get profile proxy", e)
            }
        }

        // 更新初始状态
        updateInitialState()
        AppLog.debug(TAG, "BluetoothStateManager initialized")
    }

    @SuppressLint("MissingPermission")
    private fun updateInitialState() {
        if (!hasBluetoothConnectPermission()) return
        bluetoothAdapter?.let { adapter ->
            val isEnabled = adapter.isEnabled
            _stateFlow.value = _stateFlow.value.copy(isBluetoothEnabled = isEnabled)

            if (isEnabled) {
                updateProfileStates()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleBluetoothStateChange(state: Int) {
        val isEnabled = when (state) {
            BluetoothAdapter.STATE_ON -> true
            BluetoothAdapter.STATE_OFF -> false
            else -> _stateFlow.value.isBluetoothEnabled
        }

        _stateFlow.value = _stateFlow.value.copy(isBluetoothEnabled = isEnabled)
        onBluetoothStateChanged?.invoke(_stateFlow.value)

        AppLog.info(TAG, "Bluetooth state changed: enabled=$isEnabled")
    }

    @SuppressLint("MissingPermission")
    private fun handleDeviceConnected(device: BluetoothDevice) {
        if (!hasBluetoothConnectPermission()) return
        val deviceName = try { device.name ?: device.alias ?: "Unknown" } catch (e: Exception) { "Unknown" }
        val deviceType = detectDeviceType(device)

        _stateFlow.value = _stateFlow.value.copy(
            connectedDeviceName = deviceName,
            connectedDeviceType = deviceType
        )

        onBluetoothStateChanged?.invoke(_stateFlow.value)
        AppLog.info(TAG, "Bluetooth device connected: type=$deviceType")
    }

    private fun handleDeviceDisconnected(device: BluetoothDevice) {
        _stateFlow.value = _stateFlow.value.copy(
            connectedDeviceName = null,
            connectedDeviceType = DeviceType.UNKNOWN,
            isA2dpConnected = false,
            isHfpConnected = false,
            audioQuality = AudioQuality.NORMAL,
            signalStrength = SignalStrength.UNKNOWN
        )

        onBluetoothStateChanged?.invoke(_stateFlow.value)
        AppLog.info(TAG, "Bluetooth device disconnected")
    }

    @SuppressLint("MissingPermission")
    private fun updateProfileStates() {
        if (!hasBluetoothConnectPermission()) return
        updateA2dpState()
        updateHfpState()
    }

    @SuppressLint("MissingPermission", "NewApi")
    private fun updateA2dpState() {
        if (!hasBluetoothConnectPermission()) return
        val a2dp = bluetoothA2dp ?: return

        try {
            val connectedDevices = a2dp.connectedDevices
            val isConnected = connectedDevices.isNotEmpty()

            _stateFlow.value = _stateFlow.value.copy(isA2dpConnected = isConnected)

            if (isConnected) {
                val device = connectedDevices.first()
                val quality = detectAudioQuality(device, a2dp)
                _stateFlow.value = _stateFlow.value.copy(
                    audioQuality = quality,
                    connectedDeviceName = device.name ?: device.alias ?: "Unknown"
                )
                onAudioQualityChanged?.invoke(quality)
            } else {
                _stateFlow.value = _stateFlow.value.copy(
                    audioQuality = AudioQuality.NORMAL,
                    signalStrength = SignalStrength.UNKNOWN
                )
            }

            onBluetoothStateChanged?.invoke(_stateFlow.value)
        } catch (e: Exception) {
            AppLog.error(TAG, "Failed to update A2DP state", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updateHfpState() {
        if (!hasBluetoothConnectPermission()) return
        val headset = bluetoothHeadset ?: return

        try {
            val connectedDevices = headset.connectedDevices
            val isConnected = connectedDevices.isNotEmpty()
            _stateFlow.value = _stateFlow.value.copy(isHfpConnected = isConnected)
        } catch (e: Exception) {
            AppLog.error(TAG, "Failed to update HFP state", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectDeviceType(device: BluetoothDevice): DeviceType {
        if (!hasBluetoothConnectPermission()) return DeviceType.UNKNOWN
        return try {
            when (device.bluetoothClass?.majorDeviceClass) {
                BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    when (device.bluetoothClass?.deviceClass) {
                        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> DeviceType.HEADPHONES
                        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> DeviceType.SPEAKER
                        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> DeviceType.CAR_KIT
                        else -> DeviceType.SPEAKER
                    }
                }
                BluetoothClass.Device.Major.WEARABLE -> DeviceType.WATCH
                else -> DeviceType.UNKNOWN
            }
        } catch (e: Exception) {
            DeviceType.UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    private fun detectAudioQuality(device: BluetoothDevice, a2dp: BluetoothA2dp): AudioQuality {
        if (!hasBluetoothConnectPermission()) return AudioQuality.NORMAL
        // 默认返回标准质量
        // A2DP 编解码器检测需要厂商特定 API，这里简化处理
        return AudioQuality.NORMAL
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取当前连接状态
     */
    fun getCurrentState(): BtState = _stateFlow.value

    /**
     * 判断当前是否通过蓝牙播放
     */
    fun isBluetoothPlaying(): Boolean = _stateFlow.value.isBluetoothEnabled && _stateFlow.value.isA2dpConnected

    /**
     * 释放资源
     */
    fun release() {
        try {
            context.unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to unregister receiver", e)
        }

        try {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, bluetoothA2dp)
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
        } catch (e: Exception) {
            AppLog.warning(TAG, "Failed to close profile proxy", e)
        }

        bluetoothA2dp = null
        bluetoothHeadset = null

        AppLog.debug(TAG, "BluetoothStateManager released")
    }
}
