package com.aryan.reader.shared.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.BorderStyle
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import com.aryan.reader.shared.reader.resolveSharedReaderFontFeatureSettings
import com.aryan.reader.shared.reader.resolveSharedReaderTextAlign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class SharedNativeVerticalReaderFlowTest {
    @Test
    fun `shared native spacing clamps negative css lengths before Compose padding`() {
        assertEquals(0.dp, (-12).dp.safeDp())
        assertEquals(0.dp, Dp.Unspecified.safeDp())
        assertEquals(8.dp, 8.dp.safeDp())
    }

    @Test
    fun `shared native visibility hides css hidden blocks`() {
        assertEquals(true, BlockStyle(visibility = "hidden").isSharedNativeVisibilityHidden())
        assertEquals(false, BlockStyle(visibility = "visible").isSharedNativeVisibilityHidden())
        assertEquals(false, BlockStyle().isSharedNativeVisibilityHidden())
    }

    @Test
    fun `shared native background image extracts usable image paths`() {
        assertEquals(
            "images/paper.png",
            BlockStyle(backgroundImage = """url("images/paper.png")""").sharedNativeBackgroundImagePath()
        )
        assertEquals(
            "images/paper.png",
            BlockStyle(backgroundImage = """url( "images/paper.png" )""").sharedNativeBackgroundImagePath()
        )
        assertEquals(
            "data:image/png;base64,abc",
            BlockStyle(backgroundImage = "data:image/png;base64,abc").sharedNativeBackgroundImagePath()
        )
        assertEquals(
            null,
            BlockStyle(backgroundImage = "NONE").sharedNativeBackgroundImagePath()
        )
        assertEquals(
            null,
            BlockStyle(backgroundImage = "linear-gradient(red, blue)").sharedNativeBackgroundImagePath()
        )
    }

    @Test
    fun `shared native background image keeps fit and position styles`() {
        val image = BlockStyle(
            backgroundImage = """url("images/paper.png")""",
            objectFit = "cover",
            objectPosition = "left top",
            filter = "invert(100%)"
        ).toSharedNativeBackgroundImage(blockIndex = 9)

        assertNotNull(image)
        assertEquals("images/paper.png", image.path)
        assertEquals("", image.altText)
        assertEquals("cover", image.style.blockStyle.objectFit)
        assertEquals("left top", image.style.blockStyle.objectPosition)
        assertEquals("invert(100%)", image.style.blockStyle.filter)
        assertEquals(9, image.blockIndex)
    }

    @Test
    fun `shared native link style makes links visible`() {
        val style = sharedNativeReaderLinkSpanStyle(
            isDarkTheme = false,
            themeBackgroundColor = Color.White,
            themeTextColor = Color.Black
        )

        assertEquals(true, style.color.isSpecified)
        assertEquals(true, style.background.isSpecified)
        assertEquals(true, style.textDecoration?.contains(TextDecoration.Underline))
    }

    @Test
    fun `css justify downgrades unless shared setting explicitly forces alignment`() {
        assertEquals(
            TextAlign.Left,
            resolveSharedReaderTextAlign(
                cssTextAlign = TextAlign.Justify,
                fallbackTextAlign = TextAlign.Start
            )
        )
        assertEquals(
            TextAlign.Justify,
            resolveSharedReaderTextAlign(
                cssTextAlign = TextAlign.Justify,
                fallbackTextAlign = TextAlign.Justify
            )
        )
        assertEquals(
            TextAlign.Right,
            resolveSharedReaderTextAlign(
                cssTextAlign = TextAlign.Center,
                fallbackTextAlign = TextAlign.Right
            )
        )
    }

    @Test
    fun `css numeric font variants map to shared native font features`() {
        assertEquals(
            """"tnum" on, "zero" on""",
            resolveSharedReaderFontFeatureSettings(
                existingSettings = null,
                fontVariantNumeric = "tabular-nums slashed-zero"
            )
        )
        assertEquals(
            """"smcp" on, "onum" on, "pnum" on""",
            resolveSharedReaderFontFeatureSettings(
                existingSettings = """"smcp" on""",
                fontVariantNumeric = "oldstyle-nums proportional-nums"
            )
        )
    }

    @Test
    fun `semantic images and styles stay in native vertical flow`() {
        val paragraphStyle = CssStyle(
            blockStyle = BlockStyle(
                backgroundColor = Color(0xFFEFEFEF),
                borderTop = BorderStyle(width = 1.dp, color = Color.Red)
            )
        )
        val paragraph = SemanticParagraph(
            text = "Styled paragraph",
            spans = emptyList(),
            style = paragraphStyle,
            elementId = "p1",
            cfi = "/4/2",
            startCharOffsetInSource = 0,
            blockIndex = 1
        )
        val imageStyle = CssStyle(
            blockStyle = BlockStyle(
                width = 120.dp,
                height = 80.dp,
                objectFit = "cover"
            )
        )
        val image = SemanticImage(
            path = "data:image/png;base64,iVBORw0KGgo=",
            altText = "cover",
            intrinsicWidth = 120f,
            intrinsicHeight = 80f,
            style = imageStyle,
            elementId = "img1",
            cfi = "/4/4",
            blockIndex = 2
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = "Chapter",
                    plainText = "Styled paragraph",
                    semanticBlocks = listOf(paragraph, image)
                )
            )
        )

        val items = buildSharedNativeVerticalFlowItems(book, pages = emptyList())

        assertEquals(
            listOf(SharedNativeVerticalFlowItemKind.BLOCK, SharedNativeVerticalFlowItemKind.BLOCK),
            items.map { it.kind }
        )
        val imageItem = items.single { it.block is SemanticImage }
        val flowImage = assertIs<SemanticImage>(imageItem.block)
        assertEquals("data:image/png;base64,iVBORw0KGgo=", flowImage.path)
        assertEquals("cover", flowImage.style.blockStyle.objectFit)
        assertEquals(2, imageItem.page.semanticBlocks.single().blockIndex)
        val paragraphItem = assertNotNull(items.first().block)
        assertEquals(Color(0xFFEFEFEF), paragraphItem.style.blockStyle.backgroundColor)
        assertEquals(Color.Red, paragraphItem.style.blockStyle.borderTop?.color)
    }

    @Test
    fun `shared native vertical restore prefers block locator before compat page`() {
        val first = SemanticParagraph(
            text = "First paragraph",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "p1",
            cfi = "/4/2",
            startCharOffsetInSource = 0,
            blockIndex = 1
        )
        val second = SemanticParagraph(
            text = "Second paragraph",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "p2",
            cfi = "/4/4",
            startCharOffsetInSource = 16,
            blockIndex = 2
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = "Chapter",
                    plainText = "First paragraph\nSecond paragraph",
                    semanticBlocks = listOf(first, second)
                )
            )
        )

        val items = buildSharedNativeVerticalFlowItems(book, pages = emptyList())
        val restoredIndex = items.sharedNativeVerticalItemIndexForLocator(
            ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = 16,
                endOffset = 32,
                blockIndex = 2,
                charOffset = 16,
                cfi = "/4/4:0"
            )
        )

        assertEquals(1, restoredIndex)
        assertEquals(2, items[restoredIndex!!].block?.blockIndex)
    }

    @Test
    fun `svg math blocks stay in native vertical flow`() {
        val math = SemanticMath(
            svgContent = """<svg width="24" height="12" viewBox="0 0 24 12"><text>x</text></svg>""",
            altText = "Equation",
            svgWidth = "24",
            svgHeight = "12",
            svgViewBox = "0 0 24 12",
            isFromMathJax = false,
            style = CssStyle(),
            elementId = "eq1",
            cfi = "/4/6",
            blockIndex = 3
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = "Chapter",
                    plainText = "Equation",
                    semanticBlocks = listOf(math)
                )
            )
        )

        val items = buildSharedNativeVerticalFlowItems(book, pages = emptyList())

        assertEquals(1, items.size)
        assertEquals(SharedNativeVerticalFlowItemKind.BLOCK, items.single().kind)
        val flowMath = assertIs<SemanticMath>(items.single().block)
        assertEquals("""<svg width="24" height="12" viewBox="0 0 24 12"><text>x</text></svg>""", flowMath.svgContent)
        assertEquals(3, items.single().page.semanticBlocks.single().blockIndex)
    }

    @Test
    fun `floated image wrapping blocks stay grouped in native vertical flow`() {
        val image = SemanticImage(
            path = "cover.png",
            altText = "Cover",
            intrinsicWidth = 120f,
            intrinsicHeight = 180f,
            style = CssStyle(blockStyle = BlockStyle(float = "left")),
            elementId = "img1",
            cfi = "/4/2",
            blockIndex = 4
        )
        val firstParagraph = SemanticParagraph(
            text = "Wrapped first",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "p1",
            cfi = "/4/4",
            startCharOffsetInSource = 0,
            blockIndex = 5
        )
        val secondParagraph = SemanticParagraph(
            text = "Wrapped second",
            spans = emptyList(),
            style = CssStyle(),
            elementId = "p2",
            cfi = "/4/6",
            startCharOffsetInSource = 14,
            blockIndex = 6
        )
        val wrappingBlock = SemanticWrappingBlock(
            floatedImage = image,
            paragraphsToWrap = listOf(firstParagraph, secondParagraph),
            style = CssStyle(),
            elementId = "wrap1",
            cfi = "/4/2",
            blockIndex = 7
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = "Chapter",
                    plainText = "Wrapped first\nWrapped second",
                    semanticBlocks = listOf(wrappingBlock)
                )
            )
        )

        val items = buildSharedNativeVerticalFlowItems(book, pages = emptyList())

        assertEquals(1, items.size)
        assertEquals(SharedNativeVerticalFlowItemKind.BLOCK, items.single().kind)
        val flowWrappingBlock = assertIs<SemanticWrappingBlock>(items.single().block)
        assertEquals("cover.png", flowWrappingBlock.floatedImage.path)
        assertEquals(listOf(5, 6), flowWrappingBlock.paragraphsToWrap.map { it.blockIndex })
        assertEquals(listOf(7), items.single().page.semanticBlocks.map { it.blockIndex })
        assertEquals("Wrapped first\nWrapped second", items.single().page.text)
    }

    @Test
    fun `plain text pages are used when a chapter has no semantic blocks`() {
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "Chapter",
            text = "Plain text",
            startOffset = 0,
            endOffset = 10
        )
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.txt",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "chapter_0",
                    title = "Chapter",
                    plainText = "Plain text"
                )
            )
        )

        val items = buildSharedNativeVerticalFlowItems(book, pages = listOf(page))

        assertEquals(1, items.size)
        assertEquals(SharedNativeVerticalFlowItemKind.TEXT_PAGE, items.single().kind)
        assertEquals(page, items.single().page)
    }
}
