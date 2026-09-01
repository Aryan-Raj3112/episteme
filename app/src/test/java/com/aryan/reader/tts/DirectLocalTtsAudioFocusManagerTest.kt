package com.aryan.reader.tts

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectLocalTtsAudioFocusManagerTest {

    @Test
    fun `transient focus losses including duck map to a transient loss`() {
        assertEquals(
            DirectLocalTtsFocusChange.TRANSIENT_LOSS,
            resolveDirectLocalTtsFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        )
        assertEquals(
            DirectLocalTtsFocusChange.TRANSIENT_LOSS,
            resolveDirectLocalTtsFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
        )
    }

    @Test
    fun `focus gain maps to regained`() {
        assertEquals(
            DirectLocalTtsFocusChange.REGAINED,
            resolveDirectLocalTtsFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        )
    }

    @Test
    fun `permanent focus loss maps to permanent loss`() {
        assertEquals(
            DirectLocalTtsFocusChange.PERMANENT_LOSS,
            resolveDirectLocalTtsFocusChange(AudioManager.AUDIOFOCUS_LOSS)
        )
    }

    @Test
    fun `unknown focus changes are ignored`() {
        assertNull(resolveDirectLocalTtsFocusChange(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK))
        assertNull(resolveDirectLocalTtsFocusChange(-123))
    }
}
