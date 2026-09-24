package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextAlign

/**
 * Paragraph-level rich-text model (Samsung-Notes-style lists + alignment),
 * shared-first so Android and iOS stay identical.
 *
 * Storage is two-fold:
 * - [SharedPdfRichDocument.paragraphs]: persisted per-paragraph attributes,
 *   indexed by '\n'-paragraph.
 * - [AnnotatedString]: the live source of truth. Alignment travels as
 *   [ParagraphStyle] and list type as a [SHARED_PDF_RICH_LIST_TAG] string
 *   annotation, so every subSequence/append path (pagination, sync, page
 *   insert/delete) preserves them automatically. Only the manual rebuild in
 *   `onValueChanged` and the toggle ops below need explicit handling.
 *
 * List markers ("• ", "1. ") are managed text: they live in the document
 * text itself, which keeps pagination, export, rasterization, cursor math
 * and selection rendering working unchanged. All marker maintenance runs
 * through the pure functions here so it is unit-testable.
 */
enum class SharedPdfRichTextAlign { LEFT, CENTER, RIGHT }

enum class SharedPdfRichListType { NONE, BULLET, NUMBERED }

data class SharedPdfRichParagraph(
    val alignment: SharedPdfRichTextAlign = SharedPdfRichTextAlign.LEFT,
    val listType: SharedPdfRichListType = SharedPdfRichListType.NONE,
)

internal const val SHARED_PDF_RICH_LIST_TAG = "pdf-rich-list"
internal const val SHARED_PDF_RICH_LIST_BULLET = "bullet"
internal const val SHARED_PDF_RICH_LIST_NUMBERED = "numbered"

/** Bullet marker text (U+2022 + space). */
const val SHARED_PDF_RICH_BULLET_MARKER = "• "

fun sharedPdfRichNumberedMarker(number: Int): String = "$number. "

fun sharedPdfRichMarkerForList(type: SharedPdfRichListType, number: Int): String? = when (type) {
    SharedPdfRichListType.BULLET -> SHARED_PDF_RICH_BULLET_MARKER
    SharedPdfRichListType.NUMBERED -> sharedPdfRichNumberedMarker(number)
    SharedPdfRichListType.NONE -> null
}

internal fun SharedPdfRichListType.toListTag(): String? = when (this) {
    SharedPdfRichListType.BULLET -> SHARED_PDF_RICH_LIST_BULLET
    SharedPdfRichListType.NUMBERED -> SHARED_PDF_RICH_LIST_NUMBERED
    SharedPdfRichListType.NONE -> null
}

internal fun String.toRichListType(): SharedPdfRichListType = when (this) {
    SHARED_PDF_RICH_LIST_BULLET -> SharedPdfRichListType.BULLET
    SHARED_PDF_RICH_LIST_NUMBERED -> SharedPdfRichListType.NUMBERED
    else -> SharedPdfRichListType.NONE
}

fun SharedPdfRichTextAlign.toComposeTextAlign(): TextAlign = when (this) {
    SharedPdfRichTextAlign.LEFT -> TextAlign.Left
    SharedPdfRichTextAlign.CENTER -> TextAlign.Center
    SharedPdfRichTextAlign.RIGHT -> TextAlign.Right
}

fun TextAlign.toRichTextAlign(): SharedPdfRichTextAlign = when (this) {
    TextAlign.Center -> SharedPdfRichTextAlign.CENTER
    TextAlign.Right, TextAlign.End -> SharedPdfRichTextAlign.RIGHT
    else -> SharedPdfRichTextAlign.LEFT
}

/** Content range of one '\n'-paragraph, newline excluded. */
internal data class RichParaBounds(val start: Int, val end: Int)

internal fun richParagraphBounds(text: String): List<RichParaBounds> {
    val bounds = mutableListOf<RichParaBounds>()
    var start = 0
    for (i in text.indices) {
        if (text[i] == '\n') {
            bounds += RichParaBounds(start, i)
            start = i + 1
        }
    }
    bounds += RichParaBounds(start, text.length)
    return bounds
}

/**
 * Length of a managed list marker at [offset], or 0. Only matches exact
 * managed forms: "• " or "<digits>. ". Never sniffs user prose — callers
 * only invoke it where one of OUR markers is expected.
 */
internal fun richMarkerLengthAt(text: String, offset: Int): Int {
    if (offset < 0 || offset >= text.length) return 0
    if (text.startsWith(SHARED_PDF_RICH_BULLET_MARKER, offset)) {
        return SHARED_PDF_RICH_BULLET_MARKER.length
    }
    var i = offset
    val digitsStart = i
    while (i < text.length && text[i] in '0'..'9') i++
    if (i == digitsStart || i + 1 >= text.length) return 0
    if (text[i] != '.' || text[i + 1] != ' ') return 0
    if (i - digitsStart > 6) return 0
    return (i + 2) - offset
}

internal fun readRichParagraphAlignment(
    annotated: AnnotatedString,
    paraStart: Int,
    contentOffset: Int = 0,
    isFirstParagraph: Boolean = false,
): SharedPdfRichTextAlign {
    // Local page segments carry a leading ZWSP before user content: paragraph
    // 0's styles start at contentOffset, so probe there (global callers pass
    // 0 and behave exactly as before).
    val effectiveStart = paraStart + if (isFirstParagraph) contentOffset else 0
    val style = annotated.paragraphStyles.firstOrNull { range ->
        range.start <= effectiveStart && effectiveStart < range.end
    }?.item
    return style?.textAlign?.toRichTextAlign() ?: SharedPdfRichTextAlign.LEFT
}

internal fun readRichParagraphListType(
    annotated: AnnotatedString,
    paraStart: Int,
    paraEnd: Int,
    contentOffset: Int = 0,
    isFirstParagraph: Boolean = false,
): SharedPdfRichListType {
    if (paraStart < 0 || paraStart > annotated.length) return SharedPdfRichListType.NONE
    val probeEnd = if (paraEnd > paraStart) {
        paraEnd.coerceAtMost(annotated.length)
    } else {
        (paraStart + 1).coerceAtMost(annotated.length)
    }
    if (probeEnd <= paraStart) return SharedPdfRichListType.NONE
    val effectiveStart = paraStart + if (isFirstParagraph) contentOffset else 0
    val tag = annotated.getStringAnnotations(
        tag = SHARED_PDF_RICH_LIST_TAG,
        start = paraStart,
        end = probeEnd,
    ).firstOrNull { it.start <= effectiveStart }?.item
    return tag?.toRichListType() ?: SharedPdfRichListType.NONE
}

