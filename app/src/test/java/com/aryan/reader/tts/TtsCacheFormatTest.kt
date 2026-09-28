package com.aryan.reader.tts

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class TtsCacheFormatTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun `legacy wav cache file name is unchanged`() {
        val manager = TtsCacheManager(context)
        val file = manager.getCacheFile(
            "Book", "Chapter", "hello", "Aoede", TtsPlaybackManager.TtsMode.CLOUD
        )

        assertTrue(file.name.endsWith(".wav"))
    }

    @Test
    fun `fish cache file uses mp3 with a distinct name`() {
        val manager = TtsCacheManager(context)
        val wav = manager.getCacheFile(
            "Book", "Chapter", "hello", "fish-voice-a", TtsPlaybackManager.TtsMode.CLOUD
        )
        val mp3 = manager.getCacheFile(
            "Book", "Chapter", "hello", "fish-voice-a", TtsPlaybackManager.TtsMode.CLOUD, "mp3"
        )

        assertTrue(mp3.name.endsWith(".mp3"))
        assertTrue(mp3.name.startsWith("cached_chunk_fish-voice-a_"))
        assertTrue(wav.name != mp3.name)
    }

    @Test
    fun `unknown cache extension falls back to wav`() {
        val manager = TtsCacheManager(context)
        val file = manager.getCacheFile(
            "Book", "Chapter", "hello", "Aoede", TtsPlaybackManager.TtsMode.CLOUD, "ogg"
        )

        assertEquals(
            manager.getCacheFile(
                "Book", "Chapter", "hello", "Aoede", TtsPlaybackManager.TtsMode.CLOUD
            ).name,
            file.name
        )
    }
}
