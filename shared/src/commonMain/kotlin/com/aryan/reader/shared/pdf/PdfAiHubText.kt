package com.aryan.reader.shared.pdf

/**
 * Character budget shared by the phone PDF AI hub actions. Mirrors the EPUB
 * reader hub (`SharedMobileEpubReader`), which caps chapter/recap payloads at
 * the same size before invoking `ReaderAiFeature` actions.
 */
const val PDF_AI_HUB_MAX_CHARS = 24_000

/**
 * How many trailing pages (current page included) the PDF AI hub may open
 * text sessions for when building a recap. Bounds concurrent
 * `PdfTextPageSession` handles opened by a single hub presentation; the
 * [PDF_AI_HUB_MAX_CHARS] cap does the content bounding, mirroring how the
 * EPUB hub caps joined chapter text.
 */
const val PDF_AI_HUB_RECAP_PAGE_WINDOW = 5

/**
 * Builds the recap payload for the phone PDF AI hub from page texts ordered
 * newest-first (current page first, as collected from hub text sessions).
 *
 * Blank pages contribute nothing; the surviving pages are re-ordered
 * chronologically, joined, and capped at [maxChars]. Returns `null` when no
 * page yielded text so callers can no-op exactly like the EPUB hub does.
 */
fun buildPdfAiHubRecapText(
    pageTextsNewestFirst: List<String?>,
    maxChars: Int = PDF_AI_HUB_MAX_CHARS,
): String? {
    val chronological = pageTextsNewestFirst
        .asReversed()
        .mapNotNull { it?.takeIf(String::isNotBlank) }
    if (chronological.isEmpty()) return null
    return chronological
        .joinToString("\n\n")
        .take(maxChars)
        .takeIf { it.isNotBlank() }
}
