package com.aryan.reader.paginatedreader

import androidx.compose.ui.geometry.Size

/**
 * Native vertical-rl (`tategaki`) text layout.
 *
 * Browsers implement vertical Japanese with per-character upright/rotated
 * orientation, kinsoku line breaking, furigana to the right of base text and
 * tate-chu-yoko runs. Compose [androidx.compose.ui.text.Text] has no
 * vertical writing mode, so pagination lays vertical chapters out with this
 * engine instead: characters flow top-to-bottom into columns, columns stack
 * right-to-left, exactly like the publication CSS (`writing-mode:
 * vertical-rl`) renders in the WebView.
 *
 * The engine is pure geometry: callers inject glyph measurement (backed by
 * `TextMeasurer` on every platform) so pagination measurement and on-screen
 * rendering share one implementation and can never disagree.
 */

/** How a vertical cell's glyphs are drawn. */
enum class VerticalCellOrientation {
    /** Upright glyphs (kana, kanji, CJK punctuation with `vert` forms). */
    UPRIGHT,
    /** Horizontal run drawn rotated 90 degrees clockwise (Latin, digits). */
    ROTATED,
    /** Horizontal run squeezed upright into one em square (tate-chu-yoko). */
    COMBINED,
    /** Empty advance (spaces, control chars). Nothing is drawn. */
    GAP,
}

/** One laid-out unit of a vertical column. */
data class VerticalCell(
    /** Offset of the first base-text character consumed by this cell. */
    val charOffset: Int,
    /** Base-text characters consumed (1 for plain chars, more for runs). */
    val charLength: Int,
    /** Cell left in paragraph space. */
    val xPx: Float,
    /** Cell top in paragraph space. */
    val yPx: Float,
    /** Cell width (column-direction extent). */
    val widthPx: Float,
    /** Cell height (inline-direction extent / advance). */
    val heightPx: Float,
    val orientation: VerticalCellOrientation,
    /** Text to draw (single char, Latin run or tcy content). */
    val text: String,
    /** Furigana reading when this cell starts a ruby base range. */
    val reading: VerticalReading? = null
)

/** Furigana attached to a base range, drawn as a mini vertical column. */
data class VerticalReading(
    val text: String,
    /** Reading column left in paragraph space. */
    val xPx: Float,
    /** Reading column top in paragraph space. */
    val yPx: Float,
    /** Reading column width (0.5em). */
    val widthPx: Float,
    /** Reading column height. */
    val heightPx: Float,
    val fontSizePx: Float
)

/** One vertical column (right-to-left order in [VerticalParagraphLayout.columns]). */
data class VerticalColumn(
    val cells: List<VerticalCell>,
    /** Column height extent actually used (<= column height budget). */
    val usedHeightPx: Float
)

/** Laid-out vertical paragraph. First column is the rightmost. */
data class VerticalParagraphLayout(
    val columns: List<VerticalColumn>,
    /** Uniform column pitch (em advance + ruby reserve). */
    val pitchPx: Float,
    /** Total width = columns * pitch. */
    val widthPx: Float,
    /** Column height budget. */
    val heightPx: Float
)

/**
 * Glyph measurement backend (TextMeasurer on every platform). Suspended
 * because platform text measurement is Main-bound, matching the pagination
 * measurement path.
 */
interface VerticalGlyphMeasurer {
    /**
     * Measures [text] set upright at [fontSizePx] with vertical (`vert`)
     * forms. Called on the UI thread by renderers and inside
     * `withContext(Dispatchers.Main)` by background pagination.
     */
    fun measureUpright(text: String, fontSizePx: Float): Size
    /** Measures [text] as a horizontal run at [fontSizePx]. */
    fun measureHorizontal(text: String, fontSizePx: Float): Size
}

/**
 * Totally ordered cache key for glyph sizes. Callers own the map instance
 * (one per pagination run / composition) so repeated characters are measured
 * once per style.
 */
fun verticalGlyphCache(): MutableMap<VerticalGlyphCacheKey, Size> = mutableMapOf()

data class VerticalGlyphCacheKey(
    val text: String,
    val sizePxBits: Int,
    val upright: Boolean
)

