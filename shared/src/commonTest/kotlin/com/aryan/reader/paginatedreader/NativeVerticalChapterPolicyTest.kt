package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import kotlin.test.Test
import kotlin.test.assertEquals

class NativeVerticalChapterPolicyTest {
    @Test
    fun decodesBase64AndPercentEncodedSvgDataUris() {
        assertEquals(
            "<svg>++</svg>",
            decodeNativeVerticalSvgDataUri("data:image/svg+xml,%3Csvg%3E+%2B%3C%2Fsvg%3E")
        )
        assertEquals(
            "<svg/>",
            decodeNativeVerticalSvgDataUri("data:image/svg+xml;base64,PHN2Zy8+")
        )
        assertEquals(null, decodeNativeVerticalSvgDataUri("data:image/png;base64,abc"))
        assertEquals(null, decodeNativeVerticalSvgDataUri("data:image/svg+xml"))
    }

    @Test
    fun `progress and compatibility page conversions preserve Android math`() {
        assertEquals(50, nativeVerticalCompatPageForProgress(50f, 101))
        assertEquals(50f, nativeVerticalProgressForCompatPage(50, 101))
        assertEquals(1, nativeVerticalProgressToItemIndex(listOf(0, 100, 300, 600), 0f))
        assertEquals(2, nativeVerticalProgressToItemIndex(listOf(0, 100, 300, 600), 25f))
        assertEquals(25f, estimateNativeVerticalWeightedScrollProgressPercent(listOf(100, 300, 600), 1, 500, 1000))
    }

    @Test
    fun `chapter page labels and restore policies preserve Android behavior`() {
        assertEquals(6, nativeVerticalChapterPageInfo(500, 1000, 11, 900, 850)?.currentPage)
        assertEquals(5, nativeVerticalChapterPageInfo(null, 0, 7, 24, 20)?.currentPage)
        assertEquals(6, nativeVerticalChapterPageInfoForScroll(listOf(0, 0, 1, 1), listOf(100, 300, 100, 300), 1, 500, 1000, 9)?.currentPage)
        assertEquals(false, shouldFallbackNativeVerticalInitialScrollToCompatPage(true, false))
        assertEquals(true, shouldFallbackNativeVerticalInitialScrollToCompatPage(false, false))
        assertEquals(100f, nativeVerticalCenteredScrollDelta(500f, 800f))
        assertEquals(114, nativeVerticalCompatPageForLocator(500, 110, 9, 1000, 7))
        assertEquals(7, nativeVerticalCompatPageForLocator(500, null, 9, 1000, 7))
        assertEquals(50f, nativeVerticalProgressPercentForLocator(listOf(100, 200, 300), 2, 0))
        assertEquals(100f, nativeVerticalProgressPercentForLocator(listOf(100, 200, 300), 2, 900))
        assertEquals(null, nativeVerticalProgressPercentForLocator(listOf(0, 0), 0, 0))
    }

    @Test
    fun `prefetch and warmup preserve Android ordering and bounds`() {
        assertEquals(listOf(4, 5), nativeVerticalInitialChapterPrefetchOrder(6, 3))
        assertEquals(listOf(3, 4, 2, 5, 6), nativeVerticalChapterWarmupOrder(7, 3))
        assertEquals(listOf(0, 1, 2), nativeVerticalChapterWarmupOrder(3, -4))
        assertEquals(listOf(2, 1), nativeVerticalChapterWarmupOrder(3, 9))
        assertEquals(emptyList(), nativeVerticalChapterWarmupOrder(0, 0))
    }

    @Test
    fun `failed load remains retryable while empty load becomes loaded`() {
        val placeholders = listOf(
            NativeVerticalFlowChapter(0, "Chapter", emptyList(), isLoaded = false, estimatedLocationWeight = 42),
        )
        assertEquals(
            null,
            nativeVerticalFlowChaptersAfterLoadResult(
                currentChapters = placeholders,
                placeholderChapters = placeholders,
                chapterIndex = 0,
                title = "Chapter",
                blocks = null,
                estimatedLocationWeight = 42,
            ),
        )
        assertEquals(
            true,
            nativeVerticalFlowChaptersAfterLoadResult(
                currentChapters = placeholders,
                placeholderChapters = placeholders,
                chapterIndex = 0,
                title = "Chapter",
                blocks = emptyList(),
                estimatedLocationWeight = 42,
            )?.single()?.isLoaded,
        )
    }

    @Test
    fun `flow item construction preserves Android keys boundaries and weights`() {
        val paragraph = ParagraphBlock(
            content = AnnotatedString("Readable text"),
            cfi = "/4/2",
            startCharOffsetInSource = 40,
            endCharOffsetInSource = 53,
            blockIndex = 7,
        )
        val items = buildNativeVerticalFlowItems(
            listOf(
                NativeVerticalFlowChapter(0, "Loaded", listOf(paragraph), estimatedLocationWeight = 13),
                NativeVerticalFlowChapter(1, "Loading", emptyList(), isLoaded = false, estimatedLocationWeight = 13),
                NativeVerticalFlowChapter(2, "Empty", emptyList()),
            )
        )

        assertEquals(
            listOf(
                NativeVerticalFlowItemKind.BLOCK,
                NativeVerticalFlowItemKind.CHAPTER_GAP,
                NativeVerticalFlowItemKind.UNLOADED_CHAPTER,
                NativeVerticalFlowItemKind.CHAPTER_GAP,
                NativeVerticalFlowItemKind.EMPTY_CHAPTER,
            ),
            items.map { it.kind },
        )
        assertEquals("chapter-0-block-0-7", items.first().key)
        assertEquals(24, items.first().locationWeight)
        assertEquals(24, items[2].locationWeight)
    }

    @Test
    fun `nested locator traversal resolves containing flow item and source offset`() {
        val paragraph = ParagraphBlock(
            content = AnnotatedString("nested target"),
            cfi = "/4/6",
            startCharOffsetInSource = 80,
            endCharOffsetInSource = 93,
            blockIndex = 12,
        )
        val container = FlexContainerBlock(children = listOf(paragraph), blockIndex = 10)
        val chapters = listOf(NativeVerticalFlowChapter(3, "Chapter", listOf(container)))
        val items = buildNativeVerticalFlowItems(chapters)
        val target = ReaderNavigationTarget(3, 12, 86)

        assertEquals(paragraph, findNativeVerticalFlowTextBlockForTarget(chapters, target))
        assertEquals(0, findNativeVerticalFlowItemIndexForTarget(items, chapters, target))
        assertEquals(
            ReaderNavigationTarget(3, 12, 80),
            nativeVerticalNavigationTargetForItem(items.single()),
        )
    }
}
