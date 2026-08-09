package com.aryan.reader.paginatedreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SemanticLocatorPolicyTest {
    private fun paragraph(text: String, index: Int, cfi: String, offset: Int = 0) = SemanticParagraph(
        text = text,
        spans = emptyList(),
        style = CssStyle(),
        elementId = null,
        cfi = cfi,
        startCharOffsetInSource = offset,
        blockIndex = index
    )

    @Test
    fun `cfi parsing matching and local offsets preserve Android behavior`() {
        val shallow = paragraph("shallow", 1, "/4/2")
        val deep = paragraph("deep text", 2, "/4/2/6", offset = 100)
        val blocks = listOf(shallow, deep)

        assertEquals(ParsedSemanticCfi("/4/2/6", 7), parseSemanticCfi("/4/2/6:7|/ignored:9"))
        assertEquals(ParsedSemanticCfi("/4/2", 0), parseSemanticCfi("/4/2:not-a-number"))
        assertEquals(deep, findBestMatchingSemanticBlock(blocks, "/4/2/6"))
        assertEquals("/4/2/6:7", semanticCfiForBlock(deep, 107))
        assertEquals("/4/2/6", semanticCfiForBlock(deep, 100))
    }

    @Test
    fun `nested lookup text offsets and page estimates preserve Android traversal`() {
        val first = paragraph("first", 1, "/4/2")
        val nested = paragraph("nested", 2, "/6/2")
        val container = SemanticFlexContainer(
            children = listOf(nested),
            style = CssStyle(),
            elementId = null,
            cfi = null,
            blockIndex = 10
        )
        val blocks = listOf(first, container)

        assertEquals(nested, findSemanticBlockByIndex(blocks, 2))
        assertNull(findSemanticBlockByIndex(blocks, 404))
        assertEquals(first.text.length + 1 + 3, semanticTextOffset(blocks, 2, 3))
        assertEquals(1, estimateSemanticPageCount(blocks))
        assertEquals(2, estimateSemanticPageCount(listOf(paragraph("x".repeat(2501), 3, "/8"))))
    }
}
