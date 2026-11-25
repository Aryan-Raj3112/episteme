package com.aryan.reader.paginatedreader

import com.aryan.reader.shared.READER_SEARCH_QUOTE_CONTEXT_CHARS
import com.aryan.reader.shared.readerSearchCompactText
import com.aryan.reader.shared.readerSearchCommonPrefixLength
import com.aryan.reader.shared.readerSearchCommonSuffixLength
import com.aryan.reader.shared.readerWordStartMatchOffsets
import kotlin.math.abs

/**
 * Reader-search hits addressed in the paginator's own text space.
 *
 * A hit is produced by scanning chapter source text, but navigation has to land
 * on a paginated block. The word-start ordinal is exact only while the two
 * texts agree; footnote/endnote markup, math placeholders, dropped hidden nodes
 * and book replacements all move it. [readerSearchTarget] therefore falls back
 * to the hit's own quote so a divergent text can never silently page to a
 * neighbouring occurrence.
 */

/** One searchable run of paginated text, with the offsets a [Locator] needs. */
data class ReaderSearchTextBlock(
    val blockIndex: Int,
    val charOffsetInSource: Int,
    val text: String,
)

/** A resolved position inside one paginated text block. */
data class ReaderSearchTarget(
    val blockIndex: Int,
    val charOffsetInSource: Int,
)

/** Flattens styled paginator blocks in reading order. */
fun readerSearchTextBlocks(blocks: List<ContentBlock>): List<ReaderSearchTextBlock> =
    flattenTextContentBlocksForSharedNavigation(blocks).map { block ->
        ReaderSearchTextBlock(
            blockIndex = block.blockIndex,
            charOffsetInSource = block.startCharOffsetInSource,
            text = block.content.text,
        )
    }

/**
 * Same flattening for the shared semantic IR, which is what the cross-platform
 * loaders hand the mobile paginator (iOS has no styled [ContentBlock] pass).
 */
fun readerSearchTextBlocksFromSemanticBlocks(blocks: List<SemanticBlock>): List<ReaderSearchTextBlock> {
    val out = mutableListOf<ReaderSearchTextBlock>()
    fun add(block: SemanticTextBlock) {
        if (block.text.isBlank()) return
        out += ReaderSearchTextBlock(
            blockIndex = block.blockIndex,
            charOffsetInSource = block.startCharOffsetInSource,
            text = block.text,
        )
    }
    fun walk(block: SemanticBlock) {
        when (block) {
            is SemanticTextBlock -> add(block)
            is SemanticList -> block.items.forEach(::walk)
            is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> cell.content.forEach(::walk) } }
            is SemanticFlexContainer -> block.children.forEach(::walk)
            is SemanticWrappingBlock -> block.paragraphsToWrap.forEach(::walk)
            else -> Unit // Images, math and spacers carry no searchable prose.
        }
    }
    blocks.forEach(::walk)
    return out
}

/**
 * A chapter's word-start matches, computed once.
 *
 * A chapter's hits share the same scan, and a common word can produce hundreds
 * of hits; resolving each one from scratch would rescan the chapter per hit.
 */
class ReaderSearchMatchIndex internal constructor(
    val textBlocks: List<ReaderSearchTextBlock>,
    val query: String,
    internal val matches: List<Pair<Int, Int>>,
)

/** Indexes every word-start match of [query] in one chapter's blocks. */
fun readerSearchMatchIndex(
    textBlocks: List<ReaderSearchTextBlock>,
    query: String,
): ReaderSearchMatchIndex {
    val needle = query.trim()
    val matches = mutableListOf<Pair<Int, Int>>() // blockPosition to offsetInBlock
    if (needle.isNotEmpty()) {
        textBlocks.forEachIndexed { blockPosition, block ->
            readerWordStartMatchOffsets(block.text, needle).forEach { offset ->
                matches += blockPosition to offset
            }
        }
    }
    return ReaderSearchMatchIndex(textBlocks = textBlocks, query = needle, matches = matches)
}