/** Characters that must not start a column (kinsoku-lite). */
private const val KINSOKU_LINE_START_PROHIBITED =
    "、。，．・：；？！）］｝」』〉》’”ｰ～…—～" +
        "ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶー" +
        ")]}!?,.:;%'\"»—–…"

/** Characters that must not end a column (kinsoku-lite). */
private const val KINSOKU_LINE_END_PROHIBITED =
    "「『（［｛〈《“‘（" +
        "([{\"\u2018\u201C"

/** Gap advance for an ASCII space inside vertical text, in em. */
private const val ASCII_SPACE_ADVANCE_EM = 0.5f

/** Gap between a base em box and its ruby reading column, in em. */
private const val RUBY_GAP_EM = 0.08f

private fun Int.isAsciiSpace(): Boolean = this == 0x20 || this == 0x09
private fun Int.isIdeographicSpace(): Boolean = this == 0x3000
private fun Int.isLineFeed(): Boolean = this == 0x0A

private fun codePointWidthClass(codePoint: Int): Float = when {
    codePoint.isAsciiSpace() -> ASCII_SPACE_ADVANCE_EM
    codePoint.isIdeographicSpace() -> 1f
    codePoint < 0x1100 -> 0.5f
    codePoint in 0x1100..0x115F -> 1f
    codePoint in 0x2E80..0xA4CF -> 1f
    codePoint in 0xAC00..0xD7A3 -> 1f
    codePoint in 0xF900..0xFAFF -> 1f
    codePoint in 0xFE30..0xFE4F -> 1f
    codePoint in 0xFF00..0xFF60 -> 1f
    codePoint in 0xFFE0..0xFFE6 -> 1f
    codePoint in 0x20000..0x3FFFF -> 1f
    else -> 1f
}

/**
 * Vertical orientation per UTR50, simplified: CJK scripts stay upright,
 * ASCII/halfwidth forms rotate. Callers draw ROTATED runs with a 90-degree
 * clockwise transform.
 *
 * Beyond the ASCII/halfwidth sets, two UTR50 groups rotate:
 *  - `R` classes (hyphens/dashes, curly quotes, dot leaders) always rotate.
 *  - `Tr` classes whose vertical variants have no encoded presentation form
 *    (prolonged sound mark `ー`, wave dashes `〜`/`～`, fullwidth colon `：`,
 *    wavy dash `〰`) rotate per the Tr fallback. The variants in CJK fonts
 *    are the rotated designs themselves (verified against NotoSansCJK
 *    outlines), so rotation reproduces them exactly.
 *
 * `Tr`/`Tu` punctuation with encoded vertical forms (`「` `（` `、` …) stays
 * upright: the engine substitutes those through
 * [VERTICAL_PRESENTATION_FORMS], matching the font's own `vert` feature.
 */
fun verticalOrientationForChar(char: Char): VerticalCellOrientation = when {
    char.isWhitespace() -> VerticalCellOrientation.GAP
    char < '\u0080' -> VerticalCellOrientation.ROTATED
    char in '｡'..'ﾟ' -> VerticalCellOrientation.ROTATED
    char in '￠'..'￦' && char != '￮' -> VerticalCellOrientation.ROTATED
    char in '\u2010'..'\u2015' -> VerticalCellOrientation.ROTATED // hyphens, dashes
    char in '\u2018'..'\u201D' -> VerticalCellOrientation.ROTATED // curly quotes
    char == '\u2024' || char == '\u2025' || char == '\u2027' -> VerticalCellOrientation.ROTATED // dot leaders
    char == '\u30FC' || char == '\u301C' || char == '\uFF5E' ||
        char == '\uFF1A' || char == '\u3030' -> VerticalCellOrientation.ROTATED // Tr without encoded forms
    else -> VerticalCellOrientation.UPRIGHT
}

