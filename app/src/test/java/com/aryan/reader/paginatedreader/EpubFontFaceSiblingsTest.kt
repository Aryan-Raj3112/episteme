package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubFontFaceSiblingsTest {

    @Test
    fun expandFontFacesWithSiblings_addsItalicAndBoldItalicVariants() {
        val root = createTempRoot()
        val fontsDir = File(root, "OEBPS/fonts").apply { mkdirs() }
        File(fontsDir, "Literata-Regular.ttf").writeText("regular")
        File(fontsDir, "Literata-Italic.ttf").writeText("italic")
        File(fontsDir, "Literata-BoldItalic.ttf").writeText("bold italic")
        File(fontsDir, "Other-Italic.ttf").writeText("other")

        val expanded = expandFontFacesWithSiblings(
            fontFaces = listOf(
                FontFaceInfo(
                    fontFamily = "literata",
                    src = "OEBPS/fonts/Literata-Regular.ttf",
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal
                )
            ),
            extractionPath = root.absolutePath
        )

        assertEquals(3, expanded.size)
        assertTrue(expanded.any { it.src == "OEBPS/fonts/Literata-Italic.ttf" && it.fontStyle == FontStyle.Italic })
        assertTrue(
            expanded.any {
                it.src == "OEBPS/fonts/Literata-BoldItalic.ttf" &&
                    it.fontStyle == FontStyle.Italic &&
                    it.fontWeight == FontWeight.Bold
            }
        )
        assertTrue(expanded.none { it.src.contains("Other") })
    }

    @Test
    fun buildEpubFontFaceCss_emitsVariantDescriptorsForSiblings() {
        val root = createTempRoot()
        val fontsDir = File(root, "fonts").apply { mkdirs() }
        File(fontsDir, "LoraRegular.ttf").writeText("regular")
        File(fontsDir, "LoraBoldItalic.ttf").writeText("bold italic")

        val css = buildEpubFontFaceCss(
            fontFaces = listOf(
                FontFaceInfo(
                    fontFamily = "lora",
                    src = "fonts/LoraRegular.ttf",
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal
                )
            ),
            extractionPath = root.absolutePath
        )

        assertTrue(css.contains("font-family: 'lora'"))
        assertTrue(css.contains("font-weight: 700"))
        assertTrue(css.contains("font-style: italic"))
        assertTrue(css.contains("LoraBoldItalic.ttf"))
    }

    private fun createTempRoot(): File {
        return kotlin.io.path.createTempDirectory("epub-font-siblings").toFile().also {
            it.deleteOnExit()
        }
    }
}
