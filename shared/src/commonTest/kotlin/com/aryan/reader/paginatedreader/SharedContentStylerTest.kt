package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.reader.ReaderPaintOnlyColorRange
import com.aryan.reader.shared.reader.paintOnlyColorOverlayText
import com.aryan.reader.shared.reader.paintOnlyColorRanges
import com.aryan.reader.shared.reader.withoutForegroundColorSpans
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedContentStylerTest {
    @Test
    fun `paint-only colors are removed from shaping styles but retained as ranges`() {
        val source = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Serif, color = Color.Black)) {
                append("A")
                withStyle(SpanStyle(color = Color.Red)) { append("B") }
                append("C")
            }
            addStringAnnotation("URL", "chapter.xhtml#next", 0, 3)
        }

        val shapingText = source.withoutForegroundColorSpans()

        assertTrue(shapingText.spanStyles.all { !it.item.color.isSpecified })
        assertEquals(
            1,
            shapingText.spanStyles.size,
        )
        assertEquals(0, shapingText.spanStyles.single().start)
        assertEquals(3, shapingText.spanStyles.single().end)
        assertEquals(FontFamily.Serif, shapingText.spanStyles.single().item.fontFamily)
        assertEquals("chapter.xhtml#next", shapingText.getStringAnnotations("URL", 0, 3).single().item)

        val typographyChange = buildAnnotatedString {
            withStyle(SpanStyle(fontFamily = FontFamily.Serif, color = Color.Black)) {
                append("A")
                withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) { append("B") }
                append("C")
            }
        }.withoutForegroundColorSpans()
        assertEquals(3, typographyChange.spanStyles.size)
        assertEquals(
            FontWeight.Bold,
            typographyChange.spanStyles.single { it.start == 1 && it.end == 2 }.item.fontWeight
        )
        assertEquals(
            listOf(
                ReaderPaintOnlyColorRange(0, 1, Color.Black),
                ReaderPaintOnlyColorRange(1, 2, Color.Red),
                ReaderPaintOnlyColorRange(2, 3, Color.Black)
            ),
            source.paintOnlyColorRanges()
        )
    }

    @Test
    fun `paint-only overlay has no target color on the base character`() {
        val source = buildAnnotatedString {
            append("a\u0301")
            addStyle(SpanStyle(color = Color.Red), 1, 2)
        }

        val overlay = source.paintOnlyColorOverlayText(baseColor = Color.Black)

        assertEquals(source.text, overlay.text)
        assertEquals(Color.Transparent, overlay.spanStyles.first { it.start == 0 && it.end == 2 }.item.color)
        assertEquals(
            listOf(AnnotatedString.Range(SpanStyle(color = Color.Red), 1, 2)),
            overlay.spanStyles.filter { it.item.color == Color.Red }
        )
        assertTrue(
            overlay.spanStyles.none { range ->
                range.item.color == Color.Red && range.start <= 0 && range.end > 0
            }
        )
    }

    @Test
    fun assemblesSemanticTextAndMathWithInjectedPlatformAdapters() {
        val adapterCalls = mutableListOf<String>()
        val styler = SharedContentStyler(
            baseTextStyle = TextStyle(fontSize = 18.sp, color = Color.Black, fontFamily = FontFamily.Default),
            fontFamilyMap = emptyMap(),
            density = Density(1f),
            isDarkTheme = false,
            themeBackgroundColor = Color.White,
            themeTextColor = Color.Black,
            userTextAlign = null,
            paragraphGapMultiplier = 1f,
            adaptThemeColors = true,
            applyThemeToSvg = {
                adapterCalls += "theme"
                "$it-themed"
            },
            embedImagesInSvg = {
                adapterCalls += "embed"
                "$it-embedded"
            }
        )
        val paragraph = SemanticParagraph(
            text = "Read here",
            spans = listOf(
                SemanticSpan(
                    start = 5,
                    end = 9,
                    style = CssStyle(spanStyle = SpanStyle(color = Color.Blue)),
                    tag = "a",
                    linkHref = "chapter.xhtml#next",
                    elementId = "link"
                )
            ),
            style = CssStyle(paragraphStyle = androidx.compose.ui.text.ParagraphStyle(textAlign = TextAlign.Justify)),
            elementId = "p",
            cfi = "cfi",
            blockIndex = 1
        )
        val math = SemanticMath(
            svgContent = "svg",
            altText = "formula",
            style = CssStyle(),
            elementId = "math",
            cfi = null,
            svgWidth = null,
            svgHeight = null,
            svgViewBox = null,
            isFromMathJax = false,
            blockIndex = 2
        )

        val blocks = styler.style(listOf(paragraph, math))
        val styledParagraph = assertIs<ParagraphBlock>(blocks[0])
        val styledMath = assertIs<MathBlock>(blocks[1])

        assertEquals(TextAlign.Left, styledParagraph.textAlign)
        assertEquals("chapter.xhtml#next", styledParagraph.content.getStringAnnotations("URL", 0, 9).single().item)
        assertTrue(styledParagraph.content.spanStyles.any { it.start == 5 && it.end == 9 })
        assertEquals("svg-themed-embedded", styledMath.svgContent)
        assertEquals(listOf("theme", "embed"), adapterCalls)
    }
}
