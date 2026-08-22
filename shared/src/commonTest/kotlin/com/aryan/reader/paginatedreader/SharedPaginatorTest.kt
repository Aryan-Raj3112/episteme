package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun largeVerticalContainerIsMeasuredIncrementally() = kotlinx.coroutines.test.runTest {
        val childMeasurements = mutableMapOf<Int, Int>()
        val children = (0 until 100).map { index ->
            ParagraphBlock(AnnotatedString("row"), blockIndex = index + 1)
        }
        val incrementalProvider = object : BlockMeasurementProvider {
            override suspend fun measure(block: ContentBlock): Int = when (block) {
                is FlexContainerBlock -> {
                    var total = 0
                    for (child in block.children) {
                        total += measure(child)
                    }
                    total
                }
                else -> {
                    childMeasurements[block.blockIndex] = (childMeasurements[block.blockIndex] ?: 0) + 1
                    10
                }
            }

            override suspend fun split(block: FlexContainerBlock, availableHeight: Int): Pair<FlexContainerBlock, FlexContainerBlock>? {
                var used = 0
                var splitAt = block.children.size
                for ((index, child) in block.children.withIndex()) {
                    val height = measure(child)
                    if (used + height > availableHeight) {
                        splitAt = index
                        break
                    }
                    used += height
                }
                if (splitAt <= 0 || splitAt >= block.children.size) return null
                return block.copy(children = block.children.take(splitAt)) to
                    block.copy(children = block.children.drop(splitAt))
            }

            override suspend fun split(block: ParagraphBlock, availableHeight: Int) = null
            override suspend fun split(block: WrappingContentBlock, availableHeight: Int) = null
            override suspend fun split(block: TableBlock, availableHeight: Int) = null
            override suspend fun split(block: ChantScoreBlock, availableHeight: Int) = null
        }

        val pages = paginateReaderBlocks(
            blocks = listOf(FlexContainerBlock(children = children, blockIndex = 500)),
            pageHeight = 100,
            measurementProvider = incrementalProvider,
            density = Density(1f),
        )

        assertEquals(10, pages.size)
        assertTrue(childMeasurements.values.max() <= 3, "rows should not be remeasured for every later page")
    }

    @Test
    fun breakInsideAvoidBlockTallerThanFullPageIsStillSplit() = kotlinx.coroutines.test.runTest {
        val avoid = ParagraphBlock(
            AnnotatedString("avoid"),
            style = BlockStyle(breakInside = "avoid"),
            blockIndex = 9
        )
        val part1 = avoid.copy(content = AnnotatedString("av"))
        val part2 = avoid.copy(content = AnnotatedString("oid"))
        val provider = object : BlockMeasurementProvider {
            override suspend fun measure(block: ContentBlock): Int = when (block) {
                avoid -> 1200
                part1 -> 400
                part2 -> 800
                else -> error("unexpected block $block")
            }

            override suspend fun split(block: ParagraphBlock, availableHeight: Int) =
                if (block == avoid) part1 to part2 else null

            override suspend fun split(block: WrappingContentBlock, availableHeight: Int) = null
            override suspend fun split(block: TableBlock, availableHeight: Int) = null
            override suspend fun split(block: FlexContainerBlock, availableHeight: Int) = null
            override suspend fun split(block: ChantScoreBlock, availableHeight: Int) = null
        }

        val pages = paginateReaderBlocks(listOf(avoid), 1000, provider, Density(1f))

        assertEquals(2, pages.size)
        assertEquals("av", (pages[0].content.single() as ParagraphBlock).content.text)
        assertEquals("oid", (pages[1].content.single() as ParagraphBlock).content.text)
    }

    @Test
    fun breakInsideAvoidFlexTallerThanFullPageIsStillSplit() = kotlinx.coroutines.test.runTest {
        val childA = ParagraphBlock(AnnotatedString("a"), blockIndex = 2)
        val childB = ParagraphBlock(AnnotatedString("b"), blockIndex = 2)
        val box = FlexContainerBlock(
            children = listOf(childA, childB),
            style = BlockStyle(breakInside = "avoid"),
            blockIndex = 3
        )
        val head = FlexContainerBlock(children = listOf(childA), blockIndex = 3)
        val tail = FlexContainerBlock(children = listOf(childB), blockIndex = 3)
        val provider = object : BlockMeasurementProvider {
            override suspend fun measure(block: ContentBlock): Int = when (block) {
                box -> 1500
                head -> 900
                tail -> 600
                else -> error("unexpected block $block")
            }

            override suspend fun split(block: FlexContainerBlock, availableHeight: Int) =
                if (block == box) head to tail else null

            override suspend fun split(block: ParagraphBlock, availableHeight: Int) = null
            override suspend fun split(block: WrappingContentBlock, availableHeight: Int) = null
            override suspend fun split(block: TableBlock, availableHeight: Int) = null
            override suspend fun split(block: ChantScoreBlock, availableHeight: Int) = null
        }

        val pages = paginateReaderBlocks(listOf(box), 1000, provider, Density(1f))

        assertEquals(2, pages.size)
        assertEquals(head.withReaderExpectedHeight(0), pages[0].content.single().withReaderExpectedHeight(0))
        assertEquals(tail.withReaderExpectedHeight(0), pages[1].content.single().withReaderExpectedHeight(0))
    }

    @Test
    fun breakInsideAvoidShorterThanFullPageIsKeptWholeWhenPushedToNextPage() = kotlinx.coroutines.test.runTest {
        val first = ParagraphBlock(AnnotatedString("first"), blockIndex = 1)
        val avoid = ParagraphBlock(
            AnnotatedString("avoid"),
            style = BlockStyle(breakInside = "avoid"),
            blockIndex = 2
        )
        val provider = object : BlockMeasurementProvider {
            override suspend fun measure(block: ContentBlock): Int = when (block) {
                first -> 900
                avoid -> 800
                else -> error("unexpected block $block")
            }

            override suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock> =
                error("split must not be attempted for a break-inside:avoid block that fits a full page")

            override suspend fun split(block: WrappingContentBlock, availableHeight: Int) = null
            override suspend fun split(block: TableBlock, availableHeight: Int) = null
            override suspend fun split(block: FlexContainerBlock, availableHeight: Int) = null
            override suspend fun split(block: ChantScoreBlock, availableHeight: Int) = null
        }

        val pages = paginateReaderBlocks(listOf(first, avoid), 1000, provider, Density(1f))

        assertEquals(2, pages.size)
        assertEquals(first.withReaderExpectedHeight(0), pages[0].content.single().withReaderExpectedHeight(0))
        assertEquals(avoid.withReaderExpectedHeight(0), pages[1].content.single().withReaderExpectedHeight(0))
    }

    @Test
    fun splitFragmentTallerThanRemainingSpaceIsRejectedAndPushedWhole() = kotlinx.coroutines.test.runTest {
        // Regression: a splitter that returns a head fragment measuring taller than the space it
        // was asked to fill must not be committed unchecked; that produced bottom-of-page
        // overflows on decorated callout boxes.
        val first = ParagraphBlock(AnnotatedString("first"), blockIndex = 1)
        val splittable = ParagraphBlock(AnnotatedString("splittable"), blockIndex = 2)
        val fatPart1 = ParagraphBlock(AnnotatedString("fat"), blockIndex = 2)
        val part2 = ParagraphBlock(AnnotatedString("rest"), blockIndex = 2)
        val provider = object : BlockMeasurementProvider {
            override suspend fun measure(block: ContentBlock): Int = when (block) {
                first -> 900
                splittable -> 800
                fatPart1 -> 500 // Exceeds the 100px left on page 1 despite fitting the ask loosely
                part2 -> 300
                else -> error("unexpected block $block")
            }

            override suspend fun split(block: ParagraphBlock, availableHeight: Int) =
                if (block == splittable) fatPart1 to part2 else null

            override suspend fun split(block: WrappingContentBlock, availableHeight: Int) = null
            override suspend fun split(block: TableBlock, availableHeight: Int) = null
            override suspend fun split(block: FlexContainerBlock, availableHeight: Int) = null
            override suspend fun split(block: ChantScoreBlock, availableHeight: Int) = null
        }

        val pages = paginateReaderBlocks(listOf(first, splittable), 1000, provider, Density(1f))

        assertEquals(2, pages.size)
        assertEquals(first.withReaderExpectedHeight(0), pages[0].content.single().withReaderExpectedHeight(0))
        // The whole block was pushed instead of its oversized fragment.
        assertEquals(splittable.withReaderExpectedHeight(0), pages[1].content.single().withReaderExpectedHeight(0))
    }
}
