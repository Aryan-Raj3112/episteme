package com.aryan.reader.shared.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.HighlightStyle
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Read-aloud paints its position band through the highlight pipeline, so it looks exactly like a
 * highlight the reader made. It is not one: nothing stores it, and no highlight list contains its id.
 *
 * Treating it as selectable meant tapping a spoken sentence opened the selection sheet on an id that
 * could not be found, so the highlight could not be recoloured or deleted — and because the band was
 * annotated over the whole page, it could shadow a real highlight sitting underneath it. It is now
 * painted and nothing more.
 */
class SharedNativeTransientBandTest {

    private val text = "alpha beta gamma delta"

    private fun userHighlight(id: String, start: Int, end: Int) = UserHighlight(
        id = id,
        cfi = "/4/2:$start",
        text = text.substring(start, end),
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            textQuote = text.substring(start, end),
            cfi = "/4/2:$start"
        )
    )

    /** Shaped exactly as [com.aryan.reader.shared.ReaderTtsChunk.toHighlight] builds one. */
    private fun playbackBand(start: Int, end: Int) = UserHighlight(
        id = "${com.aryan.reader.shared.TRANSIENT_BAND_ID_PREFIX}42_7",
        cfi = "/4/2:$start",
        text = text.substring(start, end),
        color = HighlightColor.YELLOW,
        chapterIndex = 0,
        locator = ReaderLocator(
            chapterIndex = 0,
            startOffset = start,
            endOffset = end,
            textQuote = text.substring(start, end),
            cfi = "/4/2:$start"
        )
    )

    private fun paint(vararg highlights: UserHighlight): AnnotatedString =
        buildAnnotatedString {
            append(text)
            applyHighlightsToTextRanges(
                highlights = highlights.toList(),
                chapterIndex = 0,
                pageIndex = 0,
                blockCfi = "/4/2",
                blockIndex = 1,
                blockCharOffset = 0,
                textStartOffset = 0,
                textLength = text.length,
                text = text
            )
        }

    private fun AnnotatedString.highlightIds(): List<String> =
        getStringAnnotations(ReaderNativeAnnotationHighlight, 0, length)
            .map { it.item }
            .distinct()

    @Test
    fun `the band is painted`() {
        val painted = paint(playbackBand(6, 10))

        // Read-aloud has to be visible, or the position indicator is simply gone.
        assertTrue(painted.spanStyles.isNotEmpty())
        assertEquals(6, painted.spanStyles.first().start)
        assertEquals(10, painted.spanStyles.first().end)
    }

    @Test
    fun `the band is not reported as a selectable highlight`() {
        val painted = paint(playbackBand(6, 10))

        assertTrue(painted.highlightIds().isEmpty())
    }

    @Test
    fun `a band over a real highlight does not shadow it`() {
        val real = userHighlight("real", 6, 10)
        val painted = paint(real, playbackBand(6, 10))

        // The band covers the same characters and is annotated first if it is annotated at all, so
        // this is the case where the user loses their own highlight.
        assertEquals(listOf("real"), painted.highlightIds())
        assertEquals(
            "real",
            painted.getStringAnnotations(ReaderNativeAnnotationHighlight, 8, 9).first().item
        )
    }

    @Test
    fun `a real highlight alone is still selectable`() {
        val painted = paint(userHighlight("real", 0, 5))

        assertEquals(listOf("real"), painted.highlightIds())
        assertEquals(
            "real",
            painted.getStringAnnotations(ReaderNativeAnnotationHighlight, 2, 3).first().item
        )
    }

    @Test
    fun `a band styled as a line is not annotated either`() {
        // The other branch of the painter annotated unconditionally, so an underline band would have
        // been tappable too.
        val band = playbackBand(0, 5).copy(style = HighlightStyle.UNDERLINE)
        val painted = paint(band)

        assertTrue(painted.highlightIds().isEmpty())
        assertTrue(
            painted.spanStyles.any { it.item.textDecoration != null },
            "a line-styled band should still draw its decoration"
        )
    }

    @Test
    fun `only ids carrying the shared prefix count as bands`() {
        // The prefix is one constant read by both the producer and the consumers; a user highlight
        // whose text merely mentions read-aloud must not be mistaken for one.
        val ordinary = userHighlight("tts-like-name", 0, 5)

        assertFalse(ordinary.isTransientPlaybackBand)
        assertTrue(playbackBand(0, 5).isTransientPlaybackBand)
        assertEquals(listOf("tts-like-name"), paint(ordinary).highlightIds())
    }
}