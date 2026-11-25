package com.aryan.reader.shared

import androidx.compose.ui.text.AnnotatedString

data class SearchResult(
    val locationInSource: Int,
    val locationTitle: String,
    val snippet: AnnotatedString,
    val query: String,
    val occurrenceIndexInLocation: Int,
    val chunkIndex: Int,
    /**
     * Whitespace-collapsed context around the match.
     *
     * The ordinal alone is only trustworthy while every consumer flattens the
     * chapter identically. The quote lets navigation re-find this exact
     * occurrence in a text space that drifted (math placeholders, dropped
     * hidden nodes, book replacements) instead of paging to a neighbour.
     * Blank when the producer has no context to offer.
     */
    val matchQuote: String = "",
)

/**
 * What a jump-history entry should remember about this hit. The match quote is
 * the better fingerprint — it is what navigation re-finds a position by — so it
 * wins over the longer results-list snippet.
 */
fun SearchResult.recordedJumpQuote(): String = matchQuote.ifBlank { snippet.text }

data class AiDefinitionResult(
    val definition: String? = null,
    val error: String? = null
)

data class SummarizationResult(
    val summary: String? = null,
    val error: String? = null,
    val cost: Double? = null,
    val freeRemaining: Int? = null,
    val isCacheHit: Boolean = false
)

data class RecapResult(
    val recap: String? = null,
    val error: String? = null,
    val cost: Double? = null,
    val freeRemaining: Int? = null
)
