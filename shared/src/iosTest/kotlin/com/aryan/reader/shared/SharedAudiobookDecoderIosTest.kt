package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SharedAudiobookDecoderIosTest {

    @Test
    fun avPlayerCannotDecodeOggAndOpus() {
        assertEquals(
            SharedAudiobookFormats.supportedExtensions - setOf("ogg", "opus"),
            sharedDecodableAudiobookExtensions,
        )
    }

    @Test
    fun splitsImportsIntoDecodableAndUnsupported() {
        data class File(val name: String)

        val files = listOf(File("a.mp3"), File("b.ogg"), File("c.m4b"), File("d.opus"), File("e.flac"))
        val split = splitFilesByAudiobookDecodability(files) { it.name }
        assertEquals(listOf("a.mp3", "c.m4b", "e.flac"), split.decodable.map { it.name })
        assertEquals(listOf("b.ogg", "d.opus"), split.unsupported.map { it.name })
        assertTrue(split.unsupported.isNotEmpty())
    }
}
