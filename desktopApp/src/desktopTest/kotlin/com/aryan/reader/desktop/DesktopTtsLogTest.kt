package com.aryan.reader.desktop

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopTtsLogTest {
    @Test
    fun `desktop tts preview redacts key and token query values`() {
        val preview = "wss://example.test/live?key=gemini_secret&token=firebase_secret"
            .desktopTtsPreview(300)

        assertFalse(preview.contains("gemini_secret"))
        assertFalse(preview.contains("firebase_secret"))
        assertTrue(preview.contains("key=<redacted>"))
        assertTrue(preview.contains("token=<redacted>"))
    }
}
