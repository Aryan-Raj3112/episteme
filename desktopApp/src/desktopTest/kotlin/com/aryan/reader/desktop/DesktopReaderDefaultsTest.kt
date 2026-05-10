package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopReaderDefaultsTest {

    @Test
    fun `desktop uses global reader defaults when book has no local settings`() {
        val defaults = ReaderSettings(fontSize = 23, readingMode = ReaderReadingMode.VERTICAL)
        val book = bookItem("without-local")

        assertEquals(defaults, resolvedDesktopReaderSettings(book, defaults))
    }

    @Test
    fun `desktop keeps local book reader settings ahead of global defaults`() {
        val defaults = ReaderSettings(fontSize = 23, readingMode = ReaderReadingMode.VERTICAL)
        val local = ReaderSettings(fontSize = 17, readingMode = ReaderReadingMode.PAGINATED, themeId = "sepia")
        val book = bookItem("with-local").copy(readerSettings = local)

        assertEquals(local, resolvedDesktopReaderSettings(book, defaults))
    }

    private fun bookItem(id: String): BookItem {
        return BookItem(
            id = id,
            path = "C:/Books/$id.epub",
            type = FileType.EPUB,
            displayName = "$id.epub",
            timestamp = 1L
        )
    }
}
