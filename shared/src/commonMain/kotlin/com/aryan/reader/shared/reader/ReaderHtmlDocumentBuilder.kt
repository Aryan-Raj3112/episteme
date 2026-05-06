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
import com.aryan.reader.shared.HighlightColor
import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.UserHighlight
import kotlin.math.roundToInt

object ReaderHtmlDocumentBuilder {
    fun verticalDocument(
        book: SharedEpubBook,
        settings: ReaderSettings,
        searchQuery: String = "",
        searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
        highlights: List<UserHighlight> = emptyList(),
        highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
        navigationLocator: ReaderLocator? = null
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
            navigationLocator = navigationLocator
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
        navigationLocator: ReaderLocator? = null
    ): String {
        val chapter = page?.let { book.chapters.getOrNull(it.chapterIndex) }
        val body = if (page == null || chapter == null) {
            "<section class=\"page\"></section>"
        } else {
            val blocks = chapter.semanticBlocks
                .filter { block ->
                    val start = (block as? SemanticTextBlock)?.startCharOffsetInSource ?: return@filter false
                    start in page.startOffset..page.endOffset
                }
                .takeIf { it.isNotEmpty() }
                ?.joinToString("") { it.toHtml(searchQuery, searchOptions) }
                ?: page.text.textToParagraphHtml(searchQuery, searchOptions, baseOffset = page.startOffset)
            val pageHtml = blocks.applyUserHighlights(
                highlights = highlights.filter { it.belongsToPage(page) },
                contentStartOffset = page.startOffset,
                contentEndOffset = page.endOffset
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
            navigationLocator = navigationLocator
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
        navigationLocator: ReaderLocator?
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
            ?.toTextureOverlayCss(settings.textureAlpha, settings.darkMode)
            .orEmpty()
        val highlightButtons = highlightPalette.sanitized().colors.joinToString("\n") { color ->
            """<button type="button" data-action="highlight" data-color-id="${color.id}" title="${color.id.escapeHtml()}">${color.id.escapeHtml()}</button>"""
        }
        val navigationAttributes = navigationLocator?.toNavigationAttributes().orEmpty()
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
                  border-collapse: collapse;
                  max-width: 100%;
                  overflow-wrap: anywhere;
                }
                td, th {
                  border: 1px solid color-mix(in srgb, var(--reader-fg) 24%, transparent);
                  padding: 0.35em 0.5em;
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
                <button type="button" data-action="find">Find</button>
                <button type="button" data-action="translate">Translate</button>
                <button type="button" data-action="clear">Clear</button>
              </div>
              <script>
                (function () {
                  var menu = document.getElementById('reader-selection-menu');
                  var savedRange = null;
                  function numberAttribute(element, name, fallback) {
                    if (!element) return fallback;
                    var value = parseInt(element.getAttribute(name) || '', 10);
                    return Number.isFinite(value) ? value : fallback;
                  }
                  function selectorValue(value) {
                    if (window.CSS && window.CSS.escape) return window.CSS.escape(String(value));
                    return String(value).replace(/"/g, '\\"');
                  }
                  function scrollToActiveLocator() {
                    var chapterIndex = document.body.getAttribute('data-reader-active-chapter-index');
                    if (chapterIndex === null || chapterIndex === '') return;
                    var chapter = document.querySelector('[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]');
                    if (!chapter) return;
                    var activeStart = numberAttribute(document.body, 'data-reader-active-start-offset', null);
                    var exact = activeStart === null
                      ? null
                      : document.querySelector('[data-reader-start-offset="' + selectorValue(activeStart) + '"]');
                    var target = exact || chapter;
                    var content = chapter.querySelector('.reader-content') || chapter;
                    if (!exact && activeStart !== null && content) {
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
                  function selectionOffsetsWithin(host, range) {
                    var before = range.cloneRange();
                    before.selectNodeContents(host);
                    try {
                      before.setEnd(range.startContainer, range.startOffset);
                    } catch (error) {
                      return { start: null, end: null };
                    }
                    var rawText = range.toString();
                    var leadingWhitespace = rawText.length - rawText.replace(/^\s+/, '').length;
                    var selectedText = rawText.trim();
                    var start = before.toString().length + leadingWhitespace;
                    return { start: start, end: start + selectedText.length };
                  }
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
                    if (action === 'find') window.find(text);
                    if (action === 'translate') window.open('https://translate.google.com/?sl=auto&tl=en&text=' + encodeURIComponent(text) + '&op=translate', '_blank');
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
                  document.addEventListener('mousedown', function (event) {
                    if (event.button === 0 && !menu.contains(event.target)) hideMenu();
                  });
                  scrollToActiveLocator();
                  window.addEventListener('load', scrollToActiveLocator, { once: true });
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

    private fun SemanticBlock.toHtml(searchQuery: String, searchOptions: ReaderSearchOptions): String {
        return when (this) {
            is SemanticHeader -> "<h${level.coerceIn(1, 6)}${textOffsetAttributes()}>${text.highlightAndEscape(searchQuery, searchOptions)}</h${level.coerceIn(1, 6)}>"
            is SemanticParagraph -> "<p${textOffsetAttributes()}>${text.highlightAndEscape(searchQuery, searchOptions)}</p>"
            is SemanticListItem -> "<li${textOffsetAttributes()}>${text.highlightAndEscape(searchQuery, searchOptions)}</li>"
            is SemanticList -> {
                val tag = if (isOrdered) "ol" else "ul"
                "<$tag>${items.joinToString("") { it.toHtml(searchQuery, searchOptions) }}</$tag>"
            }
            is SemanticImage -> "<figure><img src=\"${path.escapeHtml()}\" alt=\"${altText.orEmpty().escapeHtml()}\"></figure>"
            is SemanticMath -> svgContent ?: "<pre>${altText.orEmpty().highlightAndEscape(searchQuery, searchOptions)}</pre>"
            is SemanticSpacer -> if (isExplicitLineBreak) "<br>" else "<div style=\"height:1em\"></div>"
            is SemanticTable -> rows.joinToString("", "<table><tbody>", "</tbody></table>") { row ->
                row.joinToString("", "<tr>", "</tr>") { cell ->
                    val tag = if (cell.isHeader) "th" else "td"
                    "<$tag colspan=\"${cell.colspan.coerceAtLeast(1)}\">${cell.content.joinToString("") { it.toHtml(searchQuery, searchOptions) }}</$tag>"
                }
            }
            is SemanticFlexContainer -> children.joinToString("", "<div>", "</div>") { it.toHtml(searchQuery, searchOptions) }
            is SemanticWrappingBlock -> floatedImage.toHtml(searchQuery, searchOptions) + paragraphsToWrap.joinToString("") { it.toHtml(searchQuery, searchOptions) }
            is SemanticTextBlock -> "<p${textOffsetAttributes()}>${text.highlightAndEscape(searchQuery, searchOptions)}</p>"
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
                append(" data-reader-element-id=\"${it.escapeHtml()}\"")
            }
            cfi?.takeIf { it.isNotBlank() }?.let {
                append(" data-reader-cfi=\"${it.escapeHtml()}\"")
            }
        }
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

    private fun String.toTextureOverlayCss(alpha: Float, darkMode: Boolean): String {
        val texture = when (this) {
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
        val size = when (this) {
            ReaderTexture.EINK.id,
            ReaderTexture.RETRO_INTRO.id,
            ReaderTexture.PAPER.id,
            ReaderTexture.NATURAL_WHITE.id,
            ReaderTexture.NATURAL_BLACK.id -> "7px 7px, 100% 100%"
            else -> "auto"
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
}
