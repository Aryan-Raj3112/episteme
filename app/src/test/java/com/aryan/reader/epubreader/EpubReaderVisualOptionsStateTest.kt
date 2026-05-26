package com.aryan.reader.epubreader

import com.aryan.reader.shared.PageInfoMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubReaderVisualOptionsStateTest {

    @Test
    fun `page info always show remains visible independent of reader chrome`() {
        assertTrue(
            shouldShowEpubPageInfoBar(
                pageInfoMode = PageInfoMode.DEFAULT,
                showReaderChrome = false
            )
        )
        assertTrue(
            shouldShowEpubPageInfoBar(
                pageInfoMode = PageInfoMode.DEFAULT,
                showReaderChrome = true
            )
        )
    }

    @Test
    fun `page info sync follows reader chrome and hidden never shows`() {
        assertFalse(
            shouldShowEpubPageInfoBar(
                pageInfoMode = PageInfoMode.SYNC,
                showReaderChrome = false
            )
        )
        assertTrue(
            shouldShowEpubPageInfoBar(
                pageInfoMode = PageInfoMode.SYNC,
                showReaderChrome = true
            )
        )
        assertFalse(
            shouldShowEpubPageInfoBar(
                pageInfoMode = PageInfoMode.HIDDEN,
                showReaderChrome = true
            )
        )
    }
}
