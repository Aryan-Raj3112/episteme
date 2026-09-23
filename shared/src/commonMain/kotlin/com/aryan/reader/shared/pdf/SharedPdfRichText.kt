package com.aryan.reader.shared.pdf

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.aryan.reader.pdf.PdfPageIdentity
import com.aryan.reader.pdf.buildSharedPdfPageIndexMapping
import com.aryan.reader.shared.currentTimestamp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

const val SHARED_PDF_PAGE_BREAK_CHAR: Char = '\u000C'

private const val SHARED_PDF_ZWSP = "\u200B"
private const val SHARED_PDF_RICH_FONT_PATH_TAG = "pdf-rich-font-path"
internal const val SHARED_PDF_RICH_TEXT_SAVE_DEBOUNCE_MILLIS = 500L
internal const val SHARED_PDF_RICH_TEXT_MAX_SAVE_LATENCY_MILLIS = 3_000L

fun sharedPdfRichTextSelectionBounds(
    selectionStart: Int,
    selectionEnd: Int,
    textLength: Int
): Pair<Int, Int>? {
    val safeLength = textLength.coerceAtLeast(0)
    val localStart = minOf(selectionStart, selectionEnd).coerceIn(0, safeLength)
    val localEnd = maxOf(selectionStart, selectionEnd).coerceIn(0, safeLength)
    return if (localStart < localEnd) localStart to localEnd else null
}

fun shouldUpdateSharedPdfRichTextLayoutConfig(
    previousWidth: Float,
    previousHeight: Float,
    previousDensity: Density?,
    width: Float,
    height: Float,
    density: Density
): Boolean {
    return previousWidth != width || previousHeight != height || previousDensity != density
}

object SharedPdfRichTextLog {
    var enabled: Boolean = true

    /**
     * Device-log forwarder (Android wires this to logcat under PdfRichCursor
     * so page rich-text cursor/list/alignment issues are diagnosable on
     * device). Null keeps logging compiled out. The message strings are
     * built by callers either way.
     */
    var forwarder: ((String) -> Unit)? = null

    fun d(message: String) {
        if (enabled) {
            forwarder?.invoke(message)
        }
    }
}

/**
 * Dedicated diagnostics for exit-edit page collapse and post-alignment
 * line-height regressions. Android wires [forwarder] to logcat tag
 * PdfRichLayoutDiag so both bugs share one filterable tag.
 */
object SharedPdfRichLayoutDiag {
    var enabled: Boolean = true
    var forwarder: ((String) -> Unit)? = null

    /** Bumped whenever pageLayouts is replaced or layout config is accepted. */
    var epoch: Int = 0
        private set

    fun nextEpoch(): Int {
        epoch += 1
        return epoch
    }

    fun d(message: String) {
        if (enabled) {
            forwarder?.invoke("e=$epoch $message")
        }
    }
}

data class SharedPdfRichSpan(
    val start: Int,
    val end: Int,
    val color: Int,
    val backgroundColor: Int,
    val fontSizeNorm: Float,
    val isBold: Boolean,
    val isItalic: Boolean,
    val isUnderline: Boolean,
    val isStrikethrough: Boolean,
    val fontPath: String? = null
)

data class SharedPdfRichDocument(
    val text: String = "",
    val spans: List<SharedPdfRichSpan> = emptyList(),
    /**
     * Per-'\n'-paragraph attributes (alignment + list), indexed by paragraph.
     * Missing entries (and missing lists) mean defaults; the serializer
     * stores only non-default paragraphs sparsely.
     */
    val paragraphs: List<SharedPdfRichParagraph> = emptyList()
)

data class SharedPdfRichPageLayout(
    val pageIndex: Int,
    val visibleText: AnnotatedString,
    val globalStartIndex: Int,
    val globalEndIndex: Int,
    val pageHeightPx: Float
)

/**
 * Page owning a global cursor offset. Adjacent ranges share boundary chars
 * (page 0 ends where page 1 starts, around the explicit break): when the
 * cursor sits exactly on a shared boundary, the later page owns it if it
 * has content after the cursor; otherwise the earlier page does (an
 * end-of-text cursor stays on the content page, not the trailing blank).
 * A first-match `start <= pos <= end` scan would yank boundary cursors to
 * the earlier page (e.g. toggling a list on page 1 jumps to page 0).
 */
internal fun selectRichPageLayoutForCursor(
    layouts: List<SharedPdfRichPageLayout>,
    pos: Int,
): SharedPdfRichPageLayout? {
    var fallback: SharedPdfRichPageLayout? = null
    for (layout in layouts) {
        if (pos < layout.globalStartIndex || pos > layout.globalEndIndex) continue
        if (layout.globalEndIndex > pos) return layout
        if (fallback == null) fallback = layout
    }
    return fallback
}

object SharedPdfRichTextSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun encode(document: SharedPdfRichDocument): String {
        return json.encodeToString(
            JsonElement.serializer(),
            encodeElement(document)
        )
    }

    fun encodeElement(document: SharedPdfRichDocument): JsonElement {
        return JsonObject(
            buildMap {
                put("text", JsonPrimitive(document.text))
                put(
                    "spans",
                    JsonArray(
                        document.spans.map { span ->
                            JsonObject(
                                buildMap {
                                    put("s", JsonPrimitive(span.start))
                                    put("e", JsonPrimitive(span.end))
                                    put("c", JsonPrimitive(span.color))
                                    put("bg", JsonPrimitive(span.backgroundColor))
                                    put("sz", JsonPrimitive(span.fontSizeNorm.toDouble()))
                                    put("b", JsonPrimitive(span.isBold))
                                    put("i", JsonPrimitive(span.isItalic))
                                    put("u", JsonPrimitive(span.isUnderline))
                                    put("st", JsonPrimitive(span.isStrikethrough))
                                    span.fontPath?.let { put("fp", JsonPrimitive(it)) }
                                }
                            )
                        }
                    )
                )
                // Sparse paragraph attributes: only non-default paragraphs.
                // a: 1=center, 2=right (left omitted); l: 1=bullet, 2=numbered.
                val nonDefault = document.paragraphs.mapIndexedNotNull { index, paragraph ->
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
                if (nonDefault.isNotEmpty()) {
                    put("paragraphs", JsonArray(nonDefault))
                }
            }
        )
    }

    fun decode(raw: String): SharedPdfRichDocument {
        if (raw.isBlank()) {
            SharedPdfRichTextLog.d("serializer.decode blank -> empty document")
            return SharedPdfRichDocument()
        }
        return runCatching {
            decodeElement(json.parseToJsonElement(raw))
        }.onFailure {
            SharedPdfRichTextLog.d("serializer.decode failed rawLen=${raw.length} error=${it.message}")
        }.getOrDefault(SharedPdfRichDocument())
    }

    fun decodeElement(element: JsonElement): SharedPdfRichDocument {
        val root = element.jsonObject
        val text = requireNotNull(root.requiredString("text"))
        val spans = requireNotNull(root["spans"]?.jsonArrayOrNull()).map { spanElement ->
                val obj = requireNotNull(spanElement.jsonObjectOrNull())
                SharedPdfRichSpan(
                    start = requireNotNull(obj.int("s")),
                    end = requireNotNull(obj.int("e")),
                    color = requireNotNull(obj.int("c")),
                    backgroundColor = obj.int("bg") ?: Color.Transparent.toArgb(),
                    fontSizeNorm = SharedPdfTextAnnotationDefaults.sanitizePageRelativeFontSize(
                        requireNotNull(obj.float("sz"))
                    ),
                    isBold = obj.boolean("b") ?: false,
                    isItalic = obj.boolean("i") ?: false,
                    isUnderline = obj.boolean("u") ?: false,
                    isStrikethrough = obj.boolean("st") ?: false,
                    fontPath = obj.optionalString("fp")
                )
            }
        SharedPdfRichTextLog.d("serializer.decodeElement textLen=${text.length} spans=${spans.size}")
        val paragraphCount = richParagraphBounds(text).size
        val paragraphs = MutableList(paragraphCount) { SharedPdfRichParagraph() }
        root["paragraphs"]?.jsonArrayOrNull()?.forEach { paragraphElement ->
            val obj = paragraphElement.jsonObjectOrNull() ?: return@forEach
            val index = obj.int("p") ?: return@forEach
            if (index < 0 || index >= paragraphCount) return@forEach
            val alignment = when (obj.int("a") ?: 0) {
                1 -> SharedPdfRichTextAlign.CENTER
                2 -> SharedPdfRichTextAlign.RIGHT
                else -> SharedPdfRichTextAlign.LEFT
            }
            val listType = when (obj.int("l") ?: 0) {
                1 -> SharedPdfRichListType.BULLET
                2 -> SharedPdfRichListType.NUMBERED
                else -> SharedPdfRichListType.NONE
            }
            paragraphs[index] = SharedPdfRichParagraph(alignment = alignment, listType = listType)
        }
        return SharedPdfRichDocument(text = text, spans = spans, paragraphs = paragraphs.trimmedRichParagraphs())
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? {
        if (this is JsonNull) return null
        return runCatching { jsonArray }.getOrNull()
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? {
        if (this is JsonNull) return null
        return runCatching { jsonObject }.getOrNull()
    }

    private fun JsonObject.requiredString(name: String): String? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull }
            .getOrNull()
    }

    private fun JsonObject.optionalString(name: String): String {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull }
            .getOrNull()
            .orEmpty()
    }

    private fun JsonObject.int(name: String): Int? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull }.getOrNull()
    }

    private fun JsonObject.float(name: String): Float? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull?.toFloat() }.getOrNull()
    }

    private fun JsonObject.boolean(name: String): Boolean? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.booleanOrNull }.getOrNull()
    }
}

