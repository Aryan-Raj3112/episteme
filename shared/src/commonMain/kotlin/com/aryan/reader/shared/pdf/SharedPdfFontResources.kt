package com.aryan.reader.shared.pdf

/**
 * Maps an annotation font path to the compose-resources path of a preset TTF
 * that ships with the shared module (same files as Android `assets/fonts`).
 *
 * Android resolves `asset:fonts/...` via `Typeface.createFromAsset`. iOS has no
 * assets bundle, so export loads the same presets from compose resources.
 * Returns null for non-preset paths (custom filesystem fonts).
 */
internal fun sharedPdfBundledFontResourcePath(fontPath: String?): String? {
    if (fontPath.isNullOrBlank()) return null
    if (!fontPath.startsWith("asset:fonts/")) return null
    return "files/${fontPath.removePrefix("asset:")}"
}

/**
 * System family name used when a preset TTF cannot be loaded on this platform.
 * Mirrors [sharedPdfFontFamily] so export falls back to the same family class
 * as on-screen rendering instead of an unrelated default.
 */
internal fun sharedPdfFallbackExportFontFamily(fontPath: String?): String? = when (fontPath) {
    "Merriweather",
    "Lora",
    "asset:fonts/merriweather.ttf",
    "asset:fonts/lora.ttf" -> "serif"
    "Roboto Mono",
    "asset:fonts/roboto_mono.ttf" -> "monospace"
    "Lato",
    "Lexend",
    "asset:fonts/lato.ttf",
    "asset:fonts/lexend.ttf" -> "sans-serif"
    else -> null
}
