package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EpubFontFacePolicyTest {
    @Test
    fun infersMatchingSiblingVariantsAndRejectsOtherFamiliesOrExtensions() {
        val regular = FontFaceInfo("literata", "fonts/Literata-Regular.ttf", FontWeight.Normal, FontStyle.Normal)
        val expanded = inferEpubFontFaceSiblings(listOf(regular)) {
            listOf(
                EpubFontSiblingCandidate("Literata-Regular.ttf", "fonts/Literata-Regular.ttf"),
                EpubFontSiblingCandidate("Literata-Italic.ttf", "fonts/Literata-Italic.ttf"),
                EpubFontSiblingCandidate("Literata-BoldItalic.ttf", "fonts/Literata-BoldItalic.ttf"),
                EpubFontSiblingCandidate("Other-Italic.ttf", "fonts/Other-Italic.ttf"),
                EpubFontSiblingCandidate("Literata-BoldItalic.txt", "fonts/Literata-BoldItalic.txt")
            )
        }

        assertEquals(3, expanded.size)
        assertTrue(expanded.any { it.src.endsWith("Literata-Italic.ttf") && it.fontStyle == FontStyle.Italic })
        assertTrue(expanded.any { it.src.endsWith("Literata-BoldItalic.ttf") && it.fontWeight == FontWeight.Bold })
    }

    @Test
    fun preservesOriginalOrderAndDeduplicatesExactVariantKeys() {
        val regular = FontFaceInfo("Family", "fonts/Family-Regular.woff2", FontWeight.Normal, FontStyle.Normal)
        val expanded = inferEpubFontFaceSiblings(listOf(regular)) {
            listOf(
                EpubFontSiblingCandidate("Family-Regular.woff2", "fonts/Family-Regular.woff2"),
                EpubFontSiblingCandidate("Family-Regular.woff2", "fonts/Family-Regular.woff2")
            )
        }
        assertEquals(listOf(regular), expanded)
    }
}
