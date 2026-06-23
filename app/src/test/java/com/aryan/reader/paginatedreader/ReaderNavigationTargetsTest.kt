package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderNavigationTargetsTest {
    @Test
    fun `search locator resolves exact occurrence offset in text block`() {
        val blocks = listOf(
            ParagraphBlock(
                content = AnnotatedString("first target then second target"),
                cfi = "/4/2",
                startCharOffsetInSource = 100,
                endCharOffsetInSource = 131,
                blockIndex = 7
            )
        )
        val result = SearchResult(
            locationInSource = 3,
            locationTitle = "Chapter",
            snippet = AnnotatedString("second target"),
            query = "target",
            occurrenceIndexInLocation = 1,
            chunkIndex = 0
        )

        assertEquals(
            Locator(chapterIndex = 3, blockIndex = 7, charOffset = 125),
            findLocatorForSearchResultInBlocks(result, blocks)
        )
    }

    @Test
    fun `anchor locator resolves string annotation offset`() {
        val content = buildAnnotatedString {
            append("before anchored text")
            addStringAnnotation(tag = "ID", annotation = "anchor-1", start = 7, end = 15)
        }
        val blocks = listOf(
            ParagraphBlock(
                content = content,
                cfi = "/4/4",
                startCharOffsetInSource = 40,
                endCharOffsetInSource = 60,
                blockIndex = 9
            )
        )

        assertEquals(
            Locator(chapterIndex = 2, blockIndex = 9, charOffset = 47),
            findLocatorForAnchorInBlocks(chapterIndex = 2, anchor = "anchor-1", blocks = blocks)
        )
    }

    @Test
    fun `anchor locator resolves non text block element id`() {
        val blocks = listOf(
            ImageBlock(
                path = "images/cover.jpg",
                altText = "Cover",
                elementId = "cover-image",
                cfi = "/4/6",
                blockIndex = 11
            )
        )

        assertEquals(
            Locator(chapterIndex = 5, blockIndex = 11, charOffset = 0),
            findLocatorForAnchorInBlocks(chapterIndex = 5, anchor = "cover-image", blocks = blocks)
        )
    }

    @Test
    fun `native vertical initial prefetch is bounded around requested chapter`() {
        assertEquals(
            listOf(4, 5),
            nativeVerticalInitialChapterPrefetchOrder(chapterCount = 6, initialChapter = 3)
        )
    }

    @Test
    fun `native vertical failed flow load keeps chapter retryable`() {
        val placeholders = listOf(
            NativeVerticalFlowChapter(
                chapterIndex = 0,
                title = "Chapter",
                blocks = emptyList(),
                isLoaded = false,
                estimatedLocationWeight = 42
            )
        )

        val updated = nativeVerticalFlowChaptersAfterLoadResult(
            currentChapters = placeholders,
            placeholderChapters = placeholders,
            chapterIndex = 0,
            title = "Chapter",
            blocks = null,
            estimatedLocationWeight = 42
        )

        assertEquals(null, updated)
    }

    @Test
    fun `native vertical empty flow load marks chapter loaded`() {
        val placeholders = listOf(
            NativeVerticalFlowChapter(
                chapterIndex = 0,
                title = "Chapter",
                blocks = emptyList(),
                isLoaded = false,
                estimatedLocationWeight = 42
            )
        )

        val updated = nativeVerticalFlowChaptersAfterLoadResult(
            currentChapters = placeholders,
            placeholderChapters = placeholders,
            chapterIndex = 0,
            title = "Chapter",
            blocks = emptyList(),
            estimatedLocationWeight = 42
        )

        assertEquals(true, updated?.single()?.isLoaded)
    }

    @Test
    fun `native vertical warmup prioritizes anchor then nearby chapters`() {
        assertEquals(
            listOf(3, 4, 2, 5, 6),
            nativeVerticalChapterWarmupOrder(chapterCount = 7, anchorChapter = 3)
        )
    }

    @Test
    fun `native vertical warmup clamps anchor and bounds neighbors`() {
        assertEquals(
            listOf(0, 1, 2),
            nativeVerticalChapterWarmupOrder(chapterCount = 3, anchorChapter = -4)
        )
        assertEquals(
            listOf(2, 1),
            nativeVerticalChapterWarmupOrder(chapterCount = 3, anchorChapter = 9)
        )
    }
}
