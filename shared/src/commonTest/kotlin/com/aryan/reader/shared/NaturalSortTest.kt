package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NaturalSortTest {

    @Test
    fun `chapter titles sort numerically`() {
        val titles = listOf("Chapter 10", "Chapter 2", "Chapter 1")
        assertEquals(
            listOf("Chapter 1", "Chapter 2", "Chapter 10"),
            titles.sortedWith(::compareNatural),
        )
    }

    @Test
    fun `natural sort stays case insensitive`() {
        val titles = listOf("chapter 10", "Chapter 2", "CHAPTER 1")
        assertEquals(
            listOf("CHAPTER 1", "Chapter 2", "chapter 10"),
            titles.sortedWith(::compareNatural),
        )
    }

    @Test
    fun `numeric runs compare by value without overflow`() {
        assertTrue(compareNatural("Volume 2", "Volume 10") < 0)
        assertTrue(compareNatural("Volume 10", "Volume 2") > 0)
        // Longer than Long: length decides, no overflow.
        assertTrue(compareNatural("Book 99999999999999999999", "Book 100000000000000000000") < 0)
        assertEquals(0, compareNatural("Same 1", "Same 1"))
    }

    @Test
    fun `leading zeros sort deterministically`() {
        val ordered = listOf("Chapter 1", "Chapter 01", "Chapter 001")
            .sortedWith(::compareNatural)
        assertEquals(listOf("Chapter 1", "Chapter 01", "Chapter 001"), ordered)
    }

    @Test
    fun `library title sort is numeric`() {
        fun book(id: String, title: String) = BookItem(
            id = id,
            path = "/library/$id.epub",
            type = FileType.EPUB,
            displayName = "$id.epub",
            timestamp = 1L,
            title = title,
        )
        val books = listOf(
            book("c10", "Chapter 10"),
            book("c2", "Chapter 2"),
            book("c1", "Chapter 1"),
        )
        assertEquals(
            listOf("c1", "c2", "c10"),
            sortBooks(books, SortOrder.TITLE_ASC).map { it.id },
        )
    }

    @Test
    fun `audiobook title sort is numeric`() {
        fun audiobook(id: String, title: String) = SharedAudiobook(
            bookId = id,
            filePath = "/$id.m4b",
            format = "m4b",
            title = title,
            addedAt = 1L,
        )
        val books = listOf(
            audiobook("c10", "Chapter 10"),
            audiobook("c2", "Chapter 2"),
            audiobook("c1", "Chapter 1"),
        )
        assertEquals(
            listOf("c1", "c2", "c10"),
            sortSharedAudiobooks(books, SharedAudiobookSort.TITLE).map { it.bookId },
        )
    }
}
