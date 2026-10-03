package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EpubHighlightAnchorResolverTest {

    private fun paragraph(blockIndex: Int, start: Int, text: String) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = "/4/2/${blockIndex * 2}",
        startCharOffsetInSource = start,
        blockIndex = blockIndex
    )

    private fun listItem(blockIndex: Int, start: Int, text: String) = SemanticListItem(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = "/4/2/${blockIndex * 2}",
        startCharOffsetInSource = start,
        blockIndex = blockIndex,
        itemMarkerImage = null
    )

    private val filler = "keeps the document tall enough for scroll and pagination tests"

    private fun contiguousBlocks(): List<SemanticBlock> =
        listOf(paragraph(0, 0, "alpha beta gamma")) +
            (1..4).map { paragraph(it, 0, filler) } +
            listOf(paragraph(5, 0, "omega delta sigma"))

    @Test
    fun `locates a quote inside a single block and reports block-local and absolute ranges`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, contiguousBlocks()))
        val anchor = assertNotNull(index.locate("beta"))

        assertEquals(0, anchor.chapterIndex)
        assertEquals(HighlightAnchorConfidence.QuoteMatched, anchor.confidence)
        assertEquals(6, anchor.startOffset)
        assertEquals(10, anchor.endOffset)

        val segment = anchor.segments.single()
        assertEquals(0, segment.blockIndex)
        assertEquals("/4/2/0", segment.blockCfi)
        assertEquals(6, segment.localStart)
        assertEquals(10, segment.localEnd)
        assertEquals(6, segment.absoluteStart)
        assertEquals(10, segment.absoluteEnd)
    }

    @Test
    fun `locates a quote that repeats and commits to exactly one occurrence`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, contiguousBlocks()))

        val anchor = assertNotNull(index.locate(filler))

        // Four blocks contain this sentence verbatim. The resolver must pick one, not four:
        // painting all of them is what duplicated a highlight across pages.
        assertEquals(1, anchor.segments.size)
        assertEquals(1, anchor.segments.single().blockIndex)
    }

    @Test
    fun `locates a quote spanning consecutive blocks and emits one segment per block`() {
        val blocks = contiguousBlocks()
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks))
        val alpha = blocks[0] as SemanticParagraph
        val beta = blocks[1] as SemanticParagraph

        val anchor = assertNotNull(index.locate("${alpha.text} ${beta.text.take(20)}"))

        assertEquals(2, anchor.segments.size)
        assertEquals(0, anchor.segments[0].blockIndex)
        assertEquals(1, anchor.segments[1].blockIndex)

        // Segment one is the whole of the first block; segment two starts at its own offset zero.
        val head = anchor.segments[0]
        assertEquals(0, head.localStart)
        assertEquals(alpha.text.length, head.localEnd)
        assertEquals(0, head.absoluteStart)

        val tail = anchor.segments[1]
        assertEquals(0, tail.localStart)
        assertEquals(20, tail.localEnd)
        // Blocks are laid out one separator apart, so the second starts just past the first.
        assertEquals(alpha.text.length + 1 + 20, tail.absoluteEnd)

        // The anchor spans the boundary: start in the first block, end in the second.
        assertEquals(0, anchor.startOffset)
        assertEquals(alpha.text.length + 1 + 20, anchor.endOffset)
    }

    @Test
    fun `matches a quote that differs only in whitespace`() {
        val source = contiguousBlocks()
        val index = assertNotNull(EpubChapterTextIndex.of(0, source))

        val anchor = assertNotNull(index.locate("alpha   beta\n\tgamma"))

        val segment = anchor.segments.single()
        val block = source[0] as SemanticParagraph
        assertEquals("alpha beta gamma", block.text.substring(segment.localStart, segment.localEnd))
    }

    @Test
    fun `locates a quote that spans consecutive blocks`() {
        // Blocks are laid out one separator apart, so a quote covering the end of one paragraph and
        // the start of the next matches across the join.
        val blocks = listOf(
            paragraph(0, 0, "alpha beta"),
            paragraph(1, 999, "gamma delta")
        )
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks))

        val anchor = assertNotNull(index.locate("beta gamma"))

        assertEquals(2, anchor.segments.size)
        assertEquals(0, anchor.segments[0].blockIndex)
        assertEquals(1, anchor.segments[1].blockIndex)
    }

    @Test
    fun `ignores the offsets a block reports about itself`() {
        // Parsing restarts its offset counter per element, so real parsed chapters report the same
        // start for every block. Laying the chapter out here is what makes positions comparable, and
        // this asserts the reported values are not consulted.
        val blocks = listOf(
            paragraph(0, 0, "first paragraph"),
            paragraph(1, 0, "second paragraph"),
            paragraph(2, 0, "third paragraph")
        )
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks))

        val anchor = assertNotNull(index.locate("second paragraph"))

        assertEquals(1, anchor.segments.single().blockIndex)
        assertEquals(0, anchor.segments.single().localStart)
        assertEquals("second paragraph".length, anchor.segments.single().localEnd)
    }

    @Test
    fun `stays found when a quote appears in more than one block`() {
        val blocks = listOf(
            paragraph(0, 0, "shared sentence here"),
            paragraph(1, 0, "filler"),
            paragraph(2, 0, "shared sentence here")
        )
        val index = assertNotNull(EpubChapterTextIndex.of(0, blocks))

        val anchor = assertNotNull(index.locate("shared sentence here"))

        assertEquals(0, anchor.segments.single().blockIndex)
        assertEquals(2, index.locateAll("shared sentence here").size)
    }

    @Test
    fun `returns null for a quote that is not present`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, contiguousBlocks()))

        assertNull(index.locate("this sentence appears nowhere in the chapter"))
    }

    @Test
    fun `returns null for a blank quote`() {
        val index = assertNotNull(EpubChapterTextIndex.of(0, contiguousBlocks()))

        assertNull(index.locate("   "))
    }

    @Test
    fun `returns null index for a chapter with no text blocks`() {
        assertNull(EpubChapterTextIndex.of(0, listOf(SemanticSpacer(CssStyle(), null, null))))
    }

    @Test
    fun `flattens list and table blocks into text blocks in document order`() {
        val list = SemanticList(
            items = listOf(
                listItem(10, 0, "first item"),
                listItem(11, 11, "second item")
            ),
            isOrdered = false,
            style = CssStyle(),
            elementId = null,
            cfi = "/4/6",
            blockIndex = 1
        )
        // Block indices are the values each block declares, so the assertion is on which block was
        // matched, not on a positional index into the flattened list.
        val table = SemanticTable(
            rows = listOf(
                listOf(
                    SemanticTableCell(
                        content = listOf(paragraph(20, 22, "cell one")),
                        isHeader = false,
                        colspan = 1,
                        style = CssStyle()
                    ),
                    SemanticTableCell(
                        content = listOf(paragraph(21, 31, "cell two")),
                        isHeader = false,
                        colspan = 1,
                        style = CssStyle()
                    )
                )
            ),
            style = CssStyle(),
            elementId = null,
            cfi = "/4/8",
            blockIndex = 2
        )
        val index = assertNotNull(EpubChapterTextIndex.of(0, listOf(list, table)))

        assertEquals(10, assertNotNull(index.locate("first item")).segments.single().blockIndex)
        assertEquals(11, assertNotNull(index.locate("second item")).segments.single().blockIndex)
        assertEquals(20, assertNotNull(index.locate("cell one")).segments.single().blockIndex)
        assertEquals(21, assertNotNull(index.locate("cell two")).segments.single().blockIndex)
    }

    }