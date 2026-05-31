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
        assertEquals(
            PDF_VIEWER_FILE_TYPES,
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID, ReaderFeatureSurface.PDF_VIEWER)
        )
        assertEquals(
            EPUB_READER_FILE_TYPES,
            SharedFileCapabilities.readableTypesFor(ReaderPlatform.ANDROID, ReaderFeatureSurface.EPUB_READER)
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
        assertTrue("application/pdf" in SharedFileCapabilities.androidFilePickerMimeTypes)
        assertTrue("application/x-tar" in SharedFileCapabilities.androidFilePickerMimeTypes)
        assertTrue("text/x-kotlin" in SharedFileCapabilities.androidFilePickerMimeTypes)
        assertFalse("*/*" in SharedFileCapabilities.androidFilePickerMimeTypes)
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
                FileType.CBT,
                FileType.DOCX,
                FileType.PPTX,
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
        assertEquals(
            ReaderFeatureSurface.PDF_VIEWER,
            SharedFileCapabilities.surfaceFor(FileType.PPTX, ReaderPlatform.DESKTOP)
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
            SharedFileCapabilities.surfaceFor(FileType.CBT, ReaderPlatform.DESKTOP)
        )
        assertEquals(
            ReaderFeatureSurface.EPUB_READER,
            SharedFileCapabilities.surfaceFor(FileType.MD, ReaderPlatform.ANDROID)
        )
        assertTrue(SharedFileCapabilities.canOpen(FileType.CBZ, ReaderPlatform.ANDROID))
        assertTrue(SharedFileCapabilities.canOpen(FileType.CBT, ReaderPlatform.ANDROID))
        assertTrue(SharedFileCapabilities.canOpen(FileType.CBT, ReaderPlatform.DESKTOP))
        assertTrue(SharedFileCapabilities.isComicArchive(FileType.CBT))
    }

    @Test
    fun `shared file type resolver recognizes aliases used by desktop imports`() {
        assertEquals(FileType.MD, SharedFileCapabilities.fileTypeForName("notes.markdown"))
        assertEquals(FileType.HTML, SharedFileCapabilities.fileTypeForName("chapter.xhtml"))
        assertEquals(FileType.HTML, "chapter.xhtml".toFileType())
        assertEquals(FileType.MOBI, SharedFileCapabilities.fileTypeForName("book.azw3"))
        assertEquals(FileType.FB2, SharedFileCapabilities.fileTypeForName("book.fb2.zip"))
        assertEquals(FileType.CBT, SharedFileCapabilities.fileTypeForName("comic.cbt"))
        assertEquals(FileType.PPTX, SharedFileCapabilities.fileTypeForName("slides.pptx"))
        assertEquals(FileType.HTML, SharedFileCapabilities.fileTypeForName("payload.json.txt"))
        assertEquals(FileType.EPUB, SharedFileCapabilities.fileTypeForName("book.epub.txt"))
        assertEquals(FileType.UNKNOWN, SharedFileCapabilities.fileTypeForName("archive.zip"))
    }

    @Test
    fun `shared metadata resolver handles provider mime types and guarded archive fallbacks`() {
        assertEquals(FileType.PDF, SharedFileCapabilities.resolveFileTypeForMetadata("download", "application/pdf"))
        assertEquals(
            FileType.DOCX,
            SharedFileCapabilities.resolveFileTypeForMetadata(
                "download",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
        assertEquals(
            FileType.MD,
            SharedFileCapabilities.resolveFileTypeForMetadata("notes.md.txt", "text/plain; charset=utf-8")
        )
        assertEquals(FileType.HTML, SharedFileCapabilities.resolveFileTypeForMetadata("payload", "application/json"))
        assertEquals(FileType.CBZ, SharedFileCapabilities.resolveFileTypeForMetadata("comic.cbz", "application/zip"))
        assertEquals(FileType.CBT, SharedFileCapabilities.resolveFileTypeForMetadata("comic.cbt", "application/x-tar"))
        assertEquals(FileType.FB2, SharedFileCapabilities.resolveFileTypeForMetadata("book.fb2.zip", "application/zip"))
        assertNull(SharedFileCapabilities.resolveFileTypeForMetadata("archive.zip", "application/zip"))
        assertNull(SharedFileCapabilities.resolveFileTypeForMetadata("archive.tar", "application/x-tar"))
    }

    @Test
    fun `shared file name policy detects manual only files and suffixes`() {
        assertTrue(SharedFileCapabilities.isCodeOrDataFileName("table.csv"))
        assertTrue(SharedFileCapabilities.isManualOnlyReaderFileName("script.kt.txt"))
        assertFalse(SharedFileCapabilities.isManualOnlyReaderFileName("chapter.html"))
        assertFalse(SharedFileCapabilities.isLocalFolderSyncEligibleFile("table.csv", "text/csv"))
        assertFalse(SharedFileCapabilities.isLocalFolderSyncEligibleFile("payload", "application/json; charset=utf-8"))
        assertTrue(SharedFileCapabilities.isLocalFolderSyncEligibleFile("book.fodt", "text/xml"))
        assertEquals(".md.txt", SharedFileCapabilities.fileExtensionSuffixForName("notes.md.txt"))
        assertEquals(".fb2.zip.txt", SharedFileCapabilities.fileExtensionSuffixForName("book.fb2.zip.txt"))
    }

    @Test
    fun `desktop parity gaps list Android readable formats not yet available on desktop`() {
        assertEquals(emptyList(), SharedFileCapabilities.desktopParityGaps())
    }
}
