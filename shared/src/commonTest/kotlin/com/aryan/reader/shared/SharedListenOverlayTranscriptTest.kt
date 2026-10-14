package com.aryan.reader.shared

import com.aryan.reader.shared.reader.SharedMediaOverlayClip
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedListenOverlayTranscriptTest {
    private fun clip(
        index: Int,
        elementId: String?,
        textHref: String = "OEBPS/ch1.xhtml",
    ) = SharedMediaOverlayClip(
        parId = "par$index",
        clipIndex = index,
        textHref = textHref,
        elementId = elementId,
        audioPath = "OEBPS/audio.mp3",
        clipBeginMs = index * 1_000L,
        clipEndMs = (index + 1) * 1_000L,
        epubTypes = emptySet(),
        seqDepth = 0,
    )

    private val verseHtml = """
        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Verse</title></head><body>
        <p><span id="l1">Roses are red,</span><span id="l2">violets are blue,</span></p>
        <p><span id="l3">sugar is sweet,</span><span id="l4">and so are you.</span></p>
        </body></html>
    """.trimIndent()

    @Test
    fun `one clip per line anchors one to one like the reader`() {
        val clips = (1..4).map { clip(it - 1, "l$it") }
        val chunks = listOf(
            "Roses are red, violets are blue,",
            "sugar is sweet, and so are you.",
        )
        val spans = sharedOverlayClipChunkSpans(clips, { verseHtml }, chunks)
        assertEquals(4, spans.size)
        assertEquals(SharedOverlayClipChunkSpan(0, 0), spans[0])
        assertEquals(SharedOverlayClipChunkSpan(0, 0), spans[1])
        assertEquals(SharedOverlayClipChunkSpan(1, 1), spans[2])
        assertEquals(SharedOverlayClipChunkSpan(1, 1), spans[3])
    }

    @Test
    fun `long clip spanning chunks covers the range for interpolation`() {
        val html = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <p id="p1">Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu.</p>
            </body></html>
        """.trimIndent()
        val clips = listOf(clip(0, "p1"))
        val chunks = listOf("Alpha beta gamma", "delta epsilon zeta", "eta theta iota kappa lambda mu.")
        val spans = sharedOverlayClipChunkSpans(clips, { html }, chunks)
        assertEquals(listOf(SharedOverlayClipChunkSpan(0, 2)), spans)
        assertEquals(0, sharedSpanTranscriptChunkIndex(spans[0], 0f, chunks.size))
        assertEquals(1, sharedSpanTranscriptChunkIndex(spans[0], 0.5f, chunks.size))
        assertEquals(2, sharedSpanTranscriptChunkIndex(spans[0], 1f, chunks.size))
    }

    @Test
    fun `repeated lines advance in order instead of sticking on the first refrain`() {
        val html = """
            <html xmlns="http://www.w3.org/1999/xhtml"><body>
            <p><span id="a">oh lord</span></p>
            <p><span id="b">a middle line here</span></p>
            <p><span id="c">oh lord</span></p>
            </body></html>
        """.trimIndent()
        val clips = listOf(clip(0, "a"), clip(1, "b"), clip(2, "c"))
        val chunks = listOf("oh lord", "a middle line here", "oh lord")
        val spans = sharedOverlayClipChunkSpans(clips, { html }, chunks)
        assertEquals(
            listOf(
                SharedOverlayClipChunkSpan(0, 0),
                SharedOverlayClipChunkSpan(1, 1),
                SharedOverlayClipChunkSpan(2, 2),
            ),
            spans,
        )
    }

    @Test
    fun `unresolvable clips hold the previous line instead of jumping`() {
        val clips = listOf(
            clip(0, "l1"),
            // Audio-only interlude: no text anchor, like a page-break sound.
            clip(1, null),
            // Element the chapter does not contain.
            clip(2, "missing"),
            clip(3, "l3"),
        )
        val chunks = listOf(
            "Roses are red, violets are blue,",
            "sugar is sweet, and so are you.",
        )
        val spans = sharedOverlayClipChunkSpans(clips, { verseHtml }, chunks)
        assertEquals(4, spans.size)
        assertEquals(SharedOverlayClipChunkSpan(0, 0), spans[0])
        assertEquals(SharedOverlayClipChunkSpan(0, 0), spans[1])
        assertEquals(SharedOverlayClipChunkSpan(0, 0), spans[2])
        assertEquals(SharedOverlayClipChunkSpan(1, 1), spans[3])
    }

    @Test
    fun `clips read each text document once across files`() {
        val read = mutableMapOf<String, Int>()
        val htmlByPath = mapOf(
            "OEBPS/a.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="x">First file line.</p></body></html>""",
            "OEBPS/b.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><body><p id="y">Second file line.</p></body></html>""",
        )
        val clips = listOf(clip(0, "x", "OEBPS/a.xhtml"), clip(1, "y", "OEBPS/b.xhtml"))
        val chunks = listOf("First file line.", "Second file line.")
        val spans = sharedOverlayClipChunkSpans(
            clips,
            { path -> read[path] = (read[path] ?: 0) + 1; htmlByPath[path] },
            chunks,
        )
        assertEquals(
            listOf(SharedOverlayClipChunkSpan(0, 0), SharedOverlayClipChunkSpan(1, 1)),
            spans,
        )
        assertEquals(mapOf("OEBPS/a.xhtml" to 1, "OEBPS/b.xhtml" to 1), read)
    }

    @Test
    fun `unparseable html falls back to empty spans for the fractional estimate`() {
        val clips = listOf(clip(0, "l1"))
        assertTrue(sharedOverlayClipChunkSpans(clips, { null }, listOf("chunk")).isEmpty())
        assertTrue(sharedOverlayClipChunkSpans(clips, { verseHtml }, emptyList()).isEmpty())
        assertTrue(sharedOverlayClipChunkSpans(emptyList(), { verseHtml }, listOf("chunk")).isEmpty())
    }

    @Test
    fun `anchored spans win only when they line up with the chapter`() {
        val spans = listOf(SharedOverlayClipChunkSpan(2, 2), SharedOverlayClipChunkSpan(3, 4))
        // Aligned: the anchor decides, not the fraction.
        assertEquals(2, sharedOverlayTranscriptChunkIndex(0, 2, 10, 0f, spans))
        assertEquals(4, sharedOverlayTranscriptChunkIndex(1, 2, 10, 1f, spans))
        // Stale table (another chapter's length): the fraction decides.
        assertEquals(0, sharedOverlayTranscriptChunkIndex(0, 5, 10, 0f, spans))
        // No spans: the longtime fractional behavior, unchanged.
        assertEquals(2, sharedOverlayTranscriptChunkIndex(5, 10, 5))
    }

    @Test
    fun `span highlight clamps fractions and stays inside the chapter`() {
        val span = SharedOverlayClipChunkSpan(3, 1)
        assertEquals(1, sharedSpanTranscriptChunkIndex(span, -1f, 10))
        assertEquals(3, sharedSpanTranscriptChunkIndex(span, 9f, 10))
        assertEquals(-1, sharedSpanTranscriptChunkIndex(span, 0.5f, 0))
        assertEquals(0, sharedSpanTranscriptChunkIndex(SharedOverlayClipChunkSpan(0, 0), 0.5f, 1))
    }
}
