package com.aryan.reader.epubreader

import androidx.compose.ui.text.AnnotatedString
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.ContentBlock
import com.aryan.reader.paginatedreader.ParagraphBlock
import com.aryan.reader.shared.reader.SharedMediaOverlayDocumentCache
import com.aryan.reader.shared.reader.SharedMediaOverlayIndex
import com.aryan.reader.shared.reader.sharedMediaOverlaySpineItemIndexByChapter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /**
     * The mapping under test is shared now — `sharedMediaOverlaySpineItemIndexByChapter` — and the
     * cases below assert it through Android's own chapter type, because the reader's chapter list is
     * the thing that has to line up with the package's spine. The shared function takes content paths
     * precisely so both platforms' chapter types can feed it; see its own tests for the shared
     * contract.
     */
    private fun spineItemIndexByChapter(
        chapters: List<EpubChapter>,
        overlayIndex: SharedMediaOverlayIndex
    ): Map<Int, Int> = sharedMediaOverlaySpineItemIndexByChapter(
        chapterContentPaths = chapters.map { it.absPath },
        overlayIndex = overlayIndex
    )

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

    /**
     * A clip resolves to the chapter whose path its SMIL names, and to that clip's own offsets.
     *
     * This is the join between "the player is on clip 2" and "the band goes here": the document's
     * `epub:textref` has to find the right one of the reader's chapters, and the element id has to
     * resolve inside it. Both are silent failures — a wrong chapter is a band on the wrong page, and
     * an unresolvable id is no band at all — so both are asserted rather than assumed.
     */
    @Test
    fun `a clip projects onto the chapter its smil names, with the clip's own offsets`() = runTest {
        val projector = projector(
            chapters = listOf(
                chapter(0, "OEBPS/Text/cover.xhtml"),
                chapter(1, "OEBPS/Text/p009.xhtml")
            ),
            // The cover carries an id too, so a projector that ignored the textref and simply walked
            // the chapters from the top would still resolve *something* — just not this clip.
            blocksByChapter = mapOf(
                0 to listOf(paragraph(0, "cover-art", start = 0, end = 9)),
                1 to listOf(
                    paragraph(0, "p1", start = 100, end = 118),
                    paragraph(1, "p2", start = 120, end = 133)
                )
            ),
            textHref = "OEBPS/Text/p009.xhtml",
            parIds = listOf("p1", "p2")
        )

        val projection = projector.project(spineItemIndex = 0, clipIndex = 1)!!

        assertEquals(1, projection.chapterIndex)
        assertEquals("p2", projection.elementId)
        assertEquals(120, projection.fragment!!.startAbs)
        assertEquals(133, projection.fragment!!.endAbs)
    }

    /**
     * A TOC-split document is searched until one of its chapters actually carries the element.
     *
     * The reference book has 159 chapters for 156 spine items, so an overlay's textref can name a
     * path that several chapters share. Stopping at the first one paints the band in a chapter that
     * does not contain the text — or, more often, refuses to paint it at all, because that chapter's
     * blocks never mention the id.
     */
    @Test
    fun `a toc split chapter without the element falls through to the one that has it`() = runTest {
        val projector = projector(
            chapters = listOf(
                chapter(0, "OEBPS/Text/p009.xhtml"),
                chapter(1, "OEBPS/Text/p009.xhtml")
            ),
            blocksByChapter = mapOf(
                0 to listOf(paragraph(0, "p1", start = 0, end = 18)),
                1 to listOf(paragraph(0, "p2", start = 40, end = 52))
            ),
            textHref = "OEBPS/Text/p009.xhtml",
            parIds = listOf("p2")
        )

        val projection = projector.project(spineItemIndex = 0, clipIndex = 0)!!

        assertEquals("the second split chapter holds the id", 1, projection.chapterIndex)
        assertEquals(40, projection.fragment!!.startAbs)
        // One chapter's anchors were resolved, and the second clip reuses that same map rather than
        // walking the chapter again — a clip advances every couple of seconds.
        assertEquals(1, projector.anchorResolutionCount)
        projector.project(spineItemIndex = 0, clipIndex = 0)
        assertEquals(1, projector.anchorResolutionCount)
    }

    /**
     * Pressing play mid-chapter starts at the passage the reader is looking at.
     *
     * `RS §9.3` is about following the text; the reverse — starting from where the reader already is —
     * is what stops play from jumping them back to the top of the chapter. The first clip that reaches
     * past the offset wins, so an offset inside clip 0's own text does not silently skip it.
     */
    @Test
    fun `narration starts at the clip whose text reaches the reader's position`() = runTest {
        val projector = projector(
            chapters = listOf(chapter(0, "OEBPS/Text/p009.xhtml")),
            blocksByChapter = mapOf(
                0 to listOf(
                    paragraph(0, "p1", start = 0, end = 18),
                    paragraph(1, "p2", start = 20, end = 40),
                    paragraph(2, "p3", start = 42, end = 60)
                )
            ),
            textHref = "OEBPS/Text/p009.xhtml",
            parIds = listOf("p1", "p2", "p3")
        )

        assertEquals(0, projector.clipIndexForReaderPosition(0, readerOffset = 0))
        assertEquals(0, projector.clipIndexForReaderPosition(0, readerOffset = 17))
        assertEquals(1, projector.clipIndexForReaderPosition(0, readerOffset = 20))
        assertEquals(2, projector.clipIndexForReaderPosition(0, readerOffset = 45))
        // Past the last clip's end: there is nothing to start at, and answering "the last clip"
        // would narrate the chapter's ending at a reader who is past it.
        assertNull(projector.clipIndexForReaderPosition(0, readerOffset = 60))
        assertNull(projector.clipIndexForReaderPosition(0, readerOffset = -1))
    }

    /**
     * A chapter whose blocks are not built yet is retried, not remembered as empty.
     *
     * The paginator builds a chapter's blocks on demand, so the first projection of a chapter the
     * reader has not opened can legitimately find nothing. Caching that would silence the chapter for
     * the rest of the session, which is why only non-empty resolutions are kept. Until they arrive the
     * chapter is still reported — from the SMIL's own `epub:textref` — so narration still follows; only
     * the offsets wait.
     */
    @Test
    fun `a chapter with no blocks yet is projected again once they load`() = runTest {
        var available = false
        val projector = projector(
            chapters = listOf(chapter(0, "OEBPS/Text/p009.xhtml")),
            blocksForChapter = { if (available) listOf(paragraph(0, "p1", start = 100, end = 118)) else null },
            textHref = "OEBPS/Text/p009.xhtml",
            parIds = listOf("p1")
        )

        val before = projector.project(spineItemIndex = 0, clipIndex = 0)!!
        assertEquals("the chapter is known from the overlay's textref", 0, before.chapterIndex)
        assertNull("but there are no offsets to paint with yet", before.fragment)
        assertEquals("the clip's id is still reported", "p1", before.elementId)

        available = true
        val after = projector.project(spineItemIndex = 0, clipIndex = 0)!!
        assertEquals(0, after.chapterIndex)
        assertEquals(100, after.fragment!!.startAbs)
    }

    /**
     * A WebView chapter has no `ContentBlock`s at all, and that must not cost it its follow.
     *
     * This is the case that made "narration does not change chapter when one finishes" look like a
     * continuation bug: the chapter was resolved only through the paginated block model, so on the
     * WebView surface every clip projected to no chapter — the band painted, because it anchors by id
     * in the DOM, while the reader never followed and the chapter after a narrated one never loaded.
     */
    @Test
    fun `a clip names its chapter even when nothing can be resolved against blocks`() = runTest {
        val projector = projector(
            chapters = listOf(
                chapter(0, "OEBPS/Text/p001.xhtml"),
                chapter(1, "OEBPS/Text/p009.xhtml")
            ),
            blocksForChapter = { null },
            textHref = "OEBPS/Text/p009.xhtml",
            parIds = listOf("p1")
        )

        val projection = projector.project(spineItemIndex = 0, clipIndex = 0)!!

        assertEquals("the overlay's textref names the chapter", 1, projection.chapterIndex)
        assertNull("and there is nothing to paint from a fragment", projection.fragment)
        assertEquals("p1", projection.elementId)
    }

    /**
     * But *evidence* is evidence: when a chapter's blocks exist and do not carry the element, this is
     * a fact about the book, and the clip is reported with no chapter rather than the chapter the
     * textref claims. Following a reader to text that is not there is worse than not following.
     */
    @Test
    fun `a chapter whose blocks lack the element is not claimed from the textref`() = runTest {
        val projector = projector(
            chapters = listOf(chapter(0, "OEBPS/Text/p009.xhtml")),
            blocksByChapter = mapOf(0 to listOf(paragraph(0, "something-else", start = 0, end = 18))),
            textHref = "OEBPS/Text/p009.xhtml",
            parIds = listOf("p1")
        )

        val projection = projector.project(spineItemIndex = 0, clipIndex = 0)!!

        assertNull(projection.chapterIndex)
        assertNull(projection.fragment)
    }

    /** A spine item with no overlay has no document, so there is nothing to project at all. */
    @Test
    fun `an un-narrated spine item projects nothing`() = runTest {
        val projector = projector(
            chapters = listOf(chapter(0, "OEBPS/Text/p010.xhtml")),
            blocksByChapter = mapOf(0 to listOf(paragraph(0, "p1", start = 0, end = 10)))
        )

        assertNull(projector.project(spineItemIndex = 0, clipIndex = 0))
        assertEquals(0, projector.anchorResolutionCount)
    }

    /**
     * A projector over one narrated spine item, with the chapter's blocks supplied as a lookup.
     *
     * The SMIL body is generated rather than written out because only two things matter to this
     * projector: which content document the `seq` names, and which element ids the pars anchor.
     */
    private fun projector(
        chapters: List<EpubChapter>,
        blocksByChapter: Map<Int, List<ContentBlock>> = emptyMap(),
        blocksForChapter: (suspend (Int) -> List<ContentBlock>?)? = null,
        textHref: String = "OEBPS/Text/p009.xhtml",
        smilPath: String = "OEBPS/mo/p009.smil",
        parIds: List<String> = emptyList()
    ): EpubMediaOverlayProjector {
        val index = SharedMediaOverlayIndex(
            smilPathBySpineItem = mapOf(0 to smilPath),
            contentPathBySpineItem = mapOf(0 to textHref),
            smilIdBySpineItem = mapOf(0 to "smil-0"),
            totalDurationMs = null,
            narrator = "Chris Hughes",
            activeClass = null,
            playbackActiveClass = null,
            declaredDurationMsBySpineItem = emptyMap()
        )
        val cache = SharedMediaOverlayDocumentCache(
            index = index,
            readSmil = { requested -> if (requested == smilPath) smil(smilPath, textHref, parIds) else null }
        )
        return EpubMediaOverlayProjector(
            cache = cache,
            chapters = chapters,
            blocksForChapter = blocksForChapter ?: { blocksByChapter[it].orEmpty() }
        )
    }

    /**
     * A SMIL body naming [textHref] with one anchored `par` per id.
     *
     * The `epub:textref` is written *relative to the SMIL's own directory*, as a real overlay does —
     * and as the parser resolves it. Handing the resolved path straight to the body would look
     * correct while quietly taking the fallback that searches every chapter, which is precisely how a
     * path-matching bug would hide.
     */
    private fun smil(smilPath: String, textHref: String, parIds: List<String>): String {
        val reference = relativeToSmilDirectory(smilPath, textHref)
        val pars = parIds.mapIndexed { index, id ->
            val begin = "0:00:0$index.000"
            val end = "0:00:0${index + 1}.000"
            """
                <par id="par$index">
                    <text src="$reference#$id"/>
                    <audio clipBegin="$begin" clipEnd="$end" src="Audio/p009.mp3"/>
                </par>"""
        }.joinToString("")
        return """
            <smil xmlns="http://www.w3.org/ns/SMIL" xmlns:epub="http://www.idpf.org/2007/ops" version="3.0">
                <body><seq epub:textref="$reference">$pars</seq></body>
            </smil>
        """
    }

    /** `OEBPS/mo/p009.smil` + `OEBPS/Text/p009.xhtml` -> `../Text/p009.xhtml`. */
    private fun relativeToSmilDirectory(smilPath: String, target: String): String {
        val smilDirectories = smilPath.substringBeforeLast('/', "").split('/').filter(String::isNotEmpty)
        val targetSegments = target.split('/').filter(String::isNotEmpty)
        var shared = 0
        while (shared < smilDirectories.size && shared < targetSegments.size &&
            smilDirectories[shared] == targetSegments[shared]
        ) {
            shared++
        }
        val ups = List(smilDirectories.size - shared) { ".." }
        return (ups + targetSegments.drop(shared)).joinToString("/")
    }

    private fun paragraph(blockIndex: Int, elementId: String, start: Int, end: Int) = ParagraphBlock(
        content = AnnotatedString("narrated passage $elementId"),
        elementId = elementId,
        cfi = "/4/${blockIndex * 2 + 2}",
        startCharOffsetInSource = start,
        endCharOffsetInSource = end,
        blockIndex = blockIndex
    )

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
