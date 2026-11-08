// MediaNotificationPinning.kt
package com.aryan.reader

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap

/**
 * Whether a media playback notification must be pinned to the shade (ongoing) so it cannot be
 * removed by swiping. Playback is considered active while play is requested and the player is
 * ready or buffering. When playback is parked (paused) the notification stays swipeable and a
 * removal is delivered as the player's stop command, which the owning playback controller must
 * treat as a complete session stop.
 *
 * [sessionActivelySpeaking] covers the sessions whose media player does not describe the real
 * playback. Read-aloud with the device voice ("BASE" mode) keeps ExoPlayer dormant: between
 * chapters the wrapper reports idle/not-playing for a few hundred milliseconds while the system
 * voice is already speaking the next chapter, and an unpinned notification in that window is what
 * let an OEM media card clear the notification and stop the reading session.
 */
internal fun isPlaybackNotificationPinned(
    playWhenReady: Boolean,
    playbackState: Int,
    sessionActivelySpeaking: Boolean = false,
): Boolean = sessionActivelySpeaking ||
    (playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING))

/**
 * Whether a transport stop triggered by a *dismissed* media notification should be ignored.
 *
 * Media3 turns a notification dismissal into a `COMMAND_STOP` playback pending intent, so a system
 * media UI clearing our card is indistinguishable from the user pressing Stop and currently tears
 * the whole reading session down. A session that is actively speaking (or preparing the next
 * chunk) is pinned and cannot be dismissed by the user, so such a dismissal can only be external
 * cleanup; it must not end the session. A paused session stays dismissible and still stops.
 */
internal fun shouldIgnoreDismissedTransportStop(
    notificationDismissed: Boolean,
    sessionActivelySpeaking: Boolean,
): Boolean = notificationDismissed && sessionActivelySpeaking

/**
 * Media3's [androidx.media3.session.DefaultMediaNotificationProvider] always builds
 * swipe-dismissible notifications ([androidx.core.app.NotificationCompat.Builder.setOngoing] is
 * hardcoded to `false`), so a media notification can be swiped away mid-playback. That dismissal
 * sends a stop command to a session whose underlying playback may keep running, leaving stale UI
 * and playback without a notification.
 *
 * Call this after Media3 posts the media notification. While playback is active the posted
 * notification is re-posted with [Notification.FLAG_ONGOING_EVENT] so it cannot be swiped away.
 * Must be invoked on the main thread.
 */
@UnstableApi
/**
 * Whether [channelId] exists but the user has turned it off (`IMPORTANCE_NONE`).
 *
 * A foreground-service notification posted on such a channel is rejected inside
 * `NotificationManagerService`, and because that happens across a binder call the failure comes
 * back to this process as a `CannotPostForegroundServiceNotificationException("Bad notification for
 * startForeground")` delivered on the main thread through `ActivityThread$H` — *after*
 * `startForeground()` has returned, so no `try/catch` at the call site can catch it and the process
 * dies. Creating the channel again does not help either: the framework keeps the user's choice.
 * The only defence is not to post, which is what callers do with this check.
 * See docs/crashlytics-triage.md#119.
 */
internal fun isServiceNotificationChannelBlocked(context: Context, channelId: String): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
    val importance = context.getSystemService(NotificationManager::class.java)
        ?.getNotificationChannel(channelId)
        ?.importance
    return importance == NotificationManager.IMPORTANCE_NONE
}

