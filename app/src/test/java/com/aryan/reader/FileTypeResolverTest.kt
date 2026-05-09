package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTypeResolverTest {

    @Test
    fun `transparent txt suffix preserves supported inner extension`() {
        assertEquals(FileType.MD, resolveFileTypeFromName("notes.md.txt"))
        assertEquals(FileType.HTML, resolveFileTypeFromName("chapter.html.txt"))
        assertEquals(FileType.HTML, resolveFileTypeFromName("snippet.js.txt"))
        assertEquals(FileType.EPUB, resolveFileTypeFromName("book.epub.txt"))
    }

    @Test
    fun `code and data files resolve for manual viewing`() {
        assertEquals(FileType.HTML, resolveFileTypeFromName("table.csv"))
        assertEquals(FileType.HTML, resolveFileTypeFromName("script.kt"))
        assertEquals(FileType.HTML, resolveFileTypeFromName("payload.json.txt"))
    }

    @Test
    fun `manual only reader files are excluded from folder sync eligibility`() {
        assertTrue(isManualOnlyReaderFileName("table.csv"))
        assertTrue(isManualOnlyReaderFileName("script.kt.txt"))
        assertFalse(isManualOnlyReaderFileName("chapter.html"))
        assertFalse(isManualOnlyReaderFileName("notes.txt"))
        assertFalse(isManualOnlyReaderFileName("book.fodt"))

        assertFalse(isLocalFolderSyncEligibleFile("table.csv", "text/csv"))
        assertFalse(isLocalFolderSyncEligibleFile("payload", "application/json"))
        assertTrue(isLocalFolderSyncEligibleFile("chapter.html", "text/html"))
        assertTrue(isLocalFolderSyncEligibleFile("book.fodt", "text/xml"))
    }

    @Test
    fun `plain txt remains txt when inner extension is unsupported`() {
        assertEquals(FileType.TXT, resolveFileTypeFromName("notes.txt"))
        assertEquals(FileType.TXT, resolveFileTypeFromName("archive.unknown.txt"))
    }

    @Test
    fun `extension suffix preserves transparent txt wrapper`() {
        assertEquals(".md.txt", resolveFileExtensionSuffixFromName("notes.md.txt"))
        assertEquals(".html.txt", resolveFileExtensionSuffixFromName("chapter.html.txt"))
        assertEquals(".txt", resolveFileExtensionSuffixFromName("notes.txt"))
    }
}
