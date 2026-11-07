package com.aryan.reader.shared.tts

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log

private const val TAG = "SafeTextToSpeech"

/**
 * Builds a [TextToSpeech] engine, turning a synchronous construction failure into a null engine
 * plus an asynchronous [TextToSpeech.ERROR] init callback.
 *
 * The two-argument `TextToSpeech(context, listener)` binds to the configured engine *inside the
 * constructor* (`TextToSpeech.initTts` -> `TextToSpeech.connectToEngine` -> `Context.bindService`),
 * and that bind throws `SecurityException("Not allowed to bind to service ...")` when the package
 * stored as the user's default engine cannot be bound by this app: either it is not visible to us
 * (package visibility) or it is no longer installed for the current user. The framework does not
 * catch it, so an unguarded construction takes down whichever playback session built the engine
 * (docs/crashlytics-triage.md#117). Every TTS entry point in the app constructs one, so the guard
 * lives here instead of at each call site.
 *
 * The init callback is delivered on the main looper, matching how the framework reports its own
 * init results, so it can never run before the caller has stored the returned engine. Callers keep
 * their existing `status != TextToSpeech.SUCCESS` handling; the failure reported here arrives
 * through that same path.
 *
 * @return the engine, or null when construction threw.
 */
fun createTextToSpeechOrNull(
    context: Context,
    onInit: (Int) -> Unit,
): TextToSpeech? = createTextToSpeechOrNull(onInit) { TextToSpeech(context, onInit) }

/**
 * The guard itself, with the engine construction behind a [create] seam. Host tests cannot
 * reproduce the framework's bind failure by shadowing the constructor, so they drive this overload
 * with a throwing factory; production goes through the convenience overload above.
 */
fun createTextToSpeechOrNull(
    onInit: (Int) -> Unit,
    create: () -> TextToSpeech,
): TextToSpeech? = try {
    create()
} catch (e: RuntimeException) {
    Log.e(TAG, "TextToSpeech construction failed; engine unavailable", e)
    Handler(Looper.getMainLooper()).post { onInit(TextToSpeech.ERROR) }
    null
}
