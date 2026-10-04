package com.aryan.reader.paginatedreader

import com.aryan.reader.epubreader.logHighlightTrace
import com.aryan.reader.paginatedreader.EpubChapterTextIndex
import com.aryan.reader.paginatedreader.HighlightAnchor
import com.aryan.reader.paginatedreader.HighlightAnchorConfidence
import com.aryan.reader.paginatedreader.HighlightBlockSegment
import com.aryan.reader.shared.UserHighlight
import timber.log.Timber

/**
 * Decides which highlights a page shows, and where inside it they land.
 *
 * The previous arrangement resolved highlights per block. A highlight created in a WebView surface
 * carries no absolute offsets (its bridge sends only the selected text), so it could not be placed
 * by range and every block got a chance to claim it by matching its text. That produced the two
 * reported failures directly: a repeated sentence painted on every block containing a copy of it,
 * and a selection spanning several paragraphs matched nothing at all, because no single block was as
 * long as the selection.
 *
 * Locating the text once against the whole chapter fixes both. The highlight resolves to exactly one
 * place, and only the page holding that place shows it.
 */

/** A page's own contribution to scoping: the chapter it belongs to and the text blocks on it. */
internal data class PaginatedPageScope(
    val chapterIndex: Int?,
    val textBlocks: List<TextContentBlock>
)

