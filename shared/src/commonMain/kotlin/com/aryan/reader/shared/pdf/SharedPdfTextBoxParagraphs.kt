package com.aryan.reader.shared.pdf

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Text-box paragraph support (Android benchmark, shared-first).
 *
 * Reuses the rich-text paragraph model ([SharedPdfRichParagraph]) so text
 * boxes behave the same way: LEFT/CENTER/RIGHT alignment + BULLET/NUMBERED
 * lists with managed markers ("• ", "1. ") stored in the box text itself.
 * No JUSTIFY — same scope as rich text.
 *
 * Persistence is sparse like [SharedPdfRichDocument]: only non-default
 * paragraphs are stored as [{p, a, l}] where a: 1=center, 2=right and
 * l: 1=bullet, 2=numbered. Missing/empty -> all LEFT/NONE.
 */

/** Sparse encode; null when every paragraph is default (keeps old payloads byte-compatible). */
fun sharedPdfTextBoxParagraphsToJson(paragraphs: List<SharedPdfRichParagraph>): JsonArray? {
    val textParagraphCount = paragraphs.size
    val nonDefault = paragraphs.mapIndexedNotNull { index, paragraph ->
        if (paragraph.alignment == SharedPdfRichTextAlign.LEFT &&
            paragraph.listType == SharedPdfRichListType.NONE
        ) {
            null
        } else {
            JsonObject(
                buildMap {
                    put("p", JsonPrimitive(index))
                    if (paragraph.alignment != SharedPdfRichTextAlign.LEFT) {
                        put(
                            "a",
                            JsonPrimitive(
                                when (paragraph.alignment) {
                                    SharedPdfRichTextAlign.CENTER -> 1
                                    SharedPdfRichTextAlign.RIGHT -> 2
                                    SharedPdfRichTextAlign.LEFT -> 0
                                }
                            )
                        )
                    }
                    if (paragraph.listType != SharedPdfRichListType.NONE) {
                        put(
                            "l",
                            JsonPrimitive(
                                when (paragraph.listType) {
                                    SharedPdfRichListType.BULLET -> 1
                                    SharedPdfRichListType.NUMBERED -> 2
                                    SharedPdfRichListType.NONE -> 0
                                }
                            )
                        )
                    }
                }
            )
        }
    }
    if (nonDefault.isEmpty()) return null
    // Guard against oversized paragraph lists from corrupt payloads.
    if (textParagraphCount <= 0) return JsonArray(nonDefault)
    return JsonArray(nonDefault)
}

/**
 * Sparse decode sized to [paragraphCount] ('\n' count + 1). Unknown values
 * fall back to LEFT/NONE; out-of-range indices are dropped.
 */
fun sharedPdfTextBoxParagraphsFromJson(
    element: kotlinx.serialization.json.JsonElement?,
    paragraphCount: Int,
): List<SharedPdfRichParagraph> {
    if (element == null || element is JsonNull) return emptyList()
    val array = runCatching { element.jsonArray }.getOrNull() ?: return emptyList()
    if (paragraphCount <= 0) return emptyList()
    val paragraphs = MutableList(paragraphCount) { SharedPdfRichParagraph() }
    var hasNonDefault = false
    array.forEach { item ->
        val obj = runCatching { item.jsonObject }.getOrNull() ?: return@forEach
        val index = obj["p"]?.jsonPrimitive?.intOrNull ?: return@forEach
        if (index < 0 || index >= paragraphCount) return@forEach
        val alignment = when (obj["a"]?.jsonPrimitive?.intOrNull ?: 0) {
            1 -> SharedPdfRichTextAlign.CENTER
            2 -> SharedPdfRichTextAlign.RIGHT
            else -> SharedPdfRichTextAlign.LEFT
        }
        val listType = when (obj["l"]?.jsonPrimitive?.intOrNull ?: 0) {
            1 -> SharedPdfRichListType.BULLET
            2 -> SharedPdfRichListType.NUMBERED
            else -> SharedPdfRichListType.NONE
        }
        paragraphs[index] = SharedPdfRichParagraph(alignment = alignment, listType = listType)
        if (alignment != SharedPdfRichTextAlign.LEFT || listType != SharedPdfRichListType.NONE) {
            hasNonDefault = true
        }
    }
    if (!hasNonDefault) return emptyList()
    return paragraphs.trimmedRichParagraphs()
}

