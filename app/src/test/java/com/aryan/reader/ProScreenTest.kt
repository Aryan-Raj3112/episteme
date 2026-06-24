package com.aryan.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class ProScreenTest {

    @Test
    fun `pro screen opens pro tab by default`() {
        assertEquals(0, initialProScreenTabIndex())
    }
}
