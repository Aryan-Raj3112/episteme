// PaginatorTest.kt
package com.aryan.reader.paginatedreader

import android.os.Build
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

class FakeSplittableMeasurementProvider(
    private val heights: Map<ContentBlock, Int>,
    private val splittableParagraphs: Map<ParagraphBlock, Pair<ParagraphBlock, ParagraphBlock>> = emptyMap(),
    private val splittableWrappers: Map<WrappingContentBlock, Pair<WrappingContentBlock, List<ContentBlock>>> = emptyMap()
) : BlockMeasurementProvider {
    override suspend fun measure(block: ContentBlock): Int {
        // Provide a more helpful error message if a block's height is not defined.
        return heights[block] ?: error("No height specified for block: $block")
    }

    override suspend fun split(block: ParagraphBlock, availableHeight: Int): Pair<ParagraphBlock, ParagraphBlock>? {
        val splitPair = splittableParagraphs[block]
        if (splitPair != null) {
            val part1Height = heights[splitPair.first] ?: 0
            // Only return the split pair if the first part actually fits in the available height.
            if (part1Height <= availableHeight) {
                return splitPair
            }
        }
        return null
    }

    override suspend fun split(block: WrappingContentBlock, availableHeight: Int): Pair<WrappingContentBlock, List<ContentBlock>>? {
        val splitPair = splittableWrappers[block]
        if (splitPair != null) {
            val part1Height = heights[splitPair.first] ?: 0
            if (part1Height <= availableHeight) {
                return splitPair
            }
        }
        return null
    }

    override suspend fun split(block: TableBlock, availableHeight: Int): Pair<TableBlock, TableBlock>? = null

    override suspend fun split(block: FlexContainerBlock, availableHeight: Int): Pair<FlexContainerBlock, FlexContainerBlock>? = null
}

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class PaginatorTest {

    private val testDensity = Density(density = 1f, fontScale = 1f)
    private val pageHeight = 1000

    private fun List<ContentBlock>.withoutMeasuredHeights(): List<ContentBlock> {
        return map { it.withoutMeasuredHeight() }
    }

    private fun ContentBlock.withoutMeasuredHeight(): ContentBlock {
        return when (this) {
            is ParagraphBlock -> copy(expectedHeight = 0)
            is ImageBlock -> copy(expectedHeight = 0)
            is HeaderBlock -> copy(expectedHeight = 0)
            is SpacerBlock -> copy(expectedHeight = 0)
            is QuoteBlock -> copy(expectedHeight = 0)
            is ListItemBlock -> copy(expectedHeight = 0)
            is TableBlock -> copy(
                rows = rows.map { row ->
                    row.map { cell ->
                        cell.copy(content = cell.content.withoutMeasuredHeights())
                    }
                },
                expectedHeight = 0
            )
            is MathBlock -> copy(expectedHeight = 0)
            is WrappingContentBlock -> copy(
                floatedImage = floatedImage.copy(expectedHeight = 0),
                paragraphsToWrap = paragraphsToWrap.map { it.copy(expectedHeight = 0) },
                expectedHeight = 0
            )
            is FlexContainerBlock -> copy(
                children = children.withoutMeasuredHeights(),
                expectedHeight = 0
            )
        }
    }

    @Test
    fun paginate_givenEmptyBlocks_createsZeroPages() = runTest {
        val pages = paginate(emptyList(), pageHeight, FakeSplittableMeasurementProvider(emptyMap()), testDensity)
        assertThat(pages).isEmpty()
    }

    @Test
    fun paginate_givenBlocksThatFit_createsOnePage() = runTest {
        val block1 = ParagraphBlock(content = AnnotatedString("Block 1"), blockIndex = 0)
        val block2 = ParagraphBlock(content = AnnotatedString("Block 2"), blockIndex = 1)
        val blocks = listOf(block1, block2)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(block1 to 200, block2 to 300)
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(1)
        assertThat(pages.first().content).hasSize(2)
    }

    @Test
    fun paginate_givenBlockThatOverflows_createsTwoPages() = runTest {
        val block1 = ParagraphBlock(content = AnnotatedString("Block 1"), blockIndex = 0) // Height: 600
        val block2 = ParagraphBlock(content = AnnotatedString("Block 2"), blockIndex = 1) // Height: 500
        val blocks = listOf(block1, block2)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(block1 to 600, block2 to 500)
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(2)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(block1)
        assertThat(pages[1].content.withoutMeasuredHeights()).containsExactly(block2)
    }

    @Test
    fun paginate_honorsBreakBeforePage() = runTest {
        val block1 = ParagraphBlock(content = AnnotatedString("Before"), blockIndex = 0)
        val block2 = ParagraphBlock(
            content = AnnotatedString("After"),
            style = BlockStyle(breakBefore = "page"),
            blockIndex = 1
        )

        val pages = paginate(
            listOf(block1, block2),
            pageHeight,
            FakeSplittableMeasurementProvider(mapOf(block1 to 100, block2 to 100)),
            testDensity
        )

        assertThat(pages).hasSize(2)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(block1)
        assertThat(pages[1].content.withoutMeasuredHeights()).containsExactly(block2)
    }

    @Test
    fun paginate_breakInsideAvoidPreventsParagraphSplit() = runTest {
        val block1 = ParagraphBlock(
            content = AnnotatedString("Keep together"),
            style = BlockStyle(breakInside = "avoid"),
            blockIndex = 0
        )
        val part1 = block1.copy(content = AnnotatedString("Keep"))
        val part2 = block1.copy(content = AnnotatedString("together"))

        val pages = paginate(
            listOf(block1),
            pageHeight = 400,
            measurementProvider = FakeSplittableMeasurementProvider(
                heights = mapOf(block1 to 800, part1 to 300, part2 to 500),
                splittableParagraphs = mapOf(block1 to (part1 to part2))
            ),
            density = testDensity
        )

        assertThat(pages).hasSize(1)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(block1)
    }

    @Test
    fun paginate_correctlySplitsAParagraphBlock() = runTest {
        val block1 = ParagraphBlock(content = AnnotatedString("First block"), blockIndex = 0)
        val originalParagraph = ParagraphBlock(content = AnnotatedString("Long text to be split"), blockIndex = 1)
        val part1 = ParagraphBlock(content = AnnotatedString("Long text"), blockIndex = 1)
        val part2 = ParagraphBlock(content = AnnotatedString("to be split"), blockIndex = 1)
        val blocks = listOf(block1, originalParagraph)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(
                block1 to 500,
                originalParagraph to 800,
                part1 to 450, // Fits in the remaining 500
                part2 to 350
            ),
            splittableParagraphs = mapOf(originalParagraph to (part1 to part2))
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(2)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(block1, part1).inOrder()
        assertThat(pages[1].content.withoutMeasuredHeights()).containsExactly(part2)
    }

    @Test
    fun paginate_correctlySplitsAWrappingContentBlock() = runTest {
        val image = ImageBlock("image.png", null, 100f, 300f, blockIndex = 0)
        val para1 = ParagraphBlock(content = AnnotatedString("Para 1"), blockIndex = 1)
        val para2 = ParagraphBlock(content = AnnotatedString("Para 2"), blockIndex = 2)
        val originalWrapper = WrappingContentBlock(floatedImage = image, paragraphsToWrap = listOf(para1, para2), blockIndex = 3)

        val splitWrapper = WrappingContentBlock(floatedImage = image, paragraphsToWrap = listOf(para1), blockIndex = 3)
        val remainingBlocks = listOf(para2)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(
                originalWrapper to 1500,
                splitWrapper to 300,
                para2 to 200
            ),
            splittableWrappers = mapOf(originalWrapper to (splitWrapper to remainingBlocks))
        )

        val pages = paginate(listOf(originalWrapper), pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(2)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(splitWrapper)
        assertThat(pages[1].content.withoutMeasuredHeights()).containsExactly(para2)
    }


    @Test
    fun paginate_respectsPageBreakInsideAvoid() = runTest {
        val block1 = ParagraphBlock(content = AnnotatedString("First block"), blockIndex = 0) // Height 800
        val unsplittableBlock = ParagraphBlock(
            content = AnnotatedString("Can't split me"),
            style = BlockStyle(pageBreakInsideAvoid = true),
            blockIndex = 1
        ) // Height 300
        val blocks = listOf(block1, unsplittableBlock)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(block1 to 800, unsplittableBlock to 300)
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(2)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(block1)
        assertThat(pages[1].content.withoutMeasuredHeights()).containsExactly(unsplittableBlock)
    }

    @Test
    fun paginate_oversizedUnsplittableBlockGetsItsOwnPage() = runTest {
        val oversizedBlock = ImageBlock(path = "test.jpg", altText = null, blockIndex = 0) // Height 1200
        val blocks = listOf(oversizedBlock)

        val measurementProvider = FakeSplittableMeasurementProvider(heights = mapOf(oversizedBlock to 1200))

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(1)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(oversizedBlock)
    }

    @Test
    fun paginate_collapsesVerticalMarginsBetweenBlocks() = runTest {
        val block1 = ParagraphBlock(
            content = AnnotatedString("Block 1"),
            style = BlockStyle(margin = BoxBorders(bottom = 50.dp)), // 50px margin
            blockIndex = 0
        )
        val block2 = ParagraphBlock(
            content = AnnotatedString("Block 2"),
            style = BlockStyle(margin = BoxBorders(top = 80.dp)), // 80px margin
            blockIndex = 1
        )
        val blocks = listOf(block1, block2)
        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(block1 to 100, block2 to 100)
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)

        assertThat(pages).hasSize(1)
        val pageContent = pages.first().content
        assertThat(pageContent).hasSize(2)
        // The paginator logic sets the bottom margin of the previous block to 0
        // and sets the top margin of the current block to the collapsed value.
        assertThat(pageContent[0].style.margin.bottom).isEqualTo(0.dp)
        assertThat(pageContent[1].style.margin.top).isEqualTo(80.dp) // max(50, 80) is 80
    }

    @Test
    fun paginate_preservesTopMarginOfTheFirstBlockOnANewPage() = runTest {
        val block1 = ParagraphBlock(
            content = AnnotatedString("Block 1"),
            style = BlockStyle(margin = BoxBorders(top = 30.dp)),
            blockIndex = 0
        )
        val block2 = ParagraphBlock(content = AnnotatedString("Block 2"), blockIndex = 1)
        val blocks = listOf(block1, block2)
        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(block1 to 980, block2 to 100)
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)
        assertThat(pages).hasSize(2)

        // First block on page 1 should have its top margin preserved.
        val page1Block1 = pages[0].content.first()
        assertThat(page1Block1.style.margin.top).isEqualTo(30.dp)

        // First block on page 2 should also have its top margin preserved.
        val page2Block1 = pages[1].content.first()
        assertThat(page2Block1.style.margin.top).isEqualTo(0.dp) // The default is 0.dp
    }

    @Test
    fun paginate_blockPushedToNextPageWhenNotEnoughSpaceForSplitting() = runTest {
        val block1 = ParagraphBlock(content = AnnotatedString("Block 1"), blockIndex = 0)
        val splittableBlock = ParagraphBlock(content = AnnotatedString("Splittable"), blockIndex = 1)
        val part1 = ParagraphBlock(content = AnnotatedString("Split"), blockIndex = 1)
        val part2 = ParagraphBlock(content = AnnotatedString("table"), blockIndex = 1)
        val blocks = listOf(block1, splittableBlock)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(
                block1 to 960, // Leaves 40px remaining, which is < 50, so no split should occur
                splittableBlock to 100,
                part1 to 30,
                part2 to 70
            ),
            splittableParagraphs = mapOf(splittableBlock to (part1 to part2))
        )

        val pages = paginate(blocks, pageHeight, measurementProvider, testDensity)
        assertThat(pages).hasSize(2)
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(block1)
        assertThat(pages[1].content.withoutMeasuredHeights()).containsExactly(splittableBlock) // Was not split
    }

    @Test
    fun paginate_doesNotAddEmptyPart1AfterSplitting() = runTest {
        val originalBlock = ParagraphBlock(content = AnnotatedString("Some text"), blockIndex = 0)
        val part1 = ParagraphBlock(content = AnnotatedString(""), blockIndex = 0) // Empty part 1
        val part2 = ParagraphBlock(content = AnnotatedString("Some text"), blockIndex = 0)
        val blocks = listOf(originalBlock)

        val measurementProvider = FakeSplittableMeasurementProvider(
            heights = mapOf(
                originalBlock to 200,
                part1 to 0,
                part2 to 200
            ),
            splittableParagraphs = mapOf(originalBlock to (part1 to part2))
        )

        // Set page height so that a split is attempted.
        val pages = paginate(blocks, 150, measurementProvider, testDensity)
        assertThat(pages).hasSize(1)
        // Empty split heads are skipped so pagination keeps only the remaining content.
        assertThat(pages[0].content.withoutMeasuredHeights()).containsExactly(part2)
    }
}
