package com.aryan.reader.shared

import platform.Foundation.NSUserDefaults

/**
 * iOS mirror of Android's Smart AI engine choice: the "Dict" action opens the
 * in-app AI definition only when the user picked Smart AI in the lookup
 * settings; the default (Any App) goes through the app chooser instead.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosLookupDictionaryRoutingKey)
        return ReaderExternalLookupService.fromId(stored) == ReaderExternalLookupService.AI
    }
    set(_) = Unit

internal const val IosLookupDictionaryRoutingKey = "ios_reader_lookup_dictionary_service"