/**
 * Encoded vertical presentation forms for CJK punctuation drawn upright in
 * `tategaki`, e.g. `「` (U+300C) is drawn as `﹁` (U+FE41).
 *
 * Browsers select these via the font's `vert` GSUB feature; the targets here
 * are exactly the glyphs the device CJK font substitutes for `vert` (verified
 * against NotoSansCJK's GSUB: every entry maps to the same glyph its `vert`
 * lookup produces, so the substitution is idempotent when the feature does
 * apply). The native pipeline substitutes explicitly because feature
 * application through `TextMeasurer` proved unreliable on-device (brackets
 * rendered with horizontal glyphs while Blink on the same fonts renders the
 * vertical forms).
 *
 * Characters whose vertical variant has no encoded presentation form
 * (prolonged sound mark `ー`, fullwidth colon `：`, em/en dashes, wave dash)
 * are intentionally absent: [verticalOrientationForChar] rotates them per
 * the UTR50 Tr/R fallback instead.
 *
 * The mapping is strictly 1:1, so base-text offsets, ruby ranges, tcy
 * ranges, kinsoku checks and highlight rects are unaffected: only the drawn
 * glyph changes.
 */
internal val VERTICAL_PRESENTATION_FORMS: Map<Char, Char> = mapOf(
    '\u3001' to '\uFE11', // 、 ideographic comma -> vertical comma
    '\u3002' to '\uFE12', // 。 ideographic full stop -> vertical full stop
    '\u300C' to '\uFE41', // 「 left corner bracket -> vertical form
    '\u300D' to '\uFE42', // 」 right corner bracket -> vertical form
    '\u300E' to '\uFE43', // 『 left white corner bracket -> vertical form
    '\u300F' to '\uFE44', // 』 right white corner bracket -> vertical form
    '\u3010' to '\uFE3B', // 【 left black lenticular -> vertical form
    '\u3011' to '\uFE3C', // 】 right black lenticular -> vertical form
    '\u3014' to '\uFE39', // 〔 left tortoise shell -> vertical form
    '\u3015' to '\uFE3A', // 〕 right tortoise shell -> vertical form
    '\u3008' to '\uFE3F', // 〈 left angle bracket -> vertical form
    '\u3009' to '\uFE40', // 〉 right angle bracket -> vertical form
    '\u300A' to '\uFE3D', // 《 left double angle -> vertical form
    '\u300B' to '\uFE3E', // 》 right double angle -> vertical form
    '\uFF08' to '\uFE35', // （ fullwidth left parenthesis -> vertical form
    '\uFF09' to '\uFE36', // ） fullwidth right parenthesis -> vertical form
    '\uFF3B' to '\uFE47', // ［ fullwidth left square bracket -> vertical form
    '\uFF3D' to '\uFE48', // ］ fullwidth right square bracket -> vertical form
    '\uFF5B' to '\uFE37', // ｛ fullwidth left curly bracket -> vertical form
    '\uFF5D' to '\uFE38', // ｝ fullwidth right curly bracket -> vertical form
    '\u2026' to '\uFE19', // … horizontal ellipsis -> vertical ellipsis
    '\uFF01' to '\uFE15', // ！ fullwidth exclamation -> vertical form
    '\uFF1F' to '\uFE16', // ？ fullwidth question mark -> vertical form
    '\uFF1B' to '\uFE14', // ； fullwidth semicolon -> vertical form
    '\uFF0C' to '\uFE10', // ， fullwidth comma -> vertical comma
    '\uFF3F' to '\uFE33', // ＿ fullwidth low line -> vertical low line
)

/**
 * Glyph to draw for [this] inside an upright vertical cell: the vertical
 * presentation form for CJK punctuation, the character itself otherwise.
 */
fun Char.verticalPresentationForm(): Char = VERTICAL_PRESENTATION_FORMS[this] ?: this

private data class VerticalSegment(
    val startOffset: Int,
    val endOffset: Int,
    val text: String,
    val orientation: VerticalCellOrientation,
    val ruby: RubyAnnotation?
)

/**
 * Lays [text] out into vertical columns.
 *
 * @param text base text (readings excluded; they arrive via [rubies]).
 * @param rubies furigana over base ranges (must be sorted by baseStart).
 * @param tcyRanges horizontal-in-vertical ranges (from TateChuYoko annotations).
 * @param fontSizePx base font size.
 * @param lineHeightEm resolved line-height factor (publication em or reader spacing).
 * @param columnHeightPx usable column height (page content height minus decorations).
 * @param maxColumns stops layout after this many columns (pagination prefix).
 * @param measurer glyph backend.
 * @param cache per-run size cache from [verticalGlyphCache].
 */
