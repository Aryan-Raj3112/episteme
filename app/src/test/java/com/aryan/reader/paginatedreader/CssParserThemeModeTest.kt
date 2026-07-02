package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Constraints
import org.junit.Assert.assertEquals
import org.junit.Test

class CssParserThemeModeTest {

    @Test
    fun parseCanPreserveRawPaintColorsForLayoutCaches() {
        val result = CssParser.parse(
            cssContent = "p { color: #000000; background-color: #ffffff; border-top-width: 1px; border-top-style: solid; border-top-color: #000000; }",
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White,
            adaptThemeColors = false
        )

        val style = result.rules.byTag.getValue("p").single().style

        assertEquals(Color.Black, style.spanStyle.color)
        assertEquals(Color.White, style.blockStyle.backgroundColor)
        assertEquals(Color.Black, style.blockStyle.borderTop?.color)
    }

    @Test
    fun parseStillAdaptsPaintColorsWhenThemeModeIsEnabled() {
        val result = CssParser.parse(
            cssContent = "p { color: #000000; background-color: #ffffff; border-top-width: 1px; border-top-style: solid; border-top-color: #000000; }",
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = true,
            themeBackgroundColor = Color.Black,
            themeTextColor = Color.White,
            adaptThemeColors = true
        )

        val style = result.rules.byTag.getValue("p").single().style

        assertEquals(Color.White, style.spanStyle.color)
        assertEquals(Color.Transparent, style.blockStyle.backgroundColor)
        assertEquals(Color.White, style.blockStyle.borderTop?.color)
    }

    @Test
    fun parseKeepsColonsInsideDeclarationValues() {
        val result = CssParser.parse(
            cssContent = """
                :root { --asset: url(data:image/svg+xml;charset=utf-8,<svg viewBox='0:0'></svg>); }
                p::before { content: "chapter: one"; color: var(--missing, #123456); }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false,
            adaptThemeColors = false
        )

        val rootStyle = result.rules.byTag.getValue("html").single().style
        val beforeStyle = result.rules.otherComplex.single { it.pseudoElement == "before" }.style

        assertEquals("url(data:image/svg+xml;charset=utf-8,<svg viewBox='0:0'></svg>)", rootStyle.customProperties["--asset"])
        assertEquals("\"chapter: one\"", beforeStyle.content)
        assertEquals(Color(0xFF123456), beforeStyle.spanStyle.color)
    }

    @Test
    fun parseSanitizesCommonPseudoSelectorsWithoutRegexMatchers() {
        val result = CssParser.parse(
            cssContent = """
                :root { --accent: #123456; }
                a:hover { color: #112233; }
                p::selection { background-color: #445566; }
                p::after { content: "*"; color: #778899; }
            """.trimIndent(),
            cssPath = null,
            baseFontSizeSp = 16f,
            density = 1f,
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false,
            adaptThemeColors = false
        )

        assertEquals("#123456", result.rules.byTag.getValue("html").single().style.customProperties["--accent"])
        assertEquals(Color(0xFF112233), result.rules.byTag.getValue("a").single().style.spanStyle.color)
        assertEquals(Color(0xFF445566), result.rules.byTag.getValue("p").single().style.blockStyle.backgroundColor)
        assertEquals("\"*\"", result.rules.otherComplex.single { it.pseudoElement == "after" }.style.content)
    }


    @Test
    fun `simple selector bucketing avoids Regex matches`() {
        val source = readCssParserSource()

        assertEquals(false, source.contains("SIMPLE_TAG_SELECTOR"))
        assertEquals(false, source.contains("SIMPLE_CLASS_SELECTOR"))
        assertEquals(false, source.contains("SIMPLE_ID_SELECTOR"))
        assertEquals(false, source.contains(".matches(sanitizedSelector)"))
        assertEquals(true, source.contains("isSimpleTagSelector(sanitizedSelector)"))
        assertEquals(true, source.contains("isSimpleClassSelector(sanitizedSelector)"))
        assertEquals(true, source.contains("isSimpleIdSelector(sanitizedSelector)"))
    }

    @Test
    fun `font face extraction avoids eager Regex compilation`() {
        val source = readCssParserSource()
        val parserHeader = source.substringAfter("object CssParser {")
            .substringBefore("private val TRANSIENT_PSEUDO_CLASSES")
        val fontFaceBody = source.substringAfter("private fun parseFontFace")
            .substringBefore("internal fun parseProperties")

        assertEquals(false, parserHeader.contains("FONT_FACE_REGEX"))
        assertEquals(false, parserHeader.contains(" = Regex("))
        assertEquals(false, parserHeader.contains("private val URL_REGEX ="))
        assertEquals(true, parserHeader.contains("by lazy(LazyThreadSafetyMode.PUBLICATION)"))
        assertEquals(false, fontFaceBody.contains("Regex("))
        assertEquals(false, fontFaceBody.contains(".toRegex("))
        assertEquals(true, fontFaceBody.contains("parseFontSources(srcString)"))
    }

    @Test
    fun parseFontFacesExtractsOnlyFontFaceData() {
        val fontFaces = CssParser.parseFontFaces(
            cssContent = """
                @font-face {
                    font-family: "Reader Serif";
                    src: local("Reader, Serif"), url("../fonts/reader-serif.woff") format("woff"), url("../fonts/reader-serif.woff2") format("woff2");
                    font-weight: bold;
                }
                article#chapter.content[data-page="1"]:not(.hidden) p::first-line { color: red; }
            """.trimIndent(),
            cssPath = "OEBPS/styles/book.css",
            constraints = Constraints(maxWidth = 400, maxHeight = 800),
            isDarkTheme = false,
            adaptThemeColors = false
        )

        assertEquals(1, fontFaces.size)
        assertEquals("reader serif", fontFaces.single().fontFamily)
        assertEquals("OEBPS/fonts/reader-serif.woff2", fontFaces.single().src)
    }

    private fun readCssParserSource(): String {
        return listOf(
            java.io.File("shared/src/commonMain/kotlin/com/aryan/reader/paginatedreader/CssParser.kt"),
            java.io.File("../shared/src/commonMain/kotlin/com/aryan/reader/paginatedreader/CssParser.kt")
        ).first { it.isFile }.readText()
    }
}