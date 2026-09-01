// MediaNotificationPinning.kt
package com.aryan.reader

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
 */
internal fun isPlaybackNotificationPinned(playWhenReady: Boolean, playbackState: Int): Boolean =
    playWhenReady && (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)

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
internal fun pinPostedPlaybackNotification(
    context: Context,
    notificationId: Int,
    playWhenReady: Boolean,
    playbackState: Int,
    diagnosticsTag: String
) {
    if (!isPlaybackNotificationPinned(playWhenReady, playbackState)) return
    val notificationManager = context.getSystemService(NotificationManager::class.java)
    val postedNotification = notificationManager.activeNotifications
        .firstOrNull { it.id == notificationId }?.notification ?: return
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
 * cached per URI, so the synchronous cost is paid once per cover. All methods run on the session's
 * application looper (main thread).
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
        cache[uri.toString()]?.let { return Futures.immediateFuture(it) }
        return load {
            val bytes = readBytes(uri)
            decodeScaled(ByteArrayInputStream(bytes)).also { bitmap -> cache[uri.toString()] = bitmap }
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
        private const val BUFFER_SIZE_BYTES = 16_384
    }
}
