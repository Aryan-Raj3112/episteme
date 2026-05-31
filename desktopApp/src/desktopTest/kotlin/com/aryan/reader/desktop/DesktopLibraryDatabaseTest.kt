package com.aryan.reader.desktop

import com.aryan.reader.shared.SharedLibrarySnapshot
import com.aryan.reader.shared.SharedLibrarySnapshotJson
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopLibraryDatabaseTest {
    @Test
    fun `save writes readable library and backup snapshots`() {
        val databaseFile = Files.createTempDirectory("reader-library-db")
            .resolve("library.json")
            .toFile()
        val database = DesktopLibraryDatabase(databaseFile)
        val snapshot = SharedLibrarySnapshot(
            recentFilesLimit = 37,
            openTabIds = listOf("book-a"),
            activeTabBookId = "book-a"
        )

        database.save(snapshot)

        val loaded = database.load()
        assertEquals(37, loaded.recentFilesLimit)
        assertEquals(listOf("book-a"), loaded.openTabIds)
        assertEquals("book-a", loaded.activeTabBookId)
        assertTrue(databaseFile.isFile)
        assertTrue(databaseFile.parentFile.resolve("library.json.bak").isFile)
    }

    @Test
    fun `load falls back to backup when primary library is corrupt`() {
        val databaseFile = Files.createTempDirectory("reader-library-db-corrupt")
            .resolve("library.json")
            .toFile()
        val backupSnapshot = SharedLibrarySnapshot(
            recentFilesLimit = 19,
            openTabIds = listOf("backup-book"),
            activeTabBookId = "backup-book"
        )
        databaseFile.parentFile.mkdirs()
        databaseFile.writeText("""{"books":[""")
        databaseFile.parentFile
            .resolve("library.json.bak")
            .writeText(SharedLibrarySnapshotJson.encode(backupSnapshot))

        val loaded = DesktopLibraryDatabase(databaseFile).load()

        assertEquals(19, loaded.recentFilesLimit)
        assertEquals(listOf("backup-book"), loaded.openTabIds)
        assertEquals("backup-book", loaded.activeTabBookId)
    }
}
