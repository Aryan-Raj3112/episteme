package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SharedContentStylerTest {
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
