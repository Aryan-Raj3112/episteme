package com.aryan.reader.shared.reader

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticHeader
import com.aryan.reader.paginatedreader.SemanticImage
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticListItem
import com.aryan.reader.paginatedreader.SemanticMath
import com.aryan.reader.paginatedreader.SemanticParagraph
import com.aryan.reader.paginatedreader.SemanticSpacer
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.paginatedreader.BorderStyle
import com.aryan.reader.paginatedreader.CssStyle
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.UserHighlight
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import kotlin.math.roundToInt

object ReaderHtmlDocumentBuilder {
    fun verticalDocument(
        book: SharedEpubBook,
        settings: ReaderSettings,
        searchQuery: String = "",
        searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
        highlights: List<UserHighlight> = emptyList(),
        highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
        navigationLocator: ReaderLocator? = null,
        pages: List<ReaderPage> = emptyList(),
        readerAiFeaturesEnabled: Boolean = true,
        cloudTtsEnabled: Boolean = true,
        textureDataUri: String? = null
    ): String {
        val body = book.chapters.mapIndexed { index, chapter ->
            val chapterText = chapter.normalizedReaderText()
            val chapterHtml = chapter.toHtml(searchQuery, searchOptions)
                .applyUserHighlights(
                    highlights = highlights.filter { it.locatedChapterIndex == index },
                    contentStartOffset = 0,
                    contentEndOffset = chapterText.length
                )
            """
            <section class="chapter" id="chapter-$index" data-reader-chapter-index="$index" data-reader-chapter-id="${chapter.id.escapeHtml()}" data-reader-chapter-href="${chapter.baseHref.orEmpty().escapeHtml()}">
              <h1 class="chapter-title">${chapter.title.escapeHtml()}</h1>
              <div class="reader-content" data-reader-content-start="0" data-reader-content-end="${chapterText.length}">
                $chapterHtml
              </div>
            </section>
            """.trimIndent()
        }.joinToString("\n")
        return document(
            title = book.title,
            settings = settings,
            bookCss = book.css.values.joinToString("\n"),
            body = body,
            searchQuery = searchQuery,
            searchOptions = searchOptions,
            highlightPalette = highlightPalette,
            navigationLocator = navigationLocator,
            pageAnchors = pages,
            readerAiFeaturesEnabled = readerAiFeaturesEnabled,
            cloudTtsEnabled = cloudTtsEnabled,
            textureDataUri = textureDataUri
        )
    }

    fun pageDocument(
        book: SharedEpubBook,
        page: ReaderPage?,
        settings: ReaderSettings,
        searchQuery: String = "",
        searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
        highlights: List<UserHighlight> = emptyList(),
        highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
        navigationLocator: ReaderLocator? = null,
        readerAiFeaturesEnabled: Boolean = true,
        cloudTtsEnabled: Boolean = true,
        textureDataUri: String? = null
    ): String {
        val chapter = page?.let { book.chapters.getOrNull(it.chapterIndex) }
        val body = if (page == null || chapter == null) {
            logReaderHtml("page_document_empty reason=missing_page_or_chapter")
            "<section class=\"page\"></section>"
        } else {
            val semanticPageBlocks = chapter.semanticBlocks.blocksForPage(page)
            val usedSemanticBlocks = semanticPageBlocks.isNotEmpty()
            val blocks = if (usedSemanticBlocks) {
                semanticPageBlocks.joinToString("") { it.toHtml(searchQuery, searchOptions) }
            } else {
                page.text.textToParagraphHtml(searchQuery, searchOptions, baseOffset = page.startOffset)
            }
            val pageHtml = blocks.applyUserHighlights(
                highlights = highlights.filter { it.belongsToPage(page) },
                contentStartOffset = page.startOffset,
                contentEndOffset = page.endOffset
            )
            logReaderHtml(
                "page_document page=${page.pageIndex + 1} chapter=${page.chapterIndex} " +
                    "range=${page.startOffset}..${page.endOffset} pageText=${page.text.length} " +
                    "semantic=$usedSemanticBlocks blocks=${semanticPageBlocks.size}/${chapter.semanticBlocks.size} " +
                    "htmlChars=${pageHtml.length} settingsFont=${settings.fontSize} lineSpacing=${settings.lineSpacing} " +
                    "summary=\"${semanticPageBlocks.blockSummary()}\" styles=\"${semanticPageBlocks.styleSummary()}\""
            )
            """
            <section class="page" data-reader-chapter-index="${page.chapterIndex}" data-reader-chapter-id="${chapter.id.escapeHtml()}" data-reader-chapter-href="${chapter.baseHref.orEmpty().escapeHtml()}" data-reader-page-index="${page.pageIndex}" data-reader-page-start="${page.startOffset}" data-reader-page-end="${page.endOffset}">
              <h1 class="chapter-title">${page.chapterTitle.escapeHtml()}</h1>
              <div class="reader-content" data-reader-content-start="${page.startOffset}" data-reader-content-end="${page.endOffset}">
                $pageHtml
              </div>
            </section>
            """.trimIndent()
        }
        return document(
            title = book.title,
            settings = settings,
            bookCss = book.css.values.joinToString("\n"),
            body = body,
            searchQuery = searchQuery,
            searchOptions = searchOptions,
            highlightPalette = highlightPalette,
            navigationLocator = navigationLocator,
            pageAnchors = emptyList(),
            readerAiFeaturesEnabled = readerAiFeaturesEnabled,
            cloudTtsEnabled = cloudTtsEnabled,
            textureDataUri = textureDataUri
        )
    }

