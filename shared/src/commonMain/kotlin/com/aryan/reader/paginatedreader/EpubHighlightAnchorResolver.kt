package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight

/**
 * Turns a highlight's selected text into a concrete position in a chapter.
 *
 * Why this exists: a highlight created in a WebView surface carries only its text and a DOM
 * position string that means nothing to the paginator, so its locator has no offsets. Rendering
 * then has to guess, and guessing per page is what put the same highlight on every page of a
 * chapter and made multi-paragraph selections vanish entirely. Resolving once, against the whole
 * chapter, picks a single deterministic location and keeps it stable across pagination.
 *
 * The chapter is laid out here in one contiguous character space rather than trusting the offsets
 * each block reports, because those offsets are element-relative: the parser restarts its counter
 * per element, so a real parsed chapter reports the same start for every block. See [of].
 */

/**
 * The result of repairing a set of highlights against one chapter.
 *
 * [repaired] is the count that actually changed. Callers use it to decide whether to persist, since
 * the repair runs on every open and rewriting the stored book each time would be wasteful.
 */
data class RepairedHighlights(
    val highlights: List<UserHighlight>,
    val repaired: Int
) {
    /** True when nothing changed, so there is nothing to write back. */
    val unchanged: Boolean get() = repaired == 0
}

/** How firmly a highlight's position is known. */
enum class HighlightAnchorConfidence {
    /** The stored locator already carried offsets. */
    Resolved,
    /** No offsets were stored; the selected text was located in the chapter. */
    QuoteMatched,
    /** Nothing usable. The highlight cannot be placed and must not be painted. */
    Unresolved
}

/** One block's slice of a highlight, in both absolute-chapter and block-local coordinates. */
data class HighlightBlockSegment(
    val blockIndex: Int,
    val blockCfi: String?,
    val absoluteStart: Int,
    val absoluteEnd: Int,
    val localStart: Int,
    val localEnd: Int
)

/** A highlight's resolved position within one chapter. */
data class ResolvedHighlightAnchor(
    val chapterIndex: Int,
    val startOffset: Int,
    val endOffset: Int,
    val segments: List<HighlightBlockSegment>,
    val confidence: HighlightAnchorConfidence
)

/**
 * A chapter's text laid out in one contiguous character space.
 *
 * Build one per chapter and keep it for the life of the chapter; [locate] is then a plain string
 * search and is safe to call for every quote-only highlight on every repaint.
 */
