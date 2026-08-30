package com.aryan.reader

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aryan.reader.audiobook.AudiobookPlaybackService
import com.aryan.reader.tts.TtsService
import timber.log.Timber

/**
 * Resolves which playback service should receive media button events when there is no live
 * media session to deliver them to (the app was killed, or the session was just released and
 * the system is resuming the "last media button receiver").
 *
 * The framework requires exactly one manifest receiver for [Intent.ACTION_MEDIA_BUTTON] per
 * app; Media3's own `MediaButtonReceiver` cannot be used here because it expects a single
 * service handling `androidx.media3.session.MediaSessionService`, while this app has two
 * (TTS and audiobook). The last active playback service is persisted whenever a session
 * starts producing sound, so resumption goes back to the same surface.
 */
object MediaButtonRouting {
    private const val PREFS_NAME = "media_button_routing"
    private const val KEY_LAST_PLAYBACK_SERVICE = "last_playback_service"

    val TTS_SERVICE_CLASS_NAME: String = TtsService::class.java.name
    val AUDIOBOOK_SERVICE_CLASS_NAME: String = AudiobookPlaybackService::class.java.name

    fun recordPlaybackService(context: Context, serviceClass: Class<*>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PLAYBACK_SERVICE, serviceClass.name)
            .apply()
        logMediaTransport("media-button-routing-record", "service=${serviceClass.name}")
    }

    fun recordPlaybackService(context: Context, serviceClassName: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_PLAYBACK_SERVICE, serviceClassName)
            .apply()
        logMediaTransport("media-button-routing-record", "service=$serviceClassName")
    }

    fun storedPlaybackServiceClassName(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_PLAYBACK_SERVICE, null)
    }
}

internal fun resolveMediaButtonTargetServiceClassName(storedClassName: String?): String {
    return when (storedClassName) {
        MediaButtonRouting.AUDIOBOOK_SERVICE_CLASS_NAME -> MediaButtonRouting.AUDIOBOOK_SERVICE_CLASS_NAME
        else -> MediaButtonRouting.TTS_SERVICE_CLASS_NAME
    }
}

class MediaButtonRoutingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val targetClassName = resolveMediaButtonTargetServiceClassName(
            MediaButtonRouting.storedPlaybackServiceClassName(context)
        )
        logMediaTransport(
            "media-button-receiver-forward",
            "target=$targetClassName ${mediaButtonKeyEventDetails(intent)}"
        )
        val serviceIntent = Intent(intent).setComponent(
            ComponentName(context.packageName, targetClassName)
        )
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            Timber.tag(MEDIA_TRANSPORT_DIAG_TAG).w(e, "Failed to forward media button to $targetClassName")
        }
    }
}
