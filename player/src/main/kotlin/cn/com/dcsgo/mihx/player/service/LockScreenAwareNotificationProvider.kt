package cn.com.dcsgo.mihx.player.service

import android.app.Notification
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Custom [MediaSessionService.MediaNotificationProvider] that ensures media playback
 * controls are visible on the **lock screen** in addition to the notification shade.
 *
 * The stock [DefaultMediaNotificationProvider] does not guarantee
 * [NotificationCompat.VISIBILITY_PUBLIC], which causes some OEM skins (and stock Android
 * under certain DND / lock-screen policies) to suppress the media card on the lock screen
 * while still showing it in the expanded quick-settings panel.
 *
 * This implementation delegates actual notification building to
 * [DefaultMediaNotificationProvider] and then rebuilds the notification with
 * [NotificationCompat.VISIBILITY_PUBLIC] while preserving all Media3 extras (including
 * [androidx.media.app.MediaStyle]) via [extras.putAll].
 */
class LockScreenAwareNotificationProvider(
    context: Context,
) : MediaSessionService.MediaNotificationProvider {

    private val delegate = DefaultMediaNotificationProvider(context)

    override fun createNotification(
        mediaSession: MediaSession,
        customLayout: MediaSession.MediaLayout?,
        actionButtons: List<CommandButton>,
        smallIconResId: Int,
    ): MediaNotification {
        val result = delegate.createNotification(
            mediaSession,
            customLayout,
            actionButtons,
            smallIconResId,
        )
        val original = result.notification

        // Rebuild with VISIBILITY_PUBLIC, carrying over all critical fields and the full
        // extras bundle (which contains MediaStyle, media session token, artwork URI, etc.).
        val patched = NotificationCompat.Builder(
            original.context,
            original.channelId ?: DEFAULT_CHANNEL_ID,
        )
            .setSmallIcon(original.smallIcon)
            .apply {
                // Preserve every extra that DefaultMediaNotificationProvider set.
                extras.putAll(original.extras)
                setContentTitle(extras.getCharSequence(Notification.EXTRA_TITLE))
                setContentText(extras.getCharSequence(Notification.EXTRA_TEXT))
                setSubText(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
                setWhen(original.`when`)
                setShowWhen(true)
                setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                setContentIntent(original.contentIntent)
                setDeleteIntent(original.deleteIntent)
                setOngoing((original.flags and Notification.FLAG_ONGOING_EVENT) != 0)
                // Restore all action buttons (play / pause / next / prev).
                for (action in original.actions) {
                    @Suppress("DEPRECATION")
                    addAction(action)
                }
            }
            .build()
            .also {
                it.flags =
                    it.flags or (original.flags and Notification.FLAG_FOREGROUND_SERVICE)
            }

        return MediaNotification(result.id, patched)
    }

    companion object {
        private const val DEFAULT_CHANNEL_ID = "mihx_playback"
    }
}
