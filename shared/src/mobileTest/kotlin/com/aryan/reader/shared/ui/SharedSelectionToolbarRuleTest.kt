package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SharedSelectionToolbarRuleTest {
    @Test
    fun `the toolbar only floats while something is selected`() {
        assertFalse(sharedSelectionToolbarVisible(selectedCount = 0))
        assertTrue(sharedSelectionToolbarVisible(selectedCount = 1))
        assertTrue(sharedSelectionToolbarVisible(selectedCount = 12))
    }
}
