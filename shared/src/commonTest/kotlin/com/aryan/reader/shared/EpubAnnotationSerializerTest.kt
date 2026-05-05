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
                note = "Important"
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
            chapterIndex = 1
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
}
