package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.CfiUtils
import com.aryan.reader.paginatedreader.TtsChunk
import kotlin.math.abs

private val TTS_WHITESPACE = Regex("\\s+")

internal fun sameTtsChunkSource(first: String, second: String): Boolean {
    if (first.isBlank() || second.isBlank()) return first == second
    val firstPath = CfiUtils.getPath(first)
    val secondPath = CfiUtils.getPath(second)
    return firstPath == secondPath || cfiPathContains(firstPath, secondPath) || cfiPathContains(secondPath, firstPath)
}

internal fun findTtsChunkStartIndex(
    chunks: List<TtsChunk>,
    target: TtsChunk?
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

internal fun findTtsChunkResumeIndex(
    chunks: List<TtsChunk>,
    sourceCfi: String?,
    startOffsetInSource: Int,
    currentText: String?,
    currentChunkIndexFallback: Int
): Int? {
    val target = sourceCfi
        ?.takeIf { it.isNotBlank() }
        ?.let {
            TtsChunk(
                text = currentText.orEmpty(),
                sourceCfi = it,
                startOffsetInSource = startOffsetInSource.coerceAtLeast(0)
            )
        }

    val matchedIndex = findTtsChunkStartIndex(chunks, target)
        ?: currentText?.let { findUniqueTextMatch(chunks, it) }
    if (matchedIndex != null) return matchedIndex

    return currentChunkIndexFallback.takeIf { it in chunks.indices }
}

internal fun resolveTtsContinuationStartIndex(
    chunks: List<TtsChunk>,
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

private fun findUniqueTextMatch(chunks: List<TtsChunk>, text: String): Int? {
    val matches = chunks.mapIndexedNotNull { index, chunk ->
        index.takeIf { ttsTextMatches(chunk.text, text) }
    }
    return matches.singleOrNull()
}
