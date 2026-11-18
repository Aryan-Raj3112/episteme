package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iOS WKWebView document has no file-read access, so reader faces must be
 * inlined. These cover the platform lookup the common CSS depends on.
 */
class ReaderWebFontSourceIosTest {

    @Test
    fun `bundled reader font is inlined as a data uri`() {
        val source = readerWebFontFaceSource(fontFamily = "Lora", customFontPath = null)

        assertNotNull(source)
        assertTrue(source.startsWith("url('data:font/ttf;base64,"), source)
    }

    @Test
    fun `a missing imported font has no source`() {
        val source = readerWebFontFaceSource(
            fontFamily = "Imported",
            customFontPath = "/tmp/reader-missing-font-that-does-not-exist.ttf",
        )

        assertNull(source)
    }
}
