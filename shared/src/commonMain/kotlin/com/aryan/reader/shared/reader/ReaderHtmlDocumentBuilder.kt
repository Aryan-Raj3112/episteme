package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderHighlightPalette
import com.aryan.reader.shared.UserHighlight
import kotlin.math.abs

object ReaderHtmlDocumentBuilder {
    fun verticalDocument(
        book: SharedEpubBook,
        settings: ReaderSettings,
        searchQuery: String = "",
        searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
        highlights: List<UserHighlight> = emptyList(),
        highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
        highlightActionsEnabled: Boolean = true,
        navigationLocator: ReaderLocator? = null,
        pages: List<ReaderPage> = emptyList(),
        readerAiFeaturesEnabled: Boolean = true,
        cloudTtsEnabled: Boolean = true,
        externalLookupEnabled: Boolean = true,
        textureDataUri: String? = null,
        renderedChapterRange: IntRange? = null,
        virtualizedChapterChunks: Map<Int, List<String>> = emptyMap(),
        virtualizedInitialChunkIndex: Int = 0,
        showChapterTitles: Boolean = true
    ): String {
        val renderedChapterIndices = renderedChapterRange
            ?.asSequence()
            ?.filter { it in book.chapters.indices }
            ?.distinct()
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: book.chapters.indices.toList()
        val body = renderedChapterIndices.joinToString("\n") { index ->
            val chapter = book.chapters[index]
            val chapterText = chapter.normalizedReaderText()
            val virtualChunks = virtualizedChapterChunks[index].orEmpty()
            val chapterHtml = if (virtualChunks.isEmpty()) {
                chapter.toHtml(searchQuery, searchOptions)
                    .applyUserHighlights(
                        highlights = highlights.filter { it.locatedChapterIndex == index },
                        contentStartOffset = 0,
                        contentEndOffset = chapterText.length
                    )
            } else {
                ""
            }
            val chapterTitleHtml = if (showChapterTitles) {
                "<h1 class=\"chapter-title\">${chapter.title.escapeHtml()}</h1>"
            } else {
                ""
            }
            val renderedContent = if (virtualChunks.isEmpty()) {
                chapterHtml
            } else {
                val initialChunkCount = minOf(
                    virtualChunks.size,
                    virtualizedInitialChunkIndex.coerceIn(0, virtualChunks.lastIndex) + 2,
                    MaxInitialVirtualReaderChunks
                )
                virtualChunks.mapIndexed { chunkIndex, chunk ->
                    if (chunkIndex < initialChunkCount) {
                        "<div class=\"reader-virtual-chunk\" data-reader-chunk-index=\"$chunkIndex\">$chunk</div>"
                    } else {
                        val placeholderHeight = estimateVirtualReaderChunkHeightPx(chunk)
                        "<div class=\"reader-virtual-chunk\" data-reader-chunk-index=\"$chunkIndex\" style=\"height: ${placeholderHeight}px\"></div>"
                    }
                }.joinToString("\n")
            }
            """
            <section class="chapter" id="chapter-$index" data-reader-chapter-index="$index" data-reader-chapter-id="${chapter.id.escapeHtml()}" data-reader-chapter-href="${chapter.baseHref.orEmpty().escapeHtml()}">
              $chapterTitleHtml
              <div class="reader-content" data-reader-content-start="0" data-reader-content-end="${chapterText.length}">
                $renderedContent
              </div>
            </section>
            """.trimIndent()
        }
        val virtualizationScript = virtualizedChapterChunks.values.firstOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { virtualReaderBootstrapScript(it.size) }
            .orEmpty()
        return document(
            title = book.title,
            settings = settings,
            bookCss = book.css.values.joinToString("\n"),
            // Keep reader-owned JavaScript ahead of publication markup. Legacy MOBI HTML can
            // contain unclosed elements (for example textarea/xmp/plaintext) that cause WebKit
            // to render anything appended after the chapter as literal book text.
            body = virtualizationScript + body,
            searchQuery = searchQuery,
            searchOptions = searchOptions,
            highlightPalette = highlightPalette,
            highlightActionsEnabled = highlightActionsEnabled,
            navigationLocator = navigationLocator,
            pageAnchors = pages,
            readerAiFeaturesEnabled = readerAiFeaturesEnabled,
            cloudTtsEnabled = cloudTtsEnabled,
            externalLookupEnabled = externalLookupEnabled,
            textureDataUri = textureDataUri
        )
    }

