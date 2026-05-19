package com.aryan.reader.shared.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class SharedStringsTest {
    @Test
    fun formatsAndroidIndexedPlaceholders() {
        val formatted = formatAndroidString(
            "Remove \"%1\$s\" and its %2\$d book(s)?",
            listOf("Downloads", 3)
        )

        assertEquals("Remove \"Downloads\" and its 3 book(s)?", formatted)
    }

    @Test
    fun preservesEscapedPercentLiterals() {
        val formatted = formatAndroidString("Preparing %1\$d%%", listOf(42))

        assertEquals("Preparing 42%", formatted)
    }
}
