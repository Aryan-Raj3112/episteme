package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ContentStylingPoliciesTest {
    @Test
    fun underlineAndWordSpacingPoliciesMatchAndroidEncoding() {
        val css = CssStyle(
            textDecorationStyle = "dotted",
            textDecorationColor = Color.Red,
            textUnderlineOffset = 2.dp,
            wordSpacing = 0.2.em
        )
        val policy = readerCustomUnderlinePolicy(
            SpanStyle(textDecoration = TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))),
            css
        )
        assertEquals(TextDecoration.LineThrough, policy.spanStyle.textDecoration)
        assertEquals("dotted|${Color.Red.value}|2.0", policy.annotationData)
        assertEquals(listOf(1, 3), readerWordSpacingOffsets("a b c", 0, 4, css))
        assertEquals(emptyList(), readerWordSpacingOffsets("a b", -10, 99, css.copy(wordSpacing = 0.em)))

        val unchanged = SpanStyle(textDecoration = TextDecoration.Underline)
        assertEquals(unchanged, readerCustomUnderlinePolicy(unchanged, CssStyle()).spanStyle)
        assertEquals(null, readerCustomUnderlinePolicy(unchanged, CssStyle()).annotationData)
    }

    @Test
    fun paragraphAndFontPoliciesMatchAndroidPrecedence() {
        val paragraph = resolveReaderParagraphStyle(
            baseTextStyle = TextStyle(lineHeight = 20.sp),
            cssStyle = CssStyle(
                paragraphStyle = ParagraphStyle(
                    textAlign = TextAlign.Justify,
                    textDirection = TextDirection.Unspecified,
                    lineHeight = 12.sp
                ),
                hyphens = "auto"
            ),
            isParagraph = true,
            userTextAlign = null
        )
        assertEquals(TextAlign.Left, paragraph.textAlign)
        assertEquals(TextDirection.ContentOrLtr, paragraph.textDirection)
        // Publication line-height wins unless the reader explicitly overrides it.
        assertEquals(12.sp, paragraph.lineHeight)
        assertEquals(Hyphens.Auto, paragraph.hyphens)
        assertEquals(LineBreak.Simple, paragraph.lineBreak)

        val overriddenParagraph = resolveReaderParagraphStyle(
            baseTextStyle = TextStyle(lineHeight = 20.sp),
            cssStyle = CssStyle(
                paragraphStyle = ParagraphStyle(
                    textAlign = TextAlign.Justify,
                    textDirection = TextDirection.Unspecified,
                    lineHeight = 12.sp
                ),
                hyphens = "auto"
            ),
            isParagraph = true,
            userTextAlign = null,
            honorUserLineHeight = true
        )
        assertEquals(20.sp, overriddenParagraph.lineHeight)

        assertEquals(FontFamily.Monospace, resolveReaderBlockFontFamily(FontFamily.Monospace, FontFamily.Serif))
        assertEquals(FontFamily.Serif, resolveReaderBlockFontFamily(FontFamily.SansSerif, FontFamily.Serif))
        assertEquals(FontFamily.SansSerif, resolveReaderBlockFontFamily(FontFamily.SansSerif, FontFamily.Default))
        assertEquals(null, resolveReaderSpanFontFamily(null, FontFamily.Default))
        assertEquals(BaselineShift.Subscript, readerBaselineShift("sub"))
        assertEquals(BaselineShift.Superscript, readerBaselineShift("sup"))
        assertEquals(null, readerBaselineShift("span"))
    }

    @Test
    fun mathSvgPolicyPreservesAndroidTransformationOrderAndMathJaxBypass() {
        val calls = mutableListOf<String>()
        val themed = readerMathSvgContent("svg", false, true, {
            calls += "theme"
            "$it-themed"
        }, {
            calls += "embed"
            "$it-embedded"
        })
        assertEquals("svg-themed-embedded", themed)
        assertEquals(listOf("theme", "embed"), calls)

        calls.clear()
        assertEquals("svg", readerMathSvgContent("svg", true, true, { error("theme") }, { error("embed") }))
        assertEquals(emptyList(), calls)
        assertEquals("svg-embedded", readerMathSvgContent("svg", false, false, { error("theme") }, { "$it-embedded" }))
        assertEquals(" ", readerMathSvgContent(" ", false, true, { error("theme") }, { error("embed") }))
    }

    @Test
    fun chantPolicyPreservesNonbreakingAndDropCapSemantics() {
        fun paragraph(text: String, index: Int) = SemanticParagraph(text, emptyList(), CssStyle(), null, null, blockIndex = index)
        fun unit(marker: String, children: List<SemanticBlock>, index: Int) = SemanticFlexContainer(
            children,
            CssStyle(blockStyle = BlockStyle(display = marker)),
            null,
            null,
            index
        )
        val regular = unit("reader-chant-unit:before", listOf(paragraph("neume", 1), paragraph("lyric", 2)), 10)
        val dropCap = unit("reader-chant-dropcap", listOf(paragraph("D", 3)), 11)
        val group = unit("reader-chant-nonbreaking", listOf(regular, dropCap), 12)
        val root = unit("reader-chant-flow", listOf(group), 13)

        val units = readerChantUnits(root) { semantic ->
            (semantic as? SemanticParagraph)?.let { ParagraphBlock(AnnotatedString(it.text), blockIndex = it.blockIndex) }
        }
        assertEquals(2, units.size)
        assertEquals("neume", units[0].neume.text)
        assertEquals("lyric", units[0].lyric.text)
        assertEquals(true, units[0].keepWithNext)
        assertEquals(true, units[0].underlineBefore)
        assertEquals("", units[1].neume.text)
        assertEquals("D", units[1].lyric.text)
        assertEquals(true, units[1].isDropCap)
    }

    @Test
    fun paragraphAndImageBlockPoliciesMatchAndroid() {
        val style = CssStyle(
            paragraphStyle = ParagraphStyle(textAlign = TextAlign.Center),
            blockStyle = BlockStyle(
                margin = BoxBorders(top = 4.dp, right = 3.dp, bottom = 6.dp, left = 2.dp),
                filter = "invert(100%)"
            )
        )
        val paragraph = readerContentBlockStyle(style, true, 1.5f)
        assertEquals(6.dp, paragraph.margin.top)
        assertEquals(9.dp, paragraph.margin.bottom)
        assertEquals(3.dp, paragraph.margin.right)
        assertEquals("center", readerImageBlockStyle(style).horizontalAlign)
        assertEquals(true, shouldInvertReaderImage(style))
        assertEquals(style.blockStyle, readerContentBlockStyle(style, false, 99f))
    }

    @Test
    fun emphasisLineHeightUsesAndroidFallbackAndMultiplier() {
        val plain = AnnotatedString("plain")
        assertEquals(plain, plain.adjustReaderLineHeightForEmphasis())

        val unspecified = buildAnnotatedString {
            append("marked")
            addStringAnnotation("TextEmphasis", "dot", 0, 6)
        }.adjustReaderLineHeightForEmphasis()
        assertEquals(1.8.em, unspecified.paragraphStyles.first().item.lineHeight)

        val existing = buildAnnotatedString {
            withStyle(ParagraphStyle(lineHeight = 2.em)) {
                append("marked")
                addStringAnnotation("TextEmphasis", "dot", 0, 6)
            }
        }.adjustReaderLineHeightForEmphasis()
        assertEquals(2.6.em, existing.paragraphStyles.first().item.lineHeight)
    }

    @Test
    fun initialStylingThemeAdaptationMatchesAndroidScope() {
        val source = CssStyle(
            spanStyle = SpanStyle(color = Color.Black),
            blockStyle = BlockStyle(
                backgroundColor = Color.White,
                borderBottom = BorderStyle(1.dp, Color.Black)
            ),
            textDecorationColor = Color.Black,
            textEmphasis = TextEmphasis(color = Color.Black)
        )
        assertEquals(
            source,
            applyReaderThemeDuringContentStyling(source, false, true, Color.Black, Color.White)
        )

        val themed = applyReaderThemeDuringContentStyling(source, true, true, Color.Black, Color.White)
        assertEquals(Color.White, themed.spanStyle.color)
        assertEquals(Color.Transparent, themed.blockStyle.backgroundColor)
        assertEquals(Color.White, themed.blockStyle.borderBottom?.color)
        assertEquals(Color.White, themed.textDecorationColor)
        assertEquals(Color.Black, themed.textEmphasis?.color)
    }

    @Test
    fun listMarkersMatchAndroidFormats() {
        assertEquals("• ", readerListMarker(null, 1, false))
        assertEquals("■ ", readerListMarker("square", 1, false))
        assertEquals(null, readerListMarker("none", 1, false))
        assertEquals("03. ", readerListMarker("decimal-leading-zero", 3, true))
        assertEquals("iv. ", readerListMarker("lower-roman", 4, true))
        assertEquals("AA. ", readerListMarker("upper-alpha", 27, true))
        assertEquals("4000. ", readerListMarker("upper-roman", 4000, true))
    }

    @Test
    fun floatedImageWrapsFollowingParagraphsUntilClearOrNonParagraph() {
        val image = ImageBlock("image.png", null, style = BlockStyle(float = "left"), blockIndex = 1)
        val first = ParagraphBlock(AnnotatedString("first"), blockIndex = 2)
        val second = ParagraphBlock(
            AnnotatedString("second"),
            style = BlockStyle(clear = "left"),
            blockIndex = 3
        )

        val grouped = groupReaderFloatingBlocks(listOf(image, first, second))
        val wrapping = assertIs<WrappingContentBlock>(grouped[0])
        assertEquals(image, wrapping.floatedImage)
        assertEquals(listOf(first), wrapping.paragraphsToWrap)
        assertEquals(second, grouped[1])
    }
}
