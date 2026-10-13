package com.aryan.reader.shared.search

import com.aryan.reader.shared.readerWordStartMatchOffsets
import kotlin.math.max

/**
 * Snippet builder. Finds the first word-start match of any query term (same
 * word-boundary rule as in-reader search), cuts [SharedGlobalSearchPolicy.SNIPPET_RADIUS]
 * chars of context, and returns match ranges inside the snippet for UI highlight.
 *
 * Every query term is highlighted, not just the one that won the window race:
 * "when my love" has to highlight all three words to read as a match.
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
        val matchLen = matchLength(text, bestOffset, bestNeedle, query)
        val start = max(0, bestOffset - radius)
        val end = (bestOffset + matchLen + radius).coerceAtMost(text.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        val snippet = prefix + text.substring(start, end).trim() + suffix
        return snippet to snippetMatchRanges(snippet, needles, query)
    }

    /** Highlight spans for every needle occurrence inside [snippet]. */
    private fun snippetMatchRanges(
        snippet: String,
        needles: List<String>,
        query: SharedSearchQuery,
    ): List<IntRange> {
        val raw = buildList {
            for (needle in needles) {
                val offsets = readerWordStartMatchOffsets(snippet, needle).ifEmpty {
                    // The snippet is a trimmed window, so a needle that straddled
                    // the cut survives as a prefix match at its word start.
                    if (needle == query.prefixTerm) listOfNotNull(findPrefixOffset(snippet, needle)) else emptyList()
                }
                for (offset in offsets) {
                    val length = if (needle == query.prefixTerm) {
                        wordLengthAt(snippet, offset)
                    } else {
                        needle.length
                    }
                    add(IntRange(offset, (offset + length - 1).coerceAtMost(snippet.length - 1)))
                }
            }
        }
        return raw.sortedBy { it.first }.fold(mutableListOf()) { merged, range ->
            val last = merged.lastOrNull()
            // Overlaps come from a prefix match swallowing the same word another
            // needle already covered; merging keeps the annotated string valid.
            if (last != null && range.first <= last.last + 1) {
                merged[merged.lastIndex] = IntRange(last.first, maxOf(last.last, range.last))
            } else {
                merged += range
            }
            merged
        }
    }

    private fun matchLength(text: String, offset: Int, needle: String, query: SharedSearchQuery): Int =
        if (needle == query.prefixTerm) {
            // Extend the prefix to the full word for a stable highlight.
            wordLengthAt(text, offset)
        } else {
            needle.length
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
