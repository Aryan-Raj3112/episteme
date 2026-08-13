package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedMobilePdfRendererTest {
    @Test
    fun `pdfium password error is distinguished from corrupt document failures`() {
        assertEquals(
            SharedMobilePdfOpenError.PASSWORD_REQUIRED,
            sharedMobilePdfOpenErrorForPdfiumCode(4L),
        )
        assertEquals(
            SharedMobilePdfOpenError.INVALID_DOCUMENT,
            sharedMobilePdfOpenErrorForPdfiumCode(3L),
        )
        assertEquals(
            SharedMobilePdfOpenError.INVALID_DOCUMENT,
            sharedMobilePdfOpenErrorForPdfiumCode(0L),
        )
    }
}