/**
 * Resolves one hit to a block/character target.
 *
 * The quote goes first: each match of the query is scored by how much of its own
 * surrounding text the hit's quote shares, and the requested ordinal breaks
 * ties. That is correct whether or not the texts agree — when they do, the hit's
 * own context scores highest and the ordinal confirms it; when a math
 * placeholder, a dropped hidden node or a replacement moved the ordinal, the
 * quote still points at the right occurrence. The ordinal alone is the fallback,
 * for hits whose quote is missing or has no usable context here.
 */
fun readerSearchTarget(
    textBlocks: List<ReaderSearchTextBlock>,
    query: String,
    occurrenceIndexInLocation: Int,
    matchQuote: String,
): ReaderSearchTarget? = readerSearchTarget(
    index = readerSearchMatchIndex(textBlocks, query),
    occurrenceIndexInLocation = occurrenceIndexInLocation,
    matchQuote = matchQuote,
)

/** [readerSearchTarget] against an index built once for the whole chapter. */
fun readerSearchTarget(
    index: ReaderSearchMatchIndex,
    occurrenceIndexInLocation: Int,
    matchQuote: String,
): ReaderSearchTarget? {
    if (index.textBlocks.isEmpty()) return null

    readerSearchQuoteTarget(
        textBlocks = index.textBlocks,
        needles = index.query,
        matches = index.matches,
        matchQuote = matchQuote,
        occurrenceIndexInLocation = occurrenceIndexInLocation,
    )?.let { return it }

    return index.matches.getOrNull(occurrenceIndexInLocation)?.let { (blockPosition, offset) ->
        index.textBlocks[blockPosition].targetAt(offset)
    }
}

/**
 * Scores every query match by the context it shares with [matchQuote] — the
 * suffix before the match plus the prefix after it — and returns the best one.
 * Both contexts are compared with whitespace removed, which is the only thing
 * the source markup and the paginated blocks reliably disagree about.
 */
private fun readerSearchQuoteTarget(
    textBlocks: List<ReaderSearchTextBlock>,
    needles: String,
    matches: List<Pair<Int, Int>>,
    matchQuote: String,
    occurrenceIndexInLocation: Int,
): ReaderSearchTarget? {
    if (needles.isEmpty() || matches.isEmpty()) return null

    val compactNeedle = readerSearchCompactText(needles)
    val compactQuote = readerSearchCompactText(matchQuote)
    if (compactNeedle.isEmpty() || compactQuote.isEmpty()) return null
    val queryInQuote = compactQuote.indexOf(compactNeedle)
    if (queryInQuote < 0) return null
    val quoteBefore = compactQuote.substring(0, queryInQuote)
    val quoteAfter = compactQuote.substring(queryInQuote + compactNeedle.length)

    var best: Pair<Int, Int>? = null
    var bestScore = 0
    var bestDistance = Int.MAX_VALUE

    matches.forEachIndexed { matchOrdinal, (blockPosition, offset) ->
        val blockText = textBlocks[blockPosition].text
        val before = readerSearchCompactText(
            blockText.substring((offset - READER_SEARCH_QUOTE_CONTEXT_CHARS).coerceAtLeast(0), offset)
        )
        val after = readerSearchCompactText(
            blockText.substring(
                (offset + needles.length).coerceAtMost(blockText.length),
                (offset + needles.length + READER_SEARCH_QUOTE_CONTEXT_CHARS).coerceAtMost(blockText.length),
            )
        )
        val score = readerSearchCommonSuffixLength(before, quoteBefore) +
            readerSearchCommonPrefixLength(after, quoteAfter)
        if (score == 0) return@forEachIndexed

        val distance = abs(matchOrdinal - occurrenceIndexInLocation)
        if (score > bestScore || (score == bestScore && distance < bestDistance)) {
            bestScore = score
            bestDistance = distance
            best = blockPosition to offset
        }
    }

    return best?.let { (blockPosition, offset) -> textBlocks[blockPosition].targetAt(offset) }
}

private fun ReaderSearchTextBlock.targetAt(offsetInBlock: Int): ReaderSearchTarget =
    ReaderSearchTarget(blockIndex = blockIndex, charOffsetInSource = charOffsetInSource + offsetInBlock)