/** The highlights that belong on one page, plus the block-local ranges to paint for each. */
internal data class PaginatedPageHighlights(
    /** Highlights to paint on this page, in a stable order. */
    val highlights: List<UserHighlight>,
    /**
     * Per-highlight block-local ranges, keyed by block index. A highlight can legitimately own more
     * than one entry when it spans consecutive blocks that both landed on this page.
     */
    val rangesByHighlight: Map<String, Map<Int, List<IntRange>>>
) {
    /**
     * Per-highlight ranges restricted to one block, which is the shape a text renderer needs.
     *
     * Ranges stay in the block's own local coordinates: a container's children each render their own
     * text, so a caller descending into a child asks for the child's index, never the container's.
     * Merging several blocks here would mix coordinate spaces and paint at the wrong offsets.
     */
    fun rangesForBlock(blockIndex: Int): Map<String, List<IntRange>> {
        if (rangesByHighlight.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, List<IntRange>>()
        for ((highlightId, perBlock) in rangesByHighlight) {
            val ranges = perBlock[blockIndex]
            if (!ranges.isNullOrEmpty()) result[highlightId] = ranges
        }
        return result
    }

    /**
     * Every block's ranges on this page, keyed by block index.
     *
     * For consumers that render a whole page at once, such as the vertical-writing renderer. Same
     * per-block, per-block-local shape as [rangesForBlock]; only the grouping differs.
     */
    fun rangesByBlock(): Map<Int, Map<String, List<IntRange>>> {
        if (rangesByHighlight.isEmpty()) return emptyMap()
        val result = LinkedHashMap<Int, Map<String, List<IntRange>>>()
        for ((highlightId, perBlock) in rangesByHighlight) {
            for ((blockIndex, ranges) in perBlock) {
                if (ranges.isEmpty()) continue
                val forBlock = result.getOrPut(blockIndex) { LinkedHashMap() } as LinkedHashMap<String, List<IntRange>>
                forBlock[highlightId] = ranges
            }
        }
        return result
    }
}

/**
 * Filters [highlights] down to those belonging on [scope], resolving each one.
 *
 * [chapterTextIndex] must cover the whole chapter. Handing it one page's blocks instead is exactly
 * what let a repeated sentence resolve on every page containing a copy of it.
 */
internal fun resolvePaginatedPageHighlights(
    scope: PaginatedPageScope,
    highlights: List<UserHighlight>,
    chapterTextIndex: EpubChapterTextIndex?
): PaginatedPageHighlights {
    val chapterIndex = scope.chapterIndex
    if (chapterIndex == null || highlights.isEmpty()) {
        return PaginatedPageHighlights(emptyList(), emptyMap())
    }

    val inChapter = highlights.filter { it.chapterIndex == chapterIndex }
    if (inChapter.isEmpty()) return PaginatedPageHighlights(emptyList(), emptyMap())

    val ranges = LinkedHashMap<String, Map<Int, List<IntRange>>>()
    val visible = mutableListOf<UserHighlight>()
    for (highlight in inChapter) {
        val resolved = resolvePaginatedHighlightOnPage(highlight, scope, chapterTextIndex) ?: continue
        ranges[highlight.id] = resolved
        visible += highlight
    }
    return PaginatedPageHighlights(visible, ranges)
}

/**
 * The block-local ranges [highlight] occupies on this page, or null when it is not on this page.
 *
 * A highlight with absolute offsets is placed by those offsets, which is exact and survives
 * repagination. One without them is located once in the chapter and accepted here only when a block
 * on this page owns the resolved location.
 */
private fun resolvePaginatedHighlightOnPage(
    highlight: UserHighlight,
    scope: PaginatedPageScope,
    chapterTextIndex: EpubChapterTextIndex?
): Map<Int, List<IntRange>>? {
    val pageBlocks = scope.textBlocks
    val locator = highlight.locator

    if (locator.hasTextRange && pageBlocks.pageBlocksHaveChapterOffsets()) {
        val start = locator.startOffset ?: return null
        val end = locator.endOffset ?: return null
        val quote = locator.textQuote?.takeIf { it.isNotBlank() } ?: highlight.text
        val perBlock = pageBlocks.mapNotNull { block ->
            val blockStartAbs = block.startCharOffsetInSource
            val blockEndAbs = block.textEndOffset(blockStartAbs)
            if (start < blockEndAbs && end > blockStartAbs) {
                val from = maxOf(start, blockStartAbs) - blockStartAbs
                val to = minOf(end, blockEndAbs) - blockStartAbs
                if (to <= from) return@mapNotNull null
                // Stored offsets are an optimisation, not an authority. They are written by several
                // surfaces and by older builds, so they can describe a range in a different coordinate
                // space from the one these blocks use — and then the arithmetic still produces a
                // perfectly well-formed range, just over the wrong characters. That is how a
                // 118-character selection came to be painted across most of a page: a 297-character
                // span of stored offsets satisfied the bounds check against every block on it.
                //
                // The highlight's own text is the only thing that can say whether a range is right, so
                // it is checked before anything is painted. A selection spanning paragraphs contributes
                // one contiguous slice per block, each of which appears in the quote, so a containment
                // test covers single- and multi-block highlights alike.
                val slice = block.content.text.substring(from, to)
                if (!highlightCoversText(slice, quote)) {
                    logHighlightTrace(
                        "place_offsets_rejected id=${highlight.id} block=${block.blockIndex} " +
                            "range=$from..$to quoteChars=${quote.length} " +
                            "reason=range_does_not_match_highlight_text"
                    )
                    return@mapNotNull null
                }
                block.blockIndex to listOf(from until to)
            } else {
                null
            }
        }.toMap()
        if (perBlock.isNotEmpty()) return perBlock

        // Nothing survived the check, which means these offsets do not describe this highlight. The
        // chapter index is how it gets placed instead, and it may well be available — so falling
        // through is not optional. Returning here left a highlight unpainted on every page even with a
        // perfectly good resolver in hand, which is a repairable position presented as a dead end.
        logHighlightTrace(
            "place_offsets_unusable id=${highlight.id} quoteChars=${quote.length} " +
                "offsets=$start..$end action=resolve_by_text"
        )
    }

    val quote = locator.textQuote?.takeIf { it.isNotBlank() } ?: highlight.text
    if (quote.isBlank()) return null

    logHighlightTrace(
        "scope id=${highlight.id} chapter=${highlight.chapterIndex} page=${scope.chapterIndex} " +
            "pageBlocks=${pageBlocks.size} indexed=${chapterTextIndex != null} quoteChars=${quote.length} " +
            "offsets=${locator.startOffset}..${locator.endOffset} block=${locator.blockIndex}"
    )

    if (chapterTextIndex == null) {
        Timber.tag(TAG_HIGHLIGHT_DIAG).d(
            "map_skip reason=no_chapter_index highlightId=${highlight.id} quoteLen=${quote.length}"
        )
        return null
    }
    val anchor: HighlightAnchor? = chapterTextIndex.resolve(quote)
    if (anchor == null) {
        Timber.tag(TAG_HIGHLIGHT_DIAG).d(
            "map_skip reason=quote_unresolved highlightId=${highlight.id} " +
                "quote='${highlightDiagSnippet(quote)}'"
        )
        return null
    }
    if (anchor.chapterIndex != scope.chapterIndex) return null
    if (anchor.confidence != HighlightAnchorConfidence.QuoteMatched) return null

    // Accept only when a block on this page owns the resolved location. This is the step that stops a
    // repeated sentence from painting on every page that happens to contain a copy of it.
    //
    // A page with no blocks paints no text either, because the page body renders its blocks and
    // nothing else, so there is nothing on it to highlight. That covers a genuinely blank page and one
    // whose content has not arrived yet; the latter fills in and this runs again.
    if (pageBlocks.isEmpty()) {
        logHighlightTrace(
            "place_decide id=${highlight.id} ownedByThisPage=false reason=page_has_no_blocks " +
                "anchorBlocks=${anchor.segments.map { it.blockIndex }}"
        )
        return null
    }
    val ownedByThisPage = anchor.segments.any { segment ->
        pageBlocks.any { block ->
            block.blockIndex == segment.blockIndex &&
                (segment.blockCfi == null || block.cfi == null || segment.blockCfi == block.cfi)
        }
    }
    logHighlightTrace(
        "place_decide id=${highlight.id} ownedByThisPage=$ownedByThisPage " +
            "anchorBlocks=${anchor.segments.map { it.blockIndex }} pageBlocks=${pageBlocks.map { it.blockIndex }}"
    )
    val perBlock = pageBlocks.mapNotNull { block ->
        val segment: HighlightBlockSegment = anchor.segments
            .firstOrNull { it.blockIndex == block.blockIndex }
            ?: return@mapNotNull null
        if (segment.blockCfi != null && block.cfi != null && segment.blockCfi != block.cfi) {
            return@mapNotNull null
        }
        val length = block.content.text.length
        val from = segment.localStart.coerceIn(0, length)
        val to = segment.localEnd.coerceIn(from, length)
        if (to > from) block.blockIndex to listOf(from until to) else null
    }.toMap()
    if (perBlock.isNotEmpty()) {
        // The numbers the painter will actually receive. Placement being correct is not the same as the
        // painter being handed the right thing, and these are the only values it gets.
        logHighlightTrace(
            "place_ranges id=${highlight.id} " +
                perBlock.entries.joinToString(" ") { (blockIndex, list) ->
                    "block$blockIndex=${list.map { "${it.first}..${it.last}" }}"
                } +
                " blockTextLengths=${perBlock.keys.map { index ->
                    pageBlocks.first { it.blockIndex == index }.content.text.length
                }}"
        )
    }
    return perBlock.ifEmpty { null }
}

