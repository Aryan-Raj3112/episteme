package com.aryan.reader.pdf

import android.net.Uri

/**
 * Android renderer input for one PDF workspace slot.
 *
 * The portable workspace stores only document identity. The renderer owns
 * Android URI and document-session details here.
 */
data class PdfViewerPane(
    val bookId: String,
    val pdfUri: Uri,
    val initialPage: Int? = null,
    val initialBookmarksJson: String? = null,
)
