package com.aryan.reader.shared.reader

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Android-parity guards for the shared measured paginator (iOS paginated
 * EPUB path): measurement must use the same text metrics as rendering, and
 * both must match the Android benchmark (`PaginatedReader.layoutTextStyle`).
 */
class MeasurementBaseStyleParityTest {

    @Test
    fun `measurement paragraph style uses android simple line breaking`() {
        val paragraph = SemanticParagraph(
            text = "Breaking parity",
            spans = emptyList(),
            style = CssStyle(),
            elementId = null,
            cfi = null,
            startCharOffsetInSource = 0,
            blockIndex = 1
        )

        assertEquals(
            LineBreak.Simple,
            paragraph.toMeasurementParagraphStyleForPagination(TextAlign.Start).lineBreak
        )
    }

    @Test
    fun `measurement base style carries user spacing weight and android breaking`() {
        val base = measurementBaseStyle(
            settings = ReaderSettings(
                fontSize = 20,
                lineSpacing = 1.5f,
                fontWeight = 600,
                letterSpacing = 0.04f,
                readingMode = ReaderReadingMode.PAGINATED
            ),
            fontFamily = FontFamily.Serif
        )

        assertEquals(20.sp, base.fontSize)
        assertEquals(30.sp, base.lineHeight)
        assertEquals(FontFamily.Serif, base.fontFamily)
        assertEquals(FontWeight(600), base.fontWeight)
        assertEquals(0.04f.em, base.letterSpacing)
        assertEquals(LineBreak.Simple, base.lineBreak)
    }

    @Test
    fun `measurement base style defaults to no weight and zero spacing`() {
        val base = measurementBaseStyle(
            settings = ReaderSettings(readingMode = ReaderReadingMode.PAGINATED),
            fontFamily = FontFamily.Default
        )

        assertEquals(null, base.fontWeight)
        assertEquals(0f.em, base.letterSpacing)
        assertEquals(LineBreak.Simple, base.lineBreak)
    }

    @Test
    fun `measurement keeps android justify downgrade unless explicitly forced`() {
        val paragraph = SemanticParagraph(
            text = "Justified paragraph",
            spans = emptyList(),
            style = CssStyle(
                paragraphStyle = androidx.compose.ui.text.ParagraphStyle(
                    textAlign = TextAlign.Justify
                )
            ),
            elementId = null,
            cfi = null,
            startCharOffsetInSource = 0,
            blockIndex = 1
        )

        assertEquals(
            TextAlign.Left,
            paragraph.toMeasurementParagraphStyleForPagination(TextAlign.Start).textAlign
        )
        assertEquals(
            TextAlign.Justify,
            paragraph.toMeasurementParagraphStyleForPagination(TextAlign.Justify).textAlign
        )
    }
}
