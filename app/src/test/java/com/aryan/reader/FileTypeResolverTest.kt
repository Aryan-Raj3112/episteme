package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(com.aryan.reader.shared.SharedFileCapabilities.resolveFileTypeForName("payload.json.txt"), resolveFileTypeFromName("payload.json.txt"))
    }

    @Test
    fun `metadata resolver maps provider mime types without exposing generic archives`() {
        assertEquals(FileType.PDF, resolveFileTypeFromMetadata("download", "application/pdf"))
        assertEquals(FileType.DOCX, resolveFileTypeFromMetadata("download", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
        assertEquals(FileType.PPTX, resolveFileTypeFromMetadata("download", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertEquals(FileType.MD, resolveFileTypeFromMetadata("notes.markdown.txt", "text/plain; charset=utf-8"))
        assertEquals(FileType.EPUB, resolveFileTypeFromMetadata("book.epub.txt", "text/plain"))
        assertEquals(FileType.TXT, resolveFileTypeFromMetadata("notes", "text/plain"))
        assertEquals(FileType.HTML, resolveFileTypeFromMetadata("payload", "application/json"))
        assertEquals(FileType.CBZ, resolveFileTypeFromMetadata("comic.cbz", "application/zip"))
        assertEquals(FileType.FB2, resolveFileTypeFromMetadata("book.fb2.zip", "application/zip"))
        assertNull(resolveFileTypeFromMetadata("archive.zip", "application/zip"))
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
        assertEquals(FileType.PPTX, resolveFileTypeFromName("deck.pptx"))
        assertEquals(FileType.TXT, resolveFileTypeFromName("archive.unknown.txt"))
        assertNull(resolveFileTypeFromName("archive.zip"))
    }

    @Test
    fun `extension suffix preserves transparent txt wrapper`() {
        assertEquals(".md.txt", resolveFileExtensionSuffixFromName("notes.md.txt"))
        assertEquals(".html.txt", resolveFileExtensionSuffixFromName("chapter.html.txt"))
        assertEquals(".txt", resolveFileExtensionSuffixFromName("notes.txt"))
    }
}