object SharedPdfRichTextMapper {
    fun toAnnotatedString(
        document: SharedPdfRichDocument,
        pageHeightPx: Float,
        rangeStart: Int = 0,
        rangeEnd: Int = document.text.length,
        fontFamilyResolver: (String?) -> FontFamily = { FontFamily.Default }
    ): AnnotatedString {
        val safeGlobalStart = rangeStart.coerceIn(0, document.text.length)
        val safeGlobalEnd = rangeEnd.coerceIn(safeGlobalStart, document.text.length)
        if (safeGlobalStart == safeGlobalEnd) return AnnotatedString("")

        var minNorm = Float.MAX_VALUE
        var maxNorm = Float.MIN_VALUE
        var pxSamples = 0
        var minPx = Float.MAX_VALUE
        var maxPx = Float.MIN_VALUE
        val textSubstring = document.text.substring(safeGlobalStart, safeGlobalEnd)
        val built = buildAnnotatedString {
            append(textSubstring)
            for (span in document.spans) {
                if (span.start >= safeGlobalEnd) break
                if (span.end <= safeGlobalStart) continue

                val intersectionStart = maxOf(span.start, safeGlobalStart)
                val intersectionEnd = minOf(span.end, safeGlobalEnd)
                if (intersectionStart >= intersectionEnd) continue

                val localStart = intersectionStart - safeGlobalStart
                val localEnd = intersectionEnd - safeGlobalStart
                val fontSizeNorm = SharedPdfTextAnnotationDefaults.sanitizePageRelativeFontSize(span.fontSizeNorm)
                val fontSizePx = if (pageHeightPx > 0) fontSizeNorm * pageHeightPx else 16f
                minNorm = minOf(minNorm, fontSizeNorm)
                maxNorm = maxOf(maxNorm, fontSizeNorm)
                minPx = minOf(minPx, fontSizePx)
                maxPx = maxOf(maxPx, fontSizePx)
                pxSamples++
                addStyle(
                    style = SpanStyle(
                        color = Color(span.color),
                        background = Color(span.backgroundColor),
                        fontSize = fontSizePx.sp,
                        fontWeight = if (span.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (span.isItalic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = richTextDecoration(
                            underline = span.isUnderline,
                            strikeThrough = span.isStrikethrough
                        ),
                        fontFamily = fontFamilyResolver(span.fontPath)
                    ),
                    start = localStart,
                    end = localEnd
                )
                span.fontPath?.takeIf { it.isNotBlank() }?.let { fontPath ->
                    addStringAnnotation(
                        tag = SHARED_PDF_RICH_FONT_PATH_TAG,
                        annotation = fontPath,
                        start = localStart,
                        end = localEnd
                    )
                }
            }
            // Paragraph attributes (alignment + list tags), clipped to range.
            // Alignment uses merged runs (Approach A): one ParagraphStyle per
            // same-align run so Compose does not invent a line per range.
            richParagraphAlignRuns(document.text, document.paragraphs).forEach { run ->
                val localStart = (maxOf(run.start, safeGlobalStart) - safeGlobalStart)
                    .coerceIn(0, textSubstring.length)
                val localEnd = (minOf(run.end, safeGlobalEnd) - safeGlobalStart)
                    .coerceIn(0, textSubstring.length)
                if (localStart >= localEnd) return@forEach
                addStyle(
                    style = ParagraphStyle(textAlign = run.alignment.toComposeTextAlign()),
                    start = localStart,
                    end = localEnd
                )
            }
            richParagraphBounds(document.text).forEachIndexed { index, bound ->
                val attrs = document.paragraphs.getOrElse(index) { SharedPdfRichParagraph() }
                if (attrs.listType == SharedPdfRichListType.NONE) {
                    return@forEachIndexed
                }
                val localStart = (maxOf(bound.start, safeGlobalStart) - safeGlobalStart)
                    .coerceIn(0, textSubstring.length)
                val localEnd = (minOf(bound.end, safeGlobalEnd) - safeGlobalStart)
                    .coerceIn(0, textSubstring.length)
                if (localStart >= localEnd) return@forEachIndexed
                attrs.listType.toListTag()?.let { tag ->
                    addStringAnnotation(
                        tag = SHARED_PDF_RICH_LIST_TAG,
                        annotation = tag,
                        start = localStart,
                        end = localEnd
                    )
                }
            }
        }
        if (pxSamples > 0) {
            SharedPdfRichLayoutDiag.d(
                "bake.out range=$safeGlobalStart..$safeGlobalEnd pageH=${pageHeightPx.richLogFloat()} " +
                    "spans=$pxSamples normMin=${minNorm.richLogFloat()} normMax=${maxNorm.richLogFloat()} " +
                    "pxMin=${minPx.richLogFloat()} pxMax=${maxPx.richLogFloat()} " +
                    "paraStyles=${built.paragraphStyles.size}"
            )
        }
        // Full-document bake (edit/measure buffer): re-attach Rule 3 EOF anchor
        // when the persisted document carries a non-LEFT trailing empty line.
        return if (safeGlobalStart == 0 && safeGlobalEnd == document.text.length) {
            built.withTrailingRichAlignAnchorIfNeeded(
                document.paragraphs + List(
                    (richParagraphBounds(document.text).size - document.paragraphs.size).coerceAtLeast(0),
                ) { SharedPdfRichParagraph() },
            )
        } else {
            built
        }
    }

    fun fromAnnotatedString(
        text: AnnotatedString,
        pageHeightPx: Float,
        fontPathResolver: (FontFamily?) -> String? = { null }
    ): SharedPdfRichDocument {
        if (text.text.isEmpty()) return SharedPdfRichDocument()

        // Rule 3: read alignment (incl. EOF anchor style) before stripping the
        // anchor so a non-LEFT trailing empty line survives into document.paragraphs.
        val paragraphs = readRichParagraphs(text).trimmedRichParagraphs()
        val strippedText = text.text.withoutRichAlignAnchors()
        val stripped = if (strippedText.length != text.text.length) {
            text.subSequence(0, strippedText.length)
        } else {
            text
        }

        val spans = mutableListOf<SharedPdfRichSpan>()
        val fontPathAnnotations = stripped.getStringAnnotations(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = stripped.length
        )
        val changePoints = mutableSetOf(0, stripped.length)
        stripped.spanStyles.forEach {
            changePoints.add(it.start)
            changePoints.add(it.end)
        }
        fontPathAnnotations.forEach {
            changePoints.add(it.start)
            changePoints.add(it.end)
        }

        val sortedPoints = changePoints.sorted()
        for (i in 0 until sortedPoints.size - 1) {
            val start = sortedPoints[i]
            val end = sortedPoints[i + 1]
            if (start >= end) continue

            val activeStyles = stripped.spanStyles.filter { it.start <= start && it.end >= end }
            val activeFontPath = fontPathAnnotations
                .lastOrNull { it.start <= start && it.end >= end }
                ?.item
                ?.takeIf { it.isNotBlank() }
            var effective = SpanStyle(color = Color.Black, fontSize = 16.sp)
            activeStyles.forEach { effective = effective.merge(it.item) }
            val currentDecoration = effective.textDecoration ?: TextDecoration.None
            val fontSizeNorm = if (effective.fontSize.isSp) {
                if (pageHeightPx > 0) effective.fontSize.value / pageHeightPx else 0.015f
            } else {
                0.015f
            }.let(SharedPdfTextAnnotationDefaults::sanitizePageRelativeFontSize)

            val newSpan = SharedPdfRichSpan(
                start = start,
                end = end,
                color = effective.color.toArgb(),
                backgroundColor = effective.background.toArgb(),
                fontSizeNorm = fontSizeNorm,
                isBold = effective.fontWeight == FontWeight.Bold,
                isItalic = effective.fontStyle == FontStyle.Italic,
                isUnderline = currentDecoration.contains(TextDecoration.Underline),
                isStrikethrough = currentDecoration.contains(TextDecoration.LineThrough),
                fontPath = fontPathResolver(effective.fontFamily) ?: activeFontPath
            )

            if (spans.isNotEmpty()) {
                val last = spans.last()
                if (last.end == start && last.sameRichStyleAs(newSpan)) {
                    spans[spans.lastIndex] = last.copy(end = end)
                } else {
                    spans += newSpan
                }
            } else {
                spans += newSpan
            }
        }
        val normMin = spans.minOfOrNull { it.fontSizeNorm }
        val normMax = spans.maxOfOrNull { it.fontSizeNorm }
        SharedPdfRichLayoutDiag.d(
            "bake.in len=${stripped.length} pageH=${pageHeightPx.richLogFloat()} spans=${spans.size} " +
                "paraStyles=${stripped.paragraphStyles.size} " +
                "strippedAnchor=${strippedText.length != text.text.length} " +
                "normMin=${normMin?.richLogFloat() ?: "-"} normMax=${normMax?.richLogFloat() ?: "-"}"
        )
        return SharedPdfRichDocument(
            text = stripped.text,
            spans = spans,
            paragraphs = paragraphs,
        )
    }

    private fun SharedPdfRichSpan.sameRichStyleAs(other: SharedPdfRichSpan): Boolean {
        return color == other.color &&
            backgroundColor == other.backgroundColor &&
            fontSizeNorm == other.fontSizeNorm &&
            isBold == other.isBold &&
            isItalic == other.isItalic &&
            isUnderline == other.isUnderline &&
            isStrikethrough == other.isStrikethrough &&
            fontPath == other.fontPath
    }
}

class SharedPdfRichTextPaginationEngine {
    fun paginate(
        globalText: AnnotatedString,
        pageWidthPx: Float,
        pageHeightPx: Float,
        textMeasurer: TextMeasurer,
        density: Density,
        marginX: Float,
        marginY: Float,
        previousLayouts: List<SharedPdfRichPageLayout> = emptyList(),
        dirtyGlobalIndex: Int = 0
    ): List<SharedPdfRichPageLayout> {
        val sanitizedGlobalText = globalText.withSanitizedSharedPdfRichFontSizes(pageHeightPx)
        val totalLen = sanitizedGlobalText.length
        SharedPdfRichTextLog.d(
            "paginate start textLen=$totalLen page=${pageWidthPx.richLogFloat()}x${pageHeightPx.richLogFloat()} " +
                "margin=${marginX.richLogFloat()},${marginY.richLogFloat()} prev=${previousLayouts.size} dirty=$dirtyGlobalIndex"
        )
        if (totalLen == 0) {
            val emptyLayout = listOf(
                SharedPdfRichPageLayout(
                    pageIndex = 0,
                    visibleText = AnnotatedString(""),
                    globalStartIndex = 0,
                    globalEndIndex = 0,
                    pageHeightPx = pageHeightPx
                )
            )
            SharedPdfRichTextLog.d("paginate empty -> ${emptyLayout.richLayoutSummary()}")
            return emptyLayout
        }
        if (pageWidthPx <= 0f || pageHeightPx <= 0f) {
            SharedPdfRichTextLog.d("paginate aborted invalid page size")
            return emptyList()
        }

        val editorWidth = (pageWidthPx - (marginX * 2f)).coerceAtLeast(10f)
        val editorHeight = (pageHeightPx - (marginY * 2f)).coerceAtLeast(10f)

        val newPages = mutableListOf<SharedPdfRichPageLayout>()
        var currentPageIndex = 0
        var segmentStart = 0
        val rawText = sanitizedGlobalText.text

        while (segmentStart < totalLen) {
            val breakIndex = rawText.indexOf(SHARED_PDF_PAGE_BREAK_CHAR, startIndex = segmentStart)
            val hasExplicitBreak = breakIndex != -1
            val contentEnd = if (hasExplicitBreak) breakIndex else totalLen
            val segmentEnd = if (hasExplicitBreak) breakIndex + 1 else totalLen

            currentPageIndex = newPages.appendMeasuredRichTextSegment(
                globalText = sanitizedGlobalText,
                segmentStart = segmentStart,
                contentEnd = contentEnd,
                explicitBreakEnd = if (hasExplicitBreak) segmentEnd else null,
                pageIndex = currentPageIndex,
                pageHeightPx = pageHeightPx,
                editorWidth = editorWidth,
                editorHeight = editorHeight,
                textMeasurer = textMeasurer,
                density = density
            )
            segmentStart = segmentEnd
        }

        val result = newPages.withTrailingBlankRichTextPageIfNeeded(
            globalText = sanitizedGlobalText,
            pageHeightPx = pageHeightPx
        )
        SharedPdfRichTextLog.d("paginate done -> ${result.richLayoutSummary()}")
        SharedPdfRichLayoutDiag.d(
            "paginate textLen=$totalLen pageH=${pageHeightPx.richLogFloat()} " +
                "pageBreaks=${rawText.count { it == SHARED_PDF_PAGE_BREAK_CHAR }} " +
                "pages=${result.size} ${result.richLayoutSummary()}"
        )
        return result
    }
}

private fun MutableList<SharedPdfRichPageLayout>.appendMeasuredRichTextSegment(
    globalText: AnnotatedString,
    segmentStart: Int,
    contentEnd: Int,
    explicitBreakEnd: Int?,
    pageIndex: Int,
    pageHeightPx: Float,
    editorWidth: Float,
    editorHeight: Float,
    textMeasurer: TextMeasurer,
    density: Density
): Int {
    var nextPageIndex = pageIndex
    if (segmentStart >= contentEnd) {
        val breakEnd = explicitBreakEnd ?: return nextPageIndex
        add(
            SharedPdfRichPageLayout(
                pageIndex = nextPageIndex,
                visibleText = globalText.subSequence(segmentStart, breakEnd),
                globalStartIndex = segmentStart,
                globalEndIndex = breakEnd,
                pageHeightPx = pageHeightPx
            )
        )
        SharedPdfRichTextLog.d(
            "paginate pageBreakOnly page=$nextPageIndex global=$segmentStart..$breakEnd"
        )
        return nextPageIndex + 1
    }

    val contentLength = contentEnd - segmentStart
    var relativeStart = 0
    while (relativeStart < contentLength) {
        val globalStart = segmentStart + relativeStart
        val remainingText = globalText.subSequence(globalStart, contentEnd)
        val measureResult = textMeasurer.measure(
            text = remainingText,
            style = TextStyle(fontSize = 16.sp, color = Color.Black),
            constraints = Constraints(
                minWidth = editorWidth.toInt(),
                maxWidth = editorWidth.toInt(),
                maxHeight = Constraints.Infinity,
            ),
            density = density
        )
        val fitsOnPage = measureResult.size.height.toFloat() <= editorHeight || measureResult.lineCount <= 1
        var overflowLineIndex: Int? = null
        val relativeEnd = if (fitsOnPage) {
            contentLength
        } else {
            val lineIndex = measureResult.richLastFittingLineIndex(editorHeight)
            overflowLineIndex = lineIndex
            val localEnd = measureResult.getLineEnd(lineIndex)
                .coerceIn(0, remainingText.length)
                .coerceAtLeast(1)
            (relativeStart + localEnd)
                .coerceAtLeast(relativeStart + 1)
                .coerceAtMost(contentLength)
        }
        val isLastContentPage = relativeEnd >= contentLength
        val globalEnd = if (isLastContentPage && explicitBreakEnd != null) {
            explicitBreakEnd
        } else {
            segmentStart + relativeEnd
        }

        add(
            SharedPdfRichPageLayout(
                pageIndex = nextPageIndex,
                visibleText = globalText.subSequence(globalStart, globalEnd),
                globalStartIndex = globalStart,
                globalEndIndex = globalEnd,
                pageHeightPx = pageHeightPx
            )
        )
        if (isLastContentPage && explicitBreakEnd != null) {
            SharedPdfRichTextLog.d(
                "paginate pageBreak page=$nextPageIndex global=$globalStart..$globalEnd"
            )
        } else if (!fitsOnPage) {
            SharedPdfRichTextLog.d(
                "paginate overflow page=$nextPageIndex global=$globalStart..$globalEnd line=$overflowLineIndex"
            )
        } else {
            SharedPdfRichTextLog.d("paginate final page=$nextPageIndex global=$globalStart..$globalEnd")
        }
        nextPageIndex++
        relativeStart = relativeEnd
    }

    return nextPageIndex
}

private fun TextLayoutResult.richLastFittingLineIndex(editorHeight: Float): Int {
    var lastFitting = 0
    for (lineIndex in 0 until lineCount) {
        if (lineIndex == 0 || getLineBottom(lineIndex) <= editorHeight) {
            lastFitting = lineIndex
        } else {
            break
        }
    }
    return lastFitting.coerceIn(0, (lineCount - 1).coerceAtLeast(0))
}

fun AnnotatedString.withoutTrailingSharedPdfPageBreak(): AnnotatedString {
    return if (text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR) {
        subSequence(0, length - 1)
    } else {
        this
    }
}

fun AnnotatedString.withRestoredTrailingSharedPdfPageBreak(shouldRestore: Boolean): AnnotatedString {
    if (!shouldRestore) return this
    if (text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR) return this
    return this + AnnotatedString(SHARED_PDF_PAGE_BREAK_CHAR.toString())
}

fun sharedPdfRichTextInsertionIndexForPage(
    insertPageIndex: Int,
    pageLayouts: List<SharedPdfRichPageLayout>,
    textLength: Int
): Int {
    val rawIndex = if (insertPageIndex <= 0) {
        0
    } else {
        pageLayouts.find { it.pageIndex == insertPageIndex - 1 }?.globalEndIndex ?: textLength
    }
    return rawIndex.coerceIn(0, textLength)
}

fun sharedPdfRichTextBlankInsertBreakCount(text: String, insertionCharIndex: Int): Int {
    val safeIndex = insertionCharIndex.coerceIn(0, text.length)
    if (safeIndex == 0 || safeIndex == text.length) return 1

    val hasBoundaryBreakBefore = text.getOrNull(safeIndex - 1) == SHARED_PDF_PAGE_BREAK_CHAR
    val hasBoundaryBreakAfter = text.getOrNull(safeIndex) == SHARED_PDF_PAGE_BREAK_CHAR
    return if (hasBoundaryBreakBefore || hasBoundaryBreakAfter) 1 else 2
}

fun String.hasRenderableSharedPdfRichText(): Boolean {
    return any { it != SHARED_PDF_PAGE_BREAK_CHAR && !it.isWhitespace() }
}

fun remapSharedPdfRichTextForLayoutChange(
    currentLayout: List<PdfPageIdentity>,
    updatedLayout: List<PdfPageIdentity>,
    pageLayouts: List<SharedPdfRichPageLayout>,
): AnnotatedString {
    if (pageLayouts.isEmpty()) return AnnotatedString("")

    val mapping = buildSharedPdfPageIndexMapping(
        currentLayout = currentLayout,
        updatedLayout = updatedLayout,
        sourcePageIndices = pageLayouts.map { it.pageIndex },
    )
    if (mapping.isEmpty()) return AnnotatedString("")

    val contentByTargetPage = linkedMapOf<Int, AnnotatedString>()
    pageLayouts.sortedBy { it.pageIndex }.forEach { layout ->
        val targetPageIndex = mapping[layout.pageIndex] ?: return@forEach
        contentByTargetPage[targetPageIndex] = layout.visibleText.withoutTrailingSharedPdfPageBreak()
    }

    val lastPageWithContent = contentByTargetPage
        .filterValues { it.text.isNotEmpty() }
        .keys
        .maxOrNull()
        ?: return AnnotatedString("")

    return AnnotatedString.Builder().apply {
        for (pageIndex in 0..lastPageWithContent) {
            contentByTargetPage[pageIndex]?.let(::append)
            if (pageIndex < lastPageWithContent) {
                append(SHARED_PDF_PAGE_BREAK_CHAR.toString())
            }
        }
    }.toAnnotatedString()
}

fun List<SharedPdfRichPageLayout>.withTrailingBlankRichTextPageIfNeeded(
    globalText: AnnotatedString,
    pageHeightPx: Float
): List<SharedPdfRichPageLayout> {
    if (globalText.text.lastOrNull() != SHARED_PDF_PAGE_BREAK_CHAR) return this
    val lastLayout = lastOrNull()
    val trailingStart = globalText.length
    if (lastLayout != null &&
        lastLayout.globalStartIndex == trailingStart &&
        lastLayout.globalEndIndex == trailingStart
    ) {
        SharedPdfRichTextLog.d("trailingBlank already present page=${lastLayout.pageIndex} index=$trailingStart")
        return this
    }
    SharedPdfRichTextLog.d(
        "trailingBlank added page=${(lastLayout?.pageIndex ?: -1) + 1} global=$trailingStart"
    )
    return this + SharedPdfRichPageLayout(
        pageIndex = (lastLayout?.pageIndex ?: -1) + 1,
        visibleText = AnnotatedString(""),
        globalStartIndex = trailingStart,
        globalEndIndex = trailingStart,
        pageHeightPx = pageHeightPx
    )
}

@Stable
class SharedPdfRichTextController(
    private val scope: CoroutineScope,
    initialDocument: SharedPdfRichDocument = SharedPdfRichDocument(),
    private val onDocumentChange: suspend (SharedPdfRichDocument) -> Unit = {},
    private val documentToAnnotatedString: (SharedPdfRichDocument, Float) -> AnnotatedString =
        { document, pageHeight -> SharedPdfRichTextMapper.toAnnotatedString(document, pageHeight) },
    private val annotatedStringToDocument: (AnnotatedString, Float) -> SharedPdfRichDocument =
        { text, pageHeight -> SharedPdfRichTextMapper.fromAnnotatedString(text, pageHeight) },
    private val styleForFontPath: (SpanStyle, String?) -> SpanStyle = { style, _ -> style },
    private val onEditingFocusRequested: () -> Unit = {},
) {
    var globalTextFieldValue by mutableStateOf(
        TextFieldValue(documentToAnnotatedString(initialDocument, 1414f))
    )
        private set

    var localTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    val editingValue: TextFieldValue
        get() = if (activePageIndex != -1) localTextFieldValue else globalTextFieldValue

    var activePageIndex by mutableIntStateOf(-1)
        private set

    var pageLayouts by mutableStateOf(emptyList<SharedPdfRichPageLayout>())
        private set

    val hasRenderableText: Boolean
        get() = globalTextFieldValue.text.hasRenderableSharedPdfRichText()

    var currentStyle: SpanStyle by mutableStateOf(SpanStyle(color = Color.Black, fontSize = 16.sp))
        private set

    var currentFontPath: String? by mutableStateOf(null)
        private set

    var currentFontName: String? by mutableStateOf(null)
        private set

    var cursorPageIndex by mutableIntStateOf(-1)
        private set

    var cursorRectInPage by mutableStateOf<Rect?>(null)
        private set

    var isCursorVisible by mutableStateOf(false)
        private set

    var showCursorOverride by mutableStateOf(true)

    val focusRequester = FocusRequester()

    private var lastPageWidth = 1000f
    private var lastPageHeight = 1414f
    private var lastDensity: Density? = null
    private var lastTextMeasurer: TextMeasurer? = null
    private val engine = SharedPdfRichTextPaginationEngine()
    private var saveJob: Job? = null
    private var dirtySinceMillis: Long? = null
    private var syncJob: Job? = null
    private var isSaving = false
    /**
     * One-shot: programmatic trailing-newline padding (tap below text)
     * skips list inheritance so padding never grows list markers.
     */
    private var suppressListInheritOnce = false

    /**
     * Echo guard for Enter-continuation markers. Some software keyboards
     * echo the pre-insertion text right after the engine inserts a
     * continued marker inside onValueChange (observed: the fresh "• "
     * deleted 2ms after Enter, looking like the list "toggled off"). When
     * set, the very next change that is EXACTLY that marker's deletion is
     * treated as the echo and the marker is restored from the still-marked
     * old value. Anything else clears it, so a genuine backspace re-press
     * (guard consumed) still exits the list as designed.
     */
    private var pendingContinuationGuard: PendingContinuationMarker? = null

    private data class PendingContinuationMarker(val text: String, val start: Int, val end: Int)

    private fun setGlobalText(value: TextFieldValue, reason: String) {
        val oldLen = globalTextFieldValue.text.length
        val newLen = value.text.length
        if (oldLen != newLen) {
            SharedPdfRichLayoutDiag.d(
                "global.set reason=$reason oldLen=$oldLen newLen=$newLen active=$activePageIndex"
            )
        }
        globalTextFieldValue = value
    }

    fun loadDocumentIfEmpty(document: SharedPdfRichDocument) {
        if (globalTextFieldValue.text.isEmpty()) {
            setGlobalText(
                TextFieldValue(
                    documentToAnnotatedString(document, lastPageHeight)
                ),
                "loadIfEmpty"
            )
            repaginate(dirtyStartIndex = 0)
        }
    }

    fun replaceDocument(document: SharedPdfRichDocument) {
        SharedPdfRichTextLog.d(
            "controller.replaceDocument textLen=${document.text.length} spans=${document.spans.size} " +
                "oldLayouts=${pageLayouts.size} activePage=$activePageIndex"
        )
        SharedPdfRichLayoutDiag.d(
            "replaceDocument inLen=${document.text.length} oldGlobalLen=${globalTextFieldValue.text.length} " +
                "oldLayouts=${pageLayouts.size} active=$activePageIndex"
        )
        saveJob?.cancel()
        syncJob?.cancel()
        activePageIndex = -1
        cursorPageIndex = -1
        cursorRectInPage = null
        isCursorVisible = false
        localTextFieldValue = TextFieldValue("")
        setGlobalText(
            TextFieldValue(
                documentToAnnotatedString(document, lastPageHeight)
            ),
            "replaceDocument"
        )
        repaginate(dirtyStartIndex = 0)
    }

    fun updateLayoutConfig(width: Float, height: Float, density: Density, measurer: TextMeasurer) {
        if (shouldUpdateSharedPdfRichTextLayoutConfig(
                previousWidth = lastPageWidth,
                previousHeight = lastPageHeight,
                previousDensity = lastDensity,
                width = width,
                height = height,
                density = density,
            )
        ) {
            SharedPdfRichTextLog.d(
                "controller.layoutConfig width=${width.richLogFloat()} height=${height.richLogFloat()} " +
                    "density=${density.density.richLogFloat()} old=${lastPageWidth.richLogFloat()}x${lastPageHeight.richLogFloat()}"
            )
            val oldW = lastPageWidth
            val oldH = lastPageHeight
            lastPageWidth = width
            lastPageHeight = height
            lastDensity = density
            lastTextMeasurer = measurer
            SharedPdfRichLayoutDiag.nextEpoch()
            SharedPdfRichLayoutDiag.d(
                "layout.config old=${oldW.richLogFloat()}x${oldH.richLogFloat()} " +
                    "new=${width.richLogFloat()}x${height.richLogFloat()} " +
                    "density=${density.density.richLogFloat()} active=$activePageIndex " +
                    "layouts=${pageLayouts.richLayoutSummary()}"
            )
            repaginate(dirtyStartIndex = 0)
        }
    }

    fun clearSelection() {
        SharedPdfRichTextLog.d(
            "controller.clearSelection activePage=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                "localLen=${localTextFieldValue.text.length}"
        )
        SharedPdfRichLayoutDiag.d(
            "clear.entry active=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                "localLen=${localTextFieldValue.text.length} layouts=${pageLayouts.richLayoutSummary()} " +
                "syncJobActive=${syncJob?.isActive == true} saveJobActive=${saveJob?.isActive == true} " +
                "isSaving=$isSaving"
        )
        isCursorVisible = false
        val pageToSync = activePageIndex
        if (pageToSync != -1) {
            scope.launch {
                performSync(pageToSync)
                if (activePageIndex == pageToSync) {
                    activePageIndex = -1
                    SharedPdfRichLayoutDiag.d(
                        "clear.activePageNegated page=$pageToSync " +
                            "globalLen=${globalTextFieldValue.text.length} layouts=${pageLayouts.richLayoutSummary()}"
                    )
                }
            }
        }
        if (globalTextFieldValue.text.isNotEmpty()) {
            setGlobalText(
                globalTextFieldValue.copy(
                    selection = TextRange(globalTextFieldValue.text.length)
                ),
                "clearSelection"
            )
        }
        cursorPageIndex = -1
        cursorRectInPage = null
    }

    fun onValueChanged(newValue: TextFieldValue) {
        if (isSaving) {
            SharedPdfRichTextLog.d("controller.onValueChanged ignored because saveImmediate is running")
            return
        }

        if (activePageIndex == -1 && newValue.text.length != globalTextFieldValue.text.length) {
            SharedPdfRichLayoutDiag.d(
                "onValue.globalMismatch newLen=${newValue.text.length} " +
                    "globalLen=${globalTextFieldValue.text.length} localLen=${localTextFieldValue.text.length} " +
                    "newHead=${newValue.text.richPreview(12)} globalHead=${globalTextFieldValue.text.richPreview(12)}"
            )
        }

        if (activePageIndex != -1 && !newValue.text.startsWith(SHARED_PDF_ZWSP)) {
            SharedPdfRichTextLog.d(
                "controller.onValueChanged missing ZWSP activePage=$activePageIndex selection=${newValue.selection}"
            )
            val handled = handleBackspaceAtStart()
            if (!handled) {
                localTextFieldValue = localTextFieldValue.copy(selection = TextRange(1))
            }
            return
        }

        val oldValue = if (activePageIndex != -1) localTextFieldValue else globalTextFieldValue
        val newText = newValue.text
        val oldText = oldValue.text

        if (newText == oldText) {
            if (oldValue.selection != newValue.selection) {
                SharedPdfRichTextLog.d(
                    "controller.selectionOnly activePage=$activePageIndex oldSel=${oldValue.selection} newSel=${newValue.selection}"
                )
            }
            if (activePageIndex != -1) {
                if (
                    localTextFieldValue.selection != newValue.selection ||
                    localTextFieldValue.composition != newValue.composition
                ) {
                    localTextFieldValue = newValue.copy(annotatedString = localTextFieldValue.annotatedString)
                    isCursorVisible = true
                    updateLocalCursor()
                }
            } else {
                if (
                    globalTextFieldValue.selection != newValue.selection ||
                    globalTextFieldValue.composition != newValue.composition
                ) {
                    setGlobalText(
                        newValue.copy(annotatedString = globalTextFieldValue.annotatedString),
                        "onValue.selectionOnly"
                    )
                    updateGlobalCursor()
                }
            }
            return
        }

        val oldAnnotated = oldValue.annotatedString
        val diff = newText.length - oldText.length
        val cursor = newValue.selection.end
        val changeStart = if (diff > 0) cursor - diff else cursor
        val changeEndOld = if (diff > 0) changeStart else changeStart - diff
        pendingContinuationGuard?.let { guard ->
            pendingContinuationGuard = null
            if (diff == guard.start - guard.end &&
                changeStart == guard.start &&
                changeEndOld == guard.end &&
                newValue.selection.start == guard.start &&
                newValue.selection.end == guard.start &&
                newText == oldText.removeRange(guard.start, guard.end) &&
                oldText.substring(guard.start, guard.end) == guard.text
            ) {
                // IME echo of the just-inserted continuation marker, not a
                // user delete: oldValue still carries the marker with correct
                // spans/tags, so keep it and park the cursor after it.
                SharedPdfRichTextLog.d(
                    "controller.textChanged echo-guard restore page=$activePageIndex range=${guard.start}..${guard.end}"
                )
                if (activePageIndex != -1) {
                    localTextFieldValue = oldValue.copy(selection = TextRange(guard.end))
                    isCursorVisible = true
                    updateLocalCursor()
                    syncJob?.cancel()
                    syncJob = scope.launch {
                        delay(300)
                        performSync(activePageIndex, checkCursorMove = true)
                    }
                } else {
                    setGlobalText(
                        oldValue.copy(selection = TextRange(guard.end)),
                        "onValue.echoGuard"
                    )
                    debouncedSave(globalTextFieldValue)
                }
                return
            }
        }
        SharedPdfRichTextLog.d(
            "controller.textChanged activePage=$activePageIndex oldLen=${oldText.length} newLen=${newText.length} " +
                "diff=$diff cursor=$cursor change=$changeStart..$changeEndOld style=${currentStyle.richStyleSummary()} " +
                "preview=\"${newText.richPreview()}\""
        )
        // Paragraph maintenance first: list markers, Enter/backspace list
        // rules, renumber. Local page segments carry a leading ZWSP before
        // user content; the first local paragraph may be a page-split
        // continuation whose marker lives on the previous page.
        val contentOffset = if (activePageIndex != -1) 1 else 0
        val firstStartsAtBoundary = if (activePageIndex != -1) {
            firstLocalParagraphStartsAtBoundary()
        } else {
            true
        }
        val inheritList = consumeSuppressListInherit()
        val norm = applyRichParagraphKeystroke(
            old = oldAnnotated,
            newText = newText,
            newSelection = newValue.selection,
            contentOffset = contentOffset,
            firstStartsAtParagraphStart = firstStartsAtBoundary,
            inheritList = inheritList,
            markerFallbackStyle = currentStyle,
        )
        val mutableSpans = oldAnnotated.spanStyles.mapNotNull {
            it.shiftedByTextChange(
                diff = norm.effectiveShift.diff,
                changeStart = norm.effectiveShift.changeStart,
                changeEndOld = norm.effectiveShift.changeEndOld
            )
        }.toMutableList()
        val mutableFontAnnotations = oldAnnotated.getStringAnnotations(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = oldAnnotated.length
        ).mapNotNull {
            it.shiftedByTextChange(
                diff = norm.effectiveShift.diff,
                changeStart = norm.effectiveShift.changeStart,
                changeEndOld = norm.effectiveShift.changeEndOld
            )
        }.toMutableList()

        if (diff > 0 && norm.typedStart >= 0) {
            val start = norm.typedStart.coerceIn(0, norm.text.length)
            val end = norm.typedEnd.coerceIn(start, norm.text.length)
            mutableSpans += MutableSpan(start, end, currentStyle)
            currentFontPath?.takeIf { it.isNotBlank() }?.let { fontPath ->
                mutableFontAnnotations += MutableStringAnnotation(
                    start = start,
                    end = end,
                    tag = SHARED_PDF_RICH_FONT_PATH_TAG,
                    item = fontPath
                )
            }
        }
        // Paragraph follow-up shifts (marker insert/remove, renumber).
        for (shift in norm.shifts) {
            var i = mutableSpans.size - 1
            while (i >= 0) {
                val span = mutableSpans[i]
                val next = shiftRange(span.start, span.end, shift.diff, shift.changeStart, shift.changeEndOld)
                if (next == null) {
                    mutableSpans.removeAt(i)
                } else {
                    span.start = next.first
                    span.end = next.second
                }
                i--
            }
            var j = mutableFontAnnotations.size - 1
            while (j >= 0) {
                val annotation = mutableFontAnnotations[j]
                val next = shiftRange(
                    annotation.start,
                    annotation.end,
                    shift.diff,
                    shift.changeStart,
                    shift.changeEndOld
                )
                if (next == null) {
                    mutableFontAnnotations.removeAt(j)
                } else {
                    annotation.start = next.first
                    annotation.end = next.second
                }
                j--
            }
        }
        norm.markerSpans.forEach { marker ->
            mutableSpans += MutableSpan(marker.start, marker.end, marker.style)
        }

        val builder = AnnotatedString.Builder(norm.text)
        mutableSpans.compactSpans().forEach { span ->
            builder.addStyle(span.item, span.start, span.end)
        }
        mutableFontAnnotations.compactStringAnnotations().forEach { annotation ->
            builder.addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
        }
        applyRichParagraphsToBuilder(builder, norm.text, norm.paragraphs)
        // Rule 3 EOF anchor is global-only: local buffers keep their leading
        // ZWSP and must never grow a trailing anchor (would splice mid-document).
        val rebuilt = if (activePageIndex == -1) {
            builder.toAnnotatedString().withTrailingRichAlignAnchorIfNeeded(norm.paragraphs)
        } else {
            builder.toAnnotatedString()
        }
        SharedPdfRichLayoutDiag.d(
            "rebuild.paras active=$activePageIndex textLen=${norm.text.length} " +
                "paraCount=${norm.paragraphs.size} " +
                "aligned=${norm.paragraphs.count { it.alignment != SharedPdfRichTextAlign.LEFT }} " +
                "emitStyles=${rebuilt.paragraphStyles.size} " +
                "ranges=${rebuilt.paragraphStyles.joinToString(";") { r -> "${r.start}..${r.end}:${r.item.textAlign}" }} " +
                "anchor=${rebuilt.text.endsWith(SHARED_PDF_RICH_ALIGN_ANCHOR)} " +
                "newlines=${norm.text.count { it == '\n' }}"
        )
        val finalValue = newValue.copy(annotatedString = rebuilt, selection = norm.selection)
        // Arm the echo guard when the engine grew the text around a typed
        // newline with a fresh marker (Enter continuation): the adjacent
        // marker span is ours, recorded verbatim for exact-match restore.
        pendingContinuationGuard = if (diff > 0 && norm.text.length > newText.length) {
            val typedEnd = (changeStart + diff).coerceIn(0, newText.length)
            val typedHasNewline = changeStart < typedEnd &&
                newText.substring(changeStart, typedEnd).contains('\n')
            if (typedHasNewline) {
                norm.markerSpans.firstOrNull { it.start >= changeStart && it.start <= changeStart + diff + 4 }
                    ?.let { span ->
                        PendingContinuationMarker(
                            text = norm.text.substring(span.start, span.end),
                            start = span.start,
                            end = span.end,
                        )
                    }
            } else {
                null
            }
        } else {
            null
        }
        if (activePageIndex != -1) {
            localTextFieldValue = finalValue
            isCursorVisible = true
            updateLocalCursor()
            syncJob?.cancel()
            SharedPdfRichTextLog.d("controller.textChanged schedule local sync page=$activePageIndex")
            syncJob = scope.launch {
                delay(300)
                performSync(activePageIndex, checkCursorMove = true)
            }
        } else {
            setGlobalText(finalValue, "onValue.textChanged")
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = 0)
            SharedPdfRichTextLog.d("controller.textChanged updated global directly")
        }
    }

    fun updateCurrentStyle(style: SpanStyle, fontPath: String? = currentFontPath, fontName: String? = currentFontName) {
        val effectiveStyle = styleForFontPath(style, fontPath)
        SharedPdfRichTextLog.d(
            "controller.updateStyle activePage=$activePageIndex localSel=${localTextFieldValue.selection} " +
                "globalSel=${globalTextFieldValue.selection} fontPath=$fontPath fontName=$fontName style=${effectiveStyle.richStyleSummary()}"
        )
        currentStyle = effectiveStyle
        currentFontPath = fontPath
        currentFontName = fontName
        isCursorVisible = true

        if (activePageIndex != -1) {
            if (!localTextFieldValue.selection.collapsed) {
                localTextFieldValue = localTextFieldValue.copy(
                    annotatedString = localTextFieldValue.annotatedString.withAppliedRichStyle(
                        style = effectiveStyle,
                        fontPath = fontPath,
                        selection = localTextFieldValue.selection
                    )
                )
                syncJob?.cancel()
                syncJob = scope.launch {
                    delay(500)
                    syncLocalToGlobal()
                }
            }
        } else if (!globalTextFieldValue.selection.collapsed) {
            setGlobalText(
                globalTextFieldValue.copy(
                    annotatedString = globalTextFieldValue.annotatedString.withAppliedRichStyle(
                        style = effectiveStyle,
                        fontPath = fontPath,
                        selection = globalTextFieldValue.selection
                    )
                ),
                "updateStyle"
            )
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = globalTextFieldValue.selection.min)
        }
        requestFocus()
    }

