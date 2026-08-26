package com.aryan.reader.shared

/**
 * Normalizes a reader hyperlink without resolving it against an EPUB chapter.
 *
 * Protocol-relative links are external web links, not EPUB-relative paths.  A
 * concrete scheme keeps them safe to pass to Android intents, UIKit URL
 * handlers, and the desktop/mobile link resolvers while leaving fragment and
 * chapter-relative references untouched.
 */
fun normalizeReaderHref(href: String): String {
    val trimmed = href.trim()
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("www.", ignoreCase = true) -> "https://$trimmed"
        else -> trimmed
    }
}

/** Returns the URI scheme when [href] has one, otherwise null. */
fun readerHrefScheme(href: String): String? {
    val value = normalizeReaderHref(href)
    val colonIndex = value.indexOf(':')
    if (colonIndex <= 0) return null
    val firstPathIndex = listOf(value.indexOf('/'), value.indexOf('?'), value.indexOf('#'))
        .filter { it >= 0 }
        .minOrNull()
    if (firstPathIndex != null && firstPathIndex < colonIndex) return null
    val candidate = value.substring(0, colonIndex)
    return candidate.takeIf {
        it.first().isLetter() && it.all { char ->
            char.isLetterOrDigit() || char == '+' || char == '-' || char == '.'
        }
    }
}

/**
 * Whether [href] should leave the EPUB reader and be handled externally.
 * Keep this list in sync for native links, WebViews, and the desktop engine.
 */
fun isReaderExternalHref(href: String): Boolean = when (readerHrefScheme(href)?.lowercase()) {
    "http", "https", "mailto", "tel", "sms", "geo" -> true
    else -> false
}