fun layoutVerticalParagraph(
    text: String,
    rubies: List<RubyAnnotation> = emptyList(),
    tcyRanges: List<IntRange> = emptyList(),
    fontSizePx: Float,
    lineHeightEm: Float,
    columnHeightPx: Float,
    maxColumns: Int = Int.MAX_VALUE,
    textAlign: androidx.compose.ui.text.style.TextAlign = androidx.compose.ui.text.style.TextAlign.Start,
    measurer: VerticalGlyphMeasurer,
    cache: MutableMap<VerticalGlyphCacheKey, Size> = verticalGlyphCache()
): VerticalParagraphLayout {
    val emPx = fontSizePx.coerceAtLeast(1f)
    val pitchPx = verticalPitchPx(emPx, lineHeightEm, rubies.isNotEmpty())
    if (text.isEmpty() || columnHeightPx <= 0f || maxColumns <= 0) {
        return VerticalParagraphLayout(emptyList(), pitchPx, 0f, columnHeightPx)
    }
    val sortedRubies = rubies.filter { it.baseStart < it.baseEnd && it.reading.isNotEmpty() }
        .sortedBy { it.baseStart }
    val mergedTcy = mergeVerticalRanges(tcyRanges, text.length)
    val segments = segmentVerticalText(text, sortedRubies, mergedTcy)
    val columns = mutableListOf<VerticalColumn>()
    var segmentIndex = 0
    var columnCount = 0
    outer@ while (segmentIndex < segments.size && columnCount < maxColumns) {
        val cells = mutableListOf<VerticalCell>()
        var yPx = 0f
        while (segmentIndex < segments.size) {
            val segment = segments[segmentIndex]
            // Explicit line feed forces a column break (after any pending content).
            if (segment.orientation == VerticalCellOrientation.GAP && segment.text == "\n") {
                segmentIndex++
                break
            }
            val advancePx = verticalAdvanceFor(segment, emPx, measurer, cache)
            val cellWidthPx = verticalCellWidth(segment, emPx, measurer, cache)
            // Overflow: break into a new column when legal; kinsoku or ruby
            // violations overhang in this column (browsers overhang too).
            if (cells.isNotEmpty() && yPx + advancePx > columnHeightPx + 0.5f &&
                canBreakAtVerticalOffset(text, segment.startOffset, sortedRubies)
            ) {
                break
            }
            val cell = VerticalCell(
                charOffset = segment.startOffset,
                charLength = segment.endOffset - segment.startOffset,
                xPx = 0f, // resolved after total width is known
                yPx = yPx,
                widthPx = cellWidthPx,
                heightPx = advancePx,
                orientation = segment.orientation,
                text = segment.text,
                reading = null
            )
            cells.add(cell)
            yPx += advancePx
            segmentIndex++
            if (yPx >= columnHeightPx - 0.5f) {
                // Full column: only break when the next segment may legally
                // start a column, otherwise it overhangs here (kinsoku/ruby).
                val next = segments.getOrNull(segmentIndex)
                if (next == null || canBreakAtVerticalOffset(text, next.startOffset, sortedRubies)) break
            }
        }
        if (cells.isEmpty()) {
            // Single oversized cell (long Latin run): place it overflowing.
            val segment = segments[segmentIndex]
            val advancePx = verticalAdvanceFor(segment, emPx, measurer, cache)
            cells.add(
                VerticalCell(
                    charOffset = segment.startOffset,
                    charLength = segment.endOffset - segment.startOffset,
                    xPx = 0f,
                    yPx = 0f,
                    widthPx = verticalCellWidth(segment, emPx, measurer, cache),
                    heightPx = advancePx,
                    orientation = segment.orientation,
                    text = segment.text
                )
            )
            segmentIndex++
        }
        columns.add(VerticalColumn(cells, yPx))
        columnCount++
        if (segmentIndex >= segments.size) break@outer
    }
    // Attach ruby readings (mini vertical columns right of their base union).
    val rubyByStart = sortedRubies.associateBy { it.baseStart }
    val columnsWithRuby = columns.map { column ->
        val cellsWithRuby = column.cells.map { cell ->
            val ruby = rubyByStart[cell.charOffset]
            if (ruby == null || ruby.reading.isEmpty()) {
                cell
            } else {
                cell.copy(reading = layoutVerticalReading(ruby, column, emPx, pitchPx, measurer, cache))
            }
        }
        column.copy(cells = cellsWithRuby)
    }
    // Column content alignment: full middle columns need nothing; the final
    // (possibly short) column aligns per textAlign, non-final short columns
    // spread under forced justify, otherwise pack from the top.
    val aligned = columnsWithRuby.mapIndexed { columnIdx, column ->
        val isFinal = columnIdx == columnsWithRuby.lastIndex &&
            column.cells.lastOrNull()?.let { it.charOffset + it.charLength >= text.length } == true
        alignVerticalColumn(column, columnHeightPx, textAlign, isFinal)
    }
    // Resolve column x: first column rightmost.
    val totalWidthPx = aligned.size * pitchPx
    val placed = aligned.mapIndexed { index, column ->
        val columnLeft = totalWidthPx - (index + 1) * pitchPx
        val baseCenterX = columnLeft + pitchPx / 2f
        column.copy(
            cells = column.cells.map { cell ->
                val centeredX = baseCenterX - cell.widthPx / 2f
                val reading = cell.reading?.let { reading ->
                    // Reading sits right of the base em box, clamped inside
                    // this column's pitch slot. Browsers let ruby overhang
                    // freely; paginated pages own their bounds, so a
                    // first-column reading must never paint into the
                    // neighboring block (an image, typically). When the
                    // pitch is tight the reading overhangs its own base
                    // instead, like a line-height-constrained browser.
                    val desiredX = baseCenterX + emPx / 2f + emPx * RUBY_GAP_EM
                    val maxX = columnLeft + pitchPx - reading.widthPx
                    reading.copy(xPx = minOf(desiredX, maxX))
                }
                cell.copy(xPx = centeredX, reading = reading)
            }
        )
    }
    return VerticalParagraphLayout(placed, pitchPx, totalWidthPx, columnHeightPx)
}

