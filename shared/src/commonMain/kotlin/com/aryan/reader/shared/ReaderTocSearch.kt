package com.aryan.reader.shared

data class ReaderTocSearchItem<T>(
    val originalIndex: Int,
    val entry: T,
    val matchesQuery: Boolean
)

fun <T> filterReaderTocEntries(
    entries: List<T>,
    query: String,
    labelOf: (T) -> String,
    depthOf: (T) -> Int
): List<ReaderTocSearchItem<T>> {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isBlank()) {
        return entries.mapIndexed { index, entry ->
            ReaderTocSearchItem(originalIndex = index, entry = entry, matchesQuery = true)
        }
    }

    val includedIndices = linkedSetOf<Int>()
    val matchingIndices = mutableSetOf<Int>()
    entries.forEachIndexed { index, entry ->
        if (labelOf(entry).contains(normalizedQuery, ignoreCase = true)) {
            matchingIndices += index
            includedIndices += ancestorIndicesForTocEntry(entries, index, depthOf)
            includedIndices += index
        }
    }

    return includedIndices.sorted().map { index ->
        ReaderTocSearchItem(
            originalIndex = index,
            entry = entries[index],
            matchesQuery = index in matchingIndices
        )
    }
}

private fun <T> ancestorIndicesForTocEntry(
    entries: List<T>,
    originalIndex: Int,
    depthOf: (T) -> Int
): Set<Int> {
    val targetDepth = entries.getOrNull(originalIndex)?.let(depthOf) ?: return emptySet()
    val ancestors = mutableSetOf<Int>()
    var currentDepth = targetDepth
    for (index in originalIndex downTo 0) {
        val entryDepth = depthOf(entries[index])
        if (entryDepth < currentDepth) {
            ancestors += index
            currentDepth = entryDepth
        }
        if (currentDepth == 0) break
    }
    return ancestors
}