/** Paragraph attributes for every '\n'-paragraph in [annotated]. */
fun readRichParagraphs(annotated: AnnotatedString, contentOffset: Int = 0): List<SharedPdfRichParagraph> {
    val bounds = richParagraphBounds(annotated.text)
    val attrs = bounds.mapIndexed { index, bound ->
        SharedPdfRichParagraph(
            alignment = readRichParagraphAlignment(annotated, bound.start, contentOffset, index == 0),
            listType = readRichParagraphListType(annotated, bound.start, bound.end, contentOffset, index == 0),
        )
    }.toMutableList()
    // A trailing empty paragraph ([N,N)) cannot carry its own style range,
    // so it inherits the previous paragraph's alignment (dock display and
    // Enter/type inheritance). List type is deliberately NOT inherited:
    // marker presence governs lists (exit-list semantics stay intact).
    // Explicitly clearing to LEFT on a trailing empty line is not
    // representable and keeps the inherited value.
    val lastIndex = bounds.size - 1
    if (lastIndex > 0) {
        val last = bounds[lastIndex]
        if (last.start == last.end && last.start == annotated.length) {
            attrs[lastIndex] = attrs[lastIndex].copy(alignment = attrs[lastIndex - 1].alignment)
        }
    }
    return attrs
}

/** One merged non-LEFT alignment run: a single ParagraphStyle range. */
internal data class RichAlignRun(
    val alignment: SharedPdfRichTextAlign,
    val start: Int,
    val end: Int,
)

/**
 * Exclusive end offset for [bound]'s alignment style (mark-carries-style):
 * non-empty paragraphs include their terminating `'\n'` when present so the
 * mark stays in the same MultiParagraph segment; empty mid-text paragraphs
 * style the mark itself. Trailing empty at EOF has no character — callers
 * must not emit an empty range for it (see readRichParagraphs inherit).
 */
internal fun richParagraphStyleEnd(text: String, bound: RichParaBounds): Int {
    if (bound.end > bound.start) {
        return if (bound.end < text.length && text[bound.end] == '\n') {
            bound.end + 1
        } else {
            bound.end
        }
    }
    return if (bound.start < text.length && text[bound.start] == '\n') {
        bound.start + 1
    } else {
        bound.start
    }
}

/** Trailing ZWSP used only as a styleable EOF anchor for Rule 3. */
internal const val SHARED_PDF_RICH_ALIGN_ANCHOR: Char = '\u200B'

/**
 * Removes every ZWSP from global text (live buffer / saved document).
 * Local edit buffers keep their leading ZWSP — do not call this there.
 */
internal fun String.withoutRichAlignAnchors(): String =
    if (indexOf(SHARED_PDF_RICH_ALIGN_ANCHOR) < 0) this else replace(SHARED_PDF_RICH_ALIGN_ANCHOR.toString(), "")

/**
 * Rule 3: when the trailing empty paragraph is non-LEFT and no run already
 * covers EOF through a preceding same-align mark, append a ZWSP anchor and
 * style it so the caret/line can center. Idempotent; LEFT never anchors.
 */
internal fun AnnotatedString.withTrailingRichAlignAnchorIfNeeded(
    paragraphs: List<SharedPdfRichParagraph>,
): AnnotatedString {
    if (text.endsWith(SHARED_PDF_RICH_ALIGN_ANCHOR)) return this
    if (paragraphs.isEmpty()) return this
    // Local edit buffers start with a content ZWSP — callers must only use
    // this on global text (no leading ZWSP); local rebuild skips the anchor.
    val bounds = richParagraphBounds(text)
    val lastBound = bounds.last()
    val lastAttrs = paragraphs.getOrElse(bounds.lastIndex) { SharedPdfRichParagraph() }
    if (lastAttrs.alignment == SharedPdfRichTextAlign.LEFT) return this
    val runs = richParagraphAlignRuns(text, paragraphs)
    val coversEof = runs.any { it.start < text.length && it.end >= text.length }
    val trailingEmpty = lastBound.start == lastBound.end && lastBound.start == text.length
    if (trailingEmpty && coversEof) return this
    if (!trailingEmpty && coversEof) return this
    if (!trailingEmpty && runs.isNotEmpty() && runs.last().end == text.length) return this

    val anchored = text + SHARED_PDF_RICH_ALIGN_ANCHOR
    val builder = AnnotatedString.Builder(anchored)
    spanStyles.forEach { builder.addStyle(it.item, it.start, it.end) }
    getStringAnnotations(0, length).forEach {
        builder.addStringAnnotation(it.tag, it.item, it.start, it.end)
    }
    applyRichParagraphsToBuilder(builder, anchored, paragraphs)
    return builder.toAnnotatedString()
}

/**
 * Merged alignment runs for [text]/[paragraphs]. Consecutive same non-LEFT
 * paragraphs share one range (Compose splits MultiParagraph on every
 * ParagraphStyle boundary; per-paragraph ranges invent a line at each split).
 * LEFT paragraphs are omitted so plain documents stay sparse.
 */
internal fun richParagraphAlignRuns(
    text: String,
    paragraphs: List<SharedPdfRichParagraph>,
): List<RichAlignRun> {
    if (text.isEmpty()) return emptyList()
    val bounds = richParagraphBounds(text)
    val runs = mutableListOf<RichAlignRun>()
    var i = 0
    while (i < bounds.size) {
        val align = paragraphs.getOrElse(i) { SharedPdfRichParagraph() }.alignment
        if (align == SharedPdfRichTextAlign.LEFT) {
            i++
            continue
        }
        var j = i
        while (j + 1 < bounds.size &&
            paragraphs.getOrElse(j + 1) { SharedPdfRichParagraph() }.alignment == align
        ) {
            j++
        }
        val start = bounds[i].start
        val end = richParagraphStyleEnd(text, bounds[j])
        if (end > start) {
            runs += RichAlignRun(align, start, end)
        }
        i = j + 1
    }
    return runs
}

/**
 * Writes paragraph attributes into [builder] (whose text must already equal
 * [text]). Defaults are skipped so plain documents carry no extra ranges;
 * alignment is emitted as merged runs (see [richParagraphAlignRuns]).
 */
