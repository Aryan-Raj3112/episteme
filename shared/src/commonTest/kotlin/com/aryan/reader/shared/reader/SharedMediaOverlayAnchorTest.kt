package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpan
import com.aryan.reader.paginatedreader.CssStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Media overlay anchor resolution: element id -> absolute chapter offsets -> `SharedPlaybackFragment`.
 *
 * This is where the overlay stops being a list of clips and becomes something paintable, so the
 * tests care about two things: that the range covers the **whole** anchored element rather than
 * starting at it, and that the result is a `SharedPlaybackFragment` — the type TTS already paints
 * through — rather than a second anchor kind.
 */
class SharedMediaOverlayAnchorTest {

    private val density = androidx.compose.ui.unit.Density(1f)

    /** One chapter: three paragraphs, the middle one carrying an inline span id. */
    private fun chapter(): SharedEpubChapter {
        fun para(text: String, id: String?, cfi: String, start: Int, spans: List<SemanticSpan> = emptyList()) =
            SemanticParagraph(
                text = text,
                spans = spans,
                style = CssStyle(),
                elementId = id,
                cfi = cfi,
                startCharOffsetInSource = start,
                blockIndex = start
            )
        return SharedEpubChapter(
            id = "c1",
            title = "Chapter One",
            plainText = "",
            semanticBlocks = listOf(
                para("My mistress' eyes are nothing like the sun", "verse1", "/6/4[ch]!/4/2", 0),
                para(
                    text = "Crimson and gold forbid his eyes",
                    id = null,
                    cfi = "/6/4[ch]!/4/4",
                    start = 35,
                    spans = listOf(
                        SemanticSpan(
                            start = 11,
                            end = 22,
                            style = CssStyle(),
                            tag = "span",
                            elementId = "f000002"
                        )
                    )
                ),
                para("For his every vapour does retrace", "verse3", "/6/4[ch]!/4/6", 63)
            )
        )
    }

    private fun clip(
        elementId: String?,
        spineTextHref: String = "OEBPS/Text/ch1.xhtml",
        index: Int = 0
    ) = SharedMediaOverlayClip(
        parId = "p$index",
        clipIndex = index,
        textHref = spineTextHref,
        elementId = elementId,
        audioPath = "OEBPS/Audio/a.mp3",
        clipBeginMs = index * 1_000L,
        clipEndMs = (index + 1) * 1_000L,
        epubTypes = emptySet(),
        seqDepth = 1
    )

    // --- the anchored range -------------------------------------------------------------------

    /**
     * A block-level id covers the **whole block**. This is what the reference book relies on: every
     * one of its 2315 `par`s points at a `<span id>` wrapping one line of verse, so a resolver that
     * returned only the element's first character would highlight one character per clip.
     */
    @Test
    fun `a block level id resolves to the whole block`() {
        val chapters = listOf(chapter())
        val fragment = resolveSharedMediaOverlayFragment(
            clip = clip(elementId = "verse1"),
            spineItemIndex = 0,
            chapters = chapters
        )!!
        assertEquals("/6/4[ch]!/4/2", fragment.blockCfi)
        assertEquals(0, fragment.startAbs)
        assertEquals("My mistress' eyes are nothing like the sun".length, fragment.endAbs)
    }

    @Test
    fun `an inline span id resolves to the span extent not the whole block`() {
        val chapters = listOf(chapter())
        val fragment = resolveSharedMediaOverlayFragment(
            clip = clip(elementId = "f000002"),
            spineItemIndex = 0,
            chapters = chapters
        )!!
        assertEquals("/6/4[ch]!/4/4", fragment.blockCfi)
        // Block starts at 35, span is 11..22 within it.
        assertEquals(46, fragment.startAbs)
        assertEquals(57, fragment.endAbs)
    }