internal fun pinPostedPlaybackNotification(
    context: Context,
    notificationId: Int,
    playWhenReady: Boolean,
    playbackState: Int,
    diagnosticsTag: String,
    sessionActivelySpeaking: Boolean = false,
) {
    if (!isPlaybackNotificationPinned(playWhenReady, playbackState, sessionActivelySpeaking)) {
        // Which notifications are left dismissible is what decides whether an OEM media card can
        // clear the session, so record the decision instead of only the successful pins.
        Timber.tag(diagnosticsTag).i(
            "Left media notification (id=%d) dismissible: playWhenReady=%s playbackState=%d.",
            notificationId,
            playWhenReady,
            playbackState
        )
        return
    }
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val postedNotification = notificationManager.activeNotifications
        .firstOrNull { it.id == notificationId }?.notification
        ?: run {
            Timber.tag(diagnosticsTag).w(
                "Could not pin media notification (id=%d): it is not posted.",
                notificationId
            )
            return
        }
    if (postedNotification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
    postedNotification.flags = postedNotification.flags or Notification.FLAG_ONGOING_EVENT
    notificationManager.notify(notificationId, postedNotification)
    Timber.tag(diagnosticsTag).i(
        "Pinned media notification (id=%d) while playback is active (state=%d).",
        notificationId,
        playbackState
    )
}

/**
 * A [BitmapLoader] that resolves bitmap requests synchronously and returns immediately-done
 * futures. Media3's notification provider schedules an asynchronous re-post when a bitmap future
 * is still pending, and that re-post path bypasses [androidx.media3.session.MediaSessionService
 * .onUpdateNotification], which would re-post the media notification without the ongoing pin
 * while playback is active. Serving the artwork synchronously keeps every notification post on
 * the pinning path.
 *
 * The artwork decode is bounded by [maxBitmapSize] (notification icons are small) and results are
 * cached per URI, so the synchronous cost is paid once per cover. All instance methods still run
 * on the session's application looper (main thread); use [prewarm] from playback start (any
 * thread) to move the first read+decode onto IO before Media3 asks for it.
 */
@UnstableApi
internal class MediaNotificationBitmapLoader(
    private val context: Context,
    private val maxBitmapSize: Int = DEFAULT_MAX_BITMAP_SIZE
) : BitmapLoader {

    private val cache = object : LinkedHashMap<String, Bitmap>(4, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > CACHE_SIZE
    }

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        load { decodeScaled(ByteArrayInputStream(data)) }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        synchronized(sharedCache) { sharedCache[uri.toString()] }?.let { return Futures.immediateFuture(it) }
        cache[uri.toString()]?.let { return Futures.immediateFuture(it) }
        return load {
            val bytes = readBytes(uri)
            decodeScaled(ByteArrayInputStream(bytes)).also { bitmap ->
                cache[uri.toString()] = bitmap
                synchronized(sharedCache) {
                    sharedCache[uri.toString()] = bitmap
                    trimSharedCacheLocked()
                }
            }
        }
    }

    override fun loadBitmapFromMetadata(metadata: MediaMetadata): ListenableFuture<Bitmap>? {
        return metadata.artworkData?.let { decodeBitmap(it) }
            ?: metadata.artworkUri?.let { loadBitmap(it) }
    }

    private fun load(decode: () -> Bitmap): ListenableFuture<Bitmap> {
        return try {
            Futures.immediateFuture(decode())
        } catch (t: Throwable) {
            if (t is InterruptedException) Thread.currentThread().interrupt()
            Timber.w(t, "Failed to decode media notification artwork")
            Futures.immediateFailedFuture(t)
        }
    }

    private fun readBytes(uri: Uri): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            input.copyTo(output, BUFFER_SIZE_BYTES)
            return output.toByteArray()
        }
        error("Unable to open artwork: $uri")
    }

    private fun decodeScaled(input: ByteArrayInputStream): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, /* outPadding = */ null, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds, maxBitmapSize)
        }
        input.reset()
        return BitmapFactory.decodeStream(input, /* outPadding = */ null, options)
            ?: error("Unable to decode artwork bitmap")
    }

    private fun computeInSampleSize(bounds: BitmapFactory.Options, maxSize: Int): Int {
        var sampleSize = 1
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return sampleSize
        while (bounds.outWidth / (sampleSize * 2) >= maxSize && bounds.outHeight / (sampleSize * 2) >= maxSize) {
            sampleSize *= 2
        }
        return sampleSize
    }

    companion object {
        private const val DEFAULT_MAX_BITMAP_SIZE = 512
        private const val CACHE_SIZE = 4
        private const val SHARED_CACHE_SIZE = 8
        private const val BUFFER_SIZE_BYTES = 16_384

        /**
         * Process-wide prewarmed artwork, shared by the TTS + audiobook loader
         * instances. Written from the IO prewarm path and read on Main;
         * always access under its own monitor.
         */
        private val sharedCache = LinkedHashMap<String, Bitmap>(8, 0.75f, true)
        private val prewarmExecutor: java.util.concurrent.ExecutorService =
            java.util.concurrent.Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "media-artwork-prewarm").apply { isDaemon = true }
            }

        private fun trimSharedCacheLocked() {
            val iterator = sharedCache.entries.iterator()
            while (sharedCache.size > SHARED_CACHE_SIZE && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        /**
         * Decode [uri] on IO and cache it before Media3 requests it, so the
         * synchronous [loadBitmap] miss (content-resolver read + double decode
         * on Main) usually becomes a cache hit. Safe to call from any thread;
         * failures only log. No behavior change when the artwork is already
         * cached or the prewarm loses the race — the sync path still serves.
         */
        fun prewarm(context: Context, uri: Uri?) {
            if (uri == null) return
            val key = uri.toString()
            synchronized(sharedCache) { if (sharedCache.containsKey(key)) return }
            val appContext = context.applicationContext
            prewarmExecutor.execute {
                runCatching {
                    val bytes = appContext.contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        input.copyTo(output, BUFFER_SIZE_BYTES)
                        output.toByteArray()
                    } ?: return@execute
                    val bitmap = decodeScaledStatic(ByteArrayInputStream(bytes), DEFAULT_MAX_BITMAP_SIZE)
                    synchronized(sharedCache) {
                        if (!sharedCache.containsKey(key)) {
                            sharedCache[key] = bitmap
                            trimSharedCacheLocked()
                        } else {
                            bitmap.recycle()
                        }
                    }
                }.onFailure { error ->
                    Timber.w(error, "Failed to prewarm media notification artwork")
                }
            }
        }

        private fun decodeScaledStatic(input: ByteArrayInputStream, maxSize: Int): Bitmap {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, /* outPadding = */ null, bounds)
            var sampleSize = 1
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                while (bounds.outWidth / (sampleSize * 2) >= maxSize &&
                    bounds.outHeight / (sampleSize * 2) >= maxSize
                ) {
                    sampleSize *= 2
                }
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            input.reset()
            return BitmapFactory.decodeStream(input, /* outPadding = */ null, options)
                ?: error("Unable to decode artwork bitmap")
        }
    }
}