fun applyRichParagraphsToBuilder(
    builder: AnnotatedString.Builder,
    text: String,
    paragraphs: List<SharedPdfRichParagraph>,
) {
    richParagraphAlignRuns(text, paragraphs).forEach { run ->
        builder.addStyle(
            ParagraphStyle(textAlign = run.alignment.toComposeTextAlign()),
            run.start,
            run.end,
        )
    }
    richParagraphBounds(text).forEachIndexed { index, bound ->
        if (bound.start >= bound.end) return@forEachIndexed
        val attrs = paragraphs.getOrElse(index) { SharedPdfRichParagraph() }
        attrs.listType.toListTag()?.let { tag ->
            builder.addStringAnnotation(
                tag = SHARED_PDF_RICH_LIST_TAG,
                annotation = tag,
                start = bound.start,
                end = bound.end,
            )
        }
    }
}

/** A raw text shift for span/cursor bookkeeping. */
data class RichTextShift(val changeStart: Int, val changeEndOld: Int, val diff: Int)

/** A marker range that needs an explicit span style after shifting. */
data class RichMarkerSpan(val start: Int, val end: Int, val style: SpanStyle)

data class NormalizedRichParagraphEdit(
    val text: String,
    val selection: TextRange,
    /** The effective raw change (relocated past the marker when applicable). */
    val effectiveShift: RichTextShift,
    /** Follow-up shifts (marker insert/remove, renumber) in application order. */
    val shifts: List<RichTextShift>,
    val markerSpans: List<RichMarkerSpan>,
    val paragraphs: List<SharedPdfRichParagraph>,
    /** Typed-text range in final coords (pre-[shifts]), or -1 when deleted. */
    val typedStart: Int,
    val typedEnd: Int,
)

data class RichParagraphUiState(
    val alignment: SharedPdfRichTextAlign,
    val isBulleted: Boolean,
    val isNumbered: Boolean,
)

/**
 * Dock state for [selection]: alignment of the paragraph at selection
 * start; list buttons active only when EVERY selected paragraph has that
 * type (collapsed cursor = that paragraph).
 */
fun richParagraphUiState(
    annotated: AnnotatedString,
    selection: TextRange,
    contentOffset: Int = 0,
): RichParagraphUiState {
    val attrs = readRichParagraphs(annotated, contentOffset)
    if (attrs.isEmpty()) {
        return RichParagraphUiState(SharedPdfRichTextAlign.LEFT, false, false)
    }
    val start = selection.min.coerceIn(0, annotated.length)
    val end = selection.max.coerceIn(start, annotated.length)
    val bounds = richParagraphBounds(annotated.text)
    val firstIndex = bounds.indexOfLast { it.start <= start }.coerceAtLeast(0)
    val lastIndex = bounds.indexOfLast { it.start <= end }.coerceAtLeast(firstIndex)
    val alignment = attrs.getOrElse(firstIndex) { SharedPdfRichParagraph() }.alignment
    var bulleted = true
    var numbered = true
    for (i in firstIndex..lastIndex) {
        when (attrs.getOrElse(i) { SharedPdfRichParagraph() }.listType) {
            SharedPdfRichListType.BULLET -> numbered = false
            SharedPdfRichListType.NUMBERED -> bulleted = false
            SharedPdfRichListType.NONE -> {
                bulleted = false
                numbered = false
            }
        }
    }
    return RichParagraphUiState(alignment, bulleted, numbered)
}

/**
 * Core keystroke maintenance: takes the raw field change and returns fully
 * normalized text + paragraph attributes, Samsung-Notes rules:
 * - typing at a list paragraph start lands AFTER the marker;
 * - Enter splits: new paragraphs inherit alignment + list (an empty
 *   marker-only source exits the list instead);
 * - backspace over a marker exits the list (marker remnant removed);
 * - backspace merging paragraphs drops the merged paragraphs' lists and
 *   removes their orphaned markers at known offsets (never content-sniffed);
 * - numbered runs renumber.
 *
 * @param contentOffset chars before user content (1 when a leading ZWSP is
 *   present, 0 otherwise). The ZWSP itself is never touched.
 * @param firstStartsAtParagraphStart false when paragraph 0 is a page-split
 *   continuation (its marker lives on the previous page: don't manage one).
 * @param inheritList false for programmatic trailing-newline padding (tap
 *   below text), which stays out of lists.
 */
