package com.aryan.reader.desktop

import com.aryan.reader.shared.CloudBookTombstone
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopCloudBookDeleteOutboxTest {

    private val accountA = "account-a"
    private val accountB = "account-b"

    @Test
    fun `codec round trips tombstones preserving type and order`() {
        val tombstones = listOf(
            CloudBookTombstone(bookId = "book-b", type = "EPUB", deletedAt = 20L),
            CloudBookTombstone(bookId = "book-a", type = null, deletedAt = 10L)
        )
        val decoded = DesktopCloudBookDeleteOutboxCodec.decode(
            DesktopCloudBookDeleteOutboxCodec.encode(tombstones)
        )
        assertEquals(tombstones, decoded)
    }

    @Test
    fun `codec drops blank book ids and tolerates corrupted payloads`() {
        assertEquals(
            emptyList(),
            DesktopCloudBookDeleteOutboxCodec.decode("not json at all")
        )
        assertEquals(
            emptyList(),
            DesktopCloudBookDeleteOutboxCodec.decode(
                DesktopCloudBookDeleteOutboxCodec.encode(
                    listOf(CloudBookTombstone(bookId = "  ", deletedAt = 1L))
                )
            )
        )
    }

    @Test
    fun `merge deduplicates by book id keeping newest deletion`() {
        val merged = DesktopCloudBookDeleteOutboxCodec.merge(
            listOf(
                CloudBookTombstone(bookId = "b2", type = "PDF", deletedAt = 5L),
                CloudBookTombstone(bookId = "b1", deletedAt = 9L),
                CloudBookTombstone(bookId = "b2", deletedAt = 7L),
                CloudBookTombstone(bookId = "b1", deletedAt = 4L)
            )
        )
        assertEquals(
            listOf(
                CloudBookTombstone(bookId = "b1", type = null, deletedAt = 9L),
                CloudBookTombstone(bookId = "b2", type = "PDF", deletedAt = 7L)
            ),
            merged
        )
    }

    @Test
    fun `enqueue persists tombstones across outbox instances`() {
        val storeFile = Files.createTempDirectory("episteme-delete-outbox").resolve("outbox.json").toFile()
        try {
            val first = DesktopCloudBookDeleteOutbox(storeFile)
            first.enqueue(accountA, listOf(CloudBookTombstone(bookId = "book-1", type = "EPUB", deletedAt = 100L)))

            val reloaded = DesktopCloudBookDeleteOutbox(storeFile)
            reloaded.enqueue(accountA, listOf(CloudBookTombstone(bookId = "book-1", type = "EPUB", deletedAt = 50L)))
            reloaded.enqueue(accountA, listOf(CloudBookTombstone(bookId = "book-2", deletedAt = 200L)))

            assertEquals(
                listOf(
                    CloudBookTombstone(bookId = "book-1", type = "EPUB", deletedAt = 100L),
                    CloudBookTombstone(bookId = "book-2", type = null, deletedAt = 200L)
                ),
                DesktopCloudBookDeleteOutbox(storeFile).pending(accountA)
            )
        } finally {
            storeFile.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `remove deletes succeeded entries and clears storage when empty`() {
        val storeFile = Files.createTempDirectory("episteme-delete-outbox").resolve("outbox.json").toFile()
        try {
            val outbox = DesktopCloudBookDeleteOutbox(storeFile)
            outbox.enqueue(
                accountA,
                listOf(
                    CloudBookTombstone(bookId = "book-1", deletedAt = 1L),
                    CloudBookTombstone(bookId = "book-2", deletedAt = 2L)
                )
            )

            outbox.remove(accountA, listOf("book-1"))
            assertEquals(
                listOf(CloudBookTombstone(bookId = "book-2", type = null, deletedAt = 2L)),
                outbox.pending(accountA)
            )

            outbox.remove(accountA, listOf("book-2"))
            assertTrue(outbox.pending(accountA).isEmpty())
            assertFalse(storeFile.exists())
        } finally {
            storeFile.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `account queues are isolated in one durable store`() {
        val storeFile = Files.createTempDirectory("episteme-delete-outbox").resolve("outbox.json").toFile()
        try {
            val outbox = DesktopCloudBookDeleteOutbox(storeFile)
            assertTrue(outbox.enqueue(accountA, listOf(CloudBookTombstone("book-a", "EPUB", 1L))))
            assertTrue(outbox.enqueue(accountB, listOf(CloudBookTombstone("book-b", "PDF", 2L))))

            assertEquals(
                listOf(CloudBookTombstone("book-a", "EPUB", 1L)),
                outbox.pending(accountA),
            )
            assertEquals(
                listOf(CloudBookTombstone("book-b", "PDF", 2L)),
                outbox.pending(accountB),
            )

            assertTrue(outbox.remove(accountA, listOf("book-a")))
            assertTrue(outbox.pending(accountA).isEmpty())
            assertEquals(
                listOf(CloudBookTombstone("book-b", "PDF", 2L)),
                outbox.pending(accountB),
            )
        } finally {
            storeFile.parentFile?.deleteRecursively()
        }
    }

    @Test
    fun `legacy unscoped array is never exposed to an account`() {
        val storeFile = Files.createTempDirectory("episteme-delete-outbox").resolve("outbox.json").toFile()
        try {
            storeFile.writeText(
                DesktopCloudBookDeleteOutboxCodec.encode(
                    listOf(CloudBookTombstone("legacy-book", "EPUB", 1L)),
                ),
            )
            val outbox = DesktopCloudBookDeleteOutbox(storeFile)

            assertTrue(outbox.pending(accountA).isEmpty())
            assertTrue(outbox.enqueue(accountA, listOf(CloudBookTombstone("new-book", "PDF", 2L))))
            assertTrue(outbox.pending(accountA).single().bookId == "new-book")
            assertTrue(storeFile.exists())
        } finally {
            storeFile.parentFile?.deleteRecursively()
        }
    }
}
