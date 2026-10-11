package com.aryan.reader.shared.ui

import com.aryan.reader.shared.ReaderCloudTtsState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the rule that gates voice selection in the shared TTS settings sheet.
 *
 * The sheet now takes the lock from its host (`voiceChangeLocked`) so Android's audiobook Listen can
 * keep its looser rule — only actively speaking counts — instead of the reader's "any live session
 * freezes settings". A cloud session that is loading or playing locks regardless of which rule the
 * host supplied, so mid-utterance voice changes stay impossible on every surface.
 */
class SharedTtsSettingsVoiceLockTest {

    @Test
    fun `the host rule alone locks the panels`() {
        assertTrue(sharedTtsSettingsVoiceLocked(hostVoiceChangeLocked = true, cloudState = null))
    }

    @Test
    fun `a host that allows changes keeps them allowed while nothing is speaking`() {
        assertFalse(
            sharedTtsSettingsVoiceLocked(
                hostVoiceChangeLocked = false,
                cloudState = ReaderCloudTtsState(),
            )
        )
    }

    @Test
    fun `a loading cloud session locks on top of a permissive host`() {
        assertTrue(
            sharedTtsSettingsVoiceLocked(
                hostVoiceChangeLocked = false,
                cloudState = ReaderCloudTtsState(isLoading = true),
            )
        )
    }

    @Test
    fun `a playing cloud session locks on top of a permissive host`() {
        assertTrue(
            sharedTtsSettingsVoiceLocked(
                hostVoiceChangeLocked = false,
                cloudState = ReaderCloudTtsState(isPlaying = true),
            )
        )
    }
}
