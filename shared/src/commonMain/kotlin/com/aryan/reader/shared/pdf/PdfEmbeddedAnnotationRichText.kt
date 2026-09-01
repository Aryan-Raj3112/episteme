package com.aryan.reader.shared.pdf

private val pdfRichTextPrelude = Regex("<\\?[^>]*\\?>|<!--.*?-->|<!\\[CDATA\\[|\\]\\]>", RegexOption.DOT_MATCHES_ALL)
private val pdfRichTextLineBreak = Regex("<br\\s*/?>|</(?:p|div|span|h[1-6]|li)\\s*>", RegexOption.IGNORE_CASE)
private val pdfRichTextTag = Regex("<[^>]*>")
private val pdfRichTextEntity = Regex("&(?:#x([0-9A-Fa-f]+)|#([0-9]+)|([A-Za-z][A-Za-z0-9]*));")
private val pdfRichTextWhitespaceRun = Regex("[\\t\\x0B\\f\\r ]+")
private val pdfRichTextBlankLineRun = Regex("\n{2,}")

private val pdfRichTextNamedEntities = mapOf(
    "lt" to "<",
    "gt" to ">",
    "amp" to "&",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to "\u00A0",
)

/**
 * Extracts the readable comment text from a PDF annotation's rich-content
 * (/RC) markup. PDF producers store the rich-text variant of /Contents as an
 * XHTML fragment — often including an XML declaration and namespace-heavy
 * body tags — which must never be surfaced raw in the UI.
 *
 * Returns the markup's plain text with entities decoded and whitespace
 * collapsed, or an empty string when the markup carries no visible text.
 */
fun sharedPdfEmbeddedAnnotationRichText(markup: String): String {
    if (markup.isBlank()) return ""
    val text = markup
        .replace(pdfRichTextPrelude, "")
        .replace(pdfRichTextLineBreak, "\n")
        .replace(pdfRichTextTag, "")
        .replace(pdfRichTextEntity) { match ->
            val hex = match.groupValues[1]
            val decimal = match.groupValues[2]
            when {
                hex.isNotEmpty() -> hex.toIntOrNull(16)?.toChar()?.toString().orEmpty()
                decimal.isNotEmpty() -> decimal.toIntOrNull()?.toChar()?.toString().orEmpty()
                else -> pdfRichTextNamedEntities[match.groupValues[3].lowercase()].orEmpty()
            }
        }
    return text
        .split('\n')
        .joinToString("\n") { line -> line.trim().replace(pdfRichTextWhitespaceRun, " ") }
        .replace(pdfRichTextBlankLineRun, "\n")
        .trim()
}
