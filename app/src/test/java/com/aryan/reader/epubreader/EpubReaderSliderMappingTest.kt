package com.aryan.reader.epubreader

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the EPUB page slider's step <-> book-page boundary.
 *
 * [epubSliderBookPage] is the piece that can silently point at the wrong page: two-page paginated
 * mode is spread-indexed while native vertical and per-chapter scroll are page-indexed, and the
 * spread helper clamps to whatever page count it is handed.
 */
class EpubReaderSliderMappingTest {

    @Test
    fun `single page mode maps steps straight to book pages`() {
        val mapped = epubSliderBookPage(
            totalPages = 10,
            sliderCurrentPage = 5f,
            isTwoPageSpread = false,
            totalBookPageCount = 0,
        )

        assertEquals(10, mapped.pageCount)
        assertEquals(4, mapped.pageIndex)
    }

    @Test
    fun `single page mode ignores the book paginator total`() {
        // Per-chapter scroll reports its own small total while the paginator may already
        // know hundreds of pages; the clamp must never consult the larger number.
        val mapped = epubSliderBookPage(
            totalPages = 3,
            sliderCurrentPage = 2f,
            isTwoPageSpread = false,
            totalBookPageCount = 500,
        )

        assertEquals(3, mapped.pageCount)
        assertEquals(1, mapped.pageIndex)
    }

    @Test
    fun `single page mode clamps the step into range`() {
        assertEquals(
            0,
            epubSliderBookPage(10, 0f, isTwoPageSpread = false, totalBookPageCount = 0).pageIndex,
        )
        assertEquals(
            9,
            epubSliderBookPage(10, 99f, isTwoPageSpread = false, totalBookPageCount = 0).pageIndex,
        )
        assertEquals(
            0,
            epubSliderBookPage(0, 1f, isTwoPageSpread = false, totalBookPageCount = 0).pageIndex,
        )
    }

    @Test
    fun `two page mode maps a spread step to the spread's first book page`() {
        val mapped = epubSliderBookPage(
            totalPages = 5,
            sliderCurrentPage = 3f,
            isTwoPageSpread = true,
            totalBookPageCount = 10,
        )

        assertEquals(10, mapped.pageCount)
        assertEquals(4, mapped.pageIndex)
    }

    @Test
    fun `two page mode keeps the final short spread on its own first page`() {
        val mapped = epubSliderBookPage(
            totalPages = 3,
            sliderCurrentPage = 3f,
            isTwoPageSpread = true,
            totalBookPageCount = 5,
        )

        assertEquals(5, mapped.pageCount)
        assertEquals(4, mapped.pageIndex)
    }

    @Test
    fun `two page mode tolerates a paginator that has not reported a total yet`() {
        val mapped = epubSliderBookPage(
            totalPages = 0,
            sliderCurrentPage = 1f,
            isTwoPageSpread = true,
            totalBookPageCount = 0,
        )

        assertEquals(1, mapped.pageCount)
        assertEquals(0, mapped.pageIndex)
        assertEquals(1, epubSliderStepForBookPage(0, true, 0))
    }

    @Test
    fun `two page mode round trips every step`() {
        val totalBookPageCount = 11
        val totalPages = 6

        for (step in 1..totalPages) {
            val mapped = epubSliderBookPage(
                totalPages = totalPages,
                sliderCurrentPage = step.toFloat(),
                isTwoPageSpread = true,
                totalBookPageCount = totalBookPageCount,
            )
            assertEquals(
                "step $step",
                step,
                epubSliderStepForBookPage(mapped.pageIndex, true, totalBookPageCount),
            )
        }
    }

    @Test
    fun `step for book page is identity-offset without a spread`() {
        assertEquals(5, epubSliderStepForBookPage(4, false, 0))
        assertEquals(1, epubSliderStepForBookPage(0, false, 0))
    }
}