private fun alignVerticalColumn(
    column: VerticalColumn,
    columnHeightPx: Float,
    textAlign: androidx.compose.ui.text.style.TextAlign,
    isFinal: Boolean
): VerticalColumn {
    if (column.cells.isEmpty()) return column
    val usedPx = column.cells.maxOf { it.yPx + it.heightPx }
    val freePx = columnHeightPx - usedPx
    if (freePx <= 0.5f) return column
    // Forced justify spreads non-final columns edge to edge; the final column
    // packs start (text-align-last behavior), matching the horizontal
    // justify-downgrade contract for the common case.
    if (textAlign == androidx.compose.ui.text.style.TextAlign.Justify && !isFinal) {
        val gaps = (column.cells.size - 1).coerceAtLeast(1)
        val step = freePx / gaps
        return column.copy(
            cells = column.cells.mapIndexed { index, cell ->
                val shift = step * index
                cell.copy(
                    yPx = cell.yPx + shift,
                    reading = cell.reading?.copy(yPx = cell.reading.yPx + shift)
                )
            }
        )
    }
    if (!isFinal) return column
    val shift = when (textAlign) {
        androidx.compose.ui.text.style.TextAlign.Center -> freePx / 2f
        androidx.compose.ui.text.style.TextAlign.End,
        androidx.compose.ui.text.style.TextAlign.Right -> freePx
        else -> 0f
    }
    if (shift <= 0f) return column
    return column.copy(
        cells = column.cells.map { cell ->
            cell.copy(
                yPx = cell.yPx + shift,
                reading = cell.reading?.copy(yPx = cell.reading.yPx + shift)
            )
        }
    )
}

/** Uniform column pitch for [fontSizePx] and [lineHeightEm] (with ruby reserve). */
fun verticalPitchPx(fontSizePx: Float, lineHeightEm: Float, hasRuby: Boolean): Float {
    val emPx = fontSizePx.coerceAtLeast(1f)
    return (emPx * maxOf(lineHeightEm, if (hasRuby) RubyReserveLineHeightEm else 0f)).coerceAtLeast(1f)
}

/** End offset (exclusive) consumed by a prefix of [columnCount] columns. */
fun VerticalParagraphLayout.endOffsetForColumnPrefix(columnCount: Int): Int {
    if (columnCount <= 0 || columns.isEmpty()) return 0
    val prefix = columns.take(columnCount.coerceAtMost(columns.size))
    val lastCell = prefix.lastOrNull()?.cells?.lastOrNull() ?: return 0
    return lastCell.charOffset + lastCell.charLength
}



