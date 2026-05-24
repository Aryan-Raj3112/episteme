package com.aryan.reader

import com.aryan.reader.data.BookMetadata
import com.aryan.reader.data.RecentFileItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudEpubAnnotationMetadataTest {
    @Test
    fun `remote epub annotations fill local null annotation fields without changing timestamp`() {
        val local = localBook(
            highlightsJson = null,
            bookmarksJson = null,
            lastModifiedTimestamp = 2_000L
        )
        val remote = remoteBook(
            highlightsJson = """[{"cfi":"/4/2:1"}]""",
            bookmarksJson = """["{\"cfi\":\"/4/2\",\"chapterTitle\":\"One\",\"snippet\":\"A\",\"chapterIndex\":0}"]""",
            lastModifiedTimestamp = 3_000L
        )

        val merged = local.mergeRemoteEpubAnnotationMetadata(remote)

        assertEquals(remote.highlightsJson, merged.highlightsJson)
        assertEquals(remote.bookmarksJson, merged.bookmarksJson)
        assertEquals(2_000L, merged.lastModifiedTimestamp)
    }

    @Test
    fun `explicit local empty epub annotations are not replaced by remote annotations`() {
        val local = localBook(
            highlightsJson = "[]",
            bookmarksJson = "[]"
        )
        val remote = remoteBook(
            highlightsJson = """[{"cfi":"/4/2:1"}]""",
            bookmarksJson = """["{\"cfi\":\"/4/2\",\"chapterTitle\":\"One\",\"snippet\":\"A\",\"chapterIndex\":0}"]"""
        )

        val merged = local.mergeRemoteEpubAnnotationMetadata(remote)

        assertEquals("[]", merged.highlightsJson)
        assertEquals("[]", merged.bookmarksJson)
    }

    @Test
    fun `non epub books do not use epub annotation preservation guard`() {
        val local = localBook(type = FileType.PDF, highlightsJson = null)
        val remote = remoteBook(type = FileType.PDF.name, highlightsJson = """[{"cfi":"/4/2:1"}]""")

        assertFalse(local.needsRemoteEpubAnnotationMetadataGuard())
        assertEquals(local, local.mergeRemoteEpubAnnotationMetadata(remote))
    }

    @Test
    fun `blank and empty annotation json are equivalent noops`() {
        assertTrue(annotationJsonEquivalentForNoop(null, "[]"))
        assertTrue(annotationJsonEquivalentForNoop("", "[]"))
        assertFalse(annotationJsonEquivalentForNoop("""[{"id":"h1"}]""", "[]"))
    }

    private fun localBook(
        type: FileType = FileType.EPUB,
        highlightsJson: String? = null,
        bookmarksJson: String? = null,
        lastModifiedTimestamp: Long = 1_000L
    ): RecentFileItem {
        return RecentFileItem(
            bookId = "book-1",
            uriString = "content://book",
            type = type,
            displayName = "Book.epub",
            timestamp = 1_000L,
            lastModifiedTimestamp = lastModifiedTimestamp,
            bookmarksJson = bookmarksJson,
            highlightsJson = highlightsJson
        )
    }

    private fun remoteBook(
        type: String = FileType.EPUB.name,
        highlightsJson: String? = null,
        bookmarksJson: String? = null,
        lastModifiedTimestamp: Long = 2_000L
    ): BookMetadata {
        return BookMetadata(
            bookId = "book-1",
            displayName = "Book.epub",
            type = type,
            lastModifiedTimestamp = lastModifiedTimestamp,
            bookmarksJson = bookmarksJson,
            highlightsJson = highlightsJson
        )
    }
}
