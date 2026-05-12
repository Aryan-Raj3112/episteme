package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileCapabilitiesTest {

    @Test
    fun `shared file capabilities expose Android and desktop readable formats`() {
        assertEquals(
            PDF_VIEWER_FILE_TYPES + EPUB_READER_FILE_TYPES,
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID)
        )
        assertFalse(FileType.UNKNOWN in SharedFileCapabilities.knownFileTypes)
        assertFalse(FileType.UNKNOWN in SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID))
        assertNull(SharedFileCapabilities.primaryExtensionFor(FileType.UNKNOWN))
        assertNull(SharedFileCapabilities.mimeTypeFor(FileType.UNKNOWN))
        assertEquals("epub", SharedFileCapabilities.primaryExtensionFor(FileType.EPUB))
        assertEquals("application/pdf", SharedFileCapabilities.mimeTypeFor(FileType.PDF))
        assertEquals("pptx", SharedFileCapabilities.primaryExtensionFor(FileType.PPTX))
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            SharedFileCapabilities.mimeTypeFor(FileType.PPTX)
        )
        assertEquals(
            setOf(
                FileType.EPUB,
                FileType.PDF,
                FileType.TXT,
                FileType.MD,
                FileType.HTML,
                FileType.MOBI,
                FileType.FB2,
                FileType.CBZ,
                FileType.CBR,
                FileType.CB7,
                FileType.DOCX,
                FileType.ODT,
                FileType.FODT
            ),
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP)
        )
        assertEquals(
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.DESKTOP),
            SharedFileCapabilities.syncableTypesFor(ReaderPlatform.DESKTOP)
        )
    }

    @Test
    fun `shared file capabilities map reader surfaces per platform`() {
        assertEquals(
            ReaderFeatureSurface.PDF_VIEWER,
            SharedFileCapabilities.surfaceFor(FileType.PDF, ReaderPlatform.DESKTOP)
        )
        assertEquals(
            ReaderFeatureSurface.PDF_VIEWER,
            SharedFileCapabilities.surfaceFor(FileType.PPTX, ReaderPlatform.ANDROID)
        )
        assertNull(SharedFileCapabilities.surfaceFor(FileType.PPTX, ReaderPlatform.DESKTOP))
        assertEquals(
            ReaderFeatureSurface.TEXT_READER,
            SharedFileCapabilities.surfaceFor(FileType.MD, ReaderPlatform.DESKTOP)
        )
        assertEquals(
            ReaderFeatureSurface.TEXT_READER,
            SharedFileCapabilities.surfaceFor(FileType.DOCX, ReaderPlatform.DESKTOP)
        )
        assertEquals(
            ReaderFeatureSurface.PDF_VIEWER,
            SharedFileCapabilities.surfaceFor(FileType.CBR, ReaderPlatform.DESKTOP)
        )
        assertEquals(
            ReaderFeatureSurface.EPUB_READER,
            SharedFileCapabilities.surfaceFor(FileType.MD, ReaderPlatform.ANDROID)
        )
        assertTrue(SharedFileCapabilities.canOpen(FileType.CBZ, ReaderPlatform.ANDROID))
        assertTrue(SharedFileCapabilities.canOpen(FileType.CBZ, ReaderPlatform.DESKTOP))
    }

    @Test
    fun `shared file type resolver recognizes aliases used by desktop imports`() {
        assertEquals(FileType.MD, SharedFileCapabilities.fileTypeForName("notes.markdown"))
        assertEquals(FileType.HTML, SharedFileCapabilities.fileTypeForName("chapter.xhtml"))
        assertEquals(FileType.HTML, "chapter.xhtml".toFileType())
        assertEquals(FileType.MOBI, SharedFileCapabilities.fileTypeForName("book.azw3"))
        assertEquals(FileType.FB2, SharedFileCapabilities.fileTypeForName("book.fb2.zip"))
        assertEquals(FileType.PPTX, SharedFileCapabilities.fileTypeForName("slides.pptx"))
        assertEquals(FileType.HTML, SharedFileCapabilities.fileTypeForName("payload.json.txt"))
        assertEquals(FileType.EPUB, SharedFileCapabilities.fileTypeForName("book.epub.txt"))
        assertEquals(FileType.UNKNOWN, SharedFileCapabilities.fileTypeForName("archive.zip"))
    }

    @Test
    fun `shared file name policy detects manual only files and suffixes`() {
        assertTrue(SharedFileCapabilities.isCodeOrDataFileName("table.csv"))
        assertTrue(SharedFileCapabilities.isManualOnlyReaderFileName("script.kt.txt"))
        assertFalse(SharedFileCapabilities.isManualOnlyReaderFileName("chapter.html"))
        assertFalse(SharedFileCapabilities.isLocalFolderSyncEligibleFile("table.csv", "text/csv"))
        assertFalse(SharedFileCapabilities.isLocalFolderSyncEligibleFile("payload", "application/json"))
        assertTrue(SharedFileCapabilities.isLocalFolderSyncEligibleFile("book.fodt", "text/xml"))
        assertEquals(".md.txt", SharedFileCapabilities.fileExtensionSuffixForName("notes.md.txt"))
        assertEquals(".fb2.zip.txt", SharedFileCapabilities.fileExtensionSuffixForName("book.fb2.zip.txt"))
    }

    @Test
    fun `desktop parity gaps list Android readable formats not yet available on desktop`() {
        assertEquals(listOf(FileType.PPTX), SharedFileCapabilities.desktopParityGaps())
    }
}
