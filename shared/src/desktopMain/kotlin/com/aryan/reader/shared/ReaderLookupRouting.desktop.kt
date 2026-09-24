package com.aryan.reader.shared

/**
 * Desktop opens external lookup URLs directly (SharedReaderChrome) and has no
 * in-app AI dictionary routing, so the shared mobile menu logic never consults
 * this. Kept false to mirror the "external engine" default.
 */
actual var readerLookupUsesAiDictionary: Boolean
    get() = false
    set(_) = Unit
