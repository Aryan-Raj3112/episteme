package com.aryan.reader.audiobook

import android.content.Context
import com.aryan.reader.shared.SharedListenAudioSource

/**
 * Listen's explicit audio-source choice per book: generated TTS or the
 * publisher's media overlay narration.
 *
 * Persisted per book and resolved with
 * [com.aryan.reader.shared.resolveSharedListenAudioSource], which prefers
 * narration when present. An explicit pick always wins afterwards — switching
 * sources never happens silently.
 */
private const val LISTEN_SOURCE_PREFS = "listen_prefs"

private fun listenSourceKey(bookId: String) = "listen_source_$bookId"

private const val LISTEN_OVERLAY_SPEED_KEY = "listen_overlay_speed"

fun loadListenAudioSource(context: Context, bookId: String): SharedListenAudioSource? {
    val raw = context.getSharedPreferences(LISTEN_SOURCE_PREFS, Context.MODE_PRIVATE)
        .getString(listenSourceKey(bookId), null) ?: return null
    return runCatching { SharedListenAudioSource.valueOf(raw) }.getOrNull()
}

fun saveListenAudioSource(context: Context, bookId: String, source: SharedListenAudioSource) {
    context.getSharedPreferences(LISTEN_SOURCE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(listenSourceKey(bookId), source.name)
        .apply()
}

/** Publisher-narration speed, independent from the TTS speech rate. */
fun loadListenOverlaySpeed(context: Context): Float =
    context.getSharedPreferences(LISTEN_SOURCE_PREFS, Context.MODE_PRIVATE)
        .getFloat(LISTEN_OVERLAY_SPEED_KEY, 1f)
        .coerceIn(0.5f, 3.0f)

fun saveListenOverlaySpeed(context: Context, speed: Float) {
    context.getSharedPreferences(LISTEN_SOURCE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(LISTEN_OVERLAY_SPEED_KEY, speed.coerceIn(0.5f, 3.0f))
        .apply()
}
