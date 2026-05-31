package com.aryan.reader.epub

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class EpubImportSecurityTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `safeFileInRoot rejects traversal outside extraction root`() {
        val root = temp.newFolder("root")

        assertNotNull(safeFileInRoot(root, "OEBPS/image.png"))
        assertTrue(safeFileInRoot(root, "../outside.txt") == null)
    }

    @Test
    fun `xml parser rejects doctypes from untrusted book metadata`() {
        val xml = """
            <!DOCTYPE root [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
            <root>&xxe;</root>
        """.trimIndent()

        assertThrows(Exception::class.java) {
            parseXMLFile(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        }
    }

    @Test
    fun `odt parser skips zip entries that escape extraction root`() = runTest {
        val extractionDir = temp.newFolder("odt-root")
        val outside = File(extractionDir.parentFile, "odt-evil.txt")
        val parser = OdtParser(contextWithCache(temp.newFolder("odt-cache")))

        parser.createOdtBook(
            inputStream = ByteArrayInputStream(
                zipBytes(
                    "content.xml" to minimalOdtContent().toByteArray(Charsets.UTF_8),
                    "../odt-evil.txt" to "evil".toByteArray(Charsets.UTF_8)
                )
            ),
            bookId = "odt-book",
            originalBookNameHint = "book.odt",
            isFlat = false,
            parseContent = false,
            extractionDirOverride = extractionDir
        )

        assertFalse(outside.exists())
    }

    @Test
    fun `fb2 parser sanitizes binary image ids before writing files`() = runTest {
        val extractionDir = temp.newFolder("fb2-root")
        val outside = File(extractionDir.parentFile, "fb2-evil.png")
        val parser = Fb2Parser(contextWithCache(temp.newFolder("fb2-cache")))

        parser.createFb2Book(
            inputStream = ByteArrayInputStream(minimalFb2WithUnsafeImage().toByteArray(Charsets.UTF_8)),
            bookId = "fb2-book",
            originalBookNameHint = "book.fb2",
            parseContent = true,
            extractionDirOverride = extractionDir
        )

        assertFalse(outside.exists())
        assertTrue(extractionDir.listFiles().orEmpty().any { it.name.startsWith("fb2-evil_") && it.extension == "png" })
    }

    private fun contextWithCache(cacheDir: File): Context {
        val context = mockk<Context>(relaxed = true)
        every { context.cacheDir } returns cacheDir
        return context
    }

    private fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun minimalOdtContent(): String {
        return """
            <office:document-content
                xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0"
                xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0">
                <office:body><office:text><text:p>Hello</text:p></office:text></office:body>
            </office:document-content>
        """.trimIndent()
    }

    private fun minimalFb2WithUnsafeImage(): String {
        val payload = Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        return """
            <FictionBook xmlns:l="http://www.w3.org/1999/xlink">
                <description><title-info><book-title>Unsafe image</book-title></title-info></description>
                <body>
                    <section>
                        <p>Hello</p>
                        <image l:href="#../fb2-evil.png"/>
                    </section>
                </body>
                <binary id="../fb2-evil.png" content-type="image/png">$payload</binary>
            </FictionBook>
        """.trimIndent()
    }
}
