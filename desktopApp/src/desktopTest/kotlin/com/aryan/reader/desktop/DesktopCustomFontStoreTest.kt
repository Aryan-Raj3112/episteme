package com.aryan.reader.desktop

import com.aryan.reader.shared.CustomFontItem
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCustomFontStoreTest {
    @Test
    fun `import font copies supported file into desktop font store`() {
        val tempRoot = Files.createTempDirectory("episteme-font-store-test").toFile()
        try {
            val source = File(tempRoot, "Literata.ttf").apply { writeText("font-bytes") }
            val store = DesktopCustomFontStore(File(tempRoot, "store"))

            val font = store.importFont(source).getOrThrow()

            assertEquals("Literata", font.displayName)
            assertEquals("ttf", font.fileExtension)
            assertTrue(File(font.path).isFile)
            assertEquals("font-bytes", File(font.path).readText())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `import font rejects unsupported extension`() {
        val tempRoot = Files.createTempDirectory("episteme-font-store-test").toFile()
        try {
            val source = File(tempRoot, "not-a-font.txt").apply { writeText("nope") }
            val store = DesktopCustomFontStore(File(tempRoot, "store"))

            assertTrue(store.importFont(source).isFailure)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `delete font only removes files inside desktop font store`() {
        val tempRoot = Files.createTempDirectory("episteme-font-store-test").toFile()
        try {
            val storeDir = File(tempRoot, "store").apply { mkdirs() }
            val stored = File(storeDir, "font_a.ttf").apply { writeText("stored") }
            val outside = File(tempRoot, "outside.ttf").apply { writeText("outside") }
            val store = DesktopCustomFontStore(storeDir)

            assertTrue(store.deleteFont(stored.toFontItem()))
            assertFalse(stored.exists())
            assertFalse(store.deleteFont(outside.toFontItem()))
            assertTrue(outside.exists())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `google font css parser extracts first https font url`() {
        val css = """
            @font-face {
              font-family: 'Literata';
              src: url(https://fonts.gstatic.com/s/literata/v35/font.ttf) format('truetype');
            }
        """.trimIndent()

        assertEquals("https://fonts.gstatic.com/s/literata/v35/font.ttf", googleFontDownloadUrlFromCss(css))
        assertEquals("ttf", googleFontFileExtension("https://fonts.gstatic.com/s/literata/v35/font.ttf?foo=bar"))
    }

    @Test
    fun `google fonts json parser ignores blank names`() {
        assertEquals(listOf("Inter", "Literata"), googleFontsFromJson("""["Inter", "", " Literata "]"""))
    }

    @Test
    fun `download google font fails before network when downloads are disabled`() {
        val tempRoot = Files.createTempDirectory("episteme-font-store-test").toFile()
        try {
            val store = DesktopCustomFontStore(
                fontsDir = File(tempRoot, "store"),
                googleFontsDownloadAvailable = { false }
            )

            assertTrue(store.downloadGoogleFont("Inter").isFailure)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun File.toFontItem(): CustomFontItem {
        return CustomFontItem(
            id = nameWithoutExtension,
            displayName = nameWithoutExtension,
            fileName = name,
            fileExtension = extension,
            path = absolutePath,
            timestamp = 1L
        )
    }
}