/**
 * Fills in the offsets of a highlight just created by a WebView surface.
 *
 * The WebView bridge reports only (cfi, text, colour, style), so a highlight created there arrives
 * with no position at all. Resolving it once, here, means every other surface can place it exactly
 * instead of searching the chapter's text on every page that might contain a copy.
 *
 * Thin adapter over the shared resolver, which owns the matching rules and decides for itself
 * whether there is anything to fill in. Returns null when the text cannot be placed, leaving the
 * highlight as created: an unplaceable highlight is still shown by the surface that made it.
 */
internal fun resolveWebViewHighlightAnchor(
    highlight: UserHighlight?,
    chapterTextBlocks: List<TextContentBlock>?
): UserHighlight? {
    val target = highlight ?: return null
    val blocks = chapterTextBlocks?.takeIf { it.isNotEmpty() }
    logHighlightTrace(
        "anchor_in id=${target.id} chapter=${target.chapterIndex} chapterBlocks=${blocks?.size ?: 0} " +
            "incomingOffsets=${target.locator.startOffset}..${target.locator.endOffset} " +
            "incomingBlock=${target.locator.blockIndex} cfi=${target.cfi} quoteChars=${target.text.length}"
    )
    if (blocks == null) {
        logHighlightTrace(
            "anchor_skip id=${target.id} reason=no_chapter_blocks chapter=${target.chapterIndex} " +
                "hint=paginator_may_not_be_running_in_this_reading_mode"
        )
        return null
    }
    return EpubChapterTextIndex.of(target.chapterIndex, blocks.toSemanticTextBlocks())
        ?.anchorMissingOffsets(target)
}