    @Test
    fun `a resolved fragment is the same anchor type tts already paints`() {
        val fragment = resolveSharedMediaOverlayFragment(
            clip = clip(elementId = "verse1"),
            spineItemIndex = 0,
            chapters = listOf(chapter())
        )
        // Compile-time proof, and the reason this seam exists: one type for both features.
        assertNotNull(fragment as SharedPlaybackFragment)
    }

    // --- misses -------------------------------------------------------------------------------

    @Test
    fun `a clip with no element id has nothing to highlight`() {
        assertNull(
            resolveSharedMediaOverlayFragment(
                clip = clip(elementId = null),
                spineItemIndex = 0,
                chapters = listOf(chapter())
            )
        )
        assertNull(
            resolveSharedMediaOverlayFragment(
                clip = clip(elementId = "   "),
                spineItemIndex = 0,
                chapters = listOf(chapter())
            )
        )
    }

    @Test
    fun `an element id that is not in the chapter does not resolve`() {
        assertNull(
            resolveSharedMediaOverlayFragment(
                clip = clip(elementId = "no-such-id"),
                spineItemIndex = 0,
                chapters = listOf(chapter())
            )
        )
    }

    @Test
    fun `an empty chapter list does not resolve`() {
        assertNull(
            resolveSharedMediaOverlayFragment(
                clip = clip(elementId = "verse1"),
                spineItemIndex = 0,
                chapters = emptyList()
            )
        )
    }

    // --- chapter selection --------------------------------------------------------------------

    /**
     * The spine map wins when present, because the overlay was linked to that spine item. Without it
     * the resolver falls back to searching chapter content, which is what rescues a book whose
     * overlay and spine disagree.
     */
    @Test
    fun `the spine map is preferred over a content search`() {
        val chapters = listOf(chapter(), chapter())
        // Spine item 1 is the second chapter, whose first block has the same cfi and ids.
        val fragment = resolveSharedMediaOverlayFragment(
            clip = clip(elementId = "verse1"),
            spineItemIndex = 1,
            chapters = chapters,
            spineItemIndexToChapterIndex = mapOf(1 to 1)
        )!!
        assertEquals(0, fragment.startAbs)
    }

    @Test
    fun `a stale spine map entry falls back to searching chapter content`() {
        val chapters = listOf(chapter())
        val fragment = resolveSharedMediaOverlayFragment(
            clip = clip(elementId = "verse1"),
            spineItemIndex = 7,
            chapters = chapters,
            // Points outside the chapter list, so it cannot be trusted.
            spineItemIndexToChapterIndex = mapOf(7 to 99)
        )
        assertNotNull(fragment)
        assertEquals(0, fragment.startAbs)
    }

    // --- batch resolution ---------------------------------------------------------------------

    private fun document() = SharedMediaOverlayDocument(
        spineItemIndex = 0,
        textHref = "OEBPS/Text/ch1.xhtml",
        clips = listOf(
            clip(elementId = "verse1", index = 0),
            clip(elementId = "f000002", index = 1),
            clip(elementId = "missing", index = 2),
            clip(elementId = "verse3", index = 3),
            clip(elementId = null, index = 4)
        ),
        declaredDurationMs = null
    )

    @Test
    fun `batch resolution agrees with resolving one clip at a time`() {
        val chapters = listOf(chapter())
        val doc = document()
        val batch = resolveSharedMediaOverlayFragments(doc, chapters)
        for (clipIndex in doc.clips.indices) {
            val one = resolveSharedMediaOverlayFragment(
                clip = doc.clips[clipIndex],
                spineItemIndex = doc.spineItemIndex,
                chapters = chapters
            )
            assertEquals(one, batch[clipIndex], "disagreement at clip $clipIndex")
        }
    }

    /** Unresolvable clips are absent, not present-and-null, so a caller can skip with one lookup. */
    @Test
    fun `unresolvable clips are absent from the batch map`() {
        val batch = resolveSharedMediaOverlayFragments(document(), listOf(chapter()))
        assertEquals(setOf(0, 1, 3), batch.keys)
    }

