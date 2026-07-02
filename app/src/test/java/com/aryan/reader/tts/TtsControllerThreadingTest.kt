package com.aryan.reader.tts

import android.os.Looper
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TtsControllerThreadingTest {
    @Test
    fun `media controller is pinned to main application looper`() {
        assertSame(Looper.getMainLooper(), ttsControllerApplicationLooper())
    }
}