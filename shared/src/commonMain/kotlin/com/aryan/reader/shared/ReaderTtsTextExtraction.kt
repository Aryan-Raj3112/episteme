package com.aryan.reader.shared

/**
 * Lightweight HTML-to-plain-text extraction for TTS headless content. Mirrors
 * the Android behavior of stripping markup and decoding entities before
 * speaking, without the full reader pipeline.
 */
fun sharedHtmlToPlainText(html: String): String {
    return sharedDecodeHtmlEntities(
        html
            .replace(SharedHtmlScriptStyleRegex, " ")
            .replace(SharedHtmlBreakRegex, "\n")
            .replace(SharedHtmlBlockCloseRegex, "\n")
            .replace(SharedHtmlTagRegex, " ")
    )
        .replace('\u0000', ' ')
        .replace(SharedHtmlInlineSpaceRegex, " ")
        .replace(SharedHtmlNewlineSpaceRegex, "\n")
        .replace(SharedHtmlBlankLineRegex, "\n\n")
        .trim()
}

fun sharedDecodeHtmlEntities(text: String): String {
    var output = text.replace(SharedHtmlNumericEntityRegex) { match ->
        val codePoint = match.groupValues[1].takeIf(String::isNotBlank)?.toIntOrNull(16)
            ?: match.groupValues[2].toIntOrNull()
        codePoint?.takeIf { it in 0..0x10FFFF && it !in 0xD800..0xDFFF }?.let(::sharedCodePointToString)
            ?: match.value
    }
    return SharedHtmlNamedEntityRegex.replace(output) { match ->
        SharedHtmlNamedEntities[match.groupValues[1].lowercase()] ?: match.value
    }
}

private fun sharedCodePointToString(codePoint: Int): String {
    if (codePoint <= 0xFFFF) return codePoint.toChar().toString()
    val value = codePoint - 0x10000
    return charArrayOf(
        ((value ushr 10) + 0xD800).toChar(),
        ((value and 0x3FF) + 0xDC00).toChar(),
    ).concatToString()
}

private val SharedHtmlScriptStyleRegex =
    Regex("(?is)<style\\b[^>]*>.*?</style>|<script\\b[^>]*>.*?</script>")
private val SharedHtmlBreakRegex = Regex("""(?i)<\s*br\s*/?\s*>""")
private val SharedHtmlBlockCloseRegex = Regex(
    """(?i)</\s*(?:p|div|section|article|aside|main|header|footer|h[1-6]|li|tr|table|blockquote|ul|ol)\s*>""",
)
private val SharedHtmlTagRegex = Regex("""(?is)<[^>]+>""")
private val SharedHtmlInlineSpaceRegex = Regex("""[ \t\x0B\f\r]+""")
private val SharedHtmlNewlineSpaceRegex = Regex(""" *\n *""")
private val SharedHtmlBlankLineRegex = Regex("""\n{3,}""")
private val SharedHtmlNumericEntityRegex = Regex("""&#(?:x([0-9a-fA-F]+)|([0-9]+));""")
private val SharedHtmlNamedEntityRegex = Regex("""&([A-Za-z][A-Za-z0-9]+);""")

private val SharedHtmlNamedEntities = mapOf(
    "nbsp" to " ", "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "ensp" to " ", "emsp" to " ", "thinsp" to " ", "shy" to "", "ndash" to "–", "mdash" to "—",
    "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "laquo" to "«", "raquo" to "»",
    "hellip" to "…", "bull" to "•", "middot" to "·", "copy" to "©", "reg" to "®", "trade" to "™",
)
