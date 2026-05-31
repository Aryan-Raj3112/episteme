package com.aryan.reader.desktop

import com.aryan.reader.shared.FileType
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import java.io.File
import java.nio.file.Files
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopComicArchiveTest {
    @Test
    fun `cbz archive loads image pages for pdf reader surface`() = withTempDir { dir ->
        val cbz = File(dir, "comic.cbz")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("pages/001.png"))
            zip.write(onePixelPngBytes())
            zip.closeEntry()
        }

        val document = DesktopPdfium.loadComic(cbz, FileType.CBZ)
        try {
            assertEquals(1, document.pageCount)
            assertEquals(1f, document.pageSizes.single().width)
            assertEquals(1f, document.pageSizes.single().height)

            val image = DesktopPdfium.renderPageBufferedImage(document, pageIndex = 0, scale = 8f)

            assertEquals(8, image.width)
            assertEquals(8, image.height)
        } finally {
            document.close()
        }
    }

    @Test
    fun `closing stale comic document does not close replacement with same path`() = withTempDir { dir ->
        val cbz = File(dir, "comic.cbz")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("pages/001.png"))
            zip.write(onePixelPngBytes())
            zip.closeEntry()
        }

        val staleDocument = DesktopPdfium.loadComic(cbz, FileType.CBZ)
        val activeDocument = DesktopPdfium.loadComic(cbz, FileType.CBZ)
        try {
            staleDocument.close()

            val image = DesktopPdfium.renderPageBufferedImage(activeDocument, pageIndex = 0, scale = 4f)

            assertEquals(4, image.width)
            assertEquals(4, image.height)
        } finally {
            staleDocument.close()
            activeDocument.close()
        }
    }

    @Test
    fun `cbt archive loads image pages for pdf reader surface`() = withTempDir { dir ->
        val cbt = File(dir, "comic.cbt")
        TarArchiveOutputStream(cbt.outputStream()).use { tar ->
            val bytes = onePixelPngBytes()
            val entry = TarArchiveEntry("pages/001.png").apply {
                size = bytes.size.toLong()
            }
            tar.putArchiveEntry(entry)
            tar.write(bytes)
            tar.closeArchiveEntry()
            tar.finish()
        }

        val document = DesktopPdfium.loadComic(cbt, FileType.CBT)
        try {
            assertEquals(1, document.pageCount)
            assertEquals(1f, document.pageSizes.single().width)
            assertEquals(1f, document.pageSizes.single().height)

            val image = DesktopPdfium.renderPageBufferedImage(document, pageIndex = 0, scale = 8f)

            assertEquals(8, image.width)
            assertEquals(8, image.height)
        } finally {
            document.close()
        }
    }

    @Test
    fun `desktop comic types are routed through shared reader capability map`() {
        assertTrue(DesktopComicArchive.canLoad(FileType.CBZ))
        assertTrue(DesktopComicArchive.canLoad(FileType.CBR))
        assertTrue(DesktopComicArchive.canLoad(FileType.CB7))
        assertTrue(DesktopComicArchive.canLoad(FileType.CBT))
    }

    private fun withTempDir(block: (File) -> Unit) {
        val dir = Files.createTempDirectory("reader-desktop-comic").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun onePixelPngBytes(): ByteArray {
        return Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
        )
    }
}
