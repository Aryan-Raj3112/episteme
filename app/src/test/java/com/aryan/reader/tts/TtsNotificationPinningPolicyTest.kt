package com.aryan.reader.tts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.media3.common.Player
import org.robolectric.RuntimeEnvironment
import com.aryan.reader.isPlaybackNotificationPinned
import com.aryan.reader.pinPostedPlaybackNotification
import com.aryan.reader.shouldIgnoreDismissedTransportStop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TtsNotificationPinningPolicyTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val notificationId = 4242

    @Test
    fun `notification is pinned while playing`() {
        assertTrue(isPlaybackNotificationPinned(playWhenReady = true, playbackState = Player.STATE_READY))
    }

    @Test
    fun `notification is pinned while buffering with play requested`() {
        assertTrue(isPlaybackNotificationPinned(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun `notification is not pinned while paused`() {
        assertFalse(isPlaybackNotificationPinned(playWhenReady = false, playbackState = Player.STATE_READY))
    }

    @Test
    fun `notification is not pinned while buffering without play requested`() {
        assertFalse(isPlaybackNotificationPinned(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun `notification is not pinned when the player is idle`() {
        assertFalse(isPlaybackNotificationPinned(playWhenReady = true, playbackState = Player.STATE_IDLE))
        assertFalse(isPlaybackNotificationPinned(playWhenReady = false, playbackState = Player.STATE_IDLE))
    }

    @Test
    fun `notification is not pinned when playback has ended`() {
        assertFalse(isPlaybackNotificationPinned(playWhenReady = false, playbackState = Player.STATE_ENDED))
        assertFalse(isPlaybackNotificationPinned(playWhenReady = true, playbackState = Player.STATE_ENDED))
    }

    @Test
    fun `notification is pinned while the session is actively speaking`() {
        // Device-voice read-aloud keeps the media player idle between chapters while the system
        // voice already speaks the next one; that gap must not leave the card dismissible.
        assertTrue(
            isPlaybackNotificationPinned(
                playWhenReady = false,
                playbackState = Player.STATE_IDLE,
                sessionActivelySpeaking = true,
            )
        )
        assertTrue(
            isPlaybackNotificationPinned(
                playWhenReady = false,
                playbackState = Player.STATE_ENDED,
                sessionActivelySpeaking = true,
            )
        )
    }

    @Test
    fun `a dismissal never ends a session that is actively speaking`() {
        assertTrue(shouldIgnoreDismissedTransportStop(notificationDismissed = true, sessionActivelySpeaking = true))
    }

    @Test
    fun `a dismissal still ends a paused session`() {
        // Paused sessions stay swipeable on purpose; clearing one means "stop listening".
        assertFalse(shouldIgnoreDismissedTransportStop(notificationDismissed = true, sessionActivelySpeaking = false))
    }

    @Test
    fun `a real transport stop is honoured while speaking`() {
        // Headset stop / task removal carry no dismissal flag and must always stop.
        assertFalse(shouldIgnoreDismissedTransportStop(notificationDismissed = false, sessionActivelySpeaking = true))
    }

    @Test
    fun `posted notification is flagged ongoing while playback is active`() {
        postNotification(buildNotification())

        pinPostedPlaybackNotification(
            context = context,
            notificationId = notificationId,
            playWhenReady = true,
            playbackState = Player.STATE_READY,
            diagnosticsTag = "TEST_DIAG"
        )

        val pinned = activeNotification()
        assertNotNull(pinned)
        assertTrue(pinned!!.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `posted notification is flagged ongoing while the device voice is speaking`() {
        postNotification(buildNotification())

        pinPostedPlaybackNotification(
            context = context,
            notificationId = notificationId,
            playWhenReady = false,
            playbackState = Player.STATE_IDLE,
            diagnosticsTag = "TEST_DIAG",
            sessionActivelySpeaking = true
        )

        val pinned = activeNotification()
        assertNotNull(pinned)
        assertTrue(pinned!!.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `posted notification stays swipeable while paused`() {
        postNotification(buildNotification())

        pinPostedPlaybackNotification(
            context = context,
            notificationId = notificationId,
            playWhenReady = false,
            playbackState = Player.STATE_READY,
            diagnosticsTag = "TEST_DIAG"
        )

        val posted = activeNotification()
        assertNotNull(posted)
        assertTrue(posted!!.flags and Notification.FLAG_ONGOING_EVENT == 0)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(context, "tts_playback")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
    }

    private fun postNotification(notification: Notification) {
        context.getSystemService(NotificationManager::class.java)
            .notify(notificationId, notification)
    }

    private fun activeNotification(): Notification? {
        return context.getSystemService(NotificationManager::class.java)
            .activeNotifications
            .firstOrNull { it.id == notificationId }?.notification
    }
}
