package com.aryan.reader

import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import androidx.media3.common.Player
import timber.log.Timber

/**
 * Single tag for diagnosing external media button routing (headset/Bluetooth taps) and
 * TTS/audiobook notification issues. Every line prints "event=<name>" plus a state snapshot
 * so logs from TtsService, TtsPlaybackManager, and AudiobookPlaybackService can be correlated.
 */
const val MEDIA_TRANSPORT_DIAG_TAG = "MEDIA_TRANSPORT_DIAG"

internal fun logMediaTransport(event: String, details: String) {
    Timber.tag(MEDIA_TRANSPORT_DIAG_TAG).i("event=%s %s", event, details)
}

internal fun playerTransportSnapshot(player: Player?): String {
    if (player == null) return "player=uninitialized"
    return "playbackState=${player.playbackState} playWhenReady=${player.playWhenReady} " +
        "isPlaying=${player.isPlaying} suppression=${player.playbackSuppressionReason} " +
        "mediaItems=${player.mediaItemCount} index=${player.currentMediaItemIndex}"
}

internal fun mediaButtonKeyEventDetails(intent: Intent): String {
    val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
    } else {
        @Suppress("DEPRECATION")
        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent
    } ?: return "keyEvent=null"
    return "keyCode=${event.keyCode}(${KeyEvent.keyCodeToString(event.keyCode)}) " +
        "action=${event.action} repeat=${event.repeatCount} source=${event.source}"
}
