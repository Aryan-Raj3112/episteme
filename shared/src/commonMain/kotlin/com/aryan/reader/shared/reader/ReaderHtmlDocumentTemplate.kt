package com.aryan.reader.shared.reader

import com.aryan.reader.shared.ReaderHighlightPalette

internal fun document(
    title: String,
    settings: ReaderSettings,
    bookCss: String,
    body: String,
    searchQuery: String,
    searchOptions: ReaderSearchOptions,
    highlightPalette: ReaderHighlightPalette,
    highlightActionsEnabled: Boolean,
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
    val highlightButtons = if (highlightActionsEnabled) highlightPalette.toSelectionPaletteButtons() else ""
    val noteButton = if (highlightActionsEnabled) {
        readerSelectionActionButton("note", "Note", ReaderSelectionIconNotePath)
    } else {
        ""
    }
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
        readerSelectionActionButton("dictionary", "Dictionary", ReaderSelectionIconDefinePath) +
            readerSelectionActionButton("translate", "Translate", ReaderSelectionIconTranslatePath) +
            readerSelectionActionButton("web-search", "Search", ReaderSelectionIconSearchPath)
    } else {
        ""
    }
    val navigationAttributes = navigationLocator?.toNavigationAttributes().orEmpty()
    val pageAnchorJson = pageAnchors.toPageAnchorJson()
    val verticalMarginY = settings.readerVerticalMarginY()
    val styles = readerDocumentStyles(
        settings = settings,
        bookCss = bookCss,
        customFontCss = customFontCss,
        appearance = appearance,
        align = align,
        family = family,
        verticalMarginY = verticalMarginY
    ).replace("\n", "\n          ")
    val script = readerDocumentScript(pageAnchorJson).replace("\n", "\n          ")
    return """
        <!doctype html>
        <html class="${if (settings.readingMode == ReaderReadingMode.PAGINATED) "reader-paginated-root" else "reader-vertical-root"}">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <title>${title.escapeHtml()}</title>
          $styles
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
              $noteButton
              ${readerSelectionActionButton("clear", "Clear", ReaderSelectionIconClearPath)}
            </div>
          </div>
          <button type="button" id="reader-selection-start-handle" class="reader-selection-handle reader-selection-handle-start" aria-label="Adjust selection start" hidden>
            ${readerSelectionSvg(ReaderSelectionIconTeardropPath)}
          </button>
          <button type="button" id="reader-selection-end-handle" class="reader-selection-handle reader-selection-handle-end" aria-label="Adjust selection end" hidden>
            ${readerSelectionSvg(ReaderSelectionIconTeardropPath)}
          </button>
          $script
        </body>
        </html>
    """.trimIndent()
}
