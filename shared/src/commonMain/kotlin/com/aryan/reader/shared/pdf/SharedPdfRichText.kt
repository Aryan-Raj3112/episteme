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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

object SharedPdfRichTextLog {
    var enabled: Boolean = true

    fun d(message: String) {
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
    val spans: List<SharedPdfRichSpan> = emptyList()
)

data class SharedPdfRichPageLayout(
    val pageIndex: Int,
    val visibleText: AnnotatedString,
    val globalStartIndex: Int,
    val globalEndIndex: Int,
    val pageHeightPx: Float
)

object SharedPdfRichTextSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    fun encode(document: SharedPdfRichDocument): String {
        return json.encodeToString(
            JsonElement.serializer(),
            encodeElement(document)
        )
    }

    fun encodeElement(document: SharedPdfRichDocument): JsonElement {
        return JsonObject(
            mapOf(
                "text" to JsonPrimitive(document.text),
                "spans" to JsonArray(
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
                                put("fp", span.fontPath?.let(::JsonPrimitive) ?: JsonNull)
                            }
                        )
                    }
                )
            )
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
        val root = runCatching { element.jsonObject }.getOrNull() ?: return SharedPdfRichDocument()
        val text = root.string("text").orEmpty()
        val spans = root["spans"]
            ?.jsonArrayOrNull()
            ?.mapNotNull { spanElement ->
                val obj = spanElement.jsonObjectOrNull() ?: return@mapNotNull null
                val start = obj.int("s") ?: obj.int("start") ?: return@mapNotNull null
                val end = obj.int("e") ?: obj.int("end") ?: return@mapNotNull null
                if (start < 0 || end <= start || start >= text.length) return@mapNotNull null
                SharedPdfRichSpan(
                    start = start,
                    end = end.coerceAtMost(text.length),
                    color = obj.int("c") ?: obj.int("color") ?: Color.Black.toArgb(),
                    backgroundColor = obj.int("bg") ?: obj.int("backgroundColor") ?: Color.Transparent.toArgb(),
                    fontSizeNorm = obj.float("sz") ?: obj.float("fontSizeNorm") ?: 0.015f,
                    isBold = obj.boolean("b") ?: obj.boolean("isBold") ?: false,
                    isItalic = obj.boolean("i") ?: obj.boolean("isItalic") ?: false,
                    isUnderline = obj.boolean("u") ?: obj.boolean("isUnderline") ?: false,
                    isStrikethrough = obj.boolean("st") ?: obj.boolean("isStrikethrough") ?: false,
                    fontPath = obj.string("fp") ?: obj.string("fontPath")
                )
            }
            ?.sortedBy { it.start }
            .orEmpty()
        SharedPdfRichTextLog.d("serializer.decodeElement textLen=${text.length} spans=${spans.size}")
        return SharedPdfRichDocument(text = text, spans = spans)
    }

    private fun JsonElement.jsonArrayOrNull(): JsonArray? {
        if (this is JsonNull) return null
        return runCatching { jsonArray }.getOrNull()
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? {
        if (this is JsonNull) return null
        return runCatching { jsonObject }.getOrNull()
    }

    private fun JsonObject.string(name: String): String? {
        return runCatching { this[name]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
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
        rangeEnd: Int = document.text.length
    ): AnnotatedString {
        val safeGlobalStart = rangeStart.coerceIn(0, document.text.length)
        val safeGlobalEnd = rangeEnd.coerceIn(safeGlobalStart, document.text.length)
        if (safeGlobalStart == safeGlobalEnd) return AnnotatedString("")

        val textSubstring = document.text.substring(safeGlobalStart, safeGlobalEnd)
        return buildAnnotatedString {
            append(textSubstring)
            for (span in document.spans) {
                if (span.start >= safeGlobalEnd) break
                if (span.end <= safeGlobalStart) continue

                val intersectionStart = maxOf(span.start, safeGlobalStart)
                val intersectionEnd = minOf(span.end, safeGlobalEnd)
                if (intersectionStart >= intersectionEnd) continue

                val localStart = intersectionStart - safeGlobalStart
                val localEnd = intersectionEnd - safeGlobalStart
                val fontSizePx = if (pageHeightPx > 0) span.fontSizeNorm * pageHeightPx else 16f
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
                        )
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
        }
    }

    fun fromAnnotatedString(text: AnnotatedString, pageHeightPx: Float): SharedPdfRichDocument {
        if (text.text.isEmpty()) return SharedPdfRichDocument()

        val spans = mutableListOf<SharedPdfRichSpan>()
        val fontPathAnnotations = text.getStringAnnotations(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = text.length
        )
        val changePoints = sortedSetOf(0, text.length)
        text.spanStyles.forEach {
            changePoints.add(it.start)
            changePoints.add(it.end)
        }
        fontPathAnnotations.forEach {
            changePoints.add(it.start)
            changePoints.add(it.end)
        }

        val sortedPoints = changePoints.toList()
        for (i in 0 until sortedPoints.size - 1) {
            val start = sortedPoints[i]
            val end = sortedPoints[i + 1]
            if (start >= end) continue

            val activeStyles = text.spanStyles.filter { it.start <= start && it.end >= end }
            val activeFontPath = fontPathAnnotations
                .lastOrNull { it.start <= start && it.end >= end }
                ?.item
                ?.takeIf { it.isNotBlank() }
            if (activeStyles.isEmpty() && activeFontPath == null) continue

            var effective = SpanStyle(color = Color.Black, fontSize = 16.sp)
            activeStyles.forEach { effective = effective.merge(it.item) }
            val currentDecoration = effective.textDecoration ?: TextDecoration.None
            val fontSizeNorm = if (effective.fontSize.isSp) {
                if (pageHeightPx > 0) effective.fontSize.value / pageHeightPx else 0.015f
            } else {
                0.015f
            }

            val newSpan = SharedPdfRichSpan(
                start = start,
                end = end,
                color = effective.color.takeIf { it.isSpecified }?.toArgb() ?: Color.Black.toArgb(),
                backgroundColor = effective.background.takeIf { it.isSpecified }?.toArgb() ?: Color.Transparent.toArgb(),
                fontSizeNorm = fontSizeNorm,
                isBold = effective.fontWeight == FontWeight.Bold,
                isItalic = effective.fontStyle == FontStyle.Italic,
                isUnderline = currentDecoration.contains(TextDecoration.Underline),
                isStrikethrough = currentDecoration.contains(TextDecoration.LineThrough),
                fontPath = activeFontPath
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
        return SharedPdfRichDocument(text = text.text, spans = spans)
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
        val totalLen = globalText.length
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
        val rawText = globalText.text

        while (segmentStart < totalLen) {
            val breakIndex = rawText.indexOf(SHARED_PDF_PAGE_BREAK_CHAR, startIndex = segmentStart)
            val hasExplicitBreak = breakIndex != -1
            val contentEnd = if (hasExplicitBreak) breakIndex else totalLen
            val segmentEnd = if (hasExplicitBreak) breakIndex + 1 else totalLen

            currentPageIndex = newPages.appendMeasuredRichTextSegment(
                globalText = globalText,
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
            globalText = globalText,
            pageHeightPx = pageHeightPx
        )
        SharedPdfRichTextLog.d("paginate done -> ${result.richLayoutSummary()}")
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
            constraints = Constraints(maxWidth = editorWidth.toInt(), maxHeight = Constraints.Infinity),
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

internal fun AnnotatedString.withoutTrailingSharedPdfPageBreak(): AnnotatedString {
    return if (text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR) {
        subSequence(0, length - 1)
    } else {
        this
    }
}

private fun AnnotatedString.withRestoredTrailingSharedPdfPageBreak(shouldRestore: Boolean): AnnotatedString {
    if (!shouldRestore) return this
    if (text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR) return this
    return this + AnnotatedString(SHARED_PDF_PAGE_BREAK_CHAR.toString())
}

internal fun List<SharedPdfRichPageLayout>.withTrailingBlankRichTextPageIfNeeded(
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
    private val onDocumentChange: suspend (SharedPdfRichDocument) -> Unit = {}
) {
    var globalTextFieldValue by mutableStateOf(
        TextFieldValue(SharedPdfRichTextMapper.toAnnotatedString(initialDocument, 1414f))
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
    private var syncJob: Job? = null
    private var tapJob: Job? = null
    private var isSaving = false

    fun replaceDocument(document: SharedPdfRichDocument) {
        SharedPdfRichTextLog.d(
            "controller.replaceDocument textLen=${document.text.length} spans=${document.spans.size} " +
                "oldLayouts=${pageLayouts.size} activePage=$activePageIndex"
        )
        saveJob?.cancel()
        syncJob?.cancel()
        tapJob?.cancel()
        activePageIndex = -1
        cursorPageIndex = -1
        cursorRectInPage = null
        isCursorVisible = false
        localTextFieldValue = TextFieldValue("")
        globalTextFieldValue = TextFieldValue(
            SharedPdfRichTextMapper.toAnnotatedString(document, lastPageHeight)
        )
        repaginate(dirtyStartIndex = 0)
    }

    fun updateLayoutConfig(width: Float, height: Float, density: Density, measurer: TextMeasurer) {
        if (lastPageWidth != width || lastPageHeight != height || lastDensity != density || lastTextMeasurer != measurer) {
            val previousPageHeight = lastPageHeight
            val fontScale = if (previousPageHeight > 0f && height > 0f) {
                height / previousPageHeight
            } else {
                1f
            }
            val shouldScaleFonts = abs(fontScale - 1f) > 0.001f
            SharedPdfRichTextLog.d(
                "controller.layoutConfig width=${width.richLogFloat()} height=${height.richLogFloat()} " +
                    "density=${density.density.richLogFloat()} old=${lastPageWidth.richLogFloat()}x${previousPageHeight.richLogFloat()} " +
                    "fontScale=${fontScale.richLogFloat()}"
            )
            if (shouldScaleFonts) {
                saveJob?.cancel()
                globalTextFieldValue = globalTextFieldValue.withScaledSharedPdfRichFontSizes(fontScale)
                localTextFieldValue = localTextFieldValue.withScaledSharedPdfRichFontSizes(fontScale)
                if (globalTextFieldValue.text.isNotEmpty()) {
                    debouncedSave(globalTextFieldValue)
                }
            }
            lastPageWidth = width
            lastPageHeight = height
            lastDensity = density
            lastTextMeasurer = measurer
            repaginate(dirtyStartIndex = 0)
        }
    }

    fun clearSelection() {
        SharedPdfRichTextLog.d(
            "controller.clearSelection activePage=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                "localLen=${localTextFieldValue.text.length}"
        )
        isCursorVisible = false
        val pageToSync = activePageIndex
        if (pageToSync != -1) {
            scope.launch {
                performSync(pageToSync)
                if (activePageIndex == pageToSync) activePageIndex = -1
            }
        }
        if (globalTextFieldValue.text.isNotEmpty()) {
            globalTextFieldValue = globalTextFieldValue.copy(
                selection = TextRange(globalTextFieldValue.text.length)
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
                    globalTextFieldValue = newValue.copy(annotatedString = globalTextFieldValue.annotatedString)
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
        SharedPdfRichTextLog.d(
            "controller.textChanged activePage=$activePageIndex oldLen=${oldText.length} newLen=${newText.length} " +
                "diff=$diff cursor=$cursor change=$changeStart..$changeEndOld style=${currentStyle.richStyleSummary()} " +
                "preview=\"${newText.richPreview()}\""
        )
        val mutableSpans = oldAnnotated.spanStyles.mapNotNull {
            it.shiftedByTextChange(
                diff = diff,
                changeStart = changeStart,
                changeEndOld = changeEndOld
            )
        }.toMutableList()
        val mutableFontAnnotations = oldAnnotated.getStringAnnotations(
            tag = SHARED_PDF_RICH_FONT_PATH_TAG,
            start = 0,
            end = oldAnnotated.length
        ).mapNotNull {
            it.shiftedByTextChange(
                diff = diff,
                changeStart = changeStart,
                changeEndOld = changeEndOld
            )
        }.toMutableList()

        if (diff > 0) {
            val start = (cursor - diff).coerceAtLeast(0)
            mutableSpans += MutableSpan(start, cursor, currentStyle)
            currentFontPath?.takeIf { it.isNotBlank() }?.let { fontPath ->
                mutableFontAnnotations += MutableStringAnnotation(
                    start = start,
                    end = cursor,
                    tag = SHARED_PDF_RICH_FONT_PATH_TAG,
                    item = fontPath
                )
            }
        }

        val builder = AnnotatedString.Builder(newText)
        mutableSpans.compactSpans().forEach { span ->
            builder.addStyle(span.item, span.start, span.end)
        }
        mutableFontAnnotations.compactStringAnnotations().forEach { annotation ->
            builder.addStringAnnotation(annotation.tag, annotation.item, annotation.start, annotation.end)
        }

        val finalValue = newValue.copy(annotatedString = builder.toAnnotatedString())
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
            globalTextFieldValue = finalValue
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = 0)
            SharedPdfRichTextLog.d("controller.textChanged updated global directly")
        }
    }

    fun updateCurrentStyle(style: SpanStyle, fontPath: String? = currentFontPath, fontName: String? = currentFontName) {
        SharedPdfRichTextLog.d(
            "controller.updateStyle activePage=$activePageIndex localSel=${localTextFieldValue.selection} " +
                "globalSel=${globalTextFieldValue.selection} fontPath=$fontPath fontName=$fontName style=${style.richStyleSummary()}"
        )
        currentStyle = style
        currentFontPath = fontPath
        currentFontName = fontName
        isCursorVisible = true

        if (activePageIndex != -1) {
            if (!localTextFieldValue.selection.collapsed) {
                localTextFieldValue = localTextFieldValue.copy(
                    annotatedString = localTextFieldValue.annotatedString.withAppliedRichStyle(
                        style = style,
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
            globalTextFieldValue = globalTextFieldValue.copy(
                annotatedString = globalTextFieldValue.annotatedString.withAppliedRichStyle(
                    style = style,
                    fontPath = fontPath,
                    selection = globalTextFieldValue.selection
                )
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

    fun handleTapOnPage(pageIndex: Int, localTapOffset: Offset) {
        tapJob?.cancel()
        tapJob = scope.launch {
            handleTapOnPageAfterSync(pageIndex, localTapOffset)
        }
    }

    private suspend fun handleTapOnPageAfterSync(pageIndex: Int, localTapOffset: Offset) {
        SharedPdfRichTextLog.d(
            "controller.tap start page=$pageIndex offset=${localTapOffset.richOffsetSummary()} activePage=$activePageIndex " +
                "layouts=${pageLayouts.richLayoutSummary()} globalLen=${globalTextFieldValue.text.length}"
        )
        if (activePageIndex != -1) {
            val previousActivePage = activePageIndex
            SharedPdfRichTextLog.d("controller.tap syncing active page=$previousActivePage before placing cursor on $pageIndex")
            performSync(previousActivePage)
        }

        val measurer = lastTextMeasurer ?: run {
            SharedPdfRichTextLog.d("controller.tap abort no TextMeasurer")
            return
        }
        val density = lastDensity ?: run {
            SharedPdfRichTextLog.d("controller.tap abort no Density")
            return
        }
        var layout = pageLayouts.find { it.pageIndex == pageIndex }
        var bridgeAttempts = 0
        while (layout == null && bridgeAttempts < 3) {
            val currentLastPage = pageLayouts.lastOrNull()?.pageIndex ?: 0
            val breaksNeeded = (pageIndex - currentLastPage).coerceAtLeast(1)
            SharedPdfRichTextLog.d(
                "controller.tap bridge attempt=$bridgeAttempts target=$pageIndex last=$currentLastPage breaks=$breaksNeeded"
            )
            val builder = AnnotatedString.Builder(globalTextFieldValue.annotatedString)
            repeat(breaksNeeded) {
                builder.append(SHARED_PDF_PAGE_BREAK_CHAR.toString())
            }
            globalTextFieldValue = TextFieldValue(builder.toAnnotatedString())
            repaginateSync(0)
            layout = pageLayouts.find { it.pageIndex == pageIndex }
            SharedPdfRichTextLog.d(
                "controller.tap bridge result layoutFound=${layout != null} layouts=${pageLayouts.richLayoutSummary()} " +
                    "globalLen=${globalTextFieldValue.text.length}"
            )
            bridgeAttempts++
        }

        val currentLayout = layout ?: run {
            SharedPdfRichTextLog.d("controller.tap abort no layout for page=$pageIndex after bridge attempts")
            return
        }
        activePageIndex = pageIndex
        val editorWidth = editorWidth()
        val visibleText = currentLayout.visibleText
        val editableText = visibleText.withoutTrailingSharedPdfPageBreak()
        val textWithZwsp = AnnotatedString(SHARED_PDF_ZWSP) + editableText
        val safeLen = editableText.length
        localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(safeLen + 1))
        SharedPdfRichTextLog.d(
            "controller.tap localPrepared page=$pageIndex global=${currentLayout.globalStartIndex}..${currentLayout.globalEndIndex} " +
                "visibleLen=${visibleText.length} editableLen=${editableText.length} safeLen=$safeLen initialSel=${localTextFieldValue.selection}"
        )

        val measureResult = measurer.measure(
            text = editableText,
            style = TextStyle(fontSize = 16.sp, color = Color.Black),
            constraints = Constraints(maxWidth = editorWidth.toInt()),
            density = density
        )
        val textHeight = if (editableText.isEmpty()) 0f else measureResult.size.height.toFloat()
        if (editableText.isNotEmpty() && localTapOffset.y <= textHeight) {
            var localIndex = measureResult.getOffsetForPosition(localTapOffset)
            localIndex = localIndex.coerceIn(0, editableText.length)
            localTextFieldValue = localTextFieldValue.copy(selection = TextRange(localIndex + 1))
            SharedPdfRichTextLog.d(
                "controller.tap placedInText page=$pageIndex textHeight=${textHeight.richLogFloat()} " +
                    "localIndex=$localIndex selection=${localTextFieldValue.selection}"
            )
        } else {
            val gap = localTapOffset.y - textHeight
            SharedPdfRichTextLog.d(
                "controller.tap belowText page=$pageIndex textHeight=${textHeight.richLogFloat()} " +
                    "gap=${gap.richLogFloat()} offsetY=${localTapOffset.y.richLogFloat()}"
            )
            injectNewlinesLocal(gap)
        }

        isCursorVisible = true
        updateLocalCursor()
        SharedPdfRichTextLog.d(
            "controller.tap done page=$pageIndex cursorVisible=$isCursorVisible cursorPage=$cursorPageIndex " +
                "cursor=${cursorRectInPage.richRectSummary()} localSel=${localTextFieldValue.selection}"
        )
        requestFocus()
    }

    fun insertPageBreakAt(insertPageIndex: Int, count: Int = 1) {
        SharedPdfRichTextLog.d("controller.insertPageBreak requested page=$insertPageIndex count=$count")
        scope.launch {
            forceSyncAndClear()
            val original = globalTextFieldValue.annotatedString
            val insertionCharIndex = if (insertPageIndex == 0) {
                0
            } else {
                pageLayouts.find { it.pageIndex == insertPageIndex - 1 }?.globalEndIndex ?: original.length
            }
            val safeIndex = insertionCharIndex.coerceIn(0, original.length)
            val builder = AnnotatedString.Builder()
            builder.append(original.subSequence(0, safeIndex))
            repeat(count) { builder.append(SHARED_PDF_PAGE_BREAK_CHAR.toString()) }
            builder.append(original.subSequence(safeIndex, original.length))
            globalTextFieldValue = TextFieldValue(builder.toAnnotatedString(), TextRange(safeIndex + count))
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = safeIndex)
            SharedPdfRichTextLog.d(
                "controller.insertPageBreak inserted index=$safeIndex newLen=${globalTextFieldValue.text.length}"
            )
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
            globalTextFieldValue = TextFieldValue(builder.toAnnotatedString(), TextRange(start))
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
            return
        }
        SharedPdfRichTextLog.d(
            "controller.saveImmediate start activePage=$activePageIndex globalLen=${globalTextFieldValue.text.length} " +
                "localLen=${localTextFieldValue.text.length}"
        )
        isSaving = true
        try {
            tapJob?.cancel()
            saveJob?.cancel()
            syncJob?.cancel()
            val pageToSync = activePageIndex
            if (pageToSync != -1) {
                performSync(pageToSync)
                delay(50)
                activePageIndex = -1
                cursorPageIndex = -1
                cursorRectInPage = null
                localTextFieldValue = TextFieldValue("")
            }
            val document = withContext(Dispatchers.Default) {
                SharedPdfRichTextMapper.fromAnnotatedString(
                    text = globalTextFieldValue.annotatedString,
                    pageHeightPx = lastPageHeight
                )
            }
            SharedPdfRichTextLog.d(
                "controller.saveImmediate writing textLen=${document.text.length} spans=${document.spans.size}"
            )
            onDocumentChange(document)
        } finally {
            delay(100)
            isSaving = false
            SharedPdfRichTextLog.d("controller.saveImmediate done")
        }
    }

    private suspend fun syncLocalToGlobal() {
        if (activePageIndex == -1) {
            SharedPdfRichTextLog.d("controller.syncLocalToGlobal abort no active page")
            return
        }
        val layout = pageLayouts.find { it.pageIndex == activePageIndex } ?: run {
            SharedPdfRichTextLog.d("controller.syncLocalToGlobal abort missing layout page=$activePageIndex")
            return
        }
        val globalStart = layout.globalStartIndex
        val globalEnd = layout.globalEndIndex
        val currentGlobal = globalTextFieldValue.annotatedString
        val localEditableText = if (localTextFieldValue.annotatedString.text.isNotEmpty()) {
            localTextFieldValue.annotatedString.subSequence(1, localTextFieldValue.annotatedString.length)
        } else {
            AnnotatedString("")
        }
        val shouldPreservePageBreak = layout.visibleText.text.lastOrNull() == SHARED_PDF_PAGE_BREAK_CHAR
        val localText = localEditableText.withRestoredTrailingSharedPdfPageBreak(shouldPreservePageBreak)
        val builder = AnnotatedString.Builder()
        builder.append(currentGlobal.subSequence(0, globalStart))
        builder.append(localText)
        if (globalEnd < currentGlobal.length) {
            builder.append(currentGlobal.subSequence(globalEnd, currentGlobal.length))
        }
        val newGlobalAnnotated = builder.toAnnotatedString()
        val localSelectionStart = (localTextFieldValue.selection.start - 1).coerceAtLeast(0)
        val newGlobalCursorPos = globalStart + localSelectionStart
        SharedPdfRichTextLog.d(
            "controller.syncLocalToGlobal page=$activePageIndex global=$globalStart..$globalEnd " +
                "localEditableLen=${localEditableText.length} restoredBreak=$shouldPreservePageBreak " +
                "localLen=${localText.length} newLen=${newGlobalAnnotated.length} cursor=$newGlobalCursorPos"
        )
        globalTextFieldValue = TextFieldValue(newGlobalAnnotated, TextRange(newGlobalCursorPos))
        debouncedSave(globalTextFieldValue)

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
                dirtyGlobalIndex = globalStart
            )
        }
        pageLayouts = newLayouts
        SharedPdfRichTextLog.d("controller.syncLocalToGlobal layouts=${newLayouts.richLayoutSummary()}")
        val newActiveLayout = newLayouts.find {
            newGlobalCursorPos >= it.globalStartIndex && newGlobalCursorPos <= it.globalEndIndex
        }
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

    private suspend fun performSync(pageIdx: Int, checkCursorMove: Boolean = false) {
        if (pageIdx == -1) {
            SharedPdfRichTextLog.d("controller.performSync abort page=-1")
            return
        }
        val layout = pageLayouts.find { it.pageIndex == pageIdx } ?: run {
            SharedPdfRichTextLog.d("controller.performSync abort missing layout page=$pageIdx layouts=${pageLayouts.richLayoutSummary()}")
            return
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
        val newGlobalAnnotated = builder.toAnnotatedString()
        val localSelectionStart = (localTextFieldValue.selection.start - 1).coerceAtLeast(0)
        val newGlobalCursorPos = (globalStart + localSelectionStart).coerceIn(0, newGlobalAnnotated.length)
        SharedPdfRichTextLog.d(
            "controller.performSync page=$pageIdx checkCursor=$checkCursorMove global=$globalStart..$globalEnd " +
                "localEditableLen=${localEditableAnnotated.length} restoredBreak=$shouldPreservePageBreak " +
                "localLen=${localAnnotated.length} newLen=${newGlobalAnnotated.length} cursor=$newGlobalCursorPos"
        )
        globalTextFieldValue = TextFieldValue(newGlobalAnnotated, TextRange(newGlobalCursorPos))
        debouncedSave(globalTextFieldValue)

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
                dirtyGlobalIndex = globalStart
            )
        }
        pageLayouts = newLayouts
        SharedPdfRichTextLog.d("controller.performSync layouts=${newLayouts.richLayoutSummary()}")

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
        localTextFieldValue = TextFieldValue(next, TextRange(newCursor))
        SharedPdfRichTextLog.d(
            "controller.injectNewlines done newLen=${next.length} newCursor=$newCursor preview=\"${next.text.richPreview()}\""
        )
        onValueChanged(localTextFieldValue)
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
                constraints = Constraints(maxWidth = editorWidth().toInt()),
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
            val layout = pageLayouts.find {
                cursorIndex >= it.globalStartIndex && cursorIndex <= it.globalEndIndex
            }
            val measurer = lastTextMeasurer
            val density = lastDensity
            if (layout != null && measurer != null && density != null) {
                val measureResult = measurer.measure(
                    text = layout.visibleText,
                    style = TextStyle(fontSize = 16.sp),
                    constraints = Constraints(maxWidth = editorWidth().toInt()),
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
            performSync(activePageIndex)
            activePageIndex = -1
            cursorPageIndex = -1
            cursorRectInPage = null
            localTextFieldValue = TextFieldValue("")
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

        globalTextFieldValue = TextFieldValue(intermediateGlobal, TextRange(newCursorPos))
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
        globalTextFieldValue = TextFieldValue(newGlobalText, TextRange(newCursorPos))
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
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1000)
            val document = withContext(Dispatchers.Default) {
                SharedPdfRichTextMapper.fromAnnotatedString(tfv.annotatedString, lastPageHeight)
            }
            onDocumentChange(document)
        }
    }

    private fun requestFocus() {
        runCatching { focusRequester.requestFocus() }
            .onSuccess { SharedPdfRichTextLog.d("controller.requestFocus success") }
            .onFailure { SharedPdfRichTextLog.d("controller.requestFocus failed error=${it.message}") }
    }

    private fun editorWidth(): Float = (lastPageWidth - (marginX() * 2f)).coerceAtLeast(10f)

    private fun marginX(): Float = lastPageWidth * 0.1f

    private fun marginY(): Float = lastPageHeight * 0.08f
}

fun SharedPdfTextStyleConfig.toSharedPdfRichSpanStyle(): SpanStyle {
    return SpanStyle(
        color = Color(colorArgb),
        background = Color(backgroundColorArgb),
        fontSize = fontSize.sp,
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
        tag = SHARED_PDF_RICH_FONT_PATH_TAG,
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
