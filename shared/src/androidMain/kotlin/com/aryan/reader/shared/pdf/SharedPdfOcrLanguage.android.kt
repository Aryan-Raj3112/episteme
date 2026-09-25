package com.aryan.reader.shared.pdf

/**
 * Android actual: ML Kit v2 bundles per-script recognizers for every option the
 * shared picker offers (Latin, Devanagari, Chinese, Japanese, Korean).
 */
actual val SharedPdfOcrLanguage.isSupportedOnPlatform: Boolean
    get() = true
