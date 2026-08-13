package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals

class PdfLegacyPageBookmarksTest {
    @Test
    fun `legacy bookmark decoder preserves valid items and skips malformed objects`() {
        assertEquals(
            linkedSetOf(
                LegacyPdfPageBookmark(pageIndex = 2, title = "Chapter", totalPages = 10),
                LegacyPdfPageBookmark(pageIndex = 4, title = "End", totalPages = 10)
            ),
            LegacyPdfPageBookmarkCodec.decode(
                """[{"pageIndex":2,"title":"Chapter","totalPages":10},{"pageIndex":3},{"pageIndex":4,"title":"End","totalPages":10}]"""
            )
        )
    }

    @Test
    fun `legacy bookmark decoder rejects blank non-array and malformed roots`() {
        assertEquals(emptySet(), LegacyPdfPageBookmarkCodec.decode(null))
        assertEquals(emptySet(), LegacyPdfPageBookmarkCodec.decode("{}"))
        assertEquals(emptySet(), LegacyPdfPageBookmarkCodec.decode("["))
    }
}
