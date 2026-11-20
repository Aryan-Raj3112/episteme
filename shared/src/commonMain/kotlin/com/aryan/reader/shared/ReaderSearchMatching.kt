package com.aryan.reader.shared

/**
 * The single reader-search matching contract.
 *
 * Every consumer of a hit — the results list, the paginated locator and the
 * WebView painter — must agree on *which* occurrence of the query a hit is.
 * They only agree when they flatten a chapter the same way and apply the same
 * rule, so both live here:
 *
 * - flatten text in document order;
 * - collapse every whitespace run to one space;
 * - insert one space where a block ends ([READER_SEARCH_BLOCK_BOUNDARY_TAGS]);
 * - match case-insensitively, and require the match to start a word (the end
 *   of the query does not need a word boundary: "read" matches "reader").
 *
 * Android is the benchmark. Divergence between these consumers is what puts a
 * tapped result on a neighbouring occurrence, which reads as search results
 * being "out of order".
 */

/** Tags that end a word run when a chapter is flattened for search. */
val READER_SEARCH_BLOCK_BOUNDARY_TAGS: Set<String> = setOf(
    "address",
    "article",
    "aside",
    "blockquote",
    "br",
    "caption",
    "dd",
    "div",
    "dl",
    "dt",
    "figcaption",
    "figure",
    "footer",
    "h1",
    "h2",
    "h3",
    "h4",
    "h5",
    "h6",
    "header",
    "hr",
    "li",
    "main",
    "nav",
    "ol",
    "p",
    "pre",
    "section",
    "table",
    "td",
    "th",
    "tr",
    "ul",
)

/** Tags whose content is never rendered as reader text. */
val READER_SEARCH_SKIPPED_TAGS: Set<String> = setOf("script", "style", "noscript", "template")

/**
 * Context kept on each side of a match to re-find it in a different text
 * space. Wide enough to be distinctive, narrow enough to survive a nearby
 * divergence (a math placeholder, a dropped hidden node).
 */
const val READER_SEARCH_QUOTE_CONTEXT_CHARS = 24

private val readerSearchWhitespaceRun = Regex("\\s+")

/**
 * Case-insensitive substring matches whose first character is at the beginning
 * of a word. The end of the query does not need to be a word boundary.
 */
fun readerWordStartMatchOffsets(text: CharSequence, query: String): List<Int> =
    readerWordStartMatchOffsets(
        text = text,
        query = query,
        fromIndex = 0,
        endIndex = text.length,
        charBeforeText = null,
    )

/**
 * Incremental form used by windowed scanners: only matches that *start* before
 * [endIndex] are returned, so a caller can scan a bounded buffer and keep the
 * tail as overlap. [charBeforeText] is the character immediately before
 * [fromIndex] in the full text (null at the start of the text); it decides
 * whether a match at [fromIndex] starts a word.
 */
fun readerWordStartMatchOffsets(
    text: CharSequence,
    query: String,
    fromIndex: Int,
    endIndex: Int,
    charBeforeText: Char?,
): List<Int> {
    val needle = query.trim()
    val scanStart = fromIndex.coerceAtLeast(0)
    val scanEnd = endIndex.coerceAtMost(text.length)
    if (needle.isEmpty() || scanEnd <= scanStart) return emptyList()

    // A match may extend past the scan end but must start before it.
    val lastStart = minOf(text.length - needle.length, scanEnd - 1)
    if (lastStart < scanStart) return emptyList()

    return buildList {
        var index = scanStart
        while (index <= lastStart) {
            val found = text.indexOf(needle, index, ignoreCase = true)
            if (found < 0 || found > lastStart) break
            val previous = if (found > 0) text[found - 1] else charBeforeText
            if (previous == null || !previous.isLetterOrDigit()) add(found)
            index = found + 1
        }
    }
}

/**
 * The text around one match, whitespace-collapsed: the hit's fingerprint across
 * text spaces. Quotes are compared with all whitespace removed (see
 * [readerSearchCompactText]), so a quote survives re-wrapping and indentation.
 */
fun readerSearchMatchQuote(
    text: CharSequence,
    matchStart: Int,
    matchLength: Int,
    contextChars: Int = READER_SEARCH_QUOTE_CONTEXT_CHARS,
): String {
    if (matchStart < 0 || matchLength <= 0 || matchStart >= text.length) return ""
    val start = (matchStart - contextChars).coerceAtLeast(0)
    val end = (matchStart + matchLength + contextChars).coerceAtMost(text.length)
    if (end <= start) return ""
    return text.substring(start, end)
        .replace(readerSearchWhitespaceRun, " ")
        .trim()
}

/**
 * Lowercased text with every whitespace character removed: the comparison space
 * quotes are matched in. Both text spaces disagree about whitespace (markup
 * indentation vs. paginated runs) far more often than about letters.
 */
fun readerSearchCompactText(text: CharSequence): String = buildString {
    for (char in text) {
        if (!char.isWhitespace()) append(char.lowercaseChar())
    }
}

/** Length of the shared tail of [a] and [b]. */
fun readerSearchCommonSuffixLength(a: CharSequence, b: CharSequence): Int {
    var length = 0
    while (length < a.length && length < b.length &&
        a[a.length - 1 - length] == b[b.length - 1 - length]
    ) {
        length++
    }
    return length
}

/** Length of the shared head of [a] and [b]. */
fun readerSearchCommonPrefixLength(a: CharSequence, b: CharSequence): Int {
    var length = 0
    while (length < a.length && length < b.length && a[length] == b[length]) length++
    return length
}
