package com.aryan.reader.shared.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aryan.reader.paginatedreader.BlockStyle
import com.aryan.reader.paginatedreader.BorderStyle
import com.aryan.reader.paginatedreader.BoxBorders
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpan
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTableCell
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderLocator
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.UserHighlight
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReaderHtmlDocumentBuilderTest {

    @Test
    fun `page document writes right alignment reader css variable`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(0, 0, "One", "alpha beta", 0, 10),
            settings = ReaderSettings(textAlign = SharedReaderTextAlign.RIGHT)
        )

        assertTrue(html.contains("--reader-align: right;"))
    }

    @Test
    fun `page document renders only the highlighted occurrence from locator offsets`() {
        val text = "alpha beta alpha beta"
        val page = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = text,
            startOffset = 0,
            endOffset = text.length
        )
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "desktop:0:11:16",
            text = "alpha",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = 11,
                endOffset = 16,
                textQuote = "alpha",
                cfi = "desktop:0:11:16"
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook(text),
            page = page,
            settings = ReaderSettings(),
            highlights = listOf(highlight)
        )

        assertEquals(1, Regex("<span class=\"reader-user-highlight").findAll(html).count())
        assertTrue(html.contains("""alpha beta <span class="reader-user-highlight user-highlight-yellow" data-reader-highlight-id="highlight-1" data-cfi="desktop:0:11:16" data-reader-start-offset="11" data-reader-end-offset="16">alpha</span> beta"""))
    }

    @Test
    fun `stored highlights split around paragraph markup`() {
        val text = "alpha\n\nbeta"
        val highlight = UserHighlight(
            id = "highlight-1",
            cfi = "desktop:0:0:${text.length}",
            text = "alpha beta",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator(
                chapterIndex = 0,
                pageIndex = 0,
                startOffset = 0,
                endOffset = text.length,
                textQuote = "alpha beta",
                cfi = "desktop:0:0:${text.length}"
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook(text),
            page = ReaderPage(0, 0, "One", text, 0, text.length),
            settings = ReaderSettings(),
            highlights = listOf(highlight)
        )

        assertEquals(2, Regex("<span class=\"reader-user-highlight").findAll(html).count())
        assertFalse(html.contains("<span class=\"reader-user-highlight user-highlight-yellow\" data-reader-highlight-id=\"highlight-1\" data-cfi=\"desktop:0:0:${text.length}\" data-reader-start-offset=\"0\" data-reader-end-offset=\"${text.length}\"><p"))
        assertFalse(html.contains("</p></span>"))
    }

    @Test
    fun `reader highlight script verifies stored text before applying offsets`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta alpha beta",
                startOffset = 0,
                endOffset = 21
            ),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("actualNormalized !== expectedNormalized"))
        assertTrue(html.contains("startOffset >= pageEnd || endOffset <= pageStart"))
        assertTrue(html.contains("normalizedRangeForText(searchRoot, expectedNormalized, false)"))
        assertTrue(html.contains("locator.textQuote || highlight.text"))
        assertTrue(html.contains("var sourceCfiBases = readerCfiBases(sourceCfi);"))
        assertTrue(html.contains("return readerHostMatchesCfi(host, sourceCfiBases);"))
        assertTrue(html.contains("if (cfiOffsets) {"))
        assertTrue(html.contains("if (hasPreciseOffsets) return;"))
    }

    @Test
    fun `reader highlight script wraps locally before guarded bridge send`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(0, 0, "One", "alpha beta", 0, 10),
            settings = ReaderSettings()
        )
        val localWrapIndex = html.indexOf("wrapRangeTextSegments(localRange")
        val bridgeSendIndex = html.indexOf("sendReaderHighlightCreated(payload, 0)")

        assertTrue(html.contains("function sendReaderHighlightCreated(payload, attempt)"))
        assertTrue(html.contains("highlight_bridge_error attempt="))
        assertTrue(html.contains("var marker = document.createElement('span');"))
        assertTrue(html.contains("range.intersectsNode(node)"))
        assertTrue(html.contains("function readerHighlightCfiForRange(startSegment, endSegment, chapterIndex, startOffset, endOffset)"))
        assertTrue(html.contains("function readerOffsetsForSourceCfi(chapterIndex, sourceCfi, expectedText)"))
        assertFalse(html.contains("paintUserHighlightRange(payload"))
        assertTrue(localWrapIndex >= 0)
        assertTrue(bridgeSendIndex > localWrapIndex)
    }

    @Test
    fun `reader highlight script wraps text fallback highlights without stale overlay rects`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(0, 0, "One", "alpha beta", 0, 10),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("function applyHighlightTextFallback(highlight)"))
        assertTrue(html.contains("applyHighlightTextFallback(highlight);"))
        assertTrue(html.contains("normalizedRangeForText(content, expectedText, false)"))
        assertTrue(html.contains("wrapRangeTextSegments(range, function ()"))
        assertFalse(html.contains("function paintUserHighlightRange("))
        assertFalse(html.contains("reader-user-highlight-layer"))
        assertFalse(html.contains("reader-user-highlight-rect"))
    }

    @Test
    fun `reader highlight script rejects mismatched fallback text ranges`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta alpha beta"),
            page = ReaderPage(0, 0, "One", "alpha beta alpha beta", 0, 21),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("function rangeMatchesStoredOffsets(content, range, startOffset, endOffset)"))
        assertTrue(html.contains("rangeMatchesStoredOffsets(content, textRange, startOffset, endOffset)"))
        assertTrue(html.contains("highlight_expected_mismatch id="))
    }

    @Test
    fun `reader highlight script reconciles unsaved local highlight wrappers`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(0, 0, "One", "alpha beta", 0, 10),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("var readerCurrentHighlights = [];"))
        assertTrue(html.contains("function scheduleReaderHighlightReconcile()"))
        assertTrue(html.contains("scheduleReaderHighlightReconcile();"))
    }

    @Test
    fun `page document can render a two page spread`() {
        val left = ReaderPage(
            pageIndex = 2,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "left page",
            startOffset = 0,
            endOffset = 9
        )
        val right = ReaderPage(
            pageIndex = 3,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "right page",
            startOffset = 10,
            endOffset = 20
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("left page\n\nright page"),
            page = left,
            visiblePages = listOf(left, right),
            settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)
        )

        assertTrue(html.contains("reader-spread"))
        assertEquals(2, Regex("<section class=\"page\"").findAll(html).count())
        assertTrue(html.contains("data-reader-page-index=\"2\""))
        assertTrue(html.contains("data-reader-page-index=\"3\""))
        assertTrue(html.contains("readerPaginationLayoutLog"))
        assertTrue(html.contains("EpistemeEpubPagination"))
    }

    @Test
    fun `page document scopes android locator highlights to one page in a spread`() {
        val pageText = "prefix  target suffix"
        val book = semanticHighlightBook(
            leftText = pageText,
            rightText = pageText
        )
        val left = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = pageText,
            startOffset = 100,
            endOffset = 100 + pageText.length,
            semanticBlocks = listOf(
                SemanticParagraph(pageText, emptyList(), CssStyle(), null, "/4/2", startCharOffsetInSource = 100, blockIndex = 42)
            )
        )
        val right = ReaderPage(
            pageIndex = 1,
            chapterIndex = 0,
            chapterTitle = "One",
            text = pageText,
            startOffset = 200,
            endOffset = 200 + pageText.length,
            semanticBlocks = listOf(
                SemanticParagraph(pageText, emptyList(), CssStyle(), null, "/4/4", startCharOffsetInSource = 200, blockIndex = 43)
            )
        )
        val highlight = UserHighlight(
            id = "highlight-android",
            cfi = "android-locator:0:42:108",
            text = "target",
            color = HighlightColor.YELLOW,
            chapterIndex = 0,
            locator = ReaderLocator.fromLegacy(
                chapterIndex = 0,
                cfi = "android-locator:0:42:108",
                textQuote = "target"
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = left,
            visiblePages = listOf(left, right),
            settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE),
            highlights = listOf(highlight)
        )

        assertEquals(1, Regex("data-reader-highlight-id=\"highlight-android\"").findAll(html).count())
        assertTrue(html.contains("""data-reader-page-index="0""""))
        assertTrue(html.contains("""data-reader-page-index="1""""))
    }

    @Test
    fun `page document scopes source cfi text fallback highlights to cfi page`() {
        val pageText = "prefix  target suffix"
        val book = semanticHighlightBook(
            leftText = pageText,
            rightText = pageText
        )
        val left = ReaderPage(
            pageIndex = 0,
            chapterIndex = 0,
            chapterTitle = "One",
            text = pageText,
            startOffset = 100,
            endOffset = 100 + pageText.length,
            semanticBlocks = listOf(
                SemanticParagraph(pageText, emptyList(), CssStyle(), null, "/4/2", startCharOffsetInSource = 100, blockIndex = 42)
            )
        )
        val right = ReaderPage(
            pageIndex = 1,
            chapterIndex = 0,
            chapterTitle = "One",
            text = pageText,
            startOffset = 200,
            endOffset = 200 + pageText.length,
            semanticBlocks = listOf(
                SemanticParagraph(pageText, emptyList(), CssStyle(), null, "/4/4", startCharOffsetInSource = 200, blockIndex = 43)
            )
        )
        val highlight = UserHighlight(
            id = "highlight-cfi",
            cfi = "/4/2:8|/4/2:14",
            text = "target",
            color = HighlightColor.YELLOW,
            chapterIndex = 0
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = left,
            visiblePages = listOf(left, right),
            settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE),
            highlights = listOf(highlight)
        )

        assertEquals(1, Regex("data-reader-highlight-id=\"highlight-cfi\"").findAll(html).count())
    }

    @Test
    fun `vertical document carries active locator for shared scroll navigation`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter("one", "One", "First chapter text."),
                    SharedEpubChapter("two", "Two", "Second chapter text.")
                )
            ),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL),
            navigationLocator = ReaderLocator(
                chapterIndex = 1,
                startOffset = 7,
                endOffset = 14,
                cfi = "desktop:1:7:14"
            )
        )

        assertTrue(html.contains("data-reader-active-chapter-index=\"1\""))
        assertTrue(html.contains("data-reader-active-start-offset=\"7\""))
        assertTrue(html.contains("scrollToActiveLocator"))
        assertTrue(html.contains("readerDesktopPositionTraceLog"))
        assertTrue(html.contains("bestVisibleReaderHost"))
        assertTrue(html.contains("function prepareVerticalScrollMeasurement(targetChapter)"))
        assertTrue(html.contains("pendingRestoreLocator = locator"))
        assertTrue(html.contains("positionCfi = stableReaderCfi(positionCfi) || stableDesktopCfi(chapterIndex, offset, offset)"))
        assertFalse(html.contains("positionCfi = 'desktop-scroll:' + metrics.scrollY"))
    }

    @Test
    fun `vertical document centers followed tts locator without changing active locator scroll`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = repeatedWordBook("alpha beta gamma"),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )
        val activeScrollStart = html.indexOf("function scrollToActiveLocator()")
        val activeCallStart = html.indexOf("scrollToLocator({", activeScrollStart)
        val activeCallEnd = html.indexOf("});", activeCallStart)
        assertTrue(activeScrollStart >= 0)
        assertTrue(activeCallStart > activeScrollStart)
        assertTrue(activeCallEnd > activeCallStart)
        val activeScrollCall = html.substring(activeCallStart, activeCallEnd)

        assertTrue(html.contains("function scrollToLocator(locator, options)"))
        assertTrue(html.contains("function shouldCenterScrollTarget(options)"))
        assertTrue(html.contains("function shouldTrackScrollRestore(options)"))
        assertTrue(html.contains("return documentTop - Math.round((viewportHeight - rectHeight) / 2);"))
        assertTrue(html.contains("if (follow && locator) scrollToLocator(locator, { align: 'center', trackRestore: false });"))
        assertFalse(activeScrollCall.contains("align: 'center'"))
        assertFalse(activeScrollCall.contains("trackRestore: false"))
    }

    @Test
    fun `vertical document reports visible locator from top reader edge`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = repeatedWordBook("alpha beta gamma"),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )

        assertTrue(html.contains("return Math.max(1, Math.min(height - 1, 8));"))
        assertFalse(html.contains("Math.round(height * 0.12)"))
        assertTrue(html.contains("function firstVisibleLineRect()"))
        assertTrue(html.contains("function firstVisibleLineStart(targetLineRect)"))
        assertTrue(html.contains("var topLineStart = topLineRect ? firstVisibleLineStart(topLineRect) : null;"))
        assertTrue(html.contains("event=web_line_start_choice"))
        assertTrue(html.contains("return visibleResult(node, i, offset + i);"))
        assertFalse(html.contains("charRect.top >= viewportTop - 0.5"))
        assertTrue(html.contains("var sourceOffset = boundaryOffsetWithinContent(content, node, localOffset);"))
        assertTrue(html.contains("return positionFromReaderHost(chapter, requestedOffset, preferredY, 'restore_locator_visible');"))
        assertTrue(html.contains("var position = pendingRestoreVisiblePosition() || currentVisiblePosition();"))
        assertTrue(html.contains("return fallback || { offset: contentStart, textNode: null };"))
        assertTrue(html.contains("EpistemeDesktopTtsStartTrace"))
        assertTrue(html.contains("readerTtsStartTraceLog('event=web_position_report_send"))
    }

    @Test
    fun `vertical document can render a chapter window while retaining page anchors`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter("one", "One", "First chapter body."),
                    SharedEpubChapter("two", "Two", "Second chapter body."),
                    SharedEpubChapter("three", "Three", "Third chapter body.")
                )
            ),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL),
            pages = listOf(
                ReaderPage(0, 0, "One", "First chapter body.", 0, 19),
                ReaderPage(1, 1, "Two", "Second chapter body.", 0, 20),
                ReaderPage(2, 2, "Three", "Third chapter body.", 0, 19)
            ),
            renderedChapterRange = 1..1
        )

        assertFalse(html.contains("First chapter body."))
        assertTrue(html.contains("Second chapter body."))
        assertFalse(html.contains("Third chapter body."))
        assertFalse(html.contains("data-reader-chapter-index=\"0\""))
        assertTrue(html.contains("data-reader-chapter-index=\"1\""))
        assertFalse(html.contains("data-reader-chapter-index=\"2\""))
        assertTrue(html.contains("\"chapterIndex\":0"))
        assertTrue(html.contains("\"chapterIndex\":1"))
        assertTrue(html.contains("\"chapterIndex\":2"))
        assertTrue(html.contains("function renderedVerticalPageAnchors()"))
    }

    @Test
    fun `vertical document keeps native webview scrollbar at edge`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = repeatedWordBook("alpha beta"),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )

        assertTrue(html.contains("--reader-scrollbar-track: color-mix(in srgb, var(--reader-bg)"))
        assertTrue(html.contains("--reader-scrollbar-thumb: color-mix(in srgb, var(--reader-fg)"))
        assertTrue(html.contains("""<html class="reader-vertical-root">"""))
        assertTrue(Regex("html\\.reader-vertical-root \\{\\s*width: 100%;\\s*min-width: 0;\\s*overflow-y: scroll;\\s*scrollbar-width: thin;").containsMatchIn(html))
        assertTrue(html.contains("html.reader-vertical-root::-webkit-scrollbar"))
        assertFalse(html.contains("html.reader-vertical-root::-webkit-scrollbar,\n                body.reader-vertical::-webkit-scrollbar {\n                  width: 0;"))
        assertTrue(html.contains("scrollbar-gutter: stable;"))
    }

    @Test
    fun `vertical document lays out continuous full width content`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = repeatedWordBook("alpha beta"),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL, pageWidth = 520)
        )
        val verticalExpansionCss = Regex(
            "body\\.reader-vertical > \\.chapter,\\s*" +
                "body\\.reader-vertical > :not\\(\\.chapter\\):not\\(#reader-selection-menu\\):not\\(\\.reader-selection-handle\\):not\\(script\\):not\\(style\\),\\s*" +
                "body\\.reader-vertical > \\.chapter > :not\\(\\.reader-content\\),\\s*" +
                "body\\.reader-vertical > \\.chapter > \\.chapter-title,\\s*" +
                "body\\.reader-vertical > \\.chapter > \\.reader-content \\{\\s*" +
                "box-sizing: border-box !important;\\s*" +
                "min-width: 0 !important;"
        )
        val verticalMarginCss = Regex(
            "body\\.reader-vertical > \\.chapter \\{\\s*" +
                "width: 100% !important;\\s*" +
                "max-width: none !important;\\s*" +
                "margin: 0 !important;"
        )
        val verticalContentCss = Regex(
            "body\\.reader-vertical > :not\\(\\.chapter\\):not\\(#reader-selection-menu\\):not\\(\\.reader-selection-handle\\):not\\(script\\):not\\(style\\),\\s*" +
                "body\\.reader-vertical > \\.chapter > :not\\(\\.reader-content\\),\\s*" +
                "body\\.reader-vertical > \\.chapter > \\.chapter-title,\\s*" +
                "body\\.reader-vertical > \\.chapter > \\.reader-content \\{\\s*" +
                "width: var\\(--reader-vertical-page-width\\) !important;\\s*" +
                "max-width: none !important;\\s*" +
                "margin-left: auto !important;\\s*" +
                "margin-right: auto !important;"
        )
        val verticalChapterSiblingResetCss = Regex(
            "body\\.reader-vertical > \\.chapter > :not\\(\\.reader-content\\) \\{\\s*" +
                "position: static !important;\\s*" +
                "left: auto !important;\\s*" +
                "right: auto !important;"
        )
        val verticalContentClampCss = Regex(
            "body\\.reader-vertical \\.reader-content p,\\s*" +
                "body\\.reader-vertical \\.reader-content div,\\s*" +
                "body\\.reader-vertical \\.reader-content h1,"
        )
        val verticalTextAlignCss = Regex(
            "body\\.reader-vertical \\.reader-content,\\s*" +
                "body\\.reader-vertical \\.reader-content p,\\s*" +
                "body\\.reader-vertical \\.reader-content li,"
        )
        val verticalPositionResetCss = Regex(
            "position: static !important;\\s*" +
            "left: auto !important;\\s*" +
            "right: auto !important;\\s*" +
            "top: auto !important;\\s*" +
            "bottom: auto !important;\\s*" +
            "transform: none !important;\\s*" +
            "float: none !important;\\s*" +
            "clear: none !important;"
        )
        val verticalNestedWrapperResetCss = Regex(
            "body\\.reader-vertical \\.reader-content div,\\s*" +
                "body\\.reader-vertical \\.reader-content section,\\s*" +
                "body\\.reader-vertical \\.reader-content article,"
        )
        val verticalTitleClampCss = Regex(
            "body\\.reader-vertical \\.reader-content :where\\(h1, h2, h3, h4, h5, h6, hgroup, center,"
        )
        val verticalMarginResetCss = Regex(
            "body\\.reader-vertical \\.reader-content > p,\\s*" +
                "body\\.reader-vertical \\.reader-content > div,\\s*" +
                "body\\.reader-vertical \\.reader-content > h1,"
        )
        val paginatedWidthCss = Regex(
            "\\.chapter, \\.page \\{\\s*" +
                "max-width: var\\(--reader-page-width\\);"
        )
        val verticalBodyCss = Regex(
            "body\\.reader-vertical \\{\\s*" +
                "width: 100%;\\s*" +
                "max-width: 100%;\\s*" +
                "min-height: 100vh;\\s*" +
                "min-height: 100dvh;\\s*" +
                "min-width: 0;\\s*" +
                "overflow-x: hidden;\\s*" +
                "overflow-y: auto;\\s*" +
                "padding: var\\(--reader-vertical-margin-y\\) 0;"
        )

        assertTrue(html.contains("--reader-vertical-margin-y: 16px;"))
        assertTrue(html.contains("--reader-vertical-content-width: 92ch;"))
        assertTrue(html.contains("--reader-vertical-page-width: max(0px, calc(100% - (var(--reader-margin-x) * 2)));"))
        assertFalse(html.contains("body.reader-vertical .chapter,"))
        assertFalse(html.contains("body.reader-vertical .chapter > :not(.reader-content)"))
        assertTrue(verticalBodyCss.containsMatchIn(html))
        assertTrue(verticalExpansionCss.containsMatchIn(html))
        assertTrue(html.contains("content-visibility: auto;"))
        assertTrue(html.contains("contain-intrinsic-size: auto 1200px;"))
        assertTrue(verticalMarginCss.containsMatchIn(html))
        assertTrue(verticalContentCss.containsMatchIn(html))
        assertTrue(verticalChapterSiblingResetCss.containsMatchIn(html))
        assertTrue(verticalTextAlignCss.containsMatchIn(html))
        assertFalse(html.contains("body.reader-vertical .reader-content {\n                  flex: 1 0 auto;"))
        assertFalse(html.contains("min-height: 100dvh;\n                  display: flex;"))
        assertTrue(html.contains("text-align: var(--reader-align) !important;"))
        assertTrue(verticalContentClampCss.containsMatchIn(html))
        assertTrue(verticalPositionResetCss.containsMatchIn(html))
        assertTrue(verticalNestedWrapperResetCss.containsMatchIn(html))
        assertTrue(verticalTitleClampCss.containsMatchIn(html))
        assertTrue(verticalMarginResetCss.containsMatchIn(html))
        assertTrue(paginatedWidthCss.containsMatchIn(html))
    }

    @Test
    fun `vertical document exposes page anchor updater for in place format changes`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = repeatedWordBook("alpha beta"),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL),
            pages = listOf(
                ReaderPage(0, 0, "One", "alpha", 0, 5),
                ReaderPage(1, 0, "One", "beta", 6, 10)
            )
        )

        assertTrue(html.contains("var readerPageAnchors = ["))
        assertTrue(html.contains("window.readerSetPageAnchors = function (anchors)"))
        assertTrue(html.contains("readerPageAnchors = anchors;"))
        assertTrue(html.contains("function pageForVerticalScroll()"))
        assertTrue(html.contains("desktop-scroll-page:"))
        assertTrue(html.contains("pageIndex: numberAttribute(document.body, 'data-reader-active-page-index', null)"))
        assertTrue(html.contains("chapterId: host.getAttribute('data-reader-chapter-id')"))
        assertTrue(html.contains("href: host.getAttribute('data-reader-chapter-href')"))
        assertTrue(html.contains("blockIndex: blockPosition ? blockPosition.blockIndex : null"))
        assertTrue(html.contains("charOffset: blockPosition ? blockPosition.charOffset : null"))
        assertTrue(html.contains("blockIndex: numberAttribute(document.body, 'data-reader-active-block-index', null)"))
        assertTrue(html.contains("charOffset: numberAttribute(document.body, 'data-reader-active-char-offset', null)"))
        assertTrue(html.contains("cfi: document.body.getAttribute('data-reader-active-cfi')"))
    }

    @Test
    fun `appearance update script carries vertical format settings`() {
        val script = ReaderHtmlDocumentBuilder.appearanceUpdateScript(
            settings = ReaderSettings(
                fontSize = 24,
                lineSpacing = 1.8f,
                margin = 60,
                horizontalMargin = 72,
                verticalMargin = 90,
                pageWidth = 940,
                paragraphSpacing = 1.4f,
                imageScale = 1.35f,
                textAlign = SharedReaderTextAlign.JUSTIFY,
                fontFamily = "Serif"
            )
        )

        assertTrue(script.contains("root.style.setProperty('--reader-font-size', \"24px\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-line-height', \"1.8\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-page-width', \"940px\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-margin-x', \"72px\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-vertical-margin-y', \"30px\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-vertical-page-width', 'max(0px, calc(100% - (var(--reader-margin-x) * 2)))');"))
        assertTrue(script.contains("root.style.setProperty('--reader-image-scale', \"135%\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-align', \"justify\");"))
        assertTrue(script.contains("root.style.setProperty('--reader-family', \"Georgia, 'Times New Roman', serif\");"))
    }

    @Test
    fun `page anchor update script avoids full vertical document reload`() {
        val script = ReaderHtmlDocumentBuilder.pageAnchorsUpdateScript(
            listOf(
                ReaderPage(3, 1, "Two", "chapter", 42, 84)
            )
        )

        assertTrue(script.contains("window.readerSetPageAnchors"))
        assertTrue(script.contains("""{"pageIndex":3,"chapterIndex":1,"startOffset":42,"endOffset":84}"""))
    }

    @Test
    fun `selection menu omits ai and tts actions when disabled`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings(),
            readerAiFeaturesEnabled = false,
            cloudTtsEnabled = false
        )

        assertFalse(html.contains("""data-action="define""""))
        assertFalse(html.contains("""data-action="speak""""))
        assertTrue(html.contains("""data-action="web-search""""))
        assertTrue(html.contains("""data-action="palette""""))
        assertTrue(html.contains("""aria-label="Search""""))
        assertTrue(html.contains("""<svg viewBox="0 0 960 960""""))
        assertFalse(html.contains("""data-action="dictionary""""))
        assertFalse(html.contains("""data-action="translate""""))
        assertFalse(html.contains("""data-action="find""""))
    }

    @Test
    fun `selection menu omits all external lookup actions when offline`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings(),
            externalLookupEnabled = false
        )

        assertFalse(html.contains("""data-action="dictionary""""))
        assertFalse(html.contains("""data-action="web-search""""))
        assertFalse(html.contains("""data-action="translate""""))
        assertFalse(html.contains("""data-action="find""""))
        assertTrue(html.contains("""data-action="palette""""))
        assertTrue(html.contains("""data-action="copy""""))
        assertTrue(html.contains("""data-action="clear""""))
    }

    @Test
    fun `selection menu opens from regular selection and right click`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("function scheduleMenuFromSelection()"))
        assertTrue(html.contains("selectionAnchorRect(selection)"))
        assertTrue(html.contains("selectionMenuCandidate"))
        assertTrue(html.contains("overlapAreaWithSelection"))
        assertTrue(html.contains("if (selectionPointerDown || activeSelectionHandle) return;"))
        assertTrue(html.contains("rangeBoundaryRect(range.startContainer"))
        assertTrue(html.contains("document.addEventListener('selectionchange'"))
        assertTrue(html.contains("document.addEventListener('pointerdown'"))
        assertTrue(html.contains("document.addEventListener('mouseup'"))
        assertTrue(html.contains("document.addEventListener('touchend'"))
        assertTrue(html.contains("document.addEventListener('contextmenu'"))
    }

    @Test
    fun `vertical selection script derives offsets from raw html chapters`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Alpha beta Gamma delta",
                        htmlContent = "<p><span>Alpha beta</span></p><p><span>Gamma delta</span></p>"
                    )
                )
            ),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )

        assertTrue(html.contains("function normalizedOffsetForBoundary(root, container, offset)"))
        assertTrue(html.contains("var explicitOffset = absoluteOffsetForBoundary(content, container, offset);"))
        assertTrue(html.contains("var normalizedOffset = normalizedOffsetForBoundary(content, container, offset);"))
        assertTrue(html.contains("return normalizedOffset === null ? null : contentStartOffset(content) + normalizedOffset;"))
        assertTrue(html.contains("var boundaryInside = nodeInside(content, range.startContainer) || nodeInside(content, range.endContainer);"))
        assertTrue(html.contains("range.intersectsNode(content)"))
        assertTrue(html.contains("selection_segments_rejected contents="))
        assertTrue(html.contains("function readerHighlightFlowLog(message)"))
        assertTrue(html.contains("selection_begin mode="))
        assertTrue(html.contains("var actionSegments = selectionSegmentsForRange(actionRange);"))
        assertTrue(html.contains("payload.locator = {"))
        assertTrue(html.contains("window.kmpJsBridge.callNative('readerSelectionAction', JSON.stringify(payload));"))
        assertTrue(html.contains("sendSelectionAction('palette', text)"))
        assertTrue(html.contains("bridge_send_success attempt="))
        assertTrue(html.contains("<p><span>Alpha beta</span></p><p><span>Gamma delta</span></p>"))
    }

    @Test
    fun `vertical document prefers semantic blocks when available for cross mode locators`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Semantic text",
                        htmlContent = "<p>Raw text</p>",
                        semanticBlocks = listOf(
                            SemanticParagraph(
                                text = "Semantic text",
                                spans = emptyList(),
                                style = CssStyle(),
                                elementId = null,
                                cfi = "/4/2/4",
                                startCharOffsetInSource = 40,
                                blockIndex = 12
                            )
                        )
                    )
                )
            ),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL)
        )

        assertTrue(html.contains("Semantic text"))
        assertTrue(html.contains("""data-reader-cfi="/4/2/4""""))
        assertTrue(html.contains("""data-reader-block-index="12""""))
        assertFalse(html.contains("Raw text"))
    }

    @Test
    fun `vertical selection menu renders every configured highlight palette slot`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = repeatedWordBook("alpha beta"),
            settings = ReaderSettings(readingMode = ReaderReadingMode.VERTICAL),
            highlightPalette = ReaderHighlightPalette(
                listOf(
                    HighlightColor.CYAN,
                    HighlightColor.MAGENTA,
                    HighlightColor.LIME,
                    HighlightColor.PINK
                )
            )
        )

        assertEquals(4, Regex("""class="reader-selection-color"""").findAll(html).count())
        assertTrue(html.contains("""data-color-id="cyan""""))
        assertTrue(html.contains("""data-color-id="pink""""))
        assertTrue(html.contains("""class="reader-selection-spectrum""""))
        assertTrue(html.contains("""data-action="palette""""))
    }

    @Test
    fun `highlight palette update script replaces selection menu colors without document reload`() {
        val script = ReaderHtmlDocumentBuilder.highlightPaletteUpdateScript(
            ReaderHighlightPalette(
                listOf(
                    HighlightColor.CYAN,
                    HighlightColor.MAGENTA,
                    HighlightColor.LIME,
                    HighlightColor.PINK
                )
            )
        )

        assertTrue(script.contains("reader-selection-colors"))
        assertTrue(script.contains("""data-color-id=\"cyan\""""))
        assertTrue(script.contains("""data-color-id=\"pink\""""))
        assertTrue(script.contains("""reader-selection-spectrum"""))
        assertTrue(script.contains("""data-action=\"palette\""""))
        assertFalse(script.contains("location.reload"))
    }

    @Test
    fun `selection menu renders icons and draggable handles`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("""class="reader-selection-icon""""))
        assertTrue(html.contains("""id="reader-selection-start-handle""""))
        assertTrue(html.contains("""id="reader-selection-end-handle""""))
        assertTrue(html.contains("beginSelectionHandleDrag('start'"))
        assertTrue(html.contains("requestSelectionHandleUpdate(event)"))
        assertTrue(html.contains("document.addEventListener('selectstart'"))
        assertTrue(html.contains("EPUB_SELECTION_DEBUG"))
        assertTrue(html.contains("readerSelectionDebugLog('drag_line"))
        assertTrue(html.contains("rangeTouchesSelectionChrome"))
        assertTrue(html.contains("element.closest('#reader-selection-menu, .reader-selection-handle')"))
        assertFalse(html.contains("next.toString().trim().length"))
        assertTrue(html.contains("document.caretRangeFromPoint"))
        assertTrue(html.contains("wrapRangeTextSegments(range"))
        assertFalse(html.contains("surroundContents"))
    }

    @Test
    fun `paginated spread script targets the actual page host for locator highlights`() {
        val left = ReaderPage(
            pageIndex = 2,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "left page",
            startOffset = 0,
            endOffset = 9
        )
        val right = ReaderPage(
            pageIndex = 3,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "right page",
            startOffset = 10,
            endOffset = 20
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("left page\n\nright page"),
            page = left,
            visiblePages = listOf(left, right),
            settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)
        )

        assertTrue(html.contains("function readerHostsForLocator(chapterIndex, startOffset, endOffset)"))
        assertTrue(html.contains("function readerHostForLocator(chapterIndex, startOffset, endOffset)"))
        assertTrue(html.contains("function readerHostElementForCfiPoint(chapterIndex, cfiPoint)"))
        assertTrue(html.contains("var hosts = Array.prototype.slice.call(document.querySelectorAll(chapterSelector));"))
        assertTrue(html.contains("var targetChapters = readerHostsForLocator(chapterIndex, startOffset, endOffset);"))
        assertTrue(html.contains("var chapter = readerHostForLocator(chapterIndex, startOffset, endOffset);"))
        assertTrue(html.contains("data-reader-active-page-index"))
        assertTrue(html.contains("positionFromReaderHost(activePage, activeStart, readerProbeY(), 'active_page')"))
    }

    @Test
    fun `paginated spread script can create one highlight from a selection crossing visible pages`() {
        val left = ReaderPage(
            pageIndex = 2,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "left page",
            startOffset = 0,
            endOffset = 9
        )
        val right = ReaderPage(
            pageIndex = 3,
            chapterIndex = 0,
            chapterTitle = "One",
            text = "right page",
            startOffset = 10,
            endOffset = 20
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("left page\n\nright page"),
            page = left,
            visiblePages = listOf(left, right),
            settings = ReaderSettings(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE)
        )

        assertTrue(html.contains("function selectionSegmentsForRange(range)"))
        assertTrue(html.contains("var sameChapter = segments.every(function (segment)"))
        assertTrue(html.contains("var cfi = readerHighlightCfiForRange(firstSegment, lastSegment, chapterIndex, startOffset, endOffset);"))
        assertTrue(html.contains("return startPoint + '|' + endPoint;"))
        assertTrue(html.contains("payloads.forEach(function (payload)"))
        assertTrue(html.contains("wrapRangeTextSegments(segment.range"))
    }

    @Test
    fun `page document uses supplied texture data uri`() {
        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = repeatedWordBook("alpha beta"),
            page = ReaderPage(
                pageIndex = 0,
                chapterIndex = 0,
                chapterTitle = "One",
                text = "alpha beta",
                startOffset = 0,
                endOffset = 10
            ),
            settings = ReaderSettings(
                textureId = ReaderTexture.PAPER.id,
                textureAlpha = 0.5f
            ),
            textureDataUri = "data:image/png;base64,readertexture"
        )

        assertTrue(html.contains("url('data:image/png;base64,readertexture')"))
        assertTrue(html.contains("mix-blend-mode: multiply"))
        assertTrue(html.contains("opacity: 0.5"))
    }

    @Test
    fun `page document keeps semantic images anchored to surrounding text page`() {
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = "Before image after image.",
                    semanticBlocks = listOf(
                        SemanticParagraph("Before image", emptyList(), CssStyle(), null, null, startCharOffsetInSource = 0, blockIndex = 7),
                        SemanticImage("data:image/png;base64,abc", "Cover", null, null, CssStyle(), "cover-image", "/4/2"),
                        SemanticParagraph("after image", emptyList(), CssStyle(), null, null, startCharOffsetInSource = 13)
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "One", "Before image after image.", 0, 24),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("""<img src="data:image/png;base64,abc" alt="Cover""""))
        assertTrue(html.contains("""loading="lazy" decoding="async""""))
        assertTrue(html.contains("""data-reader-block-index="7""""))
        assertTrue(html.contains("""data-reader-cfi="/4/2""""))
        assertTrue(html.contains("""data-reader-block-index="0""""))
    }

    @Test
    fun `page document renders semantic link spans as anchors`() {
        val text = "Open the reference"
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = text,
                    semanticBlocks = listOf(
                        SemanticParagraph(
                            text = text,
                            spans = listOf(
                                SemanticSpan(
                                    start = 9,
                                    end = text.length,
                                    style = CssStyle(),
                                    linkHref = "notes.xhtml#ref",
                                    tag = "a"
                                )
                            ),
                            style = CssStyle(),
                            elementId = null,
                            cfi = null,
                            startCharOffsetInSource = 0
                        )
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "One", text, 0, text.length),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("""<a href="notes.xhtml#ref" data-reader-link="true">reference</a>"""))
        assertTrue(html.contains("--reader-link:"))
        assertTrue(html.contains("a[href],"))
        assertTrue(html.contains("color: var(--reader-link) !important"))
        assertTrue(html.contains("a[href] *"))
        assertTrue(html.contains("readerLinkClicked"))
        assertTrue(html.contains("bridge_missing"))
        assertTrue(html.contains("readerlink://click?payload="))
        assertTrue(html.contains("fallback_navigation_error"))
        assertTrue(html.contains("event.preventDefault();"))
    }

    @Test
    fun `page document carries semantic table and inline css without forced table grid`() {
        val text = "Styled cell"
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = text,
                    semanticBlocks = listOf(
                        SemanticTable(
                            rows = listOf(
                                listOf(
                                    SemanticTableCell(
                                        content = listOf(
                                            SemanticParagraph(
                                                text = text,
                                                spans = listOf(
                                                    SemanticSpan(
                                                        start = 0,
                                                        end = 6,
                                                        style = CssStyle(
                                                            spanStyle = SpanStyle(fontWeight = FontWeight.Bold),
                                                            textTransform = "uppercase"
                                                        ),
                                                        tag = "span"
                                                    )
                                                ),
                                                style = CssStyle(),
                                                elementId = null,
                                                cfi = null,
                                                startCharOffsetInSource = 0
                                            )
                                        ),
                                        isHeader = false,
                                        colspan = 1,
                                        style = CssStyle(
                                            blockStyle = BlockStyle(
                                                padding = BoxBorders(left = 4.dp),
                                                borderBottom = BorderStyle(width = 2.dp, color = Color.Red, style = "solid")
                                            )
                                        )
                                    )
                                )
                            ),
                            style = CssStyle(),
                            elementId = null,
                            cfi = null
                        )
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "One", text, 0, text.length),
            settings = ReaderSettings()
        )

        assertTrue(html.contains("border-bottom:2.0px solid #ff0000"))
        assertTrue(html.contains("padding-left:4.0px"))
        assertTrue(html.contains("font-weight:700"))
        assertTrue(html.contains("text-transform:uppercase"))
        assertTrue(!Regex("""td,\s*th\s*\{\s*border:""").containsMatchIn(html))
    }

    @Test
    fun `page document clips semantic lists to visible items and keeps marker styles`() {
        val first = "Chapter one"
        val second = "Chapter two"
        val book = SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "toc",
                    title = "Contents",
                    plainText = "$first\n$second",
                    semanticBlocks = listOf(
                        SemanticList(
                            items = listOf(
                                SemanticListItem(
                                    text = first,
                                    spans = emptyList(),
                                    style = CssStyle(),
                                    elementId = null,
                                    cfi = null,
                                    startCharOffsetInSource = 0,
                                    itemMarkerImage = null
                                ),
                                SemanticListItem(
                                    text = second,
                                    spans = listOf(
                                        SemanticSpan(
                                            start = 0,
                                            end = second.length,
                                            style = CssStyle(),
                                            linkHref = "chap02.xhtml",
                                            tag = "a"
                                        )
                                    ),
                                    style = CssStyle(
                                        blockStyle = BlockStyle(
                                            padding = BoxBorders(left = 2.dp),
                                            listStyleImage = "icons/toc-dot.png"
                                        )
                                    ),
                                    elementId = null,
                                    cfi = null,
                                    startCharOffsetInSource = first.length + 1,
                                    itemMarkerImage = "icons/toc-dot.png"
                                )
                            ),
                            isOrdered = false,
                            style = CssStyle(
                                fontSize = 0.85.em,
                                blockStyle = BlockStyle(listStyleType = "none")
                            ),
                            elementId = null,
                            cfi = null
                        )
                    )
                )
            )
        )

        val html = ReaderHtmlDocumentBuilder.pageDocument(
            book = book,
            page = ReaderPage(0, 0, "Contents", second, first.length + 1, first.length + 1 + second.length),
            settings = ReaderSettings()
        )

        assertTrue(!html.contains(first))
        assertTrue(html.contains(second))
        assertTrue(html.contains("list-style-type:none"))
        assertTrue(html.contains("font-size:0.85em"))
        assertTrue(html.contains("list-style-image:url(&#39;icons/toc-dot.png&#39;)"))
        assertTrue(html.contains("""<a href="chap02.xhtml" data-reader-link="true">Chapter two</a>"""))
    }

    @Test
    fun `vertical document includes theme aware link styling`() {
        val html = ReaderHtmlDocumentBuilder.verticalDocument(
            book = SharedEpubBook(
                id = "book",
                fileName = "book.epub",
                title = "Book",
                chapters = listOf(
                    SharedEpubChapter(
                        id = "one",
                        title = "One",
                        plainText = "Read more",
                        htmlContent = """<p><a href="notes.xhtml"><span>Read more</span></a></p>"""
                    )
                )
            ),
            settings = ReaderSettings(
                readingMode = ReaderReadingMode.VERTICAL,
                darkMode = true
            )
        )

        assertTrue(html.contains("--reader-link:"))
        assertTrue(html.contains("--reader-link-bg: rgba("))
        assertTrue(html.contains("a[href] *,"))
        assertTrue(html.contains("""<a href="notes.xhtml"><span>Read more</span></a>"""))
    }

    private fun repeatedWordBook(text: String): SharedEpubBook {
        return SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = text
                )
            )
        )
    }

    private fun semanticHighlightBook(leftText: String, rightText: String): SharedEpubBook {
        return SharedEpubBook(
            id = "book",
            fileName = "book.epub",
            title = "Book",
            chapters = listOf(
                SharedEpubChapter(
                    id = "one",
                    title = "One",
                    plainText = "$leftText\n\n$rightText",
                    semanticBlocks = listOf(
                        SemanticParagraph(leftText, emptyList(), CssStyle(), null, "/4/2", startCharOffsetInSource = 100, blockIndex = 42),
                        SemanticParagraph(rightText, emptyList(), CssStyle(), null, "/4/4", startCharOffsetInSource = 200, blockIndex = 43)
                    )
                )
            )
        )
    }
}
