package com.aryan.reader.tts

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.media3.common.Player
import org.robolectric.RuntimeEnvironment
import com.aryan.reader.isPlaybackNotificationPinned
import com.aryan.reader.pinPostedPlaybackNotification
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
