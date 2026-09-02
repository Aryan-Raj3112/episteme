package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticParagraph
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SharedEpubPackageLoaderTest {
    @Test
    fun `secure package and present malformed ncx failures match Android`() {
        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(
                MapEpubArchive(
                    mapOf(
                        "META-INF/container.xml" to """
                            <!DOCTYPE container [<!ENTITY unsafe "value">]>
                            <container><rootfiles><rootfile full-path="book.opf"/></rootfiles></container>
                        """.trimIndent().encodeToByteArray(),
                        "book.opf" to "<package><metadata/><manifest/><spine/></package>".encodeToByteArray()
                    )
                ),
                "doctype",
                "doctype.epub"
            )
        }

        val malformedNcx = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata/><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "chapter.xhtml" to "<!DOCTYPE html><html><body><p>HTML remains valid</p></body></html>".encodeToByteArray(),
                "toc.ncx" to "<!DOCTYPE ncx><ncx><navMap/></ncx>".encodeToByteArray()
            )
        )
        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(malformedNcx, "ncx-doctype", "ncx.epub")
        }

        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(
                MapEpubArchive(
                    mapOf(
                        "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                        "book.opf" to "<package><metadata></manifest><manifest/><spine/></package>".encodeToByteArray()
                    )
                ),
                "mismatched",
                "mismatched.epub"
            )
        }

        listOf(
            "<package><metadata/><manifest invalid><spine/></package>",
            "<package><metadata/><manifest/><spine toc=\"one\" toc=\"two\"/></package>",
            "<package><metadata><dc:title>Invalid&nbsp;XML</dc:title></metadata><manifest/><spine/></package>",
            "<package><metadata/><manifest/><spine toc=\"invalid&nbsp;XML\"/></package>"
        ).forEachIndexed { index, malformedOpf ->
            assertFailsWith<IllegalStateException> {
                SharedEpubPackageLoader.load(
                    MapEpubArchive(
                        mapOf(
                            "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                            "book.opf" to malformedOpf.encodeToByteArray()
                        )
                    ),
                    "malformed-attribute-$index",
                    "malformed-attribute.epub"
                )
            }
        }

        val unicodeExtension = SharedEpubPackageLoader.load(
            MapEpubArchive(
                mapOf(
                    "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                    "book.opf" to "<package><metadata><扩展 属性=\"值\"/></metadata><manifest/><spine/></package>".encodeToByteArray()
                )
            ),
            "unicode-extension",
            "unicode-extension.epub"
        )
        assertEquals(emptyList(), unicodeExtension.chapters)
    }

    @Test
    fun `logical sections preserve Android id priority and last same-child start`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata/><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "chapter.xhtml" to """
                    <html><body><div name="x">Name target must be excluded</div>
                    <div><span id="a"></span><span id="x"></span>Chosen X body</div>
                    <div id="y">Y body</div></body></html>
                """.trimIndent().encodeToByteArray(),
                "toc.ncx" to """
                    <ncx><navMap>
                      <navPoint><navLabel><text>A</text></navLabel><content src="chapter.xhtml#a"/></navPoint>
                      <navPoint><navLabel><text>X</text></navLabel><content src="chapter.xhtml#x"/></navPoint>
                      <navPoint><navLabel><text>Y</text></navLabel><content src="chapter.xhtml#y"/></navPoint>
                    </navMap></ncx>
                """.trimIndent().encodeToByteArray()
            )
        )

        val chapters = SharedEpubPackageLoader.load(archive, "sections", "sections.epub").chapters

        assertEquals(listOf("x", "y"), chapters.map(SharedEpubChapter::fragmentId))
        assertEquals(listOf("X", "Y"), chapters.map(SharedEpubChapter::title))
        assertFalse(chapters.first().plainText.contains("Name target"))
        assertTrue(chapters.first().plainText.contains("Chosen X body"))
    }

    @Test
    fun `chapters carry block level cfi semantic blocks`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata/><manifest>
                      <item id="whole" href="whole.xhtml" media-type="application/xhtml+xml"/>
                      <item id="split" href="split.xhtml" media-type="application/xhtml+xml"/>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="whole"/><itemref idref="split"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "whole.xhtml" to """
                    <html><body><h1>Heading</h1><p>First paragraph</p><ul><li>Item one</li></ul></body></html>
                """.trimIndent().encodeToByteArray(),
                "split.xhtml" to """
                    <html><body><div id="a"><p>Alpha section</p></div><div id="b"><p>Beta section</p></div></body></html>
                """.trimIndent().encodeToByteArray(),
                "toc.ncx" to """
                    <ncx><navMap>
                      <navPoint><navLabel><text>A</text></navLabel><content src="split.xhtml#a"/></navPoint>
                      <navPoint><navLabel><text>B</text></navLabel><content src="split.xhtml#b"/></navPoint>
                    </navMap></ncx>
                """.trimIndent().encodeToByteArray()
            )
        )

        val chapters = SharedEpubPackageLoader.load(archive, "blocks", "blocks.epub").chapters

        val whole = chapters.single { it.id == "whole" }
        val header = whole.semanticBlocks[0] as SemanticHeader
        assertEquals("Heading", header.text)
        assertEquals("/4/2/2", header.cfi)
        assertEquals(0, header.startCharOffsetInSource)
        val paragraph = whole.semanticBlocks[1] as SemanticParagraph
        assertEquals("First paragraph", paragraph.text)
        assertEquals("/4/2/4", paragraph.cfi)
        val list = whole.semanticBlocks[2] as SemanticList
        assertEquals("Item one", list.items[0].text)
        assertEquals("/4/2/6", list.cfi)

        val alpha = chapters.single { it.id == "split#a" }
        val alphaParagraph = alpha.semanticBlocks.single() as SemanticParagraph
        assertEquals("Alpha section", alphaParagraph.text)
        assertEquals(alpha.plainText, "Alpha section")
        assertEquals("/4/2/2", alphaParagraph.cfi)
    }

    @Test
    fun `chapter heading order blank retention and title fallback match Android`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata/><manifest>
                      <item id="ordered" href="ordered.xhtml" media-type="application/xhtml+xml"/>
                      <item id="blank" href="blank.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="ordered"/><itemref idref="blank"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "ordered.xhtml" to "<html><head><title>Head title text</title></head><body><h2>Earlier H2</h2><h1>Later H1</h1></body></html>".encodeToByteArray(),
                "blank.xhtml" to "<html><head><title>Not a chapter title</title></head><body></body></html>".encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "chapters", "chapters.epub")

        assertEquals(listOf("Earlier H2", "Chapter 2"), book.chapters.map(SharedEpubChapter::title))
        assertTrue(book.chapters.first().plainText.startsWith("Head title text"))
        assertEquals("Not a chapter title", book.chapters.last().plainText)
    }

    @Test
    fun `unsafe manifest references remain unreadable without aborting like Android`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata/><manifest>
                      <item id="up" href="../../outside.xhtml" media-type="application/xhtml+xml"/>
                      <item id="absolute" href="/absolute.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="up"/><itemref idref="absolute"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "outside.xhtml" to "<html><body>Must not be repaired</body></html>".encodeToByteArray(),
                "absolute.xhtml" to "<html><body>Must not strip slash</body></html>".encodeToByteArray()
            )
        )

        assertEquals(emptyList(), SharedEpubPackageLoader.load(archive, "unsafe", "unsafe.epub").chapters)
    }

    @Test
    fun `ncx tags and resource association are exact case like Android`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata/><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/chapter.xhtml" to "<html><body><h1>Document title</h1><p>Text</p></body></html>".encodeToByteArray(),
                "OPS/toc.ncx" to """
                    <ncx><navMap><navPoint><navLabel><text>Wrong resource case</text></navLabel>
                    <content src="CHAPTER.xhtml"/></navPoint></navMap><PageList/></ncx>
                """.trimIndent().encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "ncx-case", "case.epub")

        assertEquals("Document title", book.chapters.single().title)
        assertFalse(book.chapters.single().isInToc)
        assertEquals("OPS/CHAPTER.xhtml", book.tableOfContents.single().href)
        assertEquals(emptyList(), book.pageList)
    }

    @Test
    fun `xml attributes are exact case like Android`() {
        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(
                MapEpubArchive(
                    mapOf(
                        "META-INF/container.xml" to "<container><rootfiles><rootfile FULL-PATH=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                        "book.opf" to "<package><metadata/><manifest/><spine/></package>".encodeToByteArray()
                    )
                ),
                "attribute-case",
                "case.epub"
            )
        }

        val uppercaseIdRef = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata/><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref IDREF="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "chapter.xhtml" to "<html><body><p>Must not load</p></body></html>".encodeToByteArray()
            )
        )
        assertEquals(
            emptyList(),
            SharedEpubPackageLoader.load(uppercaseIdRef, "idref-case", "case.epub").chapters
        )
    }

    @Test
    fun `required package sections use Android exact tags and failures`() {
        fun archive(opf: String) = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to opf.encodeToByteArray()
            )
        )

        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(
                archive("<package><Metadata/><manifest/><spine/></package>"),
                "wrong-case",
                "wrong.epub"
            )
        }
        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(
                archive("<package><metadata/><spine/></package>"),
                "missing-manifest",
                "missing.epub"
            )
        }
        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(
                archive("<package><metadata/><manifest/></package>"),
                "missing-spine",
                "missing.epub"
            )
        }
    }

    @Test
    fun `metadata adapter uses Android direct exact dc children`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata>
                      <title>Unprefixed ignored</title>
                      <wrapper><dc:title>Nested ignored</dc:title></wrapper>
                      <dc:title>Direct Android title</dc:title>
                      <dc:creator>Direct author</dc:creator>
                      <opf:meta name="calibre:series" content="Fallback ignored"/>
                      <meta name="calibre:series" content="Primary series"/>
                    </metadata><manifest/><spine/></package>
                """.trimIndent().encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "metadata", "fallback-name.epub")

        assertEquals("Direct Android title", book.title)
        assertEquals("Direct author", book.author)
        assertEquals("Primary series", book.seriesName)
    }

    @Test
    fun `metadata adapter resolves epub3 collection series like Calibre`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package version="3.0"><metadata>
                      <dc:title>EPUB3 Title</dc:title>
                      <dc:creator>Arthur Conan Doyle</dc:creator>
                      <meta property="dcterms:modified">2026-07-12T00:00:00Z</meta>
                      <meta id="c1" property="belongs-to-collection">Sherlock Holmes</meta>
                      <meta refines="#c1" property="collection-type">series</meta>
                      <meta refines="#c1" property="group-position">2.0</meta>
                      <meta name="calibre:series" content="Legacy ignored"/>
                    </metadata><manifest/><spine/></package>
                """.trimIndent().encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "epub3-series", "epub3.epub")

        assertEquals("EPUB3 Title", book.title)
        assertEquals("Sherlock Holmes", book.seriesName)
        assertEquals(2.0, book.seriesIndex)
    }

    @Test
    fun `manifest attributes retain Android whitespace and case semantics`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>Raw attributes</title></metadata><manifest>
                      <item id="spaced" href=" chapter.xhtml " media-type="application/xhtml+xml"/>
                      <item id="case-sensitive" href="chapter.bin" media-type="APPLICATION/XHTML+XML"/>
                    </manifest><spine><itemref idref="spaced"/><itemref idref="case-sensitive"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/ chapter.xhtml " to "<html><body><h1>Spaced href</h1></body></html>".encodeToByteArray(),
                "OPS/chapter.bin" to "<html><body><h1>Wrong media case</h1></body></html>".encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "raw", "raw.epub")

        assertEquals(listOf("Spaced href"), book.chapters.map(SharedEpubChapter::title))
    }

    @Test
    fun `archive resource lookup is exact case like Android`() {
        val wrongContainerCase = MapEpubArchive(
            mapOf("meta-inf/container.xml" to "<container/>".encodeToByteArray())
        )
        assertFailsWith<IllegalStateException> {
            SharedEpubPackageLoader.load(wrongContainerCase, "wrong-container", "wrong.epub")
        }

        val wrongChapterCase = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path=\"OPS/book.opf\"/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>Exact</title></metadata><manifest>
                      <item id="chapter" href="Text/Chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/text/chapter.xhtml" to "<html><body><p>Wrong case</p></body></html>".encodeToByteArray()
            )
        )
        assertEquals(
            emptyList(),
            SharedEpubPackageLoader.load(wrongChapterCase, "wrong-chapter", "wrong.epub").chapters
        )
    }

    @Test
    fun `toc disabled skips all NCX projections like Android`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>
                """.trimIndent().encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>No TOC</title></metadata><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/chapter.xhtml" to "<html><head><title>Fallback</title></head><body><h1>Heading</h1><p>Text</p></body></html>".encodeToByteArray(),
                "OPS/toc.ncx" to """
                    <ncx><navMap><navPoint><navLabel><text>NCX title</text></navLabel><content src="chapter.xhtml"/></navPoint></navMap>
                    <pageList><pageTarget id="p1" value="1"><navLabel><text>One</text></navLabel><content src="chapter.xhtml#p1"/></pageTarget></pageList></ncx>
                """.trimIndent().encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "no-toc", "no-toc.epub", shouldUseToc = false)

        assertEquals("Heading", book.chapters.single().title)
        assertTrue(book.chapters.single().isInToc)
        assertEquals(emptyList(), book.tableOfContents)
        assertEquals(emptyList(), book.pageList)
    }

    @Test
    fun `container manifest and ncx paths use Android form style URL decoding`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='My+Book/package.opf'/></rootfiles></container>".encodeToByteArray(),
                "My Book/package.opf" to """
                    <package><metadata><title>Decoded</title></metadata><manifest>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                      <item id="chapter" href="caf%C3%A9+one.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "My Book/toc.ncx" to "<ncx><navMap><navPoint><navLabel><text>Decoded title</text></navLabel><content src='caf%C3%A9+one.xhtml'/></navPoint></navMap></ncx>".encodeToByteArray(),
                "My Book/café one.xhtml" to "<html><body><p>Decoded chapter.</p></body></html>".encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "decoded", "decoded.epub")

        assertEquals("Decoded title", book.chapters.single().title)
        assertEquals("My Book/café one.xhtml", book.chapters.single().baseHref)
        assertEquals("My Book/café one.xhtml", book.tableOfContents.single().href)
    }

    @Test
    fun `empty spine does not scan manifest and returns empty chapters like Android`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>".encodeToByteArray(),
                "book.opf" to """
                    <package><metadata><title>Empty Spine</title></metadata><manifest>
                      <item id="orphan" href="orphan.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine/></package>
                """.trimIndent().encodeToByteArray(),
                "orphan.xhtml" to "<html><body><h1>Must not load</h1></body></html>".encodeToByteArray()
            )
        )

        assertEquals(emptyList(), SharedEpubPackageLoader.load(archive, "empty", "empty.epub").chapters)
        assertEquals(emptyList(), loadSharedEpubTtsChapters(archive, "empty.epub"))
    }

    @Test
    fun `epub3 nav without ncx keeps Android heading titles and empty toc`() {
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
        assertEquals("Chapter One", book.chapters[0].title)
        assertTrue(book.chapters[0].htmlContent.contains("<div class=\"empty\"></div>"))
        assertTrue(book.chapters[0].htmlContent.contains("<p class=\"lead\">Styled text.</p>"))
        assertTrue(book.chapters[0].htmlContent.contains(sharedEpubResourceUrl("book-id", "OPS/images/cover.png")))
        assertFalse(book.chapters[0].htmlContent.contains("<script", ignoreCase = true))
        assertTrue(book.css.values.single().contains(sharedEpubResourceUrl("book-id", "OPS/images/cover.png")))
        assertEquals(emptyList(), book.tableOfContents)
        assertEquals(listOf(MobileEpubImage("OPS/images/cover.png")), book.images)
        assertEquals("OPS/images/cover.png", book.coverImagePath)
    }

    @Test
    fun `loads epub2 ncx hierarchy and preserves non linear spine items like Android`() {
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
                    </navPoint></navMap><pageList>
                      <pageTarget id="missing" value="0"><navLabel><text>Missing</text></navLabel></pageTarget>
                      <pageTarget id="p1" value="1"><navLabel><text>One</text></navLabel><content src="one.xhtml#page-1"/></pageTarget>
                    </pageList></ncx>
                """.trimIndent().encodeToByteArray(),
                "one.xhtml" to "<html><body><h1>One</h1><p>Visible</p></body></html>".encodeToByteArray(),
                "hidden.xhtml" to "<html><body><h1>Hidden</h1></body></html>".encodeToByteArray()
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "two", "two.epub")

        assertEquals(2, book.chapters.size)
        assertEquals("Hidden", book.chapters.last().title)
        assertEquals(listOf(true, false), book.chapters.map { it.isInToc })
        assertEquals(listOf(0, 0), book.chapters.map { it.depth })
        assertEquals(listOf(0, 1), book.tableOfContents.map { it.depth })
        assertEquals("nested", book.tableOfContents.last().fragmentId)
        assertEquals(listOf(MobileEpubPageTarget("p1", "1", "One", "one.xhtml#page-1")), book.pageList)
    }

    @Test
    fun `materializes fragment toc entries in one spine document as logical chapters`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>Sections</title></metadata><manifest>
                      <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine toc="ncx"><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/toc.ncx" to """
                    <ncx><navMap>
                      <navPoint><navLabel><text>Start</text></navLabel><content src="chapter.xhtml#start"/></navPoint>
                      <navPoint><navLabel><text>Next</text></navLabel><content src="chapter.xhtml#next"/></navPoint>
                    </navMap></ncx>
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
        assertTrue(book.css.values.any { it.contains(sharedEpubResourceUrl("visual", "OPS/images/page.jpg")) })
        assertEquals("[Image]", book.chapters[1].plainText)
        assertTrue(book.chapters[1].htmlContent.contains(sharedEpubResourceUrl("visual", "OPS/images/page.jpg")))
    }

    @Test
    fun `embeds unquoted epub html resources like the android parser`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to "<container><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>".encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>Unquoted resources</title></metadata><manifest>
                      <item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/>
                      <item id="image" href="images/picture.png" media-type="image/png"/>
                      <item id="style" href="book.css" media-type="text/css"/>
                    </manifest><spine><itemref idref="chapter"/></spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/chapter.xhtml" to """
                    <html><head><link rel=stylesheet href=book.css></head>
                    <body><img src=images/picture.png alt=Cover><a href=images/picture.png>Open image</a></body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/book.css" to ".cover { background-image: url(images/picture.png); }".encodeToByteArray(),
                "OPS/images/picture.png" to byteArrayOf(1, 2, 3)
            )
        )

        val book = SharedEpubPackageLoader.load(archive, "unquoted", "unquoted.epub")

        assertTrue(book.chapters.single().htmlContent.contains("src=\"${sharedEpubResourceUrl("unquoted", "OPS/images/picture.png")}"))
        assertTrue(book.chapters.single().htmlContent.contains("href=\"${sharedEpubResourceUrl("unquoted", "OPS/images/picture.png")}"))
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

    @Test
    fun `loadSharedEpubTtsChapters splits toc sections strips scripts and skips non html items`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container><rootfiles><rootfile full-path="OPS/package.opf"/></rootfiles></container>
                """.trimIndent().encodeToByteArray(),
                "OPS/package.opf" to """
                    <package unique-identifier="pub-id">
                      <metadata><identifier id="pub-id">u1</identifier><title>TTS Book</title></metadata>
                      <manifest>
                        <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
                        <item id="c1" href="text/c1.xhtml" media-type="application/xhtml+xml"/>
                        <item id="c2" href="text/c2.xhtml" media-type="application/xhtml+xml"/>
                        <item id="cover" href="images/cover.jpg" media-type="image/jpeg"/>
                      </manifest>
                      <spine toc="ncx"><itemref idref="cover"/><itemref idref="c1"/><itemref idref="c2"/></spine>
                    </package>
                """.trimIndent().encodeToByteArray(),
                "OPS/toc.ncx" to """
                    <ncx><navMap>
                      <navPoint><navLabel><text>Intro</text></navLabel><content src="text/c1.xhtml#intro"/></navPoint>
                      <navPoint><navLabel><text>Part Two</text></navLabel><content src="text/c1.xhtml#part2"/></navPoint>
                    </navMap></ncx>
                """.trimIndent().encodeToByteArray(),
                "OPS/text/c1.xhtml" to """
                    <html><head><title>Doc title</title></head>
                    <body>
                      <div id="intro"><p>Hello &amp; welcome.</p><script>bad()</script></div>
                      <div id="part2"><p>Second&nbsp;part here.</p></div>
                    </body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/text/c2.xhtml" to """
                    <html><head><title>Doc title</title></head><body><p>Last chapter.</p></body></html>
                """.trimIndent().encodeToByteArray(),
                "OPS/images/cover.jpg" to byteArrayOf(1, 2, 3)
            )
        )

        val chapters = loadSharedEpubTtsChapters(archive, "tts.epub")

        assertEquals(3, chapters.size)
        assertEquals("Intro", chapters[0].title)
        assertEquals(true, chapters[0].plainText.contains("Hello & welcome."))
        assertEquals(false, chapters[0].plainText.contains("bad()"))
        assertEquals("Part Two", chapters[1].title)
        assertEquals(true, chapters[1].plainText.contains("Second part here."))
        assertEquals("Chapter 3", chapters[2].title)
        assertEquals("Doc title Last chapter.", chapters[2].plainText)
    }

    @Test
    fun `loadSharedEpubTtsChapters preserves linear no like Android and falls back to heading titles`() {
        val archive = MapEpubArchive(
            mapOf(
                "META-INF/container.xml" to """
                    <container><rootfiles><rootfile full-path="OPS/book.opf"/></rootfiles></container>
                """.trimIndent().encodeToByteArray(),
                "OPS/book.opf" to """
                    <package><metadata><title>TTS Two</title></metadata><manifest>
                      <item id="skip" href="skip.xhtml" media-type="application/xhtml+xml"/>
                      <item id="main" href="main.xhtml" media-type="application/xhtml+xml"/>
                    </manifest><spine>
                      <itemref idref="skip" linear="no"/>
                      <itemref idref="main"/>
                    </spine></package>
                """.trimIndent().encodeToByteArray(),
                "OPS/skip.xhtml" to "<html><body><p>Should not be spoken</p></body></html>".encodeToByteArray(),
                "OPS/main.xhtml" to "<html><body><h2>Real Chapter</h2><p>Spoken text.</p></body></html>".encodeToByteArray()
            )
        )

        val chapters = loadSharedEpubTtsChapters(archive, "tts.epub")

        assertEquals(2, chapters.size)
        assertEquals("Chapter 1", chapters[0].title)
        assertEquals("Should not be spoken", chapters[0].plainText)
        assertEquals("Real Chapter", chapters[1].title)
        assertEquals("Real Chapter\nSpoken text.", chapters[1].plainText)
    }

    @Test
    fun `scheme resource urls round trip book ids and entry paths`() {
        val bookId = "book id/with spaces&symbols?=#"
        val entryPath = "OEBPS/images/cover (1).png"
        val url = sharedEpubResourceUrl(bookId, entryPath)
        assertTrue(isSharedEpubResourceUrl(url))
        val parsed = parseSharedEpubResourceUrl(url)
        assertEquals(bookId, parsed?.bookId)
        assertEquals(entryPath, parsed?.entryPath)
        val urlWithFragment = sharedEpubResourceUrl(bookId, entryPath) + "#anchor"
        assertEquals(entryPath, parseSharedEpubResourceUrl(urlWithFragment)?.entryPath)
        assertFalse(isSharedEpubResourceUrl("data:image/png;base64,AAA"))
        assertFalse(isSharedEpubResourceUrl("https://example.com/a.png"))
        assertEquals(null, parseSharedEpubResourceUrl("$SharedEpubResourceScheme://r/only-book-id"))
        assertTrue(isSharedEpubSchemeServableResource("x/photo.JPEG"))
        assertFalse(isSharedEpubSchemeServableResource("x/font.woff2"))
        assertFalse(isSharedEpubSchemeServableResource("x/page.xhtml"))
    }
}
