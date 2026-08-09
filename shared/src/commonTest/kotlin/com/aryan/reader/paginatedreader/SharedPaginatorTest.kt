package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedPaginatorTest {
    private val provider = object : BlockMeasurementProvider {
        override suspend fun measure(block: ContentBlock): Int = when (block) {
            is TextContentBlock -> block.content.length * 10
            else -> block.expectedHeight
        }

        override suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock>? {
            val offset = (availableHeight / 10).coerceIn(0, block.content.length)
            if (offset <= 0 || offset >= block.content.length) return null
            return block.copy(content = block.content.subSequence(0, offset)) to
                block.copy(content = block.content.subSequence(offset, block.content.length))
        }

        override suspend fun split(block: WrappingContentBlock, availableHeight: Int) = null
        override suspend fun split(block: TableBlock, availableHeight: Int) = null
        override suspend fun split(block: FlexContainerBlock, availableHeight: Int) = null
        override suspend fun split(block: ChantScoreBlock, availableHeight: Int) = null
    }

    @Test
    fun placesFitsHonorsForcedBreaksAndNormalizesCommittedMargin() = kotlinx.coroutines.test.runTest {
        val first = ParagraphBlock(AnnotatedString("four"), blockIndex = 1)
        val second = ParagraphBlock(
            AnnotatedString("five5"),
            style = BlockStyle(breakAfter = "page"),
            blockIndex = 2
        )
        val third = ParagraphBlock(AnnotatedString("next"), blockIndex = 3)

        val pages = paginateReaderBlocks(listOf(first, second, third), 100, provider, Density(1f))
        assertEquals(2, pages.size)
        assertEquals(listOf(1, 2), pages[0].content.map(ContentBlock::blockIndex))
        assertEquals(listOf(3), pages[1].content.map(ContentBlock::blockIndex))
        assertEquals(0f, pages[0].content.last().style.margin.bottom.value)
        assertEquals(40, pages[0].content[0].expectedHeight)
        assertEquals(50, pages[0].content[1].expectedHeight)
    }

    @Test
    fun splitsOversizeParagraphAndQueuesRemainder() = kotlinx.coroutines.test.runTest {
        val paragraph = ParagraphBlock(AnnotatedString("abcdefghijklmno"), blockIndex = 7)
        val pages = paginateReaderBlocks(listOf(paragraph), 100, provider, Density(1f))

        assertEquals(2, pages.size)
        assertEquals("abcdefghij", (pages[0].content.single() as ParagraphBlock).content.text)
        assertEquals("klmno", (pages[1].content.single() as ParagraphBlock).content.text)
        assertEquals(100, pages[0].content.single().expectedHeight)
        assertEquals(50, pages[1].content.single().expectedHeight)
    }
}
