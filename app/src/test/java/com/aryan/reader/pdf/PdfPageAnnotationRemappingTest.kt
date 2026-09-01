package com.aryan.reader.pdf

import com.aryan.reader.pdf.data.VirtualPage
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfPageAnnotationRemappingTest {

    @Test
    fun `bookmark follows its pdf page when a blank page is inserted`() {
        val current = listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1))
        val updated = listOf(
            VirtualPage.PdfPage(0),
            VirtualPage.BlankPage("inserted", 595, 842),
            VirtualPage.PdfPage(1),
        )

        val result = remapPdfBookmarksJsonForLayoutChange(
            currentLayout = current,
            updatedLayout = updated,
            currentBookmarksJson = "[{\"pageIndex\":1,\"title\":\"Page\",\"totalPages\":2}]",
        )

        val bookmark = JSONArray(result).getJSONObject(0)
        assertEquals(2, bookmark.getInt("pageIndex"))
        assertEquals(3, bookmark.getInt("totalPages"))
    }

    @Test
    fun `bookmark is retained on the nearest page when its generated page is removed`() {
        val current = listOf(
            VirtualPage.PdfPage(0),
            VirtualPage.BlankPage("generated", 595, 842),
            VirtualPage.PdfPage(1),
        )
        val updated = listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1))

        val result = remapPdfBookmarksJsonForLayoutChange(
            currentLayout = current,
            updatedLayout = updated,
            currentBookmarksJson = "[{\"pageIndex\":1,\"title\":\"Generated\",\"totalPages\":3}]",
        )

        val bookmark = JSONArray(result).getJSONObject(0)
        assertEquals(1, bookmark.getInt("pageIndex"))
        assertEquals(2, bookmark.getInt("totalPages"))
    }

    @Test
    fun `auto pruning refuses to remove a bookmarked generated page`() {
        val page = VirtualPage.BlankPage("generated", 595, 842)

        assertFalse(
            shouldAutoPrunePdfBlankPage(
                lastPage = page,
                currentLastIndex = 2,
                highestRequiredTextPageIndex = 0,
                hasText = false,
                hasAnnotations = false,
                hasTextBoxes = false,
                hasHighlights = false,
                hasBookmark = true,
            )
        )
        assertTrue(
            shouldAutoPrunePdfBlankPage(
                lastPage = page,
                currentLastIndex = 2,
                highestRequiredTextPageIndex = 0,
                hasText = false,
                hasAnnotations = false,
                hasTextBoxes = false,
                hasHighlights = false,
                hasBookmark = false,
            )
        )
    }
}