fun applyRichParagraphKeystroke(
    old: AnnotatedString,
    newText: String,
    newSelection: TextRange,
    contentOffset: Int = 0,
    firstStartsAtParagraphStart: Boolean = true,
    inheritList: Boolean = true,
    markerFallbackStyle: SpanStyle = SpanStyle(),
): NormalizedRichParagraphEdit {
    val oldText = old.text
    val rawDiff = newText.length - oldText.length
    if (rawDiff == 0) {
        return NormalizedRichParagraphEdit(
            text = oldText,
            selection = TextRange(
                newSelection.min.coerceIn(0, oldText.length),
                newSelection.max.coerceIn(0, oldText.length),
            ),
            effectiveShift = RichTextShift(0, 0, 0),
            shifts = emptyList(),
            markerSpans = emptyList(),
            paragraphs = readRichParagraphs(old, contentOffset),
            typedStart = -1,
            typedEnd = -1,
        )
    }
    // Clamp change coords (IME/test edge: bulk insert with cursor at 0).
    val rawCursor = newSelection.end.coerceIn(0, newText.length)
    val rawChangeStart = if (rawDiff > 0) {
        (rawCursor - rawDiff).coerceIn(0, (newText.length - rawDiff).coerceAtLeast(0))
    } else {
        rawCursor.coerceIn(0, oldText.length)
    }
    val rawChangeEndOld = if (rawDiff > 0) {
        rawChangeStart
    } else {
        (rawChangeStart - rawDiff).coerceIn(rawChangeStart, oldText.length)
    }

    val oldBounds = richParagraphBounds(oldText)
    val oldAttrs = readRichParagraphs(old, contentOffset)

    fun oldContentStartOf(index: Int): Int =
        oldBounds[index].start + if (index == 0) contentOffset else 0

    fun markerStyleFor(index: Int): SpanStyle {
        if (old.length == 0) return markerFallbackStyle
        val probe = oldContentStartOf(index.coerceIn(oldBounds.indices)).coerceIn(0, old.length - 1)
        return old.spanStyles.firstOrNull { it.start <= probe && probe < it.end }?.item
            ?: markerFallbackStyle
    }

    // --- 1. Relocate insertions at a list paragraph start past the marker.
    var working = newText
    var effectiveShift = RichTextShift(rawChangeStart, rawChangeEndOld, rawDiff)
    var typedStart = if (rawDiff > 0) rawChangeStart else -1
    var typedEnd = if (rawDiff > 0) rawChangeStart + rawDiff else -1
    var selStart = newSelection.min.coerceIn(0, newText.length)
    var selEnd = newSelection.max.coerceIn(0, newText.length)
    if (rawDiff > 0) {
        val inserted = newText.substring(rawChangeStart, rawChangeStart + rawDiff)
        val paraIndex = oldBounds.indexOfLast { it.start <= rawChangeStart }.coerceAtLeast(0)
        val contentStart = oldContentStartOf(paraIndex).coerceAtMost(oldText.length)
        val markerLen = richMarkerLengthAt(oldText, contentStart)
        if (!inserted.startsWith("\n") &&
            rawChangeStart == contentStart &&
            markerLen > 0 &&
            oldAttrs.getOrElse(paraIndex) { SharedPdfRichParagraph() }.listType != SharedPdfRichListType.NONE
        ) {
            val moved = StringBuilder(working).deleteRange(rawChangeStart, rawChangeStart + rawDiff)
            val insertAt = (contentStart + markerLen).coerceIn(0, moved.length)
            moved.insert(insertAt, inserted)
            working = moved.toString()
            // Effective change on OLD text: insertion right after the marker.
            effectiveShift = RichTextShift(contentStart + markerLen, contentStart + markerLen, rawDiff)
            typedStart = insertAt
            typedEnd = insertAt + rawDiff
            fun remap(offset: Int): Int = when {
                offset < rawChangeStart -> offset
                offset <= rawChangeStart + rawDiff -> insertAt + (offset - rawChangeStart)
                else -> offset
            }
            selStart = remap(selStart)
            selEnd = remap(selEnd)
        }
    }

    // --- 2. Structural reconcile on the (possibly relocated) change.
    val diff = working.length - oldText.length
    val cursor = selEnd.coerceIn(0, working.length)
    val changeStart = if (diff > 0) {
        (cursor - diff).coerceIn(0, (working.length - diff).coerceAtLeast(0))
    } else {
        cursor.coerceIn(0, oldText.length)
    }
    val changeEndOld = if (diff > 0) {
        changeStart
    } else {
        (changeStart - diff).coerceIn(changeStart, oldText.length)
    }
    val insertedText = if (diff > 0) {
        working.substring(changeStart.coerceIn(0, working.length), cursor)
    } else {
        ""
    }
    val deletedText = if (diff < 0) {
        oldText.substring(changeStart.coerceIn(0, oldText.length), changeEndOld.coerceIn(0, oldText.length))
    } else {
        ""
    }

    val newAttrs = oldAttrs.toMutableList()
    val shifts = mutableListOf<RichTextShift>()
    val markerSpans = mutableListOf<RichMarkerSpan>()
    val sb = StringBuilder(working)
    var trackStart = selStart.coerceIn(0, sb.length)
    var trackEnd = selEnd.coerceIn(0, sb.length)
    // NB: the typed range is intentionally NOT tracked here. It is reported
    // in pre-shift coords and the caller applies [shifts] to it (like any
    // other span). Tracking it here as well would shift it twice — e.g. an
    // Enter-continued "2. " marker would stretch the typed newline's span
    // over the marker and beyond the text end (the setSpan crash).
    val typedStartFinal = typedStart
    val typedEndFinal = typedEnd

    fun shiftTracked(shift: RichTextShift) {
        trackStart = mapRichPointThroughShift(trackStart, shift.changeStart, shift.changeEndOld, shift.diff)
            .coerceIn(0, sb.length)
        trackEnd = mapRichPointThroughShift(trackEnd, shift.changeStart, shift.changeEndOld, shift.diff)
            .coerceIn(0, sb.length)
        for (i in markerSpans.indices) {
            val m = markerSpans[i]
            val ns = mapRichPointThroughShift(m.start, shift.changeStart, shift.changeEndOld, shift.diff)
                .coerceIn(0, sb.length)
            val ne = mapRichPointThroughShift(m.end, shift.changeStart, shift.changeEndOld, shift.diff)
                .coerceIn(ns, sb.length)
            markerSpans[i] = m.copy(start = ns, end = ne)
        }
    }

    val splitIndex = oldBounds
        .indexOfFirst { it.start <= changeStart && changeStart <= it.end }
        .takeIf { it >= 0 } ?: (oldBounds.size - 1)
    if (diff > 0 && insertedText.contains('\n')) {
        val added = insertedText.count { it == '\n' }
        val source = oldAttrs.getOrElse(splitIndex) { SharedPdfRichParagraph() }
        // Empty marker-only source exits the list (Samsung empty-item rule).
        val sourceContentStart = oldContentStartOf(splitIndex)
        val sourceMarkerLen = richMarkerLengthAt(oldText, sourceContentStart)
        val sourceContent = if (sourceMarkerLen > 0) {
            oldText.substring(
                (sourceContentStart + sourceMarkerLen).coerceAtMost(oldText.length),
                oldBounds[splitIndex].end.coerceIn(0, oldText.length),
            )
        } else {
            oldText.substring(
                sourceContentStart.coerceAtMost(oldText.length),
                oldBounds[splitIndex].end.coerceIn(0, oldText.length),
            )
        }
        val exitList = source.listType != SharedPdfRichListType.NONE && sourceContent.isEmpty()
        val atVeryEnd = changeStart + insertedText.length >= working.length
        repeat(added) {
            val inherited = when {
                exitList -> SharedPdfRichParagraph(
                    alignment = source.alignment,
                    listType = SharedPdfRichListType.NONE,
                )
                !inheritList && atVeryEnd -> SharedPdfRichParagraph(
                    alignment = source.alignment,
                    listType = SharedPdfRichListType.NONE,
                )
                else -> source
            }
            newAttrs.add((splitIndex + 1).coerceIn(0, newAttrs.size), inherited)
        }
        if (exitList) {
            newAttrs[splitIndex] = source.copy(listType = SharedPdfRichListType.NONE)
            val newSourceStart = richParagraphBounds(sb.toString())[splitIndex].start +
                if (splitIndex == 0) contentOffset else 0
            val len = richMarkerLengthAt(sb.toString(), newSourceStart)
            if (len > 0) {
                sb.deleteRange(newSourceStart, newSourceStart + len)
                val shift = RichTextShift(newSourceStart, newSourceStart + len, -len)
                shifts += shift
                shiftTracked(shift)
            }
        }
    } else if (diff < 0 && deletedText.contains('\n')) {
        // Merge: each deleted newline removes one paragraph; the survivor
        // keeps the FIRST paragraph's attributes. Dropped list paragraphs
        // lose their markers at known offsets (no sniffing).
        val removedNewlines = deletedText.count { it == '\n' }
        val survIndex = oldBounds
            .indexOfFirst { it.start <= changeStart && changeStart <= it.end }
            .takeIf { it >= 0 } ?: (oldBounds.size - 1)
        // Back-to-front so offsets stay valid.
        for (k in removedNewlines downTo 1) {
            val removedIndex = survIndex + k
            val attrs = newAttrs.getOrNull(removedIndex)
            if (removedIndex < newAttrs.size) newAttrs.removeAt(removedIndex)
            if (attrs == null || attrs.listType == SharedPdfRichListType.NONE) continue
            val oldRemovedStart = oldBounds.getOrNull(removedIndex)?.start
                ?.plus(if (removedIndex == 0) contentOffset else 0)
                ?: continue
            val markerLenOld = richMarkerLengthAt(oldText, oldRemovedStart)
            if (markerLenOld == 0) continue
            val markerEndOld = oldRemovedStart + markerLenOld
            if (markerEndOld <= changeStart || oldRemovedStart >= changeEndOld) {
                // Marker fully outside the deleted span: it survives, shifted.
                val survivingAt = oldRemovedStart + diff
                if (survivingAt < 0 || survivingAt >= sb.length) continue
                val currentLen = richMarkerLengthAt(sb.toString(), survivingAt)
                if (currentLen == 0) continue
                val curBounds = richParagraphBounds(sb.toString())
                var atParaStart = false
                curBounds.forEachIndexed { boundIndex, bound ->
                    val paraStart = bound.start + (if (boundIndex == 0) contentOffset else 0)
                    if (paraStart == survivingAt) {
                        atParaStart = true
                    }
                }
                if (!atParaStart) {
                    sb.deleteRange(survivingAt, survivingAt + currentLen)
                    val shift = RichTextShift(survivingAt, survivingAt + currentLen, -currentLen)
                    shifts += shift
                    shiftTracked(shift)
                }
            } else {
                // Marker overlapped the deletion: drop the tag (done via
                // removal above) and delete any surviving head fragment.
                val headLen = (changeStart - oldRemovedStart).coerceIn(0, markerLenOld)
                val fragAt = changeStart.coerceIn(0, sb.length)
                if (headLen > 0 && fragAt + headLen <= sb.length &&
                    richMarkerLengthAt(sb.toString(), fragAt) > 0
                ) {
                    // Only a remnant: the paragraph already lost its list type.
                    sb.deleteRange(fragAt, fragAt + headLen)
                    val shift = RichTextShift(fragAt, fragAt + headLen, -headLen)
                    shifts += shift
                    shiftTracked(shift)
                }
            }
        }
        // Exit-list rule for the survivor if its own marker was touched.
        val survContentStart = oldContentStartOf(survIndex).coerceAtMost(oldText.length)
        val survMarkerLen = richMarkerLengthAt(oldText, survContentStart)
        if (survMarkerLen > 0 &&
            survContentStart < changeEndOld && survContentStart + survMarkerLen > changeStart &&
            newAttrs.indices.contains(survIndex) &&
            newAttrs[survIndex].listType != SharedPdfRichListType.NONE
        ) {
            newAttrs[survIndex] = newAttrs[survIndex].copy(listType = SharedPdfRichListType.NONE)
            val atPoint = changeStart.coerceIn(0, sb.length)
            val fragLen = richMarkerLengthAt(sb.toString(), atPoint)
            if (fragLen > 0) {
                sb.deleteRange(atPoint, atPoint + fragLen)
                val shift = RichTextShift(atPoint, atPoint + fragLen, -fragLen)
                shifts += shift
                shiftTracked(shift)
            }
        }
    } else if (diff < 0) {
        // Same-line deletion: exit the list if the marker was touched.
        val paraIndex = oldBounds
            .indexOfFirst { it.start <= changeStart && changeStart <= it.end }
            .takeIf { it >= 0 } ?: (oldBounds.size - 1)
        val contentStart = oldContentStartOf(paraIndex).coerceAtMost(oldText.length)
        val markerLen = richMarkerLengthAt(oldText, contentStart)
        if (markerLen > 0 &&
            contentStart < changeEndOld && contentStart + markerLen > changeStart &&
            newAttrs.indices.contains(paraIndex) &&
            newAttrs[paraIndex].listType != SharedPdfRichListType.NONE
        ) {
            newAttrs[paraIndex] = newAttrs[paraIndex].copy(listType = SharedPdfRichListType.NONE)
            val atPoint = changeStart.coerceIn(0, sb.length)
            val fragLen = richMarkerLengthAt(sb.toString(), atPoint)
            if (fragLen > 0) {
                sb.deleteRange(atPoint, atPoint + fragLen)
                val shift = RichTextShift(atPoint, atPoint + fragLen, -fragLen)
                shifts += shift
                shiftTracked(shift)
            }
        }
    }

    // --- 3. Ensure markers for listed paragraphs (skip continuation head).
    var currentBounds = richParagraphBounds(sb.toString())
    while (newAttrs.size < currentBounds.size) newAttrs += SharedPdfRichParagraph()
    while (newAttrs.size > currentBounds.size) newAttrs.removeAt(newAttrs.size - 1)
    var runCount = 0
    currentBounds.forEachIndexed { index, bound ->
        val attrs = newAttrs[index]
        if (attrs.listType == SharedPdfRichListType.NONE) {
            runCount = 0
            return@forEachIndexed
        }
        if (index == 0 && !firstStartsAtParagraphStart) return@forEachIndexed
        val contentStart = bound.start + if (index == 0) contentOffset else 0
        if (attrs.listType == SharedPdfRichListType.BULLET) {
            runCount = 0
            if (richMarkerLengthAt(sb.toString(), contentStart) == 0) {
                sb.insert(contentStart, SHARED_PDF_RICH_BULLET_MARKER)
                val shift = RichTextShift(contentStart, contentStart, SHARED_PDF_RICH_BULLET_MARKER.length)
                shifts += shift
                shiftTracked(shift)
                markerSpans += RichMarkerSpan(
                    start = contentStart,
                    end = contentStart + SHARED_PDF_RICH_BULLET_MARKER.length,
                    style = markerStyleFor(index),
                )
                currentBounds = richParagraphBounds(sb.toString())
            }
        } else {
            runCount += 1
            val want = sharedPdfRichNumberedMarker(runCount)
            val existingLen = richMarkerLengthAt(sb.toString(), contentStart)
            val existing = if (existingLen > 0) {
                sb.toString().substring(contentStart, contentStart + existingLen)
            } else {
                ""
            }
            if (existing != want) {
                if (existingLen > 0) {
                    sb.deleteRange(contentStart, contentStart + existingLen)
                    sb.insert(contentStart, want)
                    val shift = RichTextShift(contentStart, contentStart + existingLen, want.length - existingLen)
                    shifts += shift
                    shiftTracked(shift)
                } else {
                    sb.insert(contentStart, want)
                    val shift = RichTextShift(contentStart, contentStart, want.length)
                    shifts += shift
                    shiftTracked(shift)
                }
                markerSpans += RichMarkerSpan(
                    start = contentStart,
                    end = contentStart + want.length,
                    style = markerStyleFor(index),
                )
                currentBounds = richParagraphBounds(sb.toString())
            }
        }
    }

    val finalText = sb.toString()
    while (newAttrs.size < richParagraphBounds(finalText).size) newAttrs += SharedPdfRichParagraph()
    return NormalizedRichParagraphEdit(
        text = finalText,
        selection = TextRange(trackStart.coerceIn(0, finalText.length), trackEnd.coerceIn(0, finalText.length)),
        effectiveShift = effectiveShift,
        shifts = shifts,
        markerSpans = markerSpans.filter { it.start < it.end && it.end <= finalText.length },
        paragraphs = newAttrs,
        typedStart = typedStartFinal,
        typedEnd = typedEndFinal,
    )
}

