package com.aryan.reader.shared.reader

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.shared.SharedNativePaginationNormalLineHeightMultiplier
import com.aryan.reader.shared.sharedPaginationBaseLineHeightSp
import com.aryan.reader.shared.sharedPaginationLineHeightMultiplier
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
        // Native pagination leading: 1.5x stores as the slider position, not the CSS
        // value, exactly as Android's Compose pagination renders it.
        assertEquals(
            20f * sharedPaginationLineHeightMultiplier(1.5f),
            base.lineHeight.value,
            0.0001f
        )
        assertEquals(FontFamily.Serif, base.fontFamily)
        assertEquals(FontWeight(600), base.fontWeight)
        assertEquals(0.04f.em, base.letterSpacing)
        assertEquals(LineBreak.Simple, base.lineBreak)
    }

    @Test
    fun `measurement base style uses android normal pagination leading at the default spacing`() {
        // Stored 1.45 (the reader's normal CSS line height) must measure as the 1.2x
        // Compose multiplier Android's native pagination draws with, or shared
        // pagination fits about a fifth fewer lines per page than Android.
        val base = measurementBaseStyle(
            settings = ReaderSettings(fontSize = 20, readingMode = ReaderReadingMode.PAGINATED),
            fontFamily = FontFamily.Default
        )

        assertEquals(24.sp, base.lineHeight)
        assertEquals(20f * SharedNativePaginationNormalLineHeightMultiplier, base.lineHeight.value, 0.0001f)
        // The same base leading feeds the fallbacks measurement and rendering use
        // when a block's own style carries no line height.
        val scaled = ReaderSettings(fontSize = 36, readingMode = ReaderReadingMode.PAGINATED)
        assertEquals(36f * 1.2f, scaled.sharedPaginationBaseLineHeightSp(), 0.0001f)
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
