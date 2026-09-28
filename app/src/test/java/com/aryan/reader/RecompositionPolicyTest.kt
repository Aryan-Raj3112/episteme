package com.aryan.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards Tracks 2 (recomposition) + 3 (lists/covers/touch): reader collectors
 * must be lifecycle-aware, hot composition work memoized, and hit targets at
 * the 48dp minimum.
 */
class RecompositionPolicyTest {

    @Test
    fun `reader screens collect lifecycle-aware`() {
        val epub = readMain("epubreader/EpubReaderScreen.kt")
        val pdf = readMain("pdf/PdfViewerScreen.kt")
        val split = readMain("pdf/PdfSplitReaderScreen.kt")
        for (source in listOf(epub, pdf, split)) {
            assertFalse(source.contains("import androidx.compose.runtime.collectAsState\n"))
            assertFalse(source.contains(".collectAsState()"))
        }
        assertTrue(epub.contains("collectAsStateWithLifecycle()"))
        assertTrue(pdf.contains("collectAsStateWithLifecycle("))
        assertTrue(split.contains("collectAsStateWithLifecycle()"))
    }

    @Test
    fun `settings model build is remembered`() {
        val source = readMain("SettingsScreen.kt")
        assertTrue(source.contains("val settingsModel = remember(uiState, hideReaderAi)"))
    }

    @Test
    fun `cover request is stable and generated art is fallback-only`() {
        val source = readMain("ThemedBookCover.kt")
        assertTrue(source.contains("val coverRequest = remember(coverFile)"))
        assertTrue(source.contains("if (coverRequest != null)"))
        assertFalse(source.contains("GeneratedBookCover(item = item, modifier = Modifier.fillMaxSize())\n        if (coverFile"))
        assertTrue(source.contains("internal fun generatedBookCoverColor(item: RecentFileItem): Color"))
    }

    @Test
    fun `unified library memoizes continue reading and debounces query`() {
        val source = readMain("UnifiedLibraryScreen.kt")
        assertTrue(source.contains("val continueReading = remember(uiState.rawLibraryFiles)"))
        assertTrue(source.contains("var debouncedQuery by"))
        assertTrue(source.contains("delay(300)"))
        assertTrue(source.contains("query = debouncedQuery"))
        assertFalse(source.contains(".shadow("))
    }

    @Test
    fun `reader controls meet minimum hit targets`() {
        val controls = readMain("epubreader/EpubReaderControls.kt")
        val settings = readMain("epubreader/EpubReaderSettings.kt")
        assertFalse(controls.contains("size(32.dp)"))
        assertFalse(settings.contains("size(32.dp)"))
    }

    @Test
    fun `theme rows have keys and reachable targets`() {
        val source = readMain("HomeScreen.kt")
        assertTrue(source.contains("items(presets.size, key = { it })"))
        assertTrue(source.contains("items(uiState.customAppThemes, key = { it.id })"))
        assertTrue(source.contains("IconButton(onClick = onDelete, modifier = Modifier.size(48.dp))"))
    }

    @Test
    fun `tag membership is precomputed outside item composition`() {
        val source = readMain("SharedComposables.kt")
        assertTrue(source.contains("val tagMembership = remember(selectedBookIds, booksWithTags)"))
        val itemsBody = source.substringAfter("items(filteredTags, key = { it.id })")
        assertFalse(itemsBody.substringBefore("TriStateCheckbox").contains("booksWithTags.find"))
    }

    private fun readMain(relativePath: String): String {
        return listOf(
            File("src/main/java/com/aryan/reader/$relativePath"),
            File("app/src/main/java/com/aryan/reader/$relativePath")
        ).first { it.isFile }.readText()
    }
}
