package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HtmlParserMissingFigureTest {

    @Test
    fun `marker figure folds into captioned placeholder`() {
        val blocks = parse(
            """<div class="figcenter" style="width: 600px">""" +
                """<span class="caption">SHOOTING A LEOPARD.</span>""" +
                """<span title="" id="img_images_illus001.png">SHOOTING A LEOPARD</span></div>"""
        )

        assertEquals(1, blocks.size)
        val placeholder = blocks.single() as SemanticParagraph
        assertEquals("[Illustration] SHOOTING A LEOPARD.", placeholder.text)
        assertTrue(placeholder.style.isReaderMissingFigure())
    }

    @Test
    fun `empty marker figure yields bare placeholder`() {
        val blocks = parse(
            """<div class="figcenter"><span title="" id="img_images_butterflies.jpg"></span></div>"""
        )

        assertEquals(1, blocks.size)
        val placeholder = blocks.single() as SemanticParagraph
        assertEquals("[Illustration]", placeholder.text)
        assertTrue(placeholder.style.isReaderMissingFigure())
    }

    @Test
    fun `figures with real images are untouched`() {
        val blocks = parse(
            """<div class="figcenter"><img src="a.jpg" alt="A"/><span class="caption">Cap</span></div>"""
        )

        assertTrue(blocks.none { it.style.isReaderMissingFigure() })
    }

    @Test
    fun `non figure divs with marker ids keep old behavior`() {
        val blocks = parse("<p>Text <span id=\"img_inline.png\">caption</span> tail.</p>")

        assertFalse(blocks.single().style.isReaderMissingFigure())
    }

    private fun parse(html: String): List<SemanticBlock> {
        return htmlToSemanticBlocks(
            html = html,
            cssRules = OptimizedCssRules(),
            textStyle = TextStyle(fontSize = 16.sp),
            chapterAbsPath = "OEBPS/chapter1.xhtml",
            extractionBasePath = "",
            density = Density(1f),
            fontFamilyMap = emptyMap(),
            constraints = Constraints(maxWidth = 400, maxHeight = 800)
        )
    }
}
