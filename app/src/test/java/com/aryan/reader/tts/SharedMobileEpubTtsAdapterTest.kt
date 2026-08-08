package com.aryan.reader.tts

import com.aryan.reader.shared.ui.SharedMobileEpubLocalTtsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedMobileEpubTtsAdapterTest {
    @Test
    fun `service snapshots preserve speaking paused and idle states`() {
        assertEquals(
            SharedMobileEpubLocalTtsState.SPEAKING,
            sharedMobileTtsUiState(isPlaying = false, isLoading = true, hasSession = true),
        )
        assertEquals(
            SharedMobileEpubLocalTtsState.PAUSED,
            sharedMobileTtsUiState(isPlaying = false, isLoading = false, hasSession = true),
        )
        assertEquals(
            SharedMobileEpubLocalTtsState.IDLE,
            sharedMobileTtsUiState(isPlaying = false, isLoading = false, hasSession = false),
        )
    }

    @Test
    fun `completed and explicitly stopped timelines are not active sessions`() {
        assertTrue(sharedMobileTtsHasActiveSession(false, false, 3, false, false))
        assertFalse(sharedMobileTtsHasActiveSession(false, false, 3, true, false))
        assertFalse(sharedMobileTtsHasActiveSession(false, false, 3, false, true))
    }
}
