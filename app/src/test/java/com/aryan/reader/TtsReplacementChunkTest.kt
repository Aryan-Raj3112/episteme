package com.aryan.reader

import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementRule
import org.junit.Assert.assertEquals
import org.junit.Test

class TtsReplacementChunkTest {
    @Test
    fun `tts chunk spoken text falls back to original text`() {
        val chunk = TtsChunk(
            text = "Dr. Smith",
            sourceCfi = "epubcfi(/6/2)",
            startOffsetInSource = 12
        )

        assertEquals("Dr. Smith", chunk.spokenText)
    }

    @Test
    fun `chunk preparation keeps original text and writes spoken text`() {
        val preferences = ReaderTtsReplacementPreferences(
            globalRules = listOf(
                ReaderTtsReplacementRule(
                    id = "dr",
                    from = "Dr.",
                    to = "Doctor",
                    wholeWord = false
                )
            )
        )
        val chunk = TtsChunk(
            text = "Dr. Smith",
            sourceCfi = "epubcfi(/6/2)",
            startOffsetInSource = 12
        )

        val prepared = listOf(chunk).withTtsReplacements(preferences, "book").single()

        assertEquals("Dr. Smith", prepared.text)
        assertEquals("Doctor Smith", prepared.spokenText)
        assertEquals("epubcfi(/6/2)", prepared.sourceCfi)
        assertEquals(12, prepared.startOffsetInSource)
    }
}