/** Paragraph count for [text] ('\n' segments). */
fun sharedPdfTextBoxParagraphCount(text: String): Int = richParagraphBounds(text).size

/**
 * Builds the live [AnnotatedString] for a text box: box-level [baseStyle]
 * covering the whole text plus per-paragraph alignment runs and list tags.
 * Markers live in [text] itself (same as rich text) and flow through.
 */
fun sharedPdfTextBoxAnnotatedString(
    text: String,
    paragraphs: List<SharedPdfRichParagraph>,
    baseStyle: SpanStyle = SpanStyle(),
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val builder = AnnotatedString.Builder(text)
    builder.addStyle(baseStyle, 0, text.length)
    applyRichParagraphsToBuilder(builder, text, paragraphs)
    return builder.toAnnotatedString()
}

/** Reads back per-paragraph attributes from a text-box [AnnotatedString]. */
fun sharedPdfTextBoxParagraphs(annotated: AnnotatedString): List<SharedPdfRichParagraph> {
    if (annotated.text.isEmpty()) return emptyList()
    return readRichParagraphs(annotated).trimmedRichParagraphs()
}

/** Dock state for [selection] in [annotated]; same semantics as rich text. */
fun sharedPdfTextBoxParagraphUiState(
    annotated: AnnotatedString,
    selection: TextRange,
): RichParagraphUiState = richParagraphUiState(annotated, selection)

/**
 * Dock state from stored [text]/[paragraphs] (no anchor buffer).
 *
 * Unlike [sharedPdfTextBoxParagraphUiState], empty and trailing-empty
 * paragraphs report their STORED alignment: an empty box has no style
 * range to read back, so reading the live buffer would always show LEFT
 * after the user picks CENTER on an empty box. Lists need no such help —
 * toggles eagerly materialize markers, so stored and buffer agree.
 */
