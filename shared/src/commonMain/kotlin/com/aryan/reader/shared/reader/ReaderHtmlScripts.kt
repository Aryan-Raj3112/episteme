package com.aryan.reader.shared.reader

internal fun readerDocumentScript(pageAnchorJson: String): String = listOf(
    readerHtmlNavigationScript(pageAnchorJson),
    readerHtmlSelectionScript(),
    readerHtmlAnnotationScript()
).joinToString("\n")
