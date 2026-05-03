package com.aryan.reader

import org.junit.Assert.assertEquals
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
