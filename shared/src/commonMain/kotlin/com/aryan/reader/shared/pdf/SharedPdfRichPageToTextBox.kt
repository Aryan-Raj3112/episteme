package com.aryan.reader.shared.pdf

import androidx.compose.ui.unit.IntSize

/**
 * Legacy page-rich-text → text-box converter.
 *
 * Currently retired on Android: the flowing page editor is hidden and text
 * boxes are the only text annotation, so documents that already carry page
 * rich text are offered a one-tap conversion. Pure and unit-testable; the
 * only caller today is Android (iOS/desktop behavior unchanged).
 *
 * Granularity is paragraph groups stacked page-wise inside the retired
 * editor rect (10% x-margins, 8% y-margins): plain paragraphs each become
 * their own box, while consecutive same-type list paragraphs stay together
 * so bullet/numbered runs (and their numbering) survive. Blank paragraphs
 * are dropped. Text keeps its inline list markers; alignment + list
 * attributes carry over; style comes from the dominant span by coverage.
 * When the stack no longer fits, the remainder merges into one final
 * scrollable box instead of overlapping.
 *
 * Deliberately lossy edges: one box carries one style (mixed spans collapse
 * to the dominant run) and spans store only `fontPath` (no `fontName`).
 */
data class SharedPdfTextBoxSpec(
    val pageIndex: Int,
    val text: String,
    val paragraphs: List<SharedPdfRichParagraph> = emptyList(),
    val colorArgb: Int = 0xFF000000.toInt(),
    val backgroundArgb: Int = 0x00000000,
    val fontSizeNorm: Float = SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(16f),
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrikeThrough: Boolean = false,
    val fontPath: String? = null,
    /** Page-relative bounds (0..1), already inside the editor rect. */
    val bounds: PdfPageBounds = PdfPageBounds(0.1f, 0.08f, 0.9f, 0.2f),
)

private const val CONVERTER_EDITOR_LEFT = 0.1f
private const val CONVERTER_EDITOR_TOP = 0.08f
private const val CONVERTER_EDITOR_RIGHT = 0.9f
private const val CONVERTER_EDITOR_BOTTOM = 0.92f
private const val CONVERTER_BOX_GAP = 0.015f
private const val CONVERTER_MIN_BOX_HEIGHT = 0.02f

private data class RichPageGroup(
    val text: String,
    val paragraphs: List<SharedPdfRichParagraph>,
    val rangeStart: Int,
    val rangeEnd: Int,
)

private fun dominantRichSpan(
    spans: List<SharedPdfRichSpan>,
    from: Int,
    to: Int,
): SharedPdfRichSpan? {
    return spans
        .mapNotNull { span ->
            val overlap = minOf(span.end, to) - maxOf(span.start, from)
            if (overlap > 0) span to overlap else null
        }
        .maxByOrNull { it.second }
        ?.first
}

private fun stackedBoxHeightNorm(
    text: String,
    fontSizeNorm: Float,
    canvasSizePx: IntSize,
): Float {
    val pageHeightPx = canvasSizePx.height.coerceAtLeast(1).toFloat()
    val fontSizePx = (fontSizeNorm * pageHeightPx).coerceAtLeast(1f)
    val lines = SharedPdfTextAnnotationDefaults.estimateLineCount(
        text = text,
        fontSize = fontSizePx,
        widthPx = canvasSizePx.width.coerceAtLeast(1).toFloat() *
            (CONVERTER_EDITOR_RIGHT - CONVERTER_EDITOR_LEFT),
    )
    return ((fontSizePx * 1.35f * lines) + 14f) / pageHeightPx
}

private fun groupToSpec(
    document: SharedPdfRichDocument,
    pageIndex: Int,
    group: RichPageGroup,
    bounds: PdfPageBounds,
): SharedPdfTextBoxSpec {
    val dominant = dominantRichSpan(document.spans, group.rangeStart, group.rangeEnd)
    return SharedPdfTextBoxSpec(
        pageIndex = pageIndex,
        text = group.text,
        paragraphs = group.paragraphs.trimmedRichParagraphs(),
        colorArgb = dominant?.color ?: 0xFF000000.toInt(),
        backgroundArgb = dominant?.backgroundColor ?: 0x00000000,
        fontSizeNorm = dominant?.let {
            SharedPdfTextAnnotationDefaults.sanitizePageRelativeFontSize(it.fontSizeNorm)
        } ?: SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(16f),
        isBold = dominant?.isBold ?: false,
        isItalic = dominant?.isItalic ?: false,
        isUnderline = dominant?.isUnderline ?: false,
        isStrikeThrough = dominant?.isStrikethrough ?: false,
        fontPath = dominant?.fontPath?.takeIf { it.isNotBlank() },
        bounds = bounds,
    )
}