/**
 * Toggles [type] on every paragraph intersecting [selection]: if all of
 * them already have it, they go plain; otherwise they all become [type].
 * Alignment is preserved. Markers are inserted/removed and numbered runs
 * renumbered, all reported as shifts + marker spans.
 */
fun toggleRichParagraphList(
    annotated: AnnotatedString,
    selection: TextRange,
    type: SharedPdfRichListType,
    contentOffset: Int = 0,
    firstStartsAtParagraphStart: Boolean = true,
    markerFallbackStyle: SpanStyle = SpanStyle(),
): NormalizedRichParagraphEdit {
    require(type != SharedPdfRichListType.NONE)
    val start = selection.min.coerceIn(0, annotated.length)
    val end = selection.max.coerceIn(start, annotated.length)
    val bounds = richParagraphBounds(annotated.text)
    if (bounds.isEmpty()) {
        return NormalizedRichParagraphEdit(
            text = annotated.text, selection = selection, effectiveShift = RichTextShift(0, 0, 0),
            shifts = emptyList(), markerSpans = emptyList(),
            paragraphs = emptyList(), typedStart = -1, typedEnd = -1,
        )
    }
    val firstIndex = bounds.indexOfLast { it.start <= start }.coerceAtLeast(0)
    val lastIndex = bounds.indexOfLast { it.start <= end }.coerceAtLeast(firstIndex)
    val oldAttrs = readRichParagraphs(annotated)
    val oldStyles: List<SpanStyle?> = bounds.mapIndexed { index, bound ->
        if (annotated.length == 0) {
            null
        } else {
            val probe = (bound.start + if (index == 0) contentOffset else 0)
                .coerceIn(0, annotated.length - 1)
            annotated.spanStyles.firstOrNull { it.start <= probe && probe < it.end }?.item
        }
    }
    val allSet = (firstIndex..lastIndex).all { oldAttrs[it].listType == type }
    val next = if (allSet) SharedPdfRichListType.NONE else type
    val attrs = oldAttrs.toMutableList()
    for (i in firstIndex..lastIndex) {
        if (i == 0 && !firstStartsAtParagraphStart && next != SharedPdfRichListType.NONE) continue
        attrs[i] = attrs[i].copy(listType = next)
    }
    val sb = StringBuilder(annotated.text)
    val shifts = mutableListOf<RichTextShift>()
    val markerSpans = mutableListOf<RichMarkerSpan>()
    var trackStart = start
    var trackEnd = end

    fun shiftTracked(shift: RichTextShift) {
        trackStart = mapRichPointThroughShift(trackStart, shift.changeStart, shift.changeEndOld, shift.diff)
        trackEnd = mapRichPointThroughShift(trackEnd, shift.changeStart, shift.changeEndOld, shift.diff)
        for (i in markerSpans.indices) {
            val m = markerSpans[i]
            markerSpans[i] = m.copy(
                start = mapRichPointThroughShift(m.start, shift.changeStart, shift.changeEndOld, shift.diff),
                end = mapRichPointThroughShift(m.end, shift.changeStart, shift.changeEndOld, shift.diff),
            )
        }
    }

    // Remove markers first (back-to-front), then insert/renumber (front-to-back).
    for (i in lastIndex downTo firstIndex) {
        if (oldAttrs[i].listType == attrs[i].listType) continue
        val curBounds = richParagraphBounds(sb.toString())
        if (i >= curBounds.size) continue
        val contentStart = curBounds[i].start + if (i == 0) contentOffset else 0
        // The paragraph just lost its list type, so a managed marker at its
        // start is ours to remove.
        val len = richMarkerLengthAt(sb.toString(), contentStart)
        if (len > 0) {
            sb.deleteRange(contentStart, contentStart + len)
            val shift = RichTextShift(contentStart, contentStart + len, -len)
            shifts += shift
            shiftTracked(shift)
        }
    }
    var runCount = 0
    var curBounds = richParagraphBounds(sb.toString())
    while (attrs.size < curBounds.size) attrs += SharedPdfRichParagraph()
    for (i in curBounds.indices) {
        val a = attrs.getOrElse(i) { SharedPdfRichParagraph() }
        if (a.listType == SharedPdfRichListType.NONE) {
            runCount = 0
            continue
        }
        if (i == 0 && !firstStartsAtParagraphStart) continue
        val contentStart = curBounds[i].start + if (i == 0) contentOffset else 0
        val style = oldStyles.getOrElse(i) { null } ?: markerFallbackStyle
        if (a.listType == SharedPdfRichListType.BULLET) {
            runCount = 0
            if (richMarkerLengthAt(sb.toString(), contentStart) == 0) {
                sb.insert(contentStart, SHARED_PDF_RICH_BULLET_MARKER)
                val shift = RichTextShift(contentStart, contentStart, SHARED_PDF_RICH_BULLET_MARKER.length)
                shifts += shift
                shiftTracked(shift)
                markerSpans += RichMarkerSpan(contentStart, contentStart + SHARED_PDF_RICH_BULLET_MARKER.length, style)
                curBounds = richParagraphBounds(sb.toString())
            }
        } else {
            runCount += 1
            val want = sharedPdfRichNumberedMarker(runCount)
            val existingLen = richMarkerLengthAt(sb.toString(), contentStart)
            val existing = if (existingLen > 0) sb.toString().substring(contentStart, contentStart + existingLen) else ""
            if (existing != want) {
                if (existingLen > 0) {
                    sb.deleteRange(contentStart, contentStart + existingLen)
                    sb.insert(contentStart, want)
                    val shift = RichTextShift(contentStart, contentStart + existingLen, want.length - existingLen)
                    shifts += shift
                    shiftTracked(shift)
                } else {
                    sb.insert(contentStart, want)
                    val shift = RichTextShift(contentStart, contentStart, want.length)
                    shifts += shift
                    shiftTracked(shift)
                }
                markerSpans += RichMarkerSpan(contentStart, contentStart + want.length, style)
                curBounds = richParagraphBounds(sb.toString())
            }
        }
    }

    val finalText = sb.toString()
    return NormalizedRichParagraphEdit(
        text = finalText,
        selection = TextRange(trackStart.coerceIn(0, finalText.length), trackEnd.coerceIn(0, finalText.length)),
        effectiveShift = RichTextShift(0, 0, 0),
        shifts = shifts,
        markerSpans = markerSpans.filter { it.start < it.end && it.end <= finalText.length },
        paragraphs = attrs,
        typedStart = -1,
        typedEnd = -1,
    )
}

