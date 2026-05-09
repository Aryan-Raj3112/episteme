package com.aryan.reader

import android.content.Context
import androidx.core.content.edit
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementPreferencesJson

private const val READER_PREFS_NAME = "reader_prefs"
private const val TTS_REPLACEMENTS_KEY = "tts_word_replacements_json"

fun loadTtsReplacementPreferences(context: Context): ReaderTtsReplacementPreferences {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    return ReaderTtsReplacementPreferencesJson.decodeOrEmpty(prefs.getString(TTS_REPLACEMENTS_KEY, null))
}

fun saveTtsReplacementPreferences(
    context: Context,
    preferences: ReaderTtsReplacementPreferences,
) {
    val prefs = context.getSharedPreferences(READER_PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit {
        putString(TTS_REPLACEMENTS_KEY, ReaderTtsReplacementPreferencesJson.encode(preferences))
    }
}

fun TtsChunk.withTtsReplacements(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String?,
): TtsChunk {
    val spoken = ReaderTtsReplacementEngine.apply(
        text = text,
        preferences = preferences,
        bookId = bookId,
    ).text
    return copy(spokenText = spoken.ifBlank { text })
}

fun List<TtsChunk>.withTtsReplacements(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String?,
): List<TtsChunk> = map { it.withTtsReplacements(preferences, bookId) }
