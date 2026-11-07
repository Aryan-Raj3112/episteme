package com.aryan.reader

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import timber.log.Timber

/**
 * Meets the platform's `startForegroundService()` obligation for a playback service that was
 * started with nothing to play.
 *
 * A media button is forwarded by [MediaButtonRoutingReceiver] to the playback service that last
 * produced sound, through `startForegroundService()`. That service can arrive with an empty player
 * — its process died since, or its playlist was cleared while a controller kept the service bound
 * — and Media3 only promotes a session to the foreground once its notification is due
 * (`MediaNotificationManager.shouldShowNotification` requires a non-empty timeline), so nothing
 * ever calls `startForeground()`. The framework then kills the app with
 * `RemoteServiceException: ForegroundServiceDidNotStartInTimeException` seconds later, from the
 * background, with no app frame in the report pointing at the button press.
 *
 * So the obligation is met here instead, with the notification Media3 would have posted — same id
 * and channel, so real playback replaces it rather than adding a second entry — and only for as
 * long as the start could still become real playback: a start that never becomes playback gives
 * the started state up again, the same way [com.aryan.reader.tts.TtsService] already handles its
 * own starts. Unlike TTS's "preparing" notification there is no known content behind this start,
 * so the placeholder is silent and asks the platform to defer showing it.
 */
internal class PlaybackForegroundStartGuard(
    private val service: Service,
    private val notificationId: Int,
    private val channelId: String,
    private val channelName: String,
    private val diagnosticsTag: String,
    /**
     * How long a start without playback is given to become playback before it is given up. TTS
     * uses the same 15s for its own fallback notification.
     */
    private val idleGraceMs: Long = 15_000L,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var idleStop: Runnable? = null

    /**
     * Takes responsibility for the start. Call from [Service.onStartCommand] once the intent has
     * been handed to Media3, and only for starts the app did not send itself with `startService()`
     * (those carry no foreground obligation).
     *
     * @param startId the start being served, so a newer start is never stopped by this decision.
     * @param canReachForeground whether the start already has a way into the foreground: a playlist
     *   for Media3 to post its notification for, or Media3 already running the service there.
     */
    fun onStartCommand(startId: Int, canReachForeground: () -> Boolean) {
        cancelIdleStop()
        if (canReachForeground()) return

        // A channel the user turned off would get this notification rejected by the framework after
        // startForeground() returned, killing the process with no way to catch it. There is nothing
        // to play here anyway, so give the start up now instead of promoting.
        if (isServiceNotificationChannelBlocked(service, channelId)) {
            logMediaTransport(
                "playback-start-without-playback",
                "service=${service.javaClass.simpleName} startId=$startId channel-blocked"
            )
            service.stopSelf(startId)
            return
        }

        postPlaceholderForegroundNotification()
        val stop = Runnable {
            idleStop = null
            // Playback can start inside the grace window (a controller that was already loading a
            // book), and then Media3 owns the foreground notification under this same id: there is
            // nothing left to undo.
            if (canReachForeground()) return@Runnable
            logMediaTransport(
                "playback-start-without-playback",
                "service=${service.javaClass.simpleName} startId=$startId stopping"
            )
            try {
                service.stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Timber.tag(diagnosticsTag).w(e, "Failed to drop the placeholder foreground notification.")
            }
            service.stopSelf(startId)
        }
        idleStop = stop
        mainHandler.postDelayed(stop, idleGraceMs)
    }

    /** Call from [Service.onDestroy]: a destroyed service must not be stopped again from a timer. */
    fun release() {
        cancelIdleStop()
    }

    private fun cancelIdleStop() {
        idleStop?.let(mainHandler::removeCallbacks)
        idleStop = null
    }

    private fun postPlaceholderForegroundNotification() {
        try {
            val notificationManager = service.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW
                ).apply { setShowBadge(false) }
            )

            val notification = NotificationCompat.Builder(service, channelId)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setOngoing(false)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                service.startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                service.startForeground(notificationId, notification)
            }
            logMediaTransport(
                "playback-start-without-playback",
                "service=${service.javaClass.simpleName} notificationId=$notificationId promoted"
            )
        } catch (e: Exception) {
            // Without a notification there is no way back into the foreground, so give up the
            // started state now instead of waiting for the framework to kill the process.
            Timber.tag(diagnosticsTag).w(e, "Failed to post the placeholder foreground notification.")
            service.stopSelf()
        }
    }
}

