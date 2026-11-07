package com.aryan.reader.shared.reader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Which clip a play press starts at when a book has both a narration position and a reading position.
 *
 * The two disagree by design — narration moves a sentence at a time while the saved text position moves
 * a page at a time, and a background session moves the narration alone — so the choice is the whole
 * feature. Every case here is one where picking the wrong side is audible.
 */
class SharedMediaOverlayNarrationProgressTest {

    @Test
    fun `with no stored position the reader's clip is used`() {
        assertEquals(
            4,
            sharedMediaOverlayStartClipIndex(
                saved = null,
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 4,
                resumeSavedNarration = true
            )
        )
    }

    /** A chapter with no resolvable reader clip still starts at the top, as it always has. */
    @Test
    fun `nothing stored and nothing resolved starts at the top`() {
        assertNull(
            sharedMediaOverlayStartClipIndex(
                saved = null,
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = null,
                resumeSavedNarration = true
            )
        )
    }

    /**
     * The reported bug: the reading position restored to the top of a long section, so the clip it
     * resolves is the first, and replaying from there is exactly what a listener calls "it did not
     * resume". The stored narration position has to win.
     */
    @Test
    fun `a page-level reading position does not rewind stored narration`() {
        assertEquals(
            212,
            sharedMediaOverlayStartClipIndex(
                saved = progress(clipIndex = 212),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 0,
                resumeSavedNarration = true
            )
        )
    }

    /** Reading on after a stop is the reader's intent, so their position wins. */
    @Test
    fun `reading ahead of the stored position wins`() {
        assertEquals(
            40,
            sharedMediaOverlayStartClipIndex(
                saved = progress(clipIndex = 12),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 40,
                resumeSavedNarration = true
            )
        )
    }

    /** Reading on with no resolvable clip on the page still resumes the stored sentence. */
    @Test
    fun `a stored position answers when the reader's does not`() {
        assertEquals(
            12,
            sharedMediaOverlayStartClipIndex(
                saved = progress(clipIndex = 12),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = null,
                resumeSavedNarration = true
            )
        )
    }

    /** An explicit move — a TOC tap, a search hit, a chapter change — is the reader's intent. */
    @Test
    fun `an explicit move ignores the stored position`() {
        assertEquals(
            2,
            sharedMediaOverlayStartClipIndex(
                saved = progress(clipIndex = 300),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 2,
                resumeSavedNarration = false
            )
        )
        assertNull(
            sharedMediaOverlayStartClipIndex(
                saved = progress(clipIndex = 300),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = null,
                resumeSavedNarration = false
            )
        )
    }

    /** Narration for another spine item says nothing about this passage. */
    @Test
    fun `a position from another spine item is ignored`() {
        assertEquals(
            1,
            sharedMediaOverlayStartClipIndex(
                saved = progress(spineItemIndex = 9, clipIndex = 300),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 1,
                resumeSavedNarration = true
            )
        )
    }

    /** A content document split into several reader chapters must not resume into another one. */
    @Test
    fun `a position from another chapter of the same document is ignored`() {
        assertEquals(
            1,
            sharedMediaOverlayStartClipIndex(
                saved = progress(chapterIndex = 8, clipIndex = 300),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 1,
                resumeSavedNarration = true
            )
        )
    }

    /**
     * A position recorded without a chapter cannot be checked against the reader's chapter, so it is
     * not trusted with it: resuming the wrong chapter of a split document is worse than starting here.
     */
    @Test
    fun `a stored position without a chapter is not used against a reader chapter`() {
        assertEquals(
            1,
            sharedMediaOverlayStartClipIndex(
                saved = progress(chapterIndex = null, clipIndex = 300),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 1,
                resumeSavedNarration = true
            )
        )
    }

    /** A surface with no chapter list — Listen narrates through its own chapter table — uses it. */
    @Test
    fun `without a reader chapter the stored position applies to the document`() {
        assertEquals(
            300,
            sharedMediaOverlayStartClipIndex(
                saved = progress(chapterIndex = 7, clipIndex = 300),
                spineItemIndex = 3,
                chapterIndex = null,
                readerClipIndex = null,
                resumeSavedNarration = true
            )
        )
    }

    /** A corrupt index must not become "start from the last clip". */
    @Test
    fun `a negative stored clip index is ignored`() {
        assertEquals(
            1,
            sharedMediaOverlayStartClipIndex(
                saved = progress(clipIndex = -1),
                spineItemIndex = 3,
                chapterIndex = 7,
                readerClipIndex = 1,
                resumeSavedNarration = true
            )
        )
    }

    private fun progress(
        spineItemIndex: Int = 3,
        chapterIndex: Int? = 7,
        clipIndex: Int
    ) = SharedMediaOverlayNarrationProgress(
        bookId = "book",
        spineItemIndex = spineItemIndex,
        chapterIndex = chapterIndex,
        clipIndex = clipIndex
    )
}
