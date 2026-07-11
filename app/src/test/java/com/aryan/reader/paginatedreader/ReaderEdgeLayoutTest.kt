package com.aryan.reader.paginatedreader

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderEdgeLayoutTest {

    @Test
    fun `horizontal overflow detects text extending past either page edge`() {
        assertEquals(18, readerHorizontalOverflowPx(-18, 680, 698, 700))
        assertEquals(24, readerHorizontalOverflowPx(0, 724, 724, 700))
        assertEquals(0, readerHorizontalOverflowPx(0, 700, 700, 700))
    }
}