/**
 * Column-break legality: kinsoku (no prohibited line-start/end) plus ruby
 * atomicity (never break strictly inside a base range).
 */
fun canBreakAtVerticalOffset(text: String, offset: Int, rubies: List<RubyAnnotation>): Boolean {
    if (offset <= 0 || offset >= text.length) return true
    for (ruby in rubies) {
        if (offset > ruby.baseStart && offset < ruby.baseEnd) return false
    }
    return canBreakBeforeVertical(text, offset)
}

private fun mergeVerticalRanges(ranges: List<IntRange>, textLength: Int): List<IntRange> {
    if (ranges.isEmpty()) return emptyList()
    val clipped = ranges.mapNotNull { range ->
        val start = range.first.coerceIn(0, textLength)
        val end = range.last.coerceIn(-1, textLength - 1)
        if (end < start) null else start..end
    }.sortedBy { it.first }
    if (clipped.isEmpty()) return emptyList()
    val merged = mutableListOf(clipped.first())
    for (range in clipped.drop(1)) {
        val last = merged.last()
        if (range.first <= last.last + 1) {
            merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
        } else {
            merged.add(range)
        }
    }
    return merged
}

private fun segmentVerticalText(
    text: String,
    rubies: List<RubyAnnotation>,
    tcyRanges: List<IntRange>
): List<VerticalSegment> {
    val segments = mutableListOf<VerticalSegment>()
    val rubyByStart = rubies.associateBy { it.baseStart }
    var offset = 0
    var tcyIndex = 0
    val sortedTcy = tcyRanges.sortedBy { it.first }
    while (offset < text.length) {
        val tcy = sortedTcy.getOrNull(tcyIndex)
        if (tcy != null && offset > tcy.last) {
            tcyIndex++
            continue
        }
        if (tcy != null && offset == tcy.first) {
            val end = (tcy.last + 1).coerceAtMost(text.length)
            segments.add(
                VerticalSegment(offset, end, text.substring(offset, end), VerticalCellOrientation.COMBINED, rubyByStart[offset])
            )
            offset = end
            tcyIndex++
            continue
        }
        if (tcy != null && offset > tcy.first) {
            // Defensive: offset landed inside a tcy range; consume the rest of it.
            val end = (tcy.last + 1).coerceAtMost(text.length)
            segments.add(
                VerticalSegment(offset, end, text.substring(offset, end), VerticalCellOrientation.COMBINED, rubyByStart[offset])
            )
            offset = end
            tcyIndex++
            continue
        }
        val char = text[offset]
        val codePoint = char.code
        when {
            codePoint == 0x0A -> {
                segments.add(VerticalSegment(offset, offset + 1, "\n", VerticalCellOrientation.GAP, null))
                offset++
            }
            char.isWhitespace() -> {
                segments.add(VerticalSegment(offset, offset + 1, char.toString(), VerticalCellOrientation.GAP, rubyByStart[offset]))
                offset++
            }
            verticalOrientationForChar(char) == VerticalCellOrientation.ROTATED -> {
                var end = offset + 1
                while (end < text.length) {
                    val next = text[end]
                    if (next == '\n' || next.isWhitespace()) break
                    if (verticalOrientationForChar(next) != VerticalCellOrientation.ROTATED) break
                    if (sortedTcy.any { end >= it.first && end <= it.last }) break
                    if (rubyByStart.containsKey(end)) break
                    end++
                }
                segments.add(
                    VerticalSegment(offset, end, text.substring(offset, end), VerticalCellOrientation.ROTATED, rubyByStart[offset])
                )
                offset = end
            }
            else -> {
                // Upright cells draw the vertical presentation form for CJK
                // punctuation (see verticalPresentationForm). Offsets and
                // advances are unchanged: the mapping is 1:1 and every
                // presentation form is a fullwidth em like its source.
                val display = char.verticalPresentationForm()
                segments.add(
                    VerticalSegment(offset, offset + 1, display.toString(), VerticalCellOrientation.UPRIGHT, rubyByStart[offset])
                )
                offset++
            }
        }
    }
    return segments
}

