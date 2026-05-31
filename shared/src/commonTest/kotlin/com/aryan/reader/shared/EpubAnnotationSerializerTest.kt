package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EpubAnnotationSerializerTest {

    @Test
    fun `highlights json round trips and tolerates legacy missing ids`() {
        val highlights = listOf(
            UserHighlight(
                id = "highlight-1",
                cfi = "epubcfi(/6/2!/4/2)",
                text = "A marked sentence",
                color = HighlightColor.BLUE,
                chapterIndex = 2,
                note = "Important",
                locator = ReaderLocator(
                    chapterIndex = 2,
                    chapterId = "chapter-2",
                    pageIndex = 5,
                    startOffset = 120,
                    endOffset = 137,
                    textQuote = "A marked sentence",
                    cfi = "epubcfi(/6/2!/4/2)"
                )
            )
        )

        val decoded = EpubAnnotationSerializer.parseHighlightsJson(
            EpubAnnotationSerializer.highlightsToJson(highlights)
        )
        val legacyDecoded = EpubAnnotationSerializer.parseHighlightsJson(
            """[{"cfi":"legacy","text":"Legacy mark","colorId":"missing","chapterIndex":1,"note":""}]"""
        )

        assertEquals(highlights, decoded)
        assertEquals(HighlightColor.YELLOW, legacyDecoded.single().color)
        assertEquals(null, legacyDecoded.single().note)
        assertEquals(1, legacyDecoded.single().locator.chapterIndex)
        assertEquals("legacy", legacyDecoded.single().locator.cfi)
        assertTrue(legacyDecoded.single().id.startsWith("highlight_"))
    }

    @Test
    fun `bookmarks json supports stored string entries and object arrays`() {
        val bookmark = EpubBookmark(
            cfi = "epubcfi(/6/4!/4/8)",
            chapterTitle = "Two",
            label = "Saved place",
            snippet = "A useful bookmark",
            pageInChapter = 3,
            totalPagesInChapter = 9,
            chapterIndex = 1,
            locator = ReaderLocator(
                chapterIndex = 1,
                pageIndex = 2,
                startOffset = 80,
                endOffset = 110,
                textQuote = "A useful bookmark",
                cfi = "epubcfi(/6/4!/4/8)"
            )
        )

        val decoded = EpubAnnotationSerializer.parseBookmarksJson(
            EpubAnnotationSerializer.bookmarksToJson(listOf(bookmark)),
            chapterTitles = listOf("One", "Two")
        )
        val objectDecoded = EpubAnnotationSerializer.parseBookmarksJson(
            """[{"cfi":"cfi","chapterTitle":"Two","snippet":"By title"}]""",
            chapterTitles = listOf("One", "Two")
        )

        assertEquals(setOf(bookmark), decoded)
        assertEquals(1, objectDecoded.single().chapterIndex)
    }

    @Test
    fun `processAndAddHighlight updates exact matches and appends new highlights`() {
        val highlights = mutableListOf<UserHighlight>()
        val cfi = EpubAnnotationSerializer.processAndAddHighlight(
            newCfi = "same-cfi",
            newText = "First",
            newColor = HighlightColor.YELLOW,
            chapterIndex = 0,
            currentList = highlights
        )
        val initialId = highlights.single().id

        EpubAnnotationSerializer.processAndAddHighlight(
            newCfi = "same-cfi",
            newText = "Updated",
            newColor = HighlightColor.GREEN,
            chapterIndex = 0,
            currentList = highlights
        )
        EpubAnnotationSerializer.processAndAddHighlight(
            newCfi = "other-cfi",
            newText = "Other",
            newColor = HighlightColor.BLUE,
            chapterIndex = 0,
            currentList = highlights
        )

        assertEquals("same-cfi", cfi)
        assertEquals(2, highlights.size)
        assertEquals(initialId, highlights.first().id)
        assertEquals("Updated", highlights.first().text)
        assertEquals(HighlightColor.GREEN, highlights.first().color)
        assertNotEquals(initialId, highlights.last().id)
    }

    @Test
    fun `processAndAddHighlight matches shared locator ranges when cfi changes`() {
        val highlights = mutableListOf<UserHighlight>()
        val locator = ReaderLocator(
            chapterIndex = 0,
            pageIndex = 3,
            startOffset = 42,
            endOffset = 58,
            textQuote = "Stable quote",
            cfi = "desktop:0:42:58"
        )

        EpubAnnotationSerializer.processAndAddHighlight(
            newCfi = "desktop:0:42:58",
            newText = "Stable quote",
            newColor = HighlightColor.YELLOW,
            chapterIndex = 0,
            currentList = highlights,
            locator = locator
        )
        val initialId = highlights.single().id

        EpubAnnotationSerializer.processAndAddHighlight(
            newCfi = "changed-cfi",
            newText = "Stable quote updated",
            newColor = HighlightColor.BLUE,
            chapterIndex = 0,
            currentList = highlights,
            locator = locator.copy(cfi = "changed-cfi", textQuote = "Stable quote updated")
        )

        assertEquals(1, highlights.size)
        assertEquals(initialId, highlights.single().id)
        assertEquals(HighlightColor.BLUE, highlights.single().color)
        assertEquals(42, highlights.single().locator.startOffset)
    }

    @Test
    fun `highlight bridge parser accepts raw or wrapped json payloads`() {
        val payload = """{"cfi":"desktop:0:4:9","text":"word","colorId":"yellow","chapterIndex":0,"locator":{"chapterIndex":0,"startOffset":4,"endOffset":9,"textQuote":"word","cfi":"desktop:0:4:9"}}"""
        val wrappedPayload = "\"${payload.replace("\"", "\\\"")}\""
        val arrayPayload = "[$wrappedPayload]"

        assertEquals(4, EpubAnnotationSerializer.parseHighlightJsonLenient(payload)?.locator?.startOffset)
        assertEquals(9, EpubAnnotationSerializer.parseHighlightJsonLenient(wrappedPayload)?.locator?.endOffset)
        assertEquals("word", EpubAnnotationSerializer.parseHighlightJsonLenient(arrayPayload)?.text)
    }

    @Test
    fun `legacy desktop cfi values hydrate shared locators`() {
        val oldDesktopLocator = ReaderLocator.fromLegacy(cfi = "desktop:2:7:123456:abc")
        val timestampFallbackLocator = ReaderLocator.fromLegacy(cfi = "desktop:2:7:1780000000000")
        val rangedDesktopLocator = ReaderLocator.fromLegacy(cfi = "desktop:2:40:55")
        val scrollWrappedDesktopLocator = ReaderLocator.fromLegacy(cfi = "desktop-scroll:5238:5238:desktop:2:40:55")
        val androidLocator = ReaderLocator.fromLegacy(cfi = "android-locator:3:42:128")

        assertEquals(2, oldDesktopLocator.chapterIndex)
        assertEquals(7, oldDesktopLocator.pageIndex)
        assertEquals(7, timestampFallbackLocator.pageIndex)
        assertEquals(null, timestampFallbackLocator.startOffset)
        assertEquals(null, timestampFallbackLocator.endOffset)
        assertEquals(2, rangedDesktopLocator.chapterIndex)
        assertEquals(40, rangedDesktopLocator.startOffset)
        assertEquals(55, rangedDesktopLocator.endOffset)
        assertEquals(2, scrollWrappedDesktopLocator.chapterIndex)
        assertEquals(40, scrollWrappedDesktopLocator.startOffset)
        assertEquals(55, scrollWrappedDesktopLocator.endOffset)
        assertEquals("desktop:2:40:55", scrollWrappedDesktopLocator.cfi)
        assertEquals(3, androidLocator.chapterIndex)
        assertEquals(42, androidLocator.blockIndex)
        assertEquals(128, androidLocator.charOffset)
    }

    @Test
    fun `android locator with quote hydrates absolute text range for synced highlights`() {
        val locator = ReaderLocator.fromLegacy(
            cfi = "android-locator:3:42:128",
            textQuote = "marked"
        )

        assertEquals(3, locator.chapterIndex)
        assertEquals(42, locator.blockIndex)
        assertEquals(128, locator.charOffset)
        assertEquals(128, locator.startOffset)
        assertEquals(134, locator.endOffset)
    }
}
