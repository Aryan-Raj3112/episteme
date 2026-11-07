package com.aryan.reader.shared.reader

import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.lineHeightWithRubyReserve
import com.aryan.reader.paginatedreader.resolveReaderBlockFontFamily
import com.aryan.reader.paginatedreader.resolveReaderBlockFontWeight
import com.aryan.reader.shared.sharedNativeFontSizeSp
import com.aryan.reader.shared.sharedPaginationLineHeightMultiplier
import com.aryan.reader.shared.ui.sharedNativeParagraphStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Android-parity guards for block text styling in the shared paginated EPUB
 * path. Android styles each block with the merged book CSS over the reader
 * base as a whole-text span (`SharedContentStyler.buildAnnotatedString`) and
 * scales headers from the reader base (`createHeaderTextStyle`); the shared
 * measurement (`SemanticTextBlock.textStyle`) and rendering
 * (`renderedTextStyle`) must resolve size, leading, weight, style and family
 * with the same priorities or pages break differently from Android.
 */
class SharedReaderStyleParityTest {

    private fun settings(
        fontSize: Int = 20,
        lineSpacing: Float = 1.5f,
        fontWeight: Int = 0
    ) = ReaderSettings(
        fontSize = fontSize,
        lineSpacing = lineSpacing,
        fontWeight = fontWeight,
        readingMode = ReaderReadingMode.PAGINATED
    )

    private fun baseStyle(s: ReaderSettings, fontFamily: FontFamily = FontFamily.Default) =
        measurementBaseStyle(settings = s, fontFamily = fontFamily)

    private fun header(style: CssStyle, level: Int = 1) = SemanticHeader(
        level = level,
        text = "Title",
        spans = emptyList(),
        style = style,
        elementId = null,
        cfi = null,
        startCharOffsetInSource = 0,
        blockIndex = 0
    )

    private fun paragraph(style: CssStyle) = SemanticParagraph(
        text = "Body",
        spans = emptyList(),
        style = style,
        elementId = null,
        cfi = null,
        startCharOffsetInSource = 0,
        blockIndex = 0
    )

    // Weight priority (Android: publication wins, user fills silence, headers
    // ignore the user weight and fall back to Bold).

    @Test
    fun `block weight prefers css over user weight`() {
        assertEquals(
            FontWeight.Bold,
            resolveReaderBlockFontWeight(FontWeight.Bold, FontWeight(300), isHeader = false)
        )
        assertEquals(
            FontWeight(600),
            resolveReaderBlockFontWeight(FontWeight(600), FontWeight(300), isHeader = true)
        )
    }

    @Test
    fun `block weight falls back to user weight for body and bold for headers`() {
        assertEquals(
            FontWeight(300),
            resolveReaderBlockFontWeight(null, FontWeight(300), isHeader = false)
        )
        assertEquals(
            FontWeight.Bold,
            resolveReaderBlockFontWeight(null, FontWeight(300), isHeader = true)
        )
        assertNull(resolveReaderBlockFontWeight(null, null, isHeader = false))
    }

    @Test
    fun `measured header ignores user weight but honors css weight`() {
        val s = settings(fontWeight = 300)
        val base = baseStyle(s)

        val plain = header(CssStyle()).textStyle(base, s)
        assertEquals(FontWeight.Bold, plain.fontWeight)

        val css = header(CssStyle(spanStyle = SpanStyle(fontWeight = FontWeight(600))))
        assertEquals(FontWeight(600), css.textStyle(base, s).fontWeight)
    }

    @Test
    fun `measured body prefers css weight over user weight`() {
        val s = settings(fontWeight = 300)
        val base = baseStyle(s)

        assertEquals(FontWeight(300), paragraph(CssStyle()).textStyle(base, s).fontWeight)
        val css = paragraph(CssStyle(spanStyle = SpanStyle(fontWeight = FontWeight.Bold)))
        assertEquals(FontWeight.Bold, css.textStyle(base, s).fontWeight)
    }

    // Default leading (Android: CSS line-height or the reader base; never the
    // block size scaled, even for headers).

    @Test
    fun `measured leading uses reader base instead of scaled block size`() {
        val s = settings(fontSize = 20, lineSpacing = 1.5f)
        val base = baseStyle(s)

        // h1 with UA 2em size (36sp at the nominal baseline): the font stays
        // large but the leading is the level-scaled native reader base, like
        // Android's `createHeaderTextStyle` draws (base x h1 scale) — never the
        // block size scaled, and never the unscaled base under a scaled header,
        // which overlaps wrapped header lines that Android draws cleanly.
        val native20 = s.sharedNativeFontSizeSp()
        val readerBaseLeading = native20 * sharedPaginationLineHeightMultiplier(1.5f)
        val h1 = header(CssStyle(fontSize = 36.sp))
        val h1Style = h1.textStyle(base, s)
        assertEquals(36.sp, h1Style.fontSize)
        assertEquals(readerBaseLeading * sharedHeadingFontScale(1), h1Style.lineHeight.value, 0.0001f)

        val bigBody = paragraph(CssStyle(fontSize = 36.sp))
        val bigStyle = bigBody.textStyle(base, s)
        assertEquals(36.sp, bigStyle.fontSize)
        assertEquals(readerBaseLeading, bigStyle.lineHeight.value, 0.0001f)
    }

