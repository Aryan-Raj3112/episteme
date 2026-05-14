// HtmlParserTest.kt
package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class HtmlParserTest {

    // region Test Setup
    private val defaultTextStyle = TextStyle.Default.copy(fontSize = 16.sp, color = Color.Black)
    private val defaultDensity = Density(density = 1f, fontScale = 1f)
    private val defaultConstraints = Constraints(maxWidth = 1000)
    private val defaultChapterPath = "OEBPS/chapter1.xhtml"
    private val defaultExtractionPath = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.absolutePath + "/epub_test/"

    private fun parse(
        html: String,
        cssRules: OptimizedCssRules? = null,
        mathSvgCache: Map<String, String> = emptyMap()
    ): List<SemanticBlock> {
        val userAgentRules = CssParser.parse(
            cssContent = UserAgentStylesheet.default,
            cssPath = null,
            baseFontSizeSp = defaultTextStyle.fontSize.value,
            density = defaultDensity.density,
            constraints = defaultConstraints,
            isDarkTheme = false // This is for CSS parsing, not the semantic parser
        ).rules

        val allRules = cssRules?.let { userAgentRules.merge(it) } ?: userAgentRules

        return htmlToSemanticBlocks(
            html = "<body>$html</body>", // Wrap in body to match real usage
            cssRules = allRules, // Use the combined list of rules
            textStyle = defaultTextStyle,
            chapterAbsPath = defaultChapterPath,
            extractionBasePath = defaultExtractionPath,
            density = defaultDensity,
            fontFamilyMap = emptyMap(),
            constraints = defaultConstraints,
            mathSvgCache = mathSvgCache
        )
    }

    @Test
    fun htmlToSemanticBlocks_simpleParagraphTag_createsSemanticParagraph() {
        val blocks = parse("<p>Hello World</p>")

        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticParagraph::class.java)
        val pBlock = block as SemanticParagraph
        assertThat(pBlock.text).isEqualTo("Hello World")
    }

    @Test
    fun htmlToSemanticBlocks_headerTag_createsSemanticHeaderWithCorrectLevel() {
        val blocks = parse("<h2>Chapter 2</h2>")

        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticHeader::class.java)
        val hBlock = block as SemanticHeader
        assertThat(hBlock.text).isEqualTo("Chapter 2")
        assertThat(hBlock.level).isEqualTo(2)
    }

    @Test
    fun htmlToSemanticBlocks_nestedTag_inheritsStyleFromParent() {
        val blocks = parse("<div style=\"color: #FF0000;\"><p>This text should be red.</p></div>")

        assertThat(blocks).hasSize(1)
        val pBlock = blocks.first() as SemanticParagraph
        assertThat(pBlock.text).isEqualTo("This text should be red.")
        assertThat(pBlock.style.spanStyle.color).isEqualTo(Color.Red)
    }

    @Test
    fun htmlToSemanticBlocks_inlineStyle_overridesCssRule() {
        val css = "p { color: red; }"
        val cssRules = CssParser.parse(css, null, 16f, 1f, defaultConstraints, isDarkTheme = false).rules

        val blocks = parse("<p style=\"color: green;\">I am green.</p>", cssRules = cssRules)

        assertThat(blocks).hasSize(1)
        val pBlock = blocks.first() as SemanticParagraph
        val blockStyle = pBlock.style.spanStyle
        assertThat(blockStyle.color).isEqualTo(Color.Green)
    }

    @Test
    fun htmlToSemanticBlocks_elementWithDisplayNone_isNotIncludedInOutput() {
        val blocks = parse("<p>Visible</p><p style=\"display: none;\">Invisible</p>")

        assertThat(blocks).hasSize(1)
        assertThat((blocks.first() as SemanticParagraph).text).isEqualTo("Visible")
    }

    @Test
    fun htmlToSemanticBlocks_imageWithNonExistentPath_producesNoBlock() {
        // This tests the negative path where resolveImagePath returns null
        val blocks = parse("<img src=\"non/existent/path.jpg\" />")

        assertThat(blocks).isEmpty()
    }

    @Test
    fun htmlToSemanticBlocks_unorderedList_createsSemanticList() {
        val blocks = parse("<ul><li>Item 1</li><li>Item 2</li></ul>")

        assertThat(blocks).hasSize(1)
        val listBlock = blocks.first() as SemanticList
        assertThat(listBlock.isOrdered).isFalse()
        assertThat(listBlock.items).hasSize(2)

        val item1 = listBlock.items[0]
        val item2 = listBlock.items[1]

        assertThat(item1.text).isEqualTo("Item 1")
        assertThat(item2.text).isEqualTo("Item 2")
    }

    @Test
    fun htmlToSemanticBlocks_orderedListWithCssType_createsCorrectSemanticList() {
        val css = "ol { list-style-type: lower-roman; }"
        val cssRules = CssParser.parse(css, null, 16f, 1f, defaultConstraints, isDarkTheme = false).rules

        val blocks = parse("<ol><li>Item 1</li><li>Item 2</li></ol>", cssRules = cssRules)

        assertThat(blocks).hasSize(1)
        val listBlock = blocks.first() as SemanticList
        assertThat(listBlock.isOrdered).isTrue()
        assertThat(listBlock.style.blockStyle.listStyleType).isEqualTo("lower-roman")
        assertThat(listBlock.items).hasSize(2)
    }


    @Test
    fun htmlToSemanticBlocks_table_createsSemanticTableWithCorrectStructure() {
        val html = """
        <table>
            <tr>
                <th>Header 1</th>
                <th style="text-align: right;">Header 2</th>
            </tr>
            <tr>
                <td>Data A</td>
                <td>Data B</td>
            </tr>
        </table>
    """.trimIndent()

        val blocks = parse(html)

        assertThat(blocks).hasSize(1)
        val tableBlock = blocks.first() as SemanticTable
        assertThat(tableBlock.rows).hasSize(2)

        // Verify Header Row
        val headerRow = tableBlock.rows[0]
        assertThat(headerRow).hasSize(2)
        assertThat(headerRow[0].isHeader).isTrue()
        assertThat((headerRow[0].content.first() as SemanticParagraph).text).isEqualTo("Header 1")
        assertThat(headerRow[1].isHeader).isTrue()
        assertThat((headerRow[1].content.first() as SemanticParagraph).text).isEqualTo("Header 2")
        assertThat(headerRow[1].style.paragraphStyle.textAlign).isEqualTo(TextAlign.End)

        // Verify Data Row
        val dataRow = tableBlock.rows[1]
        assertThat(dataRow).hasSize(2)
        assertThat(dataRow[0].isHeader).isFalse()
        assertThat((dataRow[0].content.first() as SemanticParagraph).text).isEqualTo("Data A")
        assertThat(dataRow[1].isHeader).isFalse()
        assertThat((dataRow[1].content.first() as SemanticParagraph).text).isEqualTo("Data B")
    }

    @Test
    fun htmlToSemanticBlocks_textTransformations_areAppliedCorrectly() {
        val blocks = parse("<p style=\"text-transform: uppercase;\">hello world</p>")

        assertThat(blocks).hasSize(1)
        val pBlock = blocks.first() as SemanticParagraph
        // The transformation is applied during text building
        assertThat(pBlock.text).isEqualTo("HELLO WORLD")
    }

    @Test
    fun htmlToSemanticBlocks_complexInlineFormatting_isPreserved() {
        val html = "<p>This is <b>bold</b> and <i>italic</i> text.</p>"
        val blocks = parse(html)

        assertThat(blocks).hasSize(1)
        val pBlock = blocks.first() as SemanticParagraph

        assertThat(pBlock.text).isEqualTo("This is bold and italic text.")

        // Find the range for "bold" and check its style
        val boldRange = pBlock.spans.find { pBlock.text.substring(it.start, it.end) == "bold" }
        assertThat(boldRange).isNotNull()
        assertThat(boldRange!!.style.spanStyle.fontWeight).isEqualTo(FontWeight.Bold)

        // Find the range for "italic" and check its style
        val italicRange =
            pBlock.spans.find { pBlock.text.substring(it.start, it.end) == "italic" }
        assertThat(italicRange).isNotNull()
        assertThat(italicRange!!.style.spanStyle.fontStyle).isEqualTo(androidx.compose.ui.text.font.FontStyle.Italic)
    }

    @Test
    fun htmlToSemanticBlocks_imageWithExistingPath_createsSemanticImageWithCorrectPath() {
        // SETUP
        val imageRelativeSrc = "../images/test.jpg"
        val chapterParentDir = File(defaultChapterPath).parent ?: ""
        val imageFile = File(File(defaultExtractionPath, chapterParentDir), imageRelativeSrc).canonicalFile
        imageFile.parentFile?.mkdirs()
        imageFile.createNewFile()
        imageFile.deleteOnExit()

        // ACTION
        val blocks = parse("<img src=\"$imageRelativeSrc\" alt=\"A test image\" />")

        // ASSERT
        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticImage::class.java)

        val imageBlock = block as SemanticImage
        assertThat(imageBlock.path).isEqualTo(imageFile.absolutePath)
        assertThat(imageBlock.altText).isEqualTo("A test image")
    }

    @Test
    fun htmlToSemanticBlocks_pseudoElements_areIgnoredByTheParser() {
        val css = "p::before { content: \"Note: \"; }"
        val cssRules = CssParser.parse(css, null, 16f, 1f, defaultConstraints, isDarkTheme = false).rules
        val blocks = parse("<p>This is a test.</p>", cssRules = cssRules)

        // The parser now ignores pseudo-elements, so only the paragraph content should be parsed.
        assertThat(blocks).hasSize(1)
        val pBlock = blocks[0] as SemanticParagraph
        assertThat(pBlock.text).isEqualTo("This is a test.")
    }

    @Test
    fun htmlToSemanticBlocks_hrWithPseudoElement_ignoresPseudoElement() {
        val css = "hr.fancy::after { content: ''; display: block; border-bottom: 2px solid blue; }"
        val cssRules = CssParser.parse(css, null, 16f, 1f, defaultConstraints, isDarkTheme = false).rules
        val blocks = parse("<hr class=\"fancy\" />", cssRules = cssRules)

        // The pseudo-element is ignored, so only the spacer from <hr> is created.
        assertThat(blocks).hasSize(1)
        assertThat(blocks[0]).isInstanceOf(SemanticSpacer::class.java)
    }

    @Test
    fun htmlToSemanticBlocks_inlineSvg_createsSemanticMathWithCorrectContent() {
        val svg = """
    <svg width="100" height="100">
        <title>My SVG</title>
        <circle cx="50" cy="50" r="40" stroke="green" stroke-width="4" fill="yellow" />
        <text x="50" y="50" fill="red">Hello</text>
    </svg>
""".trimIndent()
        val blocks = parse(svg)

        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticMath::class.java)

        val mathBlock = block as SemanticMath
        assertThat(mathBlock.altText).isEqualTo("My SVG")
        // The parser now passes the SVG content through as-is.
        assertThat(mathBlock.svgContent).contains("""<text x="50" y="50" fill="red">Hello</text>""")
    }

    @Test
    fun htmlToSemanticBlocks_imgTagWithSvgSource_createsSemanticMath() {
        // SETUP
        val svgContent = """<svg width="10" height="10"><rect width="10" height="10" /></svg>"""
        val svgRelativeSrc = "images/test.svg"
        val chapterParentDir = File(defaultChapterPath).parent ?: ""
        val svgFile = File(File(defaultExtractionPath, chapterParentDir), svgRelativeSrc).canonicalFile
        svgFile.parentFile?.mkdirs()
        svgFile.writeText(svgContent)
        svgFile.deleteOnExit()

        // ACTION
        val blocks = parse("<img src=\"$svgRelativeSrc\" />")

        // ASSERT
        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticMath::class.java)
        val mathBlock = block as SemanticMath
        assertThat(mathBlock.svgContent).contains("<rect")
    }

    @Test
    fun htmlToSemanticBlocks_mathPlaceholder_createsSemanticMathFromCache() {
        val svgContent = "<svg><text>E=mc^2</text></svg>"
        val cache = mapOf("math-123" to svgContent)
        val blocks = parse(
            html = """<math-placeholder id="math-123" alttext="An equation"></math-placeholder>""",
            mathSvgCache = cache
        )

        assertThat(blocks).hasSize(1)
        val block = blocks.first() as SemanticMath
        assertThat(block.svgContent).isEqualTo(svgContent)
        assertThat(block.altText).isEqualTo("An equation")
        assertThat(block.isFromMathJax).isTrue()
    }

    @Test
    fun htmlToSemanticBlocks_displayFlex_createsSemanticFlexContainer() {
        val html = """
    <div style="display: flex;">
        <p>One</p>
        <p>Two</p>
    </div>
""".trimIndent()
        val blocks = parse(html)

        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticFlexContainer::class.java)

        val flexBlock = block as SemanticFlexContainer
        assertThat(flexBlock.children).hasSize(2)
        assertThat(flexBlock.children[0]).isInstanceOf(SemanticParagraph::class.java)
        assertThat((flexBlock.children[0] as SemanticParagraph).text).isEqualTo("One")
    }

    @Test
    fun htmlToSemanticBlocks_brTagInParagraph_createsNewlineCharacter() {
        val blocks = parse("<p>Line one.<br>Line two.</p>")

        assertThat(blocks).hasSize(1)
        val pBlock = blocks.first() as SemanticParagraph
        assertThat(pBlock.text).isEqualTo("Line one.\nLine two.")
    }

    @Test
    fun htmlToSemanticBlocks_veryLongInlineParagraph_splitsIntoBoundedParagraphs() {
        val longText = "a".repeat(40_000)
        val blocks = parse("<p>$longText</p>")
        val paragraphs = blocks.filterIsInstance<SemanticParagraph>()

        assertThat(paragraphs.size).isAtLeast(2)
        assertThat(paragraphs.sumOf { it.text.length }).isEqualTo(longText.length)
        assertThat(paragraphs.all { it.text.length <= 32_000 }).isTrue()
        assertThat(
            paragraphs.zipWithNext().all { (previous, next) ->
                next.startCharOffsetInSource > previous.startCharOffsetInSource
            }
        ).isTrue()
    }

    @Test
    fun htmlToSemanticBlocks_deepInlineWrapperWithBlockDescendant_parsesWithoutSelectorRecursion() {
        val mathId = "deep-math"
        val mathPlaceholder = """<math-placeholder id="$mathId" alttext="Deep math"></math-placeholder>"""
        val nestedHtml = (1..600).fold(mathPlaceholder) { content, _ ->
            "<span>$content</span>"
        }

        val blocks = parse(
            html = nestedHtml,
            mathSvgCache = mapOf(mathId to "<svg><text>x</text></svg>")
        )

        assertThat(blocks).hasSize(1)
        assertThat(blocks.first()).isInstanceOf(SemanticMath::class.java)
    }

    @Test
    fun htmlToSemanticBlocks_imageWithRootRelativePath_resolvesCorrectly() {
        // SETUP
        val imageRootRelativeSrc = "images/test.jpg"
        val imageFile = File(defaultExtractionPath, imageRootRelativeSrc).canonicalFile
        imageFile.parentFile?.mkdirs()
        imageFile.createNewFile()
        imageFile.deleteOnExit()

        // ACTION
        val blocks = parse("<img src=\"$imageRootRelativeSrc\" alt=\"A test image\" />")

        // ASSERT
        assertThat(blocks).hasSize(1)
        val block = blocks.first()
        assertThat(block).isInstanceOf(SemanticImage::class.java)

        val imageBlock = block as SemanticImage
        assertThat(imageBlock.path).isEqualTo(imageFile.absolutePath)
    }
}