class EpubChapterTextIndex private constructor(
    private val chapterIndex: Int,
    private val textBlocks: List<SemanticTextBlock>,
    /** Each block's start within the laid-out chapter text. See [of] for why it is computed here. */
    private val blockStarts: IntArray,
    private val origin: Int,
    private val buffer: String,
    /** Index into [textBlocks] for each buffer position, or [NO_BLOCK] for a gap. */
    private val blockOfPosition: IntArray
) {
    /**
     * Locates [quote] in the chapter. Tries an exact match first, then one that differs only in
     * whitespace, then a whitespace-insensitive word-sequence match.
     *
     * Returns the first match in document order. Choosing one and only one is the point: a quote
     * that appears several times in a chapter is ambiguous, and picking all of them is what
     * duplicated the highlight across pages.
     */
    fun locate(quote: String): ResolvedHighlightAnchor? {
        val needle = quote.trim()
        if (needle.isEmpty() || buffer.isEmpty()) return null
        val hits = LinkedHashSet<IntRange>()
        collectExact(needle, hits)
        if (hits.isEmpty()) collectNormalized(needle, hits)
        if (hits.isEmpty()) collectWordSequence(needle, hits)
        val hit = hits.minByOrNull { it.first } ?: return null
        return toAnchor(hit.first, hit.last + 1)
    }

    /**
     * Every place [quote] appears in the chapter, in document order.
     *
     * Callers that must pick exactly one place should use [locate]. This exists for callers that
     * genuinely want all of them, such as reporting how ambiguous a stored highlight is.
     */
    fun locateAll(quote: String): List<ResolvedHighlightAnchor> {
        val needle = quote.trim()
        if (needle.isEmpty() || buffer.isEmpty()) return emptyList()
        val hits = LinkedHashSet<IntRange>()
        collectExact(needle, hits)
        if (hits.isEmpty()) collectNormalized(needle, hits)
        if (hits.isEmpty()) collectWordSequence(needle, hits)
        return hits.sortedBy { it.first }.mapNotNull { hit -> toAnchor(hit.first, hit.last + 1) }
    }

    /**
     * Resolves [quote] to the single anchor a renderer should paint, or null when it cannot be
     * placed.
     *
     * Repeated text is genuinely ambiguous, and a highlight whose position cannot be determined must
     * not be painted somewhere arbitrary. Taking the first match in document order is deterministic
     * and stable, which is what keeps the highlight on one page across repagination and font changes.
     */
    fun resolve(quote: String): HighlightAnchor? = locate(quote)?.toHighlightAnchor()

    /**
     * Fills in the offsets [highlight] is missing, or null when there is nothing to fill in.
     *
     * Two kinds of highlight arrive here. One created in a WebView surface carries only its selected
     * text, because that surface's bridge reports no position. One created by an older build carries
     * offsets computed in a different coordinate space, where every block claimed to start at the same
     * place; those values are worse than absent, because a renderer will trust them. Both are repaired
     * the same way: locate the text once in this chapter and store the offsets it found.
     *
     * Returns null when the highlight already has usable offsets, when it belongs to another chapter,
     * or when its text cannot be placed. All three mean "leave it alone", and a caller persisting the
     * result only when it is non-null will not rewrite a highlight that is already correct.
     */
    fun anchorMissingOffsets(highlight: UserHighlight): UserHighlight? {
        if (highlight.chapterIndex != chapterIndex) return null

        val quote = highlight.locator.textQuote?.takeIf { it.isNotBlank() } ?: highlight.text
        if (quote.isBlank()) return null
        if (storedRangeAgreesWithQuote(highlight.locator, quote)) return null

        val anchor = resolve(quote) ?: return null
        if (anchor.confidence != HighlightAnchorConfidence.QuoteMatched) return null
        val firstSegment = anchor.segments.firstOrNull() ?: return null

        return highlight.copy(
            locator = highlight.locator.copy(
                startOffset = anchor.absoluteStart,
                endOffset = anchor.absoluteEnd,
                blockIndex = firstSegment.blockIndex,
                charOffset = firstSegment.absoluteStart,
                // The block's CFI comes from the resolved segment, not from the old locator: the stored
                // one may name a DOM position from a WebView, which means nothing to a paginated page.
                cfi = firstSegment.blockCfi ?: highlight.locator.cfi
            )
        )
    }

    /**
     * Repairs every highlight in [highlights] that this chapter can place.
     *
     * [repaired] counts how many actually changed, so a caller can skip persisting when nothing did.
     * That matters because persisting rewrites the stored book and the migration runs on every open.
     */
    fun repairHighlights(highlights: List<UserHighlight>): RepairedHighlights {
        val repaired = ArrayList<UserHighlight>(highlights.size)
        var changed = 0
        for (highlight in highlights) {
            val anchored = anchorMissingOffsets(highlight)
            if (anchored == null) {
                repaired += highlight
            } else {
                repaired += anchored
                changed++
            }
        }
        return RepairedHighlights(repaired, changed)
    }

    /**
     * The slice [highlight] occupies in one block, in that block's own coordinates.
     *
     * This is what a text renderer needs, and it is the answer for exactly one block: a highlight
     * spanning three paragraphs yields a segment in each of them, and a highlight whose text appears
     * in several paragraphs yields a segment in only the one it was resolved to. Returning null for
     * every other block is what stops a repeated sentence painting on all of them.
     *
     * Blocks are matched by index, and by CFI when both sides have one, because a caller can be
     * holding a re-styled copy of the block rather than the parsed original.
     */
    fun rangeInBlock(
        highlight: UserHighlight,
        blockIndex: Int?,
        blockCfi: String?
    ): HighlightBlockSegment? {
        val anchor = anchorFor(highlight) ?: return null
        val segment = anchor.segments.firstOrNull { candidate ->
            val indexMatches = blockIndex != null && candidate.blockIndex == blockIndex
            val cfiMatches = blockCfi != null && candidate.blockCfi != null &&
                candidate.blockCfi == blockCfi
            indexMatches || cfiMatches
        } ?: return null
        return segment
    }

    /**
     * Where this highlight is in this chapter, or null when it cannot be placed here.
     *
     * This is the single entry point for placing a highlight, and every surface goes through it. It
     * exists because a highlight's stored locator cannot be trusted as input: it carries no schema
     * marker, so a locator written by an older build — or by a surface whose offsets are relative to
     * one HTML element rather than to the chapter — is structurally indistinguishable from a correct
     * one. Deciding between them is only possible by checking the claim against the chapter's own text,
     * which is what this does.
     *
     * The order is deliberate:
     *
     * 1. Stored offsets that demonstrably cover the highlight's own words are already correct and are
     *    returned as they are, so an anchored highlight does not re-search its chapter on every repaint.
     * 2. Anything else is relocated by searching for the text, which covers a highlight created in a
     *    WebView (no position at all), one carrying offsets from a different coordinate space, and one
     *    left behind by an older build.
     *
     * Null means the chapter cannot place this highlight. A caller must treat that as "paint nothing"
     * rather than "paint somewhere plausible" — the ambiguity is real, and guessing is what put one
     * highlight on every page of a chapter.
 */
fun anchorFor(highlight: UserHighlight): HighlightAnchor? {
        val quote = highlight.locator.textQuote?.takeIf { it.isNotBlank() } ?: highlight.text
        if (quote.isBlank()) return null
        if (storedRangeAgreesWithQuote(highlight.locator, quote)) {
            return storedAnchor(highlight)
        }
        return resolve(quote)?.takeIf { it.confidence == HighlightAnchorConfidence.QuoteMatched }
    }

    /**
     * Turns a locator's validated stored offsets into an anchor, using the chapter layout for the
     * block boundaries. Returns null when the offsets are absent.
     */
    private fun storedAnchor(highlight: UserHighlight): HighlightAnchor? {
        val locator = highlight.locator
        val start = locator.startOffset ?: return null
        val end = locator.endOffset ?: return null
        if (start < 0 || end > buffer.length || end <= start) return null
        val segments = mutableListOf<HighlightBlockSegment>()
        var position = start
        while (position < end) {
            val block = blockOfPosition[position]
            if (block < 0) {
                // A block join carries no owner but is part of the chapter's text, so it ends the run
                // rather than the range. Rejecting the whole range here, as this once did, meant a
                // highlight whose selection ran from one paragraph into the next could never be placed
                // from its own offsets — the same defect [spansUnowned] had.
                if (isJoinSeparator(position)) {
                    position++
                    continue
                }
                // Any other unowned position means the range reaches text that is not in any block, so
                // it cannot be painted as a run. Report nothing rather than a range spanning a gap.
                return null
            }
            var runEnd = position
            while (runEnd < end && blockOfPosition[runEnd] == block) runEnd++
            val blockStart = blockStarts[block]
            segments += HighlightBlockSegment(
                blockIndex = textBlocks[block].blockIndex,
                blockCfi = textBlocks[block].cfi,
                absoluteStart = blockStart + (position - origin - blockStart),
                absoluteEnd = blockStart + (runEnd - origin - blockStart),
                localStart = position - origin - blockStart,
                localEnd = runEnd - origin - blockStart
            )
            position = runEnd
        }
        if (segments.isEmpty()) return null
        return HighlightAnchor(
            chapterIndex = chapterIndex,
            absoluteStart = start,
            absoluteEnd = end,
            segments = segments,
            confidence = HighlightAnchorConfidence.Resolved
        )
    }

    /** Built on first whitespace-insensitive search; a chapter's text does not change while open. */
    private var normalizedView: NormalizedChapterText? = null

    /**
     * Whether [locator]'s stored range already points at [quote] in this chapter's text.
     *
     * This is how the repair decides there is nothing to do, and it validates rather than trusts.
     * Checking that the selected text really is at the stored position is the only way to tell a
     * correct locator from one written by an older build, because both look structurally identical:
     * a pair of integers, no schema marker, no version. Without the check, an old locator would be
     * accepted and its offsets would never be corrected; with it, old data repairs itself the first
     * time the chapter is opened and no migration flag or stored version is needed.
     *
     * The stored range is allowed to be a little longer than the quote, since it may include
     * surrounding whitespace the quote was trimmed of.
     */
    private fun storedRangeAgreesWithQuote(locator: ReaderLocator, quote: String): Boolean {
        val start = locator.startOffset ?: return false
        val end = locator.endOffset ?: return false
        if (start < 0 || end > buffer.length || end <= start) return false
        val needle = collapseReaderWhitespace(quote)
        if (needle.isEmpty()) return false
        val slice = collapseReaderWhitespace(buffer.substring(start, end))
        if (slice.isEmpty()) return false
        return slice == needle || slice.contains(needle)
    }

    private fun collectExact(needle: String, into: MutableSet<IntRange>) {
        var from = 0
        while (from <= buffer.length - needle.length) {
            val index = buffer.indexOf(needle, from)
            if (index < 0) return
            if (index + needle.length <= buffer.length) into += index until (index + needle.length)
            from = index + 1
        }
    }

    private fun collectNormalized(needle: String, into: MutableSet<IntRange>) {
        val view = normalizedView ?: NormalizedChapterText.of(buffer, blockOfPosition)
            .also { normalizedView = it }
        view.locateAll(needle, into)
    }

    private fun collectWordSequence(needle: String, into: MutableSet<IntRange>) {
        val words = splitOnReaderSpaces(needle)
        if (words.isEmpty()) return
        var from = 0
        while (from < buffer.length) {
            val first = buffer.indexOf(words[0], from, ignoreCase = true)
            if (first < 0) return
            var cursor = first + words[0].length
            var matchedAll = true
            for (index in 1 until words.size) {
                while (cursor < buffer.length && isReaderSpace(buffer[cursor])) cursor++
                if (cursor >= buffer.length) {
                    matchedAll = false
                    break
                }
                val word = words[index]
                if (buffer.regionMatches(cursor, word, 0, word.length, ignoreCase = true)) {
                    cursor += word.length
                } else {
                    matchedAll = false
                    break
                }
            }
            if (matchedAll && !spansUnowned(first, cursor)) into += first until cursor
            from = first + 1
        }
    }

    /**
     * True when [position] is the single separator between two blocks rather than a gap in the text.
     *
     * Laying the chapter out inserts one separator character after each block, and those positions are
     * deliberately left unowned. That is right for deciding which block a *character* belongs to, but it
     * made the separator indistinguishable from a bridged gap, so any match crossing a paragraph
     * boundary contained one and was rejected — which is precisely a highlight whose selection ran from
     * one paragraph into the next. The separator is part of the selected text, so a range may contain
     * one; what it may not contain is unowned text.
     */
    private fun isJoinSeparator(position: Int): Boolean {
        if (position <= 0 || position >= blockOfPosition.size - 1) return false
        val before = blockOfPosition[position - 1]
        val after = blockOfPosition[position + 1]
        return before >= 0 && after >= 0 && before != after
    }

    /** True when any position in the range is neither real block text nor a block join. */
    private fun spansUnowned(first: Int, last: Int): Boolean {
        for (position in first..last) {
            if (blockOfPosition[position] >= 0) continue
            if (isJoinSeparator(position)) continue
            return true
        }
        return false
    }

    private fun toAnchor(bufferStart: Int, bufferEnd: Int): ResolvedHighlightAnchor? {
        if (bufferStart < 0 || bufferEnd > buffer.length || bufferStart >= bufferEnd) return null
        val segments = mutableListOf<HighlightBlockSegment>()
        var position = bufferStart
        while (position < bufferEnd) {
            val block = blockOfPosition[position]
            // A bridged inter-block gap carries no owner. It belongs to the segment that precedes
            // it: the character after the gap belongs to this block, so the highlight should cover
            // up to that character's offset and the next segment starts there.
            if (block < 0) {
                position++
                continue
            }
            // A bridged inter-block gap has no owner and ends the run, so the segment stops at this block's
            // text. localStart/localEnd are positions within the block's own text; the absolute
            // range is that offset by the block's start in the chapter.
            var end = position
            while (end < bufferEnd && blockOfPosition[end] == block) end++
            val blockStart = blockStarts[block]
            val localStart = position - origin - blockStart
            val localEnd = end - origin - blockStart
            segments += HighlightBlockSegment(
                blockIndex = textBlocks[block].blockIndex,
                blockCfi = textBlocks[block].cfi,
                absoluteStart = blockStart + localStart,
                absoluteEnd = blockStart + localEnd,
                localStart = localStart,
                localEnd = localEnd
            )
            position = end
        }
        if (segments.isEmpty()) return null
        return ResolvedHighlightAnchor(
            chapterIndex = chapterIndex,
            startOffset = segments.first().absoluteStart,
            endOffset = segments.last().absoluteEnd,
            segments = segments,
            confidence = HighlightAnchorConfidence.QuoteMatched
        )
    }

    private fun bufferOrigin(): Int = origin

    companion object {
        private const val NO_BLOCK = -1

        /** Stands in for the separator between two blocks, and belongs to neither of them. */
        private const val SEPARATOR = ' '

        /**
         * Lays the chapter's text blocks out in one contiguous character space.
         *
         * Offsets come from accumulating block lengths in document order rather than from each block's
         * own `startCharOffsetInSource`. That field is element-relative: parsing restarts its counter
         * for every paragraph, so in a real parsed chapter every block reports the same start and the
         * stored values cannot be used to compare positions across blocks. Laying the text out here is
         * what gives one shared coordinate space for the whole chapter.
         *
         * Returns null when the chapter has no text blocks, in which case no quote can be placed.
         */
        fun of(chapterIndex: Int, blocks: List<SemanticBlock>): EpubChapterTextIndex? {
            val textBlocks = blocks.flatMap { it.flattenToTextBlocks() }
            if (textBlocks.isEmpty()) return null

            // One character of separator per block, matching how paragraphs sit in the chapter's
            // plain text, so a quote spanning two paragraphs still matches across the join.
            val starts = IntArray(textBlocks.size)
            var total = 0
            for (index in textBlocks.indices) {
                starts[index] = total
                total += textBlocks[index].text.length + 1
            }
            if (total <= 0) return null

            val chars = CharArray(total) { SEPARATOR }
            val owner = IntArray(total) { NO_BLOCK }
            for ((index, block) in textBlocks.withIndex()) {
                val start = starts[index]
                block.text.toCharArray().copyInto(chars, start)
                for (position in start until start + block.text.length) owner[position] = index
            }
            return EpubChapterTextIndex(
                chapterIndex = chapterIndex,
                textBlocks = textBlocks,
                blockStarts = starts,
                origin = 0,
                buffer = chars.concatToString(),
                blockOfPosition = owner
            )
        }
    }
}

