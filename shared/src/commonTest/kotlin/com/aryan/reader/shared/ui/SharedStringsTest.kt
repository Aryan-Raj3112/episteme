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

    @Test
    fun resolvesQuantityStringsBeforeFallingBack() {
        val resolver = SharedStringResolver(
            resolveQuantity = { name, quantity ->
                when {
                    name == "book_count" && quantity == 1 -> "%1\$d localized book"
                    name == "book_count" -> "%1\$d localized books"
                    else -> null
                }
            }
        )

        assertEquals(
            "2 localized books",
            resolver.quantityString("book_count", 2, "%1\$d book", "%1\$d books", 2)
        )
        assertEquals(
            "1 fallback book",
            resolver.quantityString("missing_count", 1, "%1\$d fallback book", "%1\$d fallback books", 1)
        )
    }
}
