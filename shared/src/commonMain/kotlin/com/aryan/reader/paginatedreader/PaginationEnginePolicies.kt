package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.dp

interface BlockMeasurementProvider {
    suspend fun measure(block: ContentBlock): Int
    suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock>?
    suspend fun split(block: WrappingContentBlock, availableHeight: Int): Pair<WrappingContentBlock, List<ContentBlock>>?
    suspend fun split(block: TableBlock, availableHeight: Int): Pair<TableBlock, TableBlock>?
    suspend fun split(block: FlexContainerBlock, availableHeight: Int): Pair<FlexContainerBlock, FlexContainerBlock>?
    suspend fun split(block: ChantScoreBlock, availableHeight: Int): Pair<ChantScoreBlock, ChantScoreBlock>?
}

fun BlockStyle.avoidsReaderPageBreakInside(): Boolean =
    pageBreakInsideAvoid || breakInside in setOf("avoid", "avoid-page", "avoid-column")

fun BlockStyle.forcesReaderPageBreakBefore(): Boolean =
    breakBefore in setOf("page", "always", "left", "right", "recto", "verso")

fun BlockStyle.forcesReaderPageBreakAfter(): Boolean =
    breakAfter in setOf("page", "always", "left", "right", "recto", "verso")

fun zeroReaderLastBottomMargin(blocks: MutableList<ContentBlock>) {
    if (blocks.isEmpty()) return
    val last = blocks.last()
    val style = last.style.copy(margin = last.style.margin.copy(bottom = 0.dp))
    blocks[blocks.lastIndex] = when (last) {
        is ParagraphBlock -> last.copy(style = style)
        is HeaderBlock -> last.copy(style = style)
        is ImageBlock -> last.copy(style = style)
        is SpacerBlock -> last.copy(style = style)
        is QuoteBlock -> last.copy(style = style)
        is ListItemBlock -> last.copy(style = style)
        is WrappingContentBlock -> last.copy(style = style)
        is TableBlock -> last.copy(style = style)
        is FlexContainerBlock -> last.copy(style = style)
        is ChantScoreBlock -> last.copy(style = style)
        is MathBlock -> last.copy(style = style)
    }
}

@Suppress("UNCHECKED_CAST")
fun <T : ContentBlock> T.withReaderExpectedHeight(height: Int): T = when (this) {
    is ParagraphBlock -> copy(expectedHeight = height)
    is HeaderBlock -> copy(expectedHeight = height)
    is ImageBlock -> copy(expectedHeight = height)
    is SpacerBlock -> copy(expectedHeight = height)
    is QuoteBlock -> copy(expectedHeight = height)
    is ListItemBlock -> copy(expectedHeight = height)
    is WrappingContentBlock -> copy(expectedHeight = height)
    is TableBlock -> copy(expectedHeight = height)
    is FlexContainerBlock -> copy(expectedHeight = height)
    is ChantScoreBlock -> copy(expectedHeight = height)
    is MathBlock -> copy(expectedHeight = height)
} as T

fun ContentBlock.withReaderBlockStyle(newStyle: BlockStyle): ContentBlock = when (this) {
    is ParagraphBlock -> copy(style = newStyle)
    is HeaderBlock -> copy(style = newStyle)
    is ImageBlock -> copy(style = newStyle)
    is SpacerBlock -> copy(style = newStyle)
    is QuoteBlock -> copy(style = newStyle)
    is ListItemBlock -> copy(style = newStyle)
    is WrappingContentBlock -> copy(style = newStyle)
    is TableBlock -> copy(style = newStyle)
    is FlexContainerBlock -> copy(style = newStyle)
    is ChantScoreBlock -> copy(style = newStyle)
    is MathBlock -> copy(style = newStyle)
}