/**
 * Sets [align] on every paragraph intersecting [selection], then re-emits
 * every alignment run (merged) so the result never carries fragmented
 * per-paragraph ranges. Text characters and selection are untouched.
 */
fun setRichParagraphAlignment(
    annotated: AnnotatedString,
    selection: TextRange,
    align: SharedPdfRichTextAlign,
): AnnotatedString {
    // Normalize off any prior EOF anchor so bounds/selection match raw text.
    val rawText = annotated.text.withoutRichAlignAnchors()
    val hadAnchor = rawText.length != annotated.text.length
    val base = if (hadAnchor) {
        val b = AnnotatedString.Builder(rawText)
        annotated.spanStyles.forEach {
            val s = it.start.coerceIn(0, rawText.length)
            val e = it.end.coerceIn(s, rawText.length)
            if (s < e) b.addStyle(it.item, s, e)
        }
        annotated.getStringAnnotations(0, annotated.length).forEach {
            val s = it.start.coerceIn(0, rawText.length)
            val e = it.end.coerceIn(s, rawText.length)
            if (s < e) b.addStringAnnotation(it.tag, it.item, s, e)
        }
        b.toAnnotatedString()
    } else {
        annotated
    }

    val bounds = richParagraphBounds(base.text)
    if (bounds.isEmpty()) return base
    val start = selection.min.coerceIn(0, base.length)
    val end = selection.max.coerceIn(start, base.length)
    val firstIndex = bounds.indexOfLast { it.start <= start }.coerceAtLeast(0)
    val lastIndex = bounds.indexOfLast { it.start <= end }.coerceAtLeast(firstIndex)

    val paragraphs = bounds.mapIndexed { index, bound ->
        val inSelection = index in firstIndex..lastIndex
        val alignment = if (inSelection) {
            align
        } else {
            readRichParagraphAlignment(base, bound.start, 0, index == 0)
        }
        val listType = readRichParagraphListType(
            base, bound.start, bound.end, 0, index == 0,
        )
        SharedPdfRichParagraph(alignment = alignment, listType = listType)
    }

    val fresh = AnnotatedString.Builder(base.text)
    base.spanStyles.forEach { fresh.addStyle(it.item, it.start, it.end) }
    base.getStringAnnotations(0, base.length).forEach {
        fresh.addStringAnnotation(it.tag, it.item, it.start, it.end)
    }
    applyRichParagraphsToBuilder(fresh, base.text, paragraphs)
    val result = fresh.toAnnotatedString().withTrailingRichAlignAnchorIfNeeded(paragraphs)
    val runs = richParagraphAlignRuns(base.text, paragraphs)
    SharedPdfRichLayoutDiag.d(
        "align.free align=$align sel=$start..$end paras=$firstIndex..$lastIndex " +
            "bounds=${bounds.size} paraStylesIn=${base.paragraphStyles.size} " +
            "paraStylesOut=${result.paragraphStyles.size} " +
            "anchor=${result.text.endsWith(SHARED_PDF_RICH_ALIGN_ANCHOR)} " +
            "runs=${runs.joinToString(";") { r -> "${r.start}..${r.end}:${r.alignment}" }} " +
            "newlines=${base.text.count { it == '\n' }}"
    )
    return result
}