    fun verticalChapterChunks(
        book: SharedEpubBook,
        chapterIndex: Int,
        chunkNodeCount: Int = 20
    ): List<String> {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        val html = chapter.htmlContent.takeIf { it.isNotBlank() }
            ?: chapter.toHtml("", ReaderSearchOptions())
        return splitReaderHtmlAtTopLevel(html, chunkNodeCount)
    }

    private fun virtualReaderBootstrapScript(totalChunks: Int): String = """
        <script>
          (function () {
            var observer = null;
            var requested = Object.create(null);
            function chunk(index) {
              return document.querySelector('.reader-virtual-chunk[data-reader-chunk-index="' + index + '"]');
            }
            function request(index) {
              if (requested[index]) return;
              requested[index] = true;
              if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                window.kmpJsBridge.callNative('readerChunkRequested', JSON.stringify({ index: index }));
              }
            }
            window.readerVirtualization = {
              totalChunks: $totalChunks,
              provideChunk: function (index, html) {
                var host = chunk(index);
                if (!host) return;
                var oldHeight = host.getBoundingClientRect().height;
                host.innerHTML = html || '';
                host.style.height = '';
                requested[index] = false;
                var newHeight = host.getBoundingClientRect().height;
                if (host.getBoundingClientRect().bottom < 0 && Math.abs(newHeight - oldHeight) > 0.5) {
                  window.scrollBy(0, newHeight - oldHeight);
                }
              }
            };
            function install() {
              if (observer) observer.disconnect();
              observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                  var host = entry.target;
                  var index = parseInt(host.getAttribute('data-reader-chunk-index') || '-1', 10);
                  if (index < 0) return;
                  if (entry.isIntersecting) {
                    if (!host.innerHTML.trim()) request(index);
                  } else if (host.innerHTML.trim()) {
                    var height = host.getBoundingClientRect().height;
                    host.style.height = Math.max(1, height) + 'px';
                    host.innerHTML = '';
                    requested[index] = false;
                  }
                });
              }, { rootMargin: '2500px 0px' });
              document.querySelectorAll('.reader-virtual-chunk').forEach(function (host) { observer.observe(host); });
            }
            if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install, { once: true });
            else install();
          })();
        </script>
    """.trimIndent()

    private fun splitReaderHtmlAtTopLevel(html: String, chunkNodeCount: Int): List<String> {
        if (html.isBlank()) return emptyList()
        val size = chunkNodeCount.coerceAtLeast(1)
        val nodeRanges = topLevelReaderHtmlNodeRanges(html)
        if (nodeRanges.isEmpty()) return listOf(html)
        return nodeRanges.chunked(size).map { ranges ->
            html.substring(ranges.first().first, ranges.last().last + 1)
        }
    }

