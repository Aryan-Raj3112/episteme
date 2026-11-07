package com.aryan.reader.shared.search

import com.aryan.reader.shared.ReaderLocator

/**
 * Library content hit chosen for reader deep-link. The reader opens at the
 * hit's location with [query] pre-loaded in its own search UI (highlight +
 * prev/next); [epubOrigin]/[pdfOriginPage] seed jump history so Back
 * returns to the saved position. Platforms map [hit] to their open params;
 * ranking and matching stay shared.
 */
data class SharedLibrarySearchDeepLink(
    val bookId: String,
    val query: String,
    val hit: SharedGlobalSearchHit,
    /** Saved EPUB position at tap time; null skips the history seed. */
    val epubOrigin: ReaderLocator? = null,
    /** Saved PDF page at tap time; null skips the history seed. */
    val pdfOriginPage: Int? = null,
)

/** Quote the reader uses to find the in-reader occurrence matching a library hit. */
fun SharedGlobalSearchHit.navigationQuote(): String? =
    locator.textQuote?.takeIf { it.isNotBlank() } ?: snippet.takeIf { it.isNotBlank() }

/**
 * Matches a library hit's quote against live in-reader result snippets.
 * Returns the index of the best overlap, or null when nothing overlaps.
 * Normalization (case, ellipsis, whitespace) is shared so every shell
 * auto-steps to the same occurrence.
 */
fun bestReaderResultForLibraryHit(
    libraryTextQuote: String?,
    readerSnippets: List<String>,
): Int? {
    val distinctive = distinctiveWords(normalizeForMatch(libraryTextQuote.orEmpty()))
    if (distinctive.isEmpty() || readerSnippets.isEmpty()) return null
    var bestIndex: Int? = null
    var bestScore = 0
    readerSnippets.forEachIndexed { index, snippet ->
        val normalized = normalizeForMatch(snippet)
        if (normalized.isBlank()) return@forEachIndexed
        var score = 0
        for (word in distinctive) {
            if (normalized.contains(word)) score++
        }
        if (score > bestScore) {
            bestScore = score
            bestIndex = index
        }
    }
    return bestIndex
}

internal fun normalizeForMatch(raw: String): String =
    raw.lowercase()
        .replace("…", " ")
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.isNotBlank() }
        .joinToString(" ")

private fun distinctiveWords(normalized: String): List<String> =
    normalized.split(" ").filter { it.length >= 4 }.distinct()
