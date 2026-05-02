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

object ReaderHtmlDocumentBuilder {
    fun verticalDocument(book: SharedEpubBook, settings: ReaderSettings, searchQuery: String = ""): String {
        val body = book.chapters.mapIndexed { index, chapter ->
            """
            <section class="chapter" id="chapter-$index">
              <h1 class="chapter-title">${chapter.title.escapeHtml()}</h1>
              ${chapter.toHtml(searchQuery)}
            </section>
            """.trimIndent()
        }.joinToString("\n")
        return document(
            title = book.title,
            settings = settings,
            bookCss = book.css.values.joinToString("\n"),
            body = body,
            searchQuery = searchQuery
        )
    }

    fun pageDocument(book: SharedEpubBook, page: ReaderPage?, settings: ReaderSettings, searchQuery: String = ""): String {
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
                ?.joinToString("\n") { it.toHtml(searchQuery) }
                ?: page.text.textToParagraphHtml(searchQuery)
            """
            <section class="page">
              <h1 class="chapter-title">${page.chapterTitle.escapeHtml()}</h1>
              $blocks
            </section>
            """.trimIndent()
        }
        return document(
            title = book.title,
            settings = settings,
            bookCss = book.css.values.joinToString("\n"),
            body = body,
            searchQuery = searchQuery
        )
    }

    private fun document(
        title: String,
        settings: ReaderSettings,
        bookCss: String,
        body: String,
        searchQuery: String
    ): String {
        val bg = if (settings.darkMode) "#171A17" else "#FFFCF5"
        val fg = if (settings.darkMode) "#E7E3D8" else "#24231F"
        val highlight = if (settings.darkMode) "#675A00" else "#FFE36E"
        val align = when (settings.textAlign) {
            SharedReaderTextAlign.START -> "left"
            SharedReaderTextAlign.JUSTIFY -> "justify"
            SharedReaderTextAlign.CENTER -> "center"
        }
        val family = when (settings.fontFamily) {
            "Serif" -> "Georgia, 'Times New Roman', serif"
            "Sans" -> "Inter, Segoe UI, Arial, sans-serif"
            "Mono" -> "'Roboto Mono', Consolas, monospace"
            else -> "system-ui, -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif"
        }
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>${title.escapeHtml()}</title>
              <style>
                $bookCss
                :root {
                  color-scheme: ${if (settings.darkMode) "dark" else "light"};
                  --reader-bg: $bg;
                  --reader-fg: $fg;
                  --reader-highlight: $highlight;
                  --reader-font-size: ${settings.fontSize}px;
                  --reader-line-height: ${settings.lineSpacing};
                  --reader-page-width: ${settings.pageWidth}px;
                  --reader-margin: ${settings.margin}px;
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
                  padding: var(--reader-margin);
                  overflow-wrap: anywhere;
                }
                .chapter, .page {
                  max-width: var(--reader-page-width);
                  margin: 0 auto 48px;
                  text-align: var(--reader-align);
                }
                .chapter-title {
                  text-align: left;
                  font-size: 1.55em;
                  line-height: 1.25;
                  margin: 0 0 1.1em;
                }
                p, blockquote, pre, ul, ol, table, figure {
                  margin-top: 0;
                  margin-bottom: 1em;
                }
                img, svg, video {
                  max-width: 100%;
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
                a { color: inherit; text-decoration-thickness: 0.08em; }
              </style>
            </head>
            <body data-search="${searchQuery.escapeHtml()}">
              $body
            </body>
            </html>
        """.trimIndent()
    }

    private fun SharedEpubChapter.toHtml(searchQuery: String): String {
        htmlContent.takeIf { it.isNotBlank() }?.let { return it }
        semanticBlocks.takeIf { it.isNotEmpty() }?.let { blocks ->
            return blocks.joinToString("\n") { it.toHtml(searchQuery) }
        }
        return plainText.textToParagraphHtml(searchQuery)
    }

    private fun SemanticBlock.toHtml(searchQuery: String): String {
        return when (this) {
            is SemanticHeader -> "<h${level.coerceIn(1, 6)}>${text.highlightAndEscape(searchQuery)}</h${level.coerceIn(1, 6)}>"
            is SemanticParagraph -> "<p>${text.highlightAndEscape(searchQuery)}</p>"
            is SemanticListItem -> "<li>${text.highlightAndEscape(searchQuery)}</li>"
            is SemanticList -> {
                val tag = if (isOrdered) "ol" else "ul"
                "<$tag>${items.joinToString("") { it.toHtml(searchQuery) }}</$tag>"
            }
            is SemanticImage -> "<figure><img src=\"${path.escapeHtml()}\" alt=\"${altText.orEmpty().escapeHtml()}\"></figure>"
            is SemanticMath -> svgContent ?: "<pre>${altText.orEmpty().highlightAndEscape(searchQuery)}</pre>"
            is SemanticSpacer -> if (isExplicitLineBreak) "<br>" else "<div style=\"height:1em\"></div>"
            is SemanticTable -> rows.joinToString("", "<table><tbody>", "</tbody></table>") { row ->
                row.joinToString("", "<tr>", "</tr>") { cell ->
                    val tag = if (cell.isHeader) "th" else "td"
                    "<$tag colspan=\"${cell.colspan.coerceAtLeast(1)}\">${cell.content.joinToString("") { it.toHtml(searchQuery) }}</$tag>"
                }
            }
            is SemanticFlexContainer -> children.joinToString("", "<div>", "</div>") { it.toHtml(searchQuery) }
            is SemanticWrappingBlock -> floatedImage.toHtml(searchQuery) + paragraphsToWrap.joinToString("") { it.toHtml(searchQuery) }
            is SemanticTextBlock -> "<p>${text.highlightAndEscape(searchQuery)}</p>"
        }
    }

    private fun String.textToParagraphHtml(searchQuery: String): String {
        return split(Regex("\\n\\s*\\n"))
            .filter { it.isNotBlank() }
            .joinToString("\n") { "<p>${it.trim().highlightAndEscape(searchQuery)}</p>" }
            .ifBlank { "<p></p>" }
    }

    private fun String.highlightAndEscape(searchQuery: String): String {
        val escaped = escapeHtml()
        val query = searchQuery.trim()
        if (query.length < 2) return escaped
        return escaped.replace(Regex(Regex.escape(query.escapeHtml()), RegexOption.IGNORE_CASE)) {
            "<span class=\"reader-highlight\">${it.value}</span>"
        }
    }

    private fun String.escapeHtml(): String {
        return replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
