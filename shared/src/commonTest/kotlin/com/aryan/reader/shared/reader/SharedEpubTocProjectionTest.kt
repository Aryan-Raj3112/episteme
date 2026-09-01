package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderLocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedEpubTocProjectionTest {
    private val entries = listOf(
        SharedEpubTocEntry("Part One", "part.xhtml", depth = 0),
        SharedEpubTocEntry("Chapter One", "chapter-one.xhtml", depth = 1),
        SharedEpubTocEntry("Section", "chapter-one.xhtml", "section", depth = 2),
        SharedEpubTocEntry("Chapter Two", "chapter-two.xhtml", depth = 1),
        SharedEpubTocEntry("Part Two", "part-two.xhtml", depth = 0),
    )

    @Test
    fun `parent expansion projects nested rows and preserves original indices`() {
        val parents = readerTocParentIndices(entries) { it.depth }
        assertEquals(setOf(0, 1), parents)

        val collapsed = projectReaderTocEntries(
            entries = entries,
            expandedEntryIndices = emptySet(),
            query = "",
            labelOf = { it.label },
            depthOf = { it.depth }
        )
        assertEquals(listOf(0, 4), collapsed.map { it.originalIndex })

        val expanded = projectReaderTocEntries(
            entries = entries,
            expandedEntryIndices = parents,
            query = "",
            labelOf = { it.label },
            depthOf = { it.depth }
        )
        assertEquals(listOf(0, 1, 2, 3, 4), expanded.map { it.originalIndex })
        assertTrue(expanded[0].hasChildren)
        assertTrue(expanded[1].hasChildren)
        assertFalse(expanded[3].hasChildren)
    }

    @Test
    fun `search includes ancestors while marking only matching rows`() {
        val result = projectReaderTocEntries(
            entries = entries,
            expandedEntryIndices = emptySet(),
            query = "section",
            labelOf = { it.label },
            depthOf = { it.depth }
        )

        assertEquals(listOf(0, 1, 2), result.map { it.originalIndex })
        assertEquals(listOf(false, false, true), result.map { it.isQueryMatch })
    }

    @Test
    fun `locate expands active ancestors and returns projected list index`() {
        val plan = readerTocLocatePlan(
            entries = entries,
            expandedEntryIndices = emptySet(),
            activeOriginalIndex = 2,
            depthOf = { it.depth }
        )

        assertEquals(setOf(0, 1), plan.expandedEntryIndices)
        assertEquals(2, plan.visibleOriginalIndex)
        assertEquals(2, plan.visibleIndex)
    }

    @Test
    fun `individual expansion toggles only parent branches`() {
        val parents = readerTocParentIndices(entries) { it.depth }

        val collapsedChapter = readerTocToggleExpansion(
            entries = entries,
            expandedEntryIndices = parents,
            originalIndex = 1,
            depthOf = { it.depth }
        )
        assertEquals(setOf(0), collapsedChapter)

        val reExpandedChapter = readerTocToggleExpansion(
            entries = entries,
            expandedEntryIndices = collapsedChapter,
            originalIndex = 1,
            depthOf = { it.depth }
        )
        assertEquals(parents, reExpandedChapter)

        val leafToggle = readerTocToggleExpansion(
            entries = entries,
            expandedEntryIndices = parents,
            originalIndex = 2,
            depthOf = { it.depth }
        )
        assertEquals(parents, leafToggle)
    }

    @Test
    fun `empty toc falls back to every chapter`() {
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter("one", "One", "", baseHref = "Text/one.xhtml", depth = 0),
                SharedEpubChapter("two", "Two", "", baseHref = "Text/two.xhtml", depth = 1),
            )
        )

        assertEquals(
            listOf("One", "Two"),
            book.effectiveReaderTocEntries().map { it.label }
        )
        assertEquals(listOf(0, 1), book.effectiveReaderTocEntries().map { it.depth })
    }

    @Test
    fun `active index follows native chapter locator and webview fragment`() {
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter("one", "One", "", baseHref = "part.xhtml"),
                SharedEpubChapter("two", "Two", "", baseHref = "chapter-two.xhtml"),
            ),
            tableOfContents = entries
        )

        assertEquals(
            3,
            readerTocActiveIndex(
                entries,
                book,
                ReaderLocator(chapterIndex = 1, href = "Text/chapter-two.xhtml")
            )
        )
        assertEquals(
            2,
            readerTocActiveIndex(
                entries,
                book,
                ReaderLocator(chapterIndex = 1, href = "chapter-one.xhtml"),
                activeHref = "chapter-one.xhtml",
                activeFragmentId = "section"
            )
        )
        assertEquals(
            0,
            readerTocActiveIndex(
                entries = book.effectiveReaderTocEntries(),
                book = book,
                locator = ReaderLocator(chapterIndex = 0)
            )
        )
    }
}
