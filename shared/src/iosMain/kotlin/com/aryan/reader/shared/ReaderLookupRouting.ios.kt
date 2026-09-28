package com.aryan.reader.shared

import platform.Foundation.NSUserDefaults

/**
 * iOS mirror of Android's Smart AI engine choice: the "Dict" action opens the
 * in-app AI definition only when the user explicitly picked Smart AI in the
 * lookup settings. Default is "no selection" (nothing persisted): the lookup
 * falls through to the external-app flow, which prompts to choose an app.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() {
        val stored = NSUserDefaults.standardUserDefaults.stringForKey(IosLookupDictionaryRoutingKey)
            ?: return false
        return ReaderExternalLookupService.fromId(stored) == ReaderExternalLookupService.AI
    }
    set(_) = Unit

internal const val IosLookupDictionaryRoutingKey = "ios_reader_lookup_dictionary_service"
