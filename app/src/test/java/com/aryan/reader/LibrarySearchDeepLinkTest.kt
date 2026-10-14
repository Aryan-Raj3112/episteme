package com.aryan.reader

import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.search.SharedGlobalSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySearchDeepLinkTest {

    private fun hit(
        chapter: Int? = null,
        page: Int? = null,
    ) = SharedGlobalSearchHit(
        bookId = "b1",
        chunkIndex = 0,
        locator = ReaderLocator(chapterIndex = chapter, pageIndex = page),
        snippet = "snippet",
        score = 1.0,
        chapterIndex = chapter,
        pageIndex = page,
    )

    private fun recent(
        chapter: Int? = null,
        page: Int? = null,
        cfi: String? = null,
    ) = RecentFileItem(
        bookId = "b1",
        uriString = null,
        type = FileType.EPUB,
        displayName = "b1.epub",
        timestamp = 1L,
        lastChapterIndex = chapter,
        lastPage = page,
        lastPositionCfi = cfi,
    )

    @Test
    fun `epub target prefers hit chapter then resume then start`() {
        assertEquals(4, libraryHitEpubTargetChapter(hit(chapter = 4), resumeChapter = 9))
        assertEquals(9, libraryHitEpubTargetChapter(hit(), resumeChapter = 9))
        assertEquals(0, libraryHitEpubTargetChapter(hit(), resumeChapter = null))
        assertEquals(0, libraryHitEpubTargetChapter(hit(chapter = -3), resumeChapter = null))
    }

    @Test
    fun `pdf target prefers page then slide chapter then resume`() {
        assertEquals(7, libraryHitPdfTargetPage(hit(page = 7, chapter = 2), resumePage = 1))
        // PPTX indexes slides on the chapter axis but opens in the PDF viewer.
        assertEquals(2, libraryHitPdfTargetPage(hit(chapter = 2), resumePage = 1))
        assertEquals(1, libraryHitPdfTargetPage(hit(), resumePage = 1))
        assertNull(libraryHitPdfTargetPage(hit(), resumePage = null))
    }

    @Test
    fun `epub origin needs a saved chapter or cfi`() {
        assertNull(libraryHitEpubOrigin(recent()))
        assertNull(libraryHitEpubOrigin(null))
        val origin = libraryHitEpubOrigin(recent(chapter = 3, cfi = "cfi-1"))
        assertEquals(3, origin?.chapterIndex)
        assertEquals("cfi-1", origin?.cfi)
    }
}
