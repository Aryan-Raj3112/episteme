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
        // Every value the model reads must be a remembered key: dropping one silently
        // freezes that section, e.g. the FPS toggle would stop updating its check state.
        assertTrue(source.contains("val settingsModel = remember(uiState, hideReaderAi, fpsOverlayEnabled, settingsItemValues)"))
        // The value previews are their own remembered keys: rebuilding them inline would rebuild
        // the whole model on every recomposition. The shared helper remembers the host-owned
        // previews, and the host adds its own entries on top without re-running that helper.
        assertTrue(source.contains("val baseItemValues = rememberSharedSettingsItemValues("))
        assertTrue(source.contains("val settingsItemValues = remember(baseItemValues, "))
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
        // The app-theme sheet renders in shared (`SharedAppThemeBottomSheet`),
        // so the keyed-lazy-row and hit-target guarantees are asserted there.
        val source = listOf(
            File("shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/SharedAppThemeSettings.kt"),
            File("../shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/SharedAppThemeSettings.kt")
        ).first { it.isFile }.readText()
        assertTrue(source.contains("items(AppThemePresets.size, key = { it })"))
        assertTrue(source.contains("items(customAppThemes.size, key = { customAppThemes[it].id })"))
        assertTrue(source.contains("IconButton(onClick = onDelete, modifier = Modifier.size(48.dp))"))
    }

    @Test
    fun `tag membership is precomputed outside item composition`() {
        // The sheet now lives in shared so Android and iOS cannot drift again.
        val source = readShared("src/mobileMain/kotlin/com/aryan/reader/shared/ui/SharedMobileTagSheet.kt")
        assertTrue(source.contains("val tagCounts = remember(books, selectedBookIds)"))
        val itemsBody = source.substringAfter("items(filteredTags, key = { it.id })")
        val rowBeforeCheckbox = itemsBody.substringBefore("TriStateCheckbox")
        assertFalse(rowBeforeCheckbox.contains("books.find"))
        assertFalse(rowBeforeCheckbox.contains(".count {"))
    }

    private fun readMain(relativePath: String): String {
        return listOf(
            File("src/main/java/com/aryan/reader/$relativePath"),
            File("app/src/main/java/com/aryan/reader/$relativePath")
        ).first { it.isFile }.readText()
    }

    private fun readShared(relativePath: String): String {
        return listOf(
            File("../shared/$relativePath"),
            File("shared/$relativePath")
        ).first { it.isFile }.readText()
    }
}
