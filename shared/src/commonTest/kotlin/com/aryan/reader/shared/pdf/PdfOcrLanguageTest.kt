package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfOcrLanguageTest {
    @Test
    fun idsRoundTripAndUnknownValuesUseLatin() {
        SharedPdfOcrLanguage.entries.forEach { language ->
            assertEquals(language, SharedPdfOcrLanguage.fromId(language.id))
            assertEquals(language, SharedPdfOcrLanguage.fromId(language.name))
            assertTrue(language.visionLanguageCodes.isNotEmpty())
        }
        assertEquals(SharedPdfOcrLanguage.LATIN, SharedPdfOcrLanguage.fromId("unknown"))
    }
}
