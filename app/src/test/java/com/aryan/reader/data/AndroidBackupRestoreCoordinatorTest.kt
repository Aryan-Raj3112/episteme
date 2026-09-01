package com.aryan.reader.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidBackupRestoreCoordinatorTest {
    @Test
    fun `legacy root rich text and reflow files move into named directories`() {
        val filesDir = tempRoot("legacy-artifacts")
        try {
            val richText = File(filesDir, "rich_doc_book_1.json").apply { writeText("rich") }
            val reflow = File(filesDir, "book_1_reflow.html").apply { writeText("html") }

            assertEquals(2, AndroidBackupRestoreCoordinator.migrateLegacyRootArtifacts(filesDir))

            val migratedRichText = File(filesDir, "annotations/rich_doc/rich_doc_book_1.json")
            val migratedReflow = File(filesDir, "derived/reflow/book_1_reflow.html")
            assertTrue(migratedRichText.isFile)
            assertTrue(migratedReflow.isFile)
            assertFalse(richText.exists())
            assertFalse(reflow.exists())
            assertEquals("rich", migratedRichText.readText())
            assertEquals("html", migratedReflow.readText())
            assertEquals(0, AndroidBackupRestoreCoordinator.migrateLegacyRootArtifacts(filesDir))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `migration never overwrites a destination created by the new layout`() {
        val filesDir = tempRoot("legacy-conflict")
        try {
            File(filesDir, "rich_doc_book.json").writeText("old")
            File(filesDir, "annotations/rich_doc/rich_doc_book.json")
                .apply { parentFile?.mkdirs(); writeText("new") }

            assertEquals(0, AndroidBackupRestoreCoordinator.migrateLegacyRootArtifacts(filesDir))
            assertEquals("new", File(filesDir, "annotations/rich_doc/rich_doc_book.json").readText())
            assertTrue(File(filesDir, "rich_doc_book.json").isFile)
            assertTrue(filesDir.walkTopDown().none { it.name.startsWith(".backup-restore-") })
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `artifact paths sanitize book ids and stay below named directories`() {
        val filesDir = tempRoot("artifact-paths")
        try {
            val richText = AndroidBookArtifactPaths.richTextFile(filesDir, "../private/book?.pdf")
            val reflow = AndroidBookArtifactPaths.reflowFile(filesDir, "../private/book?.pdf")

            assertTrue(richText.path.startsWith(File(filesDir, "annotations/rich_doc").path))
            assertTrue(reflow.path.startsWith(File(filesDir, "derived/reflow").path))
            assertFalse(richText.relativeTo(filesDir).path.split(File.separator).contains(".."))
            assertFalse(reflow.relativeTo(filesDir).path.split(File.separator).contains(".."))
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun `reconciliation only treats missing app-private originals as definitive`() {
        val filesDir = tempRoot("reconciliation-scope")
        try {
            assertTrue(
                AndroidBackupRestoreCoordinator.isMissingExcludedPrivatePath(
                    filesDir,
                    File(filesDir, "books/missing.epub").path,
                ),
            )
            assertFalse(
                AndroidBackupRestoreCoordinator.isMissingExcludedPrivatePath(
                    filesDir,
                    File(filesDir.parentFile, "missing.epub").path,
                ),
            )
            assertFalse(
                AndroidBackupRestoreCoordinator.isMissingExcludedPrivateFile(
                    filesDir,
                    "content://com.example.provider/document/temporary",
                ),
            )
        } finally {
            filesDir.deleteRecursively()
        }
    }

    private fun tempRoot(name: String): File =
        File("build/test-tmp/backup-restore/$name-${System.nanoTime()}").apply { mkdirs() }
}
