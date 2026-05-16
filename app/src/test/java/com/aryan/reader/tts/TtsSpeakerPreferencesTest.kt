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
}
