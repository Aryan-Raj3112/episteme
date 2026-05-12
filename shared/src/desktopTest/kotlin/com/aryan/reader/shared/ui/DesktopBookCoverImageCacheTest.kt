package com.aryan.reader.shared.ui

import java.awt.Color
import java.awt.image.BufferedImage
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopBookCoverImageCacheTest {

    @Test
    fun `cover cache reloads when source fingerprint changes`() {
        val root = Files.createTempDirectory("reader-cover-cache").toFile()
        try {
            DesktopBookCoverImageCache.clearForTests()
            val cover = root.resolve("cover.png")
            writeImage(cover.absolutePath, width = 64, height = 64)

            val first = DesktopBookCoverImageCache.load(cover.absolutePath)
            assertNotNull(first)
            assertEquals(64, first.width)

            writeImage(cover.absolutePath, width = 96, height = 48)
            cover.setLastModified(cover.lastModified() + 2_000L)

            val second = DesktopBookCoverImageCache.load(cover.absolutePath)
            assertNotNull(second)
            assertEquals(96, second.width)
            assertEquals(48, second.height)
        } finally {
            DesktopBookCoverImageCache.clearForTests()
            root.deleteRecursively()
        }
    }

    @Test
    fun `large covers are cached at thumbnail size`() {
        val root = Files.createTempDirectory("reader-cover-cache").toFile()
        try {
            DesktopBookCoverImageCache.clearForTests()
            val cover = root.resolve("large-cover.png")
            writeImage(cover.absolutePath, width = 1_200, height = 800)

            val bitmap = DesktopBookCoverImageCache.load(cover.absolutePath)

            assertNotNull(bitmap)
            assertTrue(bitmap.width <= 512)
            assertTrue(bitmap.height <= 512)
        } finally {
            DesktopBookCoverImageCache.clearForTests()
            root.deleteRecursively()
        }
    }

    private fun writeImage(path: String, width: Int, height: Int) {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = Color(0x2A, 0x5C, 0x88)
            graphics.fillRect(0, 0, width, height)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", java.io.File(path))
    }
}
