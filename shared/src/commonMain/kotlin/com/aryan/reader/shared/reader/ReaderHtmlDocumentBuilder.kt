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
            textureDataUri = textureDataUri,
            documentLanguage = book.language
        )
    }

    fun verticalChapterChunks(
        book: SharedEpubBook,
        chapterIndex: Int,
        chunkNodeCount: Int = 20,
        maxChunkChars: Int = MaxVirtualReaderChunkChars
    ): List<String> {
        val chapter = book.chapters.getOrNull(chapterIndex) ?: return emptyList()
        val html = chapter.htmlContent.takeIf { it.isNotBlank() }
            ?: chapter.toHtml("", ReaderSearchOptions())
        return splitReaderHtmlAtTopLevel(html, chunkNodeCount, maxChunkChars)
    }

    private fun virtualReaderBootstrapScript(totalChunks: Int): String = """
        <script>
          (function () {
            var observer = null;
            var requested = Object.create(null);
            var bridgeRetries = Object.create(null);
            function chunk(index) {
              return document.querySelector('.reader-virtual-chunk[data-reader-chunk-index="' + index + '"]');
            }
            function request(index) {
              if (requested[index]) return;
              if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                requested[index] = true;
                bridgeRetries[index] = 0;
                window.kmpJsBridge.callNative('readerChunkRequested', JSON.stringify({ index: index }));
              } else {
                // Android parity: the shared Android bridge is injected in
                // onPageFinished, after DOMContentLoaded. Marking requested
                // before the bridge exists would drop the first visible
                // chunks forever. Retry briefly until the bridge arrives.
                var retries = bridgeRetries[index] || 0;
                if (retries < 50) {
                  bridgeRetries[index] = retries + 1;
                  window.setTimeout(function () { request(index); }, 100);
                }
              }
            }
            window.readerVirtualization = {
              totalChunks: $totalChunks,
            requestChunk: function (index) { request(index); },
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
                // Android parity (restoreHighlights on chunk load): freshly provided
                // chunks have no markers yet, so re-apply the current highlight list.
                // The selective reconcile keeps every already-painted marker untouched.
                if (window.readerApplyHighlights) {
                    var snapshot = window.readerCurrentHighlightsSnapshot && window.readerCurrentHighlightsSnapshot();
                    if (snapshot && snapshot.length) window.readerApplyHighlights(snapshot);
                }
                // Fresh chunks carry verbatim author colors; re-run the contrast pass
                // with the last theme args (MutationObserver also covers this).
                // Image-heavy chapters provide dozens of chunks while scrolling,
                // so skip the full-document walk for chunks without any inline
                // styling instead of re-scanning thousands of nodes per chunk.
                var contrastArgs = window.__readerLastContrastArgs;
                if (window.readerAdjustAuthorColorsForContrast && contrastArgs &&
                    /style=|color/i.test(html || '')) {
                    window.readerAdjustAuthorColorsForContrast(contrastArgs.isDark, contrastArgs.bgHex, contrastArgs.textHex);
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

    private fun splitReaderHtmlAtTopLevel(
        html: String,
        chunkNodeCount: Int,
        maxChunkChars: Int = MaxVirtualReaderChunkChars
    ): List<String> {
        if (html.isBlank()) return emptyList()
        val size = chunkNodeCount.coerceAtLeast(1)
        val charBudget = maxChunkChars.coerceAtLeast(MinVirtualReaderChunkChars)
        // Standard Ebooks chapters wrap everything in a single container
        // (<section id="...">); without unwrapping, virtualization collapses to
        // one giant chunk (a 222000px page that stalls scrolling for seconds).
        // Fragment fallbacks land at chapter start either way, which is exactly
        // where the dropped wrapper sat, so nothing observable is lost.
        val (sourceHtml, nodeRanges) = unwrapSingleReaderContainer(html, size, charBudget)
        if (nodeRanges.isEmpty()) return splitLargeReaderChunk(sourceHtml, charBudget)
        val chunks = mutableListOf<String>()
        // Linear accumulation keeps the common case (many small nodes) allocation-bounded:
        // at most MaxInitialVirtualReaderChunks end up inline in verticalDocument.
        val pending = mutableListOf<IntRange>()
        var pendingChars = 0
        for (range in nodeRanges) {
            val nodeChars = range.last - range.first + 1
            if (nodeChars > charBudget) {
                if (pending.isNotEmpty()) {
                    chunks += splitNodeGroup(pending.toList(), sourceHtml, charBudget)
                    pending.clear()
                    pendingChars = 0
                }
                chunks += splitLargeReaderChunk(sourceHtml.substring(range.first, range.last + 1), charBudget)
                continue
            }
            if (pending.size >= size || pendingChars + nodeChars > charBudget) {
                chunks += splitNodeGroup(pending.toList(), sourceHtml, charBudget)
                pending.clear()
                pendingChars = 0
            }
            pending += range
            pendingChars += nodeChars
        }
        if (pending.isNotEmpty()) {
            chunks += splitNodeGroup(pending.toList(), sourceHtml, charBudget)
        }
        return chunks.ifEmpty { listOf(sourceHtml) }
    }

    private fun unwrapSingleReaderContainer(
        html: String,
        chunkNodeCount: Int,
        charBudget: Int
    ): Pair<String, List<IntRange>> {
        var sourceHtml = html
        var ranges = topLevelReaderHtmlNodeRanges(sourceHtml)
        var guard = 0
        while (ranges.size == 1 && guard++ < 8) {
            val only = sourceHtml.substring(ranges.first().first, ranges.first().last + 1)
            val outer = outerReaderHtmlElement(only) ?: break
            if (outer.openTag.readerHtmlTagName() !in ReaderHtmlPassthroughContainers) break
            if (outer.inner.isBlank()) break
            val innerRanges = topLevelReaderHtmlNodeRanges(outer.inner)
            // Small wrappers stay intact: zero behavior change for normal books.
            if (innerRanges.size <= chunkNodeCount && outer.inner.length <= charBudget) break
            if (innerRanges.isEmpty()) break
            sourceHtml = outer.inner
            ranges = innerRanges
        }
        return sourceHtml to ranges
    }

    private fun splitNodeGroup(ranges: List<IntRange>, html: String, charBudget: Int): List<String> {
        if (ranges.isEmpty()) return emptyList()
        val groupHtml = buildString {
            for (range in ranges) append(html.substring(range.first, range.last + 1))
        }
        if (groupHtml.length <= charBudget) return listOf(groupHtml)
        if (ranges.size <= 1) return splitLargeReaderChunk(groupHtml, charBudget)
        val mid = ranges.size / 2
        return splitNodeGroup(ranges.subList(0, mid), html, charBudget) +
            splitNodeGroup(ranges.subList(mid, ranges.size), html, charBudget)
    }

    /**
     * Splits a single oversized top-level node (huge `<pre>`, single-div TXT/HTML
     * chapters) without breaking tags. Prefers whitespace outside markup; falls
     * back to tag-boundary cuts so WKWebView/Android `evaluateJavaScript`
     * payloads stay bounded and `loadHTMLString` never receives a multi-MB blob.
     */
    internal fun splitLargeReaderChunk(html: String, maxChars: Int): List<String> {
        val budget = maxChars.coerceAtLeast(MinVirtualReaderChunkChars)
        if (html.length <= budget) return listOf(html)
        val outer = outerReaderHtmlElement(html)
        if (outer != null) {
            val (openTag, inner, closeTag) = outer
            // If the wrapper itself is huge (attributes), give up on re-wrapping.
            if (openTag.length + closeTag.length + 16 < budget) {
                val innerBudget = budget - openTag.length - closeTag.length
                return splitHtmlByCharsPreservingTags(inner, innerBudget).map { slice ->
                    openTag + slice + closeTag
                }
            }
        }
        return splitHtmlByCharsPreservingTags(html, budget)
    }

    private data class OuterReaderHtmlElement(val openTag: String, val inner: String, val closeTag: String)

    private fun outerReaderHtmlElement(html: String): OuterReaderHtmlElement? {
        val trimmed = html.trim()
        if (!trimmed.startsWith("<") || !trimmed.endsWith(">")) return null
        val openEnd = readerHtmlTagEnd(trimmed, 0)
        if (openEnd < 0) return null
        val openTag = trimmed.substring(0, openEnd + 1)
        if (openTag.startsWith("</") || openTag.startsWith("<!--") || openTag.startsWith("<!") || openTag.startsWith("<?")) return null
        if (openTag.trimEnd().endsWith("/>")) return null
        val tagName = openTag.readerHtmlTagName()
        if (tagName.isBlank() || tagName in ReaderHtmlVoidTags) return null
        val closeTag = "</$tagName>"
        if (!trimmed.endsWith(closeTag, ignoreCase = true)) return null
        val inner = trimmed.substring(openTag.length, trimmed.length - closeTag.length)
        if (inner.isBlank()) return null
        return OuterReaderHtmlElement(openTag, inner, closeTag)
    }

    private fun splitHtmlByCharsPreservingTags(html: String, maxChars: Int): List<String> {
        val budget = maxChars.coerceAtLeast(MinVirtualReaderChunkChars)
        if (html.length <= budget) return listOf(html)
        val slices = mutableListOf<String>()
        var cursor = 0
        while (cursor < html.length) {
            if (html.length - cursor <= budget) {
                slices += html.substring(cursor)
                break
            }
            var cut = cursor + budget
            // Never cut inside `<...>`; back up to the tag start.
            val tagStart = html.lastIndexOf('<', cut - 1)
            if (tagStart >= cursor) {
                val tagEnd = readerHtmlTagEnd(html, tagStart)
                if (tagEnd >= cut) {
                    cut = tagStart
                }
            }
            if (cut <= cursor) cut = cursor + budget
            // Prefer whitespace outside markup so words stay intact.
            var preferred = -1
            var scan = cut - 1
            val scanFloor = maxOf(cursor, cut - 2048)
            while (scan >= scanFloor) {
                val char = html[scan]
                if (char.isWhitespace()) {
                    // Verify the whitespace is not inside a tag.
                    val open = html.lastIndexOf('<', scan)
                    val close = html.lastIndexOf('>', scan)
                    if (open < 0 || close > open) {
                        preferred = scan + 1
                        break
                    }
                }
                scan--
            }
            if (preferred > cursor && preferred < html.length) cut = preferred
            // Avoid empty progress on pathological markup without whitespace.
            if (cut <= cursor) cut = minOf(html.length, cursor + budget)
            slices += html.substring(cursor, cut)
            cursor = cut
        }
        return slices.ifEmpty { listOf(html) }
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
        val nodeCount = topLevelReaderHtmlNodeRanges(html).size.coerceAtLeast(1)
        val nodeHeight = nodeCount * EstimatedVirtualReaderNodeHeightPx
        // Char-split slices of a huge single node report a single top-level node;
        // estimate from visible text so the placeholder still reserves scroll range.
        val visibleChars = html.replace(Regex("<[^>]+>"), "").length
        val textHeight = (visibleChars * EstimatedVirtualReaderCharHeightPxNumerator / EstimatedVirtualReaderCharHeightPxDenominator)
            .coerceIn(0, MaxVirtualReaderPlaceholderHeightPx)
        // Illustrations without decoded dimensions (or before they load) would
        // otherwise reserve a single 72px row and collapse into blank gaps that
        // only fill in seconds later as bitmaps decode mid-scroll.
        val imageHeight = countReaderImages(html) * EstimatedVirtualReaderImageHeightPx
        return maxOf(nodeHeight + imageHeight, EstimatedVirtualReaderNodeHeightPx, textHeight)
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
    // Typical column-scaled illustration height before real dimensions are known.
    const val EstimatedVirtualReaderImageHeightPx = 280
    // Keeps WKWebView loadHTMLString and Android/iOS evaluateJavaScript payloads
    // bounded: 120k chars stays well under IPC/JS string limits even after JSON escaping.
    const val MaxVirtualReaderChunkChars = 120_000
    const val MinVirtualReaderChunkChars = 8_000
    // Navigation/search scripts inline small chunks for instant landing; larger
    // chunks are fetched through the chunk bridge so a multi-MB chapter cannot
    // blow up a single evaluateJavaScript call.
    const val MaxInlineVirtualChunkChars = 80_000
    // ~0.4px per visible char (≈60 chars/line at 24px line height on mobile).
    private const val EstimatedVirtualReaderCharHeightPxNumerator = 2
    private const val EstimatedVirtualReaderCharHeightPxDenominator = 5
    private const val MaxVirtualReaderPlaceholderHeightPx = 60_000
    private val ReaderHtmlVoidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
    // Structural wrappers that carry no content of their own; a chapter using
    // exactly one of these as its body child is chunked by its children.
    private val ReaderHtmlPassthroughContainers = setOf("section", "div", "article", "main", "aside")

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
            textureDataUri = textureDataUri,
            documentLanguage = book.language
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
              if (window.readerAdjustAuthorColorsForContrast) {
                window.readerAdjustAuthorColorsForContrast(
                  ${if (settings.darkMode) "true" else "false"},
                  ${appearance.background.toJsStringLiteral()},
                  ${appearance.foreground.toJsStringLiteral()}
                );
              }
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
              if (container) container.innerHTML = ${highlightButtons.toJsStringLiteral()};
              if (window.readerSyncSelectionStyles) window.readerSyncSelectionStyles();
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