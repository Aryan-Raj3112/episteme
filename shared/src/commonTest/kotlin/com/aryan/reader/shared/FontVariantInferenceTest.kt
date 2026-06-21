package com.aryan.reader.shared

import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FontVariantInferenceTest {
    @Test
    fun variableRegularAndItalicFilesShareFamilySignature() {
        val regular = "Pliant-VariableFont_wdth,wght"
        val italic = "Pliant-Italic-VariableFont_wdth,wght"

        assertEquals(regular.familyFilenameSignature(), italic.familyFilenameSignature())
        assertEquals("pliant", regular.familyFilenameSignature())
        assertEquals(FontStyle.Italic, italic.detectFontVariant()?.style)
        assertTrue(regular.supportsVariableWeightAxis())
    }

    @Test
    fun familyGroupingUsesBaseFamilyForVariableFontVariants() {
        val fonts = listOf(
            fontItem("1", "Pliant-VariableFont_wdth,wght.ttf"),
            fontItem("2", "Pliant-Italic-VariableFont_wdth,wght.ttf")
        )

        val family = fonts.groupByFamily().single()

        assertEquals("Pliant", family.familyName)
        assertEquals(2, family.variants.size)
        assertTrue(family.variants.any { it.variant?.style == FontStyle.Italic })
        assertTrue(family.variants.any { it.variant?.weight == FontWeight.Normal })
        assertEquals("Regular, Italic", family.fontFaceSummary())
        assertTrue(family.hasVariableWeightFace())
    }

    @Test
    fun variableWeightAxisEmitsCssWeightRange() {
        assertEquals(
            "100 900",
            "Pliant-VariableFont_wdth,wght".fontWeightCssDescriptor(FontWeight.Normal)
        )
        assertEquals(
            "700",
            "Literata-Bold".fontWeightCssDescriptor(FontWeight.Bold)
        )
    }

    @Test
    fun longFontFilenameTokenizationIsBounded() {
        val longSuffix = "Ignored".repeat(200)
        val fileName = "Pliant-BoldItalic-$longSuffix"

        assertTrue(fileName.familyFilenameSignature().length < fileName.length)
        assertEquals(FontStyle.Italic, fileName.detectFontVariant()?.style)
        assertEquals(FontWeight.Bold, fileName.detectFontVariant()?.weight)
    }

    private fun fontItem(id: String, fileName: String): CustomFontItem {
        return CustomFontItem(
            id = id,
            displayName = fileName.substringBeforeLast('.'),
            fileName = fileName,
            fileExtension = fileName.substringAfterLast('.'),
            path = "/fonts/$fileName",
            timestamp = id.toLong()
        )
    }
}