    fun requestEditingFocus() {
        SharedPdfRichTextLog.d(
            "controller.requestEditingFocus activePage=$activePageIndex cursorVisible=$isCursorVisible " +
                "localSel=${localTextFieldValue.selection}"
        )
        requestFocus()
    }

    /** Dock state for the current selection/cursor (alignment + list types). */
    fun richParagraphUiState(): RichParagraphUiState {
        val editing = editingValue
        return richParagraphUiState(
            editing.annotatedString,
            editing.selection,
            if (activePageIndex != -1) 1 else 0,
        )
    }

    /**
     * Toggles a list type on the paragraphs intersecting the selection.
     * Always operates on GLOBAL text: local page segments may start
     * mid-paragraph, where a marker insert would orphan. Unsynced local
     * edits are merged (and re-paginated) first so the op targets the
     * cursor's actual paragraph instead of stale global text.
     */
    fun toggleRichListType(type: SharedPdfRichListType) {
        if (type == SharedPdfRichListType.NONE) return
        if (activePageIndex != -1) {
            mergeLocalIntoGlobal(activePageIndex)
            repaginateSync(dirtyStartIndex = 0)
        }
        val global = globalTextFieldValue.annotatedString
        val norm = toggleRichParagraphList(
            annotated = global,
            selection = globalSelectionForParagraphOp(),
            type = type,
            markerFallbackStyle = currentStyle,
        )
        SharedPdfRichTextLog.d(
            "controller.toggleList type=$type textLen=${norm.text.length} shifts=${norm.shifts.size}"
        )
        applyGlobalParagraphEdit(norm)
        requestFocus()
    }

