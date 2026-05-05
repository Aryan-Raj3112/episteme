package com.aryan.reader.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmartCollectionEngineTest {

    @Test
    fun `definition JSON round trips and ignores unknown fields`() {
        val definition = SmartCollectionDefinition(
            matchAll = false,
            rules = listOf(
                SmartRule(SmartField.TITLE, SmartOperator.CONTAINS, "dune"),
                SmartRule(SmartField.PROGRESS, SmartOperator.GREATER_THAN, "50")
            )
        )

        val encoded = SmartCollectionEngine.toJson(definition)
        val decoded = SmartCollectionEngine.fromJson(
            encoded.replaceFirst("{", """{"unknown":"kept-for-forward-compat",""")
        )

        assertEquals(definition, decoded)
    }

    @Test
    fun `fromJson returns null for blank malformed and incompatible payloads`() {
        assertNull(SmartCollectionEngine.fromJson(null))
        assertNull(SmartCollectionEngine.fromJson("   "))
        assertNull(SmartCollectionEngine.fromJson("{not json"))
        assertNull(SmartCollectionEngine.fromJson("""{"matchAll":true,"rules":[{"field":"NOPE"}]}"""))
    }

    @Test
    fun `matchAll requires every rule while matchAny accepts a single matching rule`() {
        val book = book(
            title = "Dune Messiah",
            author = "Frank Herbert",
            progressPercentage = 41f,
            type = FileType.EPUB
        )

        val titleAndHighProgress = SmartCollectionDefinition(
            matchAll = true,
            rules = listOf(
                SmartRule(SmartField.TITLE, SmartOperator.CONTAINS, "dune"),
                SmartRule(SmartField.PROGRESS, SmartOperator.GREATER_THAN, "80")
            )
        )
        val titleOrHighProgress = titleAndHighProgress.copy(matchAll = false)

        assertFalse(SmartCollectionEngine.evaluate(book, titleAndHighProgress))
        assertTrue(SmartCollectionEngine.evaluate(book, titleOrHighProgress))
    }

    @Test
    fun `string folder file type and tag rules are case insensitive`() {
        val book = book(
            displayName = "fallback-name.pdf",
            title = null,
            author = "Ursula K. Le Guin",
            sourceFolder = "content://library/Sci-Fi",
            type = FileType.PDF,
            tags = listOf(
                Tag(id = "t1", name = "Classic Science Fiction"),
                Tag(id = "t2", name = "Queued")
            )
        )

        assertTrue(
            SmartCollectionEngine.evaluate(
                book,
                SmartCollectionDefinition(
                    rules = listOf(
                        SmartRule(SmartField.TITLE, SmartOperator.EQUALS, "fallback-name.pdf"),
                        SmartRule(SmartField.AUTHOR, SmartOperator.CONTAINS, "le guin"),
                        SmartRule(SmartField.FOLDER, SmartOperator.CONTAINS, "SCI-FI"),
                        SmartRule(SmartField.FILE_TYPE, SmartOperator.EQUALS, "pdf"),
                        SmartRule(SmartField.TAG, SmartOperator.CONTAINS, "science")
                    )
                )
            )
        )
    }

    @Test
    fun `numeric rules handle equals greater less missing progress and invalid values`() {
        val startedBook = book(progressPercentage = 33.5f)
        val missingProgressBook = book(progressPercentage = null)

        assertTrue(matchesProgress(startedBook, SmartOperator.EQUALS, "33.5"))
        assertTrue(matchesProgress(startedBook, SmartOperator.GREATER_THAN, "33"))
        assertTrue(matchesProgress(startedBook, SmartOperator.LESS_THAN, "34"))
        assertFalse(matchesProgress(startedBook, SmartOperator.GREATER_THAN, "not-a-number"))
        assertTrue(matchesProgress(missingProgressBook, SmartOperator.EQUALS, "0"))
    }

    @Test
    fun `empty definitions never match`() {
        assertFalse(SmartCollectionEngine.evaluate(book(), SmartCollectionDefinition()))
    }

    private fun matchesProgress(
        book: BookItem,
        operator: SmartOperator,
        value: String
    ): Boolean {
        return SmartCollectionEngine.evaluate(
            book,
            SmartCollectionDefinition(
                rules = listOf(SmartRule(SmartField.PROGRESS, operator, value))
            )
        )
    }

    private fun book(
        id: String = "book-id",
        displayName: String = "display.epub",
        title: String? = "Display",
        author: String? = null,
        progressPercentage: Float? = null,
        sourceFolder: String? = null,
        type: FileType = FileType.EPUB,
        tags: List<Tag> = emptyList()
    ): BookItem {
        return BookItem(
            id = id,
            path = "/library/$displayName",
            type = type,
            displayName = displayName,
            timestamp = 1L,
            title = title,
            author = author,
            progressPercentage = progressPercentage,
            sourceFolder = sourceFolder,
            tags = tags
        )
    }
}
