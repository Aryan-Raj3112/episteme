package com.aryan.reader.shared.ios

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.FileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the "cover only appears after restart" report.
 *
 * The reported session ran the whole pipeline correctly for the affected book —
 * extraction, file write, decode and draw all succeeded — yet the book never got
 * re-extracted after it came back into the library. The reason was the
 * already-extracted bookkeeping being keyed on the bare book id, which is a
 * content hash: a re-imported or snapshot-restored book reuses that id.
 */
class IosPresentationExtractionQueueTest {

    @Test
    fun `book counts as pending when it has no cover`() {
        assertTrue(needsIosPresentationExtraction(book(coverImagePath = null)))
    }

    @Test
    fun `book with a cover and a real title is no longer pending`() {
        // This is what lets the pending set actually drain. While a seriesless
        // book counted as pending forever, the effect re-keyed on unrelated
        // library changes and remainingPending never fell to zero.
        val extracted = book(
            coverImagePath = "/covers/a.png",
            title = "A Real Title",
            seriesName = null,
        )
        assertFalse(needsIosPresentationExtraction(extracted))
    }

    @Test
    fun `title equal to the filename still counts as needing extraction`() {
        val pending = book(
            coverImagePath = "/covers/a.png",
            title = "Weir_Egg",
            seriesName = null,
            displayName = "Weir_Egg.pdf",
        )
        assertTrue(needsIosPresentationExtraction(pending))
    }

    @Test
    fun `book without a path or of an unsupported type is not pending`() {
        assertFalse(needsIosPresentationExtraction(book(path = null, coverImagePath = null)))
        assertFalse(
            needsIosPresentationExtraction(
                book(coverImagePath = null, type = FileType.TXT),
            ),
        )
    }

    @Test
    fun `reimported book under the same id is treated as new work`() {
        // Ids are content hashes, so re-importing the same file reuses the id
        // while the path (and the cover reference) differ.
        val before = book(id = "abc", path = "/imports/old.epub", coverImagePath = null)
        val after = book(id = "abc", path = "/imports/new.epub", coverImagePath = null)

        val attempted = mutableSetOf(PresentationAttemptKey.of(before))

        assertTrue(
            PresentationAttemptKey.of(after) !in attempted,
            "reimported book must not be suppressed by the previous attempt",
        )
    }

    @Test
    fun `the old id-only bookkeeping suppressed a reimported book`() {
        // Guards the regression itself: the previous implementation stored bare
        // ids, so this returning book was filtered out and never re-extracted.
        val removed = book(id = "abc", path = "/imports/a.epub", coverImagePath = null)
        val attemptedByIdOnly = mutableSetOf(removed.id)

        val returned = book(id = "abc", path = "/imports/a.epub", coverImagePath = null)

        assertTrue(
            returned.id in attemptedByIdOnly,
            "sanity: the old key does suppress the returning book",
        )
        val currentKeyAttempted = attemptedByIdOnly
            .mapTo(mutableSetOf()) { id -> PresentationAttemptKey(id, null) }
        assertTrue(
            PresentationAttemptKey.of(returned) !in currentKeyAttempted,
            "the current key must not suppress it",
        )
    }

    @Test
    fun `the same book at the same path stays suppressed within a session`() {
        val book = book(id = "abc", path = "/imports/a.epub", coverImagePath = null)
        val attempted = mutableSetOf(PresentationAttemptKey.of(book))

        assertTrue(PresentationAttemptKey.of(book) in attempted)
    }

    @Test
    fun `pruning drops keys for books that left the library`() {
        // The reappearing-book case: the row is gone when the effect next runs, so
        // its stale key has to be evicted or it suppresses extraction forever.
        val live = listOf(book(id = "keep", path = "/imports/keep.epub"))
        val attempted = mutableSetOf(
            PresentationAttemptKey.of(book(id = "keep", path = "/imports/keep.epub")),
            PresentationAttemptKey.of(book(id = "gone", path = "/imports/gone.epub")),
        )

        attempted.retainAll(live.mapTo(mutableSetOf(), PresentationAttemptKey::of))

        assertEquals(1, attempted.size)
        assertTrue(PresentationAttemptKey.of(live.first()) in attempted)
        assertTrue(attempted.none { it.id == "gone" })
    }

    @Test
    fun `a book that returns after being pruned is picked up again`() {
        val removed = book(id = "abc", path = "/imports/a.epub", coverImagePath = null)
        val attempted = mutableSetOf(PresentationAttemptKey.of(removed))

        // Row leaves the library (library empty at effect time).
        attempted.retainAll(emptyList<BookItem>().mapTo(mutableSetOf(), PresentationAttemptKey::of))
        assertTrue(attempted.isEmpty())

        // Same id returns, now without a cover: it must be extractable again.
        val returned = book(id = "abc", path = "/imports/a.epub", coverImagePath = null)
        assertTrue(PresentationAttemptKey.of(returned) !in attempted)
        assertTrue(needsIosPresentationExtraction(returned))
    }

    private fun book(
        id: String = "book-1",
        path: String? = "/imports/a.epub",
        type: FileType = FileType.EPUB,
        displayName: String = "A Book.epub",
        coverImagePath: String? = null,
        title: String? = "A Title",
        seriesName: String? = null,
    ) = BookItem(
        id = id,
        path = path,
        type = type,
        displayName = displayName,
        timestamp = 0L,
        coverImagePath = coverImagePath,
        title = title,
        seriesName = seriesName,
    )
}