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
import kotlin.math.abs
import kotlin.math.pow
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
        externalLookupEnabled: Boolean = true,
        textureDataUri: String? = null,
        renderedChapterRange: IntRange? = null
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
            pageAnchors = pages,
            readerAiFeaturesEnabled = readerAiFeaturesEnabled,
            cloudTtsEnabled = cloudTtsEnabled,
            externalLookupEnabled = externalLookupEnabled,
            textureDataUri = textureDataUri
        )
    }

    fun pageDocument(
        book: SharedEpubBook,
        page: ReaderPage?,
        visiblePages: List<ReaderPage> = listOfNotNull(page),
        settings: ReaderSettings,
        searchQuery: String = "",
        searchOptions: ReaderSearchOptions = ReaderSearchOptions(),
        highlights: List<UserHighlight> = emptyList(),
        highlightPalette: ReaderHighlightPalette = ReaderHighlightPalette(),
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
              root.style.setProperty('--reader-line-height', ${settings.lineSpacing.toString().toJsStringLiteral()});
              root.style.setProperty('--reader-page-width', ${"${settings.pageWidth}px".toJsStringLiteral()});
              root.style.setProperty('--reader-margin', ${"${settings.margin}px".toJsStringLiteral()});
              root.style.setProperty('--reader-margin-x', ${"${settings.resolvedHorizontalMargin}px".toJsStringLiteral()});
              root.style.setProperty('--reader-margin-y', ${"${settings.resolvedVerticalMargin}px".toJsStringLiteral()});
              root.style.setProperty('--reader-vertical-margin-y', ${"${settings.readerVerticalMarginY()}px".toJsStringLiteral()});
              root.style.setProperty('--reader-vertical-page-width', 'max(0px, calc(100% - (var(--reader-margin-x) * 2)))');
              root.style.setProperty('--reader-paragraph-spacing', ${settings.paragraphSpacing.toString().toJsStringLiteral()});
              root.style.setProperty('--reader-image-scale', ${settings.readerImageScaleCss().toJsStringLiteral()});
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
        externalLookupEnabled: Boolean,
        textureDataUri: String?
    ): String {
        val appearance = settings.toDocumentAppearanceCss(textureDataUri)
        val align = settings.readerTextAlignCss()
        val customFontCss = settings.readerCustomFontFaceCss()
        val family = settings.readerFontFamilyCss()
        val highlightButtons = highlightPalette.toSelectionPaletteButtons()
        val defineButton = if (readerAiFeaturesEnabled) {
            readerSelectionActionButton("define", "Define", ReaderSelectionIconDefinePath)
        } else {
            ""
        }
        val speakButton = if (cloudTtsEnabled) {
            readerSelectionActionButton("speak", "Speak", ReaderSelectionIconSpeakPath)
        } else {
            ""
        }
        val externalLookupButtons = if (externalLookupEnabled) {
            readerSelectionActionButton("web-search", "Search", ReaderSelectionIconSearchPath)
        } else {
            ""
        }
        val navigationAttributes = navigationLocator?.toNavigationAttributes().orEmpty()
        val pageAnchorJson = pageAnchors.toPageAnchorJson()
        val verticalMarginY = settings.readerVerticalMarginY()
        return """
            <!doctype html>
            <html class="${if (settings.readingMode == ReaderReadingMode.PAGINATED) "reader-paginated-root" else "reader-vertical-root"}">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${title.escapeHtml()}</title>
              <style>
                $bookCss
                $customFontCss
                :root {
                  color-scheme: ${appearance.colorScheme};
                  --reader-bg: ${appearance.background};
                  --reader-fg: ${appearance.foreground};
                  --reader-link: ${appearance.linkColors.color};
                  --reader-link-decoration: ${appearance.linkColors.decoration};
                  --reader-link-bg: ${appearance.linkColors.background};
                  --reader-highlight: ${appearance.highlight};
                  --reader-scrollbar-track: color-mix(in srgb, var(--reader-bg) 88%, var(--reader-fg));
                  --reader-scrollbar-thumb: color-mix(in srgb, var(--reader-fg) 48%, var(--reader-bg));
                  --reader-scrollbar-thumb-hover: var(--reader-link);
                  --reader-font-size: ${settings.fontSize}px;
                  --reader-line-height: ${settings.lineSpacing};
                  --reader-page-width: ${settings.pageWidth}px;
                  --reader-margin: ${settings.margin}px;
                  --reader-margin-x: ${settings.resolvedHorizontalMargin}px;
                  --reader-margin-y: ${settings.resolvedVerticalMargin}px;
                  --reader-vertical-margin-y: ${verticalMarginY}px;
                  --reader-vertical-content-width: 92ch;
                  --reader-vertical-page-width: max(0px, calc(100% - (var(--reader-margin-x) * 2)));
                  --reader-paragraph-spacing: ${settings.paragraphSpacing};
                  --reader-image-scale: ${settings.readerImageScaleCss()};
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
                html {
                  scrollbar-color: var(--reader-scrollbar-thumb) var(--reader-scrollbar-track);
                  scrollbar-width: thin;
                }
                html.reader-vertical-root {
                  width: 100%;
                  min-width: 0;
                  overflow-y: scroll;
                  scrollbar-width: thin;
                }
                html::-webkit-scrollbar,
                body.reader-vertical::-webkit-scrollbar {
                  width: 12px;
                  height: 12px;
                }
                html::-webkit-scrollbar-track,
                body.reader-vertical::-webkit-scrollbar-track {
                  background: var(--reader-scrollbar-track);
                  border-radius: 999px;
                }
                html::-webkit-scrollbar-thumb,
                body.reader-vertical::-webkit-scrollbar-thumb {
                  background: var(--reader-scrollbar-thumb);
                  border: 3px solid var(--reader-bg);
                  border-radius: 999px;
                }
                html::-webkit-scrollbar-thumb:hover,
                body.reader-vertical::-webkit-scrollbar-thumb:hover {
                  background: var(--reader-scrollbar-thumb-hover);
                }
                body {
                  box-sizing: border-box;
                  padding: var(--reader-margin-y) var(--reader-margin-x);
                  overflow-wrap: anywhere;
                  position: relative;
                }
                body.reader-vertical {
                  width: 100%;
                  max-width: 100%;
                  min-height: 100vh;
                  min-height: 100dvh;
                  min-width: 0;
                  overflow-x: hidden;
                  overflow-y: auto;
                  padding: var(--reader-vertical-margin-y) 0;
                  scrollbar-gutter: stable;
                }
                body.reader-paginated {
                  height: 100vh;
                  overflow: hidden;
                }
                .chapter, .page {
                  max-width: var(--reader-page-width);
                  margin: 0 auto 48px;
                  text-align: var(--reader-align);
                  position: relative;
                  z-index: 1;
                }
                body.reader-vertical .chapter {
                  content-visibility: auto;
                  contain-intrinsic-size: auto 1200px;
                }
                body.reader-vertical > .chapter,
                body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
                body.reader-vertical > .chapter > :not(.reader-content),
                body.reader-vertical > .chapter > .chapter-title,
                body.reader-vertical > .chapter > .reader-content {
                  box-sizing: border-box !important;
                  min-width: 0 !important;
                }
                body.reader-vertical > .chapter {
                  width: 100% !important;
                  max-width: none !important;
                  margin: 0 !important;
                }
                body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
                body.reader-vertical > .chapter > :not(.reader-content),
                body.reader-vertical > .chapter > .chapter-title,
                body.reader-vertical > .chapter > .reader-content {
                  width: var(--reader-vertical-page-width) !important;
                  max-width: none !important;
                  margin-left: auto !important;
                  margin-right: auto !important;
                }
                body.reader-vertical > :not(.chapter):not(#reader-selection-menu):not(.reader-selection-handle):not(script):not(style),
                body.reader-vertical > .chapter > :not(.reader-content) {
                  position: static !important;
                  left: auto !important;
                  right: auto !important;
                  top: auto !important;
                  bottom: auto !important;
                  transform: none !important;
                  float: none !important;
                  clear: none !important;
                }
                body.reader-vertical .reader-content :where(h1, h2, h3, h4, h5, h6, hgroup, center, [class*="title" i], [id*="title" i], [class*="heading" i], [id*="heading" i], [class*="dedication" i], [id*="dedication" i]) {
                  box-sizing: border-box !important;
                  width: auto !important;
                  max-width: 100% !important;
                  min-width: 0 !important;
                  margin-left: 0 !important;
                  margin-right: 0 !important;
                  padding-left: 0 !important;
                  padding-right: 0 !important;
                  text-indent: 0 !important;
                  position: static !important;
                  left: auto !important;
                  right: auto !important;
                  transform: none !important;
                  float: none !important;
                  clear: none !important;
                }
                body.reader-vertical .reader-content,
                body.reader-vertical .reader-content p,
                body.reader-vertical .reader-content li,
                body.reader-vertical .reader-content div,
                body.reader-vertical .reader-content h1,
                body.reader-vertical .reader-content h2,
                body.reader-vertical .reader-content h3,
                body.reader-vertical .reader-content h4,
                body.reader-vertical .reader-content h5,
                body.reader-vertical .reader-content h6,
                body.reader-vertical .reader-content blockquote {
                  text-align: var(--reader-align) !important;
                }
                body.reader-vertical .reader-content p,
                body.reader-vertical .reader-content div,
                body.reader-vertical .reader-content h1,
                body.reader-vertical .reader-content h2,
                body.reader-vertical .reader-content h3,
                body.reader-vertical .reader-content h4,
                body.reader-vertical .reader-content h5,
                body.reader-vertical .reader-content h6,
                body.reader-vertical .reader-content blockquote,
                body.reader-vertical .reader-content section,
                body.reader-vertical .reader-content article,
                body.reader-vertical .reader-content header,
                body.reader-vertical .reader-content footer,
                body.reader-vertical .reader-content aside,
                body.reader-vertical .reader-content figure,
                body.reader-vertical .reader-content table,
                body.reader-vertical .reader-content pre {
                  box-sizing: border-box !important;
                  max-width: 100% !important;
                  min-width: 0 !important;
                  position: static !important;
                  left: auto !important;
                  right: auto !important;
                  top: auto !important;
                  bottom: auto !important;
                  transform: none !important;
                  float: none !important;
                  clear: none !important;
                }
                body.reader-vertical .reader-content div,
                body.reader-vertical .reader-content section,
                body.reader-vertical .reader-content article,
                body.reader-vertical .reader-content header,
                body.reader-vertical .reader-content footer,
                body.reader-vertical .reader-content aside,
                body.reader-vertical .reader-content figure {
                  width: auto !important;
                  margin-left: 0 !important;
                  margin-right: 0 !important;
                }
                body.reader-vertical .reader-content > p,
                body.reader-vertical .reader-content > div,
                body.reader-vertical .reader-content > h1,
                body.reader-vertical .reader-content > h2,
                body.reader-vertical .reader-content > h3,
                body.reader-vertical .reader-content > h4,
                body.reader-vertical .reader-content > h5,
                body.reader-vertical .reader-content > h6,
                body.reader-vertical .reader-content > blockquote,
                body.reader-vertical .reader-content > section,
                body.reader-vertical .reader-content > article,
                body.reader-vertical .reader-content > header,
                body.reader-vertical .reader-content > footer,
                body.reader-vertical .reader-content > aside,
                body.reader-vertical .reader-content > figure,
                body.reader-vertical .reader-content > table,
                body.reader-vertical .reader-content > pre {
                  margin-left: 0 !important;
                  margin-right: 0 !important;
                }
                body.reader-paginated .page {
                  box-sizing: border-box;
                  height: calc(100vh - (var(--reader-margin-y) * 2));
                  margin-bottom: 0;
                  overflow: hidden;
                }
                body.reader-paginated .reader-content > :last-child {
                  margin-bottom: 0 !important;
                }
                .reader-spread {
                  display: grid;
                  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
                  gap: 28px;
                  width: min(100%, calc((var(--reader-page-width) * 2) + 28px));
                  height: calc(100vh - (var(--reader-margin-y) * 2));
                  margin: 0 auto;
                  position: relative;
                  z-index: 1;
                }
                .reader-spread .page {
                  width: 100%;
                  max-width: none;
                  min-width: 0;
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
                h1, h2, h3, h4, h5, h6 {
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
                span[class*="user-highlight-"],
                mark.reader-user-highlight {
                  border-radius: 2px;
                  cursor: pointer;
                  -webkit-box-decoration-break: clone;
                  box-decoration-break: clone;
                }
                ::highlight(reader-tts-highlight) {
                  background: rgba(125, 211, 252, 0.52);
                  color: inherit;
                }
                #reader-tts-highlight-layer {
                  position: absolute;
                  inset: 0;
                  z-index: 3;
                  pointer-events: none;
                }
                .reader-tts-highlight-rect {
                  position: absolute;
                  background: rgba(125, 211, 252, 0.42);
                  border-radius: 3px;
                  box-shadow: 0 0 0 1px rgba(14, 116, 144, 0.12);
                }
                ${HighlightColor.entries.joinToString("\n") { ".${it.cssClass} { background-color: ${it.color.toCssRgba(0.4f)} !important; }" }}
                #reader-selection-menu {
                  position: fixed;
                  z-index: 99999;
                  display: none;
                  flex-direction: column;
                  width: max-content;
                  max-width: min(280px, calc(100vw - 16px));
                  padding: 0 0 6px;
                  border-radius: 14px;
                  background: color-mix(in srgb, var(--reader-bg) 92%, var(--reader-fg));
                  border: 1px solid color-mix(in srgb, var(--reader-fg) 18%, transparent);
                  box-shadow: 0 18px 44px rgba(0, 0, 0, 0.28);
                  max-height: calc(100vh - 16px);
                  overflow: auto;
                }
                #reader-selection-menu button {
                  border: 0;
                  background: transparent;
                  color: var(--reader-fg);
                  font: 600 12px system-ui, sans-serif;
                  cursor: pointer;
                }
                #reader-selection-menu button:hover {
                  background: color-mix(in srgb, var(--reader-fg) 10%, transparent);
                }
                #reader-selection-menu .reader-selection-colors {
                  display: flex;
                  justify-content: center;
                  align-items: center;
                  gap: 8px;
                  width: 100%;
                  box-sizing: border-box;
                  padding: 8px 10px;
                  border-bottom: 1px solid color-mix(in srgb, var(--reader-fg) 12%, transparent);
                  overflow-x: auto;
                }
                #reader-selection-menu .reader-selection-color {
                  width: 28px;
                  height: 28px;
                  flex: 0 0 auto;
                  padding: 0;
                  border-radius: 999px;
                  background: var(--selection-color);
                  box-shadow: inset 0 0 0 1px color-mix(in srgb, var(--reader-fg) 18%, transparent);
                }
                #reader-selection-menu .reader-selection-spectrum {
                  width: 28px;
                  height: 28px;
                  flex: 0 0 auto;
                  padding: 0;
                  border-radius: 999px;
                  background: conic-gradient(#f44336, #ff7f00, #ffeb3b, #4caf50, #2196f3, #4b0082, #8b00ff, #f44336);
                }
                #reader-selection-menu .reader-selection-actions {
                  display: grid;
                  grid-template-columns: repeat(3, 70px);
                  gap: 3px;
                  padding: 5px 6px 2px;
                }
                #reader-selection-menu .reader-selection-action {
                  min-height: 56px;
                  border-radius: 10px;
                  display: flex;
                  flex-direction: column;
                  align-items: center;
                  justify-content: center;
                  gap: 4px;
                  padding: 6px 4px 7px;
                  line-height: 1.15;
                  white-space: nowrap;
                }
                #reader-selection-menu .reader-selection-action span:last-child {
                  display: block;
                  line-height: 1.2;
                  padding-bottom: 1px;
                }
                #reader-selection-menu .reader-selection-icon {
                  display: grid;
                  place-items: center;
                  width: 22px;
                  height: 22px;
                  border-radius: 999px;
                  background: color-mix(in srgb, var(--reader-fg) 9%, transparent);
                  color: color-mix(in srgb, var(--reader-fg) 86%, transparent);
                }
                #reader-selection-menu .reader-selection-icon svg {
                  width: 16px;
                  height: 16px;
                  display: block;
                  fill: currentColor;
                }
                .reader-selection-handle {
                  position: fixed;
                  z-index: 99998;
                  display: none;
                  width: 24px;
                  height: 24px;
                  padding: 0;
                  border: 0;
                  background: transparent;
                  color: #2563eb;
                  cursor: ew-resize;
                  touch-action: none;
                }
                .reader-selection-handle svg {
                  width: 24px;
                  height: 24px;
                  display: block;
                  fill: currentColor;
                  filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.28));
                }
                .reader-selection-handle-start svg {
                  transform: rotate(30deg);
                  transform-origin: 50% 0;
                }
                .reader-selection-handle-end svg {
                  transform: rotate(-30deg);
                  transform-origin: 50% 0;
                }
                .reader-content a[href],
                .reader-content a[href]:link,
                .reader-content a[href]:visited,
                a[href],
                a[href]:link,
                a[href]:visited,
                a[data-reader-link="true"] {
                  color: var(--reader-link) !important;
                  cursor: pointer;
                  text-decoration-line: underline !important;
                  text-decoration-color: var(--reader-link-decoration) !important;
                  text-decoration-thickness: 0.08em;
                  text-decoration-thickness: max(1px, 0.08em);
                  text-underline-offset: 0.14em;
                  text-decoration-skip-ink: auto;
                  background-image: linear-gradient(transparent 62%, var(--reader-link-bg) 62%);
                  border-radius: 2px;
                }
                .reader-content a[href] *,
                a[href] *,
                a[data-reader-link="true"] * {
                  color: var(--reader-link) !important;
                  text-decoration-color: var(--reader-link-decoration) !important;
                }
              </style>
              <style id="reader-texture-style">${appearance.textureOverlayCss}</style>
            </head>
            <body class="${if (settings.readingMode == ReaderReadingMode.PAGINATED) "reader-paginated" else "reader-vertical"}" data-search="${searchQuery.escapeHtml()}"$navigationAttributes>
              $body
              <div id="reader-selection-menu" role="toolbar" aria-label="Selection actions">
                <div class="reader-selection-colors" aria-label="Highlight colors">
                  $highlightButtons
                </div>
                <div class="reader-selection-actions">
                  ${readerSelectionActionButton("copy", "Copy", ReaderSelectionIconCopyPath)}
                  $defineButton
                  $speakButton
                  $externalLookupButtons
                  ${readerSelectionActionButton("clear", "Clear", ReaderSelectionIconClearPath)}
                </div>
              </div>
              <button type="button" id="reader-selection-start-handle" class="reader-selection-handle reader-selection-handle-start" aria-label="Adjust selection start" hidden>
                ${readerSelectionSvg(ReaderSelectionIconTeardropPath)}
              </button>
              <button type="button" id="reader-selection-end-handle" class="reader-selection-handle reader-selection-handle-end" aria-label="Adjust selection end" hidden>
                ${readerSelectionSvg(ReaderSelectionIconTeardropPath)}
              </button>
              <script>
                (function () {
                  var readerDiagnosticsConsoleEnabled = ${SharedReaderDiagnosticsEnabled};
                  function readerConsoleLog(line) {
                    if (readerDiagnosticsConsoleEnabled) {
                      try { console.log(line); } catch (error) {}
                    }
                  }
                  var menu = document.getElementById('reader-selection-menu');
                  var startHandle = document.getElementById('reader-selection-start-handle');
                  var endHandle = document.getElementById('reader-selection-end-handle');
                  var savedRange = null;
                  var readerPageAnchors = $pageAnchorJson;
                  window.readerSetPageAnchors = function (anchors) {
                    if (Array.isArray(anchors)) {
                      readerPageAnchors = anchors;
                    }
                  };
                  var lastReportedPageIndex = -1;
                  var lastReportedStartOffset = -1;
                  var pendingRestoreLocator = null;
                  var pendingRestoreUntil = 0;
                  var reportTimer = null;
                  var selectionMenuTimer = null;
                  var readerCurrentHighlights = [];
                  var readerHighlightReconcileTimer = null;
                  var selectionPointerDown = false;
                  var activeSelectionHandle = null;
                  var selectionHandleFrame = null;
                  var pendingSelectionHandleEvent = null;
                  var selectionDebugSequence = 0;
                  var selectionDebugLastLineKey = null;
                  var selectionDebugLastAt = 0;
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
                    readerConsoleLog(line);
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try { window.kmpJsBridge.callNative('readerTtsHighlightLog', JSON.stringify({ message: line })); } catch (error) {}
                    }
                  }
                  window.readerTtsLog = readerTtsLog;
                  function readerSelectionDebugLog(message) {
                    var line = 'EPUB_SELECTION_DEBUG ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerSelectionDebugLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  window.readerSelectionDebugLog = readerSelectionDebugLog;
                  function readerHighlightFlowLog(message) {
                    var line = 'EpistemeEpubHighlightFlow ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerHighlightFlowLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  window.readerHighlightFlowLog = readerHighlightFlowLog;
                  function readerDesktopHighlightMapLog(message) {
                    var line = 'EpistemeDesktopHighlightMap ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerDesktopHighlightMapLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  window.readerDesktopHighlightMapLog = readerDesktopHighlightMapLog;
                  function readerDesktopPositionTraceLog(message) {
                    var line = 'EpistemeDesktopPositionTrace ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerDesktopPositionTraceLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  window.readerDesktopPositionTraceLog = readerDesktopPositionTraceLog;
                  function readerTtsStartTraceLog(message) {
                    var line = 'EpistemeDesktopTtsStartTrace ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerTtsStartTraceLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  window.readerTtsStartTraceLog = readerTtsStartTraceLog;
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
                  function readerPaginationLog(message) {
                    var line = 'EpistemeEpubPagination ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerPaginationLayoutLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  function readerGapLog(message) {
                    var line = 'EpistemeReaderGap ' + message;
                    var delivered = false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerGapLayoutLog', JSON.stringify({ message: message }));
                        delivered = true;
                      } catch (error) {}
                    }
                    if (!delivered) {
                      readerConsoleLog(line);
                    }
                  }
                  function readerPaginationLayoutLog(reason) {
                    if (!document.body || !document.body.classList.contains('reader-paginated')) return;
                    var pages = Array.prototype.slice.call(document.querySelectorAll('.page[data-reader-page-index]'));
                    var mode = document.querySelector('.reader-spread') ? 'spread' : 'single';
                    var bodyStyle = window.getComputedStyle(document.body);
                    var bodyPaddingTop = parseFloat(bodyStyle.paddingTop) || 0;
                    var bodyPaddingBottom = parseFloat(bodyStyle.paddingBottom) || 0;
                    var bodyPaddingY = bodyPaddingTop + bodyPaddingBottom;
                    pages.forEach(function (page) {
                      var content = page.querySelector('.reader-content') || page;
                      var pageRect = page.getBoundingClientRect();
                      var contentRect = content.getBoundingClientRect();
                      var children = Array.prototype.slice.call(content.children || []);
                      var last = children.length ? children[children.length - 1] : null;
                      var lastRect = last ? last.getBoundingClientRect() : null;
                      var lastStyle = last ? window.getComputedStyle(last) : null;
                      var lastMarginBottom = lastStyle ? (parseFloat(lastStyle.marginBottom) || 0) : 0;
                      var pageOverflow = (page.scrollHeight || 0) - (page.clientHeight || 0);
                      var contentOverflow = contentRect.bottom - pageRect.bottom;
                      var lastOverflow = lastRect ? (lastRect.bottom + lastMarginBottom - pageRect.bottom) : 0;
                      var overflowPx = Math.ceil(Math.max(0, pageOverflow, contentOverflow, lastOverflow));
                      var lastBottomWithMargin = lastRect ? lastRect.bottom + lastMarginBottom : contentRect.bottom;
                      var contentTopGap = contentRect.top - pageRect.top;
                      var contentBottomGap = pageRect.bottom - contentRect.bottom;
                      var lastBottomGap = pageRect.bottom - lastBottomWithMargin;
                      var pageIndex = numberAttribute(page, 'data-reader-page-index', -1);
                      var chapterIndex = numberAttribute(page, 'data-reader-chapter-index', -1);
                      var startOffset = numberAttribute(page, 'data-reader-page-start', -1);
                      var endOffset = numberAttribute(page, 'data-reader-page-end', -1);
                      var contentText = (content.textContent || '').replace(/\s+/g, ' ').trim();
                      var lastText = last ? (last.textContent || '').replace(/\s+/g, ' ').trim().substring(0, 80) : '';
                      readerPaginationLog(
                        'render_layout reason=' + (reason || 'load') +
                        ' mode=' + mode +
                        ' page=' + (pageIndex + 1) +
                        ' chapter=' + chapterIndex +
                        ' range=' + startOffset + '..' + endOffset +
                        ' overflowPx=' + overflowPx +
                        ' pageClient=' + page.clientWidth + 'x' + page.clientHeight +
                        ' pageScroll=' + page.scrollWidth + 'x' + page.scrollHeight +
                        ' pageRect=' + Math.round(pageRect.width) + 'x' + Math.round(pageRect.height) +
                        ' contentBottom=' + Math.round(contentRect.bottom - pageRect.top) +
                        ' last=' + readerElementLabel(last) +
                        ' lastBottom=' + (lastRect ? Math.round(lastRect.bottom - pageRect.top) : 'null') +
                        ' lastMarginBottom=' + Math.round(lastMarginBottom) +
                        ' bodyPaddingY=' + Math.round(bodyPaddingY) +
                        ' textChars=' + contentText.length +
                        ' lastText="' + lastText.replace(/"/g, '\\"') + '"'
                      );
                      readerGapLog(
                        'web_page layer=paginated_dom reason=' + (reason || 'load') +
                        ' mode=' + mode +
                        ' page=' + (pageIndex + 1) +
                        ' chapter=' + chapterIndex +
                        ' viewport=' + window.innerWidth + 'x' + window.innerHeight +
                        ' documentClient=' + document.documentElement.clientWidth + 'x' + document.documentElement.clientHeight +
                        ' bodyClient=' + document.body.clientWidth + 'x' + document.body.clientHeight +
                        ' bodyScroll=' + document.body.scrollWidth + 'x' + document.body.scrollHeight +
                        ' bodyPaddingTop=' + Math.round(bodyPaddingTop) +
                        ' bodyPaddingBottom=' + Math.round(bodyPaddingBottom) +
                        ' pageTop=' + Math.round(pageRect.top) +
                        ' pageBottom=' + Math.round(pageRect.bottom) +
                        ' pageHeight=' + Math.round(pageRect.height) +
                        ' pageClient=' + page.clientWidth + 'x' + page.clientHeight +
                        ' pageScroll=' + page.scrollWidth + 'x' + page.scrollHeight +
                        ' contentTopGap=' + Math.round(contentTopGap) +
                        ' contentBottomGap=' + Math.round(contentBottomGap) +
                        ' lastBottomGap=' + Math.round(lastBottomGap) +
                        ' lastMarginBottom=' + Math.round(lastMarginBottom) +
                        ' overflowPx=' + overflowPx +
                        ' range=' + startOffset + '..' + endOffset
                      );
                    });
                  }
                  window.readerPaginationLayoutLog = readerPaginationLayoutLog;
                  function readerHostsForLocator(chapterIndex, startOffset, endOffset) {
                    var selector = '[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]';
                    var hosts = Array.prototype.slice.call(document.querySelectorAll(selector));
                    if (!hosts.length) return [];
                    var parsedStart = parseInt(startOffset, 10);
                    var parsedEnd = parseInt(endOffset === undefined || endOffset === null ? parsedStart : endOffset, 10);
                    var hasOffsets = Number.isFinite(parsedStart);
                    if (!hasOffsets) return [hosts[0]];
                    var rangeEnd = Number.isFinite(parsedEnd) && parsedEnd >= parsedStart ? parsedEnd : parsedStart;
                    var containing = hosts.filter(function (host) {
                      var pageStart = numberAttribute(host, 'data-reader-page-start', null);
                      var pageEnd = numberAttribute(host, 'data-reader-page-end', null);
                      if (pageStart === null || pageEnd === null) return true;
                      if (parsedStart === rangeEnd) return parsedStart >= pageStart && parsedStart <= pageEnd;
                      return parsedStart < pageEnd && rangeEnd > pageStart;
                    });
                    if (containing.length) return containing;
                    var best = hosts.reduce(function (best, host) {
                      var bestStart = numberAttribute(best, 'data-reader-page-start', 0);
                      var hostStart = numberAttribute(host, 'data-reader-page-start', 0);
                      return Math.abs(hostStart - parsedStart) < Math.abs(bestStart - parsedStart) ? host : best;
                    }, hosts[0]);
                    return [best];
                  }
                  function readerHostForLocator(chapterIndex, startOffset, endOffset) {
                    var hosts = readerHostsForLocator(chapterIndex, startOffset, endOffset);
                    return hosts.length ? hosts[0] : null;
                  }
                  function readerBlockElementsForLocator(chapterIndex, blockIndex) {
                    var parsedBlock = parseInt(blockIndex, 10);
                    if (!Number.isFinite(parsedBlock)) return [];
                    var chapterSelector = '[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]';
                    var chapters = Array.prototype.slice.call(document.querySelectorAll(chapterSelector));
                    var blockSelector = '[data-reader-block-index="' + selectorValue(parsedBlock) + '"]';
                    return chapters.reduce(function (elements, chapter) {
                      return elements.concat(Array.prototype.slice.call(chapter.querySelectorAll(blockSelector)));
                    }, []);
                  }
                  function readerElementForBlockLocator(chapterIndex, blockIndex, charOffset) {
                    var elements = readerBlockElementsForLocator(chapterIndex, blockIndex);
                    if (!elements.length) return null;
                    var parsedChar = parseInt(charOffset, 10);
                    if (!Number.isFinite(parsedChar)) return elements[0];
                    var fallback = null;
                    for (var i = 0; i < elements.length; i++) {
                      var element = elements[i];
                      if (!fallback) fallback = element;
                      var textStart = numberAttribute(element, 'data-reader-text-start', null);
                      var textEnd = numberAttribute(element, 'data-reader-text-end', null);
                      if (textStart === null || textEnd === null) continue;
                      var host = element.closest ? element.closest('[data-reader-chapter-index]') : null;
                      var pageStart = host ? numberAttribute(host, 'data-reader-page-start', null) : null;
                      var pageEnd = host ? numberAttribute(host, 'data-reader-page-end', null) : null;
                      if (pageStart !== null && pageEnd !== null && !((parsedChar >= pageStart && parsedChar < pageEnd) || (pageStart === pageEnd && parsedChar === pageStart))) continue;
                      if ((parsedChar >= textStart && parsedChar < textEnd) || (textStart === textEnd && parsedChar === textStart)) {
                        return element;
                      }
                    }
                    return fallback;
                  }
                  function readerHostForBlockLocator(chapterIndex, blockIndex, charOffset) {
                    var element = readerElementForBlockLocator(chapterIndex, blockIndex, charOffset);
                    if (!element) return null;
                    return element.closest ? element.closest('[data-reader-chapter-index]') : null;
                  }
                  function readerLocatorTrace(locator) {
                    locator = locator || {};
                    return 'chapter=' + (locator.chapterIndex === undefined || locator.chapterIndex === null ? 'null' : locator.chapterIndex) +
                      ' page=' + (locator.pageIndex === undefined || locator.pageIndex === null ? 'null' : locator.pageIndex) +
                      ' offsets=' + (locator.startOffset === undefined || locator.startOffset === null ? 'null' : locator.startOffset) +
                      '..' + (locator.endOffset === undefined || locator.endOffset === null ? 'null' : locator.endOffset) +
                      ' block=' + (locator.blockIndex === undefined || locator.blockIndex === null ? 'null' : locator.blockIndex) +
                      ' char=' + (locator.charOffset === undefined || locator.charOffset === null ? 'null' : locator.charOffset) +
                      ' cfi=' + readerTtsPreview(locator.cfi, 160);
                  }
                  function stableReaderCfi(cfi) {
                    if (cfi === undefined || cfi === null) return null;
                    var value = String(cfi);
                    if (value.indexOf('desktop-scroll:') !== 0) return value;
                    var parts = value.split(':');
                    if (parts.length < 4) return value;
                    var stable = parts.slice(3).join(':');
                    return stable || value;
                  }
                  function stableDesktopCfi(chapterIndex, startOffset, endOffset) {
                    return 'desktop:' + chapterIndex + ':' + startOffset + ':' + (endOffset === undefined || endOffset === null ? startOffset : endOffset);
                  }
                  function locatorStartOffset(locator) {
                    locator = locator || {};
                    var start = parseInt(locator.startOffset, 10);
                    if (Number.isFinite(start)) return start;
                    var charOffset = parseInt(locator.charOffset, 10);
                    if (Number.isFinite(charOffset)) return charOffset;
                    var cfi = stableReaderCfi(locator.cfi);
                    if (cfi && cfi.indexOf('desktop:') === 0) {
                      var parts = cfi.split(':');
                      var parsed = parseInt(parts[2], 10);
                      if (Number.isFinite(parsed)) return parsed;
                    }
                    if (cfi && cfi.indexOf('android-locator:') === 0) {
                      var androidParts = cfi.split(':');
                      var androidChar = parseInt(androidParts[3], 10);
                      if (Number.isFinite(androidChar)) return androidChar;
                    }
                    return null;
                  }
                  function prepareVerticalScrollMeasurement(targetChapter) {
                    if (!isVerticalReaderDocument()) return;
                    var chapters = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
                    chapters.forEach(function (chapter) {
                      chapter.style.contentVisibility = 'visible';
                      chapter.style.containIntrinsicSize = 'auto';
                    });
                    if (targetChapter) {
                      targetChapter.style.contentVisibility = 'visible';
                      targetChapter.style.containIntrinsicSize = 'auto';
                    }
                    if (document.body) void document.body.offsetHeight;
                    if (targetChapter) void targetChapter.offsetHeight;
                  }
                  function scrollToTopWithTrace(targetTop, strategy, locator, extra) {
                    var before = verticalScrollMetrics();
                    var top = Math.max(0, Math.round(Number(targetTop) || 0));
                    window.scrollTo({ top: top, left: 0, behavior: 'auto' });
                    var after = verticalScrollMetrics();
                    var clamped = Math.abs(after.scrollY - top) > 2 && (top > after.maxScroll + 2 || top < 0);
                    readerDesktopPositionTraceLog(
                      'event=web_scroll_to_locator_done mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                      ' strategy=' + strategy +
                      ' targetY=' + top +
                      ' beforeY=' + before.scrollY +
                      ' afterY=' + after.scrollY +
                      ' maxScroll=' + after.maxScroll +
                      ' clamped=' + clamped +
                      ' ' + readerLocatorTrace(locator) +
                      (extra ? ' ' + extra : '')
                    );
                    return { targetY: top, beforeY: before.scrollY, afterY: after.scrollY, maxScroll: after.maxScroll, clamped: clamped };
                  }
                  function shouldCenterScrollTarget(options) {
                    return !!(options && options.align === 'center' && isVerticalReaderDocument());
                  }
                  function scrollTargetTopFromRect(rect, options) {
                    var documentTop = (rect ? rect.top : 0) + window.scrollY;
                    if (shouldCenterScrollTarget(options)) {
                      var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                      if (viewportHeight > 0) {
                        var rectHeight = rect && Number.isFinite(rect.height) ? Math.max(0, Math.min(rect.height, viewportHeight)) : 0;
                        return documentTop - Math.round((viewportHeight - rectHeight) / 2);
                      }
                    }
                    return documentTop - 24;
                  }
                  function scrollTargetTopFromY(documentY, options) {
                    var top = Number(documentY) || 0;
                    if (shouldCenterScrollTarget(options)) {
                      var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                      if (viewportHeight > 0) return top - Math.round(viewportHeight / 2);
                    }
                    return top - 24;
                  }
                  function scrollTraceExtra(extra, options) {
                    if (!shouldCenterScrollTarget(options)) return extra;
                    return extra ? extra + ' align=center' : 'align=center';
                  }
                  function shouldTrackScrollRestore(options) {
                    return !(options && options.trackRestore === false);
                  }
                  function scrollToLocator(locator, options) {
                    locator = locator || {};
                    options = options || {};
                    if (isVerticalReaderDocument()) {
                      if (shouldTrackScrollRestore(options)) {
                        pendingRestoreLocator = locator;
                        pendingRestoreUntil = Date.now() + 5000;
                      } else {
                        clearPendingRestoreLocator('transient_scroll');
                      }
                    }
                    readerDesktopPositionTraceLog(
                      'event=web_scroll_to_locator_start mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                      ' ' + readerLocatorTrace(locator) +
                      (shouldCenterScrollTarget(options) ? ' align=center' : '')
                    );
                    if (scrollToVerticalPage(locator, options)) return;
                    var chapterIndex = locator.chapterIndex;
                    if (chapterIndex === undefined || chapterIndex === null || chapterIndex === '') {
                      chapterIndex = document.body.getAttribute('data-reader-active-chapter-index');
                    }
                    if (chapterIndex === null || chapterIndex === '') {
                      readerDesktopPositionTraceLog('event=web_scroll_to_locator_skip reason=missing_chapter ' + readerLocatorTrace(locator));
                      return;
                    }
                    var activeStart = locatorStartOffset(locator);
                    if (activeStart === undefined || activeStart === null) {
                      activeStart = numberAttribute(document.body, 'data-reader-active-start-offset', null);
                    }
                    var requestedPageIndex = parseInt(locator.pageIndex, 10);
                    var chapter = Number.isFinite(requestedPageIndex)
                      ? document.querySelector('[data-reader-page-index="' + selectorValue(requestedPageIndex) + '"]')
                      : null;
                    if (!chapter) chapter = readerHostForLocator(chapterIndex, activeStart, locator.endOffset);
                    if (!chapter) {
                      readerDesktopPositionTraceLog(
                        'event=web_scroll_to_locator_skip reason=missing_host requestedChapter=' + chapterIndex +
                        ' activeStart=' + activeStart + ' ' + readerLocatorTrace(locator)
                      );
                      return;
                    }
                    prepareVerticalScrollMeasurement(chapter);
                    var stableCfi = stableReaderCfi(locator.cfi);
                    var locatorCfiBase = stableCfi ? String(stableCfi).split('|')[0].split(':')[0] : null;
                    var exactCfi = locatorCfiBase
                      ? chapter.querySelector('[data-reader-cfi="' + selectorValue(locatorCfiBase) + '"]')
                      : null;
                    if (exactCfi && (activeStart === undefined || activeStart === null)) {
                      var cfiRect = exactCfi.getBoundingClientRect();
                      scrollToTopWithTrace(scrollTargetTopFromRect(cfiRect, options), 'exact_cfi', locator, scrollTraceExtra('requestedChapter=' + chapterIndex, options));
                      return;
                    }
                    var exact = activeStart === null
                      ? null
                      : chapter.querySelector('[data-reader-start-offset="' + selectorValue(activeStart) + '"]');
                    var blockIndex = parseInt(locator.blockIndex, 10);
                    var exactBlock = Number.isFinite(blockIndex)
                      ? chapter.querySelector('[data-reader-block-index="' + selectorValue(blockIndex) + '"]')
                      : null;
                    var target = exact || exactBlock || chapter;
                    var content = chapter.querySelector('.reader-content') || chapter;
                    if (!exact && activeStart !== null && content) {
                      var parsedStart = parseInt(activeStart, 10);
                      var parsedEnd = parseInt(locator.endOffset === undefined || locator.endOffset === null ? activeStart : locator.endOffset, 10);
                      if (Number.isFinite(parsedStart)) {
                        var rangeEnd = Number.isFinite(parsedEnd) && parsedEnd > parsedStart ? parsedEnd : parsedStart + 1;
                        var exactRange = rangeForOffsets(parseInt(chapterIndex, 10), parsedStart, rangeEnd, stableCfi);
                        if (exactRange) {
                          var rangeRects = exactRange.getClientRects();
                          var rangeRect = shouldCenterScrollTarget(options) ? exactRange.getBoundingClientRect() : (rangeRects.length ? rangeRects[0] : exactRange.getBoundingClientRect());
                          exactRange.detach && exactRange.detach();
                          if (rangeRect && (rangeRect.top !== 0 || rangeRect.bottom !== 0)) {
                            var exactResult = scrollToTopWithTrace(scrollTargetTopFromRect(rangeRect, options), 'exact_range', locator, scrollTraceExtra('requestedChapter=' + chapterIndex, options));
                            if (!exactResult.clamped || !isVerticalReaderDocument()) {
                              return;
                            }
                            readerDesktopPositionTraceLog(
                              'event=web_scroll_to_locator_clamped strategy=exact_range requestedChapter=' + chapterIndex +
                              ' targetY=' + exactResult.targetY +
                              ' afterY=' + exactResult.afterY +
                              ' maxScroll=' + exactResult.maxScroll +
                              ' fallback=content_ratio'
                            );
                          }
                        }
                      }
                      var contentStart = numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0));
                      var contentEnd = numberAttribute(content, 'data-reader-content-end', numberAttribute(chapter, 'data-reader-page-end', contentStart));
                      if (contentEnd > contentStart && activeStart > contentStart) {
                        var ratio = Math.max(0, Math.min(1, (activeStart - contentStart) / (contentEnd - contentStart)));
                        var contentRect = content.getBoundingClientRect();
                        var approximateY = contentRect.top + window.scrollY + (content.scrollHeight * ratio);
                        scrollToTopWithTrace(scrollTargetTopFromY(approximateY, options), 'content_ratio', locator, scrollTraceExtra('requestedChapter=' + chapterIndex + ' ratio=' + ratio.toFixed(4), options));
                        return;
                      }
                    }
                    var rect = target.getBoundingClientRect();
                    scrollToTopWithTrace(scrollTargetTopFromRect(rect, options), exact ? 'exact_marker' : (exactBlock ? 'exact_block' : 'host_top'), locator, scrollTraceExtra('requestedChapter=' + chapterIndex, options));
                  }
                  function scrollToActiveLocator() {
                    scrollToLocator({
                      chapterIndex: document.body.getAttribute('data-reader-active-chapter-index'),
                      pageIndex: numberAttribute(document.body, 'data-reader-active-page-index', null),
                      startOffset: numberAttribute(document.body, 'data-reader-active-start-offset', null),
                      endOffset: numberAttribute(document.body, 'data-reader-active-end-offset', null),
                      blockIndex: numberAttribute(document.body, 'data-reader-active-block-index', null),
                      charOffset: numberAttribute(document.body, 'data-reader-active-char-offset', null),
                      cfi: document.body.getAttribute('data-reader-active-cfi')
                    });
                  }
                  window.readerScrollToLocator = scrollToLocator;
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
                  function readerProbeY() {
                    var height = window.innerHeight || document.documentElement.clientHeight || 0;
                    if (height <= 0) return 8;
                    return Math.max(1, Math.min(height - 1, 8));
                  }
                  function firstVisibleOffsetInContent(content, preferredY) {
                    var nodes = textNodesUnder(content, false);
                    var contentStart = numberAttribute(content, 'data-reader-content-start', 0);
                    var offset = contentStart;
                    var viewportTop = Number.isFinite(preferredY) ? preferredY : readerProbeY();
                    var viewportBottom = window.innerHeight - 8;
                    var fallback = null;
                    function visibleResult(node, localOffset, rawOffset) {
                      var sourceOffset = boundaryOffsetWithinContent(content, node, localOffset);
                      return { offset: sourceOffset === null ? rawOffset : sourceOffset, textNode: node };
                    }
                    function usableLineRect(rect) {
                      return rect && rect.width > 0 && rect.height > 0;
                    }
                    function firstVisibleLineRect() {
                      var best = null;
                      function better(candidate, current) {
                        if (!current) return true;
                        var candidateCrossesTop = candidate.top <= viewportTop + 0.5 && candidate.bottom >= viewportTop;
                        var currentCrossesTop = current.top <= viewportTop + 0.5 && current.bottom >= viewportTop;
                        if (candidateCrossesTop !== currentCrossesTop) return candidateCrossesTop;
                        if (candidateCrossesTop) {
                          if (Math.abs(candidate.top - current.top) > 0.5) return candidate.top > current.top;
                        } else if (Math.abs(candidate.top - current.top) > 0.5) {
                          return candidate.top < current.top;
                        }
                        return candidate.left < current.left;
                      }
                      for (var ln = 0; ln < nodes.length; ln++) {
                        var lineWhole = document.createRange();
                        lineWhole.selectNodeContents(nodes[ln]);
                        var lineRects = lineWhole.getClientRects();
                        lineWhole.detach && lineWhole.detach();
                        for (var lr = 0; lr < lineRects.length; lr++) {
                          var rect = lineRects[lr];
                          if (!usableLineRect(rect)) continue;
                          if (rect.bottom < viewportTop || rect.top > viewportBottom) continue;
                          if (better(rect, best)) best = rect;
                        }
                      }
                      return best;
                    }
                    function sameVisualLine(rect, lineRect) {
                      if (!usableLineRect(rect) || !usableLineRect(lineRect)) return false;
                      var rectMid = (rect.top + rect.bottom) / 2;
                      var lineMid = (lineRect.top + lineRect.bottom) / 2;
                      var tolerance = Math.max(2, Math.min(8, lineRect.height * 0.35));
                      return Math.abs(rectMid - lineMid) <= tolerance;
                    }
                    function firstVisibleLineStart(targetLineRect) {
                      var lineOffset = contentStart;
                      var best = null;
                      var direction = 'ltr';
                      try { direction = window.getComputedStyle(content).direction || 'ltr'; } catch (error) {}
                      for (var ln = 0; ln < nodes.length; ln++) {
                        var lineNode = nodes[ln];
                        var lineText = lineNode.nodeValue || '';
                        for (var li = 0; li < lineText.length; li++) {
                          if (!lineText[li] || /^\s$/.test(lineText[li])) continue;
                          var lineRange = document.createRange();
                          lineRange.setStart(lineNode, li);
                          lineRange.setEnd(lineNode, Math.min(li + 1, lineText.length));
                          var charLineRect = lineRange.getBoundingClientRect();
                          lineRange.detach && lineRange.detach();
                          if (sameVisualLine(charLineRect, targetLineRect)) {
                            var visualEdge = direction === 'rtl' ? -charLineRect.right : charLineRect.left;
                            if (!best || visualEdge < best.visualEdge - 0.5 || (
                              Math.abs(visualEdge - best.visualEdge) <= 0.5 && lineOffset + li < best.rawOffset
                            )) {
                              best = {
                                node: lineNode,
                                localOffset: li,
                                rawOffset: lineOffset + li,
                                visualEdge: visualEdge,
                                rect: charLineRect
                              };
                            }
                          }
                        }
                        lineOffset += lineText.length;
                      }
                      if (!best) return null;
                      var result = visibleResult(best.node, best.localOffset, best.rawOffset);
                      readerTtsStartTraceLog(
                        'event=web_line_start_choice offset=' + result.offset +
                        ' rawOffset=' + best.rawOffset +
                        ' lineTop=' + Math.round(targetLineRect.top) +
                        ' lineBottom=' + Math.round(targetLineRect.bottom) +
                        ' charLeft=' + Math.round(best.rect.left) +
                        ' charRight=' + Math.round(best.rect.right) +
                        ' text="' + readerTtsPreview(snippetFromContentOffset(content, result.offset), 120) + '"'
                      );
                      return result;
                    }
                    var topLineRect = firstVisibleLineRect();
                    var topLineStart = topLineRect ? firstVisibleLineStart(topLineRect) : null;
                    if (topLineStart) return topLineStart;
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
                          return visibleResult(node, i, offset + i);
                        }
                      }
                      offset += text.length;
                    }
                    return fallback || { offset: contentStart, textNode: null };
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
                  function isVerticalReaderDocument() {
                    return !!(document.body && document.body.classList.contains('reader-vertical'));
                  }
                  function renderedVerticalPageAnchors() {
                    if (!isVerticalReaderDocument() || !readerPageAnchors.length) return readerPageAnchors;
                    var hosts = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
                    if (!hosts.length) return readerPageAnchors;
                    var renderedChapters = {};
                    hosts.forEach(function (host) {
                      var chapterIndex = numberAttribute(host, 'data-reader-chapter-index', null);
                      if (chapterIndex !== null) renderedChapters[chapterIndex] = true;
                    });
                    var filtered = readerPageAnchors.filter(function (page) {
                      return !!renderedChapters[page.chapterIndex];
                    });
                    return filtered.length ? filtered : readerPageAnchors;
                  }
                  function verticalScrollMetrics() {
                    var scrollY = Math.round(window.scrollY || window.pageYOffset || document.documentElement.scrollTop || document.body.scrollTop || 0);
                    var scrollHeight = Math.round(Math.max(
                      document.body ? document.body.scrollHeight : 0,
                      document.documentElement ? document.documentElement.scrollHeight : 0
                    ));
                    var clientHeight = Math.round(document.documentElement.clientHeight || window.innerHeight || 0);
                    var maxScroll = Math.max(0, scrollHeight - clientHeight);
                    return {
                      scrollY: Math.max(0, Math.min(scrollY, maxScroll)),
                      scrollHeight: scrollHeight,
                      clientHeight: clientHeight,
                      maxScroll: maxScroll
                    };
                  }
                  function pageForVerticalScroll() {
                    if (!isVerticalReaderDocument() || !readerPageAnchors.length) return null;
                    var anchors = renderedVerticalPageAnchors();
                    if (!anchors.length) return null;
                    var metrics = verticalScrollMetrics();
                    if (anchors.length === 1 || metrics.maxScroll <= 0) return anchors[0];
                    var ratio = Math.max(0, Math.min(1, metrics.scrollY / metrics.maxScroll));
                    var index = Math.round((anchors.length - 1) * ratio);
                    return anchors[Math.max(0, Math.min(anchors.length - 1, index))];
                  }
                  function scrollToVerticalPage(locator, options) {
                    if (!isVerticalReaderDocument() || !locator) return false;
                    var cfi = String(locator.cfi || '');
                    if (cfi.indexOf('desktop-scroll-page:') !== 0) return false;
                    var pageIndex = parseInt(locator.pageIndex, 10);
                    if (!Number.isFinite(pageIndex) || !readerPageAnchors.length) return false;
                    var chapterIndex = parseInt(locator.chapterIndex, 10);
                    var startOffset = parseInt(locator.startOffset, 10);
                    if (Number.isFinite(chapterIndex) && Number.isFinite(startOffset)) {
                      var chapter = readerHostForLocator(chapterIndex, startOffset, locator.endOffset);
                      if (chapter) {
                        prepareVerticalScrollMeasurement(chapter);
                        var content = chapter.querySelector('.reader-content') || chapter;
                        var contentStart = numberAttribute(content, 'data-reader-content-start', numberAttribute(chapter, 'data-reader-page-start', 0));
                        var contentEnd = numberAttribute(content, 'data-reader-content-end', numberAttribute(chapter, 'data-reader-page-end', contentStart));
                        var targetY = chapter.getBoundingClientRect().top + window.scrollY;
                        if (contentEnd > contentStart && startOffset > contentStart) {
                          var ratioInContent = Math.max(0, Math.min(1, (startOffset - contentStart) / (contentEnd - contentStart)));
                          var contentRect = content.getBoundingClientRect();
                          targetY = contentRect.top + window.scrollY + (content.scrollHeight * ratioInContent);
                        }
                        scrollToTopWithTrace(scrollTargetTopFromY(targetY, options), 'vertical_page_content', locator, scrollTraceExtra('ratioSource=content', options));
                        return true;
                      }
                    }
                    var anchors = renderedVerticalPageAnchors();
                    if (!anchors.length) return false;
                    var metrics = verticalScrollMetrics();
                    var anchorPosition = anchors.findIndex(function (page) { return page.pageIndex === pageIndex; });
                    var ratio = anchorPosition >= 0
                      ? anchorPosition / Math.max(1, anchors.length - 1)
                      : pageIndex / Math.max(1, readerPageAnchors.length - 1);
                    ratio = Math.max(0, Math.min(1, ratio));
                    scrollToTopWithTrace(Math.round(metrics.maxScroll * ratio), 'vertical_page_ratio', locator, 'ratio=' + ratio.toFixed(4));
                    return true;
                  }
                  function readerHostIsVisible(host) {
                    if (!host) return false;
                    var rect = host.getBoundingClientRect();
                    return rect.bottom >= 8 && rect.top <= window.innerHeight - 8;
                  }
                  function readerHostVisibilityScore(host, probeY) {
                    if (!host) return null;
                    var rect = host.getBoundingClientRect();
                    var viewportHeight = window.innerHeight || document.documentElement.clientHeight || 0;
                    var visibleTop = Math.max(0, rect.top);
                    var visibleBottom = Math.min(viewportHeight, rect.bottom);
                    var visibleHeight = Math.max(0, visibleBottom - visibleTop);
                    var containsProbe = rect.top <= probeY && rect.bottom >= probeY;
                    var distance = containsProbe ? 0 : Math.min(Math.abs(rect.top - probeY), Math.abs(rect.bottom - probeY));
                    return {
                      visibleHeight: visibleHeight,
                      containsProbe: containsProbe,
                      distance: distance,
                      top: Math.round(rect.top),
                      bottom: Math.round(rect.bottom)
                    };
                  }
                  function bestVisibleReaderHost() {
                    var chapters = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index]'));
                    if (!chapters.length) return null;
                    var probeY = readerProbeY();
                    var probeX = Math.max(0, Math.min((window.innerWidth || 0) - 1, Math.round((window.innerWidth || 0) / 2)));
                    var probeElement = document.elementFromPoint(probeX, probeY);
                    var probeHost = nearestReaderHost(probeElement);
                    if (probeHost && readerHostIsVisible(probeHost)) {
                      return { host: probeHost, probeY: probeY, source: 'element_from_point', score: readerHostVisibilityScore(probeHost, probeY) };
                    }
                    var best = null;
                    chapters.forEach(function (chapter) {
                      if (!readerHostIsVisible(chapter)) return;
                      var score = readerHostVisibilityScore(chapter, probeY);
                      if (!score || score.visibleHeight <= 0) return;
                      if (!best ||
                        (score.containsProbe && !best.score.containsProbe) ||
                        (score.containsProbe === best.score.containsProbe && score.distance < best.score.distance) ||
                        (score.containsProbe === best.score.containsProbe && score.distance === best.score.distance && score.visibleHeight > best.score.visibleHeight)
                      ) {
                        best = { host: chapter, probeY: probeY, source: 'visibility_score', score: score };
                      }
                    });
                    return best;
                  }
                  function positionFromReaderHost(host, preferredOffset, preferredY, source) {
                    if (!host) return null;
                    var content = host.querySelector('.reader-content') || host;
                    var chapterIndex = numberAttribute(host, 'data-reader-chapter-index', 0);
                    var pageStart = numberAttribute(host, 'data-reader-page-start', null);
                    var pageEnd = numberAttribute(host, 'data-reader-page-end', null);
                    var visible = firstVisibleOffsetInContent(content, preferredY);
                    var usePreferredOffset = Number.isFinite(preferredOffset) &&
                      (pageStart === null || preferredOffset >= pageStart) &&
                      (pageEnd === null || preferredOffset <= pageEnd);
                    var offset = usePreferredOffset ? preferredOffset : visible.offset;
                    var renderedAnchors = renderedVerticalPageAnchors();
                    var page = pageForLocator(chapterIndex, offset) || pageForVerticalScroll() || renderedAnchors[0] || readerPageAnchors[0];
                    if (!page) return null;
                    var positionCfi = readerCfiPointForOffset(content, offset, false) || stableDesktopCfi(chapterIndex, offset, offset);
                    var blockPosition = readerBlockPositionForOffset(content, offset, false);
                    var metrics = isVerticalReaderDocument() ? verticalScrollMetrics() : null;
                    if (isVerticalReaderDocument()) {
                      positionCfi = stableReaderCfi(positionCfi) || stableDesktopCfi(chapterIndex, offset, offset);
                    }
                    readerDesktopHighlightMapLog(
                      'web_position_payload mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                      ' page=' + page.pageIndex +
                      ' chapter=' + chapterIndex +
                      ' offsets=' + offset + '..' + offset +
                      ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                      ' char=' + (blockPosition ? blockPosition.charOffset : 'null') +
                      ' chapterId=' + readerTtsPreview(host.getAttribute('data-reader-chapter-id'), 80) +
                      ' href=' + readerTtsPreview(host.getAttribute('data-reader-chapter-href'), 120) +
                      ' cfi=' + readerTtsPreview(positionCfi, 160) +
                      ' text="' + readerTtsPreview(snippetFromContentOffset(content, offset), 120) + '"'
                    );
                    readerDesktopPositionTraceLog(
                      'event=web_position_payload mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                      ' source=' + (source || 'unknown') +
                      ' page=' + page.pageIndex +
                      ' chapter=' + chapterIndex +
                      ' offsets=' + offset + '..' + offset +
                      ' preferredOffset=' + (Number.isFinite(preferredOffset) ? preferredOffset : 'null') +
                      ' preferredY=' + (Number.isFinite(preferredY) ? Math.round(preferredY) : 'null') +
                      ' visibleNode=' + (visible.textNode ? readerElementLabel(visible.textNode.parentElement) : 'null') +
                      ' scrollY=' + (metrics ? metrics.scrollY : 'null') +
                      ' maxScroll=' + (metrics ? metrics.maxScroll : 'null') +
                      ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                      ' char=' + (blockPosition ? blockPosition.charOffset : 'null') +
                      ' cfi=' + readerTtsPreview(positionCfi, 160) +
                      ' text="' + readerTtsPreview(snippetFromContentOffset(content, offset), 120) + '"'
                    );
                    readerTtsStartTraceLog(
                      'event=web_position_payload mode=' + (isVerticalReaderDocument() ? 'vertical' : 'paginated') +
                      ' source=' + (source || 'unknown') +
                      ' page=' + page.pageIndex +
                      ' chapter=' + chapterIndex +
                      ' offsets=' + offset + '..' + offset +
                      ' preferredY=' + (Number.isFinite(preferredY) ? Math.round(preferredY) : 'null') +
                      ' visibleNode=' + (visible.textNode ? readerElementLabel(visible.textNode.parentElement) : 'null') +
                      ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                      ' char=' + (blockPosition ? blockPosition.charOffset : 'null') +
                      ' cfi=' + readerTtsPreview(positionCfi, 160) +
                      ' text="' + readerTtsPreview(snippetFromContentOffset(content, offset), 160) + '"'
                    );
                    return {
                      pageIndex: page.pageIndex,
                      chapterIndex: chapterIndex,
                      chapterId: host.getAttribute('data-reader-chapter-id'),
                      href: host.getAttribute('data-reader-chapter-href'),
                      startOffset: offset,
                      endOffset: offset,
                      blockIndex: blockPosition ? blockPosition.blockIndex : null,
                      charOffset: blockPosition ? blockPosition.charOffset : null,
                      textQuote: snippetFromContentOffset(content, offset),
                      cfi: positionCfi
                    };
                  }
                  function locatorChapterIndex(locator) {
                    locator = locator || {};
                    var chapterIndex = parseInt(locator.chapterIndex, 10);
                    if (Number.isFinite(chapterIndex)) return chapterIndex;
                    var cfi = stableReaderCfi(locator.cfi);
                    if (cfi && cfi.indexOf('desktop:') === 0) {
                      var desktopParts = cfi.split(':');
                      var desktopChapter = parseInt(desktopParts[1], 10);
                      if (Number.isFinite(desktopChapter)) return desktopChapter;
                    }
                    if (cfi && cfi.indexOf('android-locator:') === 0) {
                      var androidParts = cfi.split(':');
                      var androidChapter = parseInt(androidParts[1], 10);
                      if (Number.isFinite(androidChapter)) return androidChapter;
                    }
                    return null;
                  }
                  function positionMatchesRestoreLocator(position, locator) {
                    if (!position || !locator) return false;
                    var chapterIndex = locatorChapterIndex(locator);
                    if (chapterIndex !== null && position.chapterIndex !== chapterIndex) return false;
                    var requestedPage = parseInt(locator.pageIndex, 10);
                    var requestedOffset = locatorStartOffset(locator);
                    if (requestedOffset !== null) {
                      if (Math.abs(position.startOffset - requestedOffset) <= 512) return true;
                      if (Number.isFinite(requestedPage) && position.pageIndex === requestedPage) return true;
                      return false;
                    }
                    return Number.isFinite(requestedPage) ? position.pageIndex === requestedPage : true;
                  }
                  function pendingRestoreVisiblePosition() {
                    if (!pendingRestoreLocator || !isVerticalReaderDocument()) return null;
                    if (Date.now() > pendingRestoreUntil) {
                      readerDesktopPositionTraceLog(
                        'event=web_restore_guard_expired ' + readerLocatorTrace(pendingRestoreLocator)
                      );
                      pendingRestoreLocator = null;
                      pendingRestoreUntil = 0;
                      return null;
                    }
                    var chapterIndex = locatorChapterIndex(pendingRestoreLocator);
                    var requestedOffset = locatorStartOffset(pendingRestoreLocator);
                    if (chapterIndex === null || requestedOffset === null) return null;
                    var chapter = readerHostForLocator(chapterIndex, requestedOffset, pendingRestoreLocator.endOffset);
                    if (!chapter || !readerHostIsVisible(chapter)) return null;
                    var range = rangeForOffsets(chapterIndex, requestedOffset, requestedOffset + 1, stableReaderCfi(pendingRestoreLocator.cfi));
                    var preferredY = readerProbeY();
                    var visible = false;
                    if (range) {
                      var rects = range.getClientRects();
                      var rect = rects.length ? rects[0] : range.getBoundingClientRect();
                      range.detach && range.detach();
                      if (rect && rect.bottom >= 8 && rect.top <= (window.innerHeight || document.documentElement.clientHeight || 0) - 8) {
                        visible = true;
                        preferredY = Math.max(8, Math.min((window.innerHeight || document.documentElement.clientHeight || 0) - 8, Math.round(rect.top)));
                      }
                    }
                    if (!visible) return null;
                    readerDesktopPositionTraceLog(
                      'event=web_restore_guard_visible chapter=' + chapterIndex +
                      ' offset=' + requestedOffset +
                      ' preferredY=' + preferredY +
                      ' ' + readerLocatorTrace(pendingRestoreLocator)
                    );
                    return positionFromReaderHost(chapter, requestedOffset, preferredY, 'restore_locator_visible');
                  }
                  function clearPendingRestoreLocator(reason) {
                    if (!pendingRestoreLocator) return;
                    readerDesktopPositionTraceLog(
                      'event=web_restore_guard_cleared reason=' + reason +
                      ' pending=' + readerLocatorTrace(pendingRestoreLocator)
                    );
                    pendingRestoreLocator = null;
                    pendingRestoreUntil = 0;
                  }
                  function currentVisiblePosition() {
                    var activePageIndex = numberAttribute(document.body, 'data-reader-active-page-index', null);
                    if (document.body.classList.contains('reader-paginated') && activePageIndex !== null) {
                      var activePage = document.querySelector('.page[data-reader-page-index="' + selectorValue(activePageIndex) + '"]');
                      if (readerHostIsVisible(activePage)) {
                        var activeStart = numberAttribute(document.body, 'data-reader-active-start-offset', null);
                        var activePosition = positionFromReaderHost(activePage, activeStart, readerProbeY(), 'active_page');
                        if (activePosition) return activePosition;
                      }
                    }
                    var best = bestVisibleReaderHost();
                    if (best && best.host) {
                      readerDesktopPositionTraceLog(
                        'event=web_visible_host_selected source=' + best.source +
                        ' chapter=' + numberAttribute(best.host, 'data-reader-chapter-index', -1) +
                        ' probeY=' + best.probeY +
                        ' visibleHeight=' + (best.score ? Math.round(best.score.visibleHeight) : 'null') +
                        ' containsProbe=' + (best.score ? best.score.containsProbe : false) +
                        ' distance=' + (best.score ? Math.round(best.score.distance) : 'null') +
                        ' rect=' + (best.score ? best.score.top + '..' + best.score.bottom : 'null')
                      );
                      var position = positionFromReaderHost(best.host, null, best.probeY, best.source);
                      if (position) return position;
                    }
                    return null;
                  }
                  function reportVisiblePage() {
                    var position = pendingRestoreVisiblePosition() || currentVisiblePosition();
                    if (!position) {
                      readerDesktopPositionTraceLog('event=web_position_report_skip reason=no_position');
                      return;
                    }
                    if (pendingRestoreLocator) {
                      if (!positionMatchesRestoreLocator(position, pendingRestoreLocator)) {
                        readerDesktopPositionTraceLog(
                          'event=web_position_report_skip reason=pending_restore page=' + position.pageIndex +
                          ' chapter=' + position.chapterIndex +
                          ' offsets=' + position.startOffset + '..' + position.endOffset +
                          ' pending=' + readerLocatorTrace(pendingRestoreLocator)
                        );
                        return;
                      }
                      readerDesktopPositionTraceLog(
                        'event=web_restore_guard_resolved page=' + position.pageIndex +
                        ' chapter=' + position.chapterIndex +
                        ' offsets=' + position.startOffset + '..' + position.endOffset +
                        ' pending=' + readerLocatorTrace(pendingRestoreLocator)
                      );
                      pendingRestoreLocator = null;
                      pendingRestoreUntil = 0;
                    }
                    if (position.pageIndex === lastReportedPageIndex && Math.abs(position.startOffset - lastReportedStartOffset) < 8) {
                      return;
                    }
                    lastReportedPageIndex = position.pageIndex;
                    lastReportedStartOffset = position.startOffset;
                    readerDesktopPositionTraceLog(
                      'event=web_position_report_send page=' + position.pageIndex +
                      ' chapter=' + position.chapterIndex +
                      ' offsets=' + position.startOffset + '..' + position.endOffset +
                      ' block=' + (position.blockIndex === null || position.blockIndex === undefined ? 'null' : position.blockIndex) +
                      ' char=' + (position.charOffset === null || position.charOffset === undefined ? 'null' : position.charOffset) +
                      ' cfi=' + readerTtsPreview(position.cfi, 160) +
                      ' text="' + readerTtsPreview(position.textQuote, 120) + '"'
                    );
                    readerTtsStartTraceLog(
                      'event=web_position_report_send page=' + position.pageIndex +
                      ' chapter=' + position.chapterIndex +
                      ' offsets=' + position.startOffset + '..' + position.endOffset +
                      ' block=' + (position.blockIndex === null || position.blockIndex === undefined ? 'null' : position.blockIndex) +
                      ' char=' + (position.charOffset === null || position.charOffset === undefined ? 'null' : position.charOffset) +
                      ' cfi=' + readerTtsPreview(position.cfi, 160) +
                      ' text="' + readerTtsPreview(position.textQuote, 160) + '"'
                    );
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
                      readerConsoleLog('READER_LINK fallback_navigation href=' + payload.href + ' reason=' + reason);
                      window.location.href = 'readerlink://click?payload=' + encoded;
                    } catch (error) {
                      readerConsoleLog('READER_LINK fallback_navigation_error href=' + payload.href + ' error=' + error);
                    }
                  }
                  function sendReaderLinkClick(payload, attempt) {
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerLinkClicked', JSON.stringify(payload));
                        readerConsoleLog('READER_LINK bridge_sent href=' + payload.href + ' attempt=' + attempt);
                        window.setTimeout(function () {
                          fallbackReaderLinkNavigation(payload, 'post_bridge');
                        }, 260);
                        return true;
                      } catch (error) {
                        readerConsoleLog('READER_LINK bridge_error href=' + payload.href + ' attempt=' + attempt + ' error=' + error);
                      }
                    } else {
                      readerConsoleLog('READER_LINK bridge_missing href=' + payload.href + ' attempt=' + attempt);
                    }
                    if (attempt < 3) {
                      window.setTimeout(function () {
                        sendReaderLinkClick(payload, attempt + 1);
                      }, attempt === 0 ? 60 : 220);
                      return true;
                    }
                    readerConsoleLog('READER_LINK bridge_gave_up href=' + payload.href);
                    fallbackReaderLinkNavigation(payload, 'bridge_gave_up');
                    return false;
                  }
                  function sendReaderHighlightClick(highlightId) {
                    if (!highlightId) return false;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerHighlightClicked', JSON.stringify({ id: highlightId }));
                        return true;
                      } catch (error) {
                        readerConsoleLog('READER_HIGHLIGHT bridge_error id=' + highlightId + ' error=' + error);
                      }
                    }
                    return false;
                  }
                  function sendReaderHighlightCreated(payload, attempt) {
                    attempt = attempt || 0;
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerHighlightCreated', JSON.stringify(payload));
                        readerHighlightFlowLog(
                          'bridge_send_success attempt=' + attempt +
                          ' cfi="' + readerTtsPreview(payload && payload.cfi, 120) + '"' +
                          ' color=' + ((payload && payload.colorId) || 'null') +
                          ' chapter=' + ((payload && payload.chapterIndex) !== undefined ? payload.chapterIndex : 'null') +
                          ' textChars=' + (((payload && payload.text) || '').length)
                        );
                        return true;
                      } catch (error) {
                        readerHighlightFlowLog('bridge_send_error attempt=' + attempt + ' error=' + readerTtsPreview(error, 180));
                        readerSelectionDebugLog('highlight_bridge_error attempt=' + attempt + ' error=' + readerTtsPreview(error, 180));
                      }
                    }
                    if (attempt < 3) {
                      window.setTimeout(function () {
                        sendReaderHighlightCreated(payload, attempt + 1);
                      }, attempt === 0 ? 80 : 240);
                      return true;
                    }
                    readerHighlightFlowLog('bridge_send_missing attempts=' + (attempt + 1));
                    readerSelectionDebugLog('highlight_bridge_missing attempts=' + (attempt + 1));
                    return false;
                  }
                  document.addEventListener('click', function (event) {
                    var target = event.target;
                    if (!target || !target.closest) return;
                    var highlight = target.closest('.reader-user-highlight[data-reader-highlight-id], span[class*="user-highlight-"][data-reader-highlight-id]');
                    if (highlight && !menu.contains(highlight)) {
                      var highlightId = highlight.getAttribute('data-reader-highlight-id') || '';
                      if (highlightId && sendReaderHighlightClick(highlightId)) {
                        event.preventDefault();
                        event.stopPropagation();
                        return;
                      }
                    }
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
                    if (selectionPointerDown || activeSelectionHandle) {
                      if (reportTimer !== null) window.clearTimeout(reportTimer);
                      reportTimer = null;
                      return;
                    }
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
                  function hideSelectionHandles() {
                    [startHandle, endHandle].forEach(function (handle) {
                      if (!handle) return;
                      handle.hidden = true;
                      handle.style.display = 'none';
                    });
                  }
                  function hideMenu() {
                    if (selectionMenuTimer !== null) {
                      window.clearTimeout(selectionMenuTimer);
                      selectionMenuTimer = null;
                    }
                    menu.style.display = 'none';
                    if (!activeSelectionHandle) hideSelectionHandles();
                  }
                  function selectionAnchorRect(selection) {
                    if (!selection || selection.rangeCount === 0) return null;
                    var range = selection.getRangeAt(0);
                    var startRect = rangeBoundaryRect(range.startContainer, range.startOffset, false);
                    var endRect = rangeBoundaryRect(range.endContainer, range.endOffset, true);
                    if (startRect && endRect) {
                      var left = Math.min(startRect.left, endRect.left);
                      var top = Math.min(startRect.top, endRect.top);
                      var right = Math.max(startRect.right, endRect.right);
                      var bottom = Math.max(startRect.bottom, endRect.bottom);
                      return {
                        left: left,
                        top: top,
                        right: right,
                        bottom: bottom,
                        width: right - left,
                        height: bottom - top
                      };
                    }
                    return startRect || endRect || firstRangeRect(range, false);
                  }
                  function positionMenu(left, top, anchorRect) {
                    menu.style.visibility = 'hidden';
                    menu.style.display = 'flex';
                    var margin = 8;
                    var gap = 14;
                    var viewportWidth = Math.max(0, window.innerWidth || 0);
                    var viewportHeight = Math.max(0, window.innerHeight || 0);
                    menu.style.maxHeight = Math.max(0, viewportHeight - margin * 2) + 'px';
                    var menuWidth = menu.offsetWidth || 300;
                    var menuHeight = menu.offsetHeight || 230;

                    function clampMenuStart(preferred, size, viewportSize) {
                      if (viewportSize <= 0 || size <= 0) return 0;
                      if (viewportSize <= size) return 0;
                      var maxStart = viewportSize - size;
                      var minStart = Math.min(margin, maxStart);
                      var maxClampedStart = Math.max(minStart, viewportSize - size - margin);
                      return Math.max(minStart, Math.min(maxClampedStart, preferred));
                    }
                    function selectionMenuCandidate(x, y) {
                      return {
                        left: clampMenuStart(x, menuWidth, viewportWidth),
                        top: clampMenuStart(y, menuHeight, viewportHeight)
                      };
                    }
                    function overlapAreaWithSelection(candidate, rect) {
                      var overlapWidth = Math.min(candidate.left + menuWidth, rect.right) - Math.max(candidate.left, rect.left);
                      var overlapHeight = Math.min(candidate.top + menuHeight, rect.bottom) - Math.max(candidate.top, rect.top);
                      return Math.max(0, overlapWidth) * Math.max(0, overlapHeight);
                    }
                    function distanceFromSelection(candidate, rect) {
                      var dx = Math.max(rect.left - (candidate.left + menuWidth), candidate.left - rect.right, 0);
                      var dy = Math.max(rect.top - (candidate.top + menuHeight), candidate.top - rect.bottom, 0);
                      return dx * dx + dy * dy;
                    }
                    var rect = anchorRect ? {
                      left: Math.max(0, Math.min(viewportWidth, Math.min(anchorRect.left, anchorRect.right))),
                      top: Math.max(0, Math.min(viewportHeight, Math.min(anchorRect.top, anchorRect.bottom))),
                      right: Math.max(0, Math.min(viewportWidth, Math.max(anchorRect.left, anchorRect.right))),
                      bottom: Math.max(0, Math.min(viewportHeight, Math.max(anchorRect.top, anchorRect.bottom)))
                    } : {
                      left: Math.max(0, Math.min(viewportWidth, left)),
                      top: Math.max(0, Math.min(viewportHeight, top)),
                      right: Math.max(0, Math.min(viewportWidth, left)),
                      bottom: Math.max(0, Math.min(viewportHeight, top))
                    };
                    var centerX = (rect.left + rect.right) / 2;
                    var centerY = (rect.top + rect.bottom) / 2;
                    var above = selectionMenuCandidate(centerX - menuWidth / 2, rect.top - gap - menuHeight);
                    var below = selectionMenuCandidate(centerX - menuWidth / 2, rect.bottom + gap);
                    var right = selectionMenuCandidate(rect.right + gap, centerY - menuHeight / 2);
                    var leftSide = selectionMenuCandidate(rect.left - gap - menuWidth, centerY - menuHeight / 2);
                    var nextLeft = above.left;
                    var nextTop = above.top;
                    if (above.top + menuHeight <= rect.top - gap && above.top >= margin) {
                      nextLeft = above.left;
                      nextTop = above.top;
                    } else if (below.top >= rect.bottom + gap && below.top + menuHeight <= viewportHeight - margin) {
                      nextLeft = below.left;
                      nextTop = below.top;
                    } else {
                      var leftSpace = rect.left - gap - margin;
                      var rightSpace = viewportWidth - rect.right - gap - margin;
                      var firstSide = rightSpace >= leftSpace ? right : leftSide;
                      var secondSide = rightSpace >= leftSpace ? leftSide : right;
                      var firstFits = firstSide === right
                        ? firstSide.left >= rect.right + gap
                        : firstSide.left + menuWidth <= rect.left - gap;
                      var secondFits = secondSide === right
                        ? secondSide.left >= rect.right + gap
                        : secondSide.left + menuWidth <= rect.left - gap;
                      if (firstFits && firstSide.top >= margin && firstSide.top + menuHeight <= viewportHeight - margin) {
                        nextLeft = firstSide.left;
                        nextTop = firstSide.top;
                      } else if (secondFits && secondSide.top >= margin && secondSide.top + menuHeight <= viewportHeight - margin) {
                        nextLeft = secondSide.left;
                        nextTop = secondSide.top;
                      } else {
                        var fallback = [above, below, firstSide, secondSide].sort(function (a, b) {
                          var overlapDelta = overlapAreaWithSelection(a, rect) - overlapAreaWithSelection(b, rect);
                          if (overlapDelta !== 0) return overlapDelta;
                          return distanceFromSelection(a, rect) - distanceFromSelection(b, rect);
                        })[0];
                        nextLeft = fallback.left;
                        nextTop = fallback.top;
                      }
                    }
                    menu.style.left = nextLeft + 'px';
                    menu.style.top = nextTop + 'px';
                    menu.style.visibility = 'visible';
                  }
                  function showSelectionHandle(handle, rect, x) {
                    if (!handle || !rect) return;
                    handle.hidden = false;
                    handle.style.display = 'block';
                    handle.style.left = (x - 12) + 'px';
                    handle.style.top = rect.bottom + 'px';
                  }
                  function selectionDebugMode() {
                    return document.body && document.body.classList.contains('reader-paginated') ? 'paginated' : 'vertical';
                  }
                  function selectionDebugRect(rect) {
                    if (!rect) return 'none';
                    return [
                      Math.round(rect.left),
                      Math.round(rect.top),
                      Math.round(rect.right),
                      Math.round(rect.bottom)
                    ].join(',');
                  }
                  function selectionDebugNode(node) {
                    if (!node) return 'null';
                    var parent = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
                    var label = readerElementLabel(parent);
                    if (node.nodeType === Node.TEXT_NODE) label += ':text';
                    return label.replace(/\s+/g, ' ').substring(0, 160);
                  }
                  function selectionDebugRange(range) {
                    if (!range) return 'null';
                    var startRect = rangeBoundaryRect(range.startContainer, range.startOffset, false);
                    var endRect = rangeBoundaryRect(range.endContainer, range.endOffset, true);
                    return 'collapsed=' + range.collapsed +
                      ' start=' + selectionDebugNode(range.startContainer) + '@' + range.startOffset +
                      ' startRect=' + selectionDebugRect(startRect) +
                      ' end=' + selectionDebugNode(range.endContainer) + '@' + range.endOffset +
                      ' endRect=' + selectionDebugRect(endRect);
                  }
                  function selectionDebugRangeTextLength(range) {
                    if (!range) return -1;
                    try { return range.toString().length; } catch (error) { return -1; }
                  }
                  function usableRangeRect(rect) {
                    return rect && (rect.width > 0 || rect.height > 0) ? rect : null;
                  }
                  function firstRangeRect(range, preferLast) {
                    var rects = Array.prototype.slice.call(range && range.getClientRects ? range.getClientRects() : []);
                    rects = rects.filter(usableRangeRect);
                    if (rects.length === 0) return null;
                    return preferLast ? rects[rects.length - 1] : rects[0];
                  }
                  function rangeBoundaryRect(container, offset, preferPrevious) {
                    if (!container) return null;
                    var collapsed = document.createRange();
                    try {
                      collapsed.setStart(container, offset);
                      collapsed.collapse(true);
                      var collapsedRect = firstRangeRect(collapsed, preferPrevious);
                      if (collapsedRect) return collapsedRect;
                    } catch (error) {
                    } finally {
                      collapsed.detach && collapsed.detach();
                    }

                    var expanded = document.createRange();
                    try {
                      if (container.nodeType === Node.TEXT_NODE) {
                        var textLength = container.nodeValue ? container.nodeValue.length : 0;
                        var start = preferPrevious ? Math.max(0, offset - 1) : Math.min(offset, Math.max(0, textLength - 1));
                        var end = Math.min(textLength, start + 1);
                        if (end <= start && start > 0) {
                          start -= 1;
                          end = start + 1;
                        }
                        if (end > start) {
                          expanded.setStart(container, start);
                          expanded.setEnd(container, end);
                          return firstRangeRect(expanded, preferPrevious);
                        }
                      } else {
                        var childCount = container.childNodes ? container.childNodes.length : 0;
                        if (childCount > 0) {
                          var childIndex = preferPrevious ? Math.max(0, offset - 1) : Math.min(offset, childCount - 1);
                          expanded.selectNodeContents(container.childNodes[childIndex]);
                          return firstRangeRect(expanded, preferPrevious);
                        }
                      }
                    } catch (error) {
                      return null;
                    } finally {
                      expanded.detach && expanded.detach();
                    }
                    return null;
                  }
                  function positionSelectionHandles(selection) {
                    if (!selection || selection.rangeCount === 0) return;
                    var range = selection.getRangeAt(0);
                    var first = rangeBoundaryRect(range.startContainer, range.startOffset, false) || firstRangeRect(range, false);
                    var last = rangeBoundaryRect(range.endContainer, range.endOffset, true) || firstRangeRect(range, true);
                    if (!first || !last) {
                      hideSelectionHandles();
                      return;
                    }
                    showSelectionHandle(startHandle, first, first.left);
                    showSelectionHandle(endHandle, last, last.right);
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
                    var rect = event ? null : selectionAnchorRect(selection);
                    positionMenu(event ? event.clientX : 0, event ? event.clientY : 0, rect);
                    positionSelectionHandles(selection);
                  }
                  function scheduleMenuFromSelection() {
                    if (selectionMenuTimer !== null) window.clearTimeout(selectionMenuTimer);
                    selectionMenuTimer = window.setTimeout(function () {
                      selectionMenuTimer = null;
                      if (selectionPointerDown || activeSelectionHandle) return;
                      if (selectionText().length > 0) showMenu(null);
                      else hideMenu();
                    }, 90);
                  }
                  function restoreRange() {
                    if (!savedRange) return false;
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    selection.addRange(savedRange);
                    return true;
                  }
                  function selectionChromeElement(node) {
                    if (!node) return null;
                    var element = node.nodeType === Node.ELEMENT_NODE ? node : node.parentElement;
                    if (!element || !element.closest) return null;
                    return element.closest('#reader-selection-menu, .reader-selection-handle');
                  }
                  function rangeTouchesSelectionChrome(range) {
                    return !!range && (!!selectionChromeElement(range.startContainer) || !!selectionChromeElement(range.endContainer));
                  }
                  function caretRangeFromPoint(clientX, clientY) {
                    if (document.caretRangeFromPoint) {
                      var range = document.caretRangeFromPoint(clientX, clientY);
                      return rangeTouchesSelectionChrome(range) ? null : range;
                    }
                    if (document.caretPositionFromPoint) {
                      var position = document.caretPositionFromPoint(clientX, clientY);
                      if (!position) return null;
                      var range = document.createRange();
                      range.setStart(position.offsetNode, position.offset);
                      range.collapse(true);
                      return rangeTouchesSelectionChrome(range) ? null : range;
                    }
                    return null;
                  }
                  function selectionRangeForHandle(handleName, pointRange) {
                    if (!savedRange || !pointRange) return null;
                    if (rangeTouchesSelectionChrome(pointRange)) return null;
                    var next = document.createRange();
                    try {
                      if (handleName === 'start') {
                        next.setStart(pointRange.startContainer, pointRange.startOffset);
                        next.setEnd(savedRange.endContainer, savedRange.endOffset);
                      } else {
                        next.setStart(savedRange.startContainer, savedRange.startOffset);
                        next.setEnd(pointRange.startContainer, pointRange.startOffset);
                      }
                    } catch (error) {
                      return null;
                    }
                    if (next.collapsed) return null;
                    if (rangeTouchesSelectionChrome(next)) return null;
                    return next;
                  }
                  function updateSelectionHandle(event) {
                    if (!activeSelectionHandle) return;
                    var pointRange = caretRangeFromPoint(event.clientX, event.clientY);
                    if (!pointRange) {
                      var nowMissing = Date.now();
                      if (nowMissing - selectionDebugLastAt > 350) {
                        selectionDebugLastAt = nowMissing;
                        readerSelectionDebugLog(
                          'drag_point_missing seq=' + (++selectionDebugSequence) +
                          ' mode=' + selectionDebugMode() +
                          ' handle=' + activeSelectionHandle +
                          ' x=' + Math.round(event.clientX) +
                          ' y=' + Math.round(event.clientY) +
                          ' scrollY=' + Math.round(window.scrollY)
                        );
                      }
                      return;
                    }
                    var nextRange = selectionRangeForHandle(activeSelectionHandle, pointRange);
                    if (!nextRange) {
                      var nowInvalid = Date.now();
                      if (nowInvalid - selectionDebugLastAt > 350) {
                        selectionDebugLastAt = nowInvalid;
                        readerSelectionDebugLog(
                          'drag_range_invalid seq=' + (++selectionDebugSequence) +
                          ' mode=' + selectionDebugMode() +
                          ' handle=' + activeSelectionHandle +
                          ' x=' + Math.round(event.clientX) +
                          ' y=' + Math.round(event.clientY) +
                          ' point=' + selectionDebugRange(pointRange) +
                          ' saved=' + selectionDebugRange(savedRange)
                        );
                      }
                      return;
                    }
                    var pointRect = rangeBoundaryRect(pointRange.startContainer, pointRange.startOffset, activeSelectionHandle === 'start');
                    var lineKey = pointRect
                      ? [Math.round(pointRect.top), Math.round(pointRect.bottom)].join(':')
                      : 'none';
                    var now = Date.now();
                    var shouldLogLine = lineKey !== selectionDebugLastLineKey || now - selectionDebugLastAt > 650;
                    var selection = window.getSelection();
                    var previousRange = selection && selection.rangeCount > 0 ? selection.getRangeAt(0).cloneRange() : null;
                    if (shouldLogLine) {
                      selectionDebugLastLineKey = lineKey;
                      selectionDebugLastAt = now;
                      readerSelectionDebugLog(
                        'drag_line seq=' + (++selectionDebugSequence) +
                        ' mode=' + selectionDebugMode() +
                        ' handle=' + activeSelectionHandle +
                        ' x=' + Math.round(event.clientX) +
                        ' y=' + Math.round(event.clientY) +
                        ' line=' + lineKey +
                        ' point=' + selectionDebugRange(pointRange) +
                        ' previous=' + selectionDebugRange(previousRange) +
                        ' next=' + selectionDebugRange(nextRange) +
                        ' nextChars=' + selectionDebugRangeTextLength(nextRange) +
                        ' scrollY=' + Math.round(window.scrollY)
                      );
                    }
                    savedRange = nextRange.cloneRange();
                    selection.removeAllRanges();
                    selection.addRange(savedRange);
                    positionSelectionHandles(selection);
                  }
                  function requestSelectionHandleUpdate(event) {
                    if (!activeSelectionHandle) return;
                    pendingSelectionHandleEvent = { clientX: event.clientX, clientY: event.clientY };
                    if (selectionHandleFrame !== null) return;
                    selectionHandleFrame = window.requestAnimationFrame(function () {
                      selectionHandleFrame = null;
                      var pending = pendingSelectionHandleEvent;
                      pendingSelectionHandleEvent = null;
                      if (pending) updateSelectionHandle(pending);
                    });
                  }
                  function cancelSelectionHandleFrame() {
                    if (selectionHandleFrame !== null) {
                      window.cancelAnimationFrame(selectionHandleFrame);
                      selectionHandleFrame = null;
                    }
                    pendingSelectionHandleEvent = null;
                  }
                  function beginSelectionHandleDrag(handleName, event) {
                    if (!savedRange && !restoreRange()) return;
                    cancelSelectionHandleFrame();
                    activeSelectionHandle = handleName;
                    selectionPointerDown = true;
                    menu.style.display = 'none';
                    selectionDebugLastLineKey = null;
                    selectionDebugLastAt = 0;
                    readerSelectionDebugLog(
                      'drag_begin seq=' + (++selectionDebugSequence) +
                      ' mode=' + selectionDebugMode() +
                      ' handle=' + activeSelectionHandle +
                      ' x=' + Math.round(event.clientX) +
                      ' y=' + Math.round(event.clientY) +
                      ' saved=' + selectionDebugRange(savedRange) +
                      ' chars=' + selectionDebugRangeTextLength(savedRange) +
                      ' scrollY=' + Math.round(window.scrollY)
                    );
                    event.preventDefault();
                    event.stopPropagation();
                    if (event.currentTarget && event.currentTarget.setPointerCapture) {
                      try { event.currentTarget.setPointerCapture(event.pointerId); } catch (error) {}
                    }
                  }
                  function finishSelectionHandleDrag(event) {
                    if (!activeSelectionHandle) return;
                    event.preventDefault();
                    event.stopPropagation();
                    cancelSelectionHandleFrame();
                    updateSelectionHandle(event);
                    readerSelectionDebugLog(
                      'drag_end seq=' + (++selectionDebugSequence) +
                      ' mode=' + selectionDebugMode() +
                      ' handle=' + activeSelectionHandle +
                      ' x=' + Math.round(event.clientX) +
                      ' y=' + Math.round(event.clientY) +
                      ' saved=' + selectionDebugRange(savedRange) +
                      ' chars=' + selectionDebugRangeTextLength(savedRange) +
                      ' scrollY=' + Math.round(window.scrollY)
                    );
                    activeSelectionHandle = null;
                    selectionPointerDown = false;
                    scheduleMenuFromSelection();
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
                    if (action === 'web-search') {
                      window.open('https://www.google.com/search?q=' + encodeURIComponent(text), '_blank');
                    }
                  }
                  function sendSelectionAction(action, text) {
                    var payload = {
                      action: action,
                      text: text
                    };
                    var actionRange = savedRange;
                    if (!actionRange) {
                      var actionSelection = window.getSelection && window.getSelection();
                      if (actionSelection && actionSelection.rangeCount > 0) actionRange = actionSelection.getRangeAt(0);
                    }
                    var actionSegments = selectionSegmentsForRange(actionRange);
                    if (actionSegments.length) {
                      var firstSegment = actionSegments[0];
                      var lastSegment = actionSegments[actionSegments.length - 1];
                      var sameChapter = actionSegments.every(function (segment) {
                        return segment.chapterIndex === firstSegment.chapterIndex;
                      });
                      if (sameChapter) {
                        var pageIndex = firstSegment.pageIndex;
                        if (pageIndex < 0 && firstSegment.startOffset !== null) {
                          var anchorPage = pageForLocator(firstSegment.chapterIndex, firstSegment.startOffset);
                          if (anchorPage) pageIndex = anchorPage.pageIndex;
                        }
                        var cfi = readerHighlightCfiForRange(
                          firstSegment,
                          lastSegment,
                          firstSegment.chapterIndex,
                          firstSegment.startOffset,
                          lastSegment.endOffset
                        );
                        payload.locator = {
                          chapterIndex: firstSegment.chapterIndex,
                          chapterId: firstSegment.chapterId,
                          href: firstSegment.chapterHref || null,
                          pageIndex: pageIndex >= 0 ? pageIndex : null,
                          startOffset: firstSegment.startOffset,
                          endOffset: lastSegment.endOffset,
                          blockIndex: firstSegment.blockIndex,
                          charOffset: firstSegment.charOffset,
                          textQuote: text,
                          cfi: cfi
                        };
                      }
                    }
                    if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
                      try {
                        window.kmpJsBridge.callNative('readerSelectionAction', JSON.stringify(payload));
                        return true;
                      } catch (error) {
                        readerConsoleLog('READER_SELECTION_ACTION bridge_error action=' + action + ' error=' + error);
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
                  function readerTextBlock(node) {
                    var parent = node && node.parentElement;
                    return parent && parent.closest
                      ? parent.closest('p, li, blockquote, pre, h1, h2, h3, h4, h5, h6, td, th, figcaption, div, section')
                      : null;
                  }
                  function contentStartOffset(content) {
                    var pageHost = content && content.closest ? content.closest('[data-reader-page-start]') : null;
                    return numberAttribute(content, 'data-reader-content-start', numberAttribute(pageHost, 'data-reader-page-start', 0));
                  }
                  function normalizedOffsetForBoundary(root, container, offset) {
                    if (!root || !container) return null;
                    var nodes = textNodesUnder(root, true);
                    if (!nodes.length) return 0;
                    var boundary = document.createRange();
                    try {
                      boundary.setStart(container, offset);
                      boundary.collapse(true);
                    } catch (error) {
                      boundary.detach && boundary.detach();
                      return null;
                    }
                    var state = {
                      cursor: 0,
                      sawText: false,
                      inWhitespace: false,
                      previousBlock: null
                    };
                    function applyBlockBoundary(node) {
                      var currentBlock = readerTextBlock(node);
                      if (state.previousBlock && currentBlock && currentBlock !== state.previousBlock && state.sawText && !state.inWhitespace) {
                        state.inWhitespace = true;
                        state.cursor += 1;
                      }
                      if (currentBlock) state.previousBlock = currentBlock;
                    }
                    function consumeNormalizedText(value, limit) {
                      var safeLimit = Math.max(0, Math.min(value.length, limit));
                      for (var i = 0; i < safeLimit; i++) {
                        if (/^\s$/.test(value[i])) {
                          if (!state.sawText) continue;
                          if (!state.inWhitespace) {
                            state.inWhitespace = true;
                            state.cursor += 1;
                          }
                          continue;
                        }
                        if (state.inWhitespace) state.inWhitespace = false;
                        state.sawText = true;
                        state.cursor += 1;
                      }
                    }
                    try {
                      for (var n = 0; n < nodes.length; n++) {
                        var node = nodes[n];
                        var value = node.nodeValue || '';
                        if (node === container) {
                          applyBlockBoundary(node);
                          consumeNormalizedText(value, offset);
                          return state.cursor;
                        }
                        var nodeRange = document.createRange();
                        var endsBeforeBoundary = false;
                        var startsAfterBoundary = false;
                        try {
                          nodeRange.selectNodeContents(node);
                          endsBeforeBoundary = nodeRange.compareBoundaryPoints(Range.END_TO_START, boundary) <= 0;
                          startsAfterBoundary = nodeRange.compareBoundaryPoints(Range.START_TO_START, boundary) >= 0;
                        } catch (error) {
                          startsAfterBoundary = true;
                        } finally {
                          nodeRange.detach && nodeRange.detach();
                        }
                        if (endsBeforeBoundary) {
                          applyBlockBoundary(node);
                          consumeNormalizedText(value, value.length);
                          continue;
                        }
                        if (startsAfterBoundary) return state.cursor;
                        return state.cursor;
                      }
                      return state.cursor;
                    } finally {
                      boundary.detach && boundary.detach();
                    }
                  }
                  function explicitTextHostForBoundary(root, container) {
                    if (!root || !container) return null;
                    var element = container.nodeType === Node.TEXT_NODE ? container.parentElement : container;
                    if (!element || !element.closest) return null;
                    var host = element.closest('[data-reader-text-start][data-reader-text-end]');
                    if (host && (host === root || root.contains(host))) return host;
                    return null;
                  }
                  function readerTextHostForOffset(content, offset, preferEnd) {
                    if (!content || !Number.isFinite(offset)) return null;
                    var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end][data-reader-cfi]'));
                    for (var i = 0; i < hosts.length; i++) {
                      var host = hosts[i];
                      var start = numberAttribute(host, 'data-reader-text-start', null);
                      var end = numberAttribute(host, 'data-reader-text-end', null);
                      var cfi = host.getAttribute && host.getAttribute('data-reader-cfi');
                      if (start === null || end === null || !cfi || cfi.charAt(0) !== '/') continue;
                      if (preferEnd) {
                        if (offset > start && offset <= end) return host;
                      } else if (offset >= start && offset < end) {
                        return host;
                      }
                    }
                    return null;
                  }
                  function readerCfiPointForOffset(content, offset, preferEnd) {
                    var host = readerTextHostForOffset(content, offset, preferEnd);
                    if (!host) return null;
                    var baseCfi = String(host.getAttribute('data-reader-cfi') || '').split(':')[0];
                    var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                    var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                    if (!baseCfi || hostStart === null || hostEnd === null) return null;
                    var localOffset = Math.max(0, Math.min(offset - hostStart, Math.max(0, hostEnd - hostStart)));
                    return baseCfi + ':' + localOffset;
                  }
                  function readerBlockPositionForOffset(content, offset, preferEnd) {
                    if (!content || !Number.isFinite(offset)) return null;
                    var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end][data-reader-block-index]'));
                    var host = null;
                    for (var i = 0; i < hosts.length; i++) {
                      var candidate = hosts[i];
                      var start = numberAttribute(candidate, 'data-reader-text-start', null);
                      var end = numberAttribute(candidate, 'data-reader-text-end', null);
                      if (start === null || end === null || end < start) continue;
                      if (preferEnd) {
                        if (offset > start && offset <= end) {
                          host = candidate;
                          break;
                        }
                      } else if (offset >= start && offset < end) {
                        host = candidate;
                        break;
                      }
                    }
                    if (!host) return null;
                    var blockIndex = numberAttribute(host, 'data-reader-block-index', null);
                    if (blockIndex === null) return null;
                    return {
                      blockIndex: blockIndex,
                      charOffset: offset
                    };
                  }
                  function readerHighlightCfiForRange(startSegment, endSegment, chapterIndex, startOffset, endOffset) {
                    var startPoint = readerCfiPointForOffset(startSegment && startSegment.content, startOffset, false);
                    var endPoint = readerCfiPointForOffset(endSegment && endSegment.content, endOffset, true);
                    if (startPoint && endPoint) return startPoint + '|' + endPoint;
                    return 'desktop:' + chapterIndex + ':' + startOffset + ':' + endOffset;
                  }
                  function absoluteOffsetForBoundary(root, container, offset) {
                    var host = explicitTextHostForBoundary(root, container);
                    if (!host) return null;
                    var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                    if (hostStart === null) return null;
                    var localOffset = offsetForBoundary(host, container, offset);
                    return localOffset === null ? null : hostStart + localOffset;
                  }
                  function boundaryOffsetWithinContent(content, container, offset) {
                    var explicitOffset = absoluteOffsetForBoundary(content, container, offset);
                    if (explicitOffset !== null) return explicitOffset;
                    var normalizedOffset = normalizedOffsetForBoundary(content, container, offset);
                    return normalizedOffset === null ? null : contentStartOffset(content) + normalizedOffset;
                  }
                  function trimSourceOffsets(rawStart, rawEnd, text) {
                    if (rawStart === null || rawEnd === null || rawEnd < rawStart) return { start: null, end: null };
                    var selectedText = String(text || '');
                    var trimmedText = selectedText.trim();
                    if (!trimmedText) return { start: null, end: null };
                    var leadingWhitespace = selectedText.length - selectedText.replace(/^\s+/, '').length;
                    var trailingWhitespace = selectedText.length - selectedText.replace(/\s+$/, '').length;
                    return { start: rawStart + leadingWhitespace, end: rawEnd - trailingWhitespace };
                  }
                  function selectionSourceOffsetsWithin(content, range) {
                    return rangeOffsetsWithinContent(content, range);
                  }
                  function rangeOffsetsWithinContent(content, range) {
                    if (!content || !range) return { start: null, end: null };
                    var rawStart = boundaryOffsetWithinContent(content, range.startContainer, range.startOffset);
                    var rawEnd = boundaryOffsetWithinContent(content, range.endContainer, range.endOffset);
                    return trimSourceOffsets(rawStart, rawEnd, range.toString());
                  }
                  function rangeIntersectsRange(range, candidate) {
                    if (!range || !candidate) return false;
                    try {
                      return range.compareBoundaryPoints(Range.END_TO_START, candidate) > 0 &&
                        range.compareBoundaryPoints(Range.START_TO_END, candidate) < 0;
                    } catch (error) {
                      return false;
                    }
                  }
                  function nodeInside(root, node) {
                    return !!root && !!node && (root === node || (root.contains && root.contains(node)));
                  }
                  function firstTextBoundary(root) {
                    var nodes = textNodesUnder(root, false);
                    if (nodes.length) return { node: nodes[0], offset: 0 };
                    return { node: root, offset: 0 };
                  }
                  function lastTextBoundary(root) {
                    var nodes = textNodesUnder(root, false);
                    if (nodes.length) {
                      var last = nodes[nodes.length - 1];
                      return { node: last, offset: (last.nodeValue || '').length };
                    }
                    return { node: root, offset: root && root.childNodes ? root.childNodes.length : 0 };
                  }
                  function clippedRangeForContent(content, range) {
                    if (!content || !range) return null;
                    var contentRange = document.createRange();
                    try {
                      contentRange.selectNodeContents(content);
                      var boundaryInside = nodeInside(content, range.startContainer) || nodeInside(content, range.endContainer);
                      var intersectsContent = boundaryInside;
                      if (!intersectsContent && range.intersectsNode) {
                        try { intersectsContent = range.intersectsNode(content); } catch (error) {}
                      }
                      if (!intersectsContent && !rangeIntersectsRange(range, contentRange)) return null;
                      var clipped = document.createRange();
                      if (nodeInside(content, range.startContainer)) {
                        clipped.setStart(range.startContainer, range.startOffset);
                      } else {
                        var first = firstTextBoundary(content);
                        clipped.setStart(first.node, first.offset);
                      }
                      if (nodeInside(content, range.endContainer)) {
                        clipped.setEnd(range.endContainer, range.endOffset);
                      } else {
                        var last = lastTextBoundary(content);
                        clipped.setEnd(last.node, last.offset);
                      }
                      if (clipped.collapsed) {
                        clipped.detach && clipped.detach();
                        return null;
                      }
                      return clipped;
                    } catch (error) {
                      return null;
                    } finally {
                      contentRange.detach && contentRange.detach();
                    }
                  }
                  function selectionSegmentsForRange(range) {
                    if (!range) return [];
                    var contents = Array.prototype.slice.call(document.querySelectorAll('.page[data-reader-page-index] .reader-content'));
                    if (!contents.length) {
                      contents = Array.prototype.slice.call(document.querySelectorAll('[data-reader-chapter-index] .reader-content'));
                    }
                    if (!contents.length) {
                      var container = range.commonAncestorContainer;
                      if (container && container.nodeType !== Node.ELEMENT_NODE) container = container.parentElement;
                      var content = container && container.closest ? container.closest('.reader-content') : null;
                      if (content) contents = [content];
                    }
                    var diagnostics = {
                      noRange: 0,
                      badOffsets: [],
                      missingHost: 0,
                      blankText: 0
                    };
                    var segments = contents.map(function (content) {
                      var segmentRange = clippedRangeForContent(content, range);
                      if (!segmentRange) {
                        diagnostics.noRange += 1;
                        return null;
                      }
                      var offsets = rangeOffsetsWithinContent(content, segmentRange);
                      if (offsets.start === null || offsets.end === null || offsets.end <= offsets.start) {
                        if (diagnostics.badOffsets.length < 4) {
                          diagnostics.badOffsets.push(
                            'start=' + offsets.start +
                            ',end=' + offsets.end +
                            ',textChars=' + segmentRange.toString().trim().length
                          );
                        }
                        segmentRange.detach && segmentRange.detach();
                        return null;
                      }
                      var readerHost = content.closest ? content.closest('[data-reader-chapter-index]') : null;
                      if (!readerHost) {
                        diagnostics.missingHost += 1;
                        segmentRange.detach && segmentRange.detach();
                        return null;
                      }
                      var segmentText = segmentRange.toString().trim();
                      if (!segmentText) {
                        diagnostics.blankText += 1;
                        segmentRange.detach && segmentRange.detach();
                        return null;
                      }
                      var blockPosition = readerBlockPositionForOffset(content, offsets.start, false);
                      readerDesktopHighlightMapLog(
                        'web_selection_segment chapter=' + parseInt(readerHost.getAttribute('data-reader-chapter-index') || '0', 10) +
                        ' page=' + parseInt(readerHost.getAttribute('data-reader-page-index') || '-1', 10) +
                        ' offsets=' + offsets.start + '..' + offsets.end +
                        ' block=' + (blockPosition ? blockPosition.blockIndex : 'null') +
                        ' char=' + (blockPosition ? blockPosition.charOffset : offsets.start) +
                        ' textChars=' + segmentText.length +
                        ' text="' + readerTtsPreview(segmentText, 120) + '"'
                      );
                      return {
                        range: segmentRange,
                        content: content,
                        readerHost: readerHost,
                        text: segmentText,
                        chapterIndex: parseInt(readerHost.getAttribute('data-reader-chapter-index') || '0', 10),
                        chapterId: readerHost.getAttribute('data-reader-chapter-id'),
                        chapterHref: readerHost.getAttribute('data-reader-chapter-href'),
                        pageIndex: parseInt(readerHost.getAttribute('data-reader-page-index') || '-1', 10),
                        startOffset: offsets.start,
                        endOffset: offsets.end,
                        blockIndex: blockPosition ? blockPosition.blockIndex : null,
                        charOffset: blockPosition ? blockPosition.charOffset : offsets.start
                      };
                    }).filter(Boolean);
                    if (!segments.length) {
                      readerHighlightFlowLog(
                        'selection_segments_rejected contents=' + contents.length +
                        ' noRange=' + diagnostics.noRange +
                        ' badOffsets=' + diagnostics.badOffsets.length +
                        ' missingHost=' + diagnostics.missingHost +
                        ' blankText=' + diagnostics.blankText +
                        ' common=' + selectionDebugNode(range.commonAncestorContainer) +
                        ' badOffsetSamples="' + diagnostics.badOffsets.join('; ') + '"'
                      );
                    }
                    return segments;
                  }
                  function rangeMatchesStoredOffsets(content, range, startOffset, endOffset) {
                    var offsets = rangeOffsetsWithinContent(content, range);
                    if (offsets.start === null || offsets.end === null) return false;
                    return Math.abs(offsets.start - startOffset) <= 1 && Math.abs(offsets.end - endOffset) <= 1;
                  }
                  function createReaderHighlightMarker(highlightId, colorId, startOffset, endOffset) {
                    var marker = document.createElement('span');
                    marker.className = 'reader-user-highlight user-highlight-' + (colorId || 'yellow');
                    if (highlightId) marker.setAttribute('data-reader-highlight-id', highlightId);
                    if (startOffset !== undefined && startOffset !== null) {
                      marker.setAttribute('data-reader-start-offset', String(startOffset));
                    }
                    if (endOffset !== undefined && endOffset !== null) {
                      marker.setAttribute('data-reader-end-offset', String(endOffset));
                    }
                    return marker;
                  }
                  function rangeIntersectsTextNode(range, node) {
                    var nodeRange = document.createRange();
                    try {
                      if (range.intersectsNode) return range.intersectsNode(node);
                      nodeRange.selectNodeContents(node);
                      return range.compareBoundaryPoints(Range.END_TO_START, nodeRange) > 0 &&
                        range.compareBoundaryPoints(Range.START_TO_END, nodeRange) < 0;
                    } catch (error) {
                      return false;
                    } finally {
                      nodeRange.detach && nodeRange.detach();
                    }
                  }
                  function textSegmentsInRange(range) {
                    var root = range.commonAncestorContainer;
                    if (root.nodeType === Node.TEXT_NODE) root = root.parentNode;
                    if (!root) return [];
                    var nodes = textNodesUnder(root, true).filter(function (node) {
                      return rangeIntersectsTextNode(range, node);
                    });
                    return nodes.map(function (node) {
                      var length = (node.nodeValue || '').length;
                      var start = node === range.startContainer ? range.startOffset : 0;
                      var end = node === range.endContainer ? range.endOffset : length;
                      start = Math.max(0, Math.min(length, start));
                      end = Math.max(0, Math.min(length, end));
                      return { node: node, start: start, end: end };
                    }).filter(function (segment) {
                      return segment.end > segment.start;
                    });
                  }
                  function wrapRangeTextSegments(range, markerFactory) {
                    var segments = textSegmentsInRange(range);
                    var wrapped = 0;
                    for (var index = segments.length - 1; index >= 0; index--) {
                      var segment = segments[index];
                      var node = segment.node;
                      var parent = node.parentNode;
                      if (!parent) continue;
                      if (parent.closest && parent.closest('span[class*="user-highlight-"], mark.reader-user-highlight')) continue;
                      var value = node.nodeValue || '';
                      var selected = value.substring(segment.start, segment.end);
                      if (!selected) continue;
                      var fragment = document.createDocumentFragment();
                      if (segment.start > 0) fragment.appendChild(document.createTextNode(value.substring(0, segment.start)));
                      var marker = markerFactory();
                      marker.textContent = selected;
                      fragment.appendChild(marker);
                      if (segment.end < value.length) fragment.appendChild(document.createTextNode(value.substring(segment.end)));
                      parent.replaceChild(fragment, node);
                      wrapped++;
                    }
                    return wrapped > 0;
                  }
                  function unwrapReaderHighlights() {
                    var marks = Array.prototype.slice.call(document.querySelectorAll('span[class*="user-highlight-"], mark.reader-user-highlight'));
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
                    var chapter = readerHostForLocator(chapterIndex, startOffset, endOffset);
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
                        var sourceCfiBases = readerCfiBases(sourceCfi);
                        var cfiHosts = hosts.filter(function (host) {
                          return readerHostMatchesCfi(host, sourceCfiBases);
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
                  function readerCfiPointBase(cfiPoint) {
                    return String(cfiPoint || '').split(':')[0];
                  }
                  function readerCfiBases(sourceCfi) {
                    var stable = stableReaderCfi(sourceCfi);
                    if (!stable) return [];
                    var seen = {};
                    return String(stable).split('|').map(function (part) {
                      return readerCfiPointBase(part);
                    }).filter(function (base) {
                      if (!base || base.charAt(0) !== '/' || seen[base]) return false;
                      seen[base] = true;
                      return true;
                    });
                  }
                  function readerHostMatchesCfi(host, cfiBases) {
                    if (!host || !host.getAttribute || !cfiBases || !cfiBases.length) return false;
                    return cfiBases.indexOf(host.getAttribute('data-reader-cfi')) >= 0;
                  }
                  function readerElementForSourceCfi(root, sourceCfi) {
                    if (!root || !root.querySelector) return null;
                    var bases = readerCfiBases(sourceCfi);
                    for (var i = 0; i < bases.length; i++) {
                      var element = root.querySelector('[data-reader-cfi="' + selectorValue(bases[i]) + '"]');
                      if (element) return element;
                    }
                    return null;
                  }
                  function readerCfiPointLocalOffset(cfiPoint) {
                    var parts = String(cfiPoint || '').split(':');
                    if (parts.length < 2) return 0;
                    var parsed = parseInt(parts[1], 10);
                    return Number.isFinite(parsed) ? parsed : 0;
                  }
                  function readerHostElementForCfiPoint(chapterIndex, cfiPoint) {
                    var baseCfi = readerCfiPointBase(cfiPoint);
                    if (!baseCfi || baseCfi.charAt(0) !== '/') return null;
                    var chapterSelector = '[data-reader-chapter-index="' + selectorValue(chapterIndex) + '"]';
                    var hosts = Array.prototype.slice.call(document.querySelectorAll(chapterSelector));
                    for (var i = 0; i < hosts.length; i++) {
                      var content = hosts[i].querySelector('.reader-content') || hosts[i];
                      var cfiHost = content.querySelector('[data-reader-cfi="' + selectorValue(baseCfi) + '"]');
                      if (cfiHost) return cfiHost;
                    }
                    return null;
                  }
                  function readerContentOffsetForCfiPoint(chapterIndex, cfiPoint) {
                    var cfiHost = readerHostElementForCfiPoint(chapterIndex, cfiPoint);
                    if (!cfiHost) return null;
                    var hostStart = numberAttribute(cfiHost, 'data-reader-text-start', null);
                    var hostEnd = numberAttribute(cfiHost, 'data-reader-text-end', null);
                    if (hostStart === null || hostEnd === null || hostEnd < hostStart) return null;
                    var localOffset = Math.max(0, readerCfiPointLocalOffset(cfiPoint));
                    return Math.max(hostStart, Math.min(hostEnd, hostStart + localOffset));
                  }
                  function readerOffsetsForSourceCfi(chapterIndex, sourceCfi, expectedText) {
                    if (!sourceCfi || sourceCfi.charAt(0) !== '/') return null;
                    var parts = String(sourceCfi).split('|');
                    var startPoint = parts[0];
                    var endPoint = parts[parts.length - 1] || startPoint;
                    var startOffset = readerContentOffsetForCfiPoint(chapterIndex, startPoint);
                    var endOffset = readerContentOffsetForCfiPoint(chapterIndex, endPoint);
                    if (startOffset === null || endOffset === null || endOffset < startOffset) return null;
                    if (endOffset === startOffset && expectedText) endOffset = startOffset + String(expectedText).length;
                    return { startOffset: startOffset, endOffset: endOffset };
                  }
                  function applyHighlightObject(highlight) {
                    if (!highlight) return;
                    var locator = highlight.locator || {};
                    var chapterIndex = locator.chapterIndex;
                    if (chapterIndex === undefined || chapterIndex === null) chapterIndex = highlight.chapterIndex;
                    var startOffset = locator.startOffset;
                    var endOffset = locator.endOffset;
                    var sourceCfi = locator.cfi || highlight.cfi;
                    var expectedText = locator.textQuote || highlight.text || '';
                    var sourceCfiIsStructural = sourceCfi && String(sourceCfi).charAt(0) === '/';
                    readerDesktopHighlightMapLog(
                      'web_apply_start id=' + (highlight.id || '') +
                      ' chapter=' + chapterIndex +
                      ' page=' + locator.pageIndex +
                      ' offsets=' + startOffset + '..' + endOffset +
                      ' block=' + locator.blockIndex +
                      ' char=' + locator.charOffset +
                      ' textChars=' + String(expectedText || '').length +
                      ' cfi=' + readerTtsPreview(sourceCfi, 160)
                    );
                    var cfiOffsets = readerOffsetsForSourceCfi(chapterIndex, sourceCfi, expectedText);
                    if (cfiOffsets) {
                      startOffset = cfiOffsets.startOffset;
                      endOffset = cfiOffsets.endOffset;
                      readerDesktopHighlightMapLog(
                        'web_apply_cfi_offsets id=' + (highlight.id || '') +
                        ' offsets=' + startOffset + '..' + endOffset
                      );
                    } else if (sourceCfiIsStructural) {
                      readerDesktopHighlightMapLog(
                        'web_apply_cfi_offsets_missing id=' + (highlight.id || '') +
                        ' cfi=' + readerTtsPreview(sourceCfi, 160)
                      );
                      startOffset = null;
                      endOffset = null;
                    }
                    if (startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) {
                      var blockChar = parseInt(locator.charOffset, 10);
                      if (Number.isFinite(blockChar) && expectedText) {
                        startOffset = blockChar;
                        endOffset = blockChar + String(expectedText).length;
                        readerDesktopHighlightMapLog(
                          'web_apply_block_offsets id=' + (highlight.id || '') +
                          ' offsets=' + startOffset + '..' + endOffset +
                          ' block=' + locator.blockIndex
                        );
                      }
                    }
                    var hasPreciseOffsets = !(chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset);
                    if (chapterIndex === undefined || chapterIndex === null || startOffset === undefined || startOffset === null || endOffset === undefined || endOffset === null || endOffset <= startOffset) {
                      readerDesktopHighlightMapLog(
                        'web_apply_fallback_request id=' + (highlight.id || '') +
                        ' reason=invalid_offsets chapter=' + chapterIndex +
                        ' offsets=' + startOffset + '..' + endOffset
                      );
                      if (sourceCfiIsStructural) return;
                      applyHighlightTextFallback(highlight);
                      return;
                    }
                    var targetChapters = readerHostsForLocator(chapterIndex, startOffset, endOffset);
                    if (!targetChapters.length) {
                      readerDesktopHighlightMapLog(
                        'web_apply_fallback_request id=' + (highlight.id || '') +
                        ' reason=no_target_chapters chapter=' + chapterIndex +
                        ' offsets=' + startOffset + '..' + endOffset
                      );
                      if (hasPreciseOffsets) return;
                      applyHighlightTextFallback(highlight);
                      return;
                    }
                    var expectedNormalized = readerTtsNormalized(expectedText);
                    var applied = false;
                    targetChapters.forEach(function (targetChapter) {
                      var pageStart = numberAttribute(targetChapter, 'data-reader-page-start', null);
                      var pageEnd = numberAttribute(targetChapter, 'data-reader-page-end', null);
                      if (pageStart !== null && pageEnd !== null && (startOffset >= pageEnd || endOffset <= pageStart)) return;
                      var segmentStart = pageStart === null ? startOffset : Math.max(startOffset, pageStart);
                      var segmentEnd = pageEnd === null ? endOffset : Math.min(endOffset, pageEnd);
                      if (segmentEnd <= segmentStart) return;
                      var range = rangeForOffsets(chapterIndex, segmentStart, segmentEnd, sourceCfi);
                      var actualNormalized = range && !range.collapsed ? readerTtsNormalized(range.toString()) : '';
                      var isSegment = segmentStart !== startOffset || segmentEnd !== endOffset || targetChapters.length > 1;
                      if (expectedNormalized && isSegment && actualNormalized && expectedNormalized.indexOf(actualNormalized) < 0) {
                        if (range && range.detach) range.detach();
                        readerSelectionDebugLog(
                          'highlight_segment_mismatch id=' + (highlight.id || '') +
                          ' offsets=' + segmentStart + '..' + segmentEnd +
                          ' expected="' + readerTtsPreview(expectedText, 120) + '"' +
                          ' actual="' + readerTtsPreview(actualNormalized, 120) + '"'
                        );
                        return;
                      }
                      if (expectedNormalized && !isSegment && (!range || range.collapsed || actualNormalized !== expectedNormalized)) {
                        var chapter = targetChapter;
                        var content = chapter ? (chapter.querySelector('.reader-content') || chapter) : null;
                        var searchRoot = content;
                        if (content && sourceCfi) {
                          searchRoot = readerElementForSourceCfi(content, sourceCfi) || content;
                        }
                        if (content && searchRoot === content) {
                          var hosts = Array.prototype.slice.call(content.querySelectorAll('[data-reader-text-start][data-reader-text-end]'));
                          var containing = null;
                          var bestSpan = Number.MAX_SAFE_INTEGER;
                          hosts.forEach(function (host) {
                            var hostStart = numberAttribute(host, 'data-reader-text-start', null);
                            var hostEnd = numberAttribute(host, 'data-reader-text-end', null);
                            if (hostStart === null || hostEnd === null || hostEnd < hostStart) return;
                            if (segmentStart >= hostEnd || segmentEnd <= hostStart) return;
                            var span = hostEnd - hostStart;
                            if (span < bestSpan) {
                              containing = host;
                              bestSpan = span;
                            }
                          });
                          searchRoot = containing || content;
                        }
                        var textRange = normalizedRangeForText(searchRoot, expectedNormalized, false);
                        if (textRange && !textRange.collapsed && rangeMatchesStoredOffsets(content, textRange, segmentStart, segmentEnd)) {
                          if (range && range.detach) range.detach();
                          range = textRange;
                        } else {
                          if (textRange && textRange.detach) textRange.detach();
                          if (range && range.detach) range.detach();
                          readerSelectionDebugLog(
                            'highlight_expected_mismatch id=' + (highlight.id || '') +
                            ' offsets=' + segmentStart + '..' + segmentEnd +
                            ' expected="' + readerTtsPreview(expectedText, 120) + '"' +
                            ' actual="' + readerTtsPreview(actualNormalized, 120) + '"'
                          );
                          return;
                        }
                      }
                      if (!range || range.collapsed) {
                        if (range && range.detach) range.detach();
                        return;
                      }
                      wrapRangeTextSegments(range, function () {
                        var marker = createReaderHighlightMarker(highlight.id, highlight.colorId || 'yellow', segmentStart, segmentEnd);
                        marker.setAttribute('data-cfi', sourceCfi || highlight.cfi || ('desktop:' + chapterIndex + ':' + startOffset + ':' + endOffset));
                        return marker;
                      });
                      readerDesktopHighlightMapLog(
                        'web_apply_segment id=' + (highlight.id || '') +
                        ' chapter=' + chapterIndex +
                        ' page=' + numberAttribute(targetChapter, 'data-reader-page-index', 'null') +
                        ' segment=' + segmentStart + '..' + segmentEnd +
                        ' expectedChars=' + expectedNormalized.length +
                        ' actualChars=' + actualNormalized.length +
                        ' root=' + readerElementLabel(targetChapter)
                      );
                      applied = true;
                      range.detach && range.detach();
                    });
                    if (!applied) {
                      readerDesktopHighlightMapLog(
                        'web_apply_fallback_request id=' + (highlight.id || '') +
                        ' reason=no_segments_applied chapter=' + chapterIndex +
                        ' offsets=' + startOffset + '..' + endOffset
                      );
                      if (hasPreciseOffsets) return;
                      applyHighlightTextFallback(highlight);
                    }
                  }
                  function applyHighlightTextFallback(highlight) {
                    var locator = highlight && highlight.locator ? highlight.locator : {};
                    var chapterIndex = locator.chapterIndex;
                    if (chapterIndex === undefined || chapterIndex === null) chapterIndex = highlight.chapterIndex;
                    var expectedText = readerTtsNormalized(locator.textQuote || highlight.text || '');
                    if (!expectedText) return false;
                    var blockElement = chapterIndex === undefined || chapterIndex === null
                      ? null
                      : readerElementForBlockLocator(chapterIndex, locator.blockIndex, locator.charOffset);
                    var root = blockElement || (chapterIndex === undefined || chapterIndex === null
                      ? document.body
                      : readerHostForLocator(chapterIndex, locator.startOffset, locator.endOffset));
                    if (!root) root = document.body;
                    var content = root.querySelector ? (root.querySelector('.reader-content') || root) : root;
                    var sourceCfi = locator.cfi || highlight.cfi;
                    var cfiPoint = sourceCfi ? String(sourceCfi).split('|')[0] : null;
                    var cfiHost = readerHostElementForCfiPoint(chapterIndex, cfiPoint);
                    if (cfiHost) content = cfiHost;
                    readerDesktopHighlightMapLog(
                      'web_text_fallback_start id=' + ((highlight && highlight.id) || '') +
                      ' chapter=' + chapterIndex +
                      ' root=' + readerElementLabel(content) +
                      ' expectedChars=' + expectedText.length +
                      ' cfi=' + readerTtsPreview(sourceCfi, 160)
                    );
                    var range = normalizedRangeForText(content, expectedText, false);
                    if (!range || range.collapsed) {
                      readerDesktopHighlightMapLog(
                        'web_text_fallback_result id=' + ((highlight && highlight.id) || '') +
                        ' applied=false reason=' + (!range ? 'no_range' : 'collapsed')
                      );
                      return false;
                    }
                    wrapRangeTextSegments(range, function () {
                      var marker = createReaderHighlightMarker(highlight.id, highlight.colorId || 'yellow', null, null);
                      marker.setAttribute('data-cfi', locator.cfi || highlight.cfi || '');
                      return marker;
                    });
                    readerDesktopHighlightMapLog(
                      'web_text_fallback_result id=' + ((highlight && highlight.id) || '') +
                      ' applied=true text="' + readerTtsPreview(range.toString(), 120) + '"'
                    );
                    range.detach && range.detach();
                    return true;
                  }
                  window.readerApplyHighlights = function (highlights) {
                    var previousX = window.scrollX;
                    var previousY = window.scrollY;
                    readerCurrentHighlights = Array.isArray(highlights) ? highlights.slice() : [];
                    unwrapReaderHighlights();
                    if (readerCurrentHighlights.length > 0) {
                      readerCurrentHighlights
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
                  function scheduleReaderHighlightReconcile() {
                    if (readerHighlightReconcileTimer !== null) window.clearTimeout(readerHighlightReconcileTimer);
                    readerHighlightReconcileTimer = window.setTimeout(function () {
                      readerHighlightReconcileTimer = null;
                      if (window.readerApplyHighlights) window.readerApplyHighlights(readerCurrentHighlights);
                    }, 1200);
                  }
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
                      var chapter = readerHostForLocator(chapterIndex, startOffset, endOffset);
                      var content = chapter ? (chapter.querySelector('.reader-content') || chapter) : null;
                      var searchRoot = content;
                      if (content && sourceCfi) {
                        searchRoot = readerElementForSourceCfi(content, sourceCfi) || content;
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
                      if (follow && locator) scrollToLocator(locator, { align: 'center', trackRestore: false });
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
                    var text = selection.toString().trim();
                    if (!text) return;
                    readerHighlightFlowLog(
                      'selection_begin mode=' + selectionDebugMode() +
                      ' color=' + (colorId || 'yellow') +
                      ' textChars=' + text.length +
                      ' range=' + selectionDebugRange(range)
                    );
                    var segments = selectionSegmentsForRange(range);
                    if (!segments.length) {
                      readerHighlightFlowLog(
                        'selection_segments_missing mode=' + selectionDebugMode() +
                        ' text="' + readerTtsPreview(text, 120) + '"' +
                        ' range=' + selectionDebugRange(range)
                      );
                      readerSelectionDebugLog('highlight_selection_segments_missing text="' + readerTtsPreview(text, 120) + '"');
                      return;
                    }
                    readerHighlightFlowLog(
                      'selection_segments count=' + segments.length +
                      ' details="' + segments.map(function (segment) {
                        return 'chapter=' + segment.chapterIndex +
                          ',page=' + segment.pageIndex +
                          ',offsets=' + segment.startOffset + '..' + segment.endOffset +
                          ',chars=' + segment.text.length;
                      }).join('; ') + '"'
                    );
                    var firstSegment = segments[0];
                    var lastSegment = segments[segments.length - 1];
                    var sameChapter = segments.every(function (segment) {
                      return segment.chapterIndex === firstSegment.chapterIndex;
                    });
                    var payloads = [];
                    if (sameChapter) {
                      var chapterIndex = firstSegment.chapterIndex;
                      var startOffset = firstSegment.startOffset;
                      var endOffset = lastSegment.endOffset;
                      var pageIndex = firstSegment.pageIndex;
                      if (pageIndex < 0 && startOffset !== null) {
                        var anchorPage = pageForLocator(chapterIndex, startOffset);
                        if (anchorPage) pageIndex = anchorPage.pageIndex;
                      }
                      var cfi = readerHighlightCfiForRange(firstSegment, lastSegment, chapterIndex, startOffset, endOffset);
                      readerDesktopHighlightMapLog(
                        'web_payload_build sameChapter=true chapter=' + chapterIndex +
                        ' page=' + pageIndex +
                        ' offsets=' + startOffset + '..' + endOffset +
                        ' block=' + firstSegment.blockIndex +
                        ' char=' + firstSegment.charOffset +
                        ' textChars=' + text.length +
                        ' cfi=' + readerTtsPreview(cfi, 160)
                      );
                      payloads.push({
                        cfi: cfi,
                        text: text,
                        colorId: colorId || 'yellow',
                        chapterIndex: chapterIndex,
                        locator: {
                          chapterIndex: chapterIndex,
                          chapterId: firstSegment.chapterId,
                          href: firstSegment.chapterHref || null,
                          pageIndex: pageIndex >= 0 ? pageIndex : null,
                          startOffset: startOffset,
                          endOffset: endOffset,
                          blockIndex: firstSegment.blockIndex,
                          charOffset: firstSegment.charOffset,
                          textQuote: text,
                          cfi: cfi
                        }
                      });
                    } else {
                      segments.forEach(function (segment) {
                        var cfi = readerHighlightCfiForRange(segment, segment, segment.chapterIndex, segment.startOffset, segment.endOffset);
                        readerDesktopHighlightMapLog(
                          'web_payload_build sameChapter=false chapter=' + segment.chapterIndex +
                          ' page=' + segment.pageIndex +
                          ' offsets=' + segment.startOffset + '..' + segment.endOffset +
                          ' block=' + segment.blockIndex +
                          ' char=' + segment.charOffset +
                          ' textChars=' + segment.text.length +
                          ' cfi=' + readerTtsPreview(cfi, 160)
                        );
                        payloads.push({
                          cfi: cfi,
                          text: segment.text,
                          colorId: colorId || 'yellow',
                          chapterIndex: segment.chapterIndex,
                          locator: {
                            chapterIndex: segment.chapterIndex,
                            chapterId: segment.chapterId,
                            href: segment.chapterHref || null,
                            pageIndex: segment.pageIndex >= 0 ? segment.pageIndex : null,
                            startOffset: segment.startOffset,
                            endOffset: segment.endOffset,
                            blockIndex: segment.blockIndex,
                            charOffset: segment.charOffset,
                            textQuote: segment.text,
                            cfi: cfi
                          }
                        });
                      });
                    }
                    readerHighlightFlowLog(
                      'payloads_built count=' + payloads.length +
                      ' sameChapter=' + sameChapter +
                      ' details="' + payloads.map(function (payload) {
                        var locator = payload.locator || {};
                        return 'cfi=' + readerTtsPreview(payload.cfi, 80) +
                          ',color=' + payload.colorId +
                          ',chapter=' + payload.chapterIndex +
                          ',page=' + locator.pageIndex +
                          ',offsets=' + locator.startOffset + '..' + locator.endOffset +
                          ',textChars=' + (payload.text || '').length;
                      }).join('; ') + '"'
                    );
                    try {
                      if (sameChapter) {
                        var payload = payloads[0];
                        var localRange = range.cloneRange ? range.cloneRange() : range;
                        var wrappedSingle = false;
                        try {
                          wrappedSingle = wrapRangeTextSegments(localRange, function () {
                            var marker = createReaderHighlightMarker(null, colorId || 'yellow', payload.locator.startOffset, payload.locator.endOffset);
                            marker.setAttribute('data-cfi', payload.cfi);
                            return marker;
                          });
                        } finally {
                          if (localRange !== range && localRange.detach) localRange.detach();
                        }
                        readerHighlightFlowLog(
                          'local_wrap_done sameChapter=true wrapped=' + wrappedSingle +
                          ' cfi="' + readerTtsPreview(payload.cfi, 120) + '"'
                        );
                      } else {
                        segments.forEach(function (segment, index) {
                          var payload = payloads[index];
                          var wrappedSegment = wrapRangeTextSegments(segment.range, function () {
                            var marker = createReaderHighlightMarker(null, colorId || 'yellow', segment.startOffset, segment.endOffset);
                            marker.setAttribute('data-cfi', payload.cfi);
                            return marker;
                          });
                          readerHighlightFlowLog(
                            'local_wrap_done sameChapter=false segment=' + index +
                            ' wrapped=' + wrappedSegment +
                            ' cfi="' + readerTtsPreview(payload.cfi, 120) + '"'
                          );
                        });
                      }
                    } catch (error) {
                      readerHighlightFlowLog('local_wrap_error error=' + readerTtsPreview(error, 180));
                      readerSelectionDebugLog('highlight_local_wrap_error error=' + readerTtsPreview(error, 180));
                    } finally {
                      segments.forEach(function (segment) {
                        if (segment.range && segment.range.detach) segment.range.detach();
                      });
                    }
                    payloads.forEach(function (payload) {
                      if (payload.text.length > 0) sendReaderHighlightCreated(payload, 0);
                    });
                    readerHighlightFlowLog('selection_end sentPayloads=' + payloads.length);
                    scheduleReaderHighlightReconcile();
                    selection.removeAllRanges();
                    hideMenu();
                  }
                  menu.addEventListener('mousedown', function (event) {
                    event.preventDefault();
                  });
                  if (startHandle && endHandle) {
                    startHandle.addEventListener('pointerdown', function (event) {
                      beginSelectionHandleDrag('start', event);
                    });
                    endHandle.addEventListener('pointerdown', function (event) {
                      beginSelectionHandleDrag('end', event);
                    });
                    [startHandle, endHandle].forEach(function (handle) {
                      handle.addEventListener('pointermove', function (event) {
                        if (!activeSelectionHandle) return;
                        event.preventDefault();
                        event.stopPropagation();
                        requestSelectionHandleUpdate(event);
                      });
                      handle.addEventListener('mousedown', function (event) {
                        event.preventDefault();
                        event.stopPropagation();
                      });
                      handle.addEventListener('pointerup', finishSelectionHandleDrag);
                      handle.addEventListener('pointercancel', finishSelectionHandleDrag);
                    });
                  }
                  menu.addEventListener('click', function (event) {
                    var target = event.target && event.target.closest ? event.target.closest('button[data-action]') : event.target;
                    var action = target && target.getAttribute('data-action');
                    var text = selectionText();
                    if (!text && restoreRange()) text = selectionText();
                    if (!text) {
                      hideMenu();
                      return;
                    }
                    if (action === 'copy') copyText(text);
                    if (action === 'highlight') highlightRange(target.getAttribute('data-color-id') || 'yellow');
                    if (action === 'palette') sendSelectionAction('palette', text);
                    if (action === 'define') sendSelectionAction('define', text);
                    if (action === 'speak') sendSelectionAction('speak', text);
                    if (action === 'web-search') sendSelectionAction('web-search', text);
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
                  document.addEventListener('selectionchange', function () {
                    if (menu.contains(document.activeElement)) return;
                    if (activeSelectionHandle) return;
                    if (selectionPointerDown) return;
                    scheduleMenuFromSelection();
                  });
                  document.addEventListener('selectstart', function (event) {
                    if (!activeSelectionHandle) return;
                    event.preventDefault();
                    event.stopPropagation();
                  }, true);
                  document.addEventListener('pointermove', function (event) {
                    if (!activeSelectionHandle) return;
                    event.preventDefault();
                    event.stopPropagation();
                    requestSelectionHandleUpdate(event);
                  });
                  document.addEventListener('pointerup', function (event) {
                    if (activeSelectionHandle) {
                      finishSelectionHandleDrag(event);
                      return;
                    }
                    if (selectionPointerDown && !menu.contains(event.target)) {
                      selectionPointerDown = false;
                      scheduleMenuFromSelection();
                      scheduleVisiblePageReport();
                    }
                  });
                  document.addEventListener('pointercancel', function () {
                    if (!activeSelectionHandle) {
                      selectionPointerDown = false;
                      scheduleMenuFromSelection();
                      scheduleVisiblePageReport();
                    }
                  });
                  document.addEventListener('mouseup', function (event) {
                    if (menu.contains(event.target)) return;
                    if (activeSelectionHandle) return;
                    selectionPointerDown = false;
                    scheduleMenuFromSelection();
                    scheduleVisiblePageReport();
                  });
                  document.addEventListener('touchend', function (event) {
                    if (menu.contains(event.target)) return;
                    if (activeSelectionHandle) return;
                    selectionPointerDown = false;
                    scheduleMenuFromSelection();
                    scheduleVisiblePageReport();
                  }, { passive: true });
                  document.addEventListener('touchcancel', function () {
                    if (activeSelectionHandle) return;
                    selectionPointerDown = false;
                    scheduleMenuFromSelection();
                    scheduleVisiblePageReport();
                  }, { passive: true });
                  document.addEventListener('keyup', function () {
                    scheduleMenuFromSelection();
                  });
                  document.addEventListener('scroll', function () {
                    if (!selectionPointerDown && !activeSelectionHandle) {
                      hideMenu();
                    }
                  }, true);
                  document.addEventListener('scroll', scheduleVisiblePageReport, true);
                  window.addEventListener('scroll', scheduleVisiblePageReport, { passive: true });
                  window.addEventListener('wheel', function () { clearPendingRestoreLocator('wheel'); }, { passive: true });
                  window.addEventListener('touchstart', function () { clearPendingRestoreLocator('touchstart'); }, { passive: true });
                  window.addEventListener('keydown', function () { clearPendingRestoreLocator('keydown'); });
                  document.addEventListener('pointerdown', function (event) {
                    clearPendingRestoreLocator('pointerdown');
                    if (event.button === 0 && !menu.contains(event.target)) {
                      selectionPointerDown = true;
                      hideMenu();
                    }
                  });
                  document.addEventListener('mousedown', function (event) {
                    if (event.button === 0 && !menu.contains(event.target)) {
                      selectionPointerDown = true;
                      hideMenu();
                    }
                  });
                  scrollToActiveLocator();
                  reportVisiblePage();
                  window.setTimeout(function () { readerPaginationLayoutLog('initial_timeout'); }, 80);
                  window.addEventListener('load', scrollToActiveLocator, { once: true });
                  window.addEventListener('load', reportVisiblePage, { once: true });
                  window.addEventListener('load', function () { readerPaginationLayoutLog('window_load'); }, { once: true });
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun SharedEpubChapter.toHtml(searchQuery: String, searchOptions: ReaderSearchOptions): String {
        semanticBlocks.takeIf { it.isNotEmpty() }?.let { blocks ->
            return blocks.joinToString("") { it.toHtml(searchQuery, searchOptions) }
        }
        htmlContent.takeIf { it.isNotBlank() }?.let { return it }
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
                    val nextText =
                        asSequence().drop(index + 1).firstNotNullOfOrNull { it.firstTextBlock() }
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
            is SemanticTable -> rows.asSequence().flatMap { it.asSequence() }
                .flatMap { it.content.asSequence() }.firstNotNullOfOrNull { it.firstTextBlock() }

            is SemanticFlexContainer -> children
                .firstNotNullOfOrNull { it.firstTextBlock() }

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
                .firstNotNullOfOrNull { it.lastTextBlock() }

            is SemanticFlexContainer -> children.asReversed()
                .firstNotNullOfOrNull { it.lastTextBlock() }

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
            is SemanticImage -> "<figure${imageAnchorAttributes()}${styleAttribute()}><img src=\"${path.escapeHtml()}\" alt=\"${altText.orEmpty().escapeHtml()}\" loading=\"lazy\" decoding=\"async\"${imageSizeAttribute()}></figure>"
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
            append(" data-reader-block-index=\"$blockIndex\"")
            elementId?.takeIf { it.isNotBlank() }?.let {
                append(" id=\"${it.escapeHtml()}\" data-reader-element-id=\"${it.escapeHtml()}\"")
            }
            cfi?.takeIf { it.isNotBlank() }?.let {
                append(" data-reader-cfi=\"${it.escapeHtml()}\"")
            }
        }
    }

    private fun SemanticImage.imageAnchorAttributes(): String {
        return buildString {
            append(" data-reader-block-index=\"$blockIndex\"")
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

    private fun readerLinkCssColors(backgroundArgb: Long, textArgb: Long, darkMode: Boolean): ReaderLinkCssColors {
        val backgroundLuminance = backgroundArgb.relativeLuminance()
        val textLuminance = textArgb.relativeLuminance()
        val candidates = if (darkMode || backgroundLuminance < 0.45f) {
            listOf(0xFF7DD3FCL, 0xFF5EEAD4L, 0xFFA5B4FCL, 0xFFFDE68AL, 0xFFFFFFFFL)
        } else {
            listOf(0xFF005FCCL, 0xFF006D75L, 0xFF7A1E52L, 0xFF4A148CL, 0xFF111827L)
        }
        val linkColor = candidates.firstOrNull {
            it.contrastRatio(backgroundArgb) >= 4.5f && abs(it.relativeLuminance() - textLuminance) >= 0.08f
        } ?: candidates.maxByOrNull { it.contrastRatio(backgroundArgb) } ?: if (darkMode) 0xFF7DD3FCL else 0xFF005FCCL
        val alpha = if (backgroundLuminance < 0.45f) 0.24f else 0.16f
        return ReaderLinkCssColors(
            color = linkColor.toCssColor(),
            decoration = linkColor.toCssColor(),
            background = linkColor.toCssRgba(alpha)
        )
    }

    private fun Long.toCssRgba(alpha: Float): String {
        val value = this and 0xFFFFFFFFL
        val red = (value shr 16) and 0xFF
        val green = (value shr 8) and 0xFF
        val blue = value and 0xFF
        return "rgba($red, $green, $blue, ${alpha.coerceIn(0f, 1f)})"
    }

    private fun Long.contrastRatio(other: Long): Float {
        val first = relativeLuminance()
        val second = other.relativeLuminance()
        val lighter = maxOf(first, second)
        val darker = minOf(first, second)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun Long.relativeLuminance(): Float {
        val value = this and 0xFFFFFFFFL
        fun channel(shift: Int): Float {
            val normalized = (((value shr shift) and 0xFF).toFloat() / 255f)
            return if (normalized <= 0.03928f) {
                normalized / 12.92f
            } else {
                ((normalized + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()
            }
        }
        return 0.2126f * channel(16) + 0.7152f * channel(8) + 0.0722f * channel(0)
    }

    private data class ReaderLinkCssColors(
        val color: String,
        val decoration: String,
        val background: String
    )

    private data class ReaderDocumentAppearanceCss(
        val background: String,
        val foreground: String,
        val linkColors: ReaderLinkCssColors,
        val highlight: String,
        val colorScheme: String,
        val textureOverlayCss: String
    )

    private fun ReaderSettings.toDocumentAppearanceCss(textureDataUri: String?): ReaderDocumentAppearanceCss {
        val bgArgb = backgroundColorArgb ?: if (darkMode) 0xFF171A17L else 0xFFFFFCF5L
        val fgArgb = textColorArgb ?: if (darkMode) 0xFFE7E3D8L else 0xFF24231FL
        return ReaderDocumentAppearanceCss(
            background = bgArgb.toCssColor(),
            foreground = fgArgb.toCssColor(),
            linkColors = readerLinkCssColors(bgArgb, fgArgb, darkMode),
            highlight = if (darkMode) "#675A00" else "#FFE36E",
            colorScheme = if (darkMode) "dark" else "light",
            textureOverlayCss = textureId
                ?.takeIf { textureAlpha > 0.01f }
                ?.toTextureOverlayCss(textureAlpha, darkMode, textureDataUri)
                .orEmpty()
        )
    }

    private fun ReaderSettings.readerTextAlignCss(): String {
        return when (textAlign) {
            SharedReaderTextAlign.START -> "left"
            SharedReaderTextAlign.RIGHT -> "right"
            SharedReaderTextAlign.JUSTIFY -> "justify"
            SharedReaderTextAlign.CENTER -> "center"
        }
    }

    private fun ReaderSettings.readerCustomFontUrl(): String? {
        return customFontPath?.takeIf { it.isNotBlank() }?.toCssFontUrl()
    }

    private fun ReaderSettings.readerCustomFontFaceCss(): String {
        val customFontUrl = readerCustomFontUrl() ?: return ""
        return "@font-face { font-family: 'ReaderCustomFont'; src: url('$customFontUrl'); font-display: swap; }"
    }

    private fun ReaderSettings.readerFontFamilyCss(): String {
        return if (readerCustomFontUrl() != null) {
            "'ReaderCustomFont', Georgia, 'Times New Roman', serif"
        } else {
            when (fontFamily) {
                "Serif" -> "Georgia, 'Times New Roman', serif"
                "Sans" -> "Inter, Segoe UI, Arial, sans-serif"
                "Mono" -> "'Roboto Mono', Consolas, monospace"
                else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
            }
        }
    }

    private fun ReaderSettings.readerVerticalMarginY(): Int {
        return (resolvedVerticalMargin / 3f).roundToInt().coerceIn(0, 56)
    }

    private fun ReaderSettings.readerImageScaleCss(): String {
        return "${(imageScale * 100f).roundToInt().coerceIn(50, 200)}%"
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

    private fun String.toJsStringLiteral(): String {
        return buildString {
            append('"')
            this@toJsStringLiteral.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> {
                        if (char.code < 0x20) {
                            append("\\u")
                            append(char.code.toString(16).padStart(4, '0'))
                        } else {
                            append(char)
                        }
                    }
                }
            }
            append('"')
        }
    }

    private fun String.applyUserHighlights(
        highlights: List<UserHighlight>,
        contentStartOffset: Int,
        contentEndOffset: Int
    ): String {
        val rangedHighlights = highlights
            .mapNotNull { it.toRenderHighlight(this, contentStartOffset, contentEndOffset) }
            .distinctBy { "${it.absoluteStart}:${it.absoluteEnd}:${it.id}" }
            .sortedWith(compareByDescending<RenderedHighlight> { it.relativeStart }.thenByDescending { it.relativeEnd })
        val rangedHighlightIds = rangedHighlights.map { it.id }.toSet()

        val rangedHtml = rangedHighlights.fold(this) { html, highlight ->
            val htmlRange = html.htmlRangeForHighlight(highlight) ?: return@fold html
            val startIndex = htmlRange.first
            val endIndex = htmlRange.last
            if (startIndex >= endIndex || endIndex > html.length) return@fold html
            val markedText = html.substring(startIndex, endIndex)
            if (markedText.visibleHtmlText().isBlank()) return@fold html
            val markerStart = """<span class="reader-user-highlight ${highlight.color.cssClass}" data-reader-highlight-id="${highlight.id.escapeHtml()}" data-cfi="${highlight.cfi.escapeHtml()}" data-reader-start-offset="${highlight.absoluteStart}" data-reader-end-offset="${highlight.absoluteEnd}">"""
            html.replaceRange(startIndex, endIndex, markedText.wrapVisibleHtmlText(markerStart, "</span>"))
        }

        return highlights
            .filterNot { it.id in rangedHighlightIds }
            .filterNot { it.locator.withFallbacks(chapterIndex = it.chapterIndex, cfi = it.cfi, textQuote = it.text).hasTextRange }
            .fold(rangedHtml) { html, highlight ->
                val text = highlight.text.trim().takeIf { it.isNotBlank() } ?: return@fold html
                val escapedText = text.escapeHtml()
                val markedText = """<span class="reader-user-highlight ${highlight.color.cssClass}" data-reader-highlight-id="${highlight.id.escapeHtml()}" data-cfi="${highlight.cfi.escapeHtml()}">$escapedText</span>"""
                html.replaceFirst(escapedText, markedText)
            }
    }

    private fun String.wrapVisibleHtmlText(markerStart: String, markerEnd: String): String {
        val output = StringBuilder(length + markerStart.length + markerEnd.length)
        var index = 0
        var markerOpen = false

        fun openMarker() {
            if (!markerOpen) {
                output.append(markerStart)
                markerOpen = true
            }
        }

        fun closeMarker() {
            if (markerOpen) {
                output.append(markerEnd)
                markerOpen = false
            }
        }

        while (index < length) {
            when (this[index]) {
                '<' -> {
                    closeMarker()
                    val tagEnd = indexOf('>', startIndex = index + 1)
                    if (tagEnd < 0) {
                        openMarker()
                        output.append(this[index])
                        index++
                    } else {
                        output.append(substring(index, tagEnd + 1))
                        index = tagEnd + 1
                    }
                }

                '&' -> {
                    openMarker()
                    val entityEnd = indexOf(';', startIndex = index + 1)
                    if (entityEnd > index) {
                        output.append(substring(index, entityEnd + 1))
                        index = entityEnd + 1
                    } else {
                        output.append(this[index])
                        index++
                    }
                }

                else -> {
                    val nextTag = indexOf('<', startIndex = index).takeIf { it >= 0 } ?: length
                    val nextEntity = indexOf('&', startIndex = index).takeIf { it >= 0 } ?: length
                    val nextBoundary = minOf(nextTag, nextEntity)
                    val textRun = substring(index, nextBoundary)
                    if (textRun.isBlank()) {
                        output.append(textRun)
                    } else {
                        openMarker()
                        output.append(textRun)
                    }
                    index = nextBoundary
                }
            }
        }
        closeMarker()
        return output.toString()
    }

    private fun String.visibleHtmlText(): String {
        val output = StringBuilder(length)
        var index = 0
        while (index < length) {
            when (this[index]) {
                '<' -> {
                    val tagEnd = indexOf('>', startIndex = index + 1)
                    index = if (tagEnd < 0) index + 1 else tagEnd + 1
                }

                '&' -> {
                    output.append('x')
                    val entityEnd = indexOf(';', startIndex = index + 1)
                    index = if (entityEnd > index) entityEnd + 1 else index + 1
                }

                else -> {
                    output.append(this[index])
                    index++
                }
            }
        }
        return output.toString()
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

    private fun String.findTextBlockRangeByCfi(cfiPath: String): HtmlTextBlockRange? {
        return findTextBlockRangeByAttribute("data-reader-cfi", cfiPath)
    }

    private fun String.findTextBlockRangeByBlockIndex(blockIndex: Int): HtmlTextBlockRange? {
        return findTextBlockRangeByAttribute("data-reader-block-index", blockIndex.toString())
    }

    private fun String.findTextBlockRangeByAttribute(attributeName: String, attributeValue: String): HtmlTextBlockRange? {
        return textBlockStartPattern.findAll(this).mapNotNull { match ->
            val openingTag = substring(match.range.first, match.range.last + 1)
            if (openingTag.htmlAttributeValue(attributeName) != attributeValue) return@mapNotNull null
            val tagName = match.groupValues[1]
            val blockStart = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
            val blockEnd = match.groupValues[3].toIntOrNull() ?: return@mapNotNull null
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

    private fun String.htmlAttributeValue(attributeName: String): String? {
        return Regex("""\b${Regex.escape(attributeName)}="([^"]*)"""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
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

    private fun UserHighlight.toRenderHighlight(
        html: String,
        contentStartOffset: Int,
        contentEndOffset: Int
    ): RenderedHighlight? {
        val normalizedLocator = locator.withFallbacks(chapterIndex = chapterIndex, cfi = cfi, textQuote = text)
        val sourceCfi = normalizedLocator.cfi ?: cfi
        html.absoluteRangeForSourceCfi(sourceCfi, normalizedLocator.textQuote ?: text)
            ?.toRenderedHighlight(
                source = this,
                cfi = sourceCfi,
                contentStartOffset = contentStartOffset,
                contentEndOffset = contentEndOffset
            )
            ?.let { return it }
        html.absoluteRangeForBlockLocator(normalizedLocator, normalizedLocator.textQuote ?: text)
            ?.toRenderedHighlight(
                source = this,
                cfi = sourceCfi,
                contentStartOffset = contentStartOffset,
                contentEndOffset = contentEndOffset
            )
            ?.let { return it }
        val start = normalizedLocator.startOffset ?: return null
        val end = normalizedLocator.endOffset ?: start
        if (end < start) return null
        return HtmlAbsoluteTextRange(start, end).toRenderedHighlight(
            source = this,
            cfi = sourceCfi,
            contentStartOffset = contentStartOffset,
            contentEndOffset = contentEndOffset
        )
    }

    private fun HtmlAbsoluteTextRange.toRenderedHighlight(
        source: UserHighlight,
        cfi: String,
        contentStartOffset: Int,
        contentEndOffset: Int
    ): RenderedHighlight? {
        if (end < start) return null
        val boundedStart = start.coerceAtLeast(contentStartOffset)
        val boundedEnd = end.coerceAtMost(contentEndOffset)
        if (boundedEnd <= boundedStart) return null
        return RenderedHighlight(
            id = source.id,
            cfi = cfi,
            color = source.color,
            absoluteStart = boundedStart,
            absoluteEnd = boundedEnd,
            relativeStart = boundedStart - contentStartOffset,
            relativeEnd = boundedEnd - contentStartOffset
        )
    }

    private fun String.absoluteRangeForSourceCfi(cfi: String?, quote: String): HtmlAbsoluteTextRange? {
        val points = cfi
            ?.takeIf { it.startsWith("/") || it.contains("|/") }
            ?.split('|')
            ?.mapNotNull { it.toHtmlSourceCfiPointOrNull() }
            ?: return null
        val startPoint = points.firstOrNull() ?: return null
        val endPoint = points.lastOrNull() ?: startPoint
        val startBlock = findTextBlockRangeByCfi(startPoint.path) ?: return null
        val endBlock = findTextBlockRangeByCfi(endPoint.path) ?: startBlock
        val start = startBlock.htmlCfiOffsetToAbsolute(startPoint.offset)
        var end = endBlock.htmlCfiOffsetToAbsolute(endPoint.offset)
        if (end == start && quote.isNotBlank()) {
            end = start + quote.length
        }
        return if (end > start) HtmlAbsoluteTextRange(start, end) else null
    }

    private fun String.absoluteRangeForBlockLocator(locator: ReaderLocator, quote: String): HtmlAbsoluteTextRange? {
        val blockIndex = locator.blockIndex ?: return null
        val block = findTextBlockRangeByBlockIndex(blockIndex) ?: return null
        val blockLength = block.endOffset - block.startOffset
        val startOffset = locator.startOffset ?: locator.charOffset ?: return null
        val rawEnd = locator.endOffset
            ?: quote.takeIf { it.isNotBlank() }?.let { startOffset + it.length }
            ?: return null
        val start = block.htmlScopedOffsetToAbsoluteOrNull(startOffset, blockLength) ?: return null
        val end = block.htmlScopedOffsetToAbsoluteOrNull(rawEnd, blockLength) ?: return null
        return if (end > start) HtmlAbsoluteTextRange(start, end) else null
    }

    private fun HtmlTextBlockRange.htmlCfiOffsetToAbsolute(offset: Int): Int {
        val blockLength = endOffset - startOffset
        return when {
            offset in 0..blockLength -> startOffset + offset
            offset in startOffset..endOffset -> offset
            else -> startOffset + offset.coerceIn(0, blockLength)
        }
    }

    private fun HtmlTextBlockRange.htmlScopedOffsetToAbsoluteOrNull(offset: Int, blockLength: Int): Int? {
        return when {
            offset in 0..blockLength -> startOffset + offset
            offset in startOffset..endOffset -> offset
            else -> null
        }
    }

    private fun UserHighlight.belongsToPage(page: ReaderPage): Boolean {
        val normalizedLocator = locator.withFallbacks(chapterIndex = chapterIndex, cfi = cfi, textQuote = text)
        val locatorChapterIndex = normalizedLocator.chapterIndex ?: chapterIndex
        if (locatorChapterIndex != page.chapterIndex) return false
        return page.containsHighlightLocator(normalizedLocator, cfi)
    }

    private fun ReaderPage.containsHighlightLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
        if (containsBlockLocator(locator)) return true
        if (containsSourceCfiLocator(locator, fallbackCfi)) return true
        if (locator.hasTextRange) {
            if (locator.hasHtmlStructuralScope(fallbackCfi)) return false
            val start = locator.startOffset ?: return false
            val end = locator.endOffset ?: start
            return if (start == end) {
                start in startOffset..endOffset
            } else {
                start < endOffset && end > startOffset
            }
        }
        locator.pageIndex?.let { return it == pageIndex }
        val prefix = "desktop:${chapterIndex}:"
        val desktopPageIndex = fallbackCfi
            .takeIf { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            ?.substringBefore(':')
            ?.toIntOrNull()
        return desktopPageIndex != null && desktopPageIndex >= 0 && desktopPageIndex == pageIndex
    }

    private fun ReaderLocator.hasHtmlStructuralScope(fallbackCfi: String): Boolean {
        val sourceCfi = cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi
        return blockIndex != null || sourceCfi.startsWith("/")
    }

    private fun ReaderPage.containsBlockLocator(locator: ReaderLocator): Boolean {
        val blockIndex = locator.blockIndex ?: return false
        val blocks = semanticBlocks.flattenHtmlSemanticBlocks()
        if (blocks.isEmpty()) return false
        val matchingBlocks = blocks.filter { it.blockIndex == blockIndex }
        if (matchingBlocks.isEmpty()) return false
        val charOffset = locator.charOffset ?: return true
        val offsetFallsOnPage = if (startOffset == endOffset) {
            charOffset == startOffset
        } else {
            charOffset >= startOffset && charOffset < endOffset
        }
        if (!offsetFallsOnPage) return false
        return matchingBlocks.filterIsInstance<SemanticTextBlock>().any { block ->
            val start = block.startCharOffsetInSource
            val end = start + block.text.length
            charOffset in start until end || (block.text.isEmpty() && charOffset == start)
        }
    }

    private fun ReaderPage.containsSourceCfiLocator(locator: ReaderLocator, fallbackCfi: String): Boolean {
        val cfi = (locator.cfi?.takeIf { it.isNotBlank() } ?: fallbackCfi)
            .takeIf { it.startsWith("/") || it.contains("|/") }
            ?: return false
        val blocks = semanticBlocks.flattenHtmlSemanticBlocks().filterIsInstance<SemanticTextBlock>()
        if (blocks.isEmpty()) return false
        val parts = cfi.split('|').mapNotNull { it.toHtmlSourceCfiPointOrNull() }
        val startPoint = parts.firstOrNull() ?: return false
        val endPoint = parts.lastOrNull() ?: startPoint
        val quoteLength = locator.textQuote?.length ?: 0
        return blocks.any { block ->
            val blockPath = block.cfi?.substringBefore(':')?.takeIf { it.startsWith("/") } ?: return@any false
            val startMatches = htmlSourceCfiPathsEquivalent(startPoint.path, blockPath)
            val endMatches = htmlSourceCfiPathsEquivalent(endPoint.path, blockPath)
            val isIntermediate = parts.size > 1 &&
                !startMatches &&
                !endMatches &&
                htmlSourceCfiPathStrictlyBetween(blockPath, startPoint.path, endPoint.path)
            if (!startMatches && !endMatches && !isIntermediate) return@any false
            val blockStart = block.startCharOffsetInSource
            val blockEnd = blockStart + block.text.length
            val rangeStart = when {
                startMatches -> htmlSourceCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length)
                isIntermediate || endMatches -> blockStart
                else -> blockStart
            }
            val rangeEnd = when {
                endMatches && parts.size > 1 -> htmlSourceCfiOffsetToAbsolute(endPoint.offset, blockStart, block.text.length)
                startMatches && parts.size == 1 && quoteLength > 0 ->
                    htmlSourceCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length) + quoteLength
                startMatches && parts.size == 1 -> htmlSourceCfiOffsetToAbsolute(startPoint.offset, blockStart, block.text.length)
                isIntermediate -> blockEnd
                else -> blockEnd
            }
            if (rangeStart == rangeEnd) {
                rangeStart in startOffset..endOffset
            } else {
                minOf(rangeStart, rangeEnd) < endOffset && maxOf(rangeStart, rangeEnd) > startOffset
            }
        }
    }

    private fun htmlSourceCfiOffsetToAbsolute(offset: Int, blockStart: Int, textLength: Int): Int {
        val blockEnd = blockStart + textLength
        return when {
            offset in 0..textLength -> blockStart + offset
            offset in blockStart..blockEnd -> offset
            else -> blockStart + offset.coerceIn(0, textLength)
        }
    }

    private data class HtmlSourceCfiPoint(
        val path: String,
        val offset: Int
    )

    private fun String.toHtmlSourceCfiPointOrNull(): HtmlSourceCfiPoint? {
        val value = trim()
        if (!value.startsWith("/")) return null
        val separator = value.lastIndexOf(':')
        return if (separator > 0 && separator < value.lastIndex) {
            HtmlSourceCfiPoint(value.substring(0, separator), value.substring(separator + 1).toIntOrNull() ?: 0)
        } else {
            HtmlSourceCfiPoint(value, 0)
        }
    }

    private fun htmlSourceCfiPathsEquivalent(first: String, second: String): Boolean {
        if (first == second || first.startsWith("$second/") || second.startsWith("$first/")) return true
        val firstParts = first.split('/').filter { it.isNotEmpty() }
        val secondParts = second.split('/').filter { it.isNotEmpty() }
        if (firstParts == secondParts) return true
        return firstParts.size == secondParts.size &&
            firstParts.isNotEmpty() &&
            firstParts.drop(1) == secondParts.drop(1)
    }

    private fun htmlSourceCfiPathStrictlyBetween(candidate: String, start: String, end: String): Boolean {
        val candidateParts = candidate.htmlSourceCfiNumericPathParts() ?: return false
        val startParts = start.htmlSourceCfiNumericPathParts() ?: return false
        val endParts = end.htmlSourceCfiNumericPathParts() ?: return false
        return htmlSourceCfiComparePathParts(candidateParts, startParts) > 0 &&
            htmlSourceCfiComparePathParts(candidateParts, endParts) < 0
    }

    private fun String.htmlSourceCfiNumericPathParts(): List<Int>? {
        val parts = split('/').filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    private fun htmlSourceCfiComparePathParts(first: List<Int>, second: List<Int>): Int {
        val length = minOf(first.size, second.size)
        for (index in 0 until length) {
            val comparison = first[index].compareTo(second[index])
            if (comparison != 0) return comparison
        }
        return first.size.compareTo(second.size)
    }

    private fun List<SemanticBlock>.flattenHtmlSemanticBlocks(): List<SemanticBlock> {
        return flatMap { it.flattenHtmlSemanticBlock() }
    }

    private fun SemanticBlock.flattenHtmlSemanticBlock(): List<SemanticBlock> {
        return when (this) {
            is SemanticList -> listOf(this) + items
            is SemanticTable -> listOf(this) + rows.flatMap { row -> row.flatMap { cell -> cell.content.flattenHtmlSemanticBlocks() } }
            is SemanticFlexContainer -> listOf(this) + children.flattenHtmlSemanticBlocks()
            is SemanticWrappingBlock -> listOf(this, floatedImage) + paragraphsToWrap
            is SemanticImage,
            is SemanticMath,
            is SemanticSpacer,
            is SemanticTextBlock -> listOf(this)
        }
    }

    private val UserHighlight.locatedChapterIndex: Int
        get() = locator.chapterIndex ?: chapterIndex

    private fun ReaderLocator.toNavigationAttributes(): String {
        val attributes = buildList {
            chapterIndex?.let { add("data-reader-active-chapter-index=\"$it\"") }
            pageIndex?.let { add("data-reader-active-page-index=\"$it\"") }
            startOffset?.let { add("data-reader-active-start-offset=\"$it\"") }
            endOffset?.let { add("data-reader-active-end-offset=\"$it\"") }
            blockIndex?.let { add("data-reader-active-block-index=\"$it\"") }
            charOffset?.let { add("data-reader-active-char-offset=\"$it\"") }
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

    private fun readerSelectionActionButton(action: String, label: String, pathData: String): String {
        val safeLabel = label.escapeHtml()
        return """<button type="button" class="reader-selection-action" data-action="${action.escapeHtml()}" aria-label="$safeLabel"><span class="reader-selection-icon" aria-hidden="true">${readerSelectionSvg(pathData)}</span><span>$safeLabel</span></button>"""
    }

    private fun ReaderHighlightPalette.toSelectionColorButtons(): String {
        return sanitized().colors.joinToString("\n") { color ->
            """<button type="button" class="reader-selection-color" data-action="highlight" data-color-id="${color.id}" title="Highlight ${color.id.escapeHtml()}" style="--selection-color:${color.color.toCssHex()}"><span></span></button>"""
        }
    }

    private fun ReaderHighlightPalette.toSelectionPaletteButtons(): String {
        return listOf(
            toSelectionColorButtons(),
            """<button type="button" class="reader-selection-spectrum" data-action="palette" title="Customize highlight palette" aria-label="Customize highlight palette"><span></span></button>"""
        ).joinToString("\n")
    }

    private fun readerSelectionSvg(pathData: String): String {
        return """<svg viewBox="0 0 960 960" focusable="false" aria-hidden="true"><path d="$pathData"></path></svg>"""
    }

    private data class TextSegment(
        val text: String,
        val startOffset: Int
    )

    private data class RenderedHighlight(
        val id: String,
        val cfi: String,
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

    private data class HtmlAbsoluteTextRange(
        val start: Int,
        val end: Int
    )

    private val textBlockStartPattern = Regex(
        """<([A-Za-z][A-Za-z0-9]*)\b[^>]*\bdata-reader-text-start="(\d+)"[^>]*\bdata-reader-text-end="(\d+)"[^>]*>"""
    )

    private const val ReaderSelectionIconCopyPath =
        "M360,720Q327,720 303.5,696.5Q280,673 280,640L280,160Q280,127 303.5,103.5Q327,80 360,80L720,80Q753,80 776.5,103.5Q800,127 800,160L800,640Q800,673 776.5,696.5Q753,720 720,720L360,720ZM360,640L720,640L720,160L360,160L360,640ZM200,880Q167,880 143.5,856.5Q120,833 120,800L120,240L200,240L200,800L640,800L640,880L200,880Z"
    private const val ReaderSelectionIconDefinePath =
        "M480,800Q432,762 376,741Q320,720 260,720Q218,720 177.5,731Q137,742 100,762Q79,773 59.5,761Q40,749 40,726L40,244Q40,233 45.5,223Q51,213 62,208Q108,184 158,172Q208,160 260,160Q318,160 373.5,175Q429,190 480,220Q531,190 586.5,175Q642,160 700,160Q752,160 802,172Q852,184 898,208Q909,213 914.5,223Q920,233 920,244L920,726Q920,749 900.5,761Q881,773 860,762Q823,742 782.5,731Q742,720 700,720Q640,720 584,741Q528,762 480,800ZM520,682Q564,661 608.5,650.5Q653,640 700,640Q736,640 770.5,646Q805,652 840,664L840,268Q807,254 771.5,247Q736,240 700,240Q653,240 607,252Q561,264 520,288L520,682ZM440,682L440,288Q399,264 353,252Q307,240 260,240Q224,240 188.5,247Q153,254 120,268L120,664Q155,652 189.5,646Q224,640 260,640Q307,640 351.5,650.5Q396,661 440,682Z"
    private const val ReaderSelectionIconSpeakPath =
        "M560,828L560,746Q653,719 706.5,642Q760,565 760,466Q760,367 706.5,290Q653,213 560,186L560,104Q687,133 763.5,234Q840,335 840,466Q840,597 763.5,698Q687,799 560,828ZM120,600L120,360L280,360L480,160L480,800L280,600L120,600ZM560,640L560,292Q612,317 646,364.5Q680,412 680,466Q680,520 646,567.5Q612,615 560,640Z"
    private const val ReaderSelectionIconSearchPath =
        "M784,840L532,588Q502,612 463,626Q424,640 380,640Q271,640 195.5,564.5Q120,489 120,380Q120,271 195.5,195.5Q271,120 380,120Q489,120 564.5,195.5Q640,271 640,380Q640,424 626,463Q612,502 588,532L840,784L784,840ZM380,560Q455,560 507.5,507.5Q560,455 560,380Q560,305 507.5,252.5Q455,200 380,200Q305,200 252.5,252.5Q200,305 200,380Q200,455 252.5,507.5Q305,560 380,560Z"
    private const val ReaderSelectionIconClearPath =
        "M256,760L200,704L424,480L200,256L256,200L480,424L704,200L760,256L536,480L760,704L704,760L480,536L256,760Z"
    private const val ReaderSelectionIconTeardropPath =
        "M480,860Q347,860 253.5,768Q160,676 160,544Q160,481 184.5,423.5Q209,366 254,322L480,100L706,322Q751,366 775.5,423.5Q800,481 800,544Q800,676 706.5,768Q613,860 480,860Z"

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

    private fun androidx.compose.ui.graphics.Color.toCssRgba(alpha: Float): String {
        fun channel(value: Float): Int = (value * 255f).roundToInt().coerceIn(0, 255)
        val safeAlpha = alpha.coerceIn(0f, 1f)
        return "rgba(${channel(red)}, ${channel(green)}, ${channel(blue)}, ${safeAlpha.formatCssAlpha()})"
    }

    private fun Float.formatCssAlpha(): String {
        val scaled = (this * 1000f).roundToInt()
        val whole = scaled / 1000
        val fraction = (scaled % 1000).toString().padStart(3, '0').trimEnd('0')
        return if (fraction.isEmpty()) whole.toString() else "$whole.$fraction"
    }

    private fun logReaderHtml(message: String) {
        logSharedReaderDiagnostic("ReaderHtmlRender") { message }
    }
}
