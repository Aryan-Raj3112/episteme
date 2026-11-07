package com.aryan.reader.shared.reader

import kotlinx.serialization.json.JsonPrimitive

/**
 * Pushes the reader search query into the live WebView document for
 * highlight-all painting (`window.readerApplySearchHighlights`, defined in
 * [readerHtmlAnnotationScript]).
 *
 * The payload is always emitted — even for a blank query — so clearing the
 * search clears the marks. Short queries (< 2 chars) clear without
 * painting, matching Android's `highlightAllOccurrences` threshold. The
 * host evaluates this through the reload-safe highlights channel, so
 * chapter loads and fresh virtual chunks repaint from the document-side
 * snapshot without further Compose traffic.
 */
fun sharedEpubSearchHighlightsApplyScript(query: String, darkMode: Boolean): String {
    val payload = buildString {
        append("{\"query\":").append(JsonPrimitive(query))
        append(",\"dark\":").append(if (darkMode) "true" else "false")
        append('}')
    }
    return "if (window.readerApplySearchHighlights) window.readerApplySearchHighlights($payload);"
}
