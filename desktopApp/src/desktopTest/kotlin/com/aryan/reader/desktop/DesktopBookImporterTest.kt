package com.aryan.reader.desktop

import com.aryan.reader.shared.ImportedBookFile
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopBookImporterTest {
    @Test
    fun `prepare imports copies supported file into app storage without source folder`() {
        val tempRoot = Files.createTempDirectory("episteme-book-importer-test").toFile()
        try {
            val source = File(tempRoot, "Book.md").apply { writeText("# Hello") }
            val store = File(tempRoot, "books")
            val importer = DesktopBookImporter(store)

            val result = importer.prepareImports(listOf(source.toImportedBookFile()))

            assertEquals(0, result.failedCount)
            val prepared = result.files.single()
            val copied = File(assertNotNull(prepared.localPath))
            assertEquals("Book.md", prepared.name)
            assertEquals(source.sha256(), prepared.id)
            assertNull(prepared.uriString)
            assertNull(prepared.sourceFolder)
            assertEquals(store.canonicalFile, copied.parentFile.canonicalFile)
            assertNotEquals(source.canonicalFile, copied.canonicalFile)
            assertEquals("# Hello", copied.readText())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `prepare imports leaves unsupported files uncopied for shared planner`() {
        val tempRoot = Files.createTempDirectory("episteme-book-importer-test").toFile()
        try {
            val source = File(tempRoot, "Archive.zip").apply { writeText("zip") }
            val store = File(tempRoot, "books")
            val importer = DesktopBookImporter(store)

            val result = importer.prepareImports(listOf(source.toImportedBookFile(sourceFolder = tempRoot.absolutePath)))

            assertEquals(0, result.failedCount)
            val prepared = result.files.single()
            assertEquals(source.absolutePath, prepared.localPath)
            assertNull(prepared.sourceFolder)
            assertNull(prepared.id)
            assertFalse(store.listFiles().orEmpty().any { it.isFile })
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `delete imported file removes managed file when nothing else references it`() {
        val tempRoot = Files.createTempDirectory("episteme-book-importer-test").toFile()
        try {
            val store = File(tempRoot, "books")
            val importer = DesktopBookImporter(store)
            val managed = importer.createBookFile("abc.epub").apply { writeText("book") }

            val deleted = importer.deleteImportedBookFileIfUnreferenced(
                path = managed.absolutePath,
                otherReferencingPaths = listOf("/elsewhere/other.epub")
            )

            assertTrue(deleted)
            assertFalse(managed.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `delete imported file keeps content addressed storage shared between entries`() {
        val tempRoot = Files.createTempDirectory("episteme-book-importer-test").toFile()
        try {
            val store = File(tempRoot, "books")
            val importer = DesktopBookImporter(store)
            val managed = importer.createBookFile("shared-hash.epub").apply { writeText("book") }

            val deleted = importer.deleteImportedBookFileIfUnreferenced(
                path = managed.absolutePath,
                otherReferencingPaths = listOf(
                    "/unrelated/other.epub",
                    File(managed.parentFile, "./${managed.name}").absolutePath
                )
            )

            assertFalse(deleted)
            assertTrue(managed.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `delete imported file never touches user owned paths outside managed storage`() {
        val tempRoot = Files.createTempDirectory("episteme-book-importer-test").toFile()
        try {
            val importer = DesktopBookImporter(File(tempRoot, "books"))
            val userOwned = File(tempRoot, "folder-sync/Book.epub").apply {
                parentFile?.mkdirs()
                writeText("user data")
            }

            val deleted = importer.deleteImportedBookFileIfUnreferenced(
                path = userOwned.absolutePath,
                otherReferencingPaths = emptyList()
            )

            assertFalse(deleted)
            assertTrue(userOwned.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun File.toImportedBookFile(sourceFolder: String? = null): ImportedBookFile {
        return ImportedBookFile(
            name = name,
            uriString = null,
            localPath = absolutePath,
            size = length(),
            sourceFolder = sourceFolder
        )
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(readBytes())
        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
