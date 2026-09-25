package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Runs only on the JVM (android target) because it asserts the Android
 * benchmark truth: ML Kit v2 supports every script the shared picker offers.
 */
class SharedPdfOcrLanguageAndroidTest {

    @Test
    fun mlKitCoversEverySharedScript() {
        SharedPdfOcrLanguage.entries.forEach { language ->
            assertTrue(language.isSupportedOnPlatform, "${language.name} must be supported on Android")
        }
    }

    @Test
    fun pickerListsAllFiveScriptsInEnumOrder() {
        val controller = SharedPdfOcrLanguageController(
            loadStored = { SharedPdfOcrLanguage.LATIN },
            hasSelection = { true },
        )
        assertEquals(SharedPdfOcrLanguage.entries.toList(), controller.availableLanguages())
    }
}
