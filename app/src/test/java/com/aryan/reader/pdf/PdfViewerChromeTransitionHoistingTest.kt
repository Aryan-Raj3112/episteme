package com.aryan.reader.pdf

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pins the #160 fix: the PDF chrome's transient notices must not rebuild their enter/exit
 * transitions on every recomposition.
 *
 * `PdfViewerChromeMusicianAndIndicators` recomposes per frame while a musician-mode hold updates
 * `rightHoldProgress` (a ~16 ms loop), and each pass used to allocate a fresh
 * `slideInVertically() + fadeIn()` / `slideOutVertically() + fadeOut()` pair per
 * `AnimatedVisibility`. Those specs capture nothing from composition, so they are hoisted to
 * file-level values (`pdfChromeNoticeEnter` / `pdfChromeNoticeExit`).
 */
class PdfViewerChromeTransitionHoistingTest {

    private fun sourceFile(): File {
        val candidates = listOf(
            File("src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt"),
            File("app/src/main/java/com/aryan/reader/pdf/PdfViewerScreen.kt"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: error("PdfViewerScreen.kt not found from ${File(".").absolutePath}")
    }

    /** Body of `PdfViewerChromeMusicianAndIndicators`, from its declaration to the next top-level fun. */
    private fun musicianAndIndicatorsBody(source: String): String {
        val start = source.indexOf("fun androidx.compose.foundation.layout.BoxWithConstraintsScope.PdfViewerChromeMusicianAndIndicators(")
        assertTrue("composable declaration not found", start >= 0)
        val nextFun = source.indexOf("\n@Composable\n", start)
        assertTrue("next @Composable after the target function not found", nextFun > start)
        return source.substring(start, nextFun)
    }

    @Test
    fun `notices use hoisted transitions instead of allocating them per recomposition`() {
        val source = sourceFile().readText()
        val body = musicianAndIndicatorsBody(source)

        // The transitions captured nothing from composition, so they must not be built in the body.
        listOf("slideInVertically(", "slideOutVertically(", "fadeIn(", "fadeOut(").forEach { call ->
            assertFalse(
                "PdfViewerChromeMusicianAndIndicators must not allocate `$call` on every recomposition",
                body.contains(call),
            )
        }
        // ...and both AnimatedVisibility notices must use the shared hoisted pair.
        assertTrue(body.contains("enter = pdfChromeNoticeEnter"))
        assertTrue(body.contains("exit = pdfChromeNoticeExit"))

        assertTrue(
            "hoisted enter transition value missing",
            source.contains("private val pdfChromeNoticeEnter = slideInVertically() + fadeIn()"),
        )
        assertTrue(
            "hoisted exit transition value missing",
            source.contains("private val pdfChromeNoticeExit = slideOutVertically() + fadeOut()"),
        )
    }
}
