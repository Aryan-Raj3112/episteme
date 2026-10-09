package com.aryan.reader.shared

import kotlin.math.abs

/**
 * The identity of a TTS chunk needed to re-anchor a saved listening position onto a freshly
 * paginated chunk list. Deliberately narrower than the platform chunk types: matching only ever
 * needs the CFI, the offset inside that CFI's source, and the text.
 */
data class SharedTtsChunkRef(
    val sourceCfi: String,
    val startOffsetInSource: Int,
    val text: String
)

private val TTS_WHITESPACE = Regex("\\s+")

/**
 * True when two CFIs address the same content. Chunks can be sliced at different DOM depths
 * across re-pagination (native reader vs paginated), so a shorter path that prefixes a longer one
 * counts as the same source.
 */
fun sameTtsChunkSource(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return first == second
    val firstPath = cfiPath(first)
    val secondPath = cfiPath(second)
    return firstPath == secondPath || cfiPathContains(firstPath, secondPath) || cfiPathContains(secondPath, firstPath)
}

/**
 * Best-effort re-anchor of [target] onto [chunks], from most to least precise. Returns null when
 * nothing in the list is plausibly the same chunk.
 */
fun findTtsChunkStartIndex(
    chunks: List<SharedTtsChunkRef>,
    target: SharedTtsChunkRef?
): Int? {
    if (target == null) return null

    val exactIndex = chunks.indexOfFirst {
        sameTtsChunkSource(it.sourceCfi, target.sourceCfi) &&
            it.startOffsetInSource == target.startOffsetInSource &&
            normalizedTtsText(it.text) == normalizedTtsText(target.text)
    }
    if (exactIndex >= 0) return exactIndex

    val sourceAndOffsetIndex = chunks.indexOfFirst {
        sameTtsChunkSource(it.sourceCfi, target.sourceCfi) &&
            target.startOffsetInSource >= it.startOffsetInSource &&
            target.startOffsetInSource < it.startOffsetInSource + it.text.length
    }
    if (sourceAndOffsetIndex >= 0) return sourceAndOffsetIndex

    val sourceAndTextIndex = chunks.indexOfFirst {
        sameTtsChunkSource(it.sourceCfi, target.sourceCfi) &&
            ttsTextMatches(it.text, target.text)
    }
    if (sourceAndTextIndex >= 0) return sourceAndTextIndex

    val sourceNearestOffsetIndex = chunks
        .mapIndexedNotNull { index, chunk ->
            if (sameTtsChunkSource(chunk.sourceCfi, target.sourceCfi)) {
                index to abs(chunk.startOffsetInSource - target.startOffsetInSource)
            } else {
                null
            }
        }
        .minByOrNull { it.second }
        ?.first
    if (sourceNearestOffsetIndex != null) return sourceNearestOffsetIndex

    return findUniqueTextMatch(chunks, target.text)
}

fun findTtsChunkResumeIndex(
    chunks: List<SharedTtsChunkRef>,
    sourceCfi: String?,
    startOffsetInSource: Int,
    currentText: String?,
    currentChunkIndexFallback: Int
): Int? {
    val target = sourceCfi
        ?.takeIf { it.isNotBlank() }
        ?.let {
            SharedTtsChunkRef(
                sourceCfi = it,
                startOffsetInSource = startOffsetInSource.coerceAtLeast(0),
                text = currentText.orEmpty()
            )
        }

    val matchedIndex = findTtsChunkStartIndex(chunks, target)
        ?: currentText?.let { findUniqueTextMatch(chunks, it) }
    if (matchedIndex != null) return matchedIndex

    return currentChunkIndexFallback.takeIf { it in chunks.indices }
}

/**
 * Where a chapter-scoped continuation should begin. Unlike a plain resume this advances past the
 * matched chunk, because the matched chunk is the last one the previous session consumed.
 */
fun resolveTtsContinuationStartIndex(
    chunks: List<SharedTtsChunkRef>,
    loadedChunkCount: Int,
    sourceCfi: String?,
    startOffsetInSource: Int,
    currentText: String?
): Int? {
    val matchedResumeIndex = findTtsChunkResumeIndex(
        chunks = chunks,
        sourceCfi = sourceCfi,
        startOffsetInSource = startOffsetInSource,
        currentText = currentText,
        currentChunkIndexFallback = -1
    )

    if (matchedResumeIndex != null) {
        // A match at the final chunk means this chapter is consumed. Returning
        // null lets the caller advance, rather than falling back to zero.
        return (matchedResumeIndex + 1).takeIf { it in chunks.indices }
    }

    // Zero cannot be a continuation point after a finished TTS session. It is
    // commonly the stale/default count that previously restarted a chapter.
    return loadedChunkCount.takeIf { it > 0 && it in chunks.indices }
}

private fun cfiPath(cfi: String): String = cfi.split(':').first()

private fun cfiPathContains(parentPath: String, childPath: String): Boolean {
    if (parentPath.isBlank() || childPath.isBlank() || parentPath == childPath) return false
    val parentParts = parentPath.split('/').filter { it.isNotEmpty() }
    val childParts = childPath.split('/').filter { it.isNotEmpty() }
    return parentParts.size < childParts.size && childParts.take(parentParts.size) == parentParts
}

private fun normalizedTtsText(text: String): String =
    text.replace(TTS_WHITESPACE, " ").trim()

private fun ttsTextMatches(first: String, second: String): Boolean {
    val firstNormalized = normalizedTtsText(first)
    val secondNormalized = normalizedTtsText(second)
    if (firstNormalized.isBlank() || secondNormalized.isBlank()) return false
    return firstNormalized == secondNormalized ||
        firstNormalized.startsWith(secondNormalized) ||
        secondNormalized.startsWith(firstNormalized)
}

private fun findUniqueTextMatch(chunks: List<SharedTtsChunkRef>, text: String): Int? {
    val matches = chunks.mapIndexedNotNull { index, chunk ->
        index.takeIf { ttsTextMatches(chunk.text, text) }
    }
    return matches.singleOrNull()
}
