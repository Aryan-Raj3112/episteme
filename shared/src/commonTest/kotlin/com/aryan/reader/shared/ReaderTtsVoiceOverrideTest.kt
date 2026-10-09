package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderTtsVoiceOverrideTest {

    @Test
    fun inheritsReaderVoiceWhileNoOverrideIsStored() {
        assertEquals(
            "com.voice.reader",
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = false,
                storedOverride = null,
                inheritedVoiceIdentifier = "com.voice.reader"
            )
        )
    }

    @Test
    fun tracksTheReaderVoiceUntilAnOverrideIsWritten() {
        // First-write-wins: with no key stored the surface must follow the reader, including
        // after the reader's own voice changes.
        assertEquals(
            "com.voice.reader.v2",
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = false,
                storedOverride = null,
                inheritedVoiceIdentifier = "com.voice.reader.v2"
            )
        )
    }

    @Test
    fun blankStoredValueIsAnExplicitSystemDefaultChoice() {
        assertEquals(
            null,
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = true,
                storedOverride = ReaderTtsVoiceOverride.SYSTEM_DEFAULT,
                inheritedVoiceIdentifier = "com.voice.reader"
            )
        )
    }

    @Test
    fun nonBlankStoredValuePinsTheSurfaceToItsOwnVoice() {
        assertEquals(
            "com.voice.listen",
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = true,
                storedOverride = "com.voice.listen",
                inheritedVoiceIdentifier = "com.voice.reader"
            )
        )
    }

    @Test
    fun systemDefaultOverrideStillBeatsAnInheritedVoice() {
        // The whole point of the sentinel: an explicit "system default" must not silently
        // fall back to the reader's pick.
        assertEquals(
            null,
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = true,
                storedOverride = ReaderTtsVoiceOverride.encodeVoiceOverride(null),
                inheritedVoiceIdentifier = "com.voice.reader"
            )
        )
    }

    @Test
    fun encodeRoundTripsBothExplicitChoices() {
        val explicit = "com.voice.listen"
        assertEquals(explicit, ReaderTtsVoiceOverride.encodeVoiceOverride(explicit))
        assertEquals(
            explicit,
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = true,
                storedOverride = ReaderTtsVoiceOverride.encodeVoiceOverride(explicit),
                inheritedVoiceIdentifier = "com.voice.reader"
            )
        )

        val systemDefault = ReaderTtsVoiceOverride.encodeVoiceOverride(null)
        assertEquals(ReaderTtsVoiceOverride.SYSTEM_DEFAULT, systemDefault)
        assertEquals(
            null,
            ReaderTtsVoiceOverride.resolveVoiceIdentifier(
                isOverrideStored = true,
                storedOverride = systemDefault,
                inheritedVoiceIdentifier = "com.voice.reader"
            )
        )
    }

    @Test
    fun hasOwnVoiceOnlyWhenPinnedAwayFromTheReader() {
        assertFalse(ReaderTtsVoiceOverride.hasOwnVoice(isOverrideStored = false))
        assertTrue(ReaderTtsVoiceOverride.hasOwnVoice(isOverrideStored = true))
    }
}