private fun verticalAdvanceFor(
    segment: VerticalSegment,
    emPx: Float,
    measurer: VerticalGlyphMeasurer,
    cache: MutableMap<VerticalGlyphCacheKey, Size>
): Float {
    if (segment.text == "\n") return 0f
    return when (segment.orientation) {
        VerticalCellOrientation.GAP -> {
            if (segment.text == " " || segment.text == "\t") ASCII_SPACE_ADVANCE_EM * emPx else emPx
        }
        VerticalCellOrientation.UPRIGHT -> emPx * codePointWidthClass(segment.text.firstOrNull()?.code ?: 0x3000)
        VerticalCellOrientation.ROTATED -> {
            val size = cachedMeasure(measurer, cache, segment.text, emPx, upright = false)
            size.width.coerceAtLeast(emPx * 0.25f)
        }
        VerticalCellOrientation.COMBINED -> emPx
    }
}

private fun verticalCellWidth(
    segment: VerticalSegment,
    emPx: Float,
    measurer: VerticalGlyphMeasurer,
    cache: MutableMap<VerticalGlyphCacheKey, Size>
): Float {
    return when (segment.orientation) {
        VerticalCellOrientation.GAP -> 0f
        VerticalCellOrientation.UPRIGHT -> emPx * codePointWidthClass(segment.text.firstOrNull()?.code ?: 0x3000)
        VerticalCellOrientation.ROTATED -> {
            val size = cachedMeasure(measurer, cache, segment.text, emPx, upright = false)
            size.height.coerceAtLeast(emPx * 0.25f)
        }
        VerticalCellOrientation.COMBINED -> emPx
    }
}

private fun cachedMeasure(
    measurer: VerticalGlyphMeasurer,
    cache: MutableMap<VerticalGlyphCacheKey, Size>,
    text: String,
    fontSizePx: Float,
    upright: Boolean
): Size {
    val key = VerticalGlyphCacheKey(text, fontSizePx.toBits(), upright)
    cache[key]?.let { return it }
    val fresh = if (upright) {
        measurer.measureUpright(text, fontSizePx)
    } else {
        measurer.measureHorizontal(text, fontSizePx)
    }
    cache[key] = fresh
    return fresh
}

private fun canBreakBeforeVertical(text: String, offset: Int): Boolean {
    if (offset <= 0 || offset >= text.length) return true
    // Never split a ruby base range (checked by callers via ranges too).
    if (text[offset] in KINSOKU_LINE_START_PROHIBITED) return false
    if (text[offset - 1] in KINSOKU_LINE_END_PROHIBITED) return false
    return true
}

private fun layoutVerticalReading(
    ruby: RubyAnnotation,
    column: VerticalColumn,
    emPx: Float,
    pitchPx: Float,
    measurer: VerticalGlyphMeasurer,
    cache: MutableMap<VerticalGlyphCacheKey, Size>
): VerticalReading {
    val readingSizePx = (emPx * ruby.effectiveReadingScale()).coerceAtLeast(1f)
    // Vertical span of the base range inside this column.
    var topPx: Float? = null
    var bottomPx: Float? = null
    for (cell in column.cells) {
        val cellEnd = cell.charOffset + cell.charLength
        if (cellEnd <= ruby.baseStart || cell.charOffset >= ruby.baseEnd) continue
        topPx = minOf(topPx ?: cell.yPx, cell.yPx)
        bottomPx = maxOf(bottomPx ?: (cell.yPx + cell.heightPx), cell.yPx + cell.heightPx)
    }
    val spanTop = topPx ?: 0f
    val spanHeight = ((bottomPx ?: spanTop) - spanTop).coerceAtLeast(emPx * 0.5f)
    val readingWidth = readingSizePx
    // Reading column height fits its characters at half-em advances.
    val readingHeight = (ruby.reading.length * readingSizePx).coerceAtLeast(readingSizePx)
    val yPx = spanTop + (spanHeight - readingHeight) / 2f
    return VerticalReading(
        text = ruby.reading,
        xPx = 0f, // resolved by the caller once total width is known
        yPx = yPx,
        widthPx = readingWidth,
        heightPx = readingHeight,
        fontSizePx = readingSizePx
    )
}
