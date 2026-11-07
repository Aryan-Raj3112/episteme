package com.aryan.reader.tts

import android.os.Looper
import android.speech.tts.TextToSpeech
import com.aryan.reader.shared.tts.createTextToSpeechOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * `TextToSpeech(context, listener)` binds to the configured engine inside its constructor, so a
 * default engine this app cannot bind - not visible to us (package visibility), or no longer
 * installed for the current user - throws `SecurityException` out of the constructor, before any
 * init callback can run. That took down a playback session
 * (docs/crashlytics-triage.md#117). These pin the guard that routes it through the ordinary
 * "init failed" path instead.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SafeTextToSpeechTest {

    @Test
    fun `a construction failure becomes a null engine and an ERROR init callback`() {
        var status = Int.MIN_VALUE

        val engine = createTextToSpeechOrNull({ status = it }) {
            throw SecurityException(
                "Not allowed to bind to service Intent { act=android.intent.action.TTS_SERVICE pkg=com.example.engine }"
            )
        }

        assertNull("a throwing engine must not be handed to the caller", engine)
        assertNotEquals(
            "the init callback must not run before the caller stores the returned engine",
            TextToSpeech.ERROR,
            status,
        )

        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(TextToSpeech.ERROR, status)
    }

    @Test
    fun `any runtime construction failure is converted, not just SecurityException`() {
        var status = Int.MIN_VALUE

        val engine = createTextToSpeechOrNull({ status = it }) {
            throw IllegalArgumentException("engine package rejected")
        }

        assertNull(engine)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(TextToSpeech.ERROR, status)
    }

    @Test
    fun `a successfully constructed engine is returned untouched and reports nothing`() {
        var status = Int.MIN_VALUE
        val created = TextToSpeech(RuntimeEnvironment.getApplication(), null)

        val engine = createTextToSpeechOrNull({ status = it }) { created }

        assertSame(created, engine)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("no failure must be reported when construction succeeded", Int.MIN_VALUE, status)
    }
}