    /**
     * Sets alignment on the paragraphs intersecting the selection
     * (global scope, same reason as [toggleRichListType]; merges unsynced
     * local edits first for the same reason).
     */
    fun setRichParagraphAlignment(align: SharedPdfRichTextAlign) {
        SharedPdfRichLayoutDiag.d(
            "align.before align=$align active=$activePageIndex " +
                "layouts=${pageLayouts.richLayoutSummary()} " +
                "globalLen=${globalTextFieldValue.text.length} " +
                "localLen=${localTextFieldValue.text.length}"
        )
        if (activePageIndex != -1) {
            mergeLocalIntoGlobal(activePageIndex)
            repaginateSync(dirtyStartIndex = 0)
            SharedPdfRichLayoutDiag.d(
                "align.afterMerge active=$activePageIndex layouts=${pageLayouts.richLayoutSummary()}"
            )
        }
        val global = globalTextFieldValue.annotatedString
        val selection = globalSelectionForParagraphOp()
        val next = setRichParagraphAlignment(global, selection, align)
        SharedPdfRichTextLog.d("controller.setAlignment align=$align sel=$selection")
        SharedPdfRichLayoutDiag.d(
            "align.apply align=$align sel=$selection paraStylesBefore=${global.paragraphStyles.size} " +
                "paraStylesAfter=${next.paragraphStyles.size}"
        )
        setGlobalText(TextFieldValue(next, selection), "align.style")
        debouncedSave(globalTextFieldValue)
        repaginate(dirtyStartIndex = selection.min)
        SharedPdfRichLayoutDiag.d(
            "align.afterRepaginate align=$align layouts=${pageLayouts.richLayoutSummary()}"
        )
        refreshLocalFromGlobal(selection.min)
        requestFocus()
    }

