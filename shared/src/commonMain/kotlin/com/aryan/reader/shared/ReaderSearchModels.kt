package com.aryan.reader.shared

data class SearchResult(
    val locationInSource: Int,
    val locationTitle: String,
    val snippet: String,
    val query: String,
    val occurrenceIndexInLocation: Int,
    val chunkIndex: Int
)

data class AiDefinitionResult(
    val definition: String? = null,
    val error: String? = null
)

data class SummarizationResult(
    val summary: String? = null,
    val error: String? = null,
    val cost: Double? = null,
    val freeRemaining: Int? = null
)

data class RecapResult(
    val recap: String? = null,
    val error: String? = null,
    val cost: Double? = null,
    val freeRemaining: Int? = null
)
