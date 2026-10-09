package com.aryan.reader.tts

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.loadNativeVoice
import com.aryan.reader.shared.ReaderTtsVoiceOverride
import com.aryan.reader.tts.TtsPlaybackManager.TtsMode

/**
 * Independent TTS voice state for Listen (book/audiobook) playback.
 *
 * Reader stays the benchmark: Listen inherits the Reader engine + voice until
 * the user makes an explicit choice in the Listen voice settings, then stays
 * pinned to it. First-write-wins is implemented via [SharedPreferences.contains]:
 * absent key -> live Reader value, present key -> Listen value.
 */
internal const val LISTEN_TTS_MODE_KEY = "listen_tts_mode"
internal const val LISTEN_TTS_SPEAKER_KEY = "listen_tts_speaker"
internal const val LISTEN_TTS_SPEAKER_NAME_KEY = "listen_tts_speaker_name"
internal const val LISTEN_NATIVE_TTS_VOICE_KEY = "listen_native_tts_voice"

/** Explicit "system default" marker: SharedPreferences cannot distinguish an
 * absent string from a stored null, so blank means system default while an
 * absent key means "inherit the Reader voice". Shared with iOS via
 * [ReaderTtsVoiceOverride] so both platforms resolve Listen's voice identically. */
private const val LISTEN_NATIVE_VOICE_SYSTEM_DEFAULT = ReaderTtsVoiceOverride.SYSTEM_DEFAULT

private fun listenReaderPrefs(context: Context) =
    context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)

private fun listenSpeakerPrefs(context: Context) =
    context.getSharedPreferences(TTS_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)

@OptIn(UnstableApi::class)
fun loadListenTtsMode(context: Context): TtsMode {
    val prefs = listenReaderPrefs(context)
    val savedName = if (prefs.contains(LISTEN_TTS_MODE_KEY)) {
        prefs.getString(LISTEN_TTS_MODE_KEY, TtsMode.BASE.name)
    } else {
        // Inherit the Reader engine until the user chooses otherwise.
        loadTtsMode(context).name
    }
    return resolveTtsModeForCurrentBuild(context, savedName)
}

@OptIn(UnstableApi::class)
fun saveListenTtsMode(context: Context, mode: TtsMode) {
    listenReaderPrefs(context).edit { putString(LISTEN_TTS_MODE_KEY, mode.name) }
}

fun loadListenTtsSpeaker(context: Context): String {
    val prefs = listenSpeakerPrefs(context)
    if (!prefs.contains(LISTEN_TTS_SPEAKER_KEY)) {
        // Inherit the Reader voice until the user chooses otherwise.
        return loadTtsSpeaker(context)
    }
    return normalizeTtsSpeakerId(prefs.getString(LISTEN_TTS_SPEAKER_KEY, DEFAULT_SPEAKER_ID))
}

fun saveListenTtsSpeaker(context: Context, speakerId: String) {
    listenSpeakerPrefs(context).edit {
        putString(LISTEN_TTS_SPEAKER_KEY, normalizeTtsSpeakerId(speakerId))
    }
}

fun loadListenTtsSpeakerName(context: Context): String? {
    val prefs = listenSpeakerPrefs(context)
    if (!prefs.contains(LISTEN_TTS_SPEAKER_NAME_KEY)) {
        return loadTtsSpeakerName(context)
    }
    return prefs.getString(LISTEN_TTS_SPEAKER_NAME_KEY, null)?.takeIf { it.isNotBlank() }
}

fun saveListenTtsSpeakerName(context: Context, name: String) {
    listenSpeakerPrefs(context).edit {
        putString(LISTEN_TTS_SPEAKER_NAME_KEY, name.trim().take(80))
    }
}

/**
 * Device (BASE-mode) voice for Listen. Null = system default, with an absent
 * key inheriting the Reader device voice.
 */
fun loadListenNativeVoice(context: Context): String? {
    val prefs = listenReaderPrefs(context)
    if (!prefs.contains(LISTEN_NATIVE_TTS_VOICE_KEY)) {
        return loadNativeVoice(context)
    }
    return prefs.getString(LISTEN_NATIVE_TTS_VOICE_KEY, null)
        ?.takeIf { it != LISTEN_NATIVE_VOICE_SYSTEM_DEFAULT }
}

fun saveListenNativeVoice(context: Context, voiceName: String?) {
    listenReaderPrefs(context).edit {
        putString(LISTEN_NATIVE_TTS_VOICE_KEY, voiceName ?: LISTEN_NATIVE_VOICE_SYSTEM_DEFAULT)
    }
}
