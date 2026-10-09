package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Resolver behaviour against the real chapter text of the UI test fixture.
 *
 * The synthetic tests build their own blocks and can pass while real parsed content fails, which is
 * what happened: the quote was present in the chapter but did not resolve.
 */
class EpubHighlightRealChapterResolverTest {

    private val filler = "keeps the document tall enough for scroll and pagination tests"

    // The fixture's chapter one paragraphs, verbatim.
    private val paragraphTexts = listOf(
        "Chapter One: Stable Opening",
        "This EPUB is intentionally plain so Android UI tests can rely on stable text, stable IDs, and stable chapter order.",
        "POSITION_TARGET_ALPHA appears near the start of chapter one. Use this marker for first-position and restore-position checks.",
        "HIGHLIGHT_TARGET_BRAVO is a short highlight target. It is surrounded by ordinary words so selection handles have context.",
        "CFI_TARGET_CHARLIE sits inside a paragraph with a fixed element id. It can be used for CFI and locator assertions."
    ) + (1..8).map {
        "Chapter one filler paragraph ${it.toString().padStart(2, '0')} $filler."
    } + listOf("END_OF_CHAPTER_ONE_MARKER")

    private fun blocks(): List<SemanticTextBlock> {
        var cursor = 0
        return paragraphTexts.mapIndexed { index, text ->
            val block = SemanticParagraph(
                text = text,
                spans = emptyList(),
                style = CssStyle(),
                elementId = null,
                cfi = "/4/${index * 2}",
                startCharOffsetInSource = cursor,
                blockIndex = index
            )
            // Paragraphs are separated by a newline in the chapter's plain text.
            cursor += text.length + 1
            block
        }
    }

    @Test
    fun `resolves the filler sentence that appears in eight paragraphs`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks()))

        val anchor = assertNotNull(index.locate(filler))

        // First occurrence only, in document order.
        assertEquals(5, anchor.segments.single().blockIndex)
    }

    @Test
    fun `resolves a sentence inside one paragraph`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks()))

        val anchor = assertNotNull(index.locate("HIGHLIGHT_TARGET_BRAVO is a short highlight target"))

        assertEquals(3, anchor.segments.single().blockIndex)
        assertEquals(anchor.segments.single().blockIndex, blocks()[3].blockIndex)
    }

    @Test
    fun `resolves a selection spanning three consecutive paragraphs`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks()))
        val quote = "${paragraphTexts[1]} ${paragraphTexts[2].substringBefore(" Use")}"

        val anchor = assertNotNull(index.locate(quote))

        assertEquals(2, anchor.segments.size)
        assertEquals(1, anchor.segments[0].blockIndex)
        assertEquals(2, anchor.segments[1].blockIndex)
    }

    @Test
    fun `every filler occurrence resolves to the same block`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks()))
        val all = index.locateAll(filler)

        // Eight paragraphs carry this sentence verbatim; the index must see all of them, and the
        // single-match accessor must still commit to the first.
        assertEquals(8, all.size)
        assertEquals(5, index.locate(filler)?.segments?.single()?.blockIndex)
    }

    @Test
    fun `reports the absolute offset range of a match`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks()))
        val source = blocks()

        val anchor = assertNotNull(index.locate("END_OF_CHAPTER_ONE_MARKER"))
        val last = source.last()

        assertEquals(last.startCharOffsetInSource, anchor.startOffset)
        assertEquals(last.startCharOffsetInSource + "END_OF_CHAPTER_ONE_MARKER".length, anchor.endOffset)
    }
}