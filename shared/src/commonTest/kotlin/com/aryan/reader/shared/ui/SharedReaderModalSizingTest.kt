package com.aryan.reader.shared.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedReaderModalSizingTest {

    @Test
    fun `reader popup width is capped on wide surfaces`() {
        assertEquals(SharedReaderPopupDefaultMaxWidth, sharedReaderPopupWidth(1200.dp))
    }

    @Test
    fun `reader popup width stays usable on narrow surfaces`() {
        assertEquals(320.dp, sharedReaderPopupWidth(500.dp))
        assertEquals(280.dp, sharedReaderPopupWidth(280.dp))
    }
}
