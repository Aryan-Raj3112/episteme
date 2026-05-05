package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.aryan.reader.epub.EpubChapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageCountEstimatorTest {

    @Test
    fun `estimateChapterPageCount always returns at least one page`() {
        assertEquals(
            1,
            PageCountEstimator.estimateChapterPageCount(
                chapter = chapter(html = ""),
                constraints = Constraints(maxWidth = 0, maxHeight = 0),
                textStyle = TextStyle(fontSize = 16.sp),
                density = Density(1f)
            )
        )
    }

    @Test
    fun `estimateChapterPageCount increases as visible content grows`() {
        val constraints = Constraints(maxWidth = 400, maxHeight = 600)
        val style = TextStyle(fontSize = 16.sp)
        val density = Density(1f)

        val short = PageCountEstimator.estimateChapterPageCount(
            chapter = chapter(html = "a".repeat(400)),
            constraints = constraints,
            textStyle = style,
            density = density
        )
        val long = PageCountEstimator.estimateChapterPageCount(
            chapter = chapter(html = "a".repeat(40_000)),
            constraints = constraints,
            textStyle = style,
            density = density
        )

        assertTrue(long > short)
    }

    @Test
    fun `larger font and line height estimate more pages`() {
        val constraints = Constraints(maxWidth = 500, maxHeight = 700)
        val density = Density(1f)
        val content = chapter(html = "reader ".repeat(5000))

        val compact = PageCountEstimator.estimateChapterPageCount(
            chapter = content,
            constraints = constraints,
            textStyle = TextStyle(fontSize = 12.sp, lineHeight = 14.sp),
            density = density
        )
        val large = PageCountEstimator.estimateChapterPageCount(
            chapter = content,
            constraints = constraints,
            textStyle = TextStyle(fontSize = 24.sp, lineHeight = 32.sp),
            density = density
        )

        assertTrue(large > compact)
    }

    private fun chapter(html: String): EpubChapter {
        return EpubChapter(
            chapterId = "chapter",
            absPath = "chapter.xhtml",
            title = "Chapter",
            htmlFilePath = "chapter.xhtml",
            plainTextContent = "",
            htmlContent = html
        )
    }
}
