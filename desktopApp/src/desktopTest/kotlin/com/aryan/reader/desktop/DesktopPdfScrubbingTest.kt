package com.aryan.reader.desktop

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopPdfScrubbingTest {
    @Test
    fun `scrub target clamps to valid page range`() {
        val settings = ReaderSettings()

        assertEquals(
            0,
            desktopPdfPageScrubTarget(
                value = -10f,
                pageCount = 6,
                displayMode = PdfDisplayMode.VERTICAL_SCROLL,
                settings = settings
            )
        )
        assertEquals(
            5,
            desktopPdfPageScrubTarget(
                value = 99f,
                pageCount = 6,
                displayMode = PdfDisplayMode.VERTICAL_SCROLL,
                settings = settings
            )
        )
    }

    @Test
    fun `paginated scrub target normalizes to spread start`() {
        val settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)

        assertEquals(
            2,
            desktopPdfPageScrubTarget(
                value = 3f,
                pageCount = 8,
                displayMode = PdfDisplayMode.PAGINATION,
                settings = settings
            )
        )
    }

    @Test
    fun `scrub commit prefers preview before page state catches up`() {
        assertEquals(
            7,
            desktopPdfPageScrubCommitTarget(
                previewPage = 7,
                currentPage = 2,
                pageCount = 10
            )
        )
    }
}