/**
 * Global safety-net pass: ensures every listed paragraph starts with the
 * correct marker, drops tags that no longer sit at a paragraph start
 * (removing their orphaned marker text at the known offset), and renumbers
 * numbered runs. Used after sync merges and page deletes.
 */
fun normalizeRichParagraphsGlobal(
    annotated: AnnotatedString,
    markerFallbackStyle: SpanStyle = SpanStyle(),
): NormalizedRichParagraphEdit {
    val sb = StringBuilder(annotated.text)
    val shifts = mutableListOf<RichTextShift>()
    val markerSpans = mutableListOf<RichMarkerSpan>()
    var bounds = richParagraphBounds(sb.toString())

    // 1. Drop orphan tags (not at a paragraph start) + their marker text.
    val orphans = annotated.getStringAnnotations(
        tag = SHARED_PDF_RICH_LIST_TAG, start = 0, end = annotated.length,
    ).filter { tag ->
        bounds.none { it.start == tag.start }
    }.sortedByDescending { it.start }
    for (tag in orphans) {
        if (tag.start < 0 || tag.start > sb.length) continue
        val len = richMarkerLengthAt(sb.toString(), tag.start)
        if (len > 0) {
            sb.deleteRange(tag.start, tag.start + len)
            shifts += RichTextShift(tag.start, tag.start + len, -len)
            bounds = richParagraphBounds(sb.toString())
        }
    }
    // Rebuild intermediate state (spans/styles surviving tags shifted).
    val rebuilt = AnnotatedString.Builder(sb.toString())
    annotated.spanStyles.forEach {
        shiftRichRange(it.start, it.end, shifts)?.let { (s, e) -> rebuilt.addStyle(it.item, s, e) }
    }
    annotated.paragraphStyles.forEach {
        shiftRichRange(it.start, it.end, shifts)?.let { (s, e) -> rebuilt.addStyle(it.item, s, e) }
    }
    annotated.getStringAnnotations(0, annotated.length).forEach { tag ->
        if (tag.tag == SHARED_PDF_RICH_LIST_TAG &&
            orphans.any { o -> o.start == tag.start && o.end == tag.end && o.item == tag.item }
        ) {
            return@forEach
        }
        shiftRichRange(tag.start, tag.end, shifts)?.let { (s, e) ->
            rebuilt.addStringAnnotation(tag.tag, tag.item, s, e)
        }
    }
    val shifted = rebuilt.toAnnotatedString()
    val freshAttrs = readRichParagraphs(shifted).toMutableList()
    bounds = richParagraphBounds(sb.toString())

    // 2. Ensure markers + renumber.
    var runCount = 0
    for (i in bounds.indices) {
        val a = freshAttrs.getOrElse(i) { SharedPdfRichParagraph() }
        if (a.listType == SharedPdfRichListType.NONE) {
            runCount = 0
            continue
        }
        val contentStart = bounds[i].start
        val style = shifted.spanStyles.firstOrNull { it.start <= contentStart && contentStart < it.end }?.item
            ?: markerFallbackStyle
        if (a.listType == SharedPdfRichListType.BULLET) {
            runCount = 0
            if (richMarkerLengthAt(sb.toString(), contentStart) == 0) {
                sb.insert(contentStart, SHARED_PDF_RICH_BULLET_MARKER)
                shifts += RichTextShift(contentStart, contentStart, SHARED_PDF_RICH_BULLET_MARKER.length)
                markerSpans += RichMarkerSpan(contentStart, contentStart + SHARED_PDF_RICH_BULLET_MARKER.length, style)
                bounds = richParagraphBounds(sb.toString())
            }
        } else {
            runCount += 1
            val want = sharedPdfRichNumberedMarker(runCount)
            val existingLen = richMarkerLengthAt(sb.toString(), contentStart)
            val existing = if (existingLen > 0) sb.toString().substring(contentStart, contentStart + existingLen) else ""
            if (existing != want) {
                if (existingLen > 0) {
                    sb.deleteRange(contentStart, contentStart + existingLen)
                    sb.insert(contentStart, want)
                    shifts += RichTextShift(contentStart, contentStart + existingLen, want.length - existingLen)
                } else {
                    sb.insert(contentStart, want)
                    shifts += RichTextShift(contentStart, contentStart, want.length)
                }
                markerSpans += RichMarkerSpan(contentStart, contentStart + want.length, style)
                bounds = richParagraphBounds(sb.toString())
            }
        }
    }

    val finalText = sb.toString()
    while (freshAttrs.size < bounds.size) freshAttrs += SharedPdfRichParagraph()
    return NormalizedRichParagraphEdit(
        text = finalText,
        selection = TextRange(0, 0),
        effectiveShift = RichTextShift(0, 0, 0),
        shifts = shifts,
        markerSpans = markerSpans.filter { it.start < it.end && it.end <= finalText.length },
        paragraphs = freshAttrs,
        typedStart = -1,
        typedEnd = -1,
    )
}

