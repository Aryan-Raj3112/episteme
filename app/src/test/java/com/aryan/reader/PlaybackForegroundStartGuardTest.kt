package com.aryan.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Looper
import java.io.File
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/**
 * A media button routed to a playback service with nothing to play must still leave the service
 * on the right side of the `startForegroundService()` contract, or the framework kills the process
 * (`ForegroundServiceDidNotStartInTimeException`). See [PlaybackForegroundStartGuard].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaybackForegroundStartGuardTest {

    class TestService : Service() {
        override fun onBind(intent: Intent?): android.os.IBinder? = null
    }

    @Test
    fun `a start with nothing to play is promoted before the framework times out`() {
        val service = newService()
        var canReachForeground = false

        guard(service).onStartCommand(START_ID) { canReachForeground }

        val shadow = shadowOf(service)
        assertEquals(NOTIFICATION_ID, shadow.lastForegroundNotificationId)
        assertNotNull(
            "the placeholder has to be a real foreground notification",
            shadow.lastForegroundNotification
        )
        assertFalse("the start is still inside its grace window", shadow.isStoppedBySelf)
    }

    @Test
    fun `a start that never becomes playback gives the service up after the grace`() {
        val service = newService()
        val guard = guard(service)

        guard.onStartCommand(START_ID) { false }
        idleFor(GRACE_MS / 2)
        assertFalse(
            "the give-up must not fire before the grace expires",
            shadowOf(service).isStoppedBySelf
        )

        idleFor(GRACE_MS / 2 + 1)

        val shadow = shadowOf(service)
        assertTrue(shadow.isForegroundStopped)
        assertTrue(shadow.isStoppedBySelf)
        assertEquals(START_ID, shadow.stopSelfId)
    }

    @Test
    fun `playback that arrives inside the grace keeps the service running`() {
        val service = newService()
        var canReachForeground = false
        guard(service).onStartCommand(START_ID) { canReachForeground }

        canReachForeground = true
        idleFor(GRACE_MS + 1)

        // Media3 re-uses this notification id for real playback, so an expired grace must not tear
        // down the foreground state it took over.
        val shadow = shadowOf(service)
        assertFalse(shadow.isForegroundStopped)
        assertFalse(shadow.isStoppedBySelf)
    }

    @Test
    fun `a start that already has playback is left untouched`() {
        val service = newService()
        guard(service).onStartCommand(START_ID) { true }

        idleFor(GRACE_MS + 1)

        val shadow = shadowOf(service)
        assertEquals(0, shadow.lastForegroundNotificationId)
        assertFalse(shadow.isStoppedBySelf)
    }

    @Test
    fun `an unreachable foreground state is given up immediately`() {
        val service = newService()
        val guard = guard(service)
        shadowOf(service).setThrowInStartForeground(IllegalStateException("not allowed from background"))

        guard.onStartCommand(START_ID) { false }

        assertTrue(shadowOf(service).isStoppedBySelf)
    }


    @Test
    fun `a channel the user turned off is given up instead of promoted`() {
        val service = newService()
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                "test_playback",
                "Test playback",
                NotificationManager.IMPORTANCE_NONE,
            )
        )

        guard(service).onStartCommand(START_ID) { false }

        // Posting on that channel is rejected by the framework after startForeground() has already
        // returned, so it cannot be caught and it kills the process: not promoting is the only
        // defence (docs/crashlytics-triage.md#119).
        val shadow = shadowOf(service)
        assertEquals(0, shadow.lastForegroundNotificationId)
        assertTrue(shadow.isStoppedBySelf)
        assertEquals(START_ID, shadow.stopSelfId)
    }

    @Test
    fun `an existing channel that is not blocked is still promoted`() {
        val service = newService()
        service.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                "test_playback",
                "Test playback",
                NotificationManager.IMPORTANCE_LOW,
            )
        )

        guard(service).onStartCommand(START_ID) { false }

        assertEquals(NOTIFICATION_ID, shadowOf(service).lastForegroundNotificationId)
    }

    @Test
    fun `release cancels a pending give-up`() {
        val service = newService()
        val guard = guard(service)
        guard.onStartCommand(START_ID) { false }

        guard.release()
        idleFor(GRACE_MS + 1)

        assertFalse(shadowOf(service).isStoppedBySelf)
    }

    @Test
    fun `both player services hand their starts to the guard`() {
        val audiobook = sourceFile("com/aryan/reader/audiobook/AudiobookPlayback.kt").readText()
        val narration = sourceFile("com/aryan/reader/mediaoverlay/MediaOverlayPlaybackService.kt").readText()

        // Without the call the service can be started by a media button with nothing to play, which
        // is the crash this guard exists for.
        val guardCall = "foregroundStartGuard.onStartCommand(startId, ::canReachForeground)"
        assertTrue(audiobook.contains(guardCall))
        assertTrue(narration.contains(guardCall))
        // A timer that outlives the service would stop a service the user started again.
        assertTrue(audiobook.contains("foregroundStartGuard.release()"))
        assertTrue(narration.contains("foregroundStartGuard.release()"))
    }

    private fun newService(): TestService =
        Robolectric.buildService(TestService::class.java).create().get()

    private fun guard(service: TestService) = PlaybackForegroundStartGuard(
        service = service,
        notificationId = NOTIFICATION_ID,
        channelId = "test_playback",
        channelName = "Test playback",
        diagnosticsTag = "GUARD_TEST",
        idleGraceMs = GRACE_MS,
    )

    private fun idleFor(millis: Long) =
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis))

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }

    private companion object {
        const val START_ID = 7
        const val NOTIFICATION_ID = 4242
        const val GRACE_MS = 1_000L
    }
}
