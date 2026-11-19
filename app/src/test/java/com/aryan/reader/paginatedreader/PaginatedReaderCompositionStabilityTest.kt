package com.aryan.reader.paginatedreader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `RenderFlexChildBlock.renderTextBlock` composes one text block of a reader page, so it runs for
 * every visible block on every pass. Two things there must stay stable:
 *
 * - the `onRegisterLayout` lambda handed to `TextWithEmphasis`. A freshly allocated lambda made the
 *   callee's group "changed" on every pass — `GapComposer.changedInstance` on that argument is where
 *   a scroll ANR was sampled — and re-created its `onGloballyPositioned` modifier node each time
 *   (docs/crashlytics-triage.md#138).
 * - the `TextStyle` handed to `TextWithEmphasis`, which used to be an unconditional
 *   `textStyle.copy(...)` per block per pass.
 */
class PaginatedReaderCompositionStabilityTest {

    @Test
    fun `renderTextBlock hands down a stable register-layout lambda`() {
        val source = sourceFile("com/aryan/reader/paginatedreader/PaginatedReaderContent.kt").readText()
        val body = source.substringAfter("fun renderTextBlock(block: TextContentBlock)")
            .substringBefore("when (childBlock)")

        assertFalse(
            "the register-layout lambda must not be allocated inline at the call site",
            body.contains("onRegisterLayout = {")
        )
        assertTrue(
            "the register-layout lambda must be remembered",
            body.contains("val registerLayout = remember(")
        )
        assertTrue(
            "TextWithEmphasis must receive the remembered lambda",
            body.contains("onRegisterLayout = registerLayout")
        )
        // The callback runs from onGloballyPositioned, so it must still see current values.
        assertTrue(
            "the remembered lambda must read the block through rememberUpdatedState",
            body.contains("rememberUpdatedState(block)") &&
                body.contains("rememberUpdatedState(pageIndex)") &&
                body.contains("rememberUpdatedState(blockLayoutMap)")
        )
    }

    @Test
    fun `renderTextBlock remembers the derived text style on cheap keys`() {
        val source = sourceFile("com/aryan/reader/paginatedreader/PaginatedReaderContent.kt").readText()
        val body = source.substringAfter("fun renderTextBlock(block: TextContentBlock)")
            .substringBefore("when (childBlock)")

        assertTrue(
            "the derived style must be remembered",
            body.contains("val finalStyle = remember(textStyle, headerLevel, blockTextAlign)")
        )
        assertFalse(
            "the style must not be rebuilt unconditionally with textStyle.copy",
            body.contains("is ParagraphBlock -> textStyle.copy")
        )
    }

    private fun sourceFile(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("app/src/main/java/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Unable to locate $relativePath from ${File(".").absolutePath}")
    }
}
