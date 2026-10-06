package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.shared.reader.SharedMediaOverlayClip
import com.aryan.reader.shared.reader.SharedMediaOverlayDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The ContentBlock anchor resolver — Android's half of the media overlay projection.
 *
 * Everything here mirrors a rule the `SemanticBlock` resolver already follows, because the two
 * describe the same book through different block families and a divergence between them would paint
 * the band somewhere the navigation never goes.
 */
class SharedMediaOverlayContentAnchorTest {

    @Test
    fun spanIdResolvesToTheSpanExtentWithTheBlockCfi() {
        val content = buildAnnotatedString {
            append("before anchored text")
            addStringAnnotation("ID", "f000001", 7, 15)
        }
        val blocks = listOf(
            ParagraphBlock(
                content = content,
                cfi = "/4/4",
                startCharOffsetInSource = 40,
                endCharOffsetInSource = 62,
                blockIndex = 3
            )
        )

        val resolved = resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "f000001")), blocks)

        assertEquals(1, resolved.size)
        val fragment = resolved.getValue(0)
        assertEquals("/4/4", fragment.blockCfi)
        // 40 + 7 .. 40 + 15, half-open at the annotation's own end.
        assertEquals(47, fragment.startAbs)
        assertEquals(55, fragment.endAbs)
    }

    @Test
    fun blockLevelIdCoversTheWholeBlock() {
        val blocks = listOf(
            ParagraphBlock(
                content = AnnotatedString("a narrated paragraph"),
                elementId = "p009",
                cfi = "/4/6",
                startCharOffsetInSource = 100,
                endCharOffsetInSource = 119,
                blockIndex = 5
            )
        )

        val resolved = resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "p009")), blocks)

        val fragment = resolved.getValue(0)
        assertEquals(100, fragment.startAbs)
        assertEquals(119, fragment.endAbs)
    }

    /**
     * `-1` is the "unset" end on blocks written by older caches, and `findPageForCfi` reads it the
     * same way. Falling back to the block's text length is what keeps the band paintably wide
     * instead of resolving to a backwards range that never draws.
     */
    @Test
    fun unsetBlockEndFallsBackToTextLength() {
        val blocks = listOf(
            ParagraphBlock(
                content = AnnotatedString("1234567890"),
                elementId = "p1",
                cfi = "/4/8",
                startCharOffsetInSource = 10,
                endCharOffsetInSource = -1,
                blockIndex = 1
            )
        )

        val fragment = resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "p1")), blocks).getValue(0)

        assertEquals(10, fragment.startAbs)
        assertEquals(20, fragment.endAbs)
    }

    @Test
    fun unresolvedIdsAreAbsentRatherThanMappedToNull() {
        val blocks = listOf(
            ParagraphBlock(AnnotatedString("no ids here"), cfi = "/4/2", blockIndex = 1)
        )

        val resolved = resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "missing")), blocks)

        assertTrue(resolved.isEmpty())
    }

    @Test
    fun clipsWithoutAnAnchorAreSkippedWithoutTouchingTheBlocks() {
        val blocks = listOf(
            ParagraphBlock(
                content = AnnotatedString("anchored"),
                elementId = "p1",
                cfi = "/4/2",
                startCharOffsetInSource = 5,
                endCharOffsetInSource = 13,
                blockIndex = 1
            )
        )
        val document = document(
            clip(0, null),
            clip(1, "p1")
        )

        val resolved = resolveSharedMediaOverlayFragmentsInBlocks(document, blocks)

        assertEquals(setOf(1), resolved.keys)
    }

    @Test
    fun nestedContainersAreWalkedLikeTheNavigationResolver() {
        val content = buildAnnotatedString {
            append("wrapped target")
            addStringAnnotation("ID", "anchor-2", 8, 14)
        }
        val blocks = listOf(
            FlexContainerBlock(
                children = listOf(
                    ParagraphBlock(
                        content = content,
                        cfi = "/4/10",
                        startCharOffsetInSource = 200,
                        endCharOffsetInSource = 214,
                        blockIndex = 4
                    )
                ),
                blockIndex = 3
            )
        )

        val fragment = resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "anchor-2")), blocks).getValue(0)

        assertEquals(208, fragment.startAbs)
        assertEquals(214, fragment.endAbs)
    }

    @Test
    fun noBlocksResolvesToNothing() {
        assertTrue(resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "p1")), null).isEmpty())
        assertTrue(resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, "p1")), emptyList()).isEmpty())
    }

    @Test
    fun aClipWithoutFragmentStaysUnresolvedRatherThanGuessing() {
        // A `par` whose text src has no fragment names a whole document; there is no single element
        // to paint, and inventing one would highlight an arbitrary slice of the chapter.
        val blocks = listOf(
            ParagraphBlock(
                content = AnnotatedString("body text that is not anchored by id"),
                cfi = "/4/2",
                startCharOffsetInSource = 0,
                endCharOffsetInSource = 36,
                blockIndex = 1
            )
        )

        assertTrue(resolveSharedMediaOverlayFragmentsInBlocks(document(clip(0, null)), blocks).isEmpty())
    }

    private fun document(vararg clips: SharedMediaOverlayClip) = SharedMediaOverlayDocument(
        spineItemIndex = 0,
        textHref = "Text/p009.xhtml",
        clips = clips.toList(),
        declaredDurationMs = null
    )

    private fun clip(index: Int, elementId: String?) = SharedMediaOverlayClip(
        parId = "p$index",
        clipIndex = index,
        textHref = "Text/p009.xhtml",
        elementId = elementId,
        audioPath = "Audio/009_008.mp3",
        clipBeginMs = index * 1_000L,
        clipEndMs = (index + 1) * 1_000L,
        epubTypes = emptySet(),
        seqDepth = 1
    )
}
