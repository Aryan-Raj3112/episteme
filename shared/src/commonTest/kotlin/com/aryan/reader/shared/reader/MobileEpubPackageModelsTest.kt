package com.aryan.reader.shared.reader

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    fun `merged chapter sections fold leading crumbs and tiny parents like Android`() {
        data class Entry(val fragment: String, val label: String, val depth: Int)
        // Jungle h-0 shape: title page crumbs, 1875, CONTENTS, ILLUS, half-title, chapters.
        val entries = listOf(
            Entry("t", "Title", 0),
            Entry("a", "Line A", 1),
            Entry("b", "Line B", 1),
            Entry("c", "Line C", 1),
            Entry("s", "Substantial", 1),
            Entry("half", "Half title", 0),
            Entry("ch", "Chapter", 1)
        )
        val childIndex = mapOf("t" to 8, "a" to 14, "b" to 16, "c" to 18, "s" to 19, "half" to 45, "ch" to 47)
        // Plain-text length of each raw range, keyed by range start child.
        val lengthByStart = mapOf(8 to 76, 14 to 131, 16 to 25, 18 to 57, 19 to 847, 45 to 19, 47 to 3970)

        val merged = mobileEpubMergedChapterSections(
            entries = entries,
            bodyChildCount = 103,
            fragmentId = Entry::fragment,
            idChildIndex = childIndex::get,
            nameChildIndex = { null },
            depthOf = Entry::depth,
            sectionTextLength = { start, _ -> lengthByStart.getValue(start) },
            sectionHasMedia = { _, _ -> false }
        )

        assertEquals(3, merged.size)
        // Title crumbs folded into one section starting at the title entry.
        assertEquals("t", merged[0].entry.fragment)
        assertEquals(8, merged[0].startChildIndex)
        assertEquals(19, merged[0].endChildIndexExclusive)
        assertEquals(listOf("a", "b", "c"), merged[0].absorbedEntries.map { it.fragment })
        // Substantial section untouched.
        assertEquals("s", merged[1].entry.fragment)
        assertEquals(19, merged[1].startChildIndex)
        assertEquals(45, merged[1].endChildIndexExclusive)
        assertEquals(emptyList(), merged[1].absorbedEntries)
        // Tiny parent half-title folded forward into its chapter.
        assertEquals("half", merged[2].entry.fragment)
        assertEquals(45, merged[2].startChildIndex)
        assertEquals(103, merged[2].endChildIndexExclusive)
        assertEquals(listOf("ch"), merged[2].absorbedEntries.map { it.fragment })
        assertEquals(listOf(0, 1, 2), merged.map { it.materializationIndex })
    }

    @Test
    fun `merged chapter sections keep single tiny leaders and all-tiny files intact`() {
        data class Entry(val fragment: String, val depth: Int)
        // Single tiny leader stays split (existing Android contract).
        val single = mobileEpubMergedChapterSections(
            entries = listOf(Entry("x", 0), Entry("y", 0)),
            bodyChildCount = 3,
            fragmentId = Entry::fragment,
            idChildIndex = { mapOf("x" to 1, "y" to 2)[it] },
            nameChildIndex = { null },
            depthOf = Entry::depth,
            sectionTextLength = { s, _ -> if (s == 1) 13 else 5000 },
            sectionHasMedia = { _, _ -> false }
        )
        assertEquals(listOf("x", "y"), single.map { it.entry.fragment })

        // All-tiny file is never folded into one chapter.
        val poems = mobileEpubMergedChapterSections(
            entries = listOf(Entry("p1", 1), Entry("p2", 1), Entry("p3", 1)),
            bodyChildCount = 4,
            fragmentId = Entry::fragment,
            idChildIndex = { mapOf("p1" to 0, "p2" to 1, "p3" to 2)[it] },
            nameChildIndex = { null },
            depthOf = Entry::depth,
            sectionTextLength = { _, _ -> 60 },
            sectionHasMedia = { _, _ -> false }
        )
        assertEquals(listOf("p1", "p2", "p3"), poems.map { it.entry.fragment })

        // Tiny section with media (illustration plate) is kept.
        val plate = mobileEpubMergedChapterSections(
            entries = listOf(Entry("cover", 0), Entry("plate", 0), Entry("ch", 0)),
            bodyChildCount = 6,
            fragmentId = Entry::fragment,
            idChildIndex = { mapOf("cover" to 0, "plate" to 2, "ch" to 4)[it] },
            nameChildIndex = { null },
            depthOf = Entry::depth,
            sectionTextLength = { s, _ -> if (s == 4) 3000 else 40 },
            sectionHasMedia = { s, _ -> s == 2 }
        )
        assertEquals(listOf("cover", "plate", "ch"), plate.map { it.entry.fragment })
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
            metaElements = listOf(
                MobileEpubMetaElement(name = "calibre:series", content = "Old"),
                MobileEpubMetaElement(name = "calibre:series_index", content = "invalid"),
                MobileEpubMetaElement(name = "calibre:series", content = "Final"),
                MobileEpubMetaElement(name = "calibre:series_index", content = "2.5")
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
    fun seriesResolutionPrefersEpub3CollectionFormOverLegacyCalibreMetas() {
        val series = resolveMobileEpubSeries(
            listOf(
                MobileEpubMetaElement(name = "calibre:series", content = "Legacy Series"),
                MobileEpubMetaElement(name = "calibre:series_index", content = "9"),
                MobileEpubMetaElement(
                    id = "c1",
                    property = "belongs-to-collection",
                    text = "Sherlock Holmes"
                ),
                MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c1"),
                MobileEpubMetaElement(property = "group-position", text = "3.5", refines = "#c1")
            )
        )

        assertEquals("Sherlock Holmes", series?.name)
        assertEquals(3.5, series?.index)
    }

    @Test
    fun seriesResolutionIgnoresNonSeriesCollectionsAndFallsBackToLegacy() {
        val series = resolveMobileEpubSeries(
            listOf(
                MobileEpubMetaElement(id = "set1", property = "belongs-to-collection", text = "Boxed Sets"),
                MobileEpubMetaElement(property = "collection-type", text = "set", refines = "set1"),
                MobileEpubMetaElement(name = "calibre:series", content = "Legacy Series"),
                MobileEpubMetaElement(name = "calibre:series_index", content = "7")
            )
        )

        assertEquals("Legacy Series", series?.name)
        assertEquals(7.0, series?.index)
    }

    @Test
    fun seriesResolutionHandlesMissingAndInvalidGroupPositions() {
        val missing = resolveMobileEpubSeries(
            listOf(
                MobileEpubMetaElement(id = "c1", property = "belongs-to-collection", text = "Sherlock Holmes"),
                MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c1")
            )
        )
        assertEquals("Sherlock Holmes", missing?.name)
        assertNull(missing?.index)

        val invalid = resolveMobileEpubSeries(
            listOf(
                MobileEpubMetaElement(id = "c2", property = "belongs-to-collection", text = "Sherlock Holmes"),
                MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c2"),
                MobileEpubMetaElement(property = "group-position", text = "not-a-number", refines = "#c2")
            )
        )
        assertEquals("Sherlock Holmes", invalid?.name)
        assertNull(invalid?.index)
    }

    @Test
    fun seriesResolutionRequiresIdRefinesAndSeriesType() {
        assertNull(
            resolveMobileEpubSeries(
                listOf(MobileEpubMetaElement(property = "belongs-to-collection", text = "No id"))
            )
        )
        assertNull(
            resolveMobileEpubSeries(
                listOf(
                    MobileEpubMetaElement(id = "c", property = "belongs-to-collection", text = "Untyped"),
                    MobileEpubMetaElement(property = "collection-type", text = "periodical", refines = "#c")
                )
            )
        )
        assertNull(
            resolveMobileEpubSeries(
                listOf(
                    MobileEpubMetaElement(id = "c", property = "belongs-to-collection", text = "   "),
                    MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c")
                )
            )
        )
    }

    @Test
    fun seriesResolutionKeepsFirstSeriesCollectionAndNormalizesWhitespace() {
        val series = resolveMobileEpubSeries(
            listOf(
                MobileEpubMetaElement(
                    id = "c1",
                    property = "belongs-to-collection",
                    text = "  Sherlock\n  Holmes  "
                ),
                MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c1"),
                MobileEpubMetaElement(property = "group-position", text = "1", refines = "#c1"),
                MobileEpubMetaElement(id = "c2", property = "belongs-to-collection", text = "Second"),
                MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c2"),
                MobileEpubMetaElement(property = "group-position", text = "2", refines = "#c2")
            )
        )

        assertEquals("Sherlock Holmes", series?.name)
        assertEquals(1.0, series?.index)
    }

    @Test
    fun metadataResolutionReadsEpub3CollectionSeries() {
        val metadata = resolveMobileEpubMetadata(
            sourceFileName = "adventures.epub",
            title = null,
            author = "Arthur Conan Doyle",
            language = "en",
            description = null,
            metaElements = listOf(
                MobileEpubMetaElement(property = "dcterms:modified", text = "2026-07-12T00:00:00Z"),
                MobileEpubMetaElement(id = "c1", property = "belongs-to-collection", text = "Sherlock Holmes"),
                MobileEpubMetaElement(property = "collection-type", text = "series", refines = "#c1"),
                MobileEpubMetaElement(property = "group-position", text = "3", refines = "#c1")
            )
        )

        assertEquals("adventures", metadata.title)
        assertEquals("Sherlock Holmes", metadata.seriesName)
        assertEquals(3.0, metadata.seriesIndex)
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
