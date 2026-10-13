package com.aryan.reader.shared.search

import com.aryan.reader.shared.readerWordStartMatchOffsets
import kotlin.math.max

/**
 * Snippet builder. Finds the first word-start match of any query term (same
 * word-boundary rule as in-reader search), cuts [SharedGlobalSearchPolicy.SNIPPET_RADIUS]
 * chars of context, and returns match ranges inside the snippet for UI highlight.
 */
object SharedGlobalSearchSnippet {

    fun build(text: String, query: SharedSearchQuery): Pair<String, List<IntRange>> {
        if (text.isEmpty() || query.isEmpty) return "" to emptyList()
        val radius = SharedGlobalSearchPolicy.SNIPPET_RADIUS
        val needles = buildList {
            addAll(query.terms)
            addAll(query.phrases.flatMap { SharedGlobalSearchTokenizer.tokenize(it) })
            query.prefixTerm?.let { add(it) }
        }.distinct().filter { it.isNotBlank() }
        if (needles.isEmpty()) return "" to emptyList()

        var bestOffset = -1
        var bestNeedle = ""
        for (needle in needles.sortedByDescending { it.length }) {
            val offsets = readerWordStartMatchOffsets(text, needle)
            // Prefix term may match a word start partially (typed prefix).
            val resolved = offsets.firstOrNull() ?: run {
                if (needle == query.prefixTerm) findPrefixOffset(text, needle) else null
            }
            if (resolved != null) {
                bestOffset = resolved
                bestNeedle = needle
                break
            }
        }
        if (bestOffset < 0) {
            val head = text.take(radius * 2).trim()
            return head to emptyList()
        }
        val matchLen = if (bestNeedle == query.prefixTerm) {
            // Extend the prefix to the full word for a stable highlight.
            wordLengthAt(text, bestOffset)
        } else {
            bestNeedle.length
        }
        val start = max(0, bestOffset - radius)
        val end = (bestOffset + matchLen + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val snippet = prefix + text.substring(start, end).trim() + suffix
        // Locate the match inside the built snippet (robust to trimming).
        val matchText = text.substring(bestOffset, (bestOffset + matchLen).coerceAtMost(text.length))
        val inSnippet = snippet.indexOf(matchText, ignoreCase = true)
        val ranges = if (inSnippet >= 0) {
            listOf(IntRange(inSnippet, inSnippet + matchText.length - 1))
        } else {
            emptyList()
        }
        return snippet to ranges
    }

    private fun findPrefixOffset(text: String, prefix: String): Int? {
        if (prefix.isBlank()) return null
        var from = 0
        while (from < text.length) {
            val found = text.indexOf(prefix, startIndex = from, ignoreCase = true)
            if (found < 0) return null
            if (found == 0 || !text[found - 1].isLetterOrDigit()) return found
            from = found + 1
        }
        return null
    }

    private fun wordLengthAt(text: String, offset: Int): Int {
        var end = offset
        while (end < text.length && text[end].isLetterOrDigit()) end++
        return (end - offset).coerceAtLeast(1)
    }
}