    @Test
    fun `measured leading honors explicit css line height`() {
        val s = settings(fontSize = 20, lineSpacing = 1.5f)
        val base = baseStyle(s)

        val fixed = paragraph(CssStyle(fontSize = 36.sp, paragraphStyle = ParagraphStyle(lineHeight = 40.sp)))
        assertEquals(40.sp, fixed.textStyle(base, s).lineHeight)

        // 2em resolves against the native reader size (stored 20 -> native), like Android.
        val relative = paragraph(CssStyle(paragraphStyle = ParagraphStyle(lineHeight = 2.em)))
        assertEquals((s.sharedNativeFontSizeSp() * 2f).sp, relative.textStyle(base, s).lineHeight)
    }

    // Font style and family.

    @Test
    fun `measured style carries block italic`() {
        val s = settings()
        val base = baseStyle(s)

        val italic = paragraph(CssStyle(spanStyle = SpanStyle(fontStyle = FontStyle.Italic)))
        assertEquals(FontStyle.Italic, italic.textStyle(base, s).fontStyle)
        assertNull(paragraph(CssStyle()).textStyle(base, s).fontStyle)
    }

    @Test
    fun `block family prefers picked reader typeface except monospace`() {
        assertEquals(FontFamily.Serif, resolveReaderBlockFontFamily(FontFamily.Serif, FontFamily.Default))
        assertEquals(FontFamily.SansSerif, resolveReaderBlockFontFamily(FontFamily.Serif, FontFamily.SansSerif))
        assertEquals(FontFamily.Monospace, resolveReaderBlockFontFamily(FontFamily.Monospace, FontFamily.SansSerif))
        assertEquals(FontFamily.Default, resolveReaderBlockFontFamily(null, FontFamily.Default))
        assertEquals(FontFamily.SansSerif, resolveReaderBlockFontFamily(null, FontFamily.SansSerif))
    }

    @Test
    fun `measured family follows picked reader typeface except monospace`() {
        val s = settings()
        val cssSerif = CssStyle(spanStyle = SpanStyle(fontFamily = FontFamily.Serif))
        val cssMono = CssStyle(spanStyle = SpanStyle(fontFamily = FontFamily.Monospace))

        assertEquals(FontFamily.Serif, paragraph(cssSerif).textStyle(baseStyle(s), s).fontFamily)
        assertEquals(
            FontFamily.SansSerif,
            paragraph(cssSerif).textStyle(baseStyle(s, FontFamily.SansSerif), s).fontFamily
        )
        assertEquals(
            FontFamily.Monospace,
            paragraph(cssMono).textStyle(baseStyle(s, FontFamily.SansSerif), s).fontFamily
        )
    }

    // Line breaking and baseline shift.

    @Test
    fun `render paragraph style uses android simple line breaking`() {
        val block = paragraph(CssStyle())
        assertEquals(LineBreak.Simple, block.sharedNativeParagraphStyle(TextAlign.Start).lineBreak)
        assertEquals(
            TextAlign.Start,
            block.sharedNativeParagraphStyle(TextAlign.Start).textAlign
        )
        val justified = paragraph(
            CssStyle(paragraphStyle = ParagraphStyle(textAlign = TextAlign.Justify))
        )
        assertEquals(
            TextAlign.Left,
            justified.sharedNativeParagraphStyle(TextAlign.Start).textAlign
        )
    }

    @Test
    fun `measured spans shift sub and sup like rendering`() {
        assertEquals(
            BaselineShift.Subscript,
            CssStyle().toMeasurementSpanStyle(20f, "sub").baselineShift
        )
        assertEquals(
            BaselineShift.Superscript,
            CssStyle().toMeasurementSpanStyle(20f, "sup").baselineShift
        )
        assertNull(CssStyle().toMeasurementSpanStyle(20f, "span").baselineShift)
        assertEquals(
            BaselineShift.Superscript,
            CssStyle(spanStyle = SpanStyle(baselineShift = BaselineShift.Superscript))
                .toMeasurementSpanStyle(20f, "span").baselineShift
        )
    }

    @Test
    fun `ruby reserve grows vertical leading only`() {
        assertEquals(30.sp, lineHeightWithRubyReserve(30.sp, 20.sp, hasRuby = true, isVertical = false))
        assertEquals(30.sp, lineHeightWithRubyReserve(30.sp, 20.sp, hasRuby = false, isVertical = true))
        assertEquals(31.sp, lineHeightWithRubyReserve(30.sp, 20.sp, hasRuby = true, isVertical = true))
    }
}
