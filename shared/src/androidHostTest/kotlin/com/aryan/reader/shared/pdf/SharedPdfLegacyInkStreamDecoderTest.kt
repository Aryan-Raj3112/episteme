package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedPdfLegacyInkStreamDecoderTest {

    @Test
    fun `streaming decode matches string decode on round trip`() {
        val annotations = (0 until 500).map { index ->
            SharedPdfLegacyInkAnnotation(
                id = "ink-$index",
                pageIndex = index % 7,
                annotationTypeName = "INK",
                inkTypeName = if (index % 2 == 0) "PEN" else "PENCIL",
                colorArgb = -16777216 + index,
                strokeWidth = 0.01f * index,
                points = (0 until 20).map { point ->
                    PdfPagePoint(0.001f * point, 0.002f * point, point.toLong())
                },
                note = if (index % 3 == 0) "Note with \\ \"quotes\" and\nnewlines" else null,
            )
        }
        val json = SharedPdfLegacyInkCodec.encode(annotations)

        val streamed = SharedPdfLegacyInkStreamDecoder.decode(json.byteInputStream()) { "generated" }
        val inMemory = SharedPdfLegacyInkCodec.decode(json) { "generated" }

        assertEquals(inMemory, streamed)
        assertEquals(annotations.map { it.id }, streamed.annotations.map { it.id })
        assertEquals(annotations.map { it.pageIndex }, streamed.annotations.map { it.pageIndex })
        assertFalse(streamed.annotationsWereCapped)
        assertFalse(streamed.pointsWereCapped)
    }

    @Test
    fun `streaming decode keeps cap policy aligned with string decode`() {
        val points = (0..5_000).joinToString(separator = ",") { index ->
            """{"x":0.1,"y":0.2,"t":$index}"""
        }
        val json = """[{"pageIndex":1,"color":-1,"strokeWidth":0.5,"points":[$points]}]"""

        val streamed = SharedPdfLegacyInkStreamDecoder.decode(json.byteInputStream()) { "generated" }
        val inMemory = SharedPdfLegacyInkCodec.decode(json) { "generated" }

        assertEquals(inMemory, streamed)
        assertEquals(SharedPdfLegacyInkCodec.MAX_POINTS_PER_ANNOTATION, streamed.annotations.single().points.size)
        assertTrue(streamed.pointsWereCapped)
    }

    @Test
    fun `streaming decode caps annotation count and stops pulling elements`() {
        val size = SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD + 50
        val json = buildString {
            append("[")
            repeat(size) { index ->
                if (index > 0) append(",")
                append("""{"id":"a$index","pageIndex":2,"color":-1,"strokeWidth":0.5,"points":[]}""")
            }
            append("]")
        }

        val streamed = SharedPdfLegacyInkStreamDecoder.decode(json.byteInputStream()) { "generated" }
        val inMemory = SharedPdfLegacyInkCodec.decode(json) { "generated" }

        assertEquals(inMemory, streamed)
        assertEquals(SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD, streamed.annotations.size)
        assertTrue(streamed.annotationsWereCapped)
        assertEquals("a0", streamed.annotations.first().id)
        assertEquals(
            "a${SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD - 1}",
            streamed.annotations.last().id,
        )
    }

    @Test
    fun `streaming decode falls back to empty on malformed truncated or non array input`() {
        val inputs = listOf(
            "",
            "not json",
            """{"not":"an array"}""",
            """[""""",
            """[{"pageIndex":1,"color":-1,"strokeWidth":0.5,"points":[{"x":0.1,"y":0.2}]}""", // truncated
            """[{"pageIndex":1,"color":-1,"strokeWidth":0.5,}""",
        )
        inputs.forEach { raw ->
            val streamed = SharedPdfLegacyInkStreamDecoder.decode(raw.byteInputStream()) { "generated" }
            assertTrue(streamed.annotations.isEmpty(), "expected empty decode for: $raw")
        }
    }

    @Test
    fun `streaming decode preserves escaped unicode and blank field fallbacks`() {
        val json = """
            [
              {
                "id": "",
                "pageIndex": 5,
                "annotationType": "INK",
                "inkType": "",
                "type": "",
                "color": -1,
                "strokeWidth": 0.25,
                "note": "„ÄÖÜ é\ttab \"quoted\"",
                "points": [{"x":0.5,"y":0.25,"t":42}]
              }
            ]
        """.trimIndent()

        val streamed = SharedPdfLegacyInkStreamDecoder.decode(json.byteInputStream()) { "generated-id" }
        val inMemory = SharedPdfLegacyInkCodec.decode(json) { "generated-id" }

        assertEquals(inMemory, streamed)
        val annotation = streamed.annotations.single()
        assertEquals("generated-id", annotation.id) // blank id falls back like string decode
        assertEquals("PEN", annotation.inkTypeName)
        assertEquals("„ÄÖÜ é\ttab \"quoted\"", annotation.note)
        assertEquals(PdfPagePoint(0.5f, 0.25f, 42L), annotation.points.single())
    }
}
