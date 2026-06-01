package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.SharedLibraryStateProjector
import com.aryan.reader.shared.SharedReaderScreenState
import com.aryan.reader.shared.ui.toNonReaderLibraryOrganizationModel
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopPdfReflowTest {
    @Test
    fun `desktop reflow ids and labels match Android text view convention`() {
        assertEquals("abc_reflow", desktopPdfReflowBookId("abc"))
        assertTrue(isDesktopPdfReflowBookId("abc_reflow"))
        assertEquals("Source (Text View)", desktopPdfReflowDisplayName("Source"))
        assertEquals("Source (Reflow)", desktopPdfReflowTitle("Source"))
        assertEquals("Generated", desktopPdfReflowGeneratedAuthor())
    }

    @Test
    fun `desktop reflow filename is safe for path-like book ids`() {
        val fileName = desktopPdfReflowFileName("C:/Books/My Source.pdf", "My Source")

        assertEquals("C__Books_My_Source.pdf_reflow.html", fileName)
    }

    @Test
    fun `desktop reflow book item maps generated html into text reader format`() {
        val source = BookItem(
            id = "pdf-id",
            path = "C:/Books/source.pdf",
            type = FileType.PDF,
            displayName = "source.pdf",
            timestamp = 1L,
            title = "Source"
        )
        val generatedFile = File("build/test-tmp/source_reflow.html")

        val item = desktopPdfReflowBookItem(
            sourceBook = source,
            generatedFile = generatedFile,
            nowMillis = 42L,
            initialPageIndex = 7
        )

        assertEquals("pdf-id_reflow", item.id)
        assertEquals(FileType.HTML, item.type)
        assertEquals(generatedFile.absolutePath, item.path)
        assertEquals("Source (Text View)", item.displayName)
        assertEquals("Source (Reflow)", item.title)
        assertEquals("Generated", item.author)
        assertEquals(42L, item.timestamp)
        assertEquals(7, item.lastPageIndex)
        assertTrue(item.isRecent)
    }

    @Test
    fun `desktop library projection hides generated reflow books but keeps tabs open`() {
        val source = BookItem(
            id = "pdf-id",
            path = "C:/Books/source.pdf",
            type = FileType.PDF,
            displayName = "source.pdf",
            timestamp = 1L,
            title = "Source"
        )
        val reflow = desktopPdfReflowBookItem(
            sourceBook = source,
            generatedFile = File("build/test-tmp/source_reflow.html"),
            nowMillis = 2L,
            initialPageIndex = 3
        )
        val projected = SharedLibraryStateProjector().projectDesktopLibraryState(
            state = SharedReaderScreenState(
                rawLibraryBooks = listOf(source, reflow),
                openTabIds = listOf(reflow.id),
                activeTabBookId = reflow.id
            ),
            shelfRecords = emptyList(),
            shelfRefs = emptyList()
        )

        assertEquals(listOf(source.id), projected.libraryBooks.map { it.id })
        assertTrue(projected.rawLibraryBooks.any { it.id == reflow.id })
        assertTrue(projected.recentBooks.none { it.id == reflow.id })
        assertEquals(1, projected.toNonReaderLibraryOrganizationModel().allBooksCount)
        assertEquals(listOf(reflow.id), projected.openTabIds)
        assertEquals(reflow.id, projected.activeTabBookId)
    }
}
