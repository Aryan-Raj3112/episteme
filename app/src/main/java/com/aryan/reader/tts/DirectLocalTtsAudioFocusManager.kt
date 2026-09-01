package com.aryan.reader.tts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.aryan.reader.logMediaTransport
import timber.log.Timber

internal enum class DirectLocalTtsFocusChange {
    TRANSIENT_LOSS,
    REGAINED,
    PERMANENT_LOSS,
}

internal fun resolveDirectLocalTtsFocusChange(focusChange: Int): DirectLocalTtsFocusChange? = when (focusChange) {
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> DirectLocalTtsFocusChange.TRANSIENT_LOSS
    AudioManager.AUDIOFOCUS_GAIN -> DirectLocalTtsFocusChange.REGAINED
    AudioManager.AUDIOFOCUS_LOSS -> DirectLocalTtsFocusChange.PERMANENT_LOSS
    else -> null
}

internal interface DirectLocalTtsAudioFocusListener {
    fun onLocalTtsInterruptionBegan()
    fun onLocalTtsInterruptionEnded(canResume: Boolean)
    fun onLocalTtsOutputBecameNoisy()
}

/**
 * Owns audio focus and the "becoming noisy" broadcast for direct-local TTS.
 *
 * Android's TextToSpeech engine never requests audio focus on the app's behalf, so without this
 * manager phone calls and other transient focus holders never pause speech, and the framework's
 * media button session election does not reliably route headset buttons to the TTS session.
 *
 * The manager only translates system signals; the pause/resume decision is made by
 * [TtsPlaybackManager] through the shared `LocalTtsInterruptionPolicy` reducer, mirroring iOS.
 */
internal class DirectLocalTtsAudioFocusManager(
    context: Context,
    private val listener: DirectLocalTtsAudioFocusListener
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var focusRequest: AudioFocusRequest? = null
    private var focusHeld = false
    private var interruptionActive = false
    private var becomingNoisyRegistered = false

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        handleFocusChange(focusChange)
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-output-became-noisy")
                listener.onLocalTtsOutputBecameNoisy()
            }
        }
    }

    fun isFocusHeld(): Boolean = focusHeld

    fun isInterruptionActive(): Boolean = interruptionActive

    fun requestFocus(): Boolean {
        if (interruptionActive) {
            Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-request blocked interruptionActive=true")
            logMediaTransport("tts-focus-request-blocked", "interruptionActive=true")
            return false
        }
        if (focusHeld) return true
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            // Spoken audio must pause on duckable transient loss, not lower its volume.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(focusChangeListener, mainHandler)
            .build()
            .also { focusRequest = it }
        val result = audioManager.requestAudioFocus(request)
        focusHeld = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (focusHeld) registerBecomingNoisyReceiver()
        Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-request result=$result held=$focusHeld")
        logMediaTransport("tts-focus-request", "result=$result held=$focusHeld")
        return focusHeld
    }

    fun abandonFocus() {
        val hadRequest = focusRequest != null
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
        focusHeld = false
        interruptionActive = false
        unregisterBecomingNoisyReceiver()
        Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-abandoned")
        logMediaTransport("tts-focus-abandoned", "hadRequest=$hadRequest")
    }

    private fun handleFocusChange(focusChange: Int) {
        when (val change = resolveDirectLocalTtsFocusChange(focusChange)) {
            DirectLocalTtsFocusChange.TRANSIENT_LOSS -> {
                interruptionActive = true
                Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-transient-loss")
                logMediaTransport("tts-focus-transient-loss", "focusChange=$focusChange")
                listener.onLocalTtsInterruptionBegan()
            }
            DirectLocalTtsFocusChange.REGAINED -> {
                interruptionActive = false
                Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-regained")
                logMediaTransport("tts-focus-regained", "focusChange=$focusChange")
                listener.onLocalTtsInterruptionEnded(canResume = true)
            }
            DirectLocalTtsFocusChange.PERMANENT_LOSS -> {
                Timber.tag(TTS_LOCAL_SPEAK_DIAG_TAG).i("focus-permanent-loss")
                logMediaTransport("tts-focus-permanent-loss", "focusChange=$focusChange")
                abandonFocus()
                listener.onLocalTtsInterruptionEnded(canResume = false)
            }
            null -> logMediaTransport("tts-focus-ignored", "focusChange=$focusChange change=unknown")
        }
    }

    private fun registerBecomingNoisyReceiver() {
        if (becomingNoisyRegistered) return
        ContextCompat.registerReceiver(
            appContext,
            becomingNoisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        becomingNoisyRegistered = true
    }

    private fun unregisterBecomingNoisyReceiver() {
        if (!becomingNoisyRegistered) return
        runCatching { appContext.unregisterReceiver(becomingNoisyReceiver) }
        becomingNoisyRegistered = false
    }
}
