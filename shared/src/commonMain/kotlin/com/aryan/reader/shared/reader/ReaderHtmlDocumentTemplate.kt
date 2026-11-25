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
    textureDataUri: String?,
    documentLanguage: String = "en"
): String {
    val appearance = settings.toDocumentAppearanceCss(textureDataUri)
    val align = settings.readerTextAlignCss()
    val webFontCss = settings.readerWebFontCss()
    val family = webFontCss.familyCss
    // The dynamic appearance script owns this exact element: it updates the
    // face and force rule in place, or removes the element when the reader
    // returns to Original. Keeping the same markup in the initial document
    // means the first paint is correct and a later Original choice can
    // actually clear the override (a rule baked into the main <style> could
    // never be removed by the script).
    val fontStyleTag = webFontCss.documentCss
        .takeIf { it.isNotBlank() }
        ?.let { "<style id=\"reader-custom-font-style\">$it</style>" }
        .orEmpty()
    val highlightButtons = if (highlightActionsEnabled) highlightPalette.toSelectionPaletteButtons() else ""
    val highlightStyleButtons = if (highlightActionsEnabled) readerSelectionStyleButtons() else ""
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
    // Temporary: the menu carries a single Define entry (in-app AI, falling
    // back to the browser define search). The separate Dictionary button is
    // gone until the external-apps story is figured out.
    val externalLookupButtons = if (externalLookupEnabled) {
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
        appearance = appearance,
        align = align,
        family = family,
        verticalMarginY = verticalMarginY
    ).replace("\n", "\n          ")
    val script = readerDocumentScript(pageAnchorJson).replace("\n", "\n          ")
    return """
        <!doctype html>
        <html lang="${documentLanguage.escapeHtml()}" class="${if (settings.readingMode == ReaderReadingMode.PAGINATED) "reader-paginated-root" else "reader-vertical-root"}">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
          <title>${title.escapeHtml()}</title>
          $styles
          $fontStyleTag
        </head>
        <body class="${if (settings.readingMode == ReaderReadingMode.PAGINATED) "reader-paginated" else "reader-vertical"}" data-search="${searchQuery.escapeHtml()}"$navigationAttributes>
          $body
          <div id="reader-selection-menu" role="toolbar" aria-label="Selection actions">
            <div class="reader-selection-styles" aria-label="Highlight styles">
              $highlightStyleButtons
            </div>
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
