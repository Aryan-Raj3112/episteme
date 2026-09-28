package com.aryan.reader.shared.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PdfOcrGateTest {

    private class FakeGate(
        var stored: SharedPdfOcrLanguage = SharedPdfOcrLanguage.LATIN,
        var selected: Boolean = false,
    ) {
        fun load(): SharedPdfOcrLanguage = stored
        fun hasSelected(): Boolean = selected
        fun persist(language: SharedPdfOcrLanguage) {
            stored = language
            selected = true
        }
        fun markSelected() {
            selected = true
        }
    }

    private fun controllerWith(gate: FakeGate): SharedPdfOcrLanguageController =
        SharedPdfOcrLanguageController(
            loadStored = { gate.load() },
            hasSelection = { gate.hasSelected() },
            persist = { gate.persist(it) },
            markSelected = { gate.markSelected() },
        )

    @Test
    fun selectionPersistsLanguageAndSelectionFlag() {
        val gate = FakeGate()
        val controller = controllerWith(gate)

        controller.onLanguageSelected(SharedPdfOcrLanguage.JAPANESE)

        assertEquals(SharedPdfOcrLanguage.JAPANESE, gate.load())
        assertTrue(gate.hasSelected())
        assertEquals(SharedPdfOcrLanguage.JAPANESE, controller.loadInitialLanguage())
    }

    @Test
    fun unsupportedStoredLanguageFallsBackToLatin() {
        // Simulates a stored choice the platform engine cannot recognize.
        val gate = FakeGate(stored = SharedPdfOcrLanguage.DEVANAGARI)
        val controller = controllerWith(gate)
        val initial = controller.loadInitialLanguage()

        if (SharedPdfOcrLanguage.DEVANAGARI.isSupportedOnPlatform) {
            assertEquals(SharedPdfOcrLanguage.DEVANAGARI, initial)
        } else {
            assertEquals(SharedPdfOcrLanguage.LATIN, initial)
        }
    }

    @Test
    fun availableLanguagesNeverEmpty() {
        val controller = controllerWith(FakeGate())

        assertTrue(controller.availableLanguages().isNotEmpty())
        assertTrue(controller.availableLanguages().contains(SharedPdfOcrLanguage.LATIN))
    }
}
