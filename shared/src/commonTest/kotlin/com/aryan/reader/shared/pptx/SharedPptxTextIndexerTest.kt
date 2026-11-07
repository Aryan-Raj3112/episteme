package com.aryan.reader.shared.pptx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract for the shared slide text indexer (search text + char boxes).
 * Both the JVM and iOS parsers delegate here, so these expectations pin
 * cross-platform highlight/search geometry.
 */
class SharedPptxTextIndexerTest {

    private fun shape(
        text: String,
        alignment: SharedPptxTextAlign = SharedPptxTextAlign.START,
        verticalAnchor: SharedPptxVerticalAnchor = SharedPptxVerticalAnchor.TOP,
        rotationDegrees: Float = 0f,
        renderText: Boolean = true
    ): SharedPptxShapeElement {
        return SharedPptxShapeElement(
            bounds = SharedPptxRect(0f, 0f, 200f, 100f),
            preset = "rect",
            fillColor = null,
            lineColor = null,
            lineWidthPoint = 0.75f,
            paragraphs = listOf(
                SharedPptxParagraph(
                    runs = listOf(SharedPptxTextRun(text, sizePt = 18f)),
                    alignment = alignment
                )
            ),
            hyperlink = null,
            placeholderKey = null,
            verticalAnchor = verticalAnchor,
            rotationDegrees = rotationDegrees,
            renderText = renderText
        )
    }

    @Test
    fun `index emits one box per char inside text bounds`() {
        val index = SharedPptxTextIndexer.index(listOf(shape("Hi")))

        assertEquals("Hi", index.text)
        assertEquals(2, index.charBoxes.size)
        index.charBoxes.forEach { box ->
            assertTrue(box.bounds.left >= 0f && box.bounds.top >= 0f)
            assertTrue(box.bounds.right <= 200f && box.bounds.bottom <= 100f)
        }
        assertTrue(index.charBoxes[0].bounds.left < index.charBoxes[1].bounds.left)
    }

    @Test
    fun `center alignment offsets boxes from the left inset`() {
        val index = SharedPptxTextIndexer.index(listOf(shape("Hi", alignment = SharedPptxTextAlign.CENTER)))

        assertEquals("Hi", index.text)
        // Centered in a 200pt shape, a 2-char line starts well right of the inset.
        assertTrue(index.charBoxes[0].bounds.left > 50f)
    }

    @Test
    fun `middle anchor starts lower than top anchor`() {
        val top = SharedPptxTextIndexer.index(listOf(shape("Hi")))
        val middle = SharedPptxTextIndexer.index(
            listOf(shape("Hi", verticalAnchor = SharedPptxVerticalAnchor.MIDDLE))
        )

        assertTrue(middle.charBoxes[0].bounds.top > top.charBoxes[0].bounds.top)
    }

    @Test
    fun `rotation changes box geometry`() {
        val flat = SharedPptxTextIndexer.index(listOf(shape("Hi")))
        val rotated = SharedPptxTextIndexer.index(listOf(shape("Hi", rotationDegrees = 90f)))

        assertEquals(flat.text, rotated.text)
        assertEquals(flat.charBoxes.size, rotated.charBoxes.size)
        assertTrue(flat.charBoxes[0].bounds != rotated.charBoxes[0].bounds)
    }

    @Test
    fun `hidden placeholder text is skipped`() {
        val index = SharedPptxTextIndexer.index(listOf(shape("Hi", renderText = false)))

        assertEquals("", index.text)
        assertEquals(0, index.charBoxes.size)
    }

    @Test
    fun `table cells are indexed`() {
        val table = SharedPptxTableElement(
            bounds = SharedPptxRect(0f, 0f, 200f, 100f),
            rows = listOf(
                SharedPptxTableRow(
                    heightPoint = null,
                    cells = listOf(
                        SharedPptxTableCell(
                            widthPoint = null,
                            fillColor = null,
                            lineColor = null,
                            paragraphs = listOf(
                                SharedPptxParagraph(runs = listOf(SharedPptxTextRun("AB", sizePt = 18f)))
                            )
                        )
                    )
                )
            )
        )
        val index = SharedPptxTextIndexer.index(listOf(table))

        assertTrue(index.text.contains("AB"))
        assertEquals(index.text.length, index.charBoxes.size)
    }
}
