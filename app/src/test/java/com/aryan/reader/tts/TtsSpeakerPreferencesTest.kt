package com.aryan.reader.tts

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TtsSpeakerPreferencesTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences(TTS_SETTINGS_PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `tts speaker preference saves and loads selected ai voice`() {
        saveTtsSpeaker(context, "Kore")

        assertEquals("Kore", loadTtsSpeaker(context))
    }

    @Test
    fun `tts speaker preference falls back to default for blank or unknown voices`() {
        assertEquals(DEFAULT_SPEAKER_ID, loadTtsSpeaker(context))

        saveTtsSpeaker(context, "")
        assertEquals(DEFAULT_SPEAKER_ID, loadTtsSpeaker(context))

        saveTtsSpeaker(context, "MissingVoice")
        assertEquals(DEFAULT_SPEAKER_ID, loadTtsSpeaker(context))
    }

    @Test
    fun `tts speaker preference keeps fish reference ids and catalog aliases`() {
        val dashedReferenceId = "93356312-9e56-4b19-a115-bed6d7406a12"
        saveTtsSpeaker(context, dashedReferenceId)
        assertEquals(dashedReferenceId, loadTtsSpeaker(context))

        // Fish voice library ids are dashless 32-char hex.
        val libraryReferenceId = "9a9cf47702da476aa4629e2506d4a857"
        saveTtsSpeaker(context, libraryReferenceId)
        assertEquals(libraryReferenceId, loadTtsSpeaker(context))

        saveTtsSpeaker(context, "fish-voice-a")
        assertEquals("fish-voice-a", loadTtsSpeaker(context))
    }

    @Test
    fun `tts speaker name preference round-trips and defaults to null`() {
        assertEquals(null, loadTtsSpeakerName(context))

        saveTtsSpeakerName(context, "Ava")
        assertEquals("Ava", loadTtsSpeakerName(context))

        saveTtsSpeakerName(context, "   ")
        assertEquals(null, loadTtsSpeakerName(context))
    }
}