/**
 * Whitespace-insensitive view of the chapter buffer.
 *
 * Collapsing runs of whitespace means a quote typed with different wrapping still matches, while
 * the position map keeps the result convertible back to absolute chapter offsets.
 */
private class NormalizedChapterText private constructor(
    private val text: String,
    private val positions: IntArray,
    private val blocks: IntArray
) {
    fun locate(needle: String): IntRange? {
        val collapsed = collapseReaderWhitespace(needle).trimEnd(' ')
        if (collapsed.isEmpty()) return null
        var from = 0
        while (from <= text.length - collapsed.length) {
            val index = text.indexOf(collapsed, from)
            if (index < 0) return null
            val last = index + collapsed.length - 1
            if (blocks[index] >= 0 && blocks[last] >= 0 && !spansGap(index, last)) {
                return positions[index] until (positions[last] + 1)
            }
            from = index + 1
        }
        return null
    }

    /** Every accepted match as a buffer range, appended to [into]. */
    fun locateAll(needle: String, into: MutableSet<IntRange>) {
        val collapsed = collapseReaderWhitespace(needle).trimEnd(' ')
        if (collapsed.isEmpty()) return
        var from = 0
        while (from <= text.length - collapsed.length) {
            val index = text.indexOf(collapsed, from)
            if (index < 0) return
            val last = index + collapsed.length - 1
            if (blocks[index] >= 0 && blocks[last] >= 0 && !spansGap(index, last)) {
                into += positions[index] until (positions[last] + 1)
            }
            from = index + 1
        }
    }

    /**
     * A run of collapsed whitespace can land on a hole between two blocks, so the whole extent of a
     * candidate must sit inside real block text before it is accepted.
     */
    private fun spansGap(first: Int, last: Int): Boolean {
        for (index in first..last) if (blocks[index] < 0) return true
        return false
    }

    companion object {
        fun of(buffer: String, blockOfPosition: IntArray): NormalizedChapterText {
            val normalized = StringBuilder(buffer.length)
            val absoluteOfPosition = IntArray(buffer.length)
            val blockOfNormalized = IntArray(buffer.length)
            var index = 0
            var pendingSpace = false
            for (position in buffer.indices) {
                val char = buffer[position]
                // Same space rule as the other matchers; see [isReaderSpace]. A non-breaking space left
                // visible here while a matcher elsewhere folded it would make the two disagree about
                // where one word ends and the next begins.
                if (isReaderSpace(char)) {
                    pendingSpace = normalized.isNotEmpty()
                    continue
                }
                if (pendingSpace) {
                    normalized.append(' ')
                    absoluteOfPosition[index] = position
                    blockOfNormalized[index] = blockOfPosition[position]
                    index++
                    pendingSpace = false
                }
                normalized.append(char)
                absoluteOfPosition[index] = position
                blockOfNormalized[index] = blockOfPosition[position]
                index++
            }
            return NormalizedChapterText(
                normalized.toString(),
                absoluteOfPosition.copyOf(index),
                blockOfNormalized.copyOf(index)
            )
        }
    }
}

