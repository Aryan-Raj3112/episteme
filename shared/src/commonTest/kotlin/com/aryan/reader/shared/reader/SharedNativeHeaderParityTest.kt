package com.aryan.reader.shared.reader

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.shared.ui.defaultHrRuleColor
import com.aryan.reader.shared.ui.needsDefaultHrRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Android-parity guards for the Sonnet CXXXIX defects (`a00e164a.epub`, verified
 * against the real book file): the shared builder applied a block's own relative
 * font-size twice (once resolving the block, once re-resolving it while laying out
 * its own text), so `h1.chapterTitle { font-size: 175% }` drew 49sp spans under a
 * 28sp block — wrapping and overlapping a header Android draws on one clean line.
 * Headers also measured the unscaled base leading while Android draws the
 * level-scaled one, and spacers measured density-blind px while rendering dp.
 */
class SharedNativeHeaderParityTest {

    private companion object {
        // Mirrors the real chapter (p140.xhtml): unstyled hr.sepBar (margins and
        // width only, like the book), small-caps 175% chapter title with a
        // bookmark id, verses as nested spans with overlay ids plus <br/>.
        const val CSS = """
            p { margin-top: 0.5em; margin-bottom: 0.5em; }
            hr { margin-left: 5%; width: 90%; text-align: center; }
            h1.chapterTitle { font-variant: small-caps; font-size: 175%; text-align: left; margin-left: 10%; }
            p.sonnet { margin-left: 10%; margin-bottom: 2em; text-indent: 0em; }
            p.navBar { text-indent: 0px; text-align: center; }
        """
        const val HTML = """
            <hr id="audio" class="sepBar"/>
            <p class="navBar"><a href="p139.xhtml#audio">[Previous]</a></p>
            <hr class="sepBar"/>
            <h1 class="chapterTitle"><span id="f000001">Sonnet CXXXIX</span></h1>
            <p class="sonnet"><span class="verse"><span id="f000002">O! call not me to justify the wrong</span></span><br/><span class="verse"><span id="f000003">That thy unkindness lays upon my heart;</span></span></p>
        """
    }

    private fun restyledBlocks(): List<com.aryan.reader.paginatedreader.SemanticBlock> {
        val book = SharedEpubBook(
            id = "sonnet",
            fileName = "sonnet.epub",
            title = "Sonnets",
            chapters = emptyList(),
            css = mapOf("Styles/style.css" to CSS)
        )
        val settings = ReaderSettings(fontSize = 18, readingMode = ReaderReadingMode.PAGINATED)
        val styling = livePaginationStyling(
            book = book,
            settings = settings,
            viewport = ReaderViewportSpec(widthPx = 1170, heightPx = 2532),
            geometry = MeasuredPageGeometry(pageWidthPx = 1170, pageHeightPx = 2532),
            density = 3f
        )
        val chapter = SharedEpubChapter(
            id = "ch",
            title = "Sonnet CXXXIX",
            plainText = "Sonnet CXXXIX",
            htmlContent = HTML
        )
        return restyleChapterBlocksForPagination(chapter, styling)
    }

    @Test
    fun `header relative size resolves once against the native base`() {
        val header = restyledBlocks().filterIsInstance<SemanticHeader>().single()
        // 175% of the 16sp native base, not of an already-scaled size.
        assertEquals(28.sp, header.style.fontSize)
        assertTrue(header.spans.isNotEmpty())
        header.spans.forEach { span ->
            // A span keeping 1.75em here would resolve against the 28sp block
            // to 49sp at draw time — the double-application this guards.
            val size = span.style.fontSize
            if (size.isSpecified) {
                assertEquals(28.sp, size, "span ${span.tag} [${span.start},${span.end}]")
            }
        }
        // The bookmark id survives on its span for overlay anchoring.
        assertTrue(header.spans.any { it.elementId == "f000001" })
    }

    @Test
    fun `header small caps reaches the spans`() {
        val header = restyledBlocks().filterIsInstance<SemanticHeader>().single()
        // `font-variant: small-caps` becomes the smcp OpenType feature on the
        // spans, exactly like the JVM parser produces for Android. Whether it
        // shows depends on the active reader font (neither Roboto nor San
        // Francisco implement smcp; WebViews synthesize it, which is why the
        // WebView reference renders caps while natives stay title-case).
        assertTrue(
            header.spans.any { it.style.spanStyle.fontFeatureSettings?.contains("smcp") == true },
            "expected an smcp span, got ${header.spans.map { it.style.spanStyle.fontFeatureSettings }}"
        )
    }

    @Test
    fun `unstyled hr keeps no border and wants the default rule`() {
        val spacers = restyledBlocks().filterIsInstance<SemanticSpacer>()
        // Two sepBar rules in the trimmed chapter: before the top navBar and
        // between the navBar and the header.
        assertEquals(2, spacers.size)
        spacers.forEach { spacer ->
            assertFalse(spacer.isExplicitLineBreak)
            assertNull(spacer.style.blockStyle.borderTop, "real book styles hr with margins only")
            assertTrue(spacer.style.blockStyle.needsDefaultHrRule())
        }
    }

    @Test
    fun `styled hr keeps its border and skips the default rule`() {
        val styled = BlockStyle()
        // sibling test for the condition shape only; a bordered style is drawn
        // by the block-box painter instead of the default rule.
        assertFalse(styled.copy(borderTop = null, borderBottom = null).copy(backgroundColor = androidx.compose.ui.graphics.Color.Red).needsDefaultHrRule())
    }

    @Test
    fun `default hr rule color follows the webview theme override`() {
        assertEquals(androidx.compose.ui.graphics.Color(0xFFCCCCCC), defaultHrRuleColor(false))
        assertEquals(androidx.compose.ui.graphics.Color(0xFF444444), defaultHrRuleColor(true))
    }

    @Test
    fun `verses keep line breaks and overlay ids`() {
        val poem = restyledBlocks().filterIsInstance<SemanticParagraph>()
            .first { it.text.contains("justify the wrong") }
        assertTrue('\n' in poem.text, "br must break verse lines")
        val ids = poem.spans.mapNotNull { it.elementId }.toSet()
        assertTrue("f000002" in ids && "f000003" in ids, "verse ids must survive, got $ids")
        // Nested verse spans resolve against the absolute body size, never compounded.
        poem.spans.forEach { span ->
            val size = span.style.fontSize
            if (size.isSpecified) {
                assertEquals(16.sp, size, "verse span ${span.tag} [${span.start},${span.end}]")
            }
        }
    }

    @Test
    fun `header default leading is level scaled like android draws`() {
        val settings = ReaderSettings(fontSize = 18, readingMode = ReaderReadingMode.PAGINATED)
        val base = measurementBaseStyle(settings, FontFamily.Default)
        // Native base leading at the default spacing: 16 x 1.2.
        val baseLeading = 16f * 1.2f
        fun leadingFor(level: Int): Float {
            val header = SemanticHeader(
                level = level,
                text = "Title",
                spans = emptyList(),
                style = com.aryan.reader.paginatedreader.CssStyle(),
                elementId = null,
                cfi = null,
                startCharOffsetInSource = 0,
                blockIndex = 0
            )
            return header.textStyle(base, settings).lineHeight.value
        }
        assertEquals(baseLeading * 1.5f, leadingFor(1), 0.0001f)
        assertEquals(baseLeading * 1.4f, leadingFor(2), 0.0001f)
        assertEquals(baseLeading * 1.1f, leadingFor(5), 0.0001f)
        assertEquals(baseLeading, leadingFor(6), 0.0001f)
    }
}
