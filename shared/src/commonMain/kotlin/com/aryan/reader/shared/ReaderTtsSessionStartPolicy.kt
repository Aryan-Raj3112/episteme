package com.aryan.reader.shared

/**
 * Chunk planning for a read-aloud session that starts mid-chapter.
 *
 * Starting inside a chunk slices the remainder out of the planned chunk
 * ([ReaderTtsPlanner.chunksFromCurrentLocation] /
 * [ReaderTtsPlanner.chunksForChapterFromLocation]). When the session anchor
 * sits near the end of a chunk that remainder is a handful of characters, and
 * a per-chunk engine then spends a whole synthesis round-trip (plus an audio
 * handoff and a navigation follow) on it: the iOS log for a session that
 * started two characters from a chunk boundary showed a 0.75s first part
 * followed by the real text.
 *
 * Android's paginated path never produces that shape — it starts at the first
 * chunk of the current page, so the head is always a full chunk. Rather than
 * dropping the characters (losing text) or keeping a wasted request, a tiny
 * head is folded into the chunk that follows it.
 */

/**
 * Largest leading remainder still treated as "too small to speak alone".
 *
 * Planned chunks target [READER_TTS_CHUNK_MAX_LENGTH] (250) characters, so a
 * head under this is roughly a fifth of a normal chunk: far too little audio to
 * pay for its own request and transition.
 */
const val READER_TTS_TINY_HEAD_CHUNK_MAX_CHARS = 48

/**
 * Folds a too-small first chunk into the chunk that follows it.
 *
 * The merge only happens when the two chunks are contiguous slices of the same
 * source block (same CFI path, adjacent offsets). That keeps the merged chunk's
 * highlight range and `toLocator()` mapping exact, and it is the shape the
 * planner produces when the anchor lands inside one semantic block. A head that
 * ends a block has no contiguous successor, so it is left alone rather than
 * merged across a CFI boundary where progress highlighting would break.
 *
 * Returns the list unchanged when there is nothing safe to fold.
 */
fun List<ReaderTtsChunk>.mergeTinyLeadingChunk(
    maxHeadChars: Int = READER_TTS_TINY_HEAD_CHUNK_MAX_CHARS,
): List<ReaderTtsChunk> {
    val head = firstOrNull() ?: return this
    val next = getOrNull(1) ?: return this
    if (head.text.length > maxHeadChars) return this
    if (!head.canMergeWith(next)) return this
    val merged = head.copy(
        text = joinTtsChunkText(head.text, next.text),
        endOffset = maxOf(head.endOffset, next.endOffset),
        spokenText = joinTtsChunkText(head.spokenText, next.spokenText),
    )
    return listOf(merged) + drop(2)
}

/**
 * True when [next] continues this chunk's text in the same source block, so the
 * two can be spoken as one without breaking offset- or CFI-based highlighting.
 */
private fun ReaderTtsChunk.canMergeWith(next: ReaderTtsChunk): Boolean {
    if (next.chapterIndex != chapterIndex) return false
    if (endOffset != next.startOffset) return false
    return sourceCfi != null && readerTtsChunksShareSource(sourceCfi, next.sourceCfi)
}

private fun joinTtsChunkText(first: String, second: String): String {
    val head = first.trimEnd()
    val tail = second.trimStart()
    if (head.isEmpty()) return tail
    if (tail.isEmpty()) return head
    return "$head $tail"
}

/** Same-source check shared with the planner's chunk matching. */
private fun readerTtsChunksShareSource(first: String?, second: String?): Boolean {
    val firstSource = first.orEmpty()
    val secondSource = second.orEmpty()
    if (firstSource.isBlank() || secondSource.isBlank()) return firstSource == secondSource
    if (firstSource == secondSource) return true
    val firstPath = firstSource.substringBefore(':')
    val secondPath = secondSource.substringBefore(':')
    return firstPath == secondPath
}
