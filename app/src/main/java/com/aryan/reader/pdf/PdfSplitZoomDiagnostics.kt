package com.aryan.reader.pdf

import timber.log.Timber

internal const val PDF_SPLIT_ZOOM_DIAG_TAG = "PdfSplitZoomDiag"

internal fun pdfSplitZoomDiag(message: String) {
    Timber.tag(PDF_SPLIT_ZOOM_DIAG_TAG).d(message)
}

internal fun Float.diagF(): String = if (isFinite()) "%.3f".format(this) else "NaN"
