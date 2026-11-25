package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedGoogleFontsPolicyTest {

    @Test
    fun blankSearchUsesAndroidCuratedPresetsWithoutLoadingFullList() {
        var fullListLoaded = false

        val result = sharedGoogleFontsDisplayList("  ") {
            fullListLoaded = true
            listOf("Unexpected")
        }

        assertEquals("Merriweather", result.first())
        assertEquals("Work Sans", result.last())
        assertFalse(fullListLoaded)
    }

    @Test
    fun typedSearchIsCaseInsensitiveAndCappedAtFifty() {
        val result = sharedGoogleFontsDisplayList("font") {
            List(80) { index -> if (index % 2 == 0) "Font $index" else "FONT $index" }
        }

        assertEquals(50, result.size)
        assertEquals("Font 0", result.first())
        assertEquals("FONT 49", result.last())
    }

    @Test
    fun catalogFaceSuffixStaysOutOfTheDisplayedFamilyName() {
        // Entries like "Buda:300" select a non-Regular face for the download,
        // but the picker must show (and de-duplicate against) "Buda".
        val result = sharedGoogleFontsDisplayList("buda") {
            listOf("Buda:300", "Molle:400italic", "Unbounded")
        }

        assertEquals(listOf("Buda"), result)
    }

    @Test
    fun everyPopularPresetIsADisplayableFamilyName() {
        assertTrue(sharedGoogleFontsPopularPresets.isNotEmpty())
        assertEquals(sharedGoogleFontsPopularPresets, sharedGoogleFontsDisplayList("  ") { emptyList() })
        assertTrue(sharedGoogleFontsPopularPresets.none { it.contains(':') })
    }
}