    private fun topLevelReaderHtmlNodeRanges(html: String): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var depth = 0
        var cursor = 0
        var nodeStart = -1
        while (cursor < html.length) {
            val tagStart = html.indexOf('<', cursor)
            if (tagStart < 0) break
            if (depth == 0 && nodeStart < 0 && html.substring(cursor, tagStart).isNotBlank()) nodeStart = cursor
            val tagEnd = readerHtmlTagEnd(html, tagStart)
            if (tagEnd < 0) break
            val token = html.substring(tagStart, tagEnd + 1)
            val isComment = token.startsWith("<!--") || token.startsWith("<!") || token.startsWith("<?")
            val isClosing = token.startsWith("</")
            val isSelfClosing = token.trimEnd().endsWith("/>") || token.readerHtmlTagName() in ReaderHtmlVoidTags
            if (!isComment) {
                if (isClosing) {
                    if (depth > 0) depth--
                    if (depth == 0 && nodeStart >= 0) {
                        ranges += nodeStart..tagEnd
                        nodeStart = -1
                    }
                } else {
                    if (depth == 0 && nodeStart < 0) nodeStart = tagStart
                    if (isSelfClosing) {
                        if (depth == 0 && nodeStart >= 0) {
                            ranges += nodeStart..tagEnd
                            nodeStart = -1
                        }
                    } else {
                        depth++
                    }
                }
            }
            cursor = tagEnd + 1
        }
        if (nodeStart >= 0) ranges += nodeStart..html.lastIndex
        else if (cursor < html.length && html.substring(cursor).isNotBlank()) ranges += cursor..html.lastIndex
        return ranges
    }

    private fun estimateVirtualReaderChunkHeightPx(html: String): Int {
        return topLevelReaderHtmlNodeRanges(html).size.coerceAtLeast(1) * EstimatedVirtualReaderNodeHeightPx
    }

    private fun readerHtmlTagEnd(html: String, start: Int): Int {
        if (html.startsWith("<!--", start)) return html.indexOf("-->", start + 4).takeIf { it >= 0 }?.plus(2) ?: -1
        var quote: Char? = null
        for (index in start + 1 until html.length) {
            val char = html[index]
            if (quote != null) {
                if (char == quote) quote = null
            } else if (char == '\'' || char == '"') {
                quote = char
            } else if (char == '>') {
                return index
            }
        }
        return -1
    }

    private fun String.readerHtmlTagName(): String = removePrefix("<")
        .removePrefix("/")
        .trimStart()
        .takeWhile { it.isLetterOrDigit() || it == ':' || it == '-' }
        .substringAfter(':')
        .lowercase()

    private const val MaxInitialVirtualReaderChunks = 8
    private const val EstimatedVirtualReaderNodeHeightPx = 72
    private val ReaderHtmlVoidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")

    fun pageDocument(
        book: SharedEpubBook,
        page: ReaderPage?,
        visiblePages: List<ReaderPage> = listOfNotNull(page),
        settings: ReaderSettings,
        searchQuery: String = "",
        searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
        highlights: List<UserHighlight> = emptyList(),
        highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
        highlightActionsEnabled: Boolean = true,
        navigationLocator: ReaderLocator? = null,
        readerAiFeaturesEnabled: Boolean = true,
        cloudTtsEnabled: Boolean = true,
        externalLookupEnabled: Boolean = true,
        textureDataUri: String? = null
    ): String {
        val paginatedSettings = settings.copy(readingMode = ReaderReadingMode.PAGINATED)
        val pagesToRender = visiblePages.ifEmpty { listOfNotNull(page) }
        val body = if (pagesToRender.isEmpty()) {
            logReaderHtml("page_document_empty reason=missing_page_or_chapter")
            "<section class=\"page\"></section>"
        } else {
            val sections = pagesToRender.mapNotNull { readerPage ->
                pageSectionHtml(
                    book = book,
                    page = readerPage,
                    settings = paginatedSettings,
                    searchQuery = searchQuery,
                    searchOptions = searchOptions,
                    highlights = highlights
                )
            }
            if (sections.size > 1) {
                sections.joinToString("\n", "<div class=\"reader-spread\" data-reader-spread-count=\"${sections.size}\">", "</div>")
            } else {
                sections.firstOrNull() ?: "<section class=\"page\"></section>"
            }
        }
        return document(
            title = book.title,
            settings = paginatedSettings,
            bookCss = book.css.values.joinToString("\n"),
            body = body,
            searchQuery = searchQuery,
            searchOptions = searchOptions,
            highlightPalette = highlightPalette,
            highlightActionsEnabled = highlightActionsEnabled,
            navigationLocator = navigationLocator,
            pageAnchors = pagesToRender,
            readerAiFeaturesEnabled = readerAiFeaturesEnabled,
            cloudTtsEnabled = cloudTtsEnabled,
            externalLookupEnabled = externalLookupEnabled,
            textureDataUri = textureDataUri
        )
    }

    fun appearanceUpdateScript(
        settings: ReaderSettings,
        textureDataUri: String? = null
    ): String {
        val appearance = settings.toDocumentAppearanceCss(textureDataUri)
        val customFontCss = settings.readerCustomFontFaceCss()
        return """
            (function () {
              var root = document.documentElement;
              if (!root) return;
              root.style.colorScheme = ${appearance.colorScheme.toJsStringLiteral()};
              root.style.setProperty('--reader-bg', ${appearance.background.toJsStringLiteral()});
              root.style.setProperty('--reader-fg', ${appearance.foreground.toJsStringLiteral()});
              root.style.setProperty('--reader-link', ${appearance.linkColors.color.toJsStringLiteral()});
              root.style.setProperty('--reader-link-decoration', ${appearance.linkColors.decoration.toJsStringLiteral()});
              root.style.setProperty('--reader-link-bg', ${appearance.linkColors.background.toJsStringLiteral()});
              root.style.setProperty('--reader-highlight', ${appearance.highlight.toJsStringLiteral()});
              root.style.setProperty('--reader-font-size', ${"${settings.fontSize}px".toJsStringLiteral()});
              root.style.setProperty('--reader-font-weight', ${settings.readerFontWeightCss().toJsStringLiteral()});
              root.style.setProperty('--reader-letter-spacing', ${"${settings.letterSpacing}em".toJsStringLiteral()});
              root.style.setProperty('--reader-line-height', ${settings.lineSpacing.toString().toJsStringLiteral()});
              root.style.setProperty('--reader-page-width', ${"${settings.pageWidth}px".toJsStringLiteral()});
              root.style.setProperty('--reader-margin', ${"${settings.margin}px".toJsStringLiteral()});
              root.style.setProperty('--reader-margin-x', ${"${settings.resolvedHorizontalMargin}px".toJsStringLiteral()});
              root.style.setProperty('--reader-margin-y', ${"${settings.resolvedVerticalMargin}px".toJsStringLiteral()});
              root.style.setProperty('--reader-vertical-margin-y', ${"${settings.readerVerticalMarginY()}px".toJsStringLiteral()});
              root.style.setProperty('--reader-vertical-page-width', 'max(0px, calc(100% - (var(--reader-margin-x) * 2)))');
              root.style.setProperty('--reader-paragraph-spacing', ${settings.paragraphSpacing.toString().toJsStringLiteral()});
              root.style.setProperty('--reader-image-scale', ${settings.readerImageScaleCss().toJsStringLiteral()});
              root.style.setProperty('--reader-hide-images', ${if (settings.hideImages) "'none'" else "'block'"});
              root.style.setProperty('--reader-align', ${settings.readerTextAlignCss().toJsStringLiteral()});
              root.style.setProperty('--reader-family', ${settings.readerFontFamilyCss().toJsStringLiteral()});
              var customFontCss = ${customFontCss.toJsStringLiteral()};
              var customFontStyle = document.getElementById('reader-custom-font-style');
              if (customFontCss) {
                if (!customFontStyle) {
                  customFontStyle = document.createElement('style');
                  customFontStyle.id = 'reader-custom-font-style';
                  document.head.appendChild(customFontStyle);
                }
                customFontStyle.textContent = customFontCss;
              } else if (customFontStyle && customFontStyle.parentNode) {
                customFontStyle.parentNode.removeChild(customFontStyle);
              }
              var textureStyle = document.getElementById('reader-texture-style');
              if (!textureStyle) {
                textureStyle = document.createElement('style');
                textureStyle.id = 'reader-texture-style';
                document.head.appendChild(textureStyle);
              }
              textureStyle.textContent = ${appearance.textureOverlayCss.toJsStringLiteral()};
            })();
        """.trimIndent()
    }

    fun pageAnchorsUpdateScript(pages: List<ReaderPage>): String {
        val pageAnchorJson = pages.toPageAnchorJson()
        return """
            (function () {
              if (window.readerSetPageAnchors) {
                window.readerSetPageAnchors($pageAnchorJson);
              }
            })();
        """.trimIndent()
    }

    fun highlightPaletteUpdateScript(highlightPalette: ReaderHighlightPalette): String {
        val highlightButtons = highlightPalette.toSelectionPaletteButtons()
        return """
            (function () {
              var container = document.querySelector('#reader-selection-menu .reader-selection-colors');
              if (!container) return;
              container.innerHTML = ${highlightButtons.toJsStringLiteral()};
            })();
        """.trimIndent()
    }

    private fun pageSectionHtml(
        book: SharedEpubBook,
        page: ReaderPage,
        settings: ReaderSettings,
        searchQuery: String,
        searchOptions: ReaderSearchOptions,
        highlights: List<UserHighlight>
    ): String? {
        val chapter = book.chapters.getOrNull(page.chapterIndex) ?: return null
        val measuredPageBlocks = page.semanticBlocks
        val semanticPageBlocks = measuredPageBlocks.ifEmpty { chapter.semanticBlocks.blocksForPage(page) }
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
                "semantic=$usedSemanticBlocks measured=${measuredPageBlocks.isNotEmpty()} " +
                "blocks=${semanticPageBlocks.size}/${chapter.semanticBlocks.size} " +
                "htmlChars=${pageHtml.length} settingsFont=${settings.fontSize} lineSpacing=${settings.lineSpacing} " +
                "summary=\"${semanticPageBlocks.blockSummary()}\" styles=\"${semanticPageBlocks.styleSummary()}\""
        )
        return """
        <section class="page" data-reader-chapter-index="${page.chapterIndex}" data-reader-chapter-id="${chapter.id.escapeHtml()}" data-reader-chapter-href="${chapter.baseHref.orEmpty().escapeHtml()}" data-reader-page-index="${page.pageIndex}" data-reader-page-start="${page.startOffset}" data-reader-page-end="${page.endOffset}">
          <div class="reader-content" data-reader-content-start="${page.startOffset}" data-reader-content-end="${page.endOffset}">
            $pageHtml
          </div>
        </section>
        """.trimIndent()
    }

}