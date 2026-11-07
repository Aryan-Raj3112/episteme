package com.aryan.reader.shared

import com.aryan.reader.shared.reader.SharedMediaOverlayClip
import com.aryan.reader.shared.reader.SharedSemanticDomElement
import com.aryan.reader.shared.reader.SharedSemanticHtmlDocument

/**
 * Which transcript chunks one narration clip covers, as absolute chunk indices.
 *
 * Narration is clip-sequenced (one SMIL `par` per line) while the transcript is
 * chunk-sequenced (the sentence-grouped text TTS would speak). A clip that sits
 * inside one chunk has `startChunk == endChunk`; a long clip spanning several
 * chunks (one `par` holding a whole sentence that TTS hard-splits) covers the
 * range, and the highlight interpolates across it with the clip's progress.
 */
data class SharedOverlayClipChunkSpan(
    val startChunk: Int,
    val endChunk: Int,
)

/** How many characters of a clip anchor the ordered text search. See below. */
private const val OVERLAY_ANCHOR_PROBE_LENGTH = 48

/**
 * Anchors every playable clip to the transcript chunks holding its words.
 *
 * This is the Listen equivalent of the reader's per-line highlight: the
 * reader resolves each clip to its text element and paints that fragment, so
 * its highlight is exact. Listen reuses the same anchors — each clip's
 * `text/@src` element looked up in the chapter XHTML — but maps them onto the
 * transcript's chunk rows instead of painting bands, so the highlight follows
 * the exact line being narrated rather than a fractional estimate.
 *
 * Alignment is an ordered text search, not offsets: the clips in playback
 * order speak the chapter's text in order, and the chunks hold the same text
 * in order, so each clip's opening words are searched from where the previous
 * clip matched. That stays correct when a chapter reflows (spine splits, TOC
 * sections) and when the two sides normalize whitespace differently, both of
 * which defeat offset arithmetic. Anything that cannot be anchored — an
 * audio-only `par` with no text, an element the parse cannot find, a repeated
 * line whose occurrences are exhausted — inherits the previous clip's span
 * rather than jumping, so the highlight holds instead of lying.
 *
 * @param clips playable clips in playback order (a plan's entries).
 * @param readHtml archive path -> chapter XHTML, or null when unreadable.
 * @param chunkTexts transcript chunk texts in chapter order (the rows shown).
 * @return one span per clip, in playback order. Empty when there is nothing
 *   to anchor against; callers fall back to the fractional estimate then.
 */
fun sharedOverlayClipChunkSpans(
    clips: List<SharedMediaOverlayClip>,
    readHtml: (String) -> String?,
    chunkTexts: List<String>,
): List<SharedOverlayClipChunkSpan> {
    if (clips.isEmpty() || chunkTexts.isEmpty()) return emptyList()
    val normChunks = chunkTexts.map(::normalizeOverlayTranscriptText)
    // Cumulative chunk starts over the joined text, so a character offset
    // resolves to its chunk with one binary search.
    val starts = IntArray(normChunks.size)
    val joined = StringBuilder()
    normChunks.forEachIndexed { index, chunk ->
        starts[index] = joined.length
        if (index > 0) joined.append(' ')
        joined.append(chunk)
    }
    val body = joined.toString()
    fun chunkAt(offset: Int): Int {
        var low = 0
        var high = starts.size - 1
        var answer = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= offset) {
                answer = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return answer
    }
    // One DOM parse and id index per text document; a chapter is usually one.
    val documents = mutableMapOf<String, Map<String, String>?>()
    fun elementText(path: String, id: String): String {
        if (id.isBlank()) return ""
        val index = documents.getOrPut(path) { indexOverlayTranscriptElements(readHtml(path)) }
            ?: return ""
        return index[id] ?: ""
    }
    val spans = ArrayList<SharedOverlayClipChunkSpan>(clips.size)
    var previous = SharedOverlayClipChunkSpan(0, 0)
    var searchFrom = 0
    var anchoredAny = false
    for (clip in clips) {
        val words = normalizeOverlayTranscriptText(elementText(clip.textHref, clip.elementId ?: ""))
        if (words.isBlank()) {
            // Audio-only interlude (a page-break sound, a pause): it belongs
            // to the surrounding line, so the highlight holds.
            spans += previous
            continue
        }
        val head = words.take(OVERLAY_ANCHOR_PROBE_LENGTH)
        val tail = words.takeLast(OVERLAY_ANCHOR_PROBE_LENGTH)
        val headAt = body.indexOf(head, searchFrom).takeIf { it >= 0 }
        if (headAt == null) {
            spans += previous
            continue
        }
        val tailAt = body.indexOf(tail, headAt).takeIf { it >= 0 } ?: headAt
        val span = SharedOverlayClipChunkSpan(
            startChunk = chunkAt(headAt),
            endChunk = chunkAt((tailAt + tail.length - 1).coerceAtMost(body.lastIndex)),
        )
        spans += span
        previous = span
        searchFrom = headAt + head.length
        anchoredAny = true
    }
    // Nothing anchored at all (unreadable chapter, unparseable markup): leave
    // the highlight to the fractional estimate rather than pinning it to the
    // top of the chapter for the whole narration.
    if (!anchoredAny) return emptyList()
    return spans
}

/**
 * Transcript highlight for an anchored clip.
 *
 * At the clip boundary the highlight is the span's first chunk; at its end,
 * the last. In between it travels with the voice, which is what keeps a long
 * clip spanning several chunks from pinning the highlight to its first line
 * while the narration runs ahead.
 */
fun sharedSpanTranscriptChunkIndex(
    span: SharedOverlayClipChunkSpan,
    clipFraction: Float,
    chunkCount: Int,
): Int {
    if (chunkCount <= 0) return -1
    val start = minOf(span.startChunk, span.endChunk).coerceIn(0, chunkCount - 1)
    val end = maxOf(span.startChunk, span.endChunk).coerceIn(0, chunkCount - 1)
    val fraction = clipFraction.coerceIn(0f, 1f)
    return (start + fraction * (end - start)).toInt().coerceIn(start, end)
}

/** Collapses whitespace runs to one space and trims, regex-free for Native. */
internal fun normalizeOverlayTranscriptText(value: String): String {
    val out = StringBuilder(value.length)
    var pendingSpace = false
    for (char in value) {
        if (char.isWhitespace()) {
            if (out.isNotEmpty()) pendingSpace = true
        } else {
            if (pendingSpace) {
                out.append(' ')
                pendingSpace = false
            }
            out.append(char)
        }
    }
    return out.toString()
}

/** `id` -> normalized element text for one chapter document, or null. */
private fun indexOverlayTranscriptElements(html: String?): Map<String, String>? {
    if (html.isNullOrBlank()) return null
    val tree = SharedSemanticHtmlDocument.parse(html) ?: return null
    val index = LinkedHashMap<String, String>()
    val stack = ArrayDeque<SharedSemanticDomElement>()
    stack.addLast(tree.root)
    while (stack.isNotEmpty()) {
        val element = stack.removeLast()
        element.elementId()?.takeIf(String::isNotBlank)?.let { id ->
            if (!index.containsKey(id)) {
                index[id] = normalizeOverlayTranscriptText(element.elementTextContent())
            }
        }
        for (i in element.children.size - 1 downTo 0) {
            (element.children[i] as? SharedSemanticDomElement)?.let(stack::addLast)
        }
    }
    return index
}
