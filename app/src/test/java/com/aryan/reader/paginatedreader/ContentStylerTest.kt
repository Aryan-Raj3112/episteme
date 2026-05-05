package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ContentStylerTest {

    @Test
    fun `paragraph styling applies user text alignment and preserves cfi metadata`() {
        val styler = styler(userTextAlign = TextAlign.Justify)

        val block = styler.style(
            listOf(
                SemanticParagraph(
                    text = "Aligned text",
                    spans = emptyList(),
                    style = CssStyle(),
                    elementId = "p1",
                    cfi = "/4/2",
                    startCharOffsetInSource = 7,
                    blockIndex = 10
                )
            )
        ).single() as ParagraphBlock

        assertEquals(TextAlign.Justify, block.textAlign)
        assertEquals("p1", block.elementId)
        assertEquals("/4/2", block.cfi)
        assertEquals(7, block.startCharOffsetInSource)
        assertEquals(10, block.blockIndex)
        assertEquals("Aligned text", block.content.text)
    }

    @Test
    fun `floating image is grouped with following paragraphs until clear`() {
        val blocks = styler().style(
            listOf(
                SemanticImage(
                    path = "image.png",
                    altText = "Cover",
                    intrinsicWidth = 120f,
                    intrinsicHeight = 200f,
                    style = CssStyle(blockStyle = BlockStyle(float = "left")),
                    elementId = "img",
                    cfi = "/4/4",
                    blockIndex = 1
                ),
                paragraph("Wrapped one", blockIndex = 2),
                paragraph("Wrapped two", blockIndex = 3),
                paragraph(
                    "After clear",
                    blockIndex = 4,
                    style = CssStyle(blockStyle = BlockStyle(clear = "left"))
                )
            )
        )

        val wrapping = blocks[0] as WrappingContentBlock
        assertEquals("image.png", wrapping.floatedImage.path)
        assertEquals(listOf("Wrapped one", "Wrapped two"), wrapping.paragraphsToWrap.map { it.content.text })
        assertEquals("After clear", (blocks[1] as ParagraphBlock).content.text)
    }

    @Test
    fun `ordered list items receive decimal markers and nested text styles`() {
        val list = SemanticList(
            items = listOf(
                SemanticListItem(
                    text = "First",
                    spans = listOf(
                        SemanticSpan(
                            start = 0,
                            end = 5,
                            style = CssStyle(spanStyle = SpanStyle(color = Color.Red)),
                            tag = "span",
                            linkHref = "https://example.org",
                            elementId = "link"
                        )
                    ),
                    style = CssStyle(),
                    elementId = "li1",
                    cfi = "/4/2/2",
                    startCharOffsetInSource = 0,
                    itemMarkerImage = null,
                    blockIndex = 11
                ),
                SemanticListItem(
                    text = "Second",
                    spans = emptyList(),
                    style = CssStyle(),
                    elementId = "li2",
                    cfi = "/4/2/4",
                    startCharOffsetInSource = 6,
                    itemMarkerImage = null,
                    blockIndex = 12
                )
            ),
            isOrdered = true,
            style = CssStyle(blockStyle = BlockStyle(listStyleType = "decimal-leading-zero")),
            elementId = "list",
            cfi = "/4/2",
            blockIndex = 10
        )

        val flex = styler().style(listOf(list)).single() as FlexContainerBlock
        val first = flex.children[0] as ListItemBlock
        val second = flex.children[1] as ListItemBlock

        assertEquals("01. ", first.itemMarker)
        assertEquals("02. ", second.itemMarker)
        assertEquals("li1", first.elementId)
        assertEquals("https://example.org", first.content.getStringAnnotations("URL", 0, 5).single().item)
        assertEquals("link", first.content.getStringAnnotations("ID", 0, 5).single().item)
    }

    @Test
    fun `math svg is themed and external images are embedded when resolvable`() {
        val root = kotlin.io.path.createTempDirectory("content-styler-svg").toFile()
        val chapterDir = java.io.File(root, "chapters").apply { mkdirs() }
        val image = java.io.File(chapterDir, "pixel.png")
        image.writeBytes(
            java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
            )
        )
        val styler = styler(
            extractionBasePath = root.absolutePath,
            chapterAbsPath = "chapters/chapter.xhtml",
            baseTextStyle = TextStyle(fontSize = 16.sp, color = Color.Black)
        )

        val math = styler.style(
            listOf(
                SemanticMath(
                    svgContent = """<svg><text fill="#fff">x</text><image href="pixel.png"/></svg>""",
                    altText = "x",
                    svgWidth = null,
                    svgHeight = null,
                    svgViewBox = null,
                    isFromMathJax = false,
                    style = CssStyle(),
                    elementId = null,
                    cfi = "/math",
                    blockIndex = 1
                )
            )
        ).single() as MathBlock

        val svgContent = math.svgContent!!
        assertTrue(svgContent.contains("fill:#000000"))
        assertTrue(svgContent.contains("data:image/png;base64,"))
    }

    private fun paragraph(
        text: String,
        blockIndex: Int,
        style: CssStyle = CssStyle()
    ): SemanticParagraph {
        return SemanticParagraph(
            text = text,
            spans = emptyList(),
            style = style,
            elementId = null,
            cfi = null,
            startCharOffsetInSource = 0,
            blockIndex = blockIndex
        )
    }

    private fun styler(
        userTextAlign: TextAlign? = null,
        extractionBasePath: String = "",
        chapterAbsPath: String = "chapter.xhtml",
        baseTextStyle: TextStyle = TextStyle(fontSize = 16.sp, color = Color.Black)
    ): ContentStyler {
        return ContentStyler(
            baseTextStyle = baseTextStyle,
            fontFamilyMap = emptyMap(),
            density = Density(1f),
            isDarkTheme = false,
            themeBackgroundColor = Color.White,
            themeTextColor = Color.Black,
            chapterAbsPath = chapterAbsPath,
            extractionBasePath = extractionBasePath,
            userTextAlign = userTextAlign,
            paragraphGapMultiplier = 1f
        )
    }
}
