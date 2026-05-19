package com.aryan.reader.shared.ui

import com.aryan.reader.shared.SharedText
import kotlin.test.Test
import kotlin.test.assertEquals

class SharedStringsTest {
    @Test
    fun formatsAndroidIndexedPlaceholders() {
        val formatted = formatAndroidString(
            "Remove \"%1\$s\" and its %2\$d books?",
            listOf("Downloads", 3)
        )

        assertEquals("Remove \"Downloads\" and its 3 books?", formatted)
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

    @Test
    fun resolvesSharedTextResources() {
        val resolver = SharedStringResolver(
            resolve = { name -> if (name == "banner_shelf_created") "Localized shelf %1\$s" else null },
            resolveQuantity = { name, quantity ->
                if (name == "banner_books_added_to_shelf" && quantity > 1) "%1\$d localized books" else null
            }
        )

        assertEquals(
            "Localized shelf Favorites",
            resolver.sharedText(SharedText.string("banner_shelf_created", "Created shelf %1\$s", "Favorites"))
        )
        assertEquals(
            "2 localized books",
            resolver.sharedText(
                SharedText.quantity(
                    "banner_books_added_to_shelf",
                    2,
                    "%1\$d fallback book",
                    "%1\$d fallback books",
                    2
                )
            )
        )
    }
}
