package com.aryan.reader.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsChunkSourceOffsetsTest {

    @Test
    fun `every chunk starts exactly where its text sits in the source`() {
        // Whitespace runs are the whole difficulty: the splitter removes them, so an offset
        // accumulated from chunk lengths would fall behind the text it names.
        val source = buildString {
            append("The first sentence ends here.\n\n")
            append("The second sentence runs on with several clauses, and keeps going ")
            append("until it finally stops.\t")
            append("A third sentence follows at once.\n")
            append("And a fourth one closes the block.")
        }

        val spans = splitTextIntoChunksWithSourceOffsets(source, maxLengthPerChunk = 60)

        assertTrue(spans.isNotEmpty())
        spans.forEach { span ->
            assertEquals(
                "chunk text is not at its recorded offset",
                span.text,
                source.substring(span.startOffsetInSource, span.startOffsetInSource + span.text.length)
            )
        }
        // The source's words are fully covered in order: nothing dropped, nothing repeated.
        assertEquals(
            source.split(Regex("\\s+")).filter { it.isNotBlank() },
            spans.joinToString(" ") { it.text }.split(Regex("\\s+")).filter { it.isNotBlank() }
        )
    }

    @Test
    fun `offsets survive the line breaks a br element produced`() {
        val source = "Sunagakure\nRyoma, Recently Promoted Ninja\n\n" +
            "\"Congratulations on your promotion, Jonin Ryoma.\" The man on the other side of " +
            "the desk was not a particularly intimidating man, on first look.\n\n" +
            "That emblem read 'Wind Shadow', and that was reason enough to fear him."

        val spans = splitTextIntoChunksWithSourceOffsets(source, maxLengthPerChunk = 70)

        assertTrue(spans.size > 2)
        spans.forEach { span ->
            assertEquals(span.text, source.substring(span.startOffsetInSource, span.startOffsetInSource + span.text.length))
        }
    }

    @Test
    fun `a chapter of one long element stays addressable from the middle`() {
        val paragraph = (1..40).joinToString(" ") { "Sentence number $it carries a handful of words." }
        val chapter = "Chapter Title\n\n$paragraph"

        val spans = splitTextIntoChunksWithSourceOffsets(chapter, maxLengthPerChunk = 120)
        val midway = spans[spans.size / 2]

        assertEquals(
            midway.text,
            chapter.substring(midway.startOffsetInSource, midway.startOffsetInSource + midway.text.length)
        )
        // A reader scrolled to the middle of the chapter must land on a chunk whose offset is in
        // the middle too, which is what makes starting playback there possible.
        assertTrue(midway.startOffsetInSource > chapter.length / 3)
    }

    @Test
    fun `offsets agree with the splitter's own text`() {
        val source = "One two three four five six seven eight nine ten. " +
            "Eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty. " +
            "Twenty one twenty two twenty three twenty four twenty five."

        val offsets = splitTextIntoChunksWithSourceOffsets(source, maxLengthPerChunk = 48)
        val plain = splitTextIntoChunks(source, maxLengthPerChunk = 48)

        assertEquals(plain, offsets.map { it.text })
    }

    @Test
    fun `blank text yields nothing rather than a zero offset`() {
        assertEquals(emptyList<TtsChunkSourceSpan>(), splitTextIntoChunksWithSourceOffsets("   \n\t "))
        assertTrue(splitTextIntoChunksWithSourceOffsets("\n \n").isEmpty())
    }
}
