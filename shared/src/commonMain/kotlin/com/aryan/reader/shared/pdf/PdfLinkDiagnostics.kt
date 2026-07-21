package com.aryan.reader.shared.pdf

const val PDF_LINK_LOG_TAG = "PdfLinkDiagnostic"

internal fun pdfLinkLog(message: () -> String) {
    println("[$PDF_LINK_LOG_TAG] ${message()}")
}
