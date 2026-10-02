package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Android benchmark: `loadListenTtsMode` / `saveListenTtsMode` in `ListenTtsVoicePreferences.kt`.
 */
class ReaderTtsEngineOverrideTest {

    @Test
    fun absentKeyInheritsTheReaderDeviceMode() {
        assertEquals(
            ReaderTtsEngineOverride.DEVICE,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = false,
                storedOverride = null,
                readerCloudModeEnabled = false,
            ),
        )
    }

    @Test
    fun absentKeyInheritsTheReaderCloudMode() {
        assertEquals(
            ReaderTtsEngineOverride.CLOUD,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = false,
                storedOverride = null,
                readerCloudModeEnabled = true,
            ),
        )
    }

    @Test
    fun storedChoiceIsPinnedAgainstTheReader() {
        // The reader switching mode must not drag a pinned Listen along with it.
        assertEquals(
            ReaderTtsEngineOverride.DEVICE,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = ReaderTtsEngineOverride.DEVICE,
                readerCloudModeEnabled = true,
            ),
        )
        assertEquals(
            ReaderTtsEngineOverride.CLOUD,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = ReaderTtsEngineOverride.CLOUD,
                readerCloudModeEnabled = false,
            ),
        )
    }

    @Test
    fun unavailableCloudFallsBackToDevice() {
        // Android's resolveTtsModeForCurrentBuild downgrades a cloud mode the build cannot serve.
        assertEquals(
            ReaderTtsEngineOverride.DEVICE,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = ReaderTtsEngineOverride.CLOUD,
                readerCloudModeEnabled = true,
                cloudAvailable = false,
            ),
        )
    }

    @Test
    fun unreadableStoredValueFallsBackToDeviceNotTheReader() {
        // A stored value means the user chose; defaulting it to the reader's mode would silently
        // undo that choice.
        assertEquals(
            ReaderTtsEngineOverride.DEVICE,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = "garbage",
                readerCloudModeEnabled = true,
            ),
        )
        assertEquals(
            ReaderTtsEngineOverride.DEVICE,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = null,
                readerCloudModeEnabled = true,
            ),
        )
    }

    @Test
    fun encodingRoundTrips() {
        assertEquals(
            ReaderTtsEngineOverride.CLOUD,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = ReaderTtsEngineOverride.encodeEngineMode(cloudEnabled = true),
                readerCloudModeEnabled = false,
            ),
        )
        assertEquals(
            ReaderTtsEngineOverride.DEVICE,
            ReaderTtsEngineOverride.resolveEngineMode(
                isOverrideStored = true,
                storedOverride = ReaderTtsEngineOverride.encodeEngineMode(cloudEnabled = false),
                readerCloudModeEnabled = true,
            ),
        )
    }

    @Test
    fun hasOwnEngineModeTracksOnlyKeyPresence() {
        assertFalse(ReaderTtsEngineOverride.hasOwnEngineMode(isOverrideStored = false))
        assertTrue(ReaderTtsEngineOverride.hasOwnEngineMode(isOverrideStored = true))
    }
}