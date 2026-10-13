package com.aryan.reader.shared.search

import com.aryan.reader.shared.BookItem

/**
 * Library merge for the "search inside files" toggle. Metadata matching is
 * untouched; when [searchInsideFiles] is on, books that only match content
 * are appended after metadata hits. Each row tells the UI whether the
 * metadata matched and carries top content hits for preview/expand.
 */
data class SharedLibraryContentRow(
    val book: BookItem,
    val metadataMatched: Boolean,
    val contentHits: List<SharedGlobalSearchHit> = emptyList(),
    val contentScore: Double? = null,
)

fun searchLibraryWithContent(
    books: List<BookItem>,
    query: String,
    searchInsideFiles: Boolean,
    metadataMatches: List<BookItem>,
    contentResults: List<SharedGlobalSearchBookResult>,
): List<SharedLibraryContentRow> {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return books.map { SharedLibraryContentRow(it, metadataMatched = true) }
    val metadataIds = metadataMatches.mapTo(mutableSetOf()) { it.id }
    val rows = metadataMatches.map { SharedLibraryContentRow(it, metadataMatched = true) }.toMutableList()
    if (!searchInsideFiles) return rows
    val contentByBook = contentResults.associateBy { it.bookId }
    // Attach content hits to metadata rows that also matched content.
    for (i in rows.indices) {
        val extra = contentByBook[rows[i].book.id]
        if (extra != null) {
            rows[i] = rows[i].copy(contentHits = extra.hits, contentScore = extra.score)
        }
    }
    // Content-only books follow, ordered by content score.
    val booksById = books.associateBy { it.id }
    val contentOnly = contentResults
        .filter { it.bookId !in metadataIds }
        .sortedByDescending { it.score }
        .mapNotNull { result ->
            booksById[result.bookId]?.let { book ->
                SharedLibraryContentRow(
                    book = book,
                    metadataMatched = false,
                    contentHits = result.hits,
                    contentScore = result.score,
                )
            }
        }
    return rows + contentOnly
}
