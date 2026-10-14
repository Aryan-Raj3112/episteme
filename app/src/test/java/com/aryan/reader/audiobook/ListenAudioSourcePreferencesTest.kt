package com.aryan.reader.audiobook

import com.aryan.reader.shared.SharedListenAudioSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Listen's explicit TTS/narration choice, persisted per book.
 *
 * The source must survive process death exactly as picked: a stored pick
 * pins the session, and anything unrecognized (a downgrade, a corrupt pref)
 * reads as "no pick" so resolution falls back to preferring narration when
 * present rather than pinning a phantom value.
 */
@RunWith(RobolectricTestRunner::class)
class ListenAudioSourcePreferencesTest {

    @Test
    fun `absent preference leaves the choice to overlay-first resolution`() {
        val context = RuntimeEnvironment.getApplication()
        assertNull(loadListenAudioSource(context, "book-a"))
    }

    @Test
    fun `saved source round-trips per book without leaking across books`() {
        val context = RuntimeEnvironment.getApplication()
        saveListenAudioSource(context, "book-a", SharedListenAudioSource.MEDIA_OVERLAY)
        saveListenAudioSource(context, "book-b", SharedListenAudioSource.TTS)

        assertEquals(SharedListenAudioSource.MEDIA_OVERLAY, loadListenAudioSource(context, "book-a"))
        assertEquals(SharedListenAudioSource.TTS, loadListenAudioSource(context, "book-b"))

        saveListenAudioSource(context, "book-a", SharedListenAudioSource.TTS)
        assertEquals(SharedListenAudioSource.TTS, loadListenAudioSource(context, "book-a"))
        assertEquals(SharedListenAudioSource.TTS, loadListenAudioSource(context, "book-b"))
    }

    @Test
    fun `overlay speed persists clamped to the engine range`() {
        val context = RuntimeEnvironment.getApplication()
        assertEquals(1f, loadListenOverlaySpeed(context))

        saveListenOverlaySpeed(context, 1.5f)
        assertEquals(1.5f, loadListenOverlaySpeed(context))

        saveListenOverlaySpeed(context, 9f)
        assertEquals(3.0f, loadListenOverlaySpeed(context))

        saveListenOverlaySpeed(context, -2f)
        assertEquals(0.5f, loadListenOverlaySpeed(context))
    }
}
