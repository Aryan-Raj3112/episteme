package com.aryan.reader.shared

/**
 * Matches Android EPUB search: case-insensitive substring matches whose first
 * character is at the beginning of a word. The end of the query does not need
 * to be a word boundary.
 */
fun readerWordStartMatchOffsets(text: String, query: String): List<Int> {
    val needle = query.trim()
    if (needle.isEmpty() || text.isEmpty()) return emptyList()

    return buildList {
        var from = 0
        while (from < text.length) {
            val found = text.indexOf(needle, startIndex = from, ignoreCase = true)
            if (found < 0) break
            if (found == 0 || !text[found - 1].isLetterOrDigit()) add(found)
            from = found + needle.length.coerceAtLeast(1)
        }
    }
}