/**
 * Collapses runs of whitespace and lowercases, so two strings that differ only in wrapping compare
 * equal. Leading whitespace is dropped; trailing whitespace can remain and callers trim it.
 *
 * Public because comparing a stored highlight's text against placed block text is not one call site's
 * job: the paginated surfaces do it too, to check that a range of stored offsets really covers the words
 * the highlight is made of. Two copies of this rule would eventually disagree about what counts as a
 * space, which is the exact failure it exists to prevent.
 */
fun collapseReaderWhitespace(value: String): String = buildString(value.length) {
    var lastWasSpace = false
    value.forEach { char ->
        if (isReaderSpace(char)) {
            if (isNotEmpty() && !lastWasSpace) {
                append(' ')
                lastWasSpace = true
            }
        } else {
            append(char.lowercaseChar())
            lastWasSpace = false
        }
    }
}

/**
 * Whether [char] separates words, for matching a highlight's stored text against parsed chapter text.
 *
 * `Char.isWhitespace` is not enough on its own: it is `Character.isWhitespace`, which reports false for
 * the non-breaking spaces — U+00A0, U+2007, U+202F and the rest of Unicode's Zs category — that EPUB
 * typography uses precisely so a line cannot break there. Two texts can be the same sentence and differ
 * only in which kind of space sits between two words, and with the non-breaking kind invisible the two
 * halves glued into one unmatchable "word". That is how a perfectly ordinary highlight ended up
 * unplaceable in every surface: it was made in a WebView, which kept the character the source had, and
 * placed against parsed blocks, which had turned it into an ordinary space.
 *
 * Both halves of the comparison have to agree on this, so every matcher here asks this question the same
 * way rather than each reaching for its own whitespace test.
 */
