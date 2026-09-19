package com.aryan.reader.paginatedreader

import androidx.compose.ui.unit.Dp

/**
 * Display marker for figures whose image is missing from the archive (Project Gutenberg
 * ebookmaker emits `<span id="img_...">caption</span>` in place of `<img>`). The block
 * stays an ordinary [SemanticParagraph] so serialization, pagination, search and CFI
 * machinery are untouched; renderers key off this marker to draw a placeholder.
 */
const val READER_MISSING_FIGURE_DISPLAY = "reader-missing-figure"

fun CssStyle.withReaderMissingFigure(): CssStyle =
    copy(
        blockStyle = blockStyle.copy(
            display = READER_MISSING_FIGURE_DISPLAY,
            // Placeholders lay out full-width; a publication fixed width (Gutenberg
            // figcenter 600px frames) would overflow narrow pages.
            width = Dp.Unspecified,
            maxWidth = Dp.Unspecified
        )
    )

fun CssStyle.isReaderMissingFigure(): Boolean =
    blockStyle.display == READER_MISSING_FIGURE_DISPLAY

fun SemanticBlock.isReaderMissingFigure(): Boolean = style.isReaderMissingFigure()

/** Placeholder text for a missing figure; keeps the caption when the book provides one. */
fun readerMissingFigureText(caption: String?): String {
    val trimmed = caption?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
    return if (trimmed.isBlank()) "[Illustration]" else "[Illustration] $trimmed"
}

/**
 * Ebookmaker image-replacement marker: `<span title="" id="img_<file>">optional caption</span>`.
 * Matches case-insensitively on the id prefix; the empty-title attribute is not required.
 */
fun String.isEbookmakerImageMarkerId(): Boolean =
    startsWith("img_", ignoreCase = true) && length > 4
