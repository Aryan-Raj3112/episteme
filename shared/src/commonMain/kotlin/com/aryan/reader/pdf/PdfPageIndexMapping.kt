package com.aryan.reader.pdf

sealed interface PdfPageIdentity {
    data class Pdf(val pdfIndex: Int) : PdfPageIdentity
    data class Blank(val id: String) : PdfPageIdentity
}

fun buildSharedPdfPageIndexMapping(
    currentLayout: List<PdfPageIdentity>,
    updatedLayout: List<PdfPageIdentity>,
    sourcePageIndices: Iterable<Int>,
): Map<Int, Int> {
    val sources = sourcePageIndices.toSet()
    if (sources.isEmpty()) return emptyMap()
    val minimumCurrentPageCount = maxOf(currentLayout.size, sources.maxOrNull()?.plus(1) ?: 0)
    val effectiveCurrent = if (currentLayout.size >= minimumCurrentPageCount) currentLayout else {
        currentLayout + (currentLayout.size until minimumCurrentPageCount).map(PdfPageIdentity::Pdf)
    }
    val currentTokens = effectiveCurrent.toOccurrenceTokens()
    val updatedTokenIndices = updatedLayout.toOccurrenceTokens().mapIndexed { index, token -> token to index }.toMap()
    return sources.mapNotNull { sourceIndex ->
        val token = currentTokens.getOrNull(sourceIndex) ?: return@mapNotNull null
        val targetIndex = updatedTokenIndices[token] ?: return@mapNotNull null
        sourceIndex to targetIndex
    }.toMap()
}

private fun List<PdfPageIdentity>.toOccurrenceTokens(): List<PdfPageOccurrenceToken> {
    val seen = mutableMapOf<PdfPageIdentity, Int>()
    return map { identity ->
        val occurrence = seen[identity] ?: 0
        seen[identity] = occurrence + 1
        PdfPageOccurrenceToken(identity, occurrence)
    }
}

private data class PdfPageOccurrenceToken(val identity: PdfPageIdentity, val occurrence: Int)
