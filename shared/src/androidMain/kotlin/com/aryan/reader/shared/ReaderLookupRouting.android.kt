package com.aryan.reader.shared

import android.content.Context
import com.aryan.reader.shared.ui.sharedAndroidMobileApplicationContext

/**
 * SharedPreferences file backing [readerLookupUsesAiDictionary]. Every writer
 * (the EPUB reader, the PDF reader, and the shared selection menu) goes through
 * this module so the per-platform sheets and the shared menu can never disagree
 * about the current engine.
 */
private const val LookupDictionaryPrefsName = "epub_reader_settings"
private const val LookupUsesAiDictionaryKey = "use_online_dictionary"

/**
 * Legacy file the EPUB reader used to persist its engine choice into, before the
 * EPUB/PDF/shared readers were unified on [LookupDictionaryPrefsName]. Read once
 * to preserve installs that picked an external app before the unification.
 */
private const val LegacyEpubLookupPrefsName = "reader_prefs"

/**
 * Android benchmark: the selection-menu "Dict" action uses the Smart AI engine
 * unless the user explicitly switched to an external app (persisted
 * "use_online_dictionary"). The default is Smart AI so a fresh install gets the
 * in-app definition — and, for free accounts, the Pro upsell — with no settings
 * detour; unavailable AI still falls back to the app chooser.
 *
 * The shared menu reads this flag instead of duplicating the app-module
 * preference helpers, which the shared module cannot see. The app-module
 * helpers delegate here so there is exactly one persisted copy.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val context = sharedAndroidMobileApplicationContext() ?: return androidDefaultUsesAiDictionary()
        return readUsesAiDictionary(context)
    }
    set(value) {
        val context = sharedAndroidMobileApplicationContext() ?: return
        writeUsesAiDictionary(context, value)
    }

/** Whether a fresh install routes "Dict" to the in-app AI definition. */
fun androidDefaultUsesAiDictionary(): Boolean =
    ReaderDefaultDictionaryLookupService == ReaderExternalLookupService.AI

private fun readUsesAiDictionary(context: Context): Boolean {
    val prefs = context.getSharedPreferences(LookupDictionaryPrefsName, Context.MODE_PRIVATE)
    if (prefs.contains(LookupUsesAiDictionaryKey)) {
        return prefs.getBoolean(LookupUsesAiDictionaryKey, androidDefaultUsesAiDictionary())
    }
    // Nothing persisted in the unified file: fall back to the legacy EPUB
    // reader copy so an existing external-app choice is not silently reset.
    val legacy = context.getSharedPreferences(LegacyEpubLookupPrefsName, Context.MODE_PRIVATE)
    if (legacy.contains(LookupUsesAiDictionaryKey)) {
        val migrated = legacy.getBoolean(LookupUsesAiDictionaryKey, androidDefaultUsesAiDictionary())
        writeUsesAiDictionary(context, migrated)
        return migrated
    }
    return androidDefaultUsesAiDictionary()
}

private fun writeUsesAiDictionary(context: Context, usesAi: Boolean) {
    context.getSharedPreferences(LookupDictionaryPrefsName, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(LookupUsesAiDictionaryKey, usesAi)
        .apply()
}

/**
 * App-module bridge for the EPUB/PDF reader sheets. Mirrors [readerLookupUsesAiDictionary]
 * but takes an explicit [Context] so the readers read/write the same persisted copy
 * the shared selection menu uses.
 */
fun loadAndroidReaderLookupUsesAiDictionary(context: Context): Boolean = readUsesAiDictionary(context)

/** @see loadAndroidReaderLookupUsesAiDictionary */
fun saveAndroidReaderLookupUsesAiDictionary(context: Context, usesAi: Boolean) =
    writeUsesAiDictionary(context, usesAi)
