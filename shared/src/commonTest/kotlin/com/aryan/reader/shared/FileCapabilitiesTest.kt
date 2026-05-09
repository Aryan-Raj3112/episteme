package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FileCapabilitiesTest {

    @Test
    fun `shared file capabilities expose Android and desktop readable formats`() {
        assertEquals(
            PDF_VIEWER_FILE_TYPES + EPUB_READER_FILE_TYPES,
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID)
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
        assertEquals(FileType.UNKNOWN, SharedFileCapabilities.fileTypeForName("archive.zip"))
    }

    @Test
    fun `desktop parity gaps list Android readable formats not yet available on desktop`() {
        assertEquals(emptyList(), SharedFileCapabilities.desktopParityGaps())
    }
}
