package com.aryan.reader.shared.docparse

/**
 * iOS actual that keeps using the hand-written converter until the platform
 * binds md4c via cinterop. Math spans are not yet extracted here;
 * [isNative] reports false so callers can surface the limitation honestly.
 */
actual object SharedMarkdownParser {
    actual fun isNative(): Boolean = false

    actual fun toHtml(markdown: String, flags: Int): String? =
        SharedMarkdownConverter.convert(markdown).joinToString(separator = "\n") { it.html }
}
