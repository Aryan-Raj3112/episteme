package com.aryan.reader.epubreader

import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.shared.SharedTtsChunkRef
import com.aryan.reader.shared.findTtsChunkResumeIndex as sharedFindTtsChunkResumeIndex
import com.aryan.reader.shared.findTtsChunkStartIndex as sharedFindTtsChunkStartIndex
import com.aryan.reader.shared.resolveTtsContinuationStartIndex as sharedResolveTtsContinuationStartIndex

private fun TtsChunk.toRef() = SharedTtsChunkRef(
    sourceCfi = sourceCfi,
    startOffsetInSource = startOffsetInSource,
    text = text
)

private fun List<TtsChunk>.toRefs() = map { it.toRef() }

internal fun findTtsChunkStartIndex(
    chunks: List<TtsChunk>,
    target: TtsChunk?
): Int? = sharedFindTtsChunkStartIndex(chunks.toRefs(), target?.toRef())

internal fun findTtsChunkResumeIndex(
    chunks: List<TtsChunk>,
    sourceCfi: String?,
    startOffsetInSource: Int,
    currentText: String?,
    currentChunkIndexFallback: Int
): Int? = sharedFindTtsChunkResumeIndex(
    chunks = chunks.toRefs(),
    sourceCfi = sourceCfi,
    startOffsetInSource = startOffsetInSource,
    currentText = currentText,
    currentChunkIndexFallback = currentChunkIndexFallback
)

internal fun resolveTtsContinuationStartIndex(
    chunks: List<TtsChunk>,
    loadedChunkCount: Int,
    sourceCfi: String?,
    startOffsetInSource: Int,
    currentText: String?
): Int? = sharedResolveTtsContinuationStartIndex(
    chunks = chunks.toRefs(),
    loadedChunkCount = loadedChunkCount,
    sourceCfi = sourceCfi,
    startOffsetInSource = startOffsetInSource,
    currentText = currentText
)
