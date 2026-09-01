package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class EpubFontFaceSiblingsTest {

    private lateinit var root: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("epub-font-siblings").toFile()
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun createFontFile(relativePath: String): File {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText("font")
        return file
    }

    @Test
    fun expandFontFacesWithSiblings_addsItalicAndBoldItalicVariants() {
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
    }

    @Test
    fun expandFontFacesWithSiblings_deduplicatesVariableFontWeightSiblings() {
        val fontsDir = File(root, "fonts").apply { mkdirs() }
        createFontFile("fonts/Pliant-VariableFont_wdth,wght.ttf")
        createFontFile("fonts/Pliant-Italic-VariableFont_wdth,wght.ttf")

        val expanded = expandFontFacesWithSiblings(
            fontFaces = listOf(
                FontFaceInfo(
                    fontFamily = "pliant",
                    src = "fonts/Pliant-VariableFont_wdth,wght.ttf",
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal
                )
            ),
            extractionPath = root.absolutePath
        )

        assertEquals(2, expanded.size)
        assertTrue(
            expanded.any {
                it.src == "fonts/Pliant-Italic-VariableFont_wdth,wght.ttf" &&
                    it.fontStyle == FontStyle.Italic &&
                    it.fontWeight == FontWeight.Normal
            }
        )
    }

    @Test
    fun buildEpubFontFaceCss_usesWeightRangeForVariableWeightFonts() {
        val fontsDir = File(root, "fonts").apply { mkdirs() }
        createFontFile("fonts/Pliant-VariableFont_wdth,wght.ttf")
        createFontFile("fonts/Pliant-Italic-VariableFont_wdth,wght.ttf")

        val css = buildEpubFontFaceCss(
            fontFaces = listOf(
                FontFaceInfo(
                    fontFamily = "pliant",
                    src = "fonts/Pliant-VariableFont_wdth,wght.ttf",
                    fontWeight = FontWeight.Normal,
                    fontStyle = FontStyle.Normal
                )
            ),
            extractionPath = root.absolutePath
        )

        assertTrue(css.contains("font-weight: 100 900"))
        assertTrue(css.contains("font-style: italic"))
        assertTrue(css.contains("Pliant-Italic-VariableFont_wdth,wght.ttf"))
    }

    @Test
    fun directoryListingsAreMemoizedSoRepeatedCallsAreConsistent() {
        createFontFile("fonts/Literata-Regular.ttf")
        createFontFile("fonts/Literata-Italic.ttf")
        val fontFace = FontFaceInfo(
            fontFamily = "literata",
            src = "fonts/Literata-Regular.ttf",
            fontWeight = FontWeight.Normal,
            fontStyle = FontStyle.Normal
        )

        val first = expandFontFacesWithSiblings(listOf(fontFace), root.absolutePath)
        val second = expandFontFacesWithSiblings(listOf(fontFace), root.absolutePath)
        assertEquals(first, second)

        // Extraction directories are immutable while a book is loaded; the per-directory cache
        // is the ANR fix, so deleting files between calls must not change the expansion.
        File(root, "fonts/Literata-Italic.ttf").delete()
        val third = expandFontFacesWithSiblings(listOf(fontFace), root.absolutePath)
        assertEquals(first, third)
    }
}