    private fun globalSelectionForParagraphOp(): TextRange {
        val global = globalTextFieldValue.annotatedString
        var layout = if (activePageIndex != -1) {
            pageLayouts.find { it.pageIndex == activePageIndex }
        } else {
            null
        }
        if (layout == null && activePageIndex != -1 && lastTextMeasurer != null && lastDensity != null) {
            // Dangling active page (layouts replaced without its page, e.g.
            // after an async sync repagination): re-paginate current global
            // text once so the toggle/align maps onto the cursor paragraph
            // instead of a stale global selection on the wrong page.
            SharedPdfRichTextLog.d(
                "controller.paragraphOp dangling activePage=$activePageIndex layouts=${pageLayouts.richLayoutSummary()} repaginating"
            )
            repaginateSync(dirtyStartIndex = 0)
            layout = pageLayouts.find { it.pageIndex == activePageIndex }
        }
        if (layout == null) return globalTextFieldValue.selection
        val local = localTextFieldValue.selection
        val start = (local.min - 1 + layout.globalStartIndex).coerceIn(0, global.length)
        val end = (local.max - 1 + layout.globalStartIndex).coerceIn(start, global.length)
        return TextRange(start, end)
    }

    private fun applyGlobalParagraphEdit(norm: NormalizedRichParagraphEdit) {
        val oldGlobal = globalTextFieldValue.annotatedString
        val allShifts = listOf(norm.effectiveShift) + norm.shifts
        fun shiftAll(start: Int, end: Int): Pair<Int, Int>? {
            var coords: Pair<Int, Int>? = start to end
            for (shift in allShifts) {
                coords = coords?.let {
                    shiftRange(it.first, it.second, shift.diff, shift.changeStart, shift.changeEndOld)
                }
            }
            return coords
        }
        val mutableSpans = oldGlobal.spanStyles.mapNotNull { range ->
            shiftAll(range.start, range.end)?.let { (s, e) -> MutableSpan(s, e, range.item) }
        }.toMutableList()
        val mutableFontAnnotations = oldGlobal.getStringAnnotations(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = oldGlobal.length
        ).mapNotNull { annotation ->
            shiftAll(annotation.start, annotation.end)?.let { (s, e) ->
                MutableStringAnnotation(s, e, annotation.tag, annotation.item)
            }
        }.toMutableList()
        norm.markerSpans.forEach { marker ->
            mutableSpans += MutableSpan(marker.start, marker.end, marker.style)
        }
        val builder = AnnotatedString.Builder(norm.text)
        mutableSpans.compactSpans().forEach { span ->
            builder.addStyle(span.item, span.start, span.end)
        }
        mutableFontAnnotations.compactStringAnnotations().forEach { annotation ->
            builder.addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
        }
        applyRichParagraphsToBuilder(builder, norm.text, norm.paragraphs)
        setGlobalText(
            TextFieldValue(
                builder.toAnnotatedString().withTrailingRichAlignAnchorIfNeeded(norm.paragraphs),
                norm.selection,
            ),
            "applyGlobalParagraphEdit",
        )
        debouncedSave(globalTextFieldValue)
        scope.launch {
            repaginateSync(dirtyStartIndex = norm.selection.min)
            refreshLocalFromGlobal(norm.selection.min)
        }
    }

