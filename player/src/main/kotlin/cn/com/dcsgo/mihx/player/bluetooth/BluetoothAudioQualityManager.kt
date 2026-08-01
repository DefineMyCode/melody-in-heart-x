package cn.com.dcsgo.mihx.player.bluetooth

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kind of the currently routed audio output, ordered by media routing priority.
 */
enum class AudioOutputKind {
    BLUETOOTH_LE,
    BLUETOOTH_A2DP,
    BLUETOOTH_SCO,
    USB,
    WIRED,
    SPEAKER,
    OTHER,
}

/**
 * Snapshot of the active audio output route and the capabilities it reports.
 *
 * [sampleRateHz] / [channelCount] are `0` when the driver reports "unspecified" (common for A2DP
 * sinks, where the negotiated rate lives inside the Bluetooth stack).
 */
data class BluetoothAudioOutput(
    val deviceName: String,
    val kind: AudioOutputKind,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
)

/**
 * Exposes the active audio output route for display in the player / settings screen. Plan P3-6.
 *
 * Scope note: the original intent was to surface the negotiated Bluetooth **codec** (SBC / AAC /
 * aptX / LDAC). That is not reachable from a third-party app — `BluetoothA2dp.getCodecStatus()` and
 * `BluetoothA2dp.ACTION_CODEC_CONFIG_CHANGED` are both hidden `@SystemApi`, and the codec-config
 * broadcast is sent with `BLUETOOTH_PRIVILEGED`, which normal apps cannot hold. The closest
 * publicly available signal is the routed output device plus the sample rates / channel counts it
 * advertises, which is what this manager reports. No permission is required.
 *
 * The routed device is picked by media routing priority (LE Audio > A2DP > SCO > USB > wired >
 * speaker) rather than read back from the audio track: `AudioRouting.getRoutedDevice()` would
 * require a handle on the ExoPlayer-owned `AudioTrack`, which Media3 does not expose.
 */
@Singleton
class BluetoothAudioQualityManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private val activeOutputState = MutableStateFlow<BluetoothAudioOutput?>(null)
    val activeOutput: Flow<BluetoothAudioOutput?> = activeOutputState.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start() {
        audioManager?.registerAudioDeviceCallback(deviceCallback, handler)
        refresh()
    }

    fun stop() {
        audioManager?.unregisterAudioDeviceCallback(deviceCallback)
        activeOutputState.value = null
    }

    private fun refresh() {
        val manager = audioManager
        if (manager == null) {
            activeOutputState.value = null
            return
        }
        val routed = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .minByOrNull { kindOf(it.type).ordinal }
        activeOutputState.value = routed?.let {
            BluetoothAudioOutput(
                deviceName = it.productName?.toString().orEmpty(),
                kind = kindOf(it.type),
                sampleRateHz = it.sampleRates.maxOrNull() ?: 0,
                channelCount = it.channelCounts.maxOrNull() ?: 0,
            )
        }
    }

    private fun kindOf(type: Int): AudioOutputKind = when (type) {
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        -> AudioOutputKind.BLUETOOTH_LE

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioOutputKind.BLUETOOTH_A2DP
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioOutputKind.BLUETOOTH_SCO

        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        -> AudioOutputKind.USB

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        -> AudioOutputKind.WIRED

        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        -> AudioOutputKind.SPEAKER

        else -> AudioOutputKind.OTHER
    }
}