fun sharedPdfTextBoxDockState(
    text: String,
    paragraphs: List<SharedPdfRichParagraph>,
    selection: TextRange,
): RichParagraphUiState {
    val bounds = richParagraphBounds(text)
    if (bounds.isEmpty()) {
        return RichParagraphUiState(SharedPdfRichTextAlign.LEFT, false, false)
    }
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(start, text.length)
    val firstIndex = bounds.indexOfLast { it.start <= start }.coerceAtLeast(0)
    val lastIndex = bounds.indexOfLast { it.start <= end }.coerceAtLeast(firstIndex)
    val alignment = paragraphs.getOrElse(firstIndex) { SharedPdfRichParagraph() }.alignment
    var bulleted = true
    var numbered = true
    for (i in firstIndex..lastIndex) {
        when (paragraphs.getOrElse(i) { SharedPdfRichParagraph() }.listType) {
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
 * Keystroke entry point for text boxes: runs shared list/alignment
 * maintenance, then preserves STORED alignment for paragraphs that were
 * empty before the keystroke.
 *
 * Rationale: an empty (or trailing-empty) paragraph carries no style range
 * in the anchor-free box buffer, so [applyRichParagraphKeystroke] reads it
 * as LEFT. Without this, picking CENTER on an empty box would be lost on
 * the first typed character. Lists are unaffected — toggles insert markers
 * eagerly, so marker presence governs them exactly like rich text.
 */
fun sharedPdfTextBoxKeystroke(
    oldText: String,
    oldParagraphs: List<SharedPdfRichParagraph>,
    newText: String,
    newSelection: TextRange,
    markerFallbackStyle: SpanStyle = SpanStyle(),
): NormalizedRichParagraphEdit {
    val oldAnnotated = sharedPdfTextBoxAnnotatedString(oldText, oldParagraphs, markerFallbackStyle)
    val result = applyRichParagraphKeystroke(
        old = oldAnnotated,
        newText = newText,
        newSelection = newSelection,
        markerFallbackStyle = markerFallbackStyle,
    )
    if (oldText.isEmpty() && result.text.isNotEmpty()) {
        val stored = oldParagraphs.getOrElse(0) { SharedPdfRichParagraph() }
        if (stored.alignment != SharedPdfRichTextAlign.LEFT) {
            val fixed = result.paragraphs.toMutableList()
            if (fixed.isNotEmpty() && fixed[0].alignment == SharedPdfRichTextAlign.LEFT) {
                fixed[0] = fixed[0].copy(alignment = stored.alignment)
            }
            return result.copy(paragraphs = fixed.trimmedRichParagraphs())
        }
        return result
    }
    val oldBounds = richParagraphBounds(oldText)
    if (oldBounds.isEmpty()) return result
    var changed = false
    val fixed = result.paragraphs.toMutableList()
    while (fixed.size < richParagraphBounds(result.text).size) fixed += SharedPdfRichParagraph()
    oldBounds.forEachIndexed { index, bound ->
        if (bound.start != bound.end) return@forEachIndexed
        val stored = oldParagraphs.getOrElse(index) { SharedPdfRichParagraph() }
        if (stored.alignment == SharedPdfRichTextAlign.LEFT) return@forEachIndexed
        if (index >= fixed.size) return@forEachIndexed
        if (fixed[index].alignment == SharedPdfRichTextAlign.LEFT) {
            fixed[index] = fixed[index].copy(alignment = stored.alignment)
            changed = true
        }
    }
    if (!changed) return result
    return result.copy(paragraphs = fixed.trimmedRichParagraphs())
}

/**
 * Alignment per '\n'-paragraph for exporters (e.g. Android StaticLayout
 * AlignmentSpans). Size == paragraph count; missing entries are LEFT.
 * Public for platform exporters; core bounds stay internal.
 */
fun sharedPdfTextBoxParagraphAlignments(
    text: String,
    paragraphs: List<SharedPdfRichParagraph>,
): List<SharedPdfRichTextAlign> {
    if (text.isEmpty()) return emptyList()
    return richParagraphBounds(text).mapIndexed { index, _ ->
        paragraphs.getOrElse(index) { SharedPdfRichParagraph() }.alignment
    }
}

/** Sets [align] on paragraphs intersecting [selection]; text unchanged. */
fun sharedPdfSetTextBoxAlignment(
    annotated: AnnotatedString,
    selection: TextRange,
    align: SharedPdfRichTextAlign,
): AnnotatedString = setRichParagraphAlignment(annotated, selection, align)

/**
 * Pure paragraph-state setter for text-box alignment (dock path).
 *
 * Unlike [sharedPdfSetTextBoxAlignment] (annotated round-trip for the
 * retired page editor), this never touches text and never appends the
 * ZWSP EOF anchor: empty paragraphs have no style range to read back,
 * so the annotated round-trip silently drops alignment set on an empty
 * box/line. Stored state is resized to the '\n'-paragraph count
 * (missing entries are LEFT) and trimmed like every other producer.
 */
fun sharedPdfSetTextBoxAlignmentState(
    text: String,
    paragraphs: List<SharedPdfRichParagraph>,
    selection: TextRange,
    align: SharedPdfRichTextAlign,
): List<SharedPdfRichParagraph> {
    val bounds = richParagraphBounds(text)
    if (bounds.isEmpty()) return paragraphs.trimmedRichParagraphs()
    val start = selection.min.coerceIn(0, text.length)
    val end = selection.max.coerceIn(start, text.length)
    val firstIndex = bounds.indexOfLast { it.start <= start }.coerceAtLeast(0)
    val lastIndex = bounds.indexOfLast { it.start <= end }.coerceAtLeast(firstIndex)
    val resized = MutableList(bounds.size) { index ->
        paragraphs.getOrElse(index) { SharedPdfRichParagraph() }
    }
    for (i in firstIndex..lastIndex) {
        resized[i] = resized[i].copy(alignment = align)
    }
    return resized.trimmedRichParagraphs()
}

/** Toggles [type] on paragraphs intersecting [selection]; manages markers. */
fun sharedPdfToggleTextBoxList(
    annotated: AnnotatedString,
    selection: TextRange,
    type: SharedPdfRichListType,
    markerFallbackStyle: SpanStyle = SpanStyle(),
): NormalizedRichParagraphEdit = toggleRichParagraphList(
    annotated = annotated,
    selection = selection,
    type = type,
    markerFallbackStyle = markerFallbackStyle,
)

/**
 * One-shot post-toggle cursor for a text-box editor (mobile shared UI).
 *
 * The dock list toggle inserts markers around the field, so the correct
 * cursor (shifted past "• "/"1. ") is computed parent-side. A plain
 * [TextRange] mirror cannot be used: it goes stale within a frame (the
 * mirror only learns the field's own reports) and re-adopting it yanks
 * the cursor. The [token] is bumped per toggle and consumed once.
 */
data class SharedPdfTextBoxPendingSelection(
    val range: TextRange,
    val token: Long,
)
