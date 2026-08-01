package cn.com.dcsgo.mihx.player.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.media3.common.Player
import cn.com.dcsgo.mihx.core.common.log.AppLogger

/**
 * Auto-pauses playback when the audio route becomes "noisy" (wired headphones unplugged) or a
 * Bluetooth device disconnects. Plan P3-5.
 *
 * This forms a double-safety with [androidx.media3.exoplayer.ExoPlayer]'s
 * `setHandleAudioBecomingNoisy(true)` (set in [cn.com.dcsgo.mihx.player.PlayerFactory]): pausing an
 * already-paused player is a no-op, so the two never conflict or double-fire.
 *
 * The monitor is bound to the service-owned [Player] (the transport's source of truth), so pausing
 * here is reflected everywhere the session is observed.
 */
class BluetoothPlaybackMonitor(
    context: Context,
    private val player: Player,
) {
    private val appContext = context.applicationContext

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_AUDIO_BECOMING_NOISY,
                BluetoothDevice.ACTION_ACL_DISCONNECTED,
                -> onAudioRouteLost()
            }
        }
    }

    private fun onAudioRouteLost() {
        if (player.isPlaying) {
            AppLogger.d(TAG, "Audio route lost (becoming noisy / BT disconnect); pausing.")
            player.pause()
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    fun stop() {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (e: Exception) {
            AppLogger.w(TAG, "BluetoothPlaybackMonitor unregister failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BluetoothPlaybackMonitor"
    }
}