    private fun refreshLocalFromGlobal(globalCursorPos: Int) {
        if (activePageIndex == -1) return
        val newLayout = selectRichPageLayoutForCursor(pageLayouts, globalCursorPos) ?: return
        activePageIndex = newLayout.pageIndex
        val reExtracted = globalTextFieldValue.annotatedString.subSequence(
            newLayout.globalStartIndex,
            newLayout.globalEndIndex
        ).withoutTrailingSharedPdfPageBreak()
        val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + reExtracted
        val newLocalCursor = (globalCursorPos - newLayout.globalStartIndex + 1)
            .coerceIn(0, textWithZwsp.length)
        localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(newLocalCursor))
        pendingContinuationGuard = null
        updateLocalCursor()
    }

    /**
     * Global paragraph safety net for merged text (sync merges, page
     * deletes): renumbers cross-page numbered runs and clears orphaned
     * markers. Returns the normalized text plus the shifts applied, so
     * callers can remap cursors. Zero-cost when already normalized.
     */
    private fun withNormalizedSyncedParagraphs(
        merged: AnnotatedString
    ): Pair<AnnotatedString, List<RichTextShift>> {
        val norm = normalizeRichParagraphsGlobal(merged, currentStyle)
        if (norm.text == merged.text) return merged to emptyList()
        val mutableSpans = merged.spanStyles.mapNotNull { range ->
            shiftRichRange(range.start, range.end, norm.shifts)?.let { (s, e) ->
                MutableSpan(s, e, range.item)
            }
        }.toMutableList()
        val mutableFonts = merged.getStringAnnotations(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = merged.length
        ).mapNotNull { annotation ->
            shiftRichRange(annotation.start, annotation.end, norm.shifts)?.let { (s, e) ->
                MutableStringAnnotation(s, e, annotation.tag, annotation.item)
            }
        }.toMutableList()
        norm.markerSpans.forEach { marker ->
            mutableSpans += MutableSpan(marker.start, marker.end, marker.style)
        }
        val builder = AnnotatedString.Builder(norm.text)
        mutableSpans.compactSpans().forEach { span ->
            builder.addStyle(span.item, span.start, span.end)
        }
        mutableFonts.compactStringAnnotations().forEach { annotation ->
            builder.addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
        }
        applyRichParagraphsToBuilder(builder, norm.text, norm.paragraphs)
        return builder.toAnnotatedString()
            .withTrailingRichAlignAnchorIfNeeded(norm.paragraphs) to norm.shifts
    }

    private fun firstLocalParagraphStartsAtBoundary(): Boolean {
        val layout = pageLayouts.find { it.pageIndex == activePageIndex } ?: return true
        val globalStart = layout.globalStartIndex
        if (globalStart <= 0) return true
        return globalTextFieldValue.text.getOrNull(globalStart - 1) == '\n'
    }

    private fun consumeSuppressListInherit(): Boolean {
        return if (suppressListInheritOnce) {
            suppressListInheritOnce = false
            false
        } else {
            true
        }
    }

    fun handleTapOnPage(pageIndex: Int, localTapOffset: Offset) {
        if (globalTextFieldValue.text.length > 500_000 && activePageIndex == -1) {
            SharedPdfRichTextLog.d("controller.tap document too large for instant interactive tap")
        }

        if (activePageIndex != -1 && activePageIndex != pageIndex) {
            scope.launch { performSync(activePageIndex) }
        }

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        var layout = pageLayouts.find { it.pageIndex == pageIndex }

        if (layout == null) {
            val currentLastPage = pageLayouts.lastOrNull()?.pageIndex ?: 0
            if (pageIndex > currentLastPage) {
                val breaksNeeded = pageIndex - currentLastPage
                val builder = AnnotatedString.Builder(globalTextFieldValue.annotatedString)
                repeat(breaksNeeded) {
                    builder.append(SHARED_PDF_PAGE_BREAK_CHAR.toString())
                }
                setGlobalText(TextFieldValue(builder.toAnnotatedString()), "tapPadPageBreaks")
                repaginateSync(0)
                layout = pageLayouts.find { it.pageIndex == pageIndex }
            }
        }

        val currentLayout = layout ?: return
        activePageIndex = pageIndex
        val margin = lastPageWidth * 0.1f
        val editorWidth = (lastPageWidth - (margin * 2f)).coerceAtLeast(10f)
        val editableText = currentLayout.visibleText.withoutTrailingSharedPdfPageBreak()
        val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + editableText
        pendingContinuationGuard = null
        val safeLen = editableText.length
        localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(safeLen + 1))

        val measureResult = measurer.measure(
            text = editableText,
            style = TextStyle(fontSize = 16.sp, color = Color.Black),
            constraints = Constraints(
                minWidth = editorWidth.toInt(),
                maxWidth = editorWidth.toInt(),
            ),
            density = density
        )
        val textHeight = if (editableText.isEmpty()) 0f else measureResult.size.height.toFloat()
        if (editableText.isNotEmpty() && localTapOffset.y <= textHeight) {
            var localIndex = measureResult.getOffsetForPosition(localTapOffset)
            localIndex = localIndex.coerceIn(0, editableText.length)
            localTextFieldValue = localTextFieldValue.copy(selection = TextRange(localIndex + 1))
        } else {
            injectNewlinesLocal(localTapOffset.y - textHeight)
        }

        isCursorVisible = true
        updateLocalCursor()
        requestFocus()
    }

    fun insertPageBreakAt(insertPageIndex: Int, count: Int = 1) {
        SharedPdfRichTextLog.d("controller.insertPageBreak requested page=$insertPageIndex count=$count")
        scope.launch {
            forceSyncAndClear()
            val original = globalTextFieldValue.annotatedString
            val safeIndex = sharedPdfRichTextInsertionIndexForPage(
                insertPageIndex = insertPageIndex,
                pageLayouts = pageLayouts,
                textLength = original.length
            )
            insertPageBreaksIntoGlobalText(original, safeIndex, count)
            SharedPdfRichTextLog.d(
                "controller.insertPageBreak inserted index=$safeIndex newLen=${globalTextFieldValue.text.length}"
            )
        }
    }

    fun insertBlankPageAt(insertPageIndex: Int) {
        SharedPdfRichTextLog.d("controller.insertBlankPage requested page=$insertPageIndex")
        scope.launch {
            forceSyncAndClear()
            val original = globalTextFieldValue.annotatedString
            val safeIndex = sharedPdfRichTextInsertionIndexForPage(
                insertPageIndex = insertPageIndex,
                pageLayouts = pageLayouts,
                textLength = original.length
            )
            val requiredBreaks = sharedPdfRichTextBlankInsertBreakCount(
                text = original.text,
                insertionCharIndex = safeIndex
            )
            insertPageBreaksIntoGlobalText(original, safeIndex, requiredBreaks)
            SharedPdfRichTextLog.d(
                "controller.insertBlankPage inserted index=$safeIndex breaks=$requiredBreaks newLen=${globalTextFieldValue.text.length}"
            )
        }
    }

    suspend fun remapPagesForLayoutChange(
        currentLayout: List<PdfPageIdentity>,
        updatedLayout: List<PdfPageIdentity>
    ) = withContext(NonCancellable) {
        forceSyncAndClear()
        val original = globalTextFieldValue.annotatedString
        if (pageLayouts.isEmpty() && original.text.isNotEmpty()) {
            return@withContext
        }
        val remapped = remapSharedPdfRichTextForLayoutChange(
            currentLayout = currentLayout,
            updatedLayout = updatedLayout,
            pageLayouts = pageLayouts,
        )
        if (remapped == original) {
            return@withContext
        }
        setGlobalText(TextFieldValue(remapped, TextRange(remapped.length)), "layoutChangeRemap")
        repaginateSync(0)
        saveCurrentGlobalTextImmediately()
    }

    private fun insertPageBreaksIntoGlobalText(
        original: AnnotatedString,
        safeIndex: Int,
        count: Int
    ) {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return
        val builder = AnnotatedString.Builder()
        builder.append(original.subSequence(0, safeIndex))
        repeat(safeCount) { builder.append(SHARED_PDF_PAGE_BREAK_CHAR.toString()) }
        builder.append(original.subSequence(safeIndex, original.length))
        setGlobalText(
            TextFieldValue(builder.toAnnotatedString(), TextRange(safeIndex + safeCount)),
            "insertPageBreaks"
        )
        debouncedSave(globalTextFieldValue)
        repaginate(dirtyStartIndex = safeIndex)
    }

    private suspend fun saveCurrentGlobalTextImmediately() {
        saveJob?.cancel()
        val finalAnnotated = globalTextFieldValue.annotatedString
        withContext(Dispatchers.Default) {
            val document = annotatedStringToDocument(finalAnnotated, lastPageHeight)
            onDocumentChange(document)
            dirtySinceMillis = null
        }
    }

    fun deleteTextOnPage(pageIndex: Int) {
        SharedPdfRichTextLog.d("controller.deleteTextOnPage requested page=$pageIndex")
        scope.launch {
            forceSyncAndClear()
            val layout = pageLayouts.find { it.pageIndex == pageIndex } ?: return@launch
            val start = layout.globalStartIndex
            val end = layout.globalEndIndex
            if (start >= end && start >= globalTextFieldValue.text.length) return@launch
            val original = globalTextFieldValue.annotatedString
            val builder = AnnotatedString.Builder()
            builder.append(original.subSequence(0, start))
            if (end < original.length) {
                builder.append(original.subSequence(end, original.length))
            }
            val (normalizedDeleted, paragraphShifts) =
                withNormalizedSyncedParagraphs(builder.toAnnotatedString())
            val newCursor = adjustRichOffset(start, paragraphShifts)
                .coerceIn(0, normalizedDeleted.length)
            setGlobalText(TextFieldValue(normalizedDeleted, TextRange(newCursor)), "deleteTextOnPage")
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = start)
            SharedPdfRichTextLog.d(
                "controller.deleteTextOnPage deleted page=$pageIndex range=$start..$end newLen=${globalTextFieldValue.text.length}"
            )
        }
    }

    fun handleBackspaceAtStart(): Boolean {
        SharedPdfRichTextLog.d(
            "controller.backspaceAtStart request activePage=$activePageIndex localSel=${localTextFieldValue.selection}"
        )
        if (localTextFieldValue.selection.start != 0 && localTextFieldValue.selection.start != 1) {
            SharedPdfRichTextLog.d("controller.backspaceAtStart not at local start")
            return false
        }
        val originalActivePage = activePageIndex
        if (originalActivePage <= 0) {
            SharedPdfRichTextLog.d("controller.backspaceAtStart ignored first page")
            return false
        }

        scope.launch {
            syncJob?.cancel()
            performSync(originalActivePage)
            val currentLayout = pageLayouts.find { it.pageIndex == originalActivePage } ?: return@launch
            val globalText = globalTextFieldValue.annotatedString
            val currentGlobalStart = currentLayout.globalStartIndex
            if (currentGlobalStart <= 0) return@launch

            val charBefore = globalText.text[currentGlobalStart - 1]
            SharedPdfRichTextLog.d(
                "controller.backspaceAtStart charBefore=${charBefore.code} globalStart=$currentGlobalStart"
            )
            if (charBefore == SHARED_PDF_PAGE_BREAK_CHAR) {
                handleBackspaceAcrossExplicitBreak(
                    originalActivePage = originalActivePage,
                    currentGlobalStart = currentGlobalStart,
                    globalText = globalText
                )
            } else {
                handleBackspaceAcrossOverflow(
                    originalActivePage = originalActivePage,
                    currentGlobalStart = currentGlobalStart,
                    globalText = globalText
                )
            }
        }
        return true
    }

    suspend fun saveImmediate() {
        if (isSaving) {
            SharedPdfRichTextLog.d("controller.saveImmediate ignored already saving")
            SharedPdfRichLayoutDiag.d("save.ignored alreadySaving active=$activePageIndex")
            return
        }
        SharedPdfRichTextLog.d(
            "controller.saveImmediate start activePage=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                "localLen=${localTextFieldValue.text.length}"
        )
        SharedPdfRichLayoutDiag.d(
            "save.start active=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                "layouts=${pageLayouts.richLayoutSummary()} " +
                "cfg=${lastPageWidth.richLogFloat()}x${lastPageHeight.richLogFloat()}"
        )
        isSaving = true
        try {
            saveJob?.cancel()
            syncJob?.cancel()
            val pageToSync = activePageIndex
            if (pageToSync != -1) {
                performSync(pageToSync)
                SharedPdfRichLayoutDiag.d(
                    "save.afterSync active=$activePageIndex layouts=${pageLayouts.richLayoutSummary()}"
                )
                delay(50)
                activePageIndex = -1
                cursorPageIndex = -1
                cursorRectInPage = null
                SharedPdfRichLayoutDiag.d(
                    "save.activePageNegated page=$pageToSync globalLen=${globalTextFieldValue.text.length}"
                )
            }
            withContext(Dispatchers.Default) {
                val document = annotatedStringToDocument(globalTextFieldValue.annotatedString, lastPageHeight)
                SharedPdfRichTextLog.d(
                    "controller.saveImmediate writing textLen=${document.text.length} spans=${document.spans.size}"
                )
                SharedPdfRichLayoutDiag.d(
                    "save.bake pageH=${lastPageHeight.richLogFloat()} textLen=${document.text.length} " +
                        "spans=${document.spans.size} paras=${document.paragraphs.size} " +
                        "layouts=${pageLayouts.richLayoutSummary()}"
                )
                onDocumentChange(document)
            }
        } finally {
            delay(100)
            isSaving = false
            SharedPdfRichTextLog.d("controller.saveImmediate done")
            SharedPdfRichLayoutDiag.d(
                "save.done active=$activePageIndex layouts=${pageLayouts.richLayoutSummary()}"
            )
        }
    }

    private suspend fun syncLocalToGlobal() {
        if (activePageIndex == -1) {
            SharedPdfRichTextLog.d("controller.syncLocalToGlobal abort no active page")
            return
        }
        val newGlobalCursorPos = mergeLocalIntoGlobal(activePageIndex) ?: return
        val newGlobalAnnotated = globalTextFieldValue.annotatedString

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val newLayouts = withContext(Dispatchers.Default) {
            engine.paginate(
                globalText = newGlobalAnnotated,
                pageWidthPx = lastPageWidth,
                pageHeightPx = lastPageHeight,
                textMeasurer = measurer,
                density = density,
                marginX = marginX(),
                marginY = marginY(),
                previousLayouts = pageLayouts,
                dirtyGlobalIndex = newGlobalCursorPos
            )
        }
        pageLayouts = newLayouts
        SharedPdfRichTextLog.d("controller.syncLocalToGlobal layouts=${newLayouts.richLayoutSummary()}")
        SharedPdfRichLayoutDiag.nextEpoch()
        SharedPdfRichLayoutDiag.d(
            "sync.localToGlobal layouts=${newLayouts.richLayoutSummary()} cursor=$newGlobalCursorPos"
        )
        val newActiveLayout = selectRichPageLayoutForCursor(newLayouts, newGlobalCursorPos)
        if (newActiveLayout != null) {
            activePageIndex = newActiveLayout.pageIndex
            val reExtractedText = newGlobalAnnotated.subSequence(
                newActiveLayout.globalStartIndex,
                newActiveLayout.globalEndIndex
            ).withoutTrailingSharedPdfPageBreak()
            val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + reExtractedText
            val newLocalCursor = (newGlobalCursorPos - newActiveLayout.globalStartIndex + 1)
                .coerceIn(0, textWithZwsp.length)
            localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(newLocalCursor))
            updateLocalCursor()
            SharedPdfRichTextLog.d(
                "controller.syncLocalToGlobal activePage=${activePageIndex} localCursor=$newLocalCursor " +
                    "cursor=${cursorRectInPage.richRectSummary()}"
            )
        } else {
            SharedPdfRichTextLog.d("controller.syncLocalToGlobal no active layout for cursor=$newGlobalCursorPos")
        }
    }

    /**
     * Merges the active page's local edits into global text (splice +
     * paragraph safety net) and returns the merged global cursor. No
     * repagination and no local refresh: callers follow up with repaginate
     * + refresh. Null when there is no active page/layout to merge.
     */
    private fun mergeLocalIntoGlobal(pageIdx: Int): Int? {
        if (pageIdx == -1) return null
        val layout = pageLayouts.find { it.pageIndex == pageIdx } ?: run {
            SharedPdfRichTextLog.d("controller.mergeLocal abort missing layout page=$pageIdx layouts=${pageLayouts.richLayoutSummary()}")
            return null
        }
        val globalStart = layout.globalStartIndex
        val globalEnd = layout.globalEndIndex
        val currentGlobal = globalTextFieldValue.annotatedString
        val localAnnotatedRaw = localTextFieldValue.annotatedString
        val localEditableAnnotated = if (localAnnotatedRaw.text.isNotEmpty()) {
            localAnnotatedRaw.subSequence(1, localAnnotatedRaw.length)
        } else {
            AnnotatedString("")
        }
        val shouldPreservePageBreak = layout.visibleText.text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR
        val localAnnotated = localEditableAnnotated.withRestoredTrailingSharedPdfPageBreak(shouldPreservePageBreak)
        val builder = AnnotatedString.Builder()
        builder.append(currentGlobal.subSequence(0, globalStart))
        builder.append(localAnnotated)
        if (globalEnd < currentGlobal.length) {
            builder.append(currentGlobal.subSequence(globalEnd, currentGlobal.length))
        }
        val (normalized, paragraphShifts) = withNormalizedSyncedParagraphs(builder.toAnnotatedString())
        val localSelectionStart = (localTextFieldValue.selection.start - 1).coerceAtLeast(0)
        val newGlobalCursorPos =
            adjustRichOffset(globalStart + localSelectionStart, paragraphShifts)
                .coerceIn(0, normalized.length)
        SharedPdfRichTextLog.d(
            "controller.mergeLocal page=$pageIdx global=$globalStart..$globalEnd " +
                "localEditableLen=${localEditableAnnotated.length} restoredBreak=$shouldPreservePageBreak " +
                "localLen=${localAnnotated.length} newLen=${normalized.length} cursor=$newGlobalCursorPos"
        )
        SharedPdfRichLayoutDiag.d(
            "merge.in page=$pageIdx globalStart=$globalStart globalEnd=$globalEnd " +
                "oldGlobalLen=${currentGlobal.length} localLen=${localAnnotatedRaw.length} " +
                "localEditableLen=${localEditableAnnotated.length}"
        )
        setGlobalText(TextFieldValue(normalized, TextRange(newGlobalCursorPos)), "mergeLocal")
        SharedPdfRichLayoutDiag.d(
            "merge.out page=$pageIdx newLen=${normalized.length} cursor=$newGlobalCursorPos"
        )
        debouncedSave(globalTextFieldValue)
        return newGlobalCursorPos
    }

    private suspend fun performSync(pageIdx: Int, checkCursorMove: Boolean = false) {
        if (pageIdx == -1) {
            SharedPdfRichTextLog.d("controller.performSync abort page=-1")
            return
        }
        val newGlobalCursorPos = mergeLocalIntoGlobal(pageIdx) ?: return
        val newGlobalAnnotated = globalTextFieldValue.annotatedString

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val newLayouts = withContext(Dispatchers.Default) {
            engine.paginate(
                globalText = newGlobalAnnotated,
                pageWidthPx = lastPageWidth,
                pageHeightPx = lastPageHeight,
                textMeasurer = measurer,
                density = density,
                marginX = marginX(),
                marginY = marginY(),
                previousLayouts = pageLayouts,
                dirtyGlobalIndex = newGlobalCursorPos
            )
        }
        pageLayouts = newLayouts
        SharedPdfRichTextLog.d("controller.performSync layouts=${newLayouts.richLayoutSummary()}")
        SharedPdfRichLayoutDiag.nextEpoch()
        SharedPdfRichLayoutDiag.d(
            "sync.perform page=$pageIdx cursor=$newGlobalCursorPos layouts=${newLayouts.richLayoutSummary()} " +
                "cfg=${lastPageWidth.richLogFloat()}x${lastPageHeight.richLogFloat()}"
        )

        if (checkCursorMove) {
            val newActiveLayout = newLayouts.find {
                newGlobalCursorPos >= it.globalStartIndex && newGlobalCursorPos < it.globalEndIndex
            } ?: newLayouts.find { newGlobalCursorPos == it.globalEndIndex }
            if (newActiveLayout != null) {
                activePageIndex = newActiveLayout.pageIndex
                val reExtracted = newGlobalAnnotated.subSequence(
                    newActiveLayout.globalStartIndex,
                    newActiveLayout.globalEndIndex
                ).withoutTrailingSharedPdfPageBreak()
                val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + reExtracted
                val newLocalCursor = (newGlobalCursorPos - newActiveLayout.globalStartIndex + 1)
                    .coerceIn(0, textWithZwsp.length)
                localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(newLocalCursor))
                updateLocalCursor()
                if (newActiveLayout.pageIndex != pageIdx) {
                    requestFocus()
                }
                SharedPdfRichTextLog.d(
                    "controller.performSync cursorMoved activePage=$activePageIndex localCursor=$newLocalCursor " +
                        "cursor=${cursorRectInPage.richRectSummary()}"
                )
            } else {
                SharedPdfRichTextLog.d("controller.performSync no layout for cursor=$newGlobalCursorPos")
            }
        }
    }

    private fun injectNewlinesLocal(gapPixels: Float) {
        val fontSizeSp = currentStyle.fontSize.value
        val densityValue = lastDensity?.density ?: 1f
        val lineHeightPx = (if (fontSizeSp.isNaN()) 16f else fontSizeSp) * densityValue * 1.3f
        val linesNeeded = (gapPixels / lineHeightPx).toInt().coerceAtLeast(1)
        val padding = "\n".repeat(linesNeeded)
        val original = localTextFieldValue.annotatedString
        val endsWithBreak = original.text.isNotEmpty() && original.text.last() == SHARED_PDF_PAGE_BREAK_CHAR
        SharedPdfRichTextLog.d(
            "controller.injectNewlines gap=${gapPixels.richLogFloat()} lineHeight=${lineHeightPx.richLogFloat()} " +
                "lines=$linesNeeded endsWithBreak=$endsWithBreak originalLen=${original.length}"
        )
        SharedPdfRichLayoutDiag.d(
            "injectNewlines gap=${gapPixels.richLogFloat()} fontSp=${fontSizeSp.richLogFloat()} " +
                "density=${densityValue.richLogFloat()} lineHeight=${lineHeightPx.richLogFloat()} " +
                "lines=$linesNeeded endsWithBreak=$endsWithBreak"
        )
        val builder = AnnotatedString.Builder()
        if (endsWithBreak) {
            builder.append(original.subSequence(0, original.length - 1))
            builder.pushStyle(currentStyle)
            builder.append(padding)
            builder.pop()
            currentFontPath?.takeIf { it.isNotBlank() }?.let {
                builder.addStringAnnotation(
                    tag = SHARED_PDF_RICH_FONT_PATH_TAG,
                    annotation = it,
                    start = original.length - 1,
                    end = original.length - 1 + padding.length
                )
            }
            builder.append(SHARED_PDF_PAGE_BREAK_CHAR.toString())
        } else {
            val start = original.length
            builder.append(original)
            builder.pushStyle(currentStyle)
            builder.append(padding)
            builder.pop()
            currentFontPath?.takeIf { it.isNotBlank() }?.let {
                builder.addStringAnnotation(
                    tag = SHARED_PDF_RICH_FONT_PATH_TAG,
                    annotation = it,
                    start = start,
                    end = start + padding.length
                )
            }
        }
        val next = builder.toAnnotatedString()
        val newCursor = if (endsWithBreak) next.length - 1 else next.length
        // Trailing padding never inherits lists (stays plain paragraphs).
        suppressListInheritOnce = true
        // Do NOT pre-assign localTextFieldValue here: onValueChanged diffs
        // against it, and a pre-set value looks like "no change" — the
        // padding would never reach global text (stale-global toggle/align
        // bugs) and no sync would be scheduled.
        onValueChanged(TextFieldValue(next, TextRange(newCursor)))
        SharedPdfRichTextLog.d(
            "controller.injectNewlines done newLen=${next.length} newCursor=$newCursor preview=\"${next.text.richPreview()}\""
        )
    }

    private fun repaginate(dirtyStartIndex: Int) {
        val measurer = lastTextMeasurer ?: run {
            SharedPdfRichTextLog.d("controller.repaginate abort no TextMeasurer dirty=$dirtyStartIndex")
            return
        }
        val density = lastDensity ?: run {
            SharedPdfRichTextLog.d("controller.repaginate abort no Density dirty=$dirtyStartIndex")
            return
        }
        val currentText = globalTextFieldValue.annotatedString
        val currentLayouts = pageLayouts
        SharedPdfRichTextLog.d(
            "controller.repaginate schedule dirty=$dirtyStartIndex textLen=${currentText.length} layouts=${currentLayouts.richLayoutSummary()}"
        )
        scope.launch {
            val newLayouts = withContext(Dispatchers.Default) {
                engine.paginate(
                    globalText = currentText,
                    pageWidthPx = lastPageWidth,
                    pageHeightPx = lastPageHeight,
                    textMeasurer = measurer,
                    density = density,
                    marginX = marginX(),
                    marginY = marginY(),
                    previousLayouts = currentLayouts,
                    dirtyGlobalIndex = dirtyStartIndex
                )
            }
            pageLayouts = newLayouts
            SharedPdfRichTextLog.d("controller.repaginate done layouts=${newLayouts.richLayoutSummary()}")
            SharedPdfRichLayoutDiag.nextEpoch()
            SharedPdfRichLayoutDiag.d(
                "repaginate.done dirty=$dirtyStartIndex textLen=${currentText.length} " +
                    "cfg=${lastPageWidth.richLogFloat()}x${lastPageHeight.richLogFloat()} " +
                    "layouts=${newLayouts.richLayoutSummary()}"
            )
        }
    }

    private fun repaginateSync(dirtyStartIndex: Int) {
        val measurer = lastTextMeasurer ?: run {
            SharedPdfRichTextLog.d("controller.repaginateSync abort no TextMeasurer dirty=$dirtyStartIndex")
            return
        }
        val density = lastDensity ?: run {
            SharedPdfRichTextLog.d("controller.repaginateSync abort no Density dirty=$dirtyStartIndex")
            return
        }
        SharedPdfRichTextLog.d(
            "controller.repaginateSync start dirty=$dirtyStartIndex textLen=${globalTextFieldValue.text.length}"
        )
        pageLayouts = engine.paginate(
            globalText = globalTextFieldValue.annotatedString,
            pageWidthPx = lastPageWidth,
            pageHeightPx = lastPageHeight,
            textMeasurer = measurer,
            density = density,
            marginX = marginX(),
            marginY = marginY(),
            previousLayouts = pageLayouts,
            dirtyGlobalIndex = dirtyStartIndex
        )
        SharedPdfRichTextLog.d("controller.repaginateSync done layouts=${pageLayouts.richLayoutSummary()}")
        SharedPdfRichLayoutDiag.nextEpoch()
        SharedPdfRichLayoutDiag.d(
            "repaginateSync.done dirty=$dirtyStartIndex " +
                "cfg=${lastPageWidth.richLogFloat()}x${lastPageHeight.richLogFloat()} " +
                "layouts=${pageLayouts.richLayoutSummary()}"
        )
    }

    private fun updateLocalCursor() {
        val measurer = lastTextMeasurer ?: run {
            SharedPdfRichTextLog.d("controller.updateLocalCursor abort no TextMeasurer")
            return
        }
        val density = lastDensity ?: run {
            SharedPdfRichTextLog.d("controller.updateLocalCursor abort no Density")
            return
        }
        val selection = localTextFieldValue.selection
        if (selection.collapsed) {
            val measureResult = measurer.measure(
                text = localTextFieldValue.annotatedString,
                style = TextStyle(fontSize = 16.sp),
                constraints = Constraints(
                    minWidth = editorWidth().toInt(),
                    maxWidth = editorWidth().toInt(),
                ),
                density = density
            )
            val safeOffset = selection.start.coerceIn(0, localTextFieldValue.text.length)
            cursorPageIndex = activePageIndex
            cursorRectInPage = measureResult.getCursorRect(safeOffset).translate(marginX(), marginY())
            SharedPdfRichTextLog.d(
                "controller.updateLocalCursor page=$cursorPageIndex safeOffset=$safeOffset " +
                    "rect=${cursorRectInPage.richRectSummary()}"
            )
        } else {
            SharedPdfRichTextLog.d("controller.updateLocalCursor skipped non-collapsed selection=$selection")
        }
    }

    private fun updateGlobalCursor() {
        val selection = globalTextFieldValue.selection
        if (isCursorVisible && showCursorOverride && selection.collapsed) {
            val cursorIndex = selection.start
            val layout = selectRichPageLayoutForCursor(pageLayouts, cursorIndex)
            val measurer = lastTextMeasurer
            val density = lastDensity
            if (layout != null && measurer != null && density != null) {
                val measureResult = measurer.measure(
                    text = layout.visibleText,
                    style = TextStyle(fontSize = 16.sp),
                    constraints = Constraints(
                        minWidth = editorWidth().toInt(),
                        maxWidth = editorWidth().toInt(),
                    ),
                    density = density
                )
                val localIndex = (cursorIndex - layout.globalStartIndex).coerceIn(0, layout.visibleText.length)
                cursorPageIndex = layout.pageIndex
                cursorRectInPage = measureResult.getCursorRect(localIndex).translate(marginX(), marginY())
                SharedPdfRichTextLog.d(
                    "controller.updateGlobalCursor global=$cursorIndex page=$cursorPageIndex local=$localIndex " +
                        "rect=${cursorRectInPage.richRectSummary()}"
                )
            } else {
                SharedPdfRichTextLog.d(
                    "controller.updateGlobalCursor missing layout/measurer cursor=$cursorIndex layouts=${pageLayouts.richLayoutSummary()}"
                )
            }
        } else {
            cursorPageIndex = -1
            cursorRectInPage = null
            SharedPdfRichTextLog.d("controller.updateGlobalCursor cleared visible=$isCursorVisible override=$showCursorOverride selection=$selection")
        }
    }

    private suspend fun forceSyncAndClear() {
        if (activePageIndex != -1) {
            SharedPdfRichLayoutDiag.d(
                "forceSyncAndClear.enter active=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                    "localLen=${localTextFieldValue.text.length}"
            )
            performSync(activePageIndex)
            activePageIndex = -1
            cursorPageIndex = -1
            cursorRectInPage = null
            localTextFieldValue = TextFieldValue("")
            SharedPdfRichLayoutDiag.d(
                "forceSyncAndClear.done globalLen=${globalTextFieldValue.text.length}"
            )
        }
    }

    private suspend fun handleBackspaceAcrossExplicitBreak(
        originalActivePage: Int,
        currentGlobalStart: Int,
        globalText: AnnotatedString
    ) {
        val targetPageIndex = originalActivePage - 1
        val builder = AnnotatedString.Builder()
        builder.append(globalText.subSequence(0, currentGlobalStart - 1))
        builder.append(globalText.subSequence(currentGlobalStart, globalText.length))
        var intermediateGlobal = builder.toAnnotatedString()
        var newCursorPos = (currentGlobalStart - 1).coerceAtLeast(0)

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val editorHeight = (lastPageHeight - (marginY() * 2f)).coerceAtLeast(10f)
        val targetLayout = pageLayouts.find { it.pageIndex == targetPageIndex }
        val safeTargetStart = (targetLayout?.globalStartIndex ?: 0).coerceIn(0, newCursorPos)
        val pageTextToMeasure = intermediateGlobal.subSequence(safeTargetStart, newCursorPos)
        val measureResult = measurer.measure(
            text = pageTextToMeasure,
            style = TextStyle(fontSize = 16.sp),
            constraints = Constraints(maxWidth = editorWidth().toInt()),
            density = density
        )
        val gap = editorHeight - measureResult.size.height.toFloat()
        if (gap > 0f) {
            val fontSizeSp = currentStyle.fontSize.value
            val lineHeightPx = (if (fontSizeSp.isNaN() || fontSizeSp <= 0f) 16f else fontSizeSp) * density.density * 1.3f
            val linesNeeded = (gap / lineHeightPx).toInt().coerceAtLeast(0)
            if (linesNeeded > 0) {
                val padding = "\n".repeat(linesNeeded)
                val paddedBuilder = AnnotatedString.Builder()
                paddedBuilder.append(intermediateGlobal.subSequence(0, newCursorPos))
                paddedBuilder.pushStyle(currentStyle)
                paddedBuilder.append(padding)
                paddedBuilder.pop()
                currentFontPath?.takeIf { it.isNotBlank() }?.let {
                    paddedBuilder.addStringAnnotation(
                        tag = SHARED_PDF_RICH_FONT_PATH_TAG,
                        annotation = it,
                        start = newCursorPos,
                        end = newCursorPos + padding.length
                    )
                }
                paddedBuilder.append(intermediateGlobal.subSequence(newCursorPos, intermediateGlobal.length))
                intermediateGlobal = paddedBuilder.toAnnotatedString()
                newCursorPos += padding.length
            }
        }

        setGlobalText(TextFieldValue(intermediateGlobal, TextRange(newCursorPos)), "backspaceExplicitBreak")
        debouncedSave(globalTextFieldValue)
        val finalLayouts = withContext(Dispatchers.Default) {
            engine.paginate(intermediateGlobal, lastPageWidth, lastPageHeight, measurer, density, marginX(), marginY())
        }
        pageLayouts = finalLayouts
        val finalActiveLayout = finalLayouts.find { it.pageIndex == targetPageIndex }
            ?: finalLayouts.findLast { newCursorPos >= it.globalStartIndex && newCursorPos <= it.globalEndIndex }
        if (finalActiveLayout != null) {
            activePageIndex = finalActiveLayout.pageIndex
            val reExtracted = intermediateGlobal.subSequence(
                finalActiveLayout.globalStartIndex,
                finalActiveLayout.globalEndIndex
            ).withoutTrailingSharedPdfPageBreak()
            val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + reExtracted
            val localCursor = (newCursorPos - finalActiveLayout.globalStartIndex + 1).coerceIn(0, textWithZwsp.length)
            localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(localCursor))
            updateLocalCursor()
            requestFocus()
        }
    }

    private suspend fun handleBackspaceAcrossOverflow(
        originalActivePage: Int,
        currentGlobalStart: Int,
        globalText: AnnotatedString
    ) {
        val builder = AnnotatedString.Builder()
        builder.append(globalText.subSequence(0, currentGlobalStart - 1))
        builder.append(globalText.subSequence(currentGlobalStart, globalText.length))
        val newGlobalText = builder.toAnnotatedString()
        val newCursorPos = (currentGlobalStart - 1).coerceAtLeast(0)
        setGlobalText(TextFieldValue(newGlobalText, TextRange(newCursorPos)), "backspaceOverflow")
        debouncedSave(globalTextFieldValue)

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val finalLayouts = withContext(Dispatchers.Default) {
            engine.paginate(newGlobalText, lastPageWidth, lastPageHeight, measurer, density, marginX(), marginY())
        }
        pageLayouts = finalLayouts
        val finalActiveLayout = finalLayouts.find {
            newCursorPos >= it.globalStartIndex && newCursorPos < it.globalEndIndex
        } ?: finalLayouts.find { newCursorPos == it.globalEndIndex }
        if (finalActiveLayout != null) {
            activePageIndex = finalActiveLayout.pageIndex
            val reExtracted = newGlobalText.subSequence(
                finalActiveLayout.globalStartIndex,
                finalActiveLayout.globalEndIndex
            ).withoutTrailingSharedPdfPageBreak()
            val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + reExtracted
            val localCursor = (newCursorPos - finalActiveLayout.globalStartIndex + 1).coerceIn(0, textWithZwsp.length)
            localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(localCursor))
            updateLocalCursor()
            requestFocus()
        } else {
            activePageIndex = originalActivePage
        }
    }

    private fun debouncedSave(tfv: TextFieldValue) {
        val now = currentTimestamp()
        val dirtySince = dirtySinceMillis ?: now.also { dirtySinceMillis = it }
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.Default) {
            val remainingMaxDelay = (SHARED_PDF_RICH_TEXT_MAX_SAVE_LATENCY_MILLIS - (now - dirtySince))
                .coerceAtLeast(0L)
            delay(minOf(SHARED_PDF_RICH_TEXT_SAVE_DEBOUNCE_MILLIS, remainingMaxDelay))
            val document = annotatedStringToDocument(tfv.annotatedString, lastPageHeight)
            onDocumentChange(document)
            dirtySinceMillis = null
        }
    }

    private fun requestFocus() {
        runCatching { focusRequester.requestFocus() }
            .onSuccess { SharedPdfRichTextLog.d("controller.requestFocus success") }
            .onFailure { SharedPdfRichTextLog.d("controller.requestFocus failed error=${it.message}") }
        runCatching { onEditingFocusRequested() }
    }

    private fun editorWidth(): Float = (lastPageWidth - (marginX() * 2f)).coerceAtLeast(10f)

    private fun marginX(): Float = lastPageWidth * 0.1f

    private fun marginY(): Float = lastPageHeight * 0.08f
}