    @Test
    fun `a document with no resolvable anchors resolves to an empty map`() {
        val doc = SharedMediaOverlayDocument(
            spineItemIndex = 0,
            textHref = null,
            clips = listOf(clip(elementId = null, index = 0)),
            declaredDurationMs = null
        )
        assertTrue(resolveSharedMediaOverlayFragments(doc, listOf(chapter())).isEmpty())
    }

    // --- the lazy document cache --------------------------------------------------------------

    private fun indexFor(vararg pairs: Pair<Int, String>) = SharedMediaOverlayIndex(
        smilPathBySpineItem = pairs.toMap(),
        smilIdBySpineItem = emptyMap(),
        totalDurationMs = null,
        narrator = null,
        activeClass = null,
        playbackActiveClass = null,
        declaredDurationMsBySpineItem = emptyMap()
    )

    private val smil = """
        <smil xmlns="http://www.w3.org/ns/SMIL">
          <body>
            <par id="a"><text src="ch1.xhtml#verse1"/><audio src="a.mp3" clipBegin="0:00:00" clipEnd="0:00:02"/></par>
            <par id="b"><text src="ch1.xhtml#verse3"/><audio src="a.mp3" clipBegin="0:00:02" clipEnd="0:00:04"/></par>
          </body>
        </smil>
    """.trimIndent()

    @Test
    fun `the cache parses a body once and reuses it`() {
        val cache = SharedMediaOverlayDocumentCache(
            index = indexFor(0 to "smil/ch1.smil"),
            readSmil = { smil },
            maxEntries = 4
        )
        assertEquals(0, cache.parseCount)
        assertNotNull(cache.document(0))
        assertNotNull(cache.document(0))
        assertEquals(1, cache.parseCount)
    }

    /**
     * The bound is the point. Without it a 155-overlay book keeps every parsed body resident, which
     * is the leak the lazy design exists to avoid.
     */
    @Test
    fun `the cache evicts beyond its bound`() {
        var reads = 0
        val cache = SharedMediaOverlayDocumentCache(
            index = indexFor(0 to "s0.smil", 1 to "s1.smil", 2 to "s2.smil"),
            readSmil = { reads++; smil },
            maxEntries = 2
        )
        cache.document(0)
        cache.document(1)
        cache.document(2) // evicts 0
        assertEquals(3, reads)
        // 0 was evicted, so it parses again.
        cache.document(0)
        assertEquals(4, reads)
    }

    @Test
    fun `an item with no overlay never reads the archive`() {
        var reads = 0
        val cache = SharedMediaOverlayDocumentCache(
            index = indexFor(0 to "s0.smil"),
            readSmil = { reads++; smil }
        )
        assertNull(cache.document(9))
        assertEquals(0, reads)
        assertEquals(0, cache.parseCount)
    }

    @Test
    fun `an unreadable or malformed body yields null without throwing`() {
        val missing = SharedMediaOverlayDocumentCache(indexFor(0 to "gone.smil"), readSmil = { null })
        assertNull(missing.document(0))

        val broken = SharedMediaOverlayDocumentCache(indexFor(0 to "bad.smil"), readSmil = { "<smil><body><par>" })
        assertNull(broken.document(0))
    }

    @Test
    fun `clearing the cache forces a reparse`() {
        val cache = SharedMediaOverlayDocumentCache(indexFor(0 to "s0.smil"), readSmil = { smil })
        cache.document(0)
        cache.clear()
        cache.document(0)
        assertEquals(2, cache.parseCount)
    }

    @Test
    fun `fragmentAt resolves end to end through the cache`() {
        val cache = SharedMediaOverlayDocumentCache(indexFor(0 to "s0.smil"), readSmil = { smil })
        val fragment = cache.fragmentAt(0, 1, listOf(chapter()))
        assertNotNull(fragment)
        assertEquals("/6/4[ch]!/4/6", fragment.blockCfi)
        assertNull(cache.fragmentAt(0, 99, listOf(chapter())))
    }
}