package com.aryan.reader.epub

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImportedFileCacheTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `active directory names are sanitized stable and marked active`() {
        val first = ImportedFileCache.activeBookDirName("Book:/One?*")
        val second = ImportedFileCache.activeBookDirName("Book:/One?*")

        assertTrue(first.startsWith("imported_file_"))
        assertFalse(first.contains(":"))
        assertFalse(first.contains("/"))
        assertFalse(first.contains("?"))
        assertFalse(first.contains("*"))
        assertTrue(ImportedFileCache.isActiveBookDir(first))
        assertFalse(ImportedFileCache.isTemporaryBookDir(first))
        assertTrue(first == second)
    }

    @Test
    fun `prepareDirectory clears stale contents before reusing directory`() {
        val dir = temp.newFolder("active")
        File(dir, "old.xhtml").writeText("stale")

        val prepared = ImportedFileCache.prepareDirectory(dir)

        assertTrue(prepared.isDirectory)
        assertTrue(prepared.listFiles().isNullOrEmpty())
    }

    @Test
    fun `ensureActiveBookDir preserves active contents and resetActiveBookDir clears them`() {
        val context = contextWithCache(temp.newFolder("ensure-active-cache"))
        val active = ImportedFileCache.ensureActiveBookDir(context, "Book")
        File(active, "book_metadata.json").writeText("cached")

        val ensuredAgain = ImportedFileCache.ensureActiveBookDir(context, "Book")

        assertEquals("cached", File(ensuredAgain, "book_metadata.json").readText())

        val reset = ImportedFileCache.resetActiveBookDir(context, "Book")

        assertTrue(reset.isDirectory)
        assertTrue(reset.listFiles().isNullOrEmpty())
    }

    @Test
    fun `temporary directory creation and targeted cleanup only remove matching book marker`() {
        val context = contextWithCache(temp.newFolder("cache"))
        val firstBookTemp = ImportedFileCache.createTemporaryBookDir(context, "Book One", "preview/import")
        val secondBookTemp = ImportedFileCache.createTemporaryBookDir(context, "Book Two", "preview/import")
        File(firstBookTemp, "file.txt").writeText("one")
        File(secondBookTemp, "file.txt").writeText("two")

        runBlocking { ImportedFileCache.clearTemporaryBookDirs(context, "Book One") }

        assertFalse(firstBookTemp.exists())
        assertTrue(secondBookTemp.exists())
        assertTrue(ImportedFileCache.isTemporaryBookDir(secondBookTemp.name))
        assertFalse(ImportedFileCache.isActiveBookDir(secondBookTemp.name))
    }

    @Test
    fun `deleteStaleTemporaryBookDirs removes old temporary dirs and keeps fresh and active dirs`() {
        val cacheDir = temp.newFolder("stale-cache")
        val context = contextWithCache(cacheDir)
        val staleTemp = ImportedFileCache.createTemporaryBookDir(context, "Book", "stale")
        val freshTemp = ImportedFileCache.createTemporaryBookDir(context, "Book", "fresh")
        val activeDir = ImportedFileCache.prepareActiveBookDir(context, "Book")
        val now = 10_000L
        staleTemp.setLastModified(1_000L)
        freshTemp.setLastModified(9_500L)
        activeDir.setLastModified(1_000L)

        ImportedFileCache.deleteStaleTemporaryBookDirs(context, olderThanMillis = 5_000L, nowMillis = now)

        assertFalse(staleTemp.exists())
        assertTrue(freshTemp.exists())
        assertTrue(activeDir.exists())
    }

    @Test
    fun `clearBookCache removes active legacy and temporary cache directories`() {
        val cacheDir = temp.newFolder("clear-book-cache")
        val context = contextWithCache(cacheDir)
        val active = ImportedFileCache.prepareActiveBookDir(context, "Book")
        val legacy = File(cacheDir, "imported_file_Book").apply { mkdirs() }
        val temporary = ImportedFileCache.createTemporaryBookDir(context, "Book", "tmp")
        File(active, "active.txt").writeText("active")
        File(legacy, "legacy.txt").writeText("legacy")
        File(temporary, "temporary.txt").writeText("temporary")

        runBlocking { ImportedFileCache.clearBookCache(context, "Book") }

        assertFalse(active.exists())
        assertFalse(legacy.exists())
        assertFalse(temporary.exists())
    }

    @Test
    fun `clear fns hop to IO so main-dispatcher cleanup cannot ANR on recursive deletes`() {
        // Book cleanup reaches clearBookCache from the main dispatcher; per-file `remove`
        // syscalls on thousands of extracted files ANR there
        // (see docs/crashlytics-triage.md#18). The I/O boundary must own the hop.
        val source = sourceFile("com/aryan/reader/epub/ImportedFileCache.kt").readText()
        listOf("fun clearBookCache", "fun clearTemporaryBookDirs").forEach { signature ->
            val body = source.substringAfter(signature).substringBefore("\n    }\n")
            assertTrue(
                "$signature must switch to Dispatchers.IO before deleting",
                body.contains("withContext(Dispatchers.IO)")
            )
        }
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }

    private fun contextWithCache(cacheDir: File): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return context
    }
}