fun SharedPdfTextStyleConfig.toSharedPdfRichSpanStyle(): SpanStyle {
    val safeFontSize = fontSize.takeIf { it.isFinite() && it > 0f }?.coerceIn(8f, 96f) ?: 16f
    return SpanStyle(
        color = Color(colorArgb),
        background = Color(backgroundColorArgb),
        fontSize = safeFontSize.sp,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
        textDecoration = richTextDecoration(isUnderline, isStrikeThrough)
    )
}

internal fun AnnotatedString.withScaledSharedPdfRichFontSizes(scale: Float): AnnotatedString {
    if (!scale.isFinite() || scale <= 0f || abs(scale - 1f) <= 0.001f) return this
    if (spanStyles.none { it.item.fontSize.isSp }) return this

    val builder = AnnotatedString.Builder(text)
    spanStyles.forEach { range ->
        val style = range.item
        builder.addStyle(
            style = if (style.fontSize.isSp) {
                style.copy(fontSize = (style.fontSize.value * scale).sp)
            } else {
                style
            },
            start = range.start,
            end = range.end
        )
    }
    paragraphStyles.forEach { range ->
        builder.addStyle(range.item, range.start, range.end)
    }
    getStringAnnotations(
        start = 0,
        end = length
    ).forEach { annotation ->
        builder.addStringAnnotation(
            tag = annotation.tag,
            annotation = annotation.item,
            start = annotation.start,
            end = annotation.end
        )
    }
    return builder.toAnnotatedString()
}

