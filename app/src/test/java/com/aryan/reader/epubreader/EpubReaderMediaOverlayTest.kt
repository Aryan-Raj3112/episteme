package com.aryan.reader.epubreader

import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.shared.reader.SharedMediaOverlayIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which spine item narrates which reader chapter.
 *
 * This mapping is what turns "the reader is in chapter 7" into "play overlay document 6", and it is
 * the one place the reader's own re-flow meets the package's spine: the reference book has 159
 * chapters for 156 spine items because several documents were split at TOC fragments, and a mapping
 * that assumed the two indexes lined up would narrate the wrong chapter from the second split
 * onwards.
 */
class EpubReaderMediaOverlayTest {

    @Test
    fun `chapters map to spines by content path`() {
        val chapters = listOf(
            chapter(0, "OEBPS/Text/cover.xhtml"),
            chapter(1, "OEBPS/Text/p009.xhtml"),
            chapter(2, "OEBPS/Text/p010.xhtml")
        )
        val index = index(
            0 to "OEBPS/Text/cover.xhtml",
            1 to "OEBPS/Text/p009.xhtml",
            2 to "OEBPS/Text/p010.xhtml"
        )

        val mapping = spineItemIndexByChapter(chapters, index)

        assertEquals(mapOf(0 to 0, 1 to 1, 2 to 2), mapping)
    }

    /**
     * A TOC split makes several chapters share one source document. All of them narrate from the
     * same spine item — and the projection then searches those chapters for the clip's element id,
     * which is why every split chapter must appear in the mapping rather than only the first.
     */
    @Test
    fun `toc-split chapters share their spine item`() {
        val chapters = listOf(
            chapter(0, "OEBPS/Text/p009.xhtml"),
            chapter(1, "OEBPS/Text/p009.xhtml"),
            chapter(2, "OEBPS/Text/p010.xhtml")
        )
        val index = index(
            4 to "OEBPS/Text/p009.xhtml",
            5 to "OEBPS/Text/p010.xhtml"
        )

        val mapping = spineItemIndexByChapter(chapters, index)

        assertEquals(mapOf(0 to 4, 1 to 4, 2 to 5), mapping)
    }

    @Test
    fun `a chapter with no spine item is absent rather than guessed`() {
        val chapters = listOf(
            chapter(0, "OEBPS/Text/p009.xhtml"),
            chapter(1, "OEBPS/Text/extra.xhtml")
        )
        val index = index(2 to "OEBPS/Text/p009.xhtml")

        val mapping = spineItemIndexByChapter(chapters, index)

        assertEquals(mapOf(0 to 2), mapping)
    }

    @Test
    fun `path differences in case and leading slash do not lose the link`() {
        val chapters = listOf(chapter(0, "/OEBPS/Text/p009.xhtml"))
        val index = index(1 to "OEBPS/Text/p009.xhtml")

        assertEquals(mapOf(0 to 1), spineItemIndexByChapter(chapters, index))
    }

    @Test
    fun `fragments and queries on a path do not lose the link`() {
        val chapters = listOf(chapter(0, "OEBPS/Text/p009.xhtml#f000002"))
        val index = index(3 to "OEBPS/Text/p009.xhtml?x=1")

        assertEquals(mapOf(0 to 3), spineItemIndexByChapter(chapters, index))
    }

    @Test
    fun `a book without overlays maps nothing`() {
        val chapters = listOf(chapter(0, "OEBPS/Text/p009.xhtml"))

        val mapping = spineItemIndexByChapter(chapters, SharedMediaOverlayIndex.EMPTY)

        assertTrue(mapping.isEmpty())
    }

    private fun chapter(index: Int, absPath: String) = EpubChapter(
        chapterId = "chapter-$index",
        absPath = absPath,
        title = "Chapter $index",
        htmlFilePath = absPath,
        plainTextContent = "",
        htmlContent = ""
    )

    /**
     * Builds the index's spine-to-content half, which is all this mapping reads. The smil half is
     * irrelevant here and deliberately left empty: the mapping must not depend on which spine items
     * *narrate*, only on which document each spine item points at.
     */
    private fun index(vararg paths: Pair<Int, String>) = SharedMediaOverlayIndex(
        smilPathBySpineItem = emptyMap(),
        contentPathBySpineItem = paths.toMap(),
        smilIdBySpineItem = emptyMap(),
        totalDurationMs = null,
        narrator = null,
        activeClass = null,
        playbackActiveClass = null,
        declaredDurationMsBySpineItem = emptyMap()
    )
}