/**
 * Whether these blocks carry usable chapter offsets.
 *
 * `startCharOffsetInSource` is element-relative: the parser restarts its counter for each element, so
 * a real parsed chapter reports the same start for every block. Comparing a stored absolute range
 * against such blocks would accept or reject by accident, so placement falls back to the text search
 * instead. A single block is fine either way, since there is nothing to compare against.
 */
/**
 * Whether [slice] is text this highlight actually covers.
 *
 * Whitespace is collapsed on both sides because the two come from different places — the stored offsets
 * index parsed block text, while the quote came from a DOM selection — and the join between two
 * paragraphs is represented differently in each. Only letters, digits and punctuation are compared;
 * whitespace differences are not evidence that a range is wrong.
 */
private fun highlightCoversText(slice: String, quote: String): Boolean {
    val collapsedSlice = collapseReaderWhitespace(slice)
    if (collapsedSlice.isEmpty()) return false
    val collapsedQuote = collapseReaderWhitespace(quote)
    if (collapsedQuote.isEmpty()) return false
    // Either the slice is the whole quote, or it is one contiguous part of it.
    return collapsedSlice == collapsedQuote || collapsedQuote.contains(collapsedSlice)
}

internal fun List<TextContentBlock>.pageBlocksHaveChapterOffsets(): Boolean {
    val starts = map { it.startCharOffsetInSource }
    return starts.size <= 1 || starts.distinct().size > 1
}

/**
 * Where this block's text ends in the chapter.
 *
 * The recorded end is -1 on blocks that never had one stored, which must not be read as "ends at -1"
 * or the block looks empty and every highlight inside it is dropped.
 */
internal fun TextContentBlock.textEndOffset(fallbackStart: Int): Int =
    endCharOffsetInSource.takeIf { it > fallbackStart } ?: (fallbackStart + content.text.length)

/**
 * A text block expressed in the [SemanticTextBlock] shape the shared chapter index expects.
 *
 * Only the fields placement needs are carried: the text, where it starts in the chapter, which block
 * it is, and its CFI. Styling is dropped because the resolver only searches text and never measures.
 * Blocks that hold no text yield null.
 */
internal fun TextContentBlock.toSemanticTextBlock(): SemanticTextBlock? {
    val text = content.text
    val style = CssStyle()
    return when (this) {
        is ParagraphBlock -> SemanticParagraph(
            text, emptyList(), style, elementId, cfi, startCharOffsetInSource, blockIndex
        )

        is HeaderBlock -> SemanticHeader(
            level, text, emptyList(), style, elementId, cfi, startCharOffsetInSource, blockIndex
        )

        is QuoteBlock -> SemanticParagraph(
            text, emptyList(), style, elementId, cfi, startCharOffsetInSource, blockIndex
        )

        is ListItemBlock -> SemanticListItem(
            text, emptyList(), style, elementId, cfi, startCharOffsetInSource, itemMarkerImage, blockIndex
        )

        else -> null
    }
}

/** A chapter's text blocks in the shape the shared chapter index expects, in document order. */
internal fun List<TextContentBlock>.toSemanticTextBlocks(): List<SemanticTextBlock> =
    mapNotNull { it.toSemanticTextBlock() }