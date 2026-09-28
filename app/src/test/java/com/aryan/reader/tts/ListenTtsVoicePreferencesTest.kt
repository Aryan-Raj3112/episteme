package com.aryan.reader.tts

import android.content.Context
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
class ListenTtsVoicePreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        context.getSharedPreferences(TTS_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `listen voice inherits reader until first explicit choice`() {
        saveTtsSpeaker(context, "Kore")

        assertEquals(TtsPlaybackManager.TtsMode.BASE, loadListenTtsMode(context))
        assertEquals("Kore", loadListenTtsSpeaker(context))
        assertNull(loadListenNativeVoice(context))

        saveListenTtsMode(context, TtsPlaybackManager.TtsMode.CLOUD)
        saveListenTtsSpeaker(context, "Puck")
        saveListenNativeVoice(context, "en-voice")

        // Reader values change afterwards; Listen stays pinned.
        saveTtsSpeaker(context, "Charon")

        assertEquals("Puck", loadListenTtsSpeaker(context))
        assertEquals("en-voice", loadListenNativeVoice(context))
    }

    @Test
    fun `listen mode stays pinned once saved`() {
        saveListenTtsMode(context, TtsPlaybackManager.TtsMode.BASE)

        assertEquals(TtsPlaybackManager.TtsMode.BASE, loadListenTtsMode(context))
    }

    @Test
    fun `listen speaker normalizes like reader voices`() {
        saveListenTtsSpeaker(context, "")

        assertEquals(DEFAULT_SPEAKER_ID, loadListenTtsSpeaker(context))

        val fishRef = "93356312-9e56-4b19-a115-bed6d7406a12"
        saveListenTtsSpeaker(context, fishRef)

        assertEquals(fishRef, loadListenTtsSpeaker(context))
    }

    @Test
    fun `listen speaker name inherits reader then pins`() {
        saveTtsSpeakerName(context, "Ava")

        assertEquals("Ava", loadListenTtsSpeakerName(context))

        saveListenTtsSpeakerName(context, "Vega")

        saveTtsSpeakerName(context, "Altair")

        assertEquals("Vega", loadListenTtsSpeakerName(context))
    }

    @Test
    fun `listen native voice distinguishes system default from inherit`() {
        // Absent key inherits the Reader device voice.
        com.aryan.reader.saveNativeVoice(context, "reader-voice")

        assertEquals("reader-voice", loadListenNativeVoice(context))

        // Explicit system default sticks even though Reader has a voice.
        saveListenNativeVoice(context, null)

        assertNull(loadListenNativeVoice(context))

        saveListenNativeVoice(context, "listen-voice")

        assertEquals("listen-voice", loadListenNativeVoice(context))
    }
}
