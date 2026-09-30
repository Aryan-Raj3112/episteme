package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFolderSidecarsTest {
    @Test
    fun `sync dir sits beside the books`() {
        assertEquals("EpistemeSyncData", LocalFolderSidecars.syncDirName())
        assertEquals("/books/EpistemeSyncData", LocalFolderSidecars.syncDirPath("/books"))
        assertEquals("/books/EpistemeSyncData", LocalFolderSidecars.syncDirPath("/books/"))
    }

    @Test
    fun `sidecar names match the shared android-compatible helpers`() {
        val bookId = "local_Book.pdf"
        assertEquals(
            localFolderSyncMetadataFileName(bookId),
            LocalFolderSidecars.metadataFileName(bookId)
        )
        assertEquals(
            localFolderSyncAnnotationFileName(bookId),
            LocalFolderSidecars.annotationFileName(bookId)
        )
    }

    @Test
    fun `dot-prefixed sidecars are classified as sidecars`() {
        val bookId = "local_Book.pdf"
        assertTrue(
            LocalFolderSidecars.isSidecarName(LocalFolderSidecars.metadataFileName(bookId))
        )
        assertTrue(
            LocalFolderSidecars.isSidecarName(LocalFolderSidecars.annotationFileName(bookId))
        )
        assertTrue(LocalFolderSidecars.isSidecarName("EpistemeSyncData"))
    }

    @Test
    fun `real books are never classified as sidecars`() {
        // A book whose name merely looks similar must still be imported,
        // otherwise adding such a folder would silently produce an empty shelf.
        listOf(
            "Book.epub",
            "book_1234.json",
            "My book.epub",
            "notes.json",
            "EpistemeSyncData.epub",
            "EpistemeSyncDatas",
        ).forEach { name ->
            assertFalse(
                LocalFolderSidecars.isSidecarName(name),
                "[$name] was wrongly treated as a sidecar",
            )
        }
    }

    @Test
    fun `anything under the sync dir is excluded at any depth`() {
        assertTrue(LocalFolderSidecars.isInsideSyncDir("EpistemeSyncData"))
        assertTrue(LocalFolderSidecars.isInsideSyncDir("EpistemeSyncData/.book_abc.json"))
        assertTrue(LocalFolderSidecars.isInsideSyncDir("Series/EpistemeSyncData/.book_abc.json"))
        assertFalse(LocalFolderSidecars.isInsideSyncDir("Series/Book.epub"))
        // A file merely named like the directory, in a different place.
        assertFalse(LocalFolderSidecars.isInsideSyncDir("Series/EpistemeSyncData.epub"))
    }
}
