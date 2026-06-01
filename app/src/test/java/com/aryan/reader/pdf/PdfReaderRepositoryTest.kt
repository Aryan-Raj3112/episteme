package com.aryan.reader.pdf

import android.content.Context
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import com.aryan.reader.pdf.data.PageLayoutRepository
import com.aryan.reader.pdf.data.PdfAnnotation
import com.aryan.reader.pdf.data.PdfAnnotationRepository
import com.aryan.reader.pdf.data.PdfHighlightRepository
import com.aryan.reader.pdf.data.PdfTextBox
import com.aryan.reader.pdf.data.PdfTextBoxRepository
import com.aryan.reader.pdf.data.VirtualPage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfReaderRepositoryTest {

    @Test
    fun `PdfAnnotationRepository saves loads and exposes non empty sync file`() = runTest {
        val context = contextWithFilesDir(tempRoot("annotation"))
        val repository = PdfAnnotationRepository(context)
        val annotations = mapOf(
            1 to listOf(
                PdfAnnotation(
                    type = AnnotationType.INK,
                    inkType = InkType.PEN,
                    pageIndex = 1,
                    points = listOf(PdfPoint(0.1f, 0.2f, 123L)),
                    color = Color.Red,
                    strokeWidth = 0.01f
                )
            )
        )

        repository.saveAnnotations("folder/book.pdf", annotations)

        val loaded = repository.loadAnnotations("folder/book.pdf")
        assertEquals(1, loaded.getValue(1).single().pageIndex)
        assertEquals(InkType.PEN, loaded.getValue(1).single().inkType)
        assertNotNull(repository.getAnnotationFileForSync("folder/book.pdf"))
        assertTrue(File(context.filesDir, "annotations/annotation_folder_book.pdf.json").exists())
    }

    @Test
    fun `PdfAnnotationRepository keeps empty save as no syncable file`() = runTest {
        val context = contextWithFilesDir(tempRoot("annotation-empty"))
        val repository = PdfAnnotationRepository(context)

        repository.saveAnnotations("book", emptyMap())

        assertEquals(emptyMap<Int, List<PdfAnnotation>>(), repository.loadAnnotations("book"))
        assertNull(repository.getAnnotationFileForSync("book"))
    }

    @Test
    fun `PdfAnnotationRepository does not rewrite unchanged annotation file`() = runTest {
        val context = contextWithFilesDir(tempRoot("annotation-noop"))
        val repository = PdfAnnotationRepository(context)
        val annotations = mapOf(
            0 to listOf(
                PdfAnnotation(
                    type = AnnotationType.INK,
                    inkType = InkType.PEN,
                    pageIndex = 0,
                    points = listOf(PdfPoint(0.1f, 0.2f, 123L)),
                    color = Color.Blue,
                    strokeWidth = 0.01f
                )
            )
        )

        repository.saveAnnotations("book", annotations)
        val file = requireNotNull(repository.getAnnotationFileForSync("book"))
        val previousModified = 1_700_000_000_000L
        assertTrue(file.setLastModified(previousModified))

        repository.saveAnnotations("book", annotations)

        assertEquals(previousModified, file.lastModified())
    }

    @Test
    fun `PdfAnnotationRepository stores deleted annotation tombstones for sync`() = runTest {
        val context = contextWithFilesDir(tempRoot("annotation-deleted"))
        val repository = PdfAnnotationRepository(context)

        repository.markAnnotationsDeleted("book", listOf("old-ink"), deletedAt = 123L)

        val file = requireNotNull(repository.getDeletedAnnotationsFileForSync("book"))
        assertTrue(file.readText().contains("old-ink"))
        assertTrue(file.readText().contains("123"))
    }

    @Test
    fun `PdfHighlightRepository saves loads deletes empty highlights and clears all`() = runTest {
        val context = contextWithFilesDir(tempRoot("highlights"))
        val repository = PdfHighlightRepository(context)
        val highlight = PdfUserHighlight(
            id = "h1",
            pageIndex = 2,
            bounds = emptyList(),
            color = PdfHighlightColor.GREEN,
            text = "quote",
            range = 5 to 10
        )

        repository.saveHighlights("book/one", listOf(highlight))
        assertEquals(listOf(highlight), repository.loadHighlights("book/one"))
        assertTrue(repository.getFileForSync("book/one").exists())

        repository.saveHighlights("book/one", emptyList())
        assertEquals(emptyList<PdfUserHighlight>(), repository.loadHighlights("book/one"))
        assertFalse(repository.getFileForSync("book/one").exists())

        repository.saveHighlights("book/two", listOf(highlight.copy(id = "h2")))
        repository.clearAll()
        assertFalse(File(context.filesDir, "pdf_highlights").exists())
    }

    @Test
    fun `PdfHighlightRepository does not rewrite unchanged highlight file`() = runTest {
        val context = contextWithFilesDir(tempRoot("highlights-noop"))
        val repository = PdfHighlightRepository(context)
        val highlight = PdfUserHighlight(
            id = "h1",
            pageIndex = 2,
            bounds = emptyList(),
            color = PdfHighlightColor.GREEN,
            text = "quote",
            range = 5 to 10
        )

        repository.saveHighlights("book", listOf(highlight))
        val file = repository.getFileForSync("book")
        val previousModified = 1_700_000_000_000L
        assertTrue(file.setLastModified(previousModified))

        repository.saveHighlights("book", listOf(highlight))

        assertEquals(previousModified, file.lastModified())
    }

    @Test
    fun `PdfTextBoxRepository saves loads deletes and clears files`() = runTest {
        val context = contextWithFilesDir(tempRoot("textboxes"))
        val repository = PdfTextBoxRepository(context)
        val box = PdfTextBox(
            id = "box",
            pageIndex = 0,
            relativeBounds = Rect(0.1f, 0.2f, 0.3f, 0.4f),
            text = "Text box",
            color = Color.Black,
            backgroundColor = Color.White,
            fontSize = 16f
        )

        repository.saveTextBoxes("book/one", listOf(box))
        assertEquals(listOf(box), repository.loadTextBoxes("book/one"))
        assertTrue(repository.getFileForSync("book/one").exists())

        repository.deleteForBook("book/one")
        assertEquals(emptyList<PdfTextBox>(), repository.loadTextBoxes("book/one"))

        repository.saveTextBoxes("book/two", listOf(box.copy(id = "box2")))
        repository.clearAll()
        assertTrue(File(context.filesDir, "textboxes").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `PdfTextBoxRepository does not rewrite unchanged textbox file`() = runTest {
        val context = contextWithFilesDir(tempRoot("textboxes-noop"))
        val repository = PdfTextBoxRepository(context)
        val box = PdfTextBox(
            id = "box",
            pageIndex = 0,
            relativeBounds = Rect(0.1f, 0.2f, 0.3f, 0.4f),
            text = "Text box",
            color = Color.Black,
            backgroundColor = Color.White,
            fontSize = 16f
        )

        repository.saveTextBoxes("book", listOf(box))
        val file = repository.getFileForSync("book")
        val previousModified = 1_700_000_000_000L
        assertTrue(file.setLastModified(previousModified))

        repository.saveTextBoxes("book", listOf(box))

        assertEquals(previousModified, file.lastModified())
    }

    @Test
    fun `PageLayoutRepository returns default pdf pages when no layout exists`() = runTest {
        val repository = PageLayoutRepository(contextWithFilesDir(tempRoot("layout-default")))

        assertEquals(
            listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1), VirtualPage.PdfPage(2)),
            repository.loadLayout("missing", totalPdfPages = 3)
        )
        assertNull(repository.getLayoutOrNull("missing"))
    }

    @Test
    fun `PageLayoutRepository round trips pdf and blank virtual pages`() = runTest {
        val context = contextWithFilesDir(tempRoot("layout"))
        val repository = PageLayoutRepository(context)
        val pages = listOf(
            VirtualPage.PdfPage(0),
            VirtualPage.BlankPage(id = "blank-1", width = 612, height = 792, wasManuallyAdded = true),
            VirtualPage.PdfPage(3)
        )

        repository.saveLayout("folder/book.pdf", pages)

        assertEquals(pages, repository.loadLayout("folder/book.pdf", totalPdfPages = 10))
        assertEquals(pages, repository.getLayoutOrNull("folder/book.pdf"))
        assertTrue(File(context.filesDir, "page_layouts/layout_folder_book.pdf.json").exists())
    }

    @Test
    fun `PageLayoutRepository falls back for corrupt loadLayout but returns null for corrupt optional lookup`() = runTest {
        val context = contextWithFilesDir(tempRoot("layout-corrupt"))
        val repository = PageLayoutRepository(context)
        repository.getLayoutFile("book").writeText("not json")

        assertEquals(
            listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1)),
            repository.loadLayout("book", totalPdfPages = 2)
        )
        assertNull(repository.getLayoutOrNull("book"))
    }

    private fun contextWithFilesDir(filesDir: File): Context {
        val context = mockk<Context>()
        every { context.filesDir } returns filesDir
        return context
    }

    private fun tempRoot(name: String): File {
        return File("build/test-tmp/pdf-reader/$name-${System.nanoTime()}").apply { mkdirs() }
    }
}