fun sharedPdfRichPagesToTextBoxes(
    document: SharedPdfRichDocument,
    layouts: List<SharedPdfRichPageLayout>,
    canvasSizePxForPage: (pageIndex: Int) -> IntSize,
): List<SharedPdfTextBoxSpec> {
    if (document.text.isEmpty() || layouts.isEmpty()) return emptyList()
    val specs = mutableListOf<SharedPdfTextBoxSpec>()
    layouts.sortedBy { it.pageIndex }.forEach { layout ->
        val canvasSizePx = canvasSizePxForPage(layout.pageIndex)
        val start = layout.globalStartIndex.coerceIn(0, document.text.length)
        val end = layout.globalEndIndex.coerceIn(start, document.text.length)
        if (start >= end) return@forEach
        val rawSlice = document.text.substring(start, end)
            .withoutRichAlignAnchors()
            .replace(SHARED_PDF_PAGE_BREAK_CHAR.toString(), "")
        val trimStart = rawSlice.length - rawSlice.trimStart().length
        val slice = rawSlice.trim()
        if (slice.isBlank()) return@forEach
        val base = start + trimStart

        // Non-blank paragraphs with global ranges (for style dominance).
        data class Para(val text: String, val attrs: SharedPdfRichParagraph, val from: Int, val to: Int)
        val paragraphOffset = document.text.take(base).count { it == '\n' }
        val sliceBounds = richParagraphBounds(slice)
        val paras = sliceBounds.mapIndexedNotNull { index, bound ->
            val seg = slice.substring(bound.start, bound.end)
            if (seg.isBlank()) return@mapIndexedNotNull null
            Para(
                text = seg,
                attrs = document.paragraphs.getOrElse(paragraphOffset + index) { SharedPdfRichParagraph() },
                from = base + bound.start,
                to = base + bound.end,
            )
        }
        if (paras.isEmpty()) return@forEach

        // Group: plain paragraphs stand alone; consecutive same-type list
        // paragraphs stay together so runs keep their numbering.
        val groups = mutableListOf<RichPageGroup>()
        var currentTexts = mutableListOf<String>()
        var currentAttrs = mutableListOf<SharedPdfRichParagraph>()
        var currentFrom = -1
        var currentTo = -1
        var currentList = SharedPdfRichListType.NONE
        var currentIsListRun = false
        fun flush() {
            if (currentTexts.isEmpty()) return
            groups += RichPageGroup(
                text = currentTexts.joinToString("\n"),
                paragraphs = currentAttrs.toList(),
                rangeStart = currentFrom,
                rangeEnd = currentTo,
            )
            currentTexts = mutableListOf()
            currentAttrs = mutableListOf()
            currentFrom = -1
            currentTo = -1
            currentIsListRun = false
        }
        paras.forEach { para ->
            val isList = para.attrs.listType != SharedPdfRichListType.NONE
            val continuesRun = isList && currentIsListRun && para.attrs.listType == currentList
            if (currentTexts.isNotEmpty() && !continuesRun) {
                flush()
            }
            if (currentTexts.isEmpty()) {
                currentFrom = para.from
                currentList = para.attrs.listType
                currentIsListRun = isList
            }
            currentTexts += para.text
            currentAttrs += para.attrs
            currentTo = para.to
        }
        flush()

        // Stack inside the editor rect; the remainder merges into one final
        // scrollable box instead of overlapping past the bottom.
        var cursor = CONVERTER_EDITOR_TOP
        var groupIndex = 0
        var placedOnPage = 0
        while (groupIndex < groups.size) {
            val group = groups[groupIndex]
            val norm = dominantRichSpan(document.spans, group.rangeStart, group.rangeEnd)?.let {
                SharedPdfTextAnnotationDefaults.sanitizePageRelativeFontSize(it.fontSizeNorm)
            } ?: SharedPdfTextAnnotationDefaults.displayFontSizeToPageRelative(16f)
            val height = stackedBoxHeightNorm(group.text, norm, canvasSizePx)
            if (cursor + height > CONVERTER_EDITOR_BOTTOM) {
                if (placedOnPage == 0) {
                    // Nothing placed yet: single scrollable box for the page.
                    val rest = groups.subList(groupIndex, groups.size)
                    val merged = RichPageGroup(
                        text = rest.joinToString("\n") { it.text },
                        paragraphs = rest.flatMap { it.paragraphs },
                        rangeStart = rest.first().rangeStart,
                        rangeEnd = rest.last().rangeEnd,
                    )
                    specs += groupToSpec(
                        document, layout.pageIndex, merged,
                        PdfPageBounds(
                            CONVERTER_EDITOR_LEFT, CONVERTER_EDITOR_TOP,
                            CONVERTER_EDITOR_RIGHT, CONVERTER_EDITOR_BOTTOM,
                        )
                    )
                } else {
                    // Append the remainder to the last placed box and let it
                    // scroll; its style stays that of its first paragraphs.
                    val rest = groups.subList(groupIndex, groups.size)
                    val last = specs.removeAt(specs.lastIndex)
                    val mergedText = (last.text + "\n" + rest.joinToString("\n") { it.text }).trim()
                    val mergedParas = (last.paragraphs + rest.flatMap { it.paragraphs })
                        .take(richParagraphBounds(mergedText).size)
                    specs += last.copy(
                        text = mergedText,
                        paragraphs = mergedParas.trimmedRichParagraphs(),
                        bounds = last.bounds.copy(bottom = CONVERTER_EDITOR_BOTTOM),
                    )
                }
                break
            }
            val bottom = maxOf(cursor + height, cursor + CONVERTER_MIN_BOX_HEIGHT)
                .coerceAtMost(CONVERTER_EDITOR_BOTTOM)
            specs += groupToSpec(
                document, layout.pageIndex, group,
                PdfPageBounds(CONVERTER_EDITOR_LEFT, cursor, CONVERTER_EDITOR_RIGHT, bottom)
            )
            placedOnPage++
            cursor = bottom + CONVERTER_BOX_GAP
            groupIndex++
        }
    }
    return specs
}
