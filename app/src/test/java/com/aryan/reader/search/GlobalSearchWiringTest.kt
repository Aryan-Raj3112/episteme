package com.aryan.reader.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards the two defects that made library-wide file search silently return
 * nothing while appearing to work:
 *
 *  1. Reading the library from `_internalState` instead of `uiState`.
 *     `_internalState` never carries the library — only `libraryState`
 *     round-trips through it, so `rawLibraryFiles` is empty and every
 *     content query ran with `entries=0`.
 *  2. Indexing through the reader's shared extraction dir. With
 *     `parseContent=true` and no override the parsers run in ACTIVE_CACHE
 *     mode and call `resetActiveBookDir(bookId)`, which deletes the
 *     chapters of a book a reader currently has open.
 */
class GlobalSearchWiringTest {

    @Test
    fun `content search reads the library from the projected ui state`() {
        val source = readMain("MainViewModel.kt")
        assertTrue(
            "content search must use uiState (the DB projection fills rawLibraryFiles)",
            source.contains("globalSearchEntries(uiState.value.rawLibraryFiles)"),
        )
        assertFalse(
            "_internalState never carries the library, so every query would see entries=0",
            source.contains("globalSearchEntries(_internalState.value.rawLibraryFiles)"),
        )
    }

    @Test
    fun `index reconcile on toggle enable reads the projected ui state`() {
        val source = readMain("MainViewModel.kt")
        assertTrue(
            "reconcile must read the store: uiState has no subscriber at startup",
            source.contains("bookStore.getRecentFilesFlow().first()"),
        )
        // The reconcile path is what backfills books added while the toggle was off.
        assertTrue(source.contains("private fun reconcileGlobalSearchIndex("))
        assertTrue(source.contains("reconcileGlobalSearchIndex()"))
    }

    @Test
    fun `index worker never extracts into a shared active book dir`() {
        val source = readMain("search/GlobalSearchIndexWorker.kt")
        // Every parse path gets the private dir.
        assertTrue(source.contains("File(context.cacheDir, \"gsi_extract_\$bookId\")"))
        val overrideCount = Regex("extractionDirOverride = extractionDir,").findAll(source).count()
        assertTrue("all five parse paths override the extraction dir", overrideCount == 5)
        // And it is cleaned up.
        assertTrue(source.contains("extractionDir.deleteRecursively()"))
    }

    @Test
    fun `single file importer only resets the active dir when no override is given`() {
        val source = readMain("epub/SingleFileImporter.kt")
        val resets = Regex("ImportedFileCache\\.resetActiveBookDir\\(context, bookId\\)").findAll(source).count()
        val gated = Regex("if \\(extractionDirOverride == null\\) \\{").findAll(source).count()
        assertTrue("expected 4 reset sites, found $resets", resets == 4)
        assertTrue("each reset must be gated on no-override, found $gated gates", gated == resets)
    }

    @Test
    fun `inside-files toggle appears as soon as the search field takes focus`() {
        val topBar = readMain("SharedAndroidUnifiedTopBar.kt")
        val home = readMain("SharedAndroidUnifiedLibraryHome.kt")
        assertTrue(
            "focus must reveal the toggle, not just a non-empty query",
            topBar.contains("searchFocused || searchQuery.isNotEmpty()"),
        )
        assertTrue("the toggle is a labelled control, not an icon-only button", topBar.contains("searchInsideFilesLabel"))
        // Results mode replaces the grid with one row per matching book.
        assertTrue(home.contains("contentSearchActive"))
        assertTrue(home.contains("contentResultRow"))
    }

    private fun readMain(relativePath: String): String =
        listOf(
            File("src/main/java/com/aryan/reader/$relativePath"),
            File("app/src/main/java/com/aryan/reader/$relativePath"),
            File("shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/$relativePath"),
            File("../shared/src/commonMain/kotlin/com/aryan/reader/shared/ui/$relativePath"),
        ).firstOrNull { it.isFile }?.readText().orEmpty()
}
