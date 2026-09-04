package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedLegacyCharsetFamiliesTest {

    @Test
    fun detectsIso2022EscapeSequences() {
        // ESC $ B designator of ISO-2022-JP.
        val payload = byteArrayOf(0x41, 0x1B, 0x24, 0x42, 0x46, 0x7C, 0x41)
        assertTrue(SharedLegacyCharsetFamilies.containsIso2022Escape(payload))
    }

    @Test
    fun plainAsciiHasNoIso2022Escapes() {
        val plain = "Plain ASCII and some text without escape bytes.".encodeToByteArray()
        assertFalse(SharedLegacyCharsetFamilies.containsIso2022Escape(plain))
    }

    @Test
    fun lateEscapeSequencesOutsideTheScanWindowAreIgnored() {
        val lateEscape = ByteArray(2048)
        lateEscape[1024] = 0x1B
        assertFalse(SharedLegacyCharsetFamilies.containsIso2022Escape(lateEscape))
    }

    @Test
    fun emptyPayloadHasNoIso2022Escapes() {
        assertFalse(SharedLegacyCharsetFamilies.containsIso2022Escape(ByteArray(0)))
    }
}
