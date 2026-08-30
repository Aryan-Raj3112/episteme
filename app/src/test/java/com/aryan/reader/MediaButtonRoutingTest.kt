package com.aryan.reader

import com.aryan.reader.audiobook.AudiobookPlaybackService
import com.aryan.reader.tts.TtsService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class MediaButtonRoutingTest {

    @Test
    fun `exactly one exported media button receiver is declared for the framework`() {
        val receivers = parseManifest().documentElement.elements("receiver")
        val mediaButtonReceivers = receivers.filter {
            it.elements("intent-filter").any { filter ->
                filter.elements("action").any { it.getAttribute("android:name") == "android.intent.action.MEDIA_BUTTON" }
            }
        }

        assertEquals(1, mediaButtonReceivers.size)
        val receiver = mediaButtonReceivers.single()
        assertEquals(MediaButtonRoutingReceiver::class.java.name, receiver.getAttribute("android:name"))
        assertEquals("true", receiver.getAttribute("android:exported"))
    }

    @Test
    fun `resolver falls back to tts service and honours a stored audiobook target`() {
        assertEquals(
            TtsService::class.java.name,
            resolveMediaButtonTargetServiceClassName(null)
        )
        assertEquals(
            TtsService::class.java.name,
            resolveMediaButtonTargetServiceClassName("some/unknown/Class")
        )
        assertEquals(
            AudiobookPlaybackService::class.java.name,
            resolveMediaButtonTargetServiceClassName(AudiobookPlaybackService::class.java.name)
        )
        assertEquals(
            AudiobookPlaybackService::class.java.name,
            resolveMediaButtonTargetServiceClassName(MediaButtonRouting.AUDIOBOOK_SERVICE_CLASS_NAME)
        )
    }

    @Test
    fun `silence anchor asset is a valid pcm wav with real data bytes`() {
        val wav = sequenceOf(
            File("src/main/res/raw/tts_silence_anchor.wav"),
            File("app/src/main/res/raw/tts_silence_anchor.wav"),
        ).firstOrNull(File::isFile) ?: error("tts_silence_anchor.wav is missing from res/raw")

        val bytes = wav.readBytes()
        assertTrue("wav too small", bytes.size > 44)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        // 16-bit PCM mono: zero-filled samples so playback is silent while still
        // registering this app's uid as an active audio playback client.
        assertTrue(bytes.copyOfRange(44, bytes.size).all { it == 0.toByte() })
    }

    private fun parseManifest() = DocumentBuilderFactory.newInstance()
        .newDocumentBuilder()
        .parse(
            sequenceOf(
                File("src/main/AndroidManifest.xml"),
                File("app/src/main/AndroidManifest.xml"),
            ).firstOrNull(File::isFile) ?: error("Unable to locate AndroidManifest.xml")
        )

    private fun Element.elements(name: String): List<Element> {
        val nodes = getElementsByTagName(name)
        return buildList(nodes.length) {
            for (index in 0 until nodes.length) add(nodes.item(index) as Element)
        }
    }
}