internal fun isReaderSpace(char: Char): Boolean = when (char.code) {
    0x20, 0xA0, 0x1680, 0x202F, 0x205F, 0x3000 -> true
    in 0x2000..0x200A -> true
    else -> char.isWhitespace()
}

/** Splits on any space [isReaderSpace] recognises, so no matcher can disagree about word boundaries. */
internal fun splitOnReaderSpaces(value: String): List<String> {
    val words = mutableListOf<String>()
    val current = StringBuilder()
    value.forEach { char ->
        if (isReaderSpace(char)) {
            if (current.isNotEmpty()) {
                words += current.toString()
                current.clear()
            }
        } else {
            current.append(char)
        }
    }
    if (current.isNotEmpty()) words += current.toString()
    return words
}

/** Depth-first flattening of a block tree into its text blocks, in document order. */
internal fun SemanticBlock.flattenToTextBlocks(): List<SemanticTextBlock> = when (this) {
    is SemanticTextBlock -> listOf(this)
    is SemanticList -> items.flatMap { it.flattenToTextBlocks() }
    is SemanticTable -> rows.flatMap { row -> row.flatMap { cell -> cell.content.flatMap { it.flattenToTextBlocks() } } }
    is SemanticFlexContainer -> children.flatMap { it.flattenToTextBlocks() }
    is SemanticWrappingBlock -> paragraphsToWrap.flatMap { it.flattenToTextBlocks() }
    else -> emptyList()
}

/**
 * The resolved position of a highlight, in the vocabulary the reader surfaces use.
 *
 * [HighlightAnchor] is what a renderer needs: absolute chapter offsets for page scoping and
 * block-local offsets for painting. It is deliberately free of any renderer's types so the
 * paginated reader, the native vertical reader and the WebView bridges can all consume it.
 */
data class HighlightAnchor(
    val chapterIndex: Int,
    /** Absolute chapter offsets, for deciding which page a highlight belongs to. */
    val absoluteStart: Int,
    val absoluteEnd: Int,
    /** Per-block slices, for painting. */
    val segments: List<HighlightBlockSegment>,
    val confidence: HighlightAnchorConfidence
)

/** True when this anchor can be trusted for page scoping and painting without further searching. */
val HighlightAnchor.isResolved: Boolean get() = confidence == HighlightAnchorConfidence.Resolved

private fun ResolvedHighlightAnchor.toHighlightAnchor(): HighlightAnchor = HighlightAnchor(
    chapterIndex = chapterIndex,
    absoluteStart = startOffset,
    absoluteEnd = endOffset,
    segments = segments,
    confidence = confidence
)
