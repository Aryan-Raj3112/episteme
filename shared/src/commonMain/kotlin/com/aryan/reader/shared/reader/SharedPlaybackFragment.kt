package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * A contiguous stretch of text that some playback engine currently owns: the sentence TTS is
 * speaking, or the fragment an EPUB media overlay is narrating.
 *
 * This type exists so read-aloud and media overlays anchor the same way. Before it, Android's
 * painter took an `epubreader.TtsHighlightInfo(text, cfi, offset)` — a type named for one feature,
 * shaped for one feature, living in the Android-only package — and every paint site re-derived the
 * same block intersection from it. A second playback feature would have meant a second parameter and
 * a second copy of that intersection, in a `when` nobody would remember to extend.
 *
 * **Coordinates are absolute within the chapter**, matching `SemanticBlock.startCharOffsetInSource`
 * and Android's `TtsState.startOffsetInSource`. They are *not* block-local: a chunk routinely spans
 * a block boundary, and each block that contains part of it has to intersect against its own span.
 * Use [sharedPlaybackFragmentRangeInBlock] to get the block-local range to paint.
 *
 * [blockCfi] is the block identity, matched exactly as Android matched it — see
 * [sharedPlaybackFragmentRangeInBlock] for why a null never matches.
 */
data class SharedPlaybackFragment(
    val blockCfi: String?,
    val startAbs: Int,
    val endAbs: Int,
) {
    /** The chunk's own length, before it is clipped to any one block. */
    val length: Int get() = (endAbs - startAbs).coerceAtLeast(0)

    /** True when the fragment covers no text at all and must not be painted. */
    val isEmpty: Boolean get() = endAbs <= startAbs

    companion object {
        /**
         * Builds a fragment from a start offset and a text length, the shape Android's TTS state
         * carries (`startOffsetInSource` + the chunk text). An empty or negative length yields an
         * empty fragment rather than a backwards range.
         */
        fun ofLength(blockCfi: String?, startAbs: Int, length: Int): SharedPlaybackFragment =
            SharedPlaybackFragment(
                blockCfi = blockCfi,
                startAbs = startAbs,
                endAbs = startAbs + length.coerceAtLeast(0)
            )
    }
}

/**
 * The part of [fragment] that falls inside one block, in **block-local** offsets, or null when this
 * block owns none of it.
 *
 * This is the single source for the block intersection, replacing six verbatim copies of it in
 * Android's paint paths (`PaginatedReaderContent` x4, `PaginatedReaderSelection`, and
 * `VerticalPageContent`'s rect painter) plus the cfi guard each copy had to repeat.
 *
 * The rules, which are Android's and are deliberately not "improved" here:
 *
 * - **A null [blockCfi] never matches**, on either side. Android compared `block.cfi ==
 *   ttsHighlightInfo.cfi` where the fragment's cfi was a non-null `String`, so a block with no cfi
 *   could never be highlighted. Treating two nulls as equal would paint every cfi-less block in the
 *   chapter with the live chunk.
 * - **Only the intersection is painted.** A chunk that starts in one paragraph and ends in the next
 *   highlights the tail of the first and the head of the second, which is what makes sentence-level
 *   TTS look continuous.
 * - **Touching is not overlapping.** A zero-length intersection paints nothing, so a chunk that
 *   ends exactly at a block boundary does not bleed a sliver onto the next block.
 *
 * @param blockCfi the block's own cfi, or null when it has none.
 * @param blockStartAbs the block's `startCharOffsetInSource`.
 * @param blockLength the block's text length.
 * @param fragment the fragment to resolve, or null when nothing is playing.
 * @return a half-open `[start, end)` block-local range, or null when this block owns none of it.
 */
fun sharedPlaybackFragmentRangeInBlock(
    fragment: SharedPlaybackFragment?,
    blockCfi: String?,
    blockStartAbs: Int,
    blockLength: Int
): IntRange? {
    if (fragment == null || fragment.isEmpty) return null
    val cfi = fragment.blockCfi
    if (cfi.isNullOrEmpty() || blockCfi.isNullOrEmpty() || cfi != blockCfi) return null

    val blockEndAbs = blockStartAbs + blockLength.coerceAtLeast(0)
    val startAbs = maxOf(blockStartAbs, fragment.startAbs)
    val endAbs = minOf(blockEndAbs, fragment.endAbs)
    if (startAbs >= endAbs) return null
    return (startAbs - blockStartAbs) until (endAbs - blockStartAbs)
}

/**
 * Whether [blockCfi] is the block this fragment is anchored to.
 *
 * Kept separate from the range math because the painters need the cheap check on every block in a
 * page, and most blocks do not match. Same null rules as [sharedPlaybackFragmentRangeInBlock]: a
 * fragment with no cfi, or a block with no cfi, never matches.
 */
fun SharedPlaybackFragment.matchesBlock(blockCfi: String?): Boolean {
    val cfi = blockCfi
    return !cfi.isNullOrEmpty() && cfi == this.blockCfi && !this.blockCfi.isNullOrEmpty()
}

/**
 * Paints [fragment]'s share of one block onto [this], returning it unchanged when the fragment does
 * not touch the block.
 *
 * Paint-only by construction: it adds a background `SpanStyle` and no string annotation, so the
 * fragment can never be tapped, selected, recoloured or deleted. That is the same contract the
 * transient playback band has on the highlight path (`UserHighlight.isTransientPlaybackBand`), and it
 * is why this is a separate function rather than a `UserHighlight` — a read-aloud band is not a
 * highlight the reader made.
 *
 * Insertion order matters and is preserved: pass the already search-highlighted string so the
 * playback band is added after the search fill, matching what every call site did inline.
 */
fun AnnotatedString.withPlaybackFragmentBackground(
    fragment: SharedPlaybackFragment?,
    blockCfi: String?,
    blockStartAbs: Int,
    blockLength: Int,
    color: Color
): AnnotatedString {
    val range = sharedPlaybackFragmentRangeInBlock(fragment, blockCfi, blockStartAbs, blockLength)
        ?: return this
    return buildAnnotatedString {
        append(this@withPlaybackFragmentBackground)
        addStyle(
            style = SpanStyle(background = color),
            start = range.first,
            end = range.last + 1
        )
    }
}