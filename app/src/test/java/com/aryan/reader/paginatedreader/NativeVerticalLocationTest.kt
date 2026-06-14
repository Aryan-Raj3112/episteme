package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeVerticalLocationTest {

    @Test
    fun `compat page follows native progress`() {
        assertEquals(0, nativeVerticalCompatPageForProgress(0f, 101))
        assertEquals(50, nativeVerticalCompatPageForProgress(50f, 101))
        assertEquals(100, nativeVerticalCompatPageForProgress(100f, 101))
    }

    @Test
    fun `progress follows compat page`() {
        assertEquals(0f, nativeVerticalProgressForCompatPage(0, 101), 0.001f)
        assertEquals(50f, nativeVerticalProgressForCompatPage(50, 101), 0.001f)
        assertEquals(100f, nativeVerticalProgressForCompatPage(100, 101), 0.001f)
    }

    @Test
    fun `progress target skips zero weight chapter gaps`() {
        val weights = listOf(0, 100, 300, 600)

        assertEquals(1, nativeVerticalProgressToItemIndex(weights, 0f))
        assertEquals(2, nativeVerticalProgressToItemIndex(weights, 25f))
        assertEquals(3, nativeVerticalProgressToItemIndex(weights, 100f))
    }

    @Test
    fun `scroll progress updates within visible item offset`() {
        val weights = listOf(100, 300, 600)

        assertEquals(
            25f,
            estimateNativeVerticalWeightedScrollProgressPercent(
                itemWeights = weights,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 500,
                firstVisibleItemSize = 1000
            ),
            0.001f
        )
        assertEquals(
            40f,
            estimateNativeVerticalWeightedScrollProgressPercent(
                itemWeights = weights,
                firstVisibleItemIndex = 1,
                firstVisibleItemScrollOffset = 1000,
                firstVisibleItemSize = 1000
            ),
            0.001f
        )
    }

    @Test
    fun `chapter page info uses chapter local locator offset`() {
        val pageInfo = nativeVerticalChapterPageInfo(
            chapterCharOffset = 500,
            chapterLengthChars = 1000,
            chapterPageCount = 11,
            compatPageIndex = 900,
            chapterStartPageIndex = 850
        )

        assertEquals(6, pageInfo?.currentPage)
        assertEquals(11, pageInfo?.totalPages)
    }

    @Test
    fun `chapter page info falls back to absolute page within chapter`() {
        val pageInfo = nativeVerticalChapterPageInfo(
            chapterCharOffset = null,
            chapterLengthChars = 0,
            chapterPageCount = 7,
            compatPageIndex = 24,
            chapterStartPageIndex = 20
        )

        assertEquals(5, pageInfo?.currentPage)
        assertEquals(7, pageInfo?.totalPages)
    }

    @Test
    fun `chapter page info follows scroll weight within current chapter`() {
        val pageInfo = nativeVerticalChapterPageInfoForScroll(
            itemChapterIndices = listOf(0, 0, 1, 1),
            itemWeights = listOf(100, 300, 100, 300),
            firstVisibleItemIndex = 1,
            firstVisibleItemScrollOffset = 500,
            firstVisibleItemSize = 1000,
            chapterPageCount = 9
        )

        assertEquals(6, pageInfo?.currentPage)
        assertEquals(9, pageInfo?.totalPages)
    }

    @Test
    fun `native vertical image model decodes svg data uris for coil svg fetcher`() {
        val model = nativeVerticalImageModelData(
            "data:image/svg+xml,%3Csvg%20viewBox%3D%220%200%2010%2010%22%3E%3Ccircle%20r%3D%225%22%2F%3E%3C%2Fsvg%3E"
        )

        assertTrue(model is SvgData)
        assertEquals("""<svg viewBox="0 0 10 10"><circle r="5"/></svg>""", (model as SvgData).content)
    }

    @Test
    fun `native vertical svg data uri decoding preserves plus signs`() {
        assertEquals(
            """<svg><path d="M1+2"/></svg>""",
            nativeVerticalSvgContentFromDataUri(
                "data:image/svg+xml,%3Csvg%3E%3Cpath%20d%3D%22M1+2%22%2F%3E%3C%2Fsvg%3E"
            )
        )
    }

    @Test
    fun `native vertical persistence locator prefers visible text range`() {
        val location = NativeVerticalLocation(
            locator = Locator(chapterIndex = 2, blockIndex = 10, charOffset = 100),
            chapterIndex = 2,
            progressPercent = 42f,
            compatPageIndex = 20,
            compatTotalPages = 100,
            firstVisibleItemIndex = 4,
            firstVisibleItemScrollOffset = 250,
            firstVisibleItemSize = 1000,
            isAtStart = false,
            isAtEnd = false,
            visibleTextRanges = listOf(
                NativeVerticalVisibleTextRange(
                    chapterIndex = 2,
                    blockIndex = 10,
                    startCharOffset = 380,
                    endCharOffset = 520
                )
            )
        )

        assertEquals(Locator(chapterIndex = 2, blockIndex = 10, charOffset = 380), location.locatorForPersistence())
    }

    @Test
    fun `native vertical initial restore does not fallback to compat page when locator exists`() {
        assertEquals(
            false,
            shouldFallbackNativeVerticalInitialScrollToCompatPage(
                hasInitialLocator = true,
                didLocatorScroll = false
            )
        )
        assertEquals(
            true,
            shouldFallbackNativeVerticalInitialScrollToCompatPage(
                hasInitialLocator = false,
                didLocatorScroll = false
            )
        )
    }

    @Test
    fun `native vertical tts follow centers target offset in viewport`() {
        assertEquals(
            100f,
            nativeVerticalCenteredScrollDelta(
                targetOffsetInViewport = 500f,
                viewportHeight = 800f
            ),
            0.001f
        )
        assertEquals(
            -200f,
            nativeVerticalCenteredScrollDelta(
                targetOffsetInViewport = 200f,
                viewportHeight = 800f
            ),
            0.001f
        )
    }
}