/**
 * Keeps interactive and restored rich-text spans in the same bounded unit as
 * the persisted document (a fraction of the current page height).
 */
internal fun AnnotatedString.withSanitizedSharedPdfRichFontSizes(pageHeightPx: Float): AnnotatedString {
    if (spanStyles.none { it.item.fontSize.isSp }) return this
    val safePageHeight = pageHeightPx.takeIf { it.isFinite() && it > 0f } ?: 1_000f
    val minFontSize = SharedPdfTextAnnotationDefaults.MinPageRelativeFontSize * safePageHeight
    val maxFontSize = SharedPdfTextAnnotationDefaults.MaxPageRelativeFontSize * safePageHeight
    var clamped = 0
    var sampleInMin = Float.MAX_VALUE
    var sampleInMax = Float.MIN_VALUE
    var sampleOutMin = Float.MAX_VALUE
    var sampleOutMax = Float.MIN_VALUE
    val builder = AnnotatedString.Builder(text)
    spanStyles.forEach { range ->
        val style = range.item
        if (style.fontSize.isSp) {
            val inSize = style.fontSize.value
            if (inSize.isFinite() && inSize > 0f) {
                sampleInMin = minOf(sampleInMin, inSize)
                sampleInMax = maxOf(sampleInMax, inSize)
            }
        }
        val safeFontSize = style.fontSize.value
            .takeIf { it.isFinite() && it > 0f }
            ?.coerceIn(minFontSize, maxFontSize)
            ?: (16f.coerceIn(minFontSize, maxFontSize))
        if (style.fontSize.isSp && style.fontSize.value.isFinite() && style.fontSize.value > 0f &&
            safeFontSize != style.fontSize.value
        ) {
            clamped++
            sampleOutMin = minOf(sampleOutMin, safeFontSize)
            sampleOutMax = maxOf(sampleOutMax, safeFontSize)
        }
        builder.addStyle(
            style = if (style.fontSize.isSp) style.copy(fontSize = safeFontSize.sp) else style,
            start = range.start,
            end = range.end
        )
    }
    if (clamped > 0) {
        SharedPdfRichLayoutDiag.d(
            "fontSanitize.clamp pageH=${safePageHeight.richLogFloat()} spans=$clamped " +
                "inMin=${sampleInMin.richLogFloat()} inMax=${sampleInMax.richLogFloat()} " +
                "outMin=${sampleOutMin.richLogFloat()} outMax=${sampleOutMax.richLogFloat()} " +
                "allowed=${minFontSize.richLogFloat()}..${maxFontSize.richLogFloat()}"
        )
    }
    paragraphStyles.forEach { range ->
        builder.addStyle(range.item, range.start, range.end)
    }
    getStringAnnotations(
        start = 0,
        end = length
    ).forEach { annotation ->
        builder.addStringAnnotation(
            tag = annotation.tag,
            annotation = annotation.item,
            start = annotation.start,
            end = annotation.end
        )
    }
    return builder.toAnnotatedString()
}

private fun TextFieldValue.withScaledSharedPdfRichFontSizes(scale: Float): TextFieldValue {
    return copy(annotatedString = annotatedString.withScaledSharedPdfRichFontSizes(scale))
}

fun SharedPdfRichTextController.currentSharedPdfTextStyleConfig(): SharedPdfTextStyleConfig {
    val decoration = currentStyle.textDecoration ?: TextDecoration.None
    return SharedPdfTextStyleConfig(
        colorArgb = currentStyle.color.takeIf { it.isSpecified }?.toArgb() ?: Color.Black.toArgb(),
        backgroundColorArgb = currentStyle.background.takeIf { it.isSpecified }?.toArgb() ?: Color.Transparent.toArgb(),
        fontSize = if (currentStyle.fontSize.isSp) currentStyle.fontSize.value else 16f,
        isBold = currentStyle.fontWeight == FontWeight.Bold,
        isItalic = currentStyle.fontStyle == FontStyle.Italic,
        isUnderline = decoration.contains(TextDecoration.Underline),
        isStrikeThrough = decoration.contains(TextDecoration.LineThrough),
        fontPath = currentFontPath,
        fontName = currentFontName
    )
}

fun SharedPdfRichTextController.updateCurrentSharedPdfTextStyle(style: SharedPdfTextStyleConfig) {
    updateCurrentStyle(
        style = style.toSharedPdfRichSpanStyle(),
        fontPath = style.fontPath,
        fontName = style.fontName
    )
}

private data class MutableSpan(
    var start: Int,
    var end: Int,
    val item: SpanStyle
)

private data class MutableStringAnnotation(
    var start: Int,
    var end: Int,
    val tag: String,
    val item: String
)

private fun AnnotatedString.Range<SpanStyle>.shiftedByTextChange(
    diff: Int,
    changeStart: Int,
    changeEndOld: Int
): MutableSpan? {
    return shiftRange(start, end, diff, changeStart, changeEndOld)
        ?.let { (nextStart, nextEnd) -> MutableSpan(nextStart, nextEnd, item) }
}

private fun AnnotatedString.Range<String>.shiftedByTextChange(
    diff: Int,
    changeStart: Int,
    changeEndOld: Int
): MutableStringAnnotation? {
    return shiftRange(start, end, diff, changeStart, changeEndOld)
        ?.let { (nextStart, nextEnd) ->
            MutableStringAnnotation(
                start = nextStart,
                end = nextEnd,
                tag = tag,
                item = item
            )
        }
}

private fun shiftRange(
    start: Int,
    end: Int,
    diff: Int,
    changeStart: Int,
    changeEndOld: Int
): Pair<Int, Int>? {
    val next = if (diff > 0) {
        when {
            end <= changeStart -> start to end
            start >= changeStart -> (start + diff) to (end + diff)
            else -> start to (end + diff)
        }
    } else {
        when {
            end <= changeStart -> start to end
            start >= changeEndOld -> (start + diff) to (end + diff)
            else -> {
                val newStart = if (start < changeStart) start else changeStart
                val newEnd = (end + diff).coerceAtLeast(changeStart)
                newStart to newEnd
            }
        }
    }
    return next.takeIf { it.first < it.second }
}

private fun List<MutableSpan>.compactSpans(): List<MutableSpan> {
    return groupBy { it.item }
        .flatMap { (_, spans) ->
            spans.sortedBy { it.start }.mergeAdjacentRanges { current, next ->
                current.copy(end = maxOf(current.end, next.end))
            }
        }
}

private fun List<MutableStringAnnotation>.compactStringAnnotations(): List<MutableStringAnnotation> {
    return groupBy { it.tag to it.item }
        .flatMap { (_, annotations) ->
            annotations.sortedBy { it.start }.mergeAdjacentRanges { current, next ->
                current.copy(end = maxOf(current.end, next.end))
            }
        }
}

private fun <T> List<T>.mergeAdjacentRanges(merge: (T, T) -> T): List<T>
    where T : Any {
    if (isEmpty()) return emptyList()
    val result = mutableListOf<T>()
    var current = first()
    for (i in 1 until size) {
        val next = this[i]
        val currentEnd = current.richRangeEnd()
        val nextStart = next.richRangeStart()
        if (nextStart <= currentEnd) {
            current = merge(current, next)
        } else {
            result += current
            current = next
        }
    }
    result += current
    return result
}

private fun Any.richRangeStart(): Int {
    return when (this) {
        is MutableSpan -> start
        is MutableStringAnnotation -> start
        else -> 0
    }
}

private fun Any.richRangeEnd(): Int {
    return when (this) {
        is MutableSpan -> end
        is MutableStringAnnotation -> end
        else -> 0
    }
}

private fun AnnotatedString.withAppliedRichStyle(
    style: SpanStyle,
    fontPath: String?,
    selection: TextRange
): AnnotatedString {
    val start = selection.min.coerceIn(0, length)
    val end = selection.max.coerceIn(start, length)
    if (start == end) return this
    val builder = AnnotatedString.Builder(this)
    builder.addStyle(style, start, end)
    fontPath?.takeIf { it.isNotBlank() }?.let {
        builder.addStringAnnotation(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            annotation = it,
            start = start,
            end = end
        )
    }
    return builder.toAnnotatedString()
}

private fun richTextDecoration(
    underline: Boolean,
    strikeThrough: Boolean
): TextDecoration {
    val decorations = mutableListOf<TextDecoration>()
    if (underline) decorations += TextDecoration.Underline
    if (strikeThrough) decorations += TextDecoration.LineThrough
    return if (decorations.isEmpty()) TextDecoration.None else TextDecoration.combine(decorations)
}

private fun List<SharedPdfRichPageLayout>.richLayoutSummary(): String {
    if (isEmpty()) return "[]"
    return joinToString(prefix = "[", postfix = "]", limit = 8, truncated = "...") { layout ->
        "p${layout.pageIndex}:${layout.globalStartIndex}-${layout.globalEndIndex}/len${layout.visibleText.length}"
    }
}

private fun String.richPreview(maxLength: Int = 80): String {
    return replace("\n", "\\n")
        .replace(SHARED_PDF_PAGE_BREAK_CHAR.toString(), "\\f")
        .let { if (it.length <= maxLength) it else it.take(maxLength) + "..." }
}

private fun Float.richLogFloat(): String {
    return if (isFinite()) {
        val rounded = kotlin.math.round(this * 10f) / 10f
        rounded.toString()
    } else {
        toString()
    }
}

private fun Offset.richOffsetSummary(): String {
    return "(${x.richLogFloat()},${y.richLogFloat()})"
}

private fun Rect?.richRectSummary(): String {
    if (this == null) return "null"
    return "(${left.richLogFloat()},${top.richLogFloat()},${right.richLogFloat()},${bottom.richLogFloat()})"
}

private fun SpanStyle.richStyleSummary(): String {
    return "color=$color bg=$background size=$fontSize weight=$fontWeight style=$fontStyle deco=$textDecoration"
}
