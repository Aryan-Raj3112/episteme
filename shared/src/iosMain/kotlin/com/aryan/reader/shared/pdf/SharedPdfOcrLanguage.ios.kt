@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.aryan.reader.shared.pdf

import kotlinx.cinterop.useContents
import platform.Foundation.NSProcessInfo
import platform.Vision.VNRecognizeTextRequest

/**
 * iOS actual: support is driven by the live Vision engine so scripts light up
 * automatically as Apple ships new OCR models (Devanagari and Marathi joined
 * the set in iOS 26). On iOS 17/18 the recognizer covers Latin/CJK only, so
 * those releases use a static Devanagari exclusion. When the engine query is
 * unavailable, fall back to the same iOS 17/18 baseline.
 */
actual val SharedPdfOcrLanguage.isSupportedOnPlatform: Boolean
    get() {
        val expandedSetOs = NSProcessInfo.processInfo.operatingSystemVersion.useContents {
            majorVersion >= 26
        }
        if (!expandedSetOs && this == SharedPdfOcrLanguage.DEVANAGARI) {
            // iOS 17/18 baseline: Vision recognized no Devanagari.
            return false
        }
        val supported = supportedIosVisionLanguages()
        if (supported.isEmpty()) return true
        val supportedPrimaries = supported.map { it.primaryLanguageCodeSafe() }.toSet()
        return visionLanguageCodes.any { it.primaryLanguageCodeSafe() in supportedPrimaries }
    }

private fun String.primaryLanguageCodeSafe(): String =
    substringBefore('-').lowercase()

private var cachedIosVisionLanguages: Set<String>? = null

/**
 * Languages the on-device Vision engine can actually recognize for the
 * accurate level. Returns an empty set when the query fails (for example on a
 * simulator without OCR models); callers then fall back to the iOS 17/18
 * baseline above.
 */
internal fun supportedIosVisionLanguages(): Set<String> {
    cachedIosVisionLanguages?.let { return it }
    val languages = runCatching {
        val request = VNRecognizeTextRequest()
        val result = request.supportedRecognitionLanguagesAndReturnError(null)
        result.orEmpty().mapNotNull { it as? String }
    }.getOrDefault(emptyList())
    val supported = languages.toSet()
    cachedIosVisionLanguages = supported
    return supported
}
