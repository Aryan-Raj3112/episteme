package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderThemeApplierTest {

    @Test
    fun recolorsPageWithoutMutatingSource() {
        val source = Page(
            listOf(
                ParagraphBlock(
                    content = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.Black)) { append("Hello") }
                    },
                    style = BlockStyle(
                        backgroundColor = Color.White,
                        borderTop = BorderStyle(width = 1.dp, color = Color.Black)
                    ),
                    blockIndex = 1
                )
            )
        )

        val themed = source.applyReaderThemeForDisplayPolicy(true, Color.Black, Color.White)
        val sourceParagraph = source.content.single() as ParagraphBlock
        val themedParagraph = themed.content.single() as ParagraphBlock

        assertEquals(Color.Black, sourceParagraph.content.spanStyles.single().item.color)
        assertEquals(Color.White, themedParagraph.content.spanStyles.single().item.color)
        assertEquals(Color.White, sourceParagraph.style.backgroundColor)
        assertEquals(Color.Transparent, themedParagraph.style.backgroundColor)
        assertEquals(Color.Black, sourceParagraph.style.borderTop?.color)
        assertEquals(Color.White, themedParagraph.style.borderTop?.color)
    }

    @Test
    fun recolorsUnderlineAndRoutesOnlyNonMathJaxSvgThroughPlatformAdapter() {
        val underlineColor = Color.Black.value.toString()
        val source = Page(
            listOf(
                TableBlock(
                    rows = listOf(
                        listOf(
                            TableCell(
                                content = listOf(
                                    ParagraphBlock(
                                        content = buildAnnotatedString {
                                            append("Hello")
                                            addStringAnnotation("CustomUnderline", "solid|$underlineColor|0", 0, 5)
                                        },
                                        blockIndex = 2
                                    ),
                                    MathBlock("book-svg", null, BlockStyle(), null, null, isFromMathJax = false, blockIndex = 3),
                                    MathBlock("mathjax-svg", null, BlockStyle(), null, null, isFromMathJax = true, blockIndex = 4)
                                )
                            )
                        )
                    ),
                    blockIndex = 1
                )
            )
        )

        val themed = source.applyReaderThemeForDisplayPolicy(true, Color.Black, Color.White) { svg, color ->
            "$svg:${color.value}"
        }
        val content = ((themed.content.single() as TableBlock).rows.single().single()).content
        val paragraph = content[0] as ParagraphBlock

        assertEquals(
            "solid|${Color.White.value}|0",
            paragraph.content.getStringAnnotations("CustomUnderline", 0, 5).single().item
        )
        assertEquals("book-svg:${Color.White.value}", (content[1] as MathBlock).svgContent)
        assertEquals("mathjax-svg", (content[2] as MathBlock).svgContent)
    }
}
