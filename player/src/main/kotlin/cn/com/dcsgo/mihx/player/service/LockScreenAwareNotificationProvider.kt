package cn.com.dcsgo.mihx.player.service

import android.app.Notification
import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList

/**
 * Wraps the default media notification provider and forces the produced notification to be publicly
 * visible on the lock screen.
 *
 * The stock provider leaves the notification visibility at the platform default, so some OEM skins
 * (and stock Android under certain lock-screen policies) suppress the media card on the lock screen
 * while still showing it in the expanded notification shade. Flipping the visibility to
 * [Notification.VISIBILITY_PUBLIC] makes the system render the controls on the lock screen too.
 *
 * Implementation note: per architecture gate A7 the :player module must not use the AndroidX compat
 * notification builder. We therefore do NOT rebuild the notification — we only mutate the framework
 * [Notification.visibility] field on the already-built [MediaNotification.notification] (a public,
 * writable field), which preserves every original field (MediaStyle, actions, artwork, session
 * token) and only changes the visibility flag.
 */
class LockScreenAwareNotificationProvider(
    private val delegate: MediaNotification.Provider,
) : MediaNotification.Provider {

    override fun createNotification(
        session: MediaSession,
        mediaButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        val original = delegate.createNotification(session, mediaButtons, actionFactory, callback)
        original.notification.visibility = Notification.VISIBILITY_PUBLIC
        return MediaNotification(original.notificationId, original.notification)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)
}
