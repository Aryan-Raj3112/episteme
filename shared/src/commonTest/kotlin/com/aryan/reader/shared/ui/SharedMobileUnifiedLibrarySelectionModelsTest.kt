package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedMobileUnifiedLibrarySelectionModelsTest {
    @Test
    fun defaultCapabilitiesHideContextualActions() {
        val capabilities = SharedMobileUnifiedLibrarySelectionCapabilities()

        assertFalse(capabilities.selectionActions)
        assertEquals(emptySet(), capabilities.enabledActions)
    }

    @Test
    fun disabledSelectionActionsGateIndividualCapabilities() {
        val capabilities = SharedMobileUnifiedLibrarySelectionCapabilities(selectAll = true)

        assertEquals(emptySet(), capabilities.enabledActions)
    }

    @Test
    fun enabledCapabilitiesExposeTheAndroidContextualActionSet() {
        val capabilities = SharedMobileUnifiedLibrarySelectionCapabilities(
            selectionActions = true,
            selectAll = true,
            pin = true,
            addToShelf = true,
            tag = true,
            info = true,
            save = true,
            share = true,
            exportAnnotations = true,
            delete = true,
        )

        assertTrue(capabilities.selectionActions)
        assertEquals(
            SharedMobileUnifiedLibrarySelectionAction.entries.toSet(),
            capabilities.enabledActions,
        )
    }
}
