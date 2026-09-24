package com.aryan.reader.shared

import platform.Foundation.NSUserDefaults

/**
 * iOS mirror of Android's Smart AI engine choice: the "Dict" action opens the
 * in-app AI definition only when the user picked Smart AI in the lookup
 * settings. Android parity (PdfPreferences use_online_dictionary = true):
 * a fresh install with nothing persisted defaults to Smart AI.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosLookupDictionaryRoutingKey)
            ?: return true
        return ReaderExternalLookupService.fromId(stored) == ReaderExternalLookupService.AI
    }
    set(_) = Unit

internal const val IosLookupDictionaryRoutingKey = "ios_reader_lookup_dictionary_service"
