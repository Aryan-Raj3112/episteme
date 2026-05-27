package com.aryan.reader.pdf

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import com.aryan.reader.pdf.data.VirtualPage
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PdfReaderRichTextTest {

    @Test
    fun `RichTextMapper toAnnotatedString clips global spans into requested local range`() {
        val document = GlobalRichDocument(
            text = "0123456789",
            spans = listOf(
                GlobalRichSpan(
                    start = 2,
                    end = 6,
                    color = Color.Red.toArgb(),
                    backgroundColor = Color.Yellow.toArgb(),
                    fontSizeNorm = 0.02f,
                    isBold = true,
                    isItalic = true,
                    isUnderline = true,
                    isStrikethrough = true
                )
            )
        )

        val annotated = RichTextMapper.toAnnotatedString(
            document = document,
            pageHeightPx = 1_000f,
            rangeStart = 4,
            rangeEnd = 8
        )

        assertEquals("4567", annotated.text)
        val range = annotated.spanStyles.single()
        assertEquals(0, range.start)
        assertEquals(2, range.end)
        assertEquals(Color.Red, range.item.color)
        assertEquals(Color.Yellow, range.item.background)
        assertEquals(20.sp, range.item.fontSize)
        assertEquals(FontWeight.Bold, range.item.fontWeight)
        assertEquals(FontStyle.Italic, range.item.fontStyle)
        assertTrue(range.item.textDecoration!!.contains(TextDecoration.Underline))
        assertTrue(range.item.textDecoration!!.contains(TextDecoration.LineThrough))
    }

    @Test
    fun `RichTextMapper toAnnotatedString clamps invalid ranges and falls back to 16sp when page height is invalid`() {
        val document = GlobalRichDocument(
            text = "abcdef",
            spans = listOf(
                GlobalRichSpan(
                    start = 0,
                    end = 6,
                    color = Color.Blue.toArgb(),
                    backgroundColor = Color.Transparent.toArgb(),
                    fontSizeNorm = 0.5f,
                    isBold = false,
                    isItalic = false,
                    isUnderline = false,
                    isStrikethrough = false
                )
            )
        )

        val empty = RichTextMapper.toAnnotatedString(document, pageHeightPx = 500f, rangeStart = 10, rangeEnd = 1)
        val full = RichTextMapper.toAnnotatedString(document, pageHeightPx = 0f, rangeStart = -10, rangeEnd = 99)

        assertEquals("", empty.text)
        assertEquals("abcdef", full.text)
        assertEquals(16.sp, full.spanStyles.single().item.fontSize)
    }

    @Test
    fun `RichTextMapper fromAnnotatedString splits overlapping styles and preserves page breaks`() {
        val text = "Hello${PAGE_BREAK_CHAR}World"
        val annotated = buildAnnotatedString {
            append(text)
            addStyle(
                SpanStyle(
                    color = Color.Black,
                    background = Color.Transparent,
                    fontSize = 20.sp
                ),
                start = 0,
                end = text.length
            )
            addStyle(
                SpanStyle(
                    color = Color.Magenta,
                    background = Color.Cyan,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    textDecoration = TextDecoration.combine(
                        listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                    )
                ),
                start = 0,
                end = 5
            )
        }

        val document = RichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 1_000f)

        assertEquals(text, document.text)
        assertEquals(2, document.spans.size)
        val first = document.spans[0]
        assertEquals(0, first.start)
        assertEquals(5, first.end)
        assertEquals(Color.Magenta.toArgb(), first.color)
        assertEquals(Color.Cyan.toArgb(), first.backgroundColor)
        assertEquals(0.024f, first.fontSizeNorm, 0.0001f)
        assertTrue(first.isBold)
        assertTrue(first.isItalic)
        assertTrue(first.isUnderline)
        assertTrue(first.isStrikethrough)
        val second = document.spans[1]
        assertEquals(5, second.start)
        assertEquals(text.length, second.end)
        assertEquals(Color.Black.toArgb(), second.color)
        assertFalse(second.isBold)
    }

    @Test
    fun `RichTextMapper fromAnnotatedString merges adjacent identical effective styles`() {
        val annotated = buildAnnotatedString {
            append("abcd")
            val style = SpanStyle(
                color = Color.Green,
                background = Color.Transparent,
                fontSize = 18.sp
            )
            addStyle(style, start = 0, end = 2)
            addStyle(style, start = 2, end = 4)
        }

        val document = RichTextMapper.fromAnnotatedString(annotated, pageHeightPx = 900f)

        assertEquals(1, document.spans.size)
        val span = document.spans.single()
        assertEquals(0, span.start)
        assertEquals(4, span.end)
        assertEquals(Color.Green.toArgb(), span.color)
        assertEquals(Color.Transparent.toArgb(), span.backgroundColor)
        assertEquals(0.02f, span.fontSizeNorm, 0.0001f)
        assertFalse(span.isBold)
        assertFalse(span.isItalic)
        assertFalse(span.isUnderline)
        assertFalse(span.isStrikethrough)
    }

    @Test
    fun `RichTextMapper fromAnnotatedString returns empty rich document for empty text`() {
        assertEquals(
            GlobalRichDocument("", emptyList()),
            RichTextMapper.fromAnnotatedString(AnnotatedString(""), pageHeightPx = 1_000f)
        )
    }

    @Test
    fun `hasRenderableRichText ignores whitespace and explicit page breaks`() {
        assertFalse(" \n\t${PAGE_BREAK_CHAR}".hasRenderableRichText())
        assertTrue("${PAGE_BREAK_CHAR}\nVisible".hasRenderableRichText())
    }

    @Test
    fun `selection bounds normalize reversed and clamped rich text selections`() {
        assertEquals(44 to 45, androidPdfRichTextSelectionBounds(45, 44, textLength = 45))
        assertEquals(0 to 5, androidPdfRichTextSelectionBounds(-3, 99, textLength = 5))
        assertEquals(null, androidPdfRichTextSelectionBounds(3, 3, textLength = 5))
    }

    @Test
    fun `blank page insertion uses one page break when the rich text boundary is already explicit`() {
        val text = "Page 1${PAGE_BREAK_CHAR}Page 2"
        val insertionIndex = "Page 1${PAGE_BREAK_CHAR}".length

        assertEquals(1, androidRichTextBlankInsertBreakCount(text, insertionIndex))
        assertEquals(1, androidRichTextBlankInsertBreakCount("Page 1", "Page 1".length))
        assertEquals(1, androidRichTextBlankInsertBreakCount("Page 1", 0))
    }

    @Test
    fun `blank page insertion uses two page breaks only for measured text boundaries with following content`() {
        val text = "Page 1Page 2"
        val insertionIndex = "Page 1".length

        assertEquals(2, androidRichTextBlankInsertBreakCount(text, insertionIndex))
        assertEquals(
            insertionIndex,
            androidRichTextInsertionIndexForPage(
                insertPageIndex = 1,
                pageLayouts = listOf(
                    PageTextLayout(
                        pageIndex = 0,
                        visibleText = AnnotatedString("Page 1"),
                        globalStartIndex = 0,
                        globalEndIndex = insertionIndex,
                        pageHeightPx = 1_000f
                    ),
                    PageTextLayout(
                        pageIndex = 1,
                        visibleText = AnnotatedString("Page 2"),
                        globalStartIndex = insertionIndex,
                        globalEndIndex = text.length,
                        pageHeightPx = 1_000f
                    )
                ),
                textLength = text.length
            )
        )
    }

    @Test
    fun `rich text remap keeps later text on same pdf page when inserting a blank page`() {
        val currentLayout = listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1))
        val updatedLayout = listOf(
            VirtualPage.PdfPage(0),
            VirtualPage.BlankPage("blank", 612, 792, wasManuallyAdded = true),
            VirtualPage.PdfPage(1)
        )
        val pageLayouts = listOf(
            PageTextLayout(
                pageIndex = 0,
                visibleText = AnnotatedString("Page 1$PAGE_BREAK_CHAR"),
                globalStartIndex = 0,
                globalEndIndex = 7,
                pageHeightPx = 1_000f
            ),
            PageTextLayout(
                pageIndex = 1,
                visibleText = AnnotatedString("Page 2"),
                globalStartIndex = 7,
                globalEndIndex = 13,
                pageHeightPx = 1_000f
            )
        )

        val remapped = remapAndroidRichTextForLayoutChange(currentLayout, updatedLayout, pageLayouts)

        assertEquals("Page 1${PAGE_BREAK_CHAR}${PAGE_BREAK_CHAR}Page 2", remapped.text)
    }

    @Test
    fun `rich text remap drops deleted blank page and shifts later text back`() {
        val currentLayout = listOf(
            VirtualPage.PdfPage(0),
            VirtualPage.BlankPage("blank", 612, 792, wasManuallyAdded = true),
            VirtualPage.PdfPage(1)
        )
        val updatedLayout = listOf(VirtualPage.PdfPage(0), VirtualPage.PdfPage(1))
        val pageLayouts = listOf(
            PageTextLayout(0, AnnotatedString("A$PAGE_BREAK_CHAR"), 0, 2, 1_000f),
            PageTextLayout(1, AnnotatedString("$PAGE_BREAK_CHAR"), 2, 3, 1_000f),
            PageTextLayout(2, AnnotatedString("B"), 3, 4, 1_000f)
        )

        val remapped = remapAndroidRichTextForLayoutChange(currentLayout, updatedLayout, pageLayouts)

        assertEquals("A${PAGE_BREAK_CHAR}B", remapped.text)
    }

    @Test
    fun `PdfRichTextRepository saves and loads rich document with sanitized book id`() = runTest {
        val context = contextWithFilesDir(tempRoot("rich-save-load"))
        val repository = PdfRichTextRepository(context)
        val document = GlobalRichDocument(
            text = "Saved rich text",
            spans = listOf(
                GlobalRichSpan(
                    start = 0,
                    end = 5,
                    color = Color.Red.toArgb(),
                    backgroundColor = Color.Transparent.toArgb(),
                    fontSizeNorm = 0.018f,
                    isBold = true,
                    isItalic = false,
                    isUnderline = true,
                    isStrikethrough = false,
                    fontPath = "asset:fonts/lora.ttf"
                )
            )
        )

        repository.save("folder/book:name?.pdf", document)

        val file = repository.getFileForSync("folder/book:name?.pdf")
        assertTrue(file.name.matches(Regex("rich_doc_folder_book_name_\\.pdf\\.json")))
        assertTrue(file.exists())
        assertEquals(document, repository.document.value)

        val reloaded = PdfRichTextRepository(context)
        reloaded.load("folder/book:name?.pdf")
        assertEquals(document, reloaded.document.value)
    }

    @Test
    fun `PdfRichTextRepository load returns empty document for missing and corrupt files`() = runTest {
        val context = contextWithFilesDir(tempRoot("rich-corrupt"))
        val repository = PdfRichTextRepository(context)

        repository.load("missing")
        assertEquals(GlobalRichDocument("", emptyList()), repository.document.value)

        repository.getFileForSync("corrupt").writeText("{not json")
        repository.load("corrupt")

        assertEquals(GlobalRichDocument("", emptyList()), repository.document.value)
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
