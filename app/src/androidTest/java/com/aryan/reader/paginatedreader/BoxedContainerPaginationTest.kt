// BoxedContainerPaginationTest.kt
package com.aryan.reader.paginatedreader

import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression tests for publisher callout boxes (bordered flex containers) that must fragment
 * across pages. Children must be measured and line-split at the container's CONTENT width;
 * splitting them at the full page width produces fragments that render taller than budgeted
 * and overflow the page bottom.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.VANILLA_ICE_CREAM)
class BoxedContainerPaginationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testDensity = Density(density = 2f, fontScale = 1f)
    private val pageWidthPx = 1000
    private val pageHeightPx = 700

    private fun longBoxParagraph(blockIndex: Int): ParagraphBlock {
        val sentence = "Tout objet moins dense qu'un fluide dans lequel il repose flotte. "
        return ParagraphBlock(
            content = AnnotatedString(sentence.repeat(30)),
            blockIndex = blockIndex
        )
    }

    private fun greyCalloutBox(children: List<ContentBlock>, blockIndex: Int): FlexContainerBlock {
        return FlexContainerBlock(
            children = children,
            style = BlockStyle(
                margin = BoxBorders(left = 90.dp, right = 20.dp, top = 24.dp, bottom = 24.dp),
                padding = BoxBorders(top = 16.dp, bottom = 16.dp, left = 12.dp, right = 12.dp),
                borderLeft = BorderStyle(width = 2.dp, color = Color.Gray),
                borderRight = BorderStyle(width = 2.dp, color = Color.Gray),
                borderTop = BorderStyle(width = 8.dp, color = Color.DarkGray),
                borderBottom = BorderStyle(width = 8.dp, color = Color.DarkGray)
            ),
            blockIndex = blockIndex
        )
    }

    @Test
    fun boxedContainerWithOversizedParagraphKeepsEveryPageWithinBudget() {
        var paginatedPages: List<Page>? = null

        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                val box = greyCalloutBox(
                    children = listOf(
                        HeaderBlock(
                            level = 1,
                            content = AnnotatedString("Pourquoi les bateaux flottent-ils ?"),
                            textAlign = TextAlign.Center,
                            blockIndex = 1
                        ),
                        longBoxParagraph(blockIndex = 2)
                    ),
                    blockIndex = 3
                )
                val provider = SuspendingAndroidBlockMeasurementProvider(
                    textMeasurer = textMeasurer,
                    constraints = Constraints(maxWidth = pageWidthPx, maxHeight = pageHeightPx),
                    textStyle = TextStyle(fontSize = 16.sp),
                    density = testDensity,
                    imageSizeMultiplier = 1f
                )
                paginatedPages = paginate(listOf(box), pageHeightPx, provider, testDensity)
            }
        }
        composeTestRule.waitForIdle()

        val pages = requireNotNull(paginatedPages) { "pagination did not run" }

        // The long paragraph cannot fit one page, so the box must fragment.
        assertThat(pages.size).isAtLeast(2)

        // No page may be overfilled: every placed block's expected height (which includes its
        // collapsed spacing) must sum to within the page budget.
        pages.forEachIndexed { pageIndex, page ->
            val placedHeight = page.content.sumOf { it.expectedHeight }
            assertThat(placedHeight).isAtMost(pageHeightPx)
        }
    }

    @Test
    fun boxedContainerContinuationSplitsInsideFirstChildWhenAloneOnFreshPage() {
        var paginatedPages: List<Page>? = null

        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                // Simulates the queue state after an earlier between-children break: the
                // remainder's first child alone is taller than a full page.
                val box = greyCalloutBox(
                    children = listOf(longBoxParagraph(blockIndex = 2)),
                    blockIndex = 3
                )
                val provider = SuspendingAndroidBlockMeasurementProvider(
                    textMeasurer = textMeasurer,
                    constraints = Constraints(maxWidth = pageWidthPx, maxHeight = pageHeightPx),
                    textStyle = TextStyle(fontSize = 16.sp),
                    density = testDensity,
                    imageSizeMultiplier = 1f
                )
                paginatedPages = paginate(listOf(box), pageHeightPx, provider, testDensity)
            }
        }
        composeTestRule.waitForIdle()

        val pages = requireNotNull(paginatedPages) { "pagination did not run" }

        assertThat(pages.size).isAtLeast(2)
        pages.forEach { page ->
            val placedHeight = page.content.sumOf { it.expectedHeight }
            assertThat(placedHeight).isAtMost(pageHeightPx)
        }
    }

    @Test
    fun boxedContainerWithNestedListFlexKeepsEveryPageWithinBudget() {
        var paginatedPages: List<Page>? = null

        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                // Publisher list boxes are double-nested: the bordered div wraps a <ul>, which
                // the styler emits as an inner FlexContainerBlock of list items. Nested
                // containers must derive widths from the box's content area.
                val listFlex = FlexContainerBlock(
                    children = listOf(
                        longBoxParagraph(blockIndex = 1),
                        longBoxParagraph(blockIndex = 2),
                        longBoxParagraph(blockIndex = 3)
                    ),
                    blockIndex = 4
                )
                val box = greyCalloutBox(children = listOf(listFlex), blockIndex = 5)
                val provider = SuspendingAndroidBlockMeasurementProvider(
                    textMeasurer = textMeasurer,
                    constraints = Constraints(maxWidth = pageWidthPx, maxHeight = pageHeightPx),
                    textStyle = TextStyle(fontSize = 16.sp),
                    density = testDensity,
                    imageSizeMultiplier = 1f
                )
                paginatedPages = paginate(listOf(box), pageHeightPx, provider, testDensity)
            }
        }
        composeTestRule.waitForIdle()

        val pages = requireNotNull(paginatedPages) { "pagination did not run" }

        // Three long paragraphs cannot fit one page; the nested list must fragment.
        assertThat(pages.size).isAtLeast(2)
        pages.forEach { page ->
            val placedHeight = page.content.sumOf { block -> block.expectedHeight }
            assertThat(placedHeight).isAtMost(pageHeightPx)
        }
    }

    @Test
    fun boxedContainerWithListItemsKeepsEveryPageWithinBudget() {
        var paginatedPages: List<Page>? = null

        composeTestRule.setContent {
            val textMeasurer = rememberTextMeasurer()
            LaunchedEffect(Unit) {
                // List items render beside a marker area; their line breaks must be chosen at
                // the marker-narrowed width or fragments re-wrap taller than budgeted.
                val sentence = "Les sloops ont soit un grand-voile et un foc, soit une grand-voile seule. "
                fun listItem(index: Int): ListItemBlock = ListItemBlock(
                    content = AnnotatedString(sentence.repeat(12)),
                    itemMarker = "•",
                    style = BlockStyle(),
                    blockIndex = index
                )
                val listFlex = FlexContainerBlock(
                    children = listOf(listItem(1), listItem(2), listItem(3)),
                    blockIndex = 4
                )
                val box = greyCalloutBox(children = listOf(listFlex), blockIndex = 5)
                val provider = SuspendingAndroidBlockMeasurementProvider(
                    textMeasurer = textMeasurer,
                    constraints = Constraints(maxWidth = pageWidthPx, maxHeight = pageHeightPx),
                    textStyle = TextStyle(fontSize = 16.sp),
                    density = testDensity,
                    imageSizeMultiplier = 1f
                )
                paginatedPages = paginate(listOf(box), pageHeightPx, provider, testDensity)
            }
        }
        composeTestRule.waitForIdle()

        val pages = requireNotNull(paginatedPages) { "pagination did not run" }

        assertThat(pages.size).isAtLeast(2)
        pages.forEach { page ->
            val placedHeight = page.content.sumOf { block -> block.expectedHeight }
            assertThat(placedHeight).isAtMost(pageHeightPx)
        }
    }
}