    private fun document(
        title: String,
        settings: ReaderSettings,
        bookCss: String,
        body: String,
        searchQuery: String,
        searchOptions: ReaderSearchOptions,
        highlightPalette: ReaderHighlightPalette,
        navigationLocator: ReaderLocator?,
        pageAnchors: List<ReaderPage>,
        readerAiFeaturesEnabled: Boolean,
        cloudTtsEnabled: Boolean,
        textureDataUri: String?
    ): String {
        val bg = settings.backgroundColorArgb?.toCssColor() ?: if (settings.darkMode) "#171A17" else "#FFFCF5"
        val fg = settings.textColorArgb?.toCssColor() ?: if (settings.darkMode) "#E7E3D8" else "#24231F"
        val highlight = if (settings.darkMode) "#675A00" else "#FFE36E"
        val align = when (settings.textAlign) {
            SharedReaderTextAlign.START -> "left"
            SharedReaderTextAlign.JUSTIFY -> "justify"
            SharedReaderTextAlign.CENTER -> "center"
        }
        val customFontUrl = settings.customFontPath?.takeIf { it.isNotBlank() }?.toCssFontUrl()
        val customFontCss = customFontUrl?.let {
            "@font-face { font-family: 'ReaderCustomFont'; src: url('$it'); font-display: swap; }"
        }.orEmpty()
        val family = if (customFontUrl != null) {
            "'ReaderCustomFont', Georgia, 'Times New Roman', serif"
        } else {
            when (settings.fontFamily) {
                "Serif" -> "Georgia, 'Times New Roman', serif"
                "Sans" -> "Inter, Segoe UI, Arial, sans-serif"
                "Mono" -> "'Roboto Mono', Consolas, monospace"
                else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
            }
        }
        val textureOverlayCss = settings.textureId
            ?.takeIf { settings.textureAlpha > 0.01f }
            ?.toTextureOverlayCss(settings.textureAlpha, settings.darkMode, textureDataUri)
            .orEmpty()
        val highlightButtons = highlightPalette.sanitized().colors.joinToString("\n") { color ->
            """<button type="button" data-action="highlight" data-color-id="${color.id}" title="${color.id.escapeHtml()}">${color.id.escapeHtml()}</button>"""
        }
        val defineButton = if (readerAiFeaturesEnabled) {
            """<button type="button" data-action="define">Define</button>"""
        } else {
            ""
        }
        val speakButton = if (cloudTtsEnabled) {
            """<button type="button" data-action="speak">Speak</button>"""
        } else {
            ""
        }
        val navigationAttributes = navigationLocator?.toNavigationAttributes().orEmpty()
        val pageAnchorJson = pageAnchors.toPageAnchorJson()
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${title.escapeHtml()}</title>
              <style>
                $bookCss
                $customFontCss
                :root {
                  color-scheme: ${if (settings.darkMode) "dark" else "light"};
                  --reader-bg: $bg;
                  --reader-fg: $fg;
                  --reader-highlight: $highlight;
                  --reader-font-size: ${settings.fontSize}px;
                  --reader-line-height: ${settings.lineSpacing};
                  --reader-page-width: ${settings.pageWidth}px;
                  --reader-margin: ${settings.margin}px;
                  --reader-margin-x: ${settings.resolvedHorizontalMargin}px;
                  --reader-margin-y: ${settings.resolvedVerticalMargin}px;
                  --reader-paragraph-spacing: ${settings.paragraphSpacing};
                  --reader-image-scale: ${(settings.imageScale * 100f).roundToInt().coerceIn(50, 200)}%;
                  --reader-align: $align;
                  --reader-family: $family;
                }
                html, body {
                  min-height: 100%;
                  margin: 0;
                  background: var(--reader-bg);
                  color: var(--reader-fg);
                  font-family: var(--reader-family);
                  font-size: var(--reader-font-size);
                  line-height: var(--reader-line-height);
                }
                body {
                  box-sizing: border-box;
                  padding: var(--reader-margin-y) var(--reader-margin-x);
                  overflow-wrap: anywhere;
                  position: relative;
                }
                $textureOverlayCss
                .chapter, .page {
                  max-width: var(--reader-page-width);
                  margin: 0 auto 48px;
                  text-align: var(--reader-align);
                  position: relative;
                  z-index: 1;
                }
                .chapter-title {
                  text-align: left;
                  font-size: 1.55em;
                  line-height: 1.25;
                  margin: 0 0 1.1em;
                }
                p, blockquote, pre, ul, ol, table, figure {
                  margin-top: 0;
                  margin-bottom: calc(1em * var(--reader-paragraph-spacing));
                }
                img, svg, video {
                  max-width: var(--reader-image-scale);
                  height: auto;
                }
                table {
                  max-width: 100%;
                  overflow-wrap: anywhere;
                }
                td, th {
                  vertical-align: top;
                }
                .reader-highlight {
                  background: var(--reader-highlight);
                  color: inherit;
                  border-radius: 2px;
                }
                .reader-user-highlight {
                  background: color-mix(in srgb, var(--reader-highlight) 72%, transparent);
                  border-radius: 2px;
                }
                ::highlight(reader-tts-highlight) {
                  background: rgba(125, 211, 252, 0.52);
                  color: inherit;
                }
                #reader-tts-highlight-layer {
                  position: absolute;
                  inset: 0;
                  z-index: 2;
                  pointer-events: none;
                }
                .reader-tts-highlight-rect {
                  position: absolute;
                  background: rgba(125, 211, 252, 0.42);
                  border-radius: 3px;
                  box-shadow: 0 0 0 1px rgba(14, 116, 144, 0.12);
                }
                ${HighlightColor.entries.joinToString("\n") { ".${it.cssClass} { background: ${it.color.toCssHex()}; }" }}
                #reader-selection-menu {
                  position: fixed;
                  z-index: 99999;
                  display: none;
                  gap: 4px;
                  align-items: center;
                  flex-wrap: wrap;
                  max-width: min(560px, calc(100vw - 16px));
                  padding: 4px;
                  border-radius: 8px;
                  background: color-mix(in srgb, var(--reader-bg) 92%, var(--reader-fg));
                  border: 1px solid color-mix(in srgb, var(--reader-fg) 18%, transparent);
                  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.24);
                }
                #reader-selection-menu button {
                  border: 0;
                  border-radius: 6px;
                  padding: 6px 9px;
                  background: transparent;
                  color: var(--reader-fg);
                  font: 600 12px system-ui, sans-serif;
                  cursor: pointer;
                }
                #reader-selection-menu button:hover {
                  background: color-mix(in srgb, var(--reader-fg) 10%, transparent);
                }
                a { color: inherit; text-decoration-thickness: 0.08em; }
              </style>
            </head>
            <body data-search="${searchQuery.escapeHtml()}"$navigationAttributes>
              $body
              <div id="reader-selection-menu" role="toolbar" aria-label="Selection actions">
                <button type="button" data-action="copy">Copy</button>
                $highlightButtons
                $defineButton
                $speakButton
                <button type="button" data-action="dictionary">Dict</button>
                <button type="button" data-action="find">Find</button>
                <button type="button" data-action="web-search">Web</button>
                <button type="button" data-action="translate">Translate</button>
                <button type="button" data-action="clear">Clear</button>
              </div>
              <script>
                (function () {
                  var menu = document.getElementById('reader-selection-menu');
                  var savedRange = null;
                  var readerPageAnchors = $pageAnchorJson;
                  var lastReportedPageIndex = -1;
                  var lastReportedStartOffset = -1;
                  var reportTimer = null;
                  function numberAttribute(element, name, fallback) {
                    if (!element) return fallback;
                    var value = parseInt(element.getAttribute(name) || '', 10);
                    return Number.isFinite(value) ? value : fallback;
                  }
                  function selectorValue(value) {
                    if (window.CSS && window.CSS.escape) return window.CSS.escape(String(value));
                    return String(value).replace(/"/g, '\\"');
                  }
                  function readerTtsLog(message) {
                    var line = 'EPUB_TTS_HIGHLIGHT ' + message;
                    try { console.log(line); } catch (error) {}
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try { window.kmpJsBridge.callNative('readerTtsHighlightLog', JSON.stringify({ message: line })); } catch (error) {}
                    }
                  }
                  window.readerTtsLog = readerTtsLog;
                  function readerTtsPreview(value, limit) {
                    return String(value || '').replace(/\s+/g, ' ').trim().substring(0, limit || 120);
                  }
                  function readerTtsNormalized(value) {
                    return String(value || '').replace(/\s+/g, ' ').trim();
                  }
                  function readerElementLabel(element) {
                    if (!element || !element.tagName) return 'null';
                    var label = element.tagName.toLowerCase();
                    if (element.id) label += '#' + element.id;
                    var cfi = element.getAttribute && element.getAttribute('data-reader-cfi');
                    var start = element.getAttribute && element.getAttribute('data-reader-text-start');
                    var end = element.getAttribute && element.getAttribute('data-reader-text-end');
                    if (cfi) label += '[cfi=' + readerTtsPreview(cfi, 80) + ']';
                    if (start !== null && end !== null) label += '[range=' + start + '..' + end + ']';
                    return label;
                  }
                  function scrollToLocator(locator) {
                    locator = locator || {};
                    var chapterIndex = locator.chapterIndex;
                    if (chapterIndex === undefined || chapterIndex === null || chapterIndex === '') {
                      chapterIndex = document.body.getAttribute('data-reader-active-chapter-index');
                    }
                    if (chapterIndex === null || chapterIndex === '') return;
                    var chapter = document.querySelector('[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]');
                    if (!chapter) return;
                    var activeStart = locator.startOffset;
                    if (activeStart === undefined || activeStart === null) {
                      activeStart = numberAttribute(document.body, 'data-reader-active-start-offset', null);
                    }
                    var exact = activeStart === null
                      ? null
                      : chapter.querySelector('[data-reader-start-offset="' + selectorValue(activeStart) + '"]');
                    var target = exact || chapter;
                    var content = chapter.querySelector('.reader-content') || chapter;
                    if (!exact && activeStart !== null && content) {
                      var parsedStart = parseInt(activeStart, 10);
                      var parsedEnd = parseInt(locator.endOffset === undefined || locator.endOffset === null ? activeStart : locator.endOffset, 10);
                      if (Number.isFinite(parsedStart)) {
                        var rangeEnd = Number.isFinite(parsedEnd) && parsedEnd > parsedStart ? parsedEnd : parsedStart + 1;
                        var exactRange = rangeForOffsets(parseInt(chapterIndex, 10), parsedStart, rangeEnd, locator.cfi);
                        if (exactRange) {
                          var rangeRects = exactRange.getClientRects();
                          var rangeRect = rangeRects.length ? rangeRects[0] : exactRange.getBoundingClientRect();
                          exactRange.detach && exactRange.detach();
                          if (rangeRect && (rangeRect.top !== 0 || rangeRect.bottom !== 0)) {
                            window.scrollTo({ top: Math.max(0, rangeRect.top + window.scrollY - 24), left: 0, behavior: 'auto' });
                            return;
                          }
                        }
                      }
                      var contentStart = numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0));
                      var contentEnd = numberAttribute(content, 'data-reader-content-end', numberAttribute(chapter, 'data-reader-page-end', contentStart));
                      if (contentEnd > contentStart && activeStart > contentStart) {
                        var ratio = Math.max(0, Math.min(1, (activeStart - contentStart) / (contentEnd - contentStart)));
                        var contentRect = content.getBoundingClientRect();
                        var approximateY = contentRect.top + window.scrollY + (content.scrollHeight * ratio);
                        window.scrollTo({ top: Math.max(0, approximateY - 24), left: 0, behavior: 'auto' });
                        return;
                      }
                    }
                    var rect = target.getBoundingClientRect();
                    window.scrollTo({ top: Math.max(0, rect.top + window.scrollY - 24), left: 0, behavior: 'auto' });
                  }
                  function scrollToActiveLocator() {
                    scrollToLocator({
                      chapterIndex: document.body.getAttribute('data-reader-active-chapter-index'),
                      startOffset: numberAttribute(document.body, 'data-reader-active-start-offset', null)
                    });
                  }
                  window.readerScrollToLocator = scrollToLocator;
                  var readerAutoScrollFrame = null;
                  var readerAutoScrollLastTime = 0;
                  var readerAutoScrollSpeed = 36;
                  function readerAutoScrollStep(timestamp) {
                    if (readerAutoScrollFrame === null) return;
                    if (!readerAutoScrollLastTime) readerAutoScrollLastTime = timestamp;
                    var elapsed = Math.max(0, timestamp - readerAutoScrollLastTime);
                    readerAutoScrollLastTime = timestamp;
                    window.scrollBy(0, readerAutoScrollSpeed * elapsed / 1000);
                    if (window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2) {
                      window.readerAutoScroll.stop();
                      return;
                    }
                    readerAutoScrollFrame = window.requestAnimationFrame(readerAutoScrollStep);
                  }
                  window.readerAutoScroll = {
                    start: function (speed) {
                      readerAutoScrollSpeed = Math.max(12, Math.min(160, Number(speed) || 36));
                      if (readerAutoScrollFrame !== null) window.cancelAnimationFrame(readerAutoScrollFrame);
                      readerAutoScrollLastTime = 0;
                      readerAutoScrollFrame = window.requestAnimationFrame(readerAutoScrollStep);
                    },
                    stop: function () {
                      if (readerAutoScrollFrame !== null) window.cancelAnimationFrame(readerAutoScrollFrame);
                      readerAutoScrollFrame = null;
                      readerAutoScrollLastTime = 0;
                    }
                  };
                  function textNodesUnder(root, includeWhitespace) {
                    includeWhitespace = includeWhitespace === undefined ? true : includeWhitespace;
                    var nodes = [];
                    var walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
                      acceptNode: function (node) {
                        if (!node.nodeValue) return NodeFilter.FILTER_REJECT;
                        var parent = node.parentElement;
                        if (parent && parent.closest && parent.closest('#reader-selection-menu')) return NodeFilter.FILTER_REJECT;
                        if (parent && parent.closest && parent.closest('script, style')) return NodeFilter.FILTER_REJECT;
                        if (!includeWhitespace && node.nodeValue.trim().length === 0) return NodeFilter.FILTER_REJECT;
                        return NodeFilter.FILTER_ACCEPT;
                      }
                    });
                    var node;
                    while ((node = walker.nextNode())) nodes.push(node);
                    return nodes;
                  }
                  function normalizedRangeForText(root, expectedText, debugTts) {
                    var expected = readerTtsNormalized(expectedText);
                    if (!root || !expected) return null;
                    var nodes = textNodesUnder(root, true);
                    var flatText = '';
                    var flatMap = [];
                    var sawText = false;
                    var pendingWhitespace = false;
                    nodes.forEach(function (node) {
                      var value = node.nodeValue || '';
                      for (var i = 0; i < value.length; i++) {
                        if (/^\s$/.test(value[i])) {
                          if (sawText) pendingWhitespace = true;
                          continue;
                        }
                        if (pendingWhitespace && flatText.length > 0) {
                          flatText += ' ';
                          flatMap.push({ node: node, start: i, end: i });
                          pendingWhitespace = false;
                        }
                        flatText += value[i];
                        flatMap.push({ node: node, start: i, end: i + 1 });
                        sawText = true;
                      }
                    });
                    var index = flatText.indexOf(expected);
                    if (debugTts) {
                      readerTtsLog(
                        'range_text_search root=' + readerElementLabel(root) +
                        ' expectedChars=' + expected.length +
                        ' flatChars=' + flatText.length +
                        ' index=' + index +
                        ' expected="' + readerTtsPreview(expected, 120) + '"'
                      );
                    }
                    if (index < 0 || index >= flatMap.length) return null;
                    var endIndex = Math.min(flatMap.length - 1, index + expected.length - 1);
                    var startMap = flatMap[index];
                    var endMap = flatMap[endIndex];
                    if (!startMap || !endMap) return null;
                    var range = document.createRange();
                    try {
                      range.setStart(startMap.node, startMap.start);
                      range.setEnd(endMap.node, endMap.end);
                    } catch (error) {
                      range.detach && range.detach();
                      if (debugTts) readerTtsLog('range_text_search_failed reason=set_range_error error=' + readerTtsPreview(error, 140));
                      return null;
                    }
                    return range;
                  }
                  function firstVisibleOffsetInContent(content) {
                    var nodes = textNodesUnder(content, false);
                    var contentStart = numberAttribute(content, 'data-reader-content-start', 0);
                    var offset = contentStart;
                    var viewportTop = Math.max(8, window.innerHeight * 0.08);
                    var viewportBottom = window.innerHeight - 8;
                    for (var n = 0; n < nodes.length; n++) {
                      var node = nodes[n];
                      var text = node.nodeValue || '';
                      var whole = document.createRange();
                      whole.selectNodeContents(node);
                      var rects = whole.getClientRects();
                      whole.detach && whole.detach();
                      var visible = false;
                      for (var r = 0; r < rects.length; r++) {
                        if (rects[r].bottom >= viewportTop && rects[r].top <= viewportBottom) {
                          visible = true;
                          break;
                        }
                      }
                      if (!visible) {
                        offset += text.length;
                        continue;
                      }
                      for (var i = 0; i < text.length; i++) {
                        if (!text[i] || /^\s$/.test(text[i])) continue;
                        var charRange = document.createRange();
                        charRange.setStart(node, i);
                        charRange.setEnd(node, Math.min(i + 1, text.length));
                        var charRect = charRange.getBoundingClientRect();
                        charRange.detach && charRange.detach();
                        if (charRect.bottom >= viewportTop && charRect.top <= viewportBottom) {
                          return { offset: offset + i, textNode: node };
                        }
                      }
                      offset += text.length;
                    }
                    return { offset: contentStart, textNode: null };
                  }
                  function snippetFromContentOffset(content, startOffset) {
                    var nodes = textNodesUnder(content, false);
                    var contentStart = numberAttribute(content, 'data-reader-content-start', 0);
                    var remaining = Math.max(0, startOffset - contentStart);
                    var text = '';
                    for (var n = 0; n < nodes.length; n++) {
                      var value = nodes[n].nodeValue || '';
                      if (remaining >= value.length) {
                        remaining -= value.length;
                        continue;
                      }
                      text += value.substring(remaining);
                      remaining = 0;
                      if (text.length >= 160) break;
                    }
                    return text.replace(/\s+/g, ' ').trim().substring(0, 140);
                  }
                  function pageForLocator(chapterIndex, offset) {
                    if (!readerPageAnchors.length) return null;
                    var sameChapter = readerPageAnchors.filter(function (page) { return page.chapterIndex === chapterIndex; });
                    if (!sameChapter.length) return null;
                    var best = sameChapter[0];
                    for (var p = 0; p < sameChapter.length; p++) {
                      var page = sameChapter[p];
                      if (offset >= page.startOffset && offset < page.endOffset) return page;
                      if (Math.abs(page.startOffset - offset) < Math.abs(best.startOffset - offset)) best = page;
                    }
                    return best;
                  }
                  function currentVisiblePosition() {
                    var chapters = document.querySelectorAll('[data-reader-chapter-index]');
                    for (var i = 0; i < chapters.length; i++) {
                      var chapter = chapters[i];
                      var rect = chapter.getBoundingClientRect();
                      if (rect.bottom < 8 || rect.top > window.innerHeight - 8) continue;
                      var content = chapter.querySelector('.reader-content') || chapter;
                      var chapterIndex = numberAttribute(chapter, 'data-reader-chapter-index', 0);
                      var visible = firstVisibleOffsetInContent(content);
                      var page = pageForLocator(chapterIndex, visible.offset) || readerPageAnchors[0];
                      if (!page) return null;
                      return {
                        pageIndex: page.pageIndex,
                        chapterIndex: chapterIndex,
                        startOffset: visible.offset,
                        endOffset: visible.offset,
                        textQuote: snippetFromContentOffset(content, visible.offset),
                        cfi: 'desktop:' + chapterIndex + ':' + visible.offset + ':' + visible.offset
                      };
                    }
                    return null;
                  }
                  function reportVisiblePage() {
                    var position = currentVisiblePosition();
                    if (!position) return;
                    if (position.pageIndex === lastReportedPageIndex && Math.abs(position.startOffset - lastReportedStartOffset) < 8) return;
                    lastReportedPageIndex = position.pageIndex;
                    lastReportedStartOffset = position.startOffset;
                    if (window.kmpJsBridge) {
                      window.kmpJsBridge.callNative('readerPositionChanged', JSON.stringify(position));
                    }
                  }
                  function nearestReaderHost(element) {
                    return element && element.closest ? element.closest('[data-reader-chapter-index]') : null;
                  }
                  function fallbackReaderLinkNavigation(payload, reason) {
                    try {
                      var encoded = encodeURIComponent(JSON.stringify(payload));
                      console.log('READER_LINK fallback_navigation href=' + payload.href + ' reason=' + reason);
                      window.location.href = 'readerlink://click?payload=' + encoded;
                    } catch (error) {
                      console.log('READER_LINK fallback_navigation_error href=' + payload.href + ' error=' + error);
                    }
                  }
                  function sendReaderLinkClick(payload, attempt) {
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerLinkClicked', JSON.stringify(payload));
                        console.log('READER_LINK bridge_sent href=' + payload.href + ' attempt=' + attempt);
                        window.setTimeout(function () {
                          fallbackReaderLinkNavigation(payload, 'post_bridge');
                        }, 260);
                        return true;
                      } catch (error) {
                        console.log('READER_LINK bridge_error href=' + payload.href + ' attempt=' + attempt + ' error=' + error);
                      }
                    } else {
                      console.log('READER_LINK bridge_missing href=' + payload.href + ' attempt=' + attempt);
                    }
                    if (attempt < 3) {
                      window.setTimeout(function () {
                        sendReaderLinkClick(payload, attempt + 1);
                      }, attempt === 0 ? 60 : 220);
                      return true;
                    }
                    console.log('READER_LINK bridge_gave_up href=' + payload.href);
                    fallbackReaderLinkNavigation(payload, 'bridge_gave_up');
                    return false;
                  }
                  document.addEventListener('click', function (event) {
                    var target = event.target;
                    if (!target || !target.closest) return;
                    var anchor = target.closest('a[href]');
                    if (!anchor || menu.contains(anchor)) return;
                    var href = anchor.getAttribute('href') || '';
                    if (!href) return;
                    var readerHost = nearestReaderHost(anchor);
                    event.preventDefault();
                    event.stopPropagation();
                    sendReaderLinkClick({
                      href: href,
                      text: (anchor.textContent || '').trim().substring(0, 120),
                      chapterIndex: readerHost ? numberAttribute(readerHost, 'data-reader-chapter-index', null) : null,
                      chapterId: readerHost ? readerHost.getAttribute('data-reader-chapter-id') : null,
                      chapterHref: readerHost ? readerHost.getAttribute('data-reader-chapter-href') : null
                    }, 0);
                  }, true);
                  function scheduleVisiblePageReport() {
                    if (reportTimer !== null) window.clearTimeout(reportTimer);
                    reportTimer = window.setTimeout(function () {
                      reportTimer = null;
                      reportVisiblePage();
                    }, 140);
                  }
                  function selectionText() {
                    var selection = window.getSelection();
                    return selection ? selection.toString().trim() : '';
                  }
                  function hideMenu() {
                    menu.style.display = 'none';
                  }
                  function showMenu(event) {
                    var selection = window.getSelection();
                    if (!selection || selection.rangeCount === 0 || selectionText().length === 0) {
                      hideMenu();
                      return;
                    }
                    savedRange = selection.getRangeAt(0).cloneRange();
                    if (savedRange.collapsed) {
                      hideMenu();
                      return;
                    }
                    menu.style.left = Math.max(8, Math.min(window.innerWidth - 360, event.clientX)) + 'px';
                    menu.style.top = Math.max(8, Math.min(window.innerHeight - 54, event.clientY)) + 'px';
                    menu.style.display = 'flex';
                  }
                  function restoreRange() {
                    if (!savedRange) return false;
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    selection.addRange(savedRange);
                    return true;
                  }
                  function copyText(text) {
                    if (navigator.clipboard && navigator.clipboard.writeText) {
                      navigator.clipboard.writeText(text);
                      return;
                    }
                    var textarea = document.createElement('textarea');
                    textarea.value = text;
                    textarea.setAttribute('readonly', 'true');
                    textarea.style.position = 'fixed';
                    textarea.style.left = '-9999px';
                    document.body.appendChild(textarea);
                    textarea.select();
                    document.execCommand('copy');
                    document.body.removeChild(textarea);
                  }
                  function fallbackSelectionAction(action, text) {
                    if (action === 'translate') {
                      window.open('https://translate.google.com/?sl=auto&tl=en&text=' + encodeURIComponent(text) + '&op=translate', '_blank');
                    } else if (action === 'dictionary') {
                      window.open('https://www.google.com/search?q=define+' + encodeURIComponent(text), '_blank');
                    } else if (action === 'web-search') {
                      window.open('https://www.google.com/search?q=' + encodeURIComponent(text), '_blank');
                    }
                  }
                  function sendSelectionAction(action, text) {
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerSelectionAction', JSON.stringify({
                          action: action,
                          text: text
                        }));
                        return true;
                      } catch (error) {
                        console.log('READER_SELECTION_ACTION bridge_error action=' + action + ' error=' + error);
                      }
                    }
                    fallbackSelectionAction(action, text);
                    return false;
                  }
                  function selectionOffsetsWithin(host, range) {
                    var rawStart = offsetForBoundary(host, range.startContainer, range.startOffset);
                    var rawEnd = offsetForBoundary(host, range.endContainer, range.endOffset);
                    if (rawStart === null || rawEnd === null || rawEnd < rawStart) {
                      return { start: null, end: null };
                    }
                    var selectedText = textBetweenOffsets(host, rawStart, rawEnd);
                    var trimmedText = selectedText.trim();
                    if (!trimmedText) return { start: null, end: null };
                    var leadingWhitespace = selectedText.length - selectedText.replace(/^\s+/, '').length;
                    var trailingWhitespace = selectedText.length - selectedText.replace(/\s+$/, '').length;
                    return { start: rawStart + leadingWhitespace, end: rawEnd - trailingWhitespace };
                  }
                  function offsetForBoundary(host, container, offset) {
                    var includeWhitespace = host && host.getAttribute && host.hasAttribute('data-reader-text-start');
                    var nodes = textNodesUnder(host, includeWhitespace);
                    var boundary = document.createRange();
                    try {
                      boundary.setStart(container, offset);
                      boundary.collapse(true);
                    } catch (error) {
                      boundary.detach && boundary.detach();
                      return null;
                    }
                    var cursor = 0;
                    for (var n = 0; n < nodes.length; n++) {
                      var node = nodes[n];
                      var length = (node.nodeValue || '').length;
                      if (node === container) {
                        boundary.detach && boundary.detach();
                        return cursor + Math.max(0, Math.min(length, offset));
                      }
                      var nodeRange = document.createRange();
                      nodeRange.selectNodeContents(node);
                      var nodeEndsBeforeBoundary = nodeRange.compareBoundaryPoints(Range.END_TO_START, boundary) <= 0;
                      nodeRange.detach && nodeRange.detach();
                      if (nodeEndsBeforeBoundary) {
                        cursor += length;
                      } else {
                        boundary.detach && boundary.detach();
                        return cursor;
                      }
                    }
                    boundary.detach && boundary.detach();
                    return cursor;
                  }
                  function textBetweenOffsets(host, startOffset, endOffset) {
                    var includeWhitespace = host && host.getAttribute && host.hasAttribute('data-reader-text-start');
                    var nodes = textNodesUnder(host, includeWhitespace);
                    var cursor = 0;
                    var text = '';
                    for (var n = 0; n < nodes.length; n++) {
                      var value = nodes[n].nodeValue || '';
                      var next = cursor + value.length;
                      if (next <= startOffset) {
                        cursor = next;
                        continue;
                      }
                      if (cursor >= endOffset) break;
                      var startInNode = Math.max(0, startOffset - cursor);
                      var endInNode = Math.min(value.length, endOffset - cursor);
                      if (endInNode > startInNode) text += value.substring(startInNode, endInNode);
                      cursor = next;
                    }
                    return text;
                  }
                  function unwrapReaderHighlights() {
                    var marks = Array.prototype.slice.call(document.querySelectorAll('mark.reader-user-highlight'));
                    marks.forEach(function (mark) {
                      var parent = mark.parentNode;
                      if (!parent) return;
                      while (mark.firstChild) parent.insertBefore(mark.firstChild, mark);
                      parent.removeChild(mark);
                      parent.normalize();
                    });
                  }
                  function rangeForOffsets(chapterIndex, startOffset, endOffset, sourceCfi, debugTts) {
                    debugTts = debugTts === true;
                    function ttsRangeLog(message) {
                      if (debugTts) readerTtsLog(message);
                    }
                    var chapter = document.querySelector('[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]');
                    if (!chapter) {
                      ttsRangeLog('range_failed reason=missing_chapter chapter=' + chapterIndex + ' offsets=' + startOffset + '..' + endOffset + ' cfi=' + readerTtsPreview(sourceCfi, 100));
                      return null;
                    }
                    var content = chapter.querySelector('.reader-content') || chapter;
                    var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end]'));
                    ttsRangeLog(
                      'range_start chapter=' + chapterIndex +
                      ' offsets=' + startOffset + '..' + endOffset +
                      ' cfi=' + readerTtsPreview(sourceCfi, 100) +
                      ' hosts=' + hosts.length +
                      ' contentRange=' + numberAttribute(content, 'data-reader-content-start', 'null') + '..' + numberAttribute(content, 'data-reader-content-end', 'null') +
                      ' pageRange=' + numberAttribute(chapter, 'data-reader-page-start', 'null') + '..' + numberAttribute(chapter, 'data-reader-page-end', 'null')
                    );
                    function readerTextBlock(node) {
                      var parent = node && node.parentElement;
                      return parent && parent.closest
                        ? parent.closest('p, li, blockquote, pre, h1, h2, h3, h4, h5, h6, td, th, figcaption, div, section')
                        : null;
                    }
                    function textHostForOffset(offset, preferEnd) {
                      if (sourceCfi) {
                        var cfiHosts = hosts.filter(function (host) {
                          return host.getAttribute && host.getAttribute('data-reader-cfi') === sourceCfi;
                        });
                        var cfiBest = null;
                        var cfiBestSpan = Number.MAX_SAFE_INTEGER;
                        cfiHosts.forEach(function (host) {
                          var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                          var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                          if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                          var contains = preferEnd
                            ? offset > hostStart && offset <= hostEnd
                            : offset >= hostStart && offset < hostEnd;
                          if (!contains) return;
                          var span = hostEnd - hostStart;
                          if (span < cfiBestSpan) {
                            cfiBest = host;
                            cfiBestSpan = span;
                          }
                        });
                        if (cfiBest) {
                          ttsRangeLog('range_host_cfi_match offset=' + offset + ' preferEnd=' + preferEnd + ' host=' + readerElementLabel(cfiBest));
                          return cfiBest;
                        }
                        if (cfiHosts.length > 0) {
                          ttsRangeLog(
                            'range_cfi_hosts_no_offset_match offset=' + offset +
                            ' preferEnd=' + preferEnd +
                            ' cfiHosts=' + cfiHosts.length +
                            ' firstHost=' + readerElementLabel(cfiHosts[0])
                          );
                        } else {
                          ttsRangeLog('range_cfi_host_missing cfi=' + readerTtsPreview(sourceCfi, 100) + ' hostCount=' + hosts.length);
                        }
                      }
                      var best = null;
                      var bestSpan = Number.MAX_SAFE_INTEGER;
                      hosts.forEach(function (host) {
                        var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                        var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                        if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                        var contains = preferEnd
                          ? offset > hostStart && offset <= hostEnd
                          : offset >= hostStart && offset < hostEnd;
                        if (!contains && offset === hostStart && offset === hostEnd) contains = true;
                        if (!contains) return;
                        var span = hostEnd - hostStart;
                        if (span < bestSpan) {
                          best = host;
                          bestSpan = span;
                        }
                      });
                      if (best) return best;
                      var fallback = null;
                      var fallbackDistance = Number.MAX_SAFE_INTEGER;
                      hosts.forEach(function (host) {
                        var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                        var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                        if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                        var distance = preferEnd
                          ? Math.abs(offset - hostEnd)
                          : Math.abs(offset - hostStart);
                        if (distance < fallbackDistance) {
                          fallback = host;
                          fallbackDistance = distance;
                        }
                      });
                      return fallback || content;
                    }
                    function boundaryForOffset(offset, preferEnd) {
                      var host = textHostForOffset(offset, preferEnd);
                      var hasExplicitTextOffsets = host && host.getAttribute && host.hasAttribute('data-reader-text-start');
                      var nodes = textNodesUnder(host, true);
                      var cursor = numberAttribute(
                        host,
                        'data-reader-text-start',
                        numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0))
                      );
                      if (nodes.length === 0) {
                        ttsRangeLog('boundary_failed reason=no_nodes offset=' + offset + ' preferEnd=' + preferEnd + ' host=' + readerElementLabel(host));
                        return null;
                      }
                      if (!hasExplicitTextOffsets) {
                        var normalizedTarget = Math.max(0, offset - cursor);
                        var normalizedCursor = 0;
                        var sawText = false;
                        var inWhitespace = false;
                        var lastBoundary = { node: nodes[0], offset: 0 };
                        var previousBlock = null;
                        for (var nn = 0; nn < nodes.length; nn++) {
                          var value = nodes[nn].nodeValue || '';
                          var currentBlock = readerTextBlock(nodes[nn]);
                          if (previousBlock && currentBlock && currentBlock !== previousBlock && sawText && !inWhitespace) {
                            if (normalizedCursor >= normalizedTarget) return { node: nodes[nn], offset: 0 };
                            inWhitespace = true;
                            normalizedCursor += 1;
                            if (normalizedCursor >= normalizedTarget) return { node: nodes[nn], offset: 0 };
                          }
                          if (currentBlock) previousBlock = currentBlock;
                          for (var ii = 0; ii < value.length; ii++) {
                            var before = { node: nodes[nn], offset: ii };
                            var after = { node: nodes[nn], offset: ii + 1 };
                            var isWhitespace = /^\s$/.test(value[ii]);
                            if (isWhitespace) {
                              lastBoundary = after;
                              if (!sawText) continue;
                              if (!inWhitespace) {
                                if (normalizedCursor >= normalizedTarget) return before;
                                inWhitespace = true;
                                normalizedCursor += 1;
                              }
                              continue;
                            }
                            if (inWhitespace) {
                              if (normalizedCursor >= normalizedTarget) return before;
                              inWhitespace = false;
                            }
                            if (normalizedCursor >= normalizedTarget) return before;
                            sawText = true;
                            normalizedCursor += 1;
                            lastBoundary = after;
                            if (normalizedCursor >= normalizedTarget) return after;
                          }
                        }
                        return lastBoundary;
                      }
                      for (var n = 0; n < nodes.length; n++) {
                        var node = nodes[n];
                        var length = (node.nodeValue || '').length;
                        var next = cursor + length;
                        var contains = preferEnd ? offset >= cursor && offset <= next : offset >= cursor && offset < next;
                        if (contains || (n === nodes.length - 1 && offset >= next)) {
                          return {
                            node: node,
                            offset: Math.max(0, Math.min(length, offset - cursor))
                          };
                        }
                        cursor = next;
                      }
                      var last = nodes[nodes.length - 1];
                      return { node: last, offset: (last.nodeValue || '').length };
                    }
                    var startBoundary = boundaryForOffset(startOffset, false);
                    var endBoundary = boundaryForOffset(endOffset, true);
                    if (!startBoundary || !endBoundary) {
                      ttsRangeLog(
                        'range_failed reason=missing_boundary startBoundary=' + !!startBoundary +
                        ' endBoundary=' + !!endBoundary +
                        ' offsets=' + startOffset + '..' + endOffset +
                        ' cfi=' + readerTtsPreview(sourceCfi, 100)
                      );
                      return null;
                    }
                    var range = document.createRange();
                    try {
                      range.setStart(startBoundary.node, startBoundary.offset);
                      range.setEnd(endBoundary.node, endBoundary.offset);
                    } catch (error) {
                      range.detach && range.detach();
                      ttsRangeLog(
                        'range_failed reason=set_range_error error=' + readerTtsPreview(error, 140) +
                        ' startHost=' + readerElementLabel(startBoundary.node && startBoundary.node.parentElement) +
                        ' endHost=' + readerElementLabel(endBoundary.node && endBoundary.node.parentElement)
                      );
                      return null;
                    }
                    ttsRangeLog(
                      'range_success text="' + readerTtsPreview(range.toString(), 140) +
                      '" startHost=' + readerElementLabel(startBoundary.node && startBoundary.node.parentElement) +
                      ' endHost=' + readerElementLabel(endBoundary.node && endBoundary.node.parentElement)
                    );
                    return range;
                  }
                  function applyHighlightObject(highlight) {
                    if (!highlight) return;
                    var locator = highlight.locator || {};
                    var chapterIndex = locator.chapterIndex;
                    if (chapterIndex === undefined || chapterIndex === null) chapterIndex = highlight.chapterIndex;
                    var startOffset = locator.startOffset;
                    var endOffset = locator.endOffset;
                    if (chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) return;
                    var range = rangeForOffsets(chapterIndex, startOffset, endOffset);
                    if (!range || range.collapsed) return;
                    var marker = document.createElement('mark');
                    marker.className = 'reader-user-highlight user-highlight-' + (highlight.colorId || 'yellow');
                    if (highlight.id) marker.setAttribute('data-reader-highlight-id', highlight.id);
                    marker.setAttribute('data-reader-start-offset', String(startOffset));
                    marker.setAttribute('data-reader-end-offset', String(endOffset));
                    try {
                      range.surroundContents(marker);
                    } catch (error) {
                      marker.appendChild(range.extractContents());
                      range.insertNode(marker);
                    }
                  }
                  window.readerApplyHighlights = function (highlights) {
                    var previousX = window.scrollX;
                    var previousY = window.scrollY;
                    unwrapReaderHighlights();
                    if (Array.isArray(highlights)) {
                      highlights
                        .slice()
                        .sort(function (a, b) {
                          var aStart = (a.locator && a.locator.startOffset) || 0;
                          var bStart = (b.locator && b.locator.startOffset) || 0;
                          return bStart - aStart;
                        })
                        .forEach(applyHighlightObject);
                    }
                    window.scrollTo({ top: previousY, left: previousX, behavior: 'auto' });
                  };
                  var readerTtsLocator = null;
                  var readerTtsOverlayTimer = null;
                  function ensureTtsLayer() {
                    var layer = document.getElementById('reader-tts-highlight-layer');
                    if (!layer) {
                      layer = document.createElement('div');
                      layer.id = 'reader-tts-highlight-layer';
                      document.body.appendChild(layer);
                    }
                    return layer;
                  }
                  function clearTtsHighlight() {
                    if (window.CSS && CSS.highlights && CSS.highlights.delete) {
                      CSS.highlights.delete('reader-tts-highlight');
                    }
                    var layer = document.getElementById('reader-tts-highlight-layer');
                    if (layer) layer.innerHTML = '';
                  }
                  function paintTtsOverlay(range) {
                    var layer = ensureTtsLayer();
                    layer.innerHTML = '';
                    var rects = Array.prototype.slice.call(range.getClientRects());
                    var painted = 0;
                    rects.forEach(function (rect) {
                      if (!rect || rect.width <= 0 || rect.height <= 0) return;
                      var marker = document.createElement('div');
                      marker.className = 'reader-tts-highlight-rect';
                      marker.style.left = (rect.left + window.scrollX) + 'px';
                      marker.style.top = (rect.top + window.scrollY) + 'px';
                      marker.style.width = rect.width + 'px';
                      marker.style.height = rect.height + 'px';
                      layer.appendChild(marker);
                      painted++;
                    });
                    readerTtsLog('overlay_paint rects=' + rects.length + ' painted=' + painted);
                  }
                  function applyTtsLocator(locator) {
                    clearTtsHighlight();
                    readerTtsLocator = locator || null;
                    if (!readerTtsLocator) {
                      readerTtsLog('locator_clear');
                      return;
                    }
                    var chapterIndex = readerTtsLocator.chapterIndex;
                    var startOffset = readerTtsLocator.startOffset;
                    var endOffset = readerTtsLocator.endOffset;
                    var sourceCfi = readerTtsLocator.cfi;
                    readerTtsLog(
                      'locator_apply chapter=' + chapterIndex +
                      ' page=' + readerTtsLocator.pageIndex +
                      ' offsets=' + startOffset + '..' + endOffset +
                      ' cfi=' + readerTtsPreview(sourceCfi, 100) +
                      ' expected="' + readerTtsPreview(readerTtsLocator.textQuote, 140) + '"'
                    );
                    if (chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) {
                      readerTtsLog('locator_ignored reason=invalid_locator');
                      return;
                    }
                    var range = rangeForOffsets(chapterIndex, startOffset, endOffset, sourceCfi, true);
                    var expectedText = readerTtsLocator.textQuote;
                    var expectedNormalized = readerTtsNormalized(expectedText);
                    var actualNormalized = range && !range.collapsed ? readerTtsNormalized(range.toString()) : '';
                    if (expectedNormalized) {
                      readerTtsLog(
                        'range_expected_compare expectedChars=' + expectedNormalized.length +
                        ' actualChars=' + actualNormalized.length +
                        ' exact=' + (actualNormalized === expectedNormalized) +
                        ' actual="' + readerTtsPreview(actualNormalized, 140) + '"'
                      );
                    }
                    if (expectedNormalized && (!range || range.collapsed || actualNormalized !== expectedNormalized)) {
                      var chapter = document.querySelector('[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]');
                      var content = chapter ? (chapter.querySelector('.reader-content') || chapter) : null;
                      var searchRoot = content;
                      if (content && sourceCfi) {
                        searchRoot = content.querySelector('[data-reader-cfi="' + selectorValue(sourceCfi) + '"]') || content;
                      }
                      var textRange = normalizedRangeForText(searchRoot, expectedNormalized, true);
                      if (textRange && !textRange.collapsed) {
                        if (range && range.detach) range.detach();
                        range = textRange;
                        readerTtsLog('range_expected_fallback used=true root=' + readerElementLabel(searchRoot));
                      } else {
                        readerTtsLog('range_expected_fallback used=false root=' + readerElementLabel(searchRoot));
                      }
                    }
                    if (!range || range.collapsed) {
                      readerTtsLog('locator_failed reason=' + (!range ? 'no_range' : 'collapsed_range'));
                      return;
                    }
                    if (window.CSS && window.Highlight && CSS.highlights && CSS.highlights.set) {
                      CSS.highlights.set('reader-tts-highlight', new Highlight(range));
                      readerTtsLog('css_highlight_set supported=true');
                    } else {
                      readerTtsLog('css_highlight_set supported=false');
                    }
                    paintTtsOverlay(range);
                  }
                  window.readerSetTtsLocator = function (locator, follow) {
                    try {
                      applyTtsLocator(locator);
                      if (follow && locator) scrollToLocator(locator);
                    } catch (error) {
                      readerTtsLog('locator_exception error=' + readerTtsPreview(error, 180));
                    }
                  };
                  function refreshTtsHighlight() {
                    if (!readerTtsLocator) return;
                    applyTtsLocator(readerTtsLocator);
                  }
                  window.addEventListener('resize', function () {
                    if (readerTtsOverlayTimer !== null) window.clearTimeout(readerTtsOverlayTimer);
                    readerTtsOverlayTimer = window.setTimeout(refreshTtsHighlight, 80);
                  });
                  function highlightRange(colorId) {
                    if (!restoreRange()) return;
                    var selection = window.getSelection();
                    if (!selection || selection.rangeCount === 0) return;
                    var range = selection.getRangeAt(0);
                    var marker = document.createElement('mark');
                    marker.className = 'reader-user-highlight user-highlight-' + (colorId || 'yellow');
                    var container = range.commonAncestorContainer;
                    if (container && container.nodeType !== 1) container = container.parentElement;
                    var contentHost = container && container.closest ? container.closest('.reader-content') : null;
                    var textHost = container && container.closest ? container.closest('[data-reader-text-start]') : null;
                    var readerHost = contentHost && contentHost.closest
                      ? contentHost.closest('[data-reader-chapter-index]')
                      : (container && container.closest ? container.closest('[data-reader-chapter-index]') : null);
                    var chapterIndex = readerHost ? parseInt(readerHost.getAttribute('data-reader-chapter-index') || '0', 10) : 0;
                    var chapterId = readerHost ? readerHost.getAttribute('data-reader-chapter-id') : null;
                    var chapterHref = readerHost ? readerHost.getAttribute('data-reader-chapter-href') : null;
                    var pageIndex = readerHost ? parseInt(readerHost.getAttribute('data-reader-page-index') || '-1', 10) : -1;
                    var offsetHost = textHost || contentHost || readerHost;
                    var fallbackStart = readerHost ? readerHost.getAttribute('data-reader-page-start') : '0';
                    var pageStart = offsetHost ? parseInt(offsetHost.getAttribute('data-reader-text-start') || offsetHost.getAttribute('data-reader-content-start') || fallbackStart || '0', 10) : 0;
                    var offsets = offsetHost ? selectionOffsetsWithin(offsetHost, range) : { start: null, end: null };
                    var startOffset = offsets.start === null ? null : pageStart + offsets.start;
                    var endOffset = offsets.end === null ? null : pageStart + offsets.end;
                    var text = selection.toString().trim();
                    var cfi = startOffset === null || endOffset === null
                      ? 'desktop:' + chapterIndex + ':' + pageIndex + ':' + Date.now()
                      : 'desktop:' + chapterIndex + ':' + startOffset + ':' + endOffset;
                    if (startOffset !== null) marker.setAttribute('data-reader-start-offset', String(startOffset));
                    if (endOffset !== null) marker.setAttribute('data-reader-end-offset', String(endOffset));
                    if (window.kmpJsBridge && text.length > 0) {
                      window.kmpJsBridge.callNative('readerHighlightCreated', JSON.stringify({
                        cfi: cfi,
                        text: text,
                        colorId: colorId || 'yellow',
                        chapterIndex: chapterIndex,
                        locator: {
                          chapterIndex: chapterIndex,
                          chapterId: chapterId,
                          href: chapterHref || null,
                          pageIndex: pageIndex >= 0 ? pageIndex : null,
                          startOffset: startOffset,
                          endOffset: endOffset,
                          textQuote: text,
                          cfi: cfi
                        }
                      }));
                    }
                    try {
                      range.surroundContents(marker);
                    } catch (error) {
                      marker.appendChild(range.extractContents());
                      range.insertNode(marker);
                    }
                    selection.removeAllRanges();
                    hideMenu();
                  }
                  menu.addEventListener('mousedown', function (event) {
                    event.preventDefault();
                  });
                  menu.addEventListener('click', function (event) {
                    var action = event.target && event.target.getAttribute('data-action');
                    var text = selectionText();
                    if (!text && restoreRange()) text = selectionText();
                    if (!text) {
                      hideMenu();
                      return;
                    }
                    if (action === 'copy') copyText(text);
                    if (action === 'highlight') highlightRange(event.target.getAttribute('data-color-id') || 'yellow');
                    if (action === 'define') sendSelectionAction('define', text);
                    if (action === 'speak') sendSelectionAction('speak', text);
                    if (action === 'dictionary') sendSelectionAction('dictionary', text);
                    if (action === 'find') window.find(text);
                    if (action === 'web-search') sendSelectionAction('web-search', text);
                    if (action === 'translate') sendSelectionAction('translate', text);
                    if (action === 'clear') {
                      window.getSelection().removeAllRanges();
                      hideMenu();
                    }
                    if (action !== 'highlight' && action !== 'clear') hideMenu();
                  });
                  document.addEventListener('contextmenu', function (event) {
                    if (selectionText().length > 0) {
                      event.preventDefault();
                      showMenu(event);
                    }
                  });
                  document.addEventListener('scroll', hideMenu, true);
                  document.addEventListener('scroll', scheduleVisiblePageReport, true);
                  window.addEventListener('scroll', scheduleVisiblePageReport, { passive: true });
                  document.addEventListener('mousedown', function (event) {
                    if (event.button === 0 && !menu.contains(event.target)) hideMenu();
                  });
                  scrollToActiveLocator();
                  reportVisiblePage();
                  window.addEventListener('load', scrollToActiveLocator, { once: true });
                  window.addEventListener('load', reportVisiblePage, { once: true });
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun SharedEpubChapter.toHtml(searchQuery: String, searchOptions: ReaderSearchOptions): String {
        htmlContent.takeIf { it.isNotBlank() }?.let { return it }
        semanticBlocks.takeIf { it.isNotEmpty() }?.let { blocks ->
            return blocks.joinToString("") { it.toHtml(searchQuery, searchOptions) }
        }
        return normalizedReaderText().textToParagraphHtml(searchQuery, searchOptions)
    }

    private fun List<SemanticBlock>.blocksForPage(page: ReaderPage): List<SemanticBlock> {
        return mapIndexedNotNull { index, block ->
            block.clipToPage(page)
                ?: block.takeIf {
                    val previousText = asSequence()
                        .take(index)
                        .mapNotNull { it.lastTextBlock() }
                        .lastOrNull()
                    val nextText = asSequence()
                        .drop(index + 1)
                        .mapNotNull { it.firstTextBlock() }
                        .firstOrNull()
                    val anchor = previousText?.let { it.startCharOffsetInSource + it.text.length }
                        ?: nextText?.startCharOffsetInSource
                        ?: 0
                    anchor in page.startOffset..page.endOffset
                }
        }
    }

    private fun SemanticTextBlock.intersects(startOffset: Int, endOffset: Int): Boolean {
        val start = startCharOffsetInSource
        val end = start + text.length
        return start < endOffset && end > startOffset
    }

    private fun SemanticBlock.clipToPage(page: ReaderPage): SemanticBlock? {
        return when (this) {
            is SemanticTextBlock -> takeIf { intersects(page.startOffset, page.endOffset) }
            is SemanticList -> {
                val visibleItems = items.filter { it.intersects(page.startOffset, page.endOffset) }
                takeIf { visibleItems.isNotEmpty() }?.copy(items = visibleItems)
            }
            is SemanticTable -> {
                val visibleRows = rows.mapNotNull { row ->
                    val visibleCells = row.mapNotNull { cell ->
                        val visibleContent = cell.content.mapNotNull { it.clipToPage(page) }
                        cell.takeIf { visibleContent.isNotEmpty() }?.copy(content = visibleContent)
                    }
                    visibleCells.takeIf { it.isNotEmpty() }
                }
                takeIf { visibleRows.isNotEmpty() }?.copy(rows = visibleRows)
            }
            is SemanticFlexContainer -> {
                val visibleChildren = children.mapNotNull { it.clipToPage(page) }
                takeIf { visibleChildren.isNotEmpty() }?.copy(children = visibleChildren)
            }
            is SemanticWrappingBlock -> {
                val visibleParagraphs = paragraphsToWrap.filter { it.intersects(page.startOffset, page.endOffset) }
                takeIf { visibleParagraphs.isNotEmpty() }?.copy(paragraphsToWrap = visibleParagraphs)
            }
            else -> null
        }
    }

    private fun SemanticBlock.firstTextBlock(): SemanticTextBlock? {
        return when (this) {
            is SemanticTextBlock -> this
            is SemanticList -> items.firstOrNull()
            is SemanticTable -> rows.asSequence()
                .flatMap { it.asSequence() }
                .flatMap { it.content.asSequence() }
                .mapNotNull { it.firstTextBlock() }
                .firstOrNull()
            is SemanticFlexContainer -> children.asSequence().mapNotNull { it.firstTextBlock() }.firstOrNull()
            is SemanticWrappingBlock -> paragraphsToWrap.firstOrNull()
            else -> null
        }
    }

    private fun SemanticBlock.lastTextBlock(): SemanticTextBlock? {
        return when (this) {
            is SemanticTextBlock -> this
            is SemanticList -> items.lastOrNull()
            is SemanticTable -> rows.asReversed().asSequence()
                .flatMap { it.asReversed().asSequence() }
                .flatMap { it.content.asReversed().asSequence() }
                .mapNotNull { it.lastTextBlock() }
                .firstOrNull()
            is SemanticFlexContainer -> children.asReversed().asSequence().mapNotNull { it.lastTextBlock() }.firstOrNull()
            is SemanticWrappingBlock -> paragraphsToWrap.lastOrNull()
            else -> null
        }
    }

    private fun List<SemanticBlock>.blockSummary(): String {
        var textBlocks = 0
        var lists = 0
        var listItems = 0
        var tables = 0
        var tableCells = 0
        var flex = 0
        var images = 0
        var math = 0
        fun visit(block: SemanticBlock) {
            when (block) {
                is SemanticTextBlock -> textBlocks++
                is SemanticList -> {
                    lists++
                    listItems += block.items.size
                    block.items.forEach(::visit)
                }
                is SemanticTable -> {
                    tables++
                    tableCells += block.rows.sumOf { it.size }
                    block.rows.flatten().forEach { cell -> cell.content.forEach(::visit) }
                }
                is SemanticFlexContainer -> {
                    flex++
                    block.children.forEach(::visit)
                }
                is SemanticWrappingBlock -> {
                    images++
                    block.paragraphsToWrap.forEach(::visit)
                }
                is SemanticImage -> images++
                is SemanticMath -> math++
                else -> Unit
            }
        }
        forEach(::visit)
        return "text=$textBlocks lists=$lists items=$listItems tables=$tables cells=$tableCells flex=$flex images=$images math=$math"
    }

    private fun List<SemanticBlock>.styleSummary(): String {
        val fontSizes = mutableListOf<String>()
        val listStyles = mutableListOf<String>()
        val displayValues = mutableListOf<String>()
        fun collectStyle(style: CssStyle) {
            style.fontSize.toDiagnosticTextUnit()?.let { fontSizes += it }
            style.spanStyle.fontSize.toDiagnosticTextUnit()?.let { fontSizes += it }
            style.blockStyle.listStyleType?.takeIf { it.isNotBlank() }?.let { listStyles += "type=$it" }
            style.blockStyle.listStyleImage?.takeIf { it.isNotBlank() }?.let { listStyles += "image=$it" }
            style.display?.takeIf { it.isNotBlank() }?.let { displayValues += it }
            style.blockStyle.display?.takeIf { it.isNotBlank() }?.let { displayValues += it }
        }
        fun visit(block: SemanticBlock) {
            collectStyle(block.style)
            when (block) {
                is SemanticTextBlock -> block.spans.forEach { collectStyle(it.style) }
                is SemanticList -> block.items.forEach(::visit)
                is SemanticTable -> block.rows.flatten().forEach { cell ->
                    collectStyle(cell.style)
                    cell.content.forEach(::visit)
                }
                is SemanticFlexContainer -> block.children.forEach(::visit)
                is SemanticWrappingBlock -> {
                    visit(block.floatedImage)
                    block.paragraphsToWrap.forEach(::visit)
                }
                else -> Unit
            }
        }
        forEach(::visit)
        return "fontSizes=${fontSizes.distinct().take(12)} listStyles=${listStyles.distinct().take(12)} display=${displayValues.distinct().take(12)}"
    }

    private fun SemanticBlock.toHtml(searchQuery: String, searchOptions: ReaderSearchOptions): String {
        return when (this) {
            is SemanticHeader -> "<h${level.coerceIn(1, 6)}${textOffsetAttributes()}${styleAttribute()}>${textHtml(searchQuery, searchOptions)}</h${level.coerceIn(1, 6)}>"
            is SemanticParagraph -> "<p${textOffsetAttributes()}${styleAttribute()}>${textHtml(searchQuery, searchOptions)}</p>"
            is SemanticListItem -> "<li${textOffsetAttributes()}${listItemStyleAttribute()}>${textHtml(searchQuery, searchOptions)}</li>"
            is SemanticList -> {
                val tag = if (isOrdered) "ol" else "ul"
                "<$tag${styleAttribute()}>${items.joinToString("") { it.toHtml(searchQuery, searchOptions) }}</$tag>"
            }
            is SemanticImage -> "<figure${styleAttribute()}><img src=\"${path.escapeHtml()}\" alt=\"${altText.orEmpty().escapeHtml()}\"${imageSizeAttribute()}></figure>"
            is SemanticMath -> svgContent ?: "<pre${styleAttribute()}>${altText.orEmpty().highlightAndEscape(searchQuery, searchOptions)}</pre>"
            is SemanticSpacer -> if (isExplicitLineBreak) "<br>" else "<div${styleAttribute("height:1em")}></div>"
            is SemanticTable -> rows.joinToString("", "<table${styleAttribute()}><tbody>", "</tbody></table>") { row ->
                row.joinToString("", "<tr>", "</tr>") { cell ->
                    val tag = if (cell.isHeader) "th" else "td"
                    "<$tag colspan=\"${cell.colspan.coerceAtLeast(1)}\"${cell.style.toStyleAttribute()}>${cell.content.joinToString("") { it.toHtml(searchQuery, searchOptions) }}</$tag>"
                }
            }
            is SemanticFlexContainer -> children.joinToString("", "<div${styleAttribute()}>", "</div>") { it.toHtml(searchQuery, searchOptions) }
            is SemanticWrappingBlock -> floatedImage.toHtml(searchQuery, searchOptions) + paragraphsToWrap.joinToString("") { it.toHtml(searchQuery, searchOptions) }
            is SemanticTextBlock -> "<p${textOffsetAttributes()}${styleAttribute()}>${textHtml(searchQuery, searchOptions)}</p>"
        }
    }

    private fun String.textToParagraphHtml(
        searchQuery: String,
        searchOptions: ReaderSearchOptions,
        baseOffset: Int = 0
    ): String {
        return paragraphSegments()
            .joinToString("") { paragraph ->
                val start = baseOffset + paragraph.startOffset
                val end = start + paragraph.text.length
                """<p data-reader-text-start="$start" data-reader-text-end="$end">${paragraph.text.highlightAndEscape(searchQuery, searchOptions)}</p>"""
            }
            .ifBlank { "<p></p>" }
    }

    private fun String.paragraphSegments(): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        var index = 0
        while (index < length) {
            while (index < length && this[index].isWhitespace()) index++
            val start = index
            if (start >= length) break

            var end = start
            while (end < length) {
                if (this[end] == '\n') {
                    var probe = end
                    var newlineCount = 0
                    while (probe < length && this[probe].isWhitespace()) {
                        if (this[probe] == '\n') newlineCount++
                        probe++
                    }
                    if (newlineCount >= 2) break
                }
                end++
            }

            val raw = substring(start, end)
            val trimmedEnd = raw.indexOfLast { !it.isWhitespace() }
            if (trimmedEnd >= 0) {
                segments += TextSegment(
                    text = raw.substring(0, trimmedEnd + 1),
                    startOffset = start
                )
            }
            index = end + 1
        }
        return segments
    }

    private fun SharedEpubChapter.normalizedReaderText(): String {
        return plainText
            .replace("\r\n", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun SemanticTextBlock.textOffsetAttributes(): String {
        val start = startCharOffsetInSource.coerceAtLeast(0)
        val end = (start + text.length).coerceAtLeast(start)
        return buildString {
            append(" data-reader-text-start=\"$start\" data-reader-text-end=\"$end\"")
            elementId?.takeIf { it.isNotBlank() }?.let {
                append(" id=\"${it.escapeHtml()}\" data-reader-element-id=\"${it.escapeHtml()}\"")
            }
            cfi?.takeIf { it.isNotBlank() }?.let {
                append(" data-reader-cfi=\"${it.escapeHtml()}\"")
            }
        }
    }

    private fun SemanticTextBlock.textHtml(
        searchQuery: String,
        searchOptions: ReaderSearchOptions
    ): String {
        if (text.isEmpty()) return ""
        val inlineSpans = spans
            .filter { it.end > it.start }
            .map {
                it.copy(
                    start = it.start.coerceIn(0, text.length),
                    end = it.end.coerceIn(0, text.length)
                )
            }
            .filter { it.end > it.start }
            .sortedWith(compareBy({ it.start }, { it.end }))
        val linkSpans = inlineSpans.filter { !it.linkHref.isNullOrBlank() }
        val markersByOffset = spans
            .mapNotNull { span ->
                span.elementId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { id -> span.start.coerceIn(0, text.length) to id }
            }
            .groupBy({ it.first }, { it.second })

        if (inlineSpans.isEmpty() && markersByOffset.isEmpty()) {
            return text.highlightAndEscape(searchQuery, searchOptions)
        }

        val boundaries = mutableSetOf(0, text.length)
        inlineSpans.forEach { span ->
            boundaries += span.start
            boundaries += span.end
        }
        boundaries += markersByOffset.keys

        val ordered = boundaries.sorted()
        val builder = StringBuilder()
        fun appendMarkers(offset: Int) {
            markersByOffset[offset].orEmpty().distinct().forEach { id ->
                builder.append("""<span id="${id.escapeHtml()}" data-reader-element-id="${id.escapeHtml()}"></span>""")
            }
        }

        for (index in 0 until ordered.lastIndex) {
            val start = ordered[index]
            val end = ordered[index + 1]
            appendMarkers(start)
            if (end <= start) continue
            val html = text.substring(start, end).highlightAndEscape(searchQuery, searchOptions)
            val link = linkSpans.firstOrNull { it.start <= start && it.end >= end }
            val segmentStyle = inlineSpans
                .filter { it.start <= start && it.end >= end }
                .fold(CssStyle()) { merged, span -> merged.merge(span.style) }
                .toStyleAttribute()
            if (link?.linkHref != null) {
                builder.append("""<a href="${link.linkHref.escapeHtml()}" data-reader-link="true"$segmentStyle>$html</a>""")
            } else if (segmentStyle.isNotEmpty()) {
                builder.append("""<span$segmentStyle>$html</span>""")
            } else {
                builder.append(html)
            }
        }
        appendMarkers(text.length)
        return builder.toString()
    }

    private fun SemanticBlock.styleAttribute(extra: String? = null): String {
        return style.toStyleAttribute(extra)
    }

    private fun SemanticListItem.listItemStyleAttribute(): String {
        val markerStyle = itemMarkerImage
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { style.blockStyle.listStyleImage.isNullOrBlank() }
            ?.let { "list-style-image:url('${it.escapeHtml()}')" }
        return style.toStyleAttribute(markerStyle)
    }

    private fun CssStyle.toStyleAttribute(extra: String? = null): String {
        val declarations = mutableListOf<String>()
        extra?.takeIf { it.isNotBlank() }?.let { declarations += it }
        (spanStyle.fontSize.takeIf { it.isSpecified } ?: fontSize.takeIf { it.isSpecified })
            ?.toCssLength()
            ?.let { declarations += "font-size:$it" }
        wordSpacing.toCssLength()?.let { declarations += "word-spacing:$it" }
        textTransform?.takeIf { it.isNotBlank() }?.let { declarations += "text-transform:$it" }
        hyphens?.takeIf { it.isNotBlank() }?.let { declarations += "hyphens:$it" }
        fontVariantNumeric?.takeIf { it.isNotBlank() }?.let { declarations += "font-variant-numeric:$it" }
        if (spanStyle.color.isSpecified) declarations += "color:${spanStyle.color.toCssHex()}"
        if (spanStyle.background.isSpecified) declarations += "background-color:${spanStyle.background.toCssHex()}"
        spanStyle.fontWeight?.let { declarations += "font-weight:${it.weight}" }
        spanStyle.fontStyle?.let { declarations += "font-style:${it.toString().substringAfterLast('.').lowercase()}" }
        spanStyle.textDecoration
            ?.takeIf { it.toString() != "None" }
            ?.let { declarations += "text-decoration:${it.toString().lowercase()}" }
        textDecorationStyle?.takeIf { it.isNotBlank() }?.let { declarations += "text-decoration-style:$it" }
        if (textDecorationColor.isSpecified) declarations += "text-decoration-color:${textDecorationColor.toCssHex()}"
        if (textUnderlineOffset.isSpecified) declarations += "text-underline-offset:${textUnderlineOffset.value}px"
        fontFamilies.firstOrNull()?.takeIf { it.isNotBlank() }?.let {
            declarations += "font-family:'${it.escapeHtml()}'"
        }
        paragraphStyle.lineHeight.toCssLength()?.let { declarations += "line-height:$it" }
        paragraphStyle.textIndent?.firstLine
            ?.takeIf { it.isSpecified && it.value != 0f }
            ?.toCssLength()
            ?.let { declarations += "text-indent:$it" }
        paragraphStyle.textAlign
            ?.takeIf { it.toString() != "Unspecified" }
            ?.let { align ->
            declarations += "text-align:${align.toString().lowercase()}"
        }
        val block = blockStyle
        display?.takeIf { it.isNotBlank() }?.let { declarations += "display:$it" }
        boxSizing?.takeIf { it.isNotBlank() }?.let { declarations += "box-sizing:$it" }
        if (block.backgroundColor.isSpecified) declarations += "background-color:${block.backgroundColor.toCssHex()}"
        if (block.width.isSpecified) declarations += "width:${block.width.value}px"
        if (block.maxWidth.isSpecified) declarations += "max-width:${block.maxWidth.value}px"
        if (block.height.isSpecified) declarations += "height:${block.height.value}px"
        block.boxSizing?.takeIf { it.isNotBlank() }?.let { declarations += "box-sizing:$it" }
        if (block.margin.top.isSpecified && block.margin.top.value != 0f) declarations += "margin-top:${block.margin.top.value}px"
        if (block.margin.right.isSpecified && block.margin.right.value != 0f) declarations += "margin-right:${block.margin.right.value}px"
        if (block.margin.bottom.isSpecified && block.margin.bottom.value != 0f) declarations += "margin-bottom:${block.margin.bottom.value}px"
        if (block.margin.left.isSpecified && block.margin.left.value != 0f) declarations += "margin-left:${block.margin.left.value}px"
        if (block.padding.top.isSpecified && block.padding.top.value != 0f) declarations += "padding-top:${block.padding.top.value}px"
        if (block.padding.right.isSpecified && block.padding.right.value != 0f) declarations += "padding-right:${block.padding.right.value}px"
        if (block.padding.bottom.isSpecified && block.padding.bottom.value != 0f) declarations += "padding-bottom:${block.padding.bottom.value}px"
        if (block.padding.left.isSpecified && block.padding.left.value != 0f) declarations += "padding-left:${block.padding.left.value}px"
        block.borderTop?.toCssBorder()?.let { declarations += "border-top:$it" }
        block.borderRight?.toCssBorder()?.let { declarations += "border-right:$it" }
        block.borderBottom?.toCssBorder()?.let { declarations += "border-bottom:$it" }
        block.borderLeft?.toCssBorder()?.let { declarations += "border-left:$it" }
        if (block.borderTopLeftRadius.isSpecified && block.borderTopLeftRadius.value != 0f) declarations += "border-top-left-radius:${block.borderTopLeftRadius.value}px"
        if (block.borderTopRightRadius.isSpecified && block.borderTopRightRadius.value != 0f) declarations += "border-top-right-radius:${block.borderTopRightRadius.value}px"
        if (block.borderBottomRightRadius.isSpecified && block.borderBottomRightRadius.value != 0f) declarations += "border-bottom-right-radius:${block.borderBottomRightRadius.value}px"
        if (block.borderBottomLeftRadius.isSpecified && block.borderBottomLeftRadius.value != 0f) declarations += "border-bottom-left-radius:${block.borderBottomLeftRadius.value}px"
        block.float?.takeIf { it.isNotBlank() }?.let { declarations += "float:$it" }
        block.clear?.takeIf { it.isNotBlank() }?.let { declarations += "clear:$it" }
        block.position?.takeIf { it.isNotBlank() }?.let { declarations += "position:$it" }
        if (block.top.isSpecified) declarations += "top:${block.top.value}px"
        if (block.right.isSpecified) declarations += "right:${block.right.value}px"
        if (block.bottom.isSpecified) declarations += "bottom:${block.bottom.value}px"
        if (block.left.isSpecified) declarations += "left:${block.left.value}px"
        block.display?.takeIf { it.isNotBlank() }?.let { declarations += "display:$it" }
        block.flexDirection?.takeIf { it.isNotBlank() }?.let { declarations += "flex-direction:$it" }
        block.justifyContent?.takeIf { it.isNotBlank() }?.let { declarations += "justify-content:$it" }
        block.alignItems?.takeIf { it.isNotBlank() }?.let { declarations += "align-items:$it" }
        block.horizontalAlign?.takeIf { it.isNotBlank() }?.let { declarations += "text-align:$it" }
        block.filter?.takeIf { it.isNotBlank() }?.let { declarations += "filter:$it" }
        block.borderCollapse?.takeIf { it.isNotBlank() }?.let { declarations += "border-collapse:$it" }
        if (block.borderSpacing.isSpecified && block.borderSpacing.value != 0f) declarations += "border-spacing:${block.borderSpacing.value}px"
        block.listStyleType?.takeIf { it.isNotBlank() }?.let { declarations += "list-style-type:$it" }
        block.listStyleImage?.takeIf { it.isNotBlank() }?.let { declarations += "list-style-image:url('${it.escapeHtml()}')" }
        return if (declarations.isEmpty()) "" else " style=\"${declarations.joinToString(";").escapeHtml()}\""
    }

    private fun BorderStyle.toCssBorder(): String? {
        if (!width.isSpecified || width.value <= 0f) return null
        val styleValue = style.takeIf { it.isNotBlank() } ?: "solid"
        val colorValue = if (color.isSpecified) color.toCssHex() else "currentColor"
        return "${width.value}px $styleValue $colorValue"
    }

    private fun TextUnit.toCssLength(): String? {
        if (!isSpecified || value <= 0f) return null
        return when {
            isEm -> "${value}em"
            isSp -> "${value}px"
            else -> value.toString()
        }
    }

    private fun TextUnit.toDiagnosticTextUnit(): String? {
        if (!isSpecified || value <= 0f) return null
        return when {
            isEm -> "${value}em"
            isSp -> "${value}sp"
            else -> value.toString()
        }
    }

    private fun SemanticImage.imageSizeAttribute(): String {
        val declarations = buildList {
            intrinsicWidth?.takeIf { it > 0f }?.let { add("width:${it}px") }
            intrinsicHeight?.takeIf { it > 0f }?.let { add("height:${it}px") }
        }
        return if (declarations.isEmpty()) "" else " style=\"${declarations.joinToString(";")}\""
    }

    private fun String.highlightAndEscape(searchQuery: String, searchOptions: ReaderSearchOptions): String {
        val escaped = escapeHtml()
        val query = searchQuery.trim()
        if (query.isEmpty()) return escaped
        val escapedQuery = Regex.escape(query.escapeHtml())
        val pattern = if (searchOptions.wholeWords) {
            "(^|[^A-Za-z0-9_])($escapedQuery)(?=$|[^A-Za-z0-9_])"
        } else {
            "($escapedQuery)"
        }
        val options: Set<RegexOption> = if (searchOptions.matchCase) emptySet() else setOf(RegexOption.IGNORE_CASE)
        return escaped.replace(Regex(pattern, options)) {
            val leading = if (searchOptions.wholeWords) it.groupValues[1] else ""
            val value = if (searchOptions.wholeWords) it.groupValues[2] else it.groupValues[1]
            "$leading<span class=\"reader-highlight\">$value</span>"
        }
    }

    private fun Long.toCssColor(): String {
        val value = this and 0xFFFFFFFFL
        val red = ((value shr 16) and 0xFF).toString(16).padStart(2, '0')
        val green = ((value shr 8) and 0xFF).toString(16).padStart(2, '0')
        val blue = (value and 0xFF).toString(16).padStart(2, '0')
        return "#$red$green$blue"
    }

    private fun String.toCssFontUrl(): String {
        val trimmed = trim()
        val normalizedInput = trimmed.replace("\\", "/")
        val withScheme = when {
            normalizedInput.startsWith("file:///") -> normalizedInput
            normalizedInput.startsWith("file:/") -> "file:///" + normalizedInput.removePrefix("file:/")
            normalizedInput.contains("://") -> normalizedInput
            normalizedInput.matches(Regex("^[A-Za-z]:/.*")) -> "file:///$normalizedInput"
            else -> normalizedInput
        }
        return withScheme
            .replace(" ", "%20")
            .replace("'", "%27")
            .replace(")", "%29")
            .replace("(", "%28")
    }

    private fun String.toTextureOverlayCss(alpha: Float, darkMode: Boolean, dataUri: String?): String {
        val hasTextureData = !dataUri.isNullOrBlank()
        val texture = dataUri
            ?.takeIf { hasTextureData }
            ?.let { "url('${it.escapeCssString()}')" }
            ?: when (this) {
                ReaderTexture.NATURAL_WHITE.id,
                ReaderTexture.PAPER.id -> "radial-gradient(circle at 20% 30%, rgba(0,0,0,.09) 0 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.22), rgba(0,0,0,.04))"
                ReaderTexture.NATURAL_BLACK.id,
                ReaderTexture.SLATE.id -> "radial-gradient(circle at 20% 30%, rgba(255,255,255,.12) 0 1px, transparent 1px), linear-gradient(120deg, rgba(255,255,255,.08), rgba(0,0,0,.18))"
                ReaderTexture.LIGHT_VENEER.id,
                ReaderTexture.RETINA_WOOD.id -> "repeating-linear-gradient(90deg, rgba(120,76,32,.10) 0 3px, rgba(255,255,255,.09) 3px 7px)"
                ReaderTexture.GREY_WASH.id -> "repeating-linear-gradient(135deg, rgba(255,255,255,.07) 0 2px, rgba(0,0,0,.08) 2px 5px)"
                ReaderTexture.CLASSY_FABRIC.id,
                ReaderTexture.CANVAS.id -> "repeating-linear-gradient(0deg, rgba(255,255,255,.08) 0 1px, transparent 1px 4px), repeating-linear-gradient(90deg, rgba(0,0,0,.08) 0 1px, transparent 1px 4px)"
                ReaderTexture.RETRO_INTRO.id,
                ReaderTexture.EINK.id -> "radial-gradient(circle, rgba(0,0,0,.12) 0 1px, transparent 1px)"
                else -> "linear-gradient(135deg, rgba(255,255,255,.08), rgba(0,0,0,.08))"
            }
        val size = if (hasTextureData) {
            "auto"
        } else {
            when (this) {
                ReaderTexture.EINK.id,
                ReaderTexture.RETRO_INTRO.id,
                ReaderTexture.PAPER.id,
                ReaderTexture.NATURAL_WHITE.id,
                ReaderTexture.NATURAL_BLACK.id -> "7px 7px, 100% 100%"
                else -> "auto"
            }
        }
        return """
                body::before {
                  content: "";
                  position: fixed;
                  inset: 0;
                  pointer-events: none;
                  background-image: $texture;
                  background-size: $size;
                  opacity: ${alpha.coerceIn(0f, 1f)};
                  mix-blend-mode: ${if (darkMode) "screen" else "multiply"};
                  z-index: 0;
                }
        """.trimIndent()
    }

    private fun String.escapeCssString(): String {
        return replace("\\", "\\\\").replace("'", "\\'")
    }

    private fun String.applyUserHighlights(
        highlights: List<UserHighlight>,
        contentStartOffset: Int,
        contentEndOffset: Int
    ): String {
        val rangedHighlights = highlights
            .mapNotNull { it.toRenderHighlight(contentStartOffset, contentEndOffset) }
            .distinctBy { "${it.absoluteStart}:${it.absoluteEnd}:${it.id}" }
            .sortedWith(compareByDescending<RenderedHighlight> { it.relativeStart }.thenByDescending { it.relativeEnd })

        val rangedHtml = rangedHighlights.fold(this) { html, highlight ->
            val htmlRange = html.htmlRangeForHighlight(highlight) ?: return@fold html
            val startIndex = htmlRange.first
            val endIndex = htmlRange.last
            if (startIndex >= endIndex || endIndex > html.length) return@fold html
            val markedText = html.substring(startIndex, endIndex)
            if (markedText.isBlank()) return@fold html
            val marker = """<mark class="reader-user-highlight ${highlight.color.cssClass}" data-reader-highlight-id="${highlight.id.escapeHtml()}" data-reader-start-offset="${highlight.absoluteStart}" data-reader-end-offset="${highlight.absoluteEnd}">$markedText</mark>"""
            html.replaceRange(startIndex, endIndex, marker)
        }

        return highlights
            .filterNot { it.locator.withFallbacks(chapterIndex = it.chapterIndex, cfi = it.cfi, textQuote = it.text).hasTextRange }
            .fold(rangedHtml) { html, highlight ->
                val text = highlight.text.trim().takeIf { it.isNotBlank() } ?: return@fold html
                val escapedText = text.escapeHtml()
                val markedText = """<mark class="reader-user-highlight ${highlight.color.cssClass}" data-reader-highlight-id="${highlight.id.escapeHtml()}">$escapedText</mark>"""
                html.replaceFirst(escapedText, markedText)
            }
    }

    private fun String.htmlRangeForHighlight(highlight: RenderedHighlight): IntRange? {
        val block = findTextBlockRange(highlight.absoluteStart, highlight.absoluteEnd)
        if (block != null) {
            val startIndex = htmlIndexForTextOffset(
                targetOffset = highlight.absoluteStart - block.startOffset,
                startIndex = block.contentStartIndex,
                endIndex = block.contentEndIndex
            ) ?: return null
            val endIndex = htmlIndexForTextOffset(
                targetOffset = highlight.absoluteEnd - block.startOffset,
                startIndex = block.contentStartIndex,
                endIndex = block.contentEndIndex
            ) ?: return null
            return startIndex..endIndex
        }
        val startIndex = htmlIndexForTextOffset(highlight.relativeStart) ?: return null
        val endIndex = htmlIndexForTextOffset(highlight.relativeEnd) ?: return null
        return startIndex..endIndex
    }

    private fun String.findTextBlockRange(absoluteStart: Int, absoluteEnd: Int): HtmlTextBlockRange? {
        return textBlockStartPattern.findAll(this).mapNotNull { match ->
            val tagName = match.groupValues[1]
            val blockStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val blockEnd = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
            if (absoluteStart < blockStart || absoluteEnd > blockEnd) return@mapNotNull null
            val contentStart = match.range.last + 1
            val closingTag = "</$tagName>"
            val contentEnd = indexOf(closingTag, startIndex = contentStart, ignoreCase = true)
            if (contentEnd < contentStart) return@mapNotNull null
            HtmlTextBlockRange(
                startOffset = blockStart,
                endOffset = blockEnd,
                contentStartIndex = contentStart,
                contentEndIndex = contentEnd
            )
        }.firstOrNull()
    }

    private fun String.htmlIndexForTextOffset(
        targetOffset: Int,
        startIndex: Int = 0,
        endIndex: Int = length
    ): Int? {
        if (targetOffset < 0) return null
        var index = startIndex.coerceIn(0, length)
        val limit = endIndex.coerceIn(index, length)
        var textOffset = 0
        var boundaryAfterText: Int? = null
        while (index < limit) {
            when (this[index]) {
                '<' -> {
                    val tagEnd = indexOf('>', startIndex = index + 1)
                    if (tagEnd < 0 || tagEnd >= limit) return null
                    index = tagEnd + 1
                }

                '&' -> {
                    if (textOffset == targetOffset) return index
                    val entityEnd = indexOf(';', startIndex = index + 1)
                    if (entityEnd > index) {
                        textOffset++
                        index = entityEnd + 1
                    } else {
                        textOffset++
                        index++
                    }
                    boundaryAfterText = index
                }

                else -> {
                    if (textOffset == targetOffset) return index
                    textOffset++
                    index++
                    boundaryAfterText = index
                }
            }
        }
        return if (textOffset == targetOffset) boundaryAfterText ?: startIndex else null
    }

    private fun UserHighlight.toRenderHighlight(contentStartOffset: Int, contentEndOffset: Int): RenderedHighlight? {
        val normalizedLocator = locator.withFallbacks(chapterIndex = chapterIndex, cfi = cfi, textQuote = text)
        val start = normalizedLocator.startOffset ?: return null
        val end = normalizedLocator.endOffset ?: start
        if (end < start) return null
        val boundedStart = start.coerceAtLeast(contentStartOffset)
        val boundedEnd = end.coerceAtMost(contentEndOffset)
        if (boundedEnd <= boundedStart) return null
        return RenderedHighlight(
            id = id,
            color = color,
            absoluteStart = boundedStart,
            absoluteEnd = boundedEnd,
            relativeStart = boundedStart - contentStartOffset,
            relativeEnd = boundedEnd - contentStartOffset
        )
    }

    private fun UserHighlight.belongsToPage(page: ReaderPage): Boolean {
        val normalizedLocator = locator.withFallbacks(chapterIndex = chapterIndex, cfi = cfi, textQuote = text)
        val locatorChapterIndex = normalizedLocator.chapterIndex ?: chapterIndex
        if (locatorChapterIndex != page.chapterIndex) return false
        if (normalizedLocator.hasTextRange) {
            val start = normalizedLocator.startOffset ?: return false
            val end = normalizedLocator.endOffset ?: start
            return if (start == end) {
                start in page.startOffset..page.endOffset
            } else {
                start < page.endOffset && end > page.startOffset
            }
        }
        normalizedLocator.pageIndex?.let { return it == page.pageIndex }
        val prefix = "desktop:${page.chapterIndex}:"
        val desktopPageIndex = cfi
            .takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.substringBefore(':')
            ?.toIntOrNull()
        return desktopPageIndex == null || desktopPageIndex < 0 || desktopPageIndex == page.pageIndex
    }

    private val UserHighlight.locatedChapterIndex: Int
        get() = locator.chapterIndex ?: chapterIndex

    private fun ReaderLocator.toNavigationAttributes(): String {
        val attributes = buildList {
            chapterIndex?.let { add("data-reader-active-chapter-index=\"$it\"") }
            pageIndex?.let { add("data-reader-active-page-index=\"$it\"") }
            startOffset?.let { add("data-reader-active-start-offset=\"$it\"") }
            endOffset?.let { add("data-reader-active-end-offset=\"$it\"") }
            cfi?.takeIf { it.isNotBlank() }?.let { add("data-reader-active-cfi=\"${it.escapeHtml()}\"") }
        }
        return if (attributes.isEmpty()) "" else " " + attributes.joinToString(" ")
    }

    private fun List<ReaderPage>.toPageAnchorJson(): String {
        if (isEmpty()) return "[]"
        return joinToString(prefix = "[", postfix = "]") { page ->
            """{"pageIndex":${page.pageIndex},"chapterIndex":${page.chapterIndex},"startOffset":${page.startOffset},"endOffset":${page.endOffset}}"""
        }
    }

    private data class TextSegment(
        val text: String,
        val startOffset: Int
    )

    private data class RenderedHighlight(
        val id: String,
        val color: HighlightColor,
        val absoluteStart: Int,
        val absoluteEnd: Int,
        val relativeStart: Int,
        val relativeEnd: Int
    )

    private data class HtmlTextBlockRange(
        val startOffset: Int,
        val endOffset: Int,
        val contentStartIndex: Int,
        val contentEndIndex: Int
    )

    private val textBlockStartPattern = Regex(
        """<([A-Za-z][A-Za-z0-9]*)\b[^>]*\bdata-reader-text-start="(\d+)"[^>]*\bdata-reader-text-end="(\d+)"[^>]*>"""
    )

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun androidx.compose.ui.graphics.Color.toCssHex(): String {
        fun channel(value: Float): String = (value * 255f).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0')
        return "#${channel(red)}${channel(green)}${channel(blue)}"
    }

    private fun logReaderHtml(message: String) {
        println("ReaderHtmlRender $message")
    }
}
