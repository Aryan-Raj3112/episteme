package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryTagPolicyTest {

    @Test
    fun `default tags match the Android first-run seed exactly`() {
        assertEquals(
            listOf(
                "default_to_read" to "To Read",
                "default_reading" to "Reading",
                "default_finished" to "Finished",
                "default_favorites" to "Favorites",
                "default_reference" to "Reference",
            ),
            LibraryTagPolicy.DEFAULT_TAGS.map { it.id to it.name },
        )
        assertEquals(
            listOf(0xFF64B5F6, 0xFF81C784, 0xFFFFB74D, 0xFFF06292, 0xFF9575CD).map(Long::toInt),
            LibraryTagPolicy.DEFAULT_TAGS.map { it.color },
        )
    }

    @Test
    fun `defaults are seeded only into an empty tag list`() {
        assertEquals(LibraryTagPolicy.DEFAULT_TAGS, LibraryTagPolicy.seedDefaultTagsIfEmpty(emptyList()))

        val custom = listOf(Tag(id = "custom", name = "Custom"))
        assertEquals(custom, LibraryTagPolicy.seedDefaultTagsIfEmpty(custom))
    }

    @Test
    fun `new tag ids are unique and never derived from the name`() {
        val ids = List(50) { LibraryTagPolicy.newTagId() }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.none { it.isBlank() })
        // The historical iOS bug: different names collapsing to one slug and then
        // disappearing behind `distinctBy(id)`.
        assertTrue(ids.none { it == "sci_fi" })
    }

    @Test
    fun `tag colours come from the Android palette`() {
        assertEquals(14, LibraryTagPolicy.TAG_COLORS.size)
        assertEquals(0xFFE57373.toInt(), LibraryTagPolicy.TAG_COLORS.first())
        assertEquals(0xFF90A4AE.toInt(), LibraryTagPolicy.TAG_COLORS.last())
        val picked = LibraryTagPolicy.selectTagColor(kotlin.random.Random(7))
        assertTrue(picked in LibraryTagPolicy.TAG_COLORS)
    }

    @Test
    fun `tag sorting is natural and case insensitive`() {
        val tags = listOf(
            Tag(id = "3", name = "tag 10"),
            Tag(id = "1", name = "Alpha"),
            Tag(id = "2", name = "beta"),
            Tag(id = "4", name = "Tag 2"),
        )

        assertEquals(
            listOf("Alpha", "beta", "Tag 2", "tag 10"),
            LibraryTagPolicy.sortTags(tags).map { it.name },
        )
    }

    @Test
    fun `book tags are reordered naturally`() {
        val zeta = Tag(id = "z", name = "Zeta")
        val alpha = Tag(id = "a", name = "Alpha")
        val book = BookItem(
            id = "book",
            path = "/book.epub",
            type = FileType.EPUB,
            displayName = "book.epub",
            timestamp = 1L,
            tags = listOf(zeta, alpha),
        )

        val sorted = book.withNaturallySortedTags()
        assertEquals(listOf(alpha, zeta), sorted.tags)
        // No needless copy when already sorted.
        assertTrue(sorted.withNaturallySortedTags() === sorted)
    }
}