/** Drops trailing all-default paragraphs so plain documents stay minimal. */
fun List<SharedPdfRichParagraph>.trimmedRichParagraphs(): List<SharedPdfRichParagraph> {
    val lastNonDefault = indexOfLast {
        it.alignment != SharedPdfRichTextAlign.LEFT || it.listType != SharedPdfRichListType.NONE
    }
    return if (lastNonDefault < 0) emptyList() else take(lastNonDefault + 1)
}

/** Maps one offset through a single text shift (insert / delete / replace). */
fun mapRichPointThroughShift(offset: Int, changeStart: Int, changeEndOld: Int, diff: Int): Int {
    return when {
        offset < changeStart -> offset
        offset >= changeEndOld -> offset + diff
        // Strictly inside a delete/replace: collapse to the change start.
        // (Pure insertions have changeEndOld == changeStart, so a cursor at
        // the insertion point lands AFTER the inserted text — e.g. after a
        // freshly continued list marker.)
        else -> changeStart
    }
}

/** Maps an offset through a list of shifts in order. */
fun adjustRichOffset(offset: Int, shifts: List<RichTextShift>): Int {
    var result = offset
    for (shift in shifts) {
        result = mapRichPointThroughShift(result, shift.changeStart, shift.changeEndOld, shift.diff)
    }
    return result
}

/** Shifts one range through a list of shifts; null when collapsed away. */
fun shiftRichRange(start: Int, end: Int, shifts: List<RichTextShift>): Pair<Int, Int>? {
    var s = start
    var e = end
    for (shift in shifts) {
        s = mapRichPointThroughShift(s, shift.changeStart, shift.changeEndOld, shift.diff)
        e = mapRichPointThroughShift(e, shift.changeStart, shift.changeEndOld, shift.diff)
    }
    return if (s < e) s to e else null
}
