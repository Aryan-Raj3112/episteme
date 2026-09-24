package com.aryan.reader.shared

import com.aryan.reader.shared.ui.sharedAndroidMobileApplicationContext

/**
 * Android benchmark: the selection-menu "Dict" action uses the Smart AI engine
 * when the persisted engine preference ("use_online_dictionary", default true —
 * see PdfPreferences.loadUseOnlineDict) is on and AI features are available.
 * The shared menu reads this flag instead of duplicating the app-module
 * preference helpers, which the shared module cannot see.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val context = sharedAndroidMobileApplicationContext() ?: return false
        val prefs = context.getSharedPreferences("epub_reader_settings", android.content.Context.MODE_PRIVATE)
        return prefs.getBoolean("use_online_dictionary", true)
    }
    set(_) = Unit
