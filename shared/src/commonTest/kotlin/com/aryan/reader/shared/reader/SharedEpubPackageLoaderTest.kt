package com.aryan.reader.shared.reader

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedEpubPackageLoaderTest {
    @Test
    fun `loads epub3 spine toc css images and metadata without flattening xhtml`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <?xml version="1.0"?>
                    <container><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>
                """.trimIndent().encodeToByteArray(),
                "OPS/package.opf" to """
                    <package unique-identifier="pub-id">
                      <metadata>
                        <dc:identifier id="pub-id">urn:uuid:12345678-1234-1234-1234-123456789abc</dc:identifier>
                        <dc:title>Complete &amp; Styled</dc:title>
                        <dc:creator>Reader Author</dc:creator>
                      </metadata>
                      <manifest>
                        <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                        <item id="chapter-one" href="text/chapter1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="chapter-two" href="text/chapter2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="style" href="styles/book.css" media-type="text/css"/>
                        <item id="image" href="images/cover.png" media-type="image/png"/>
                      </manifest>
                      <spine><itemref idref="chapter-one"/><itemref idref="chapter-two"/></spine>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/nav.xhtml" to """
                    <html xmlns:epub="http://www.idpf.org/2007/ops"><body>
                      <nav epub:type="toc"><ol>
                        <li><a href="text/chapter1.xhtml#opening">Opening</a></li>
                        <li><a href="text/chapter2.xhtml">Second <span>Styled</span> Section</a></li>
                      </ol></nav>
                    </body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/text/chapter1.xhtml" to """
                    <html><head><title>Ignored title</title><link rel="stylesheet" href="../styles/book.css"/></head>
                    <body><h1 id="opening">Chapter One</h1><div class="empty"/><p class="lead">Styled text.</p>
                    <img src="../images/cover.png"/><script>window.bad = true</script></body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/text/chapter2.xhtml" to """
                    <html><body><h2>Chapter Two</h2><p>More text.</p></body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/styles/book.css" to ".lead { color: #123456; background-image: url('../images/cover.png'); }".encodeToByteArray(),
                "OPS/images/cover.png" to byteArrayOf(1, 2, 3, 4)
            )
        )

        val book = SharedEpubPackageLoader.load(archive, sourceId = "book-id", fileName = "book.epub")

        assertEquals("Complete & Styled", book.title)
        assertEquals("Reader Author", book.author)
        assertEquals(2, book.chapters.size)
        assertEquals("Opening", book.chapters[0].title)
        assertTrue(book.chapters[0].htmlContent.contains("<div class=\"empty\"></div>"))
        assertTrue(book.chapters[0].htmlContent.contains("<p class=\"lead\">Styled text.</p>"))
        assertTrue(book.chapters[0].htmlContent.contains("data:image/png;base64,"))
        assertFalse(book.chapters[0].htmlContent.contains("<script", ignoreCase = true))
        assertTrue(book.css.values.single().contains("data:image/png;base64,"))
        assertEquals(listOf("Opening", "Second Styled Section"), book.tableOfContents.map { it.label })
        assertEquals("OPS/text/chapter1.xhtml", book.tableOfContents.first().href)
        assertEquals("opening", book.tableOfContents.first().fragmentId)
    }

    @Test
    fun `loads epub2 ncx hierarchy and ignores non linear spine items`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='content.opf'/></rootfiles></container>".encodeToByteArray(),
                "content.opf" to """
                    <package><metadata><title>EPUB Two</title></metadata><manifest>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                      <item id="one" href="one.xhtml" media-type="application/xhtml+xml"/>
                      <item id="hidden" href="hidden.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="one"/><itemref idref="hidden" linear="no"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "toc.ncx" to """
                    <ncx><navMap><navPoint><navLabel><text>Part One</text></navLabel><content src="one.xhtml"/>
                      <navPoint><navLabel><text>Nested</text></navLabel><content src="one.xhtml#nested"/></navPoint>
                    </navPoint></navMap></ncx>
                """.trimIndent().encodeToByteArray(),
                "one.xhtml" to "<html><body><h1>One</h1><p>Visible</p></body></html>".encodeToByteArray(),
                "hidden.xhtml" to "<html><body><h1>Hidden</h1></body></html>".encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "two", "two.epub")

        assertEquals(1, book.chapters.size)
        assertEquals(listOf(0, 1), book.tableOfContents.map { it.depth })
        assertEquals("nested", book.tableOfContents.last().fragmentId)
    }

    @Test
    fun `materializes fragment toc entries in one spine document as logical chapters`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>Sections</title></metadata><manifest>
                      <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/nav.xhtml" to """
                    <html><body><nav><ol>
                      <li><a href="chapter.xhtml#start">Start</a></li>
                      <li><a href="chapter.xhtml#next">Next</a></li>
                    </ol></nav></body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/chapter.xhtml" to """
                    <html><body><section id="start"><h1>Start</h1><p>First section.</p></section>
                    <section id="next"><h1>Next</h1><p>Second section.</p></section></body></html>
                """.trimIndent().encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "sections", "sections.epub")

        assertEquals(listOf("Start", "Next"), book.chapters.map { it.title })
        assertEquals(listOf("start", "next"), book.chapters.map { it.fragmentId })
        assertTrue(book.chapters[0].plainText.contains("First section."))
        assertFalse(book.chapters[0].plainText.contains("Second section."))
    }

    @Test
    fun `dublin core source metadata does not unwind the package stack`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/content.opf'/></rootfiles></container>".encodeToByteArray(),
                "OPS/content.opf" to """
                    <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                      <metadata>
                        <dc:title>Source Metadata</dc:title>
                        <dc:source>https://example.org/original-book</dc:source>
                        <meta name="cover" content="cover-image"/>
                      </metadata>
                      <manifest><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
                      <spine><itemref idref="chapter"/></spine>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/chapter.xhtml" to "<html><body><p>Readable chapter</p></body></html>".encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "source", "source.epub")

        assertEquals("Source Metadata", book.title)
        assertEquals(1, book.chapters.size)
        assertTrue(book.chapters.single().plainText.contains("Readable chapter"))
    }

    @Test
    fun `renders image spine items and preserves embedded chapter styles`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>Visual Book</title></metadata><manifest>
                      <item id="styled" href="text/styled.xhtml" media-type="application/xhtml+xml"/>
                      <item id="image-page" href="images/page.jpg" media-type="image/jpeg"/>
                    </manifest><spine><itemref idref="styled"/><itemref idref="image-page"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/text/styled.xhtml" to """
                    <html><head><style>.hero { background: url('../images/page.jpg'); color: rebeccapurple; }</style></head>
                    <body><p class="hero">Styled inline</p></body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/images/page.jpg" to byteArrayOf(9, 8, 7)
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "visual", "visual.epub")

        assertEquals(2, book.chapters.size)
        assertTrue(book.css.values.any { it.contains("color: rebeccapurple") })
        assertTrue(book.css.values.any { it.contains("data:image/jpeg;base64,") })
        assertEquals("[Image]", book.chapters[1].plainText)
        assertTrue(book.chapters[1].htmlContent.contains("data:image/jpeg;base64,"))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `deobfuscates idpf fonts from container root paths and uses manifest mime type`() {
        val clearFont = ByteArray(1_100) { index -> (index * 31).toByte() }
        val key = "c12d11495401cf12256a830ecde8a78b17879cc3".chunked(2).map { it.toInt(16).toByte() }
        val encryptedFont = clearFont.copyOf().also { bytes ->
            repeat(1_040) { index -> bytes[index] = (bytes[index].toInt() xor key[index % key.size].toInt()).toByte() }
        }
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>".encodeToByteArray(),
                "META-INF/encryption.xml" to """
                    <encryption><EncryptedData><EncryptionMethod Algorithm="http://www.idpf.org/2008/embedding"/>
                    <CipherData><CipherReference URI="OPS/fonts/readerfont"/></CipherData></EncryptedData></encryption>
                """.trimIndent().encodeToByteArray(),
                "OPS/book.opf" to """
                    <package unique-identifier="pub-id"><metadata>
                      <identifier id="pub-id">urn:uuid:12345678-1234-1234-1234-123456789abc</identifier><title>Font Book</title>
                    </metadata><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      <item id="style" href="book.css" media-type="text/css"/>
                      <item id="font" href="fonts/readerfont" media-type="font/ttf"/>
                    </manifest><spine><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/chapter.xhtml" to "<html><body><p>Uses a font</p></body></html>".encodeToByteArray(),
                "OPS/book.css" to "@font-face { font-family: Reader; src: url('fonts/readerfont'); }".encodeToByteArray(),
                "OPS/fonts/readerfont" to encryptedFont
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "font", "font.epub")
        val encoded = Regex("data:font/ttf;base64,([A-Za-z0-9+/=]+)")
            .find(book.css.values.joinToString("\n"))
            ?.groupValues
            ?.get(1)
            ?: error("Embedded font data URI was not generated")

        assertTrue(Base64.Default.decode(encoded).contentEquals(clearFont))
    }

    private class MapEpubArchive(private val values: Map<String, ByteArray>) : SharedEpubArchive {
        override val entryPaths: Set<String> = values.keys
        override fun readBytes(path: String): ByteArray? = values[path]
    }
}
