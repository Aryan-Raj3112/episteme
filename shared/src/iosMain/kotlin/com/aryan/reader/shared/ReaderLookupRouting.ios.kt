package com.aryan.reader.shared

import platform.Foundation.NSUserDefaults

/**
 * iOS mirror of Android's Smart AI engine choice: the "Dict" action opens the
 * in-app AI definition unless the user explicitly switched to an external app.
 * The default is Smart AI (Android benchmark), so a fresh install gets the
 * in-app definition — and, for free accounts, the Pro upsell — without a
 * settings detour. Unavailable AI still falls back to the app chooser.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosLookupDictionaryRoutingKey)
            ?: return ReaderDefaultDictionaryLookupService == ReaderExternalLookupService.AI
        return ReaderExternalLookupService.fromId(stored) == ReaderExternalLookupService.AI
    }
    set(value) {
        NSUserDefaults.standardUserDefaults.setObject(
            (if (value) ReaderExternalLookupService.AI else ReaderExternalLookupService.ANY_APP).id,
            forKey = IosLookupDictionaryRoutingKey,
        )
    }

internal const val IosLookupDictionaryRoutingKey = "ios_reader_lookup_dictionary_service"
