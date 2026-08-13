package com.aryan.reader.shared.reader

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MobileEpubPackageModelsTest {
    @Test
    fun `chapter scroll and character progress preserve Android boundaries`() {
        assertEquals(0f, mobileEpubChapterScrollFraction(40, 0, 100))
        assertEquals(1f, mobileEpubChapterScrollFraction(0, 80, 100))
        assertEquals(0.5f, mobileEpubChapterScrollFraction(50, 200, 100))
        assertEquals(1f, mobileEpubChapterScrollFraction(150, 200, 100))
        assertEquals(0f, mobileEpubCharacterProgress(0, 20, 10, false))
        assertEquals(37.5f, mobileEpubCharacterProgress(200, 50, 25, false))
        assertEquals(100f, mobileEpubCharacterProgress(200, 50, 25, true))
        assertEquals(33.3, mobileEpubCharacterDisplayProgress(300, 50, 50, false))
        assertEquals(100.0, mobileEpubCharacterDisplayProgress(100, 120, 0, false))
        assertEquals(100.0, mobileEpubCharacterDisplayProgress(0, 0, 0, true))
    }

    @Test
    fun `extraction byte limits preserve Android cache and metadata bounds`() {
        assertEquals(4 * 1024 * 1024, MOBILE_EPUB_MAX_METADATA_ENTRY_BYTES)
        assertEquals(4L * 1024L * 1024L, MOBILE_EPUB_MAX_CACHED_BOOK_METADATA_BYTES)
        assertEquals(2L * 1024L * 1024L, MOBILE_EPUB_MAX_LOCATOR_ON_DEMAND_HTML_BYTES)
        assertEquals(2 * 1024 * 1024, MOBILE_EPUB_MAX_LOCATOR_ON_DEMAND_HTML_CHARS)
    }

    @Test
    fun `logical section ranges preserve Android id priority ordering and collision policy`() {
        data class Entry(val fragment: String, val label: String)
        val first = Entry("shared", "First collision")
        val last = Entry("id-wins", "Last collision")
        val ranges = mobileEpubLogicalSectionRanges(
            entries = listOf(first, Entry("name-only", "Middle"), last, Entry("shared", "Duplicate fragment")),
            bodyChildCount = 5,
            fragmentId = Entry::fragment,
            idChildIndex = { fragment -> mapOf("id-wins" to 3, "shared" to 1)[fragment] },
            nameChildIndex = { fragment -> mapOf("id-wins" to 0, "name-only" to 3)[fragment] }
        )

        assertEquals(
            listOf(
                MobileEpubLogicalSectionRange(first, 1, 3, materializationIndex = 0),
                MobileEpubLogicalSectionRange(last, 3, 5, materializationIndex = 2)
            ),
            ranges
        )
        assertEquals(
            emptyList(),
            mobileEpubLogicalSectionRanges(
                entries = listOf(first, last),
                bodyChildCount = 5,
                fragmentId = Entry::fragment,
                idChildIndex = { 2 },
                nameChildIndex = { null }
            )
        )
    }

    @Test
    fun `extraction lifecycle preserves Android directory cache and cleanup policy`() {
        assertEquals(
            MobileEpubExtractionLifecycle(MobileEpubExtractionDirectoryMode.OVERRIDE, false, false, false),
            mobileEpubExtractionLifecycle(parseContent = true, hasDirectoryOverride = true)
        )
        assertEquals(
            MobileEpubExtractionLifecycle(MobileEpubExtractionDirectoryMode.OVERRIDE, false, false, false),
            mobileEpubExtractionLifecycle(parseContent = false, hasDirectoryOverride = true)
        )
        assertEquals(
            MobileEpubExtractionLifecycle(MobileEpubExtractionDirectoryMode.TEMPORARY_METADATA, false, false, true),
            mobileEpubExtractionLifecycle(parseContent = false, hasDirectoryOverride = false)
        )
        assertEquals(
            MobileEpubExtractionLifecycle(MobileEpubExtractionDirectoryMode.ACTIVE_CACHE, true, true, false),
            mobileEpubExtractionLifecycle(parseContent = true, hasDirectoryOverride = false)
        )
    }

    @Test
    fun `content path and extracted cache readability preserve Android rules`() {
        assertEquals("OPS/chapter.xhtml", mobileEpubContentFilePath("OPS/chapter.xhtml#part?ignored"))
        assertFalse(isMobileEpubExtractionCacheReadable(false, true, 1, true, true))
        assertFalse(isMobileEpubExtractionCacheReadable(true, false, 1, true, true))
        assertTrue(isMobileEpubExtractionCacheReadable(true, true, 0, true, false))
        assertFalse(isMobileEpubExtractionCacheReadable(true, true, 0, false, true))
        assertTrue(isMobileEpubExtractionCacheReadable(true, true, 2, false, true))
        assertFalse(isMobileEpubExtractionCacheReadable(true, true, 2, true, false))
    }

    @Test
    fun `extraction cache chapter strips payload and preserves effective length`() {
        val chapter = MobileEpubChapter(
            chapterId = "chapter",
            absPath = "chapter.xhtml",
            title = "Chapter",
            htmlFilePath = "chapter.xhtml",
            plainTextContent = "longer text",
            htmlContent = "<p>longer text</p>",
            plainTextLength = 3
        )

        val cached = chapter.toMobileEpubExtractionCacheChapter()

        assertEquals("", cached.plainTextContent)
        assertEquals("", cached.htmlContent)
        assertEquals(11, cached.plainTextLength)
        assertEquals(chapter.chapterId, cached.chapterId)
        assertEquals(chapter.htmlFilePath, cached.htmlFilePath)
    }

    @Test
    fun `extraction cache compatibility preserves Android key contract`() {
        val manifest = MobileEpubExtractionCacheManifest(
            bookId = "book",
            originalBookNameHint = "book.epub",
            parserVersion = MOBILE_EPUB_EXTRACTION_CACHE_VERSION,
            parseContent = true,
            shouldUseToc = true,
            sourceFingerprint = "fingerprint"
        )
        assertTrue(manifest.matchesMobileEpubExtractionCache("book", "book.epub", true, "fingerprint"))
        assertFalse(manifest.copy(parserVersion = 2).matchesMobileEpubExtractionCache("book", "book.epub", true, "fingerprint"))
        assertFalse(manifest.copy(parseContent = false).matchesMobileEpubExtractionCache("book", "book.epub", true, "fingerprint"))
        assertFalse(manifest.matchesMobileEpubExtractionCache("book", "book.epub", false, "fingerprint"))
        assertFalse(manifest.matchesMobileEpubExtractionCache("book", "book.epub", true, null))
    }

    @Test
    fun `extraction action preserves Android full and metadata-only policy`() {
        assertEquals(MobileEpubExtractionAction.EXTRACT_AND_READ, mobileEpubExtractionAction("OPS/BOOK.OPF", true, false))
        assertEquals(MobileEpubExtractionAction.EXTRACT_WITHOUT_MEMORY, mobileEpubExtractionAction("OPS/chapter.xhtml", true, false))
        assertEquals(MobileEpubExtractionAction.READ_IN_MEMORY, mobileEpubExtractionAction("META-INF/container.xml", false, false))
        assertEquals(MobileEpubExtractionAction.EXTRACT_WITHOUT_MEMORY, mobileEpubExtractionAction("Images/COVER.JPG", false, true))
        assertEquals(MobileEpubExtractionAction.SKIP, mobileEpubExtractionAction("Images/COVER.JPG", false, false))
        assertEquals(MobileEpubExtractionAction.SKIP, mobileEpubExtractionAction("OPS/toc.ncx", false, true))
    }

    @Test
    fun `spine chapter title preserves Android heading fallback policy`() {
        assertEquals("Heading", resolveMobileEpubSpineChapterTitle("Heading", 2))
        assertEquals("Chapter 3", resolveMobileEpubSpineChapterTitle("", 2))
        assertEquals("Chapter 1", resolveMobileEpubSpineChapterTitle(null, 0))
    }

    @Test
    fun `package reference resolution preserves Android unsafe lexical results`() {
        assertEquals("OPS/Text/chapter.xhtml", resolveMobileEpubReference("OPS/book.opf", "./Text/part/../chapter.xhtml"))
        assertEquals("../outside.xhtml", resolveMobileEpubReference("book.opf", "../outside.xhtml"))
        assertEquals("/outside.xhtml", resolveMobileEpubReference("OPS/book.opf", "/outside.xhtml"))
        assertEquals("OPS/Text\\chapter.xhtml", resolveMobileEpubReference("OPS/book.opf", "Text\\chapter.xhtml"))
    }

    @Test
    fun `url decoding matches Android form style UTF8 and malformed fallback`() {
        assertEquals("OPS/My Book/café.xhtml", decodeMobileEpubUrl("OPS/My+Book/caf%C3%A9.xhtml"))
        assertEquals("broken%2", decodeMobileEpubUrl("broken%2"))
        assertEquals("broken%XZ+name", decodeMobileEpubUrl("broken%XZ+name"))
    }

    @Test
    fun `image inventory and cover candidates preserve Android ordering`() {
        val manifest = listOf(
            MobileEpubManifestItem("hero", "Art/Hero.PNG", "image/png", ""),
            MobileEpubManifestItem("cover-id", "Custom/cover.webp", "image/webp", ""),
            MobileEpubManifestItem("fallback", "OPS/Images/COVER.JPG", "image/jpeg", "")
        )
        assertEquals(
            listOf("Art/Hero.PNG", "Custom/cover.webp", "OPS/Images/COVER.JPG", "loose.svg"),
            mobileEpubImages(manifest, listOf("loose.svg", "Art/Hero.PNG", "ignored.bmp")).map { it.absPath }
        )
        assertEquals(
            listOf("Custom/cover.webp", "OPS/Images/COVER.JPG", "OPS/Images/COVER.JPG"),
            mobileEpubCoverCandidates("cover-id", manifest, emptySet())
        )
    }

    @Test
    fun `cover bitmap sampling preserves Android power of two bounds`() {
        assertEquals(1, mobileEpubCoverBitmapSampleSize(0, 2048))
        assertEquals(1, mobileEpubCoverBitmapSampleSize(1024, 512))
        assertEquals(2, mobileEpubCoverBitmapSampleSize(1025, 600))
        assertEquals(4, mobileEpubCoverBitmapSampleSize(4096, 1024))
        assertEquals(4, mobileEpubCoverBitmapSampleSize(4097, 1))
        assertEquals(8, mobileEpubCoverBitmapSampleSize(4100, 1))
    }

    @Test
    fun `css paths preserve Android manifest first and unlisted archive order`() {
        val manifest = listOf(
            MobileEpubManifestItem("a", "styles/a.css", "text/css", ""),
            MobileEpubManifestItem("b", "styles/b.css", "application/octet-stream", "")
        )
        assertEquals(
            listOf("styles/a.css", "loose.CSS", "styles/b.css"),
            mobileEpubCssPaths(manifest, listOf("loose.CSS", "styles/a.css", "styles/b.css", "image.png"))
        )
    }

    @Test
    fun `spine resource classification preserves Android MIME and extension rules`() {
        assertEquals(MobileEpubSpineResourceKind.HTML, mobileEpubSpineResourceKind("application/xhtml+xml; charset=utf-8", "OPS/chapter.bin"))
        assertEquals(MobileEpubSpineResourceKind.HTML, mobileEpubSpineResourceKind("application/octet-stream", "OPS/chapter.XML"))
        assertEquals(MobileEpubSpineResourceKind.IMAGE, mobileEpubSpineResourceKind("image/custom", "OPS/plate.bin"))
        assertEquals(MobileEpubSpineResourceKind.UNSUPPORTED, mobileEpubSpineResourceKind("application/octet-stream", "OPS/chapter.htm"))
        assertEquals(MobileEpubSpineResourceKind.UNSUPPORTED, mobileEpubSpineResourceKind("application/octet-stream", "OPS/cover.jpg"))
    }

    @Test
    fun `chapter navigation preserves Android title depth and toc membership`() {
        val metadata = mapOf(
            "OPS/one.xhtml" to MobileEpubNcxChapterMetadata("NCX One", 2)
        )

        assertEquals(
            MobileEpubChapterNavigation("NCX One", depth = 2, isInToc = true),
            resolveMobileEpubChapterNavigation("OPS/one.xhtml#fragment", "HTML One", metadata)
        )
        assertEquals(
            MobileEpubChapterNavigation("HTML Two", depth = 0, isInToc = false),
            resolveMobileEpubChapterNavigation("OPS/two.xhtml", "HTML Two", metadata)
        )
        assertEquals(
            MobileEpubChapterNavigation("HTML Two", depth = 0, isInToc = true),
            resolveMobileEpubChapterNavigation("OPS/two.xhtml", "HTML Two", emptyMap())
        )
    }

    @Test
    fun `page targets preserve order and omit entries without content like Android`() {
        assertEquals(
            listOf(MobileEpubPageTarget("p1", "1", "One", "OPS/chapter.xhtml#p1")),
            mobileEpubPageTargets(
                listOf(
                    MobileEpubNcxPageNode("missing", null, null, null),
                    MobileEpubNcxPageNode("p1", "1", "One", "OPS/chapter.xhtml#p1")
                )
            )
        )
    }

    @Test
    fun chapterDefaultsAndPersistedJsonShapeMatchAndroidContract() {
        val chapter = MobileEpubChapter(
            chapterId = "chapter-1",
            absPath = "OPS/chapter.xhtml",
            title = "Chapter",
            htmlFilePath = "OPS/chapter.xhtml",
            plainTextContent = "Readable text",
            htmlContent = "<p>Readable text</p>"
        )

        assertEquals(0, chapter.depth)
        assertEquals(true, chapter.isInToc)
        assertEquals(13, chapter.plainTextLength)
        assertEquals(13, chapter.plainTextCharacterCount())
        assertEquals(
            "{\"chapterId\":\"chapter-1\",\"absPath\":\"OPS/chapter.xhtml\",\"title\":\"Chapter\",\"htmlFilePath\":\"OPS/chapter.xhtml\",\"plainTextContent\":\"Readable text\",\"htmlContent\":\"<p>Readable text</p>\"}",
            Json.encodeToString(chapter)
        )
    }

    @Test
    fun characterCountPreservesAndroidCachedLengthBehavior() {
        val chapter = MobileEpubChapter(
            chapterId = "chapter-1",
            absPath = "chapter.xhtml",
            title = "Chapter",
            htmlFilePath = "chapter.xhtml",
            plainTextContent = "short",
            htmlContent = "",
            plainTextLength = 20
        )

        assertEquals(20, chapter.plainTextCharacterCount())
    }

    @Test
    fun ncxFlatteningPreservesAndroidDepthAndTargetlessParentBehavior() {
        val entries = flattenMobileEpubNcxNavigation(
            listOf(
                MobileEpubNcxNavigationNode(
                    label = "Part",
                    absolutePath = "OPS/part.xhtml",
                    fragmentId = null,
                    children = listOf(
                        MobileEpubNcxNavigationNode("Chapter", "OPS/chapter.xhtml", "start")
                    )
                ),
                MobileEpubNcxNavigationNode(
                    label = "Missing target",
                    absolutePath = null,
                    fragmentId = null,
                    children = listOf(
                        MobileEpubNcxNavigationNode("Skipped child", "OPS/skipped.xhtml", null)
                    )
                )
            )
        )

        assertEquals(
            listOf(
                MobileEpubTocEntry("Part", "OPS/part.xhtml", null, 0),
                MobileEpubTocEntry("Chapter", "OPS/chapter.xhtml", "start", 1)
            ),
            entries
        )
    }

    @Test
    fun chapterMetadataTraversesTargetlessParentsAndKeepsFirstResourceEntry() {
        val roots = listOf(
            MobileEpubNcxNavigationNode(
                label = null,
                absolutePath = null,
                fragmentId = null,
                children = listOf(
                    MobileEpubNcxNavigationNode("Outer", "OPS/chapter.xhtml", "start"),
                    MobileEpubNcxNavigationNode("Nested", "OPS/chapter.xhtml", "nested")
                )
            ),
            MobileEpubNcxNavigationNode("", "OPS/ignored.xhtml", null)
        )

        assertEquals(
            mapOf("OPS/chapter.xhtml" to MobileEpubNcxChapterMetadata("Outer", 1)),
            mobileEpubNcxChapterMetadata(roots)
        )
    }

    @Test
    fun metadataResolutionPreservesAndroidDefaultsAndLastCalibreValue() {
        val metadata = resolveMobileEpubMetadata(
            sourceFileName = "/books/fallback.epub",
            title = "Series/Book",
            author = null,
            language = null,
            description = "Description",
            metaEntries = listOf(
                "calibre:series" to "Old",
                "calibre:series_index" to "invalid",
                "calibre:series" to "Final",
                "calibre:series_index" to "2.5"
            )
        )

        assertEquals("Series_Book", metadata.fileName)
        assertEquals("Series/Book", metadata.title)
        assertEquals("Unknown Author", metadata.author)
        assertEquals("en", metadata.language)
        assertEquals("Final", metadata.seriesName)
        assertEquals(2.5, metadata.seriesIndex)
        assertEquals("Description", metadata.description)
    }

    @Test
    fun spineAndNcxSelectionPreserveAndroidOrderingAndPrecedence() {
        assertEquals(
            listOf("linear", "nonlinear", "missing"),
            mobileEpubSpineItemIds(listOf("linear", "nonlinear", null, "missing"))
        )
        val manifest = listOf(
            MobileEpubManifestItem("fallback", "OPS/fallback.ncx", "application/octet-stream", ""),
            MobileEpubManifestItem("typed", "OPS/navigation.xml", "application/x-dtbncx+xml", ""),
            MobileEpubManifestItem("declared", "OPS/declared.xml", "application/xml", "")
        )

        assertEquals("declared", resolveMobileEpubNcxManifestId("declared", manifest))
        assertEquals("typed", resolveMobileEpubNcxManifestId("unknown", manifest))
        assertEquals("typed", resolveMobileEpubNcxManifestId(null, manifest))
        assertEquals(
            "fallback",
            resolveMobileEpubNcxManifestId(null, manifest.filterNot { it.id == "typed" })
        )
        assertEquals("OPS/first.opf", resolveMobileEpubOpfPath(listOf("/OPS/first.opf", "second.opf")))
        assertEquals("", resolveMobileEpubOpfPath(listOf("///")))
        assertEquals(null, resolveMobileEpubOpfPath(emptyList()))
    }
}
