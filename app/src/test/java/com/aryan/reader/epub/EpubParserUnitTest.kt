package com.aryan.reader.epub

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubParserUnitTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `createEpubBook parses metadata spine ncx toc page list css images and extracted files`() = runTest {
        val cacheDir = temp.newFolder("cache")
        val extractionDir = temp.newFolder("extract")
        val parser = EpubParser(contextWithCache(cacheDir))

        val book = parser.createEpubBook(
            inputStream = ByteArrayInputStream(sampleEpubBytes()),
            bookId = "book-id",
            shouldUseToc = true,
            originalBookNameHint = "fallback.epub",
            parseContent = true,
            extractionDirOverride = extractionDir
        )

        assertEquals("Sample/Book".asFileName(), book.fileName)
        assertEquals("Sample/Book", book.title)
        assertEquals("Jane Writer", book.author)
        assertEquals("en", book.language)
        assertEquals("Series Name", book.seriesName)
        assertEquals(2.5, book.seriesIndex)
        assertEquals("Long description", book.description)
        assertEquals(extractionDir.absolutePath, book.extractionBasePath)
        assertTrue(File(extractionDir, "OEBPS/chapters/chapter 2.xhtml").isFile)

        assertEquals(2, book.chapters.size)
        assertEquals("NCX Chapter One", book.chapters[0].title)
        assertEquals("OEBPS/chapters/chapter1.xhtml", book.chapters[0].htmlFilePath)
        assertEquals(0, book.chapters[0].depth)
        assertTrue(book.chapters[0].isInToc)
        assertEquals("Nested Two", book.chapters[1].title)
        assertEquals("OEBPS/chapters/chapter 2.xhtml", book.chapters[1].htmlFilePath)
        assertEquals(1, book.chapters[1].depth)
        assertTrue(book.chapters[1].plainTextContent.contains("Chapter Two"))

        assertEquals(
            listOf(
                EpubTocEntry("NCX Chapter One", "OEBPS/chapters/chapter1.xhtml", "start", 0),
                EpubTocEntry("Nested Two", "OEBPS/chapters/chapter 2.xhtml", "top", 1)
            ),
            book.tableOfContents
        )
        assertEquals(1, book.pageList.size)
        assertEquals("7", book.pageList.single().value)
        assertEquals("OEBPS/chapters/chapter 2.xhtml#page7", book.pageList.single().contentSrc)
        assertEquals(
            mapOf(
                "OEBPS/styles/main.css" to "body { color: black; }",
                "OEBPS/styles/extra.css" to "p { margin: 0; }"
            ),
            book.css
        )
        assertEquals(
            setOf("OEBPS/images/picture.jpg", "OEBPS/images/unlisted.png"),
            book.images.map { it.absPath }.toSet()
        )
    }

    @Test
    fun `createEpubBook can parse metadata only without chapters css or images`() = runTest {
        val cacheDir = temp.newFolder("cache-metadata")
        val extractionDir = temp.newFolder("extract-metadata")
        val parser = EpubParser(contextWithCache(cacheDir))

        val book = parser.createEpubBook(
            inputStream = ByteArrayInputStream(sampleEpubBytes()),
            bookId = "book-id",
            shouldUseToc = true,
            originalBookNameHint = "fallback.epub",
            parseContent = false,
            extractionDirOverride = extractionDir
        )

        assertEquals("Sample/Book", book.title)
        assertEquals(emptyList<EpubChapter>(), book.chapters)
        assertEquals(emptyList<EpubImage>(), book.images)
        assertEquals(emptyMap<String, String>(), book.css)
        assertEquals(emptyList<EpubTocEntry>(), book.tableOfContents)
        assertTrue(extractionDir.list().isNullOrEmpty())
    }

    @Test
    fun `metadata only extraction streams images to disk without retaining image bytes`() {
        val cacheDir = temp.newFolder("cache-metadata-stream")
        val extractionDir = temp.newFolder("extract-metadata-stream")
        val parser = EpubParser(contextWithCache(cacheDir))
        val imageBytes = ByteArray(2 * 1024 * 1024) { 7 }
        val zipFileOnDisk = File(temp.root, "metadata-stream.epub")
        zipFileOnDisk.writeBytes(
            zipBinaryBytes(
                "META-INF/container.xml" to """
                    <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
                """.trimIndent().toByteArray(Charsets.UTF_8),
                "OEBPS/content.opf" to """
                    <package>
                        <metadata />
                        <manifest>
                            <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
                        </manifest>
                        <spine />
                    </package>
                """.trimIndent().toByteArray(Charsets.UTF_8),
                "OEBPS/images/cover.jpg" to imageBytes
            )
        )

        val files = parser.extractEpubContents(
            zipFile = ZipFile(zipFileOnDisk),
            extractionDir = extractionDir,
            parseContent = false,
            extractImagesForMetadata = true
        )

        assertTrue(files["META-INF/container.xml"]!!.data.isNotEmpty())
        assertEquals(0, files["OEBPS/images/cover.jpg"]!!.data.size)
        assertEquals(imageBytes.size.toLong(), File(extractionDir, "OEBPS/images/cover.jpg").length())
    }

    @Test
    fun `createEpubBook reuses active extraction cache on matching warm open`() = runTest {
        val cacheDir = temp.newFolder("cache-warm-open")
        val parser = EpubParser(contextWithCache(cacheDir))

        val first = parser.createEpubBook(
            inputStream = ByteArrayInputStream(sampleEpubBytes()),
            bookId = "warm-book",
            shouldUseToc = true,
            originalBookNameHint = "warm.epub"
        )
        val activeDir = ImportedFileCache.activeBookDir(contextWithCache(cacheDir), "warm-book")
        File(activeDir, "sentinel.txt").writeText("still here")

        val second = parser.createEpubBook(
            inputStream = ByteArrayInputStream(minimalEpubBytesWithoutOptionalMetadata()),
            bookId = "warm-book",
            shouldUseToc = true,
            originalBookNameHint = "warm.epub"
        )

        assertEquals(first.title, second.title)
        assertEquals(first.chapters.size, second.chapters.size)
        assertTrue(File(activeDir, "sentinel.txt").isFile)
    }

    @Test
    fun `createEpubBook invalidates active extraction cache when source fingerprint changes`() = runTest {
        val cacheDir = temp.newFolder("cache-source-change")
        val context = contextWithCache(cacheDir)
        val parser = EpubParser(context)

        val first = parser.createEpubBook(
            inputStream = ByteArrayInputStream(sampleEpubBytes()),
            bookId = "changed-book",
            shouldUseToc = true,
            originalBookNameHint = "changed.epub",
            sourceFingerprint = "100:1000"
        )
        val activeDir = ImportedFileCache.activeBookDir(context, "changed-book")
        File(activeDir, "sentinel.txt").writeText("old extraction")

        val second = parser.createEpubBook(
            inputStream = ByteArrayInputStream(sampleEpubBytes(author = "Edited Writer")),
            bookId = "changed-book",
            shouldUseToc = true,
            originalBookNameHint = "changed.epub",
            sourceFingerprint = "120:2000"
        )

        assertEquals("Jane Writer", first.author)
        assertEquals("Edited Writer", second.author)
        assertFalse(File(activeDir, "sentinel.txt").isFile)
    }

    @Test
    fun `metadata only parse does not clear active extracted content`() = runTest {
        val cacheDir = temp.newFolder("cache-metadata-preserve")
        val context = contextWithCache(cacheDir)
        val parser = EpubParser(context)
        val activeDir = ImportedFileCache.ensureActiveBookDir(context, "metadata-book")
        File(activeDir, "sentinel.txt").writeText("active")

        parser.createEpubBook(
            inputStream = ByteArrayInputStream(sampleEpubBytes()),
            bookId = "metadata-book",
            parseContent = false,
            originalBookNameHint = "metadata.epub"
        )

        assertTrue(File(activeDir, "sentinel.txt").isFile)
    }

    @Test
    fun `createEpubBook falls back to file hint author language and chapter titles when metadata and ncx are absent`() = runTest {
        val parser = EpubParser(contextWithCache(temp.newFolder("cache-fallback")))
        val extractionDir = temp.newFolder("extract-fallback")

        val book = parser.createEpubBook(
            inputStream = ByteArrayInputStream(minimalEpubBytesWithoutOptionalMetadata()),
            bookId = "book-id",
            shouldUseToc = false,
            originalBookNameHint = "Original Name.epub",
            parseContent = true,
            extractionDirOverride = extractionDir
        )

        assertEquals("Original Name", book.title)
        assertEquals("Unknown Author", book.author)
        assertEquals("en", book.language)
        assertEquals("HTML Heading", book.chapters.single().title)
        assertEquals(0, book.chapters.single().depth)
        assertTrue(book.chapters.single().isInToc)
        assertEquals(emptyList<EpubTocEntry>(), book.tableOfContents)
    }

    @Test
    fun `createEpubBook throws parser exception for missing container rootfile or opf`() = runTest {
        val parser = EpubParser(contextWithCache(temp.newFolder("cache-errors")))

        val missingContainer = runCatching {
            parser.createEpubBook(ByteArrayInputStream(zipBytes("OEBPS/content.opf" to "<package/>")), "id")
        }.exceptionOrNull()
        val missingOpf = runCatching {
            parser.createEpubBook(
                ByteArrayInputStream(
                    zipBytes(
                        "META-INF/container.xml" to """
                            <container><rootfiles><rootfile full-path="OEBPS/missing.opf"/></rootfiles></container>
                        """.trimIndent()
                    )
                ),
                "id"
            )
        }.exceptionOrNull()

        assertTrue(missingContainer is EpubParserException)
        assertTrue(missingContainer!!.message!!.contains("container.xml"))
        assertTrue(missingOpf is EpubParserException)
        assertTrue(missingOpf!!.message!!.contains(".opf file missing"))
    }

    @Test
    fun `EpubXMLFileParser extracts first heading and preserves optional fragment`() {
        val parser = EpubXMLFileParser(
            fileRelativePath = "chapters/one.xhtml",
            data = "<html><body><h2> Chapter Title </h2><h1>Ignored</h1></body></html>".toByteArray(),
            fragmentId = "anchor"
        )

        val output = parser.parseForTitleAndPath()

        assertEquals("Chapter Title", output.title)
        assertEquals("chapters/one.xhtml#anchor", output.effectiveHtmlPath)
    }

    @Test
    fun `xml helpers select tags attributes children and filename conversions`() {
        val document = parseXMLFile(
            """
                <root>
                    <item id="one"><child>A</child><child>B</child></item>
                    <item id="two" />
                </root>
            """.trimIndent().toByteArray()
        )!!

        val firstItem = document.selectFirstTag("item")!!

        assertEquals("one", firstItem.getAttributeValue("id"))
        assertEquals("A", firstItem.selectFirstChildTag("child")!!.textContent)
        assertEquals(listOf("A", "B"), firstItem.selectChildTag("child").map { it.textContent }.toList())
        assertEquals("OPS_chapter_one.xhtml", "OPS/chapter/one.xhtml".asFileName())
        assertNull(document.selectFirstTag("missing"))
    }

    @Test
    fun `EpubXMLFileParser returns null title and unfragmented path when heading and fragment are absent`() {
        val parser = EpubXMLFileParser(
            fileRelativePath = "chapters/plain.xhtml",
            data = "<html><body><p>No heading here.</p></body></html>".toByteArray()
        )

        val output = parser.parseForTitleAndPath()

        assertNull(output.title)
        assertEquals("chapters/plain.xhtml", output.effectiveHtmlPath)
    }

    @Test
    fun `createEpubBook normalizes leading slash opf path from container`() = runTest {
        val parser = EpubParser(contextWithCache(temp.newFolder("cache-leading-slash")))
        val extractionDir = temp.newFolder("extract-leading-slash")

        val book = parser.createEpubBook(
            inputStream = ByteArrayInputStream(
                zipBytes(
                    "META-INF/container.xml" to """
                        <container><rootfiles><rootfile full-path="/OEBPS/content.opf"/></rootfiles></container>
                    """.trimIndent(),
                    "OEBPS/content.opf" to """
                        <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                            <metadata><dc:title>Slash Book</dc:title></metadata>
                            <manifest><item id="chap1" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest>
                            <spine><itemref idref="chap1"/></spine>
                        </package>
                    """.trimIndent(),
                    "OEBPS/chapter.xhtml" to "<html><body><p>Text</p></body></html>"
                )
            ),
            bookId = "book-id",
            originalBookNameHint = "fallback.epub",
            extractionDirOverride = extractionDir
        )

        assertEquals("Slash Book", book.title)
        assertEquals("OEBPS/chapter.xhtml", book.chapters.single().htmlFilePath)
    }

    @Test
    fun `createEpubBook creates synthetic readable chapter for image spine items`() = runTest {
        val parser = EpubParser(contextWithCache(temp.newFolder("cache-image-spine")))
        val extractionDir = temp.newFolder("extract-image-spine")

        val book = parser.createEpubBook(
            inputStream = ByteArrayInputStream(imageSpineEpubBytes()),
            bookId = "book-id",
            shouldUseToc = false,
            originalBookNameHint = "image-book.epub",
            parseContent = true,
            extractionDirOverride = extractionDir
        )

        val chapter = book.chapters.single()
        assertEquals("Image", chapter.title)
        assertEquals("OEBPS/images/page1.jpg", chapter.htmlFilePath)
        assertEquals("[Image]", chapter.plainTextContent)
        assertTrue(chapter.htmlContent.contains("<img src=\"OEBPS/images/page1.jpg\""))
        assertEquals(listOf(EpubImage("OEBPS/images/page1.jpg")), book.images)
    }

    @Test
    fun `hasReadableExtractedContent validates blank dirs empty dirs and chapter files`() {
        assertFalse(epubBook(extractionBasePath = "").hasReadableExtractedContent())
        assertFalse(epubBook(extractionBasePath = File(temp.root, "missing").absolutePath).hasReadableExtractedContent())

        val emptyDir = temp.newFolder("empty-readable")
        assertFalse(epubBook(extractionBasePath = emptyDir.absolutePath).hasReadableExtractedContent())

        val nonChapterDir = temp.newFolder("non-chapter")
        File(nonChapterDir, "asset.css").writeText("body{}")
        assertTrue(epubBook(extractionBasePath = nonChapterDir.absolutePath).hasReadableExtractedContent())

        val chapterDir = temp.newFolder("chapters-readable")
        File(chapterDir, "one.xhtml").writeText("<p>One</p>")
        val readable = epubBook(
            extractionBasePath = chapterDir.absolutePath,
            chapters = listOf(chapter("one.xhtml"))
        )
        val missing = readable.copy(chapters = listOf(chapter("one.xhtml"), chapter("two.xhtml")))

        assertTrue(readable.hasReadableExtractedContent())
        assertFalse(missing.hasReadableExtractedContent())
    }

    private fun contextWithCache(cacheDir: File): Context {
        val context = mockk<Context>()
        every { context.cacheDir } returns cacheDir
        return context
    }

    private fun sampleEpubBytes(author: String = "Jane Writer"): ByteArray = zipBytes(
        "META-INF/container.xml" to """
            <container version="1.0">
                <rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles>
            </container>
        """.trimIndent(),
        "OEBPS/content.opf" to """
            <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                <metadata>
                    <dc:title>Sample/Book</dc:title>
                    <dc:creator>$author</dc:creator>
                    <dc:language>en</dc:language>
                    <dc:description>Long description</dc:description>
                    <meta name="calibre:series" content="Series Name"/>
                    <meta name="calibre:series_index" content="2.5"/>
                </metadata>
                <manifest>
                    <item id="chap1" href="chapters/chapter1.xhtml" media-type="application/xhtml+xml"/>
                    <item id="chap2" href="chapters/chapter%202.xhtml" media-type="application/xhtml+xml"/>
                    <item id="toc" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    <item id="style" href="styles/main.css" media-type="text/css"/>
                    <item id="pic" href="images/picture.jpg" media-type="image/jpeg"/>
                </manifest>
                <spine toc="toc">
                    <itemref idref="chap1"/>
                    <itemref idref="chap2"/>
                </spine>
            </package>
        """.trimIndent(),
        "OEBPS/toc.ncx" to """
            <ncx>
                <navMap>
                    <navPoint id="nav1">
                        <navLabel><text>NCX Chapter One</text></navLabel>
                        <content src="chapters/chapter1.xhtml#start"/>
                        <navPoint id="nav2">
                            <navLabel><text>Nested Two</text></navLabel>
                            <content src="chapters/chapter%202.xhtml#top"/>
                        </navPoint>
                    </navPoint>
                </navMap>
                <pageList>
                    <pageTarget id="p7" value="7">
                        <navLabel><text>7</text></navLabel>
                        <content src="chapters/chapter%202.xhtml#page7"/>
                    </pageTarget>
                </pageList>
            </ncx>
        """.trimIndent(),
        "OEBPS/chapters/chapter1.xhtml" to "<html><body><h1>Ignored HTML Title</h1><p>One</p></body></html>",
        "OEBPS/chapters/chapter 2.xhtml" to "<html><body><h2>Chapter Two</h2><p>Two text</p></body></html>",
        "OEBPS/styles/main.css" to "body { color: black; }",
        "OEBPS/styles/extra.css" to "p { margin: 0; }",
        "OEBPS/images/picture.jpg" to "not-real-image",
        "OEBPS/images/unlisted.png" to "not-real-image"
    )

    private fun minimalEpubBytesWithoutOptionalMetadata(): ByteArray = zipBytes(
        "META-INF/container.xml" to """
            <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
        """.trimIndent(),
        "OEBPS/content.opf" to """
            <package>
                <metadata />
                <manifest>
                    <item id="chap1" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                </manifest>
                <spine><itemref idref="chap1"/></spine>
            </package>
        """.trimIndent(),
        "OEBPS/chapter.xhtml" to "<html><body><h1>HTML Heading</h1><p>Text</p></body></html>"
    )

    private fun imageSpineEpubBytes(): ByteArray = zipBytes(
        "META-INF/container.xml" to """
            <container><rootfiles><rootfile full-path="OEBPS/content.opf"/></rootfiles></container>
        """.trimIndent(),
        "OEBPS/content.opf" to """
            <package xmlns:dc="http://purl.org/dc/elements/1.1/">
                <metadata><dc:title>Image Book</dc:title></metadata>
                <manifest>
                    <item id="page1" href="images/page1.jpg" media-type="image/jpeg"/>
                </manifest>
                <spine><itemref idref="page1"/></spine>
            </package>
        """.trimIndent(),
        "OEBPS/images/page1.jpg" to "not-real-image"
    )

    private fun zipBytes(vararg entries: Pair<String, String>): ByteArray {
        return zipBinaryBytes(*entries.map { it.first to it.second.toByteArray(Charsets.UTF_8) }.toTypedArray())
    }

    private fun zipBinaryBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun epubBook(
        extractionBasePath: String,
        chapters: List<EpubChapter> = emptyList()
    ): EpubBook =
        EpubBook(
            fileName = "book.epub",
            title = "Book",
            author = "Author",
            language = "en",
            coverImage = null,
            chapters = chapters,
            extractionBasePath = extractionBasePath
        )

    private fun chapter(path: String): EpubChapter =
        EpubChapter(
            chapterId = path,
            absPath = path,
            title = path,
            htmlFilePath = path,
            plainTextContent = "",
            htmlContent = ""
        )
}
