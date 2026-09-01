package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderHighlightListModelsTest {
    @Test
    fun `highlight row actions preserve Android order when palette manager is available`() {
        assertEquals(
            listOf(
                ReaderHighlightListAction.CHANGE_COLOR,
                ReaderHighlightListAction.MANAGE_PALETTE,
                ReaderHighlightListAction.EDIT_NOTE,
                ReaderHighlightListAction.DELETE,
            ),
            readerHighlightListActions(hasPaletteManager = true),
        )
    }

    @Test
    fun `highlight row keeps direct actions when palette manager is unavailable`() {
        assertEquals(
            listOf(
                ReaderHighlightListAction.CHANGE_COLOR,
                ReaderHighlightListAction.EDIT_NOTE,
                ReaderHighlightListAction.DELETE,
            ),
            readerHighlightListActions(hasPaletteManager = false),
        )
    }
}
