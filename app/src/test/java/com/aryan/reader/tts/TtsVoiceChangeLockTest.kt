package com.aryan.reader.tts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsVoiceChangeLockTest {
    @Test
    fun `voice change unlocked when idle`() {
        assertFalse(isReaderTtsVoiceChangeLocked(null, false))
        assertFalse(isReaderTtsVoiceChangeLocked("", false))
    }

    @Test
    fun `voice change locked for live reader sessions`() {
        assertTrue(isReaderTtsVoiceChangeLocked("READER", false))
    }

    @Test
    fun `voice change locked for paused sessions`() {
        // Paused is not a separate flag here: the session keeps its source
        // with sessionFinished=false while old-voice audio stays queued.
        assertTrue(isReaderTtsVoiceChangeLocked("READER", false))
    }

    @Test
    fun `voice change unlocked after the session finishes`() {
        assertFalse(isReaderTtsVoiceChangeLocked("READER", true))
    }

    @Test
    fun `voice change unlocked for non-reader sources`() {
        assertFalse(isReaderTtsVoiceChangeLocked("AUDIOBOOK_TTS", false))
    }
}
