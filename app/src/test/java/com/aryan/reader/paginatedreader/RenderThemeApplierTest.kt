package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class RenderThemeApplierTest {

    @Test
    fun displayThemeRecolorsCachedPageWithoutMutatingSource() {
        val source = Page(
            content = listOf(
                ParagraphBlock(
                    content = buildAnnotatedString {
                        withStyle(SpanStyle(color = Color.Black)) {
                            append("Hello")
                        }
                    },
                    style = BlockStyle(
                        backgroundColor = Color.White,
                        borderTop = BorderStyle(width = 1.dp, color = Color.Black)
                    ),
                    blockIndex = 1
                )
            )
        )

        val themed = source.applyReaderThemeForDisplay(
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White
        )

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
    fun displayThemeRecolorsCustomUnderlineAnnotations() {
        val underlineColor = Color.Black.value.toString()
        val source = Page(
            content = listOf(
                ParagraphBlock(
                    content = buildAnnotatedString {
                        append("Hello")
                        addStringAnnotation("CustomUnderline", "solid|$underlineColor|0", 0, 5)
                    },
                    blockIndex = 1
                )
            )
        )

        val themed = source.applyReaderThemeForDisplay(
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White
        )

        val themedParagraph = themed.content.single() as ParagraphBlock
        val annotation = themedParagraph.content.getStringAnnotations("CustomUnderline", 0, 5).single()

        assertEquals("solid|${Color.White.value}|0", annotation.item)
    }
}
