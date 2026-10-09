package com.aryan.reader.shared.pdf

import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The streaming encoder replaced the DOM-then-String encode that OOMed the
 * saver on large ink collections (see the ink-annotation save OOM entries in
 * docs/crashlytics-triage.md). Its contract is that it writes exactly the same
 * bytes as [SharedPdfLegacyInkCodec.encode] — byte-identity is what keeps
 * already-synced sidecars and the unchanged-save check valid.
 */
class SharedPdfLegacyInkStreamEncoderTest {

    private fun stream(annotations: List<SharedPdfLegacyInkAnnotation>): String {
        val out = ByteArrayOutputStream()
        SharedPdfLegacyInkStreamEncoder.encode(annotations, out)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun annotations(count: Int) = (0 until count).map { index ->
        SharedPdfLegacyInkAnnotation(
            id = "ink-$index",
            pageIndex = index % 7,
            annotationTypeName = "INK",
            inkTypeName = if (index % 2 == 0) "PEN" else "PENCIL",
            colorArgb = -16777216 + index,
            strokeWidth = 0.01f * index,
            points = (0 until 12).map { point ->
                PdfPagePoint(0.001f * point, 0.002f * point, (index * 100 + point).toLong())
            },
            // Escapes and non-ASCII must survive identically on both paths.
            note = if (index % 3 == 0) "Note with \\ \"quotes\" and\nnewlines é中" else null,
        )
    }

    @Test
    fun `streamed encode is byte identical to the string encoder`() {
        for (count in listOf(0, 1, 2, 37)) {
            val annotations = annotations(count)
            assertEquals(
                SharedPdfLegacyInkCodec.encode(annotations),
                stream(annotations),
                "streamed encode diverged at $count annotations",
            )
        }
    }

    @Test
    fun `streamed encode round trips through decode`() {
        val annotations = annotations(5)
        val decoded = SharedPdfLegacyInkCodec.decode(stream(annotations)) { "generated" }
        assertEquals(annotations.size, decoded.annotations.size)
        assertEquals(annotations.first().id, decoded.annotations.first().id)
        assertEquals(annotations.first().points.size, decoded.annotations.first().points.size)
        assertEquals(annotations.first().note, decoded.annotations.first().note)
    }

    @Test
    fun `streamed encode applies the same annotation cap as the string encoder`() {
        val annotations = (0 until SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD + 50).map { index ->
            SharedPdfLegacyInkAnnotation(
                id = "a$index",
                pageIndex = 0,
                colorArgb = -1,
                strokeWidth = 0.5f,
                points = listOf(PdfPagePoint(0.1f, 0.2f, 0L)),
            )
        }
        assertEquals(
            SharedPdfLegacyInkCodec.encode(annotations),
            stream(annotations),
        )
        val decoded = SharedPdfLegacyInkCodec.decode(stream(annotations)) { "generated" }
        assertEquals(SharedPdfLegacyInkCodec.MAX_ANNOTATIONS_PER_LOAD, decoded.annotations.size)
    }

    @Test
    fun `streamed encode applies the same point cap as the string encoder`() {
        val annotations = listOf(
            SharedPdfLegacyInkAnnotation(
                id = "ink",
                pageIndex = 0,
                colorArgb = -1,
                strokeWidth = 0.5f,
                points = (0 until SharedPdfLegacyInkCodec.MAX_POINTS_PER_ANNOTATION + 500).map { index ->
                    PdfPagePoint(0.1f, 0.2f, index.toLong())
                },
            )
        )
        assertEquals(SharedPdfLegacyInkCodec.encode(annotations), stream(annotations))
        val decoded = SharedPdfLegacyInkCodec.decode(stream(annotations)) { "generated" }
        assertEquals(
            SharedPdfLegacyInkCodec.MAX_POINTS_PER_ANNOTATION,
            decoded.annotations.single().points.size,
        )
    }

    @Test
    fun `streamed encode leaves the stream open for the caller to close`() {
        val out = object : ByteArrayOutputStream() {
            var closed = false
            override fun close() {
                closed = true
                super.close()
            }
        }
        SharedPdfLegacyInkStreamEncoder.encode(annotations(1), out)
        assertTrue(out.size() > 0)
        assertTrue(!out.closed, "encoder must not close a stream it does not own")
    }
}