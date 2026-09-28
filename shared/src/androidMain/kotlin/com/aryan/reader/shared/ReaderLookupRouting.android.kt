package com.aryan.reader.shared

import com.aryan.reader.shared.ui.sharedAndroidMobileApplicationContext

/**
 * Android benchmark: the selection-menu "Dict" action uses the Smart AI engine
 * only when the user explicitly picked it (persisted "use_online_dictionary" —
 * see PdfPreferences.loadUseOnlineDict). Default is false ("no selection"):
 * with nothing persisted the lookup falls through to the external-app flow,
 * which prompts to choose an app.
 * The shared menu reads this flag instead of duplicating the app-module
 * preference helpers, which the shared module cannot see.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val context = sharedAndroidMobileApplicationContext() ?: return false
        val prefs = context.getSharedPreferences("epub_reader_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("use_online_dictionary", false)
    }
    set(_) = Unit
