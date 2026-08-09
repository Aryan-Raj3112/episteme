package com.aryan.reader.paginatedreader

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.aryan.reader.shared.SearchResult
import kotlin.test.Test
import kotlin.test.assertEquals

class ReaderNavigationTargetsTest {
    @Test
    fun searchTargetPreservesAndroidOccurrenceAndSourceOffsetSemantics() {
        val blocks = listOf(
            ParagraphBlock(
                content = AnnotatedString("first target then second target"),
                cfi = "/4/2",
                startCharOffsetInSource = 100,
                endCharOffsetInSource = 131,
                blockIndex = 7
            )
        )

        assertEquals(
            ReaderNavigationTarget(chapterIndex = 3, blockIndex = 7, charOffset = 125),
            findSharedNavigationTargetForSearchResult(
                result = SearchResult(3, "Chapter", AnnotatedString("second target"), "target", 1, 0),
                blocks = blocks
            )
        )
    }

    @Test
    fun nestedAnchorUsesAnnotatedTextSourceOffset() {
        val content = buildAnnotatedString {
            append("before anchored text")
            addStringAnnotation("ID", "anchor-1", 7, 15)
        }
        val blocks = listOf(
            FlexContainerBlock(
                children = listOf(
                    ParagraphBlock(
                        content = content,
                        cfi = "/4/4",
                        startCharOffsetInSource = 40,
                        endCharOffsetInSource = 60,
                        blockIndex = 9
                    )
                ),
                blockIndex = 8
            )
        )

        assertEquals(
            ReaderNavigationTarget(chapterIndex = 2, blockIndex = 9, charOffset = 47),
            findSharedNavigationTargetForAnchor(2, "anchor-1", blocks)
        )
    }

    @Test
    fun blankAnchorAndNonTextElementIdPreserveAndroidFallbacks() {
        val image = ImageBlock(
            path = "images/cover.jpg",
            altText = "Cover",
            elementId = "cover-image",
            cfi = "/4/6",
            blockIndex = 11
        )

        assertEquals(
            ReaderNavigationTarget(5, 0, 0),
            findSharedNavigationTargetForAnchor(5, null, listOf(image))
        )
        assertEquals(
            ReaderNavigationTarget(5, 11, 0),
            findSharedNavigationTargetForAnchor(5, "cover-image", listOf(image))
        )
    }
}
