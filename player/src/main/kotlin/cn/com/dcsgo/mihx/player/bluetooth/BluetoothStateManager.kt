package cn.com.dcsgo.mihx.player.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import cn.com.dcsgo.mihx.core.common.log.AppLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Emits whether a Bluetooth audio sink (headset / car kit / speaker) is currently connected.
 * Plan P3-4.
 *
 * Transitions are tracked from the system `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED`
 * broadcasts, which fire without requiring [android.Manifest.permission.BLUETOOTH_CONNECT]. The
 * steady-state probe of the A2DP/HEADSET profile connection (used only for the initial value)
 * is permission-gated and degrades to `false` when the permission is unavailable.
 */
@Singleton
class BluetoothStateManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
) {
    private val appContext = context.applicationContext
    private val adapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter

    private val _connected = MutableStateFlow(initialConnected())
    val connected: Flow<Boolean> = _connected.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    if (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON)
                        == BluetoothAdapter.STATE_OFF
                    ) {
                        _connected.value = false
                    }
                }
                // ACL broadcasts fire for any BT device (audio or not); a music app treats a
                // connect as "audio route available" and a disconnect as "audio route lost".
                // False positives (e.g. a paired BT watch) are harmless for this heuristic.
                BluetoothDevice.ACTION_ACL_CONNECTED -> _connected.value = true
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> _connected.value = false
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun initialConnected(): Boolean {
        if (adapter == null || !hasConnectPermission()) return false
        return try {
            // The *profile id* argument is a BluetoothProfile constant, but the returned state is
            // annotated with the BluetoothAdapter.STATE_* IntDef. The two families share numeric
            // values, yet comparing against BluetoothProfile.STATE_CONNECTED trips lint
            // (WrongConstant), which is an error under this project's settings.
            adapter.getProfileConnectionState(BluetoothProfile.A2DP) == BluetoothAdapter.STATE_CONNECTED ||
                adapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothAdapter.STATE_CONNECTED
        } catch (e: Exception) {
            false
        }
    }

    private fun hasConnectPermission(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: Exception) {
            AppLogger.w(TAG, "BluetoothStateManager unregister failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BluetoothStateManager"
    }
}
