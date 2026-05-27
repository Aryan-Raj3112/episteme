// CssParserTest.kt
package com.aryan.reader.paginatedreader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.google.common.truth.Truth.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CssParserTest {

    private val dummyConstraints = androidx.compose.ui.unit.Constraints()
    private val baseFontSize = 16f
    private val density = 1f

    private fun parseTextColor(value: String): Color? {
        val result = CssParser.parse(
            cssContent = "p { color: $value; }",
            cssPath = null,
            baseFontSizeSp = baseFontSize,
            density = density,
            constraints = dummyConstraints,
            isDarkTheme = false
        )
        return result.rules.byTag["p"]
            ?.firstOrNull()
            ?.style
            ?.spanStyle
            ?.color
            ?.takeIf { it.isSpecified }
    }

    @Test
    fun parseColor_handlesNamedColorsCorrectly() {
        assertThat(parseTextColor("red")).isEqualTo(Color.Red)
        assertThat(parseTextColor("black")).isEqualTo(Color.Black)
        assertThat(parseTextColor("transparent")).isEqualTo(Color.Transparent)
    }

    @Test
    fun parseColor_handles3DigitHexCodes() {
        assertThat(parseTextColor("#F0C")).isEqualTo(Color(0xFFFF00CC))
    }

    @Test
    fun parseColor_handles6DigitHexCodes() {
        assertThat(parseTextColor("#FF00CC")).isEqualTo(Color(0xFFFF00CC))
    }

    @Test
    fun parseColor_handles8DigitHexCodes() {
        assertThat(parseTextColor("#80FF00CC")).isEqualTo(Color(128, 255, 0, 204))
    }

    @Test
    fun parseColor_handlesRgbFunction() {
        assertThat(parseTextColor("rgb(255, 0, 204)")).isEqualTo(Color(255, 0, 204))
    }

    @Test
    fun parseColor_handlesRgbaFunction() {
        assertThat(parseTextColor("rgba(255, 0, 204, 0.5)")).isEqualTo(Color(255, 0, 204, 128))
    }

    @Test
    fun parseColor_returnsNullForInvalidInput() {
        assertThat(parseTextColor("not a color")).isNull()
        assertThat(parseTextColor("#12345")).isNull()
        assertThat(parseTextColor("rgb(1,2)")).isNull()
    }

    @Test
    fun parse_handlesSimpleRule() {
        val css = "p { color: red; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val rules = result.rules.byTag["p"]
        assertThat(rules).hasSize(1)
        assertThat(rules?.first()?.style?.spanStyle?.color).isEqualTo(Color.Red)
    }

    @Test
    fun parse_handlesMultipleSelectors() {
        val css = "h1, h2, h3 { font-weight: bold; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.rules.byTag["h1"]).hasSize(1)
        assertThat(result.rules.byTag["h2"]).hasSize(1)
        assertThat(result.rules.byTag["h3"]).hasSize(1)
        assertThat(result.rules.byTag["h1"]?.first()?.style?.spanStyle?.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(result.rules.byTag["h2"]?.first()?.style?.spanStyle?.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(result.rules.byTag["h3"]?.first()?.style?.spanStyle?.fontWeight).isEqualTo(FontWeight.Bold)
    }

    @Test
    fun parse_handlesImportantRules() {
        val css = "p { color: red !important; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val importantRule = result.rules.byTag["p"]?.find { it.selector.specificity >= 10000 }
        assertThat(importantRule).isNotNull()
        assertThat(importantRule!!.style.spanStyle.color).isEqualTo(Color.Red)
        val normalRule = result.rules.byTag["p"]?.find { it.selector.specificity < 10000 }
        assertThat(normalRule).isNull()
    }

    @Test
    fun parse_createsBothNormalAndImportantRulesWhenMixed() {
        val css = "p { color: blue; background-color: white !important; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val rules = result.rules.byTag["p"]
        assertThat(rules).hasSize(2)

        val importantRule = rules?.find { it.selector.specificity >= 10000 }
        assertThat(importantRule).isNotNull()
        assertThat(importantRule!!.style.blockStyle.backgroundColor).isEqualTo(Color.White)
        assertThat(importantRule.style.spanStyle.color.isSpecified).isFalse()

        val normalRule = rules.find { it.selector.specificity < 10000 }
        assertThat(normalRule).isNotNull()
        assertThat(normalRule!!.style.spanStyle.color).isEqualTo(Color.Blue)
        assertThat(normalRule.style.blockStyle.backgroundColor.isSpecified).isFalse()
    }

    @Test
    fun parse_extractsFontFaceRulesAndResolvesPath() {
        val css = """
        @font-face {
            font-family: "MyCustomFont";
            src: url("../fonts/myfont.ttf");
            font-weight: bold;
        }
        p { color: black; }
        """.trimIndent()
        val result = CssParser.parse(css, "OEBPS/styles/style.css", baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.rules.byTag).containsKey("p")
        assertThat(result.fontFaces).hasSize(1)
        val fontFace = result.fontFaces.first()
        assertThat(fontFace.fontFamily).isEqualTo("mycustomfont")
        assertThat(fontFace.src).isEqualTo("OEBPS/fonts/myfont.ttf")
        assertThat(fontFace.fontWeight).isEqualTo(FontWeight.Bold)
        assertThat(fontFace.fontStyle).isEqualTo(FontStyle.Normal)
    }

    @Test
    fun parse_handlesFontFaceWithDataUri() {
        val dataUri = "data:font/truetype;base64,AAEAAA..."
        val css = """
        @font-face {
            font-family: 'MyDataFont';
            src: url('$dataUri');
        }
        """.trimIndent()
        val result = CssParser.parse(css, "/css/style.css", baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.fontFaces).hasSize(1)
        assertThat(result.fontFaces.first().src).isEqualTo(dataUri)
    }

    @Test
    fun parse_sanitizesPseudoClassesFromSelectors() {
        val css = "a:hover, p::first-line, button:focus { color: red; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.rules.byTag.keys).containsExactly("a", "p", "button")
    }

    @Test
    fun parse_calculatesSpecificityCorrectly() {
        val css = """
        #myId { color: red; } /* 100 */
        p.myClass { color: green; } /* 11 */
        p { color: blue; } /* 1 */
        div p { color: yellow; } /* 2 */
        """.trimIndent()
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val idRule = result.rules.byId["myId"]?.first()
        val classRule = result.rules.otherComplex.find { it.selector.selector == "p.myClass" }
        val elementRule = result.rules.byTag["p"]?.first()
        val descendantRule = result.rules.otherComplex.find { it.selector.selector == "div p" }

        assertThat(idRule?.selector?.specificity).isEqualTo(100)
        assertThat(classRule?.selector?.specificity).isEqualTo(11)
        assertThat(elementRule?.selector?.specificity).isEqualTo(1)
        assertThat(descendantRule?.selector?.specificity).isEqualTo(2)
    }

    @Test
    fun parse_ignoresComments() {
        val css = """
        /* This is a comment */
        p {
            color: /* another comment */ blue; /* block comment */
        }
        """.trimIndent()
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val rules = result.rules.byTag["p"]
        assertThat(rules).hasSize(1)
        assertThat(rules?.first()?.style?.spanStyle?.color).isEqualTo(Color.Blue)
    }

    @Test
    fun parse_handlesBorderShorthand() {
        val css = "div { border: 2px solid red; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["div"]?.first()?.style?.blockStyle
        val expectedBorder = BorderStyle(width = 2.dp, color = Color.Red, style = "solid")
        assertThat(style?.borderTop).isEqualTo(expectedBorder)
        assertThat(style?.borderRight).isEqualTo(expectedBorder)
        assertThat(style?.borderBottom).isEqualTo(expectedBorder)
        assertThat(style?.borderLeft).isEqualTo(expectedBorder)
    }

    @Test
    fun parse_handlesMarginAndPaddingShorthand() {
        val css = "p { margin: 10px 20px; padding: 1em 2em 3em 4em; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["p"]?.first()?.style?.blockStyle
        assertThat(style?.margin?.top).isEqualTo(10.dp)
        assertThat(style?.margin?.right).isEqualTo(20.dp)
        assertThat(style?.margin?.bottom).isEqualTo(10.dp)
        assertThat(style?.margin?.left).isEqualTo(20.dp)

        assertThat(style?.padding?.top).isEqualTo(16.dp) // 1em
        assertThat(style?.padding?.right).isEqualTo(32.dp) // 2em
        assertThat(style?.padding?.bottom).isEqualTo(48.dp) // 3em
        assertThat(style?.padding?.left).isEqualTo(64.dp) // 4em
    }

    @Test
    fun parse_handlesFontSizeWithEmUnits() {
        val css = "p { font-size: 1.2em; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["p"]?.first()?.style
        assertThat(style?.fontSize?.isEm).isTrue()
        assertThat(style?.fontSize?.value).isEqualTo(1.2f)
    }

    @Test
    fun parse_optimizationCategorizesRulesCorrectly() {
        val css = """
            p { color: blue; }
            .myClass { color: green; }
            #myId { color: red; }
            div > p { color: yellow; }
        """.trimIndent()
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.rules.byTag).containsKey("p")
        assertThat(result.rules.byClass).containsKey("myClass")
        assertThat(result.rules.byId).containsKey("myId")
        assertThat(result.rules.otherComplex).hasSize(1)
        assertThat(result.rules.otherComplex.first().selector.selector).isEqualTo("div > p")
    }

    @Test
    fun parse_mediaQueryAppliesDarkThemeRules() {
        val css = """
            p { color: black; }
            @media (prefers-color-scheme: dark) {
                p { color: white; }
            }
        """.trimIndent()
        val lightResult = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(lightResult.rules.byTag["p"]?.first()?.style?.spanStyle?.color).isEqualTo(Color.Black)

        val darkResult = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = true)
        assertThat(darkResult.rules.byTag["p"]?.last()?.style?.spanStyle?.color).isEqualTo(Color.White)
    }

    @Test
    fun parse_fontFaceSelectsPreferredSourceFormat() {
        val css = """
        @font-face {
            font-family: "MyFont";
            src: url("font.woff2") format("woff2"),
                 url("font.otf") format("opentype"),
                 url("font.ttf") format("truetype");
        }
        """.trimIndent()
        val result = CssParser.parse(css, "OEBPS/css/style.css", baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.fontFaces).hasSize(1)
        assertThat(result.fontFaces.first().src).isEqualTo("OEBPS/css/font.otf")
    }

    @Test
    fun parse_handlesNestedMediaAndCalcVariables() {
        val css = """
            :root { --gap: 12px; }
            @media screen and (min-width: 300px) {
                p { margin-left: calc(var(--gap) + 8px); color: hsl(120 100% 25%); }
            }
        """.trimIndent()

        val result = CssParser.parse(
            css,
            null,
            baseFontSize,
            density,
            androidx.compose.ui.unit.Constraints(maxWidth = 500),
            isDarkTheme = false
        )

        val style = result.rules.byTag["p"]!!.first().style
        assertThat(style.blockStyle.margin.left).isEqualTo(20.dp)
        assertThat(style.spanStyle.color).isEqualTo(Color(0, 128, 0))
    }

    @Test
    fun parse_preservesBeforeAfterPseudoElementRules() {
        val css = "p::before { content: 'Note: '; color: red; } p { color: blue; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)

        val pseudoRule = result.rules.otherComplex.single { it.pseudoElement == "before" }
        assertThat(pseudoRule.selector.selector).isEqualTo("p")
        assertThat(pseudoRule.style.content).isEqualTo("'Note: '")
        assertThat(pseudoRule.style.spanStyle.color).isEqualTo(Color.Red)
        assertThat(result.rules.byTag["p"]!!.single().style.spanStyle.color).isEqualTo(Color.Blue)
    }

    @Test
    fun parse_handlesModernRgbSlashAlphaAndCssHexAlpha() {
        assertThat(parseTextColor("rgb(255 0 204 / 50%)")).isEqualTo(Color(255, 0, 204, 128))
        assertThat(parseTextColor("#ff00cc80")).isEqualTo(Color(255, 0, 204, 128))
    }

    @Test
    fun parse_backgroundShorthandExtractsColorAndImage() {
        val css = "section { background: #ffeecc url('../images/paper.png') repeat; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["section"]!!.first().style.blockStyle

        assertThat(style.backgroundColor).isEqualTo(Color(255, 238, 204))
        assertThat(style.backgroundImage).isEqualTo("../images/paper.png")
    }

    @Test
    fun parse_listStyleShorthandExtractsMarkerTypeAndImage() {
        val css = "ul { list-style: square url('../images/bullet.png') outside; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["ul"]!!.first().style.blockStyle

        assertThat(style.listStyleType).isEqualTo("square")
        assertThat(style.listStyleImage).isEqualTo("../images/bullet.png")
    }

    @Test
    fun parse_propertiesHandlesVariousUnitsAndValues() {
        val css = """
            p {
                font-size: 150%;
                text-transform: uppercase;
                text-decoration: underline;
                text-align: center;
                page-break-inside: avoid;
                margin: 0 auto;
            }
        """.trimIndent()
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["p"]?.first()?.style
        assertThat(style?.fontSize).isEqualTo(1.5.em)
        assertThat(style?.textTransform).isEqualTo("uppercase")
        assertThat(style?.spanStyle?.textDecoration).isEqualTo(TextDecoration.Underline)
        assertThat(style?.paragraphStyle?.textAlign).isEqualTo(TextAlign.Center)
        assertThat(style?.blockStyle?.pageBreakInsideAvoid).isTrue()
        assertThat(style?.blockStyle?.horizontalAlign).isEqualTo("center")
    }

    @Test
    fun parse_themeAdaptationAdaptsColorsCorrectlyForDarkTheme() {
        val css = "p { color: #111; background-color: #EEE; }" // very dark text, very light bg
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = true)
        val style = result.rules.byTag["p"]?.first()?.style

        assertThat(style?.spanStyle?.color).isEqualTo(Color.White.copy(alpha = 0.87f))
        assertThat(style?.blockStyle?.backgroundColor).isEqualTo(Color.Transparent)
    }

    @Test
    fun parse_dataUriWithSemicolonParsesCorrectly() {
        val dataUri = "data:font/opentype;base64,d09GMgABAAAAAAPs...;something=else"
        val css = """
        @font-face {
            font-family: 'MyDataFont';
            src: url('$dataUri');
        }
        p { color: red; }
        """.trimIndent()
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        assertThat(result.fontFaces).hasSize(1)
        assertThat(result.fontFaces.first().src).isEqualTo(dataUri)
        assertThat(result.rules.byTag).containsKey("p")
    }

    @Test
    fun parse_textEmphasisParsesCorrectly() {
        val css = "p { -epub-text-emphasis-style: filled dot; -epub-text-emphasis-color: red; }"
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val emphasis = result.rules.byTag["p"]?.first()?.style?.textEmphasis
        assertThat(emphasis).isNotNull()
        assertThat(emphasis?.style).isEqualTo("dot")
        assertThat(emphasis?.fill).isEqualTo("filled")
        assertThat(emphasis?.color).isEqualTo(Color.Red)
    }

    @Test
    fun parse_lineHeightPreservesUnitlessMultiplier() {
        val css = "p { line-height: 1.1; }" // This is treated as 1.1em
        val result = CssParser.parse(css, null, baseFontSize, density, dummyConstraints, isDarkTheme = false)
        val style = result.rules.byTag["p"]?.first()?.style
        assertThat(style?.paragraphStyle?.lineHeight).isEqualTo(1.1.em)
    }
}
