package com.aryan.reader

import com.aryan.reader.shared.googleFontFamilyName
import com.aryan.reader.shared.ui.sharedGoogleFontsPopularPresets
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the bundled "Google Fonts" catalog: the picker only shows families from
 * this file and the downloader asks Google's CSS endpoint for each one, so an
 * entry that Google does not serve becomes a dead search result (OpenDyslexic
 * used to be listed even though it is not a Google Font).
 */
class GoogleFontsCatalogTest {

    @Test
    fun catalogOnlyContainsGoogleFamilies() {
        val families = catalog().map(::googleFontFamilyName).toSet()

        // Google Fonts is the only source; these are popular fonts from other
        // foundries that a previous Fontsource-derived catalog wrongly included.
        listOf(
            "OpenDyslexic",
            "DejaVu Sans",
            "Iosevka",
            "Adwaita Sans",
            "Monaspace Neon",
            "Material Icons",
            "Geist Sans",
            "Comic Mono",
        ).forEach { nonGoogleFont ->
            assertFalse("$nonGoogleFont is not a Google Font", nonGoogleFont in families)
        }
    }

    @Test
    fun catalogContainsCurrentGoogleFamilies() {
        val families = catalog().map(::googleFontFamilyName).toSet()

        listOf("Merriweather", "Literata", "Atkinson Hyperlegible", "STIX Two Math", "Instrument Serif")
            .forEach { googleFont ->
                assertTrue("$googleFont is missing from the bundled catalog", googleFont in families)
            }
    }

    @Test
    fun everyPopularPresetIsInTheCatalog() {
        val families = catalog().map(::googleFontFamilyName).toSet()

        sharedGoogleFontsPopularPresets.forEach { preset ->
            assertTrue("preset $preset is not downloadable", preset in families)
        }
    }

    @Test
    fun catalogEntriesAreUniqueAndSortedByFamilyName() {
        val familyNames = catalog().map(::googleFontFamilyName)

        assertEquals(familyNames.distinct(), familyNames)
        assertEquals(familyNames.sortedBy { it.lowercase() }, familyNames)
    }

    @Test
    fun nonRegularFaceEntriesUseTheCssFaceSuffix() {
        val entries = catalog()

        // Google answers 400 for a plain family request unless the family ships
        // a Regular 400 face, so the catalog carries an explicit face for these.
        assertTrue("Buda:300" in entries)
        assertTrue("Molle:400italic" in entries)
        assertTrue("UnifrakturCook:700" in entries)
    }

    private fun catalog(): List<String> {
        val json = JSONArray(catalogAsset().readText())
        return List(json.length()) { index -> json.getString(index) }
    }

    private fun catalogAsset(): File {
        val candidates = listOf(
            File("src/main/assets/google_fonts.json"),
            File("app/src/main/assets/google_fonts.json"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to locate google_fonts.json from ${File(".").absolutePath}")
    }
}
