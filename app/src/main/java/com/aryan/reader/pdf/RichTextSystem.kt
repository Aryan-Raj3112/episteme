/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
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
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.shared.pdf.SHARED_PDF_PAGE_BREAK_CHAR
import com.aryan.reader.shared.pdf.SharedPdfRichPageLayout
import com.aryan.reader.shared.pdf.SharedPdfRichDocument
import com.aryan.reader.shared.pdf.SharedPdfRichSpan
import com.aryan.reader.shared.pdf.SharedPdfRichTextPaginationEngine
import com.aryan.reader.shared.pdf.SharedPdfRichTextMapper
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.hasRenderableSharedPdfRichText
import com.aryan.reader.shared.pdf.remapSharedPdfRichTextForLayoutChange
import com.aryan.reader.shared.pdf.sharedPdfRichTextBlankInsertBreakCount
import com.aryan.reader.shared.pdf.sharedPdfRichTextInsertionIndexForPage
import com.aryan.reader.shared.pdf.sharedPdfRichTextSelectionBounds
import com.aryan.reader.shared.pdf.withRestoredTrailingSharedPdfPageBreak
import com.aryan.reader.shared.pdf.withoutTrailingSharedPdfPageBreak
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

const val PAGE_BREAK_CHAR = SHARED_PDF_PAGE_BREAK_CHAR
private const val ZWSP = "\u200B"

internal fun String.hasRenderableRichText(): Boolean =
    hasRenderableSharedPdfRichText()

internal fun androidPdfRichTextSelectionBounds(
    selectionStart: Int,
    selectionEnd: Int,
    textLength: Int
): Pair<Int, Int>? {
    return sharedPdfRichTextSelectionBounds(selectionStart, selectionEnd, textLength)
}

object PdfFontCache {
    private val cache = ConcurrentHashMap<String, FontFamily>()
    private var assetManager: android.content.res.AssetManager? = null

    fun init(assets: android.content.res.AssetManager) {
        this.assetManager = assets
    }

    fun getFontFamily(path: String?): FontFamily {
        if (path.isNullOrBlank()) {
            return FontFamily.Default
        }

        return cache.getOrPut(path) {
            try {
                if (path.startsWith("asset:")) {
                    val assetPath = path.removePrefix("asset:")
                    assetManager?.let {
                        Timber.tag("PdfFontDebug").i("Loading font from assets: $assetPath")
                        FontFamily(Font(assetPath, it))
                    } ?: FontFamily.Default
                } else {
                    val file = File(path)
                    if (file.exists()) {
                        FontFamily(Font(file))
                    } else {
                        FontFamily.Default
                    }
                }
            } catch (e: Exception) {
                Timber.tag("PdfFontDebug").e(e, "getFontFamily: Failed to load $path")
                FontFamily.Default
            }
        }
    }
    fun getPath(fontFamily: FontFamily?): String? {
        if (fontFamily == null) return null
        return cache.entries.find { it.value == fontFamily }?.key
    }
}

typealias GlobalRichSpan = SharedPdfRichSpan
typealias GlobalRichDocument = SharedPdfRichDocument
typealias PageTextLayout = SharedPdfRichPageLayout
typealias TextPaginationEngine = SharedPdfRichTextPaginationEngine

object RichTextMapper {
    fun toAnnotatedString(
        document: GlobalRichDocument,
        pageHeightPx: Float,
        rangeStart: Int = 0,
        rangeEnd: Int = document.text.length
    ): AnnotatedString {
        return SharedPdfRichTextMapper.toAnnotatedString(
            document = document,
            pageHeightPx = pageHeightPx,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            fontFamilyResolver = PdfFontCache::getFontFamily,
        )
    }

    fun fromAnnotatedString(text: AnnotatedString, pageHeightPx: Float): GlobalRichDocument {
        return SharedPdfRichTextMapper.fromAnnotatedString(
            text = text,
            pageHeightPx = pageHeightPx,
            fontPathResolver = PdfFontCache::getPath,
        )
    }
}

private fun AnnotatedString.withoutTrailingAndroidPageBreak(): AnnotatedString {
    return withoutTrailingSharedPdfPageBreak()
}

private fun AnnotatedString.withRestoredTrailingAndroidPageBreak(shouldRestore: Boolean): AnnotatedString {
    return withRestoredTrailingSharedPdfPageBreak(shouldRestore)
}

internal fun androidRichTextInsertionIndexForPage(
    insertPageIndex: Int,
    pageLayouts: List<PageTextLayout>,
    textLength: Int
): Int {
    return sharedPdfRichTextInsertionIndexForPage(
        insertPageIndex = insertPageIndex,
        pageLayouts = pageLayouts,
        textLength = textLength,
    )
}

internal fun androidRichTextBlankInsertBreakCount(text: String, insertionCharIndex: Int): Int {
    return sharedPdfRichTextBlankInsertBreakCount(text, insertionCharIndex)
}

internal fun remapAndroidRichTextForLayoutChange(
    currentLayout: List<VirtualPage>,
    updatedLayout: List<VirtualPage>,
    pageLayouts: List<PageTextLayout>
): AnnotatedString {
    return remapSharedPdfRichTextForLayoutChange(
        currentLayout = currentLayout.map(VirtualPage::toSharedRichTextPageIdentity),
        updatedLayout = updatedLayout.map(VirtualPage::toSharedRichTextPageIdentity),
        pageLayouts = pageLayouts,
    )
}

private fun VirtualPage.toSharedRichTextPageIdentity(): PdfPageIdentity = when (this) {
    is VirtualPage.PdfPage -> PdfPageIdentity.Pdf(pdfIndex)
    is VirtualPage.BlankPage -> PdfPageIdentity.Blank(id)
}

class PdfRichTextRepository(private val context: Context) {
    private val _document = MutableStateFlow<GlobalRichDocument?>(null)
    val document = _document.asStateFlow()

    private fun getFile(bookId: String): File {
        val safeId = bookId.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return File(context.filesDir, "rich_doc_${safeId}.json")
    }

    fun getFileForSync(bookId: String): File = getFile(bookId)

    suspend fun load(bookId: String) {
        withContext(Dispatchers.IO) {
            val file = getFile(bookId)
            Timber.d(
                "android.repository.load start book=$bookId exists=${file.exists()} path=${file.absolutePath}"
            )
            if (!file.exists()) {
                _document.value = GlobalRichDocument("", emptyList())
                Timber.d("android.repository.load missing -> empty book=$bookId")
                return@withContext
            }
            try {
                val jsonString = file.readText()
                val document = SharedPdfRichTextSerializer.decode(jsonString)
                _document.value = document
                Timber.d(
                    "android.repository.load decoded book=$bookId rawLen=${jsonString.length} " +
                        "textLen=${document.text.length} spans=${document.spans.size}"
                )
            } catch (e: Exception) {
                Timber.e(e, "android.repository.load failed book=$bookId")
                Timber.e(e, "Failed to load rich text doc")
                _document.value = GlobalRichDocument("", emptyList())
            }
        }
    }

    suspend fun save(bookId: String, document: GlobalRichDocument) {
        _document.value = document
        withContext(Dispatchers.IO) {
            try {
                Timber.d(
                    "android.repository.save start book=$bookId textLen=${document.text.length} spans=${document.spans.size}"
                )
                val file = getFile(bookId)
                file.writeText(SharedPdfRichTextSerializer.encode(document))
                Timber.d(
                    "android.repository.save done book=$bookId bytes=${file.length()} path=${file.absolutePath}"
                )
            } catch (e: Exception) {
                Timber.e(e, "android.repository.save failed book=$bookId")
                Timber.e(e, "Failed to save rich text doc")
            }
        }
    }
}

@Stable
class RichTextController(
    private val repository: PdfRichTextRepository,
    private val scope: CoroutineScope,
    private val bookId: String
) {
    var globalTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    var localTextFieldValue by mutableStateOf(TextFieldValue(""))
        private set

    val editingValue: TextFieldValue
        get() = if (activePageIndex != -1) localTextFieldValue else globalTextFieldValue

    var activePageIndex by mutableIntStateOf(-1)
        private set

    var pageLayouts by mutableStateOf(emptyList<PageTextLayout>())
        private set

    val hasRenderableText: Boolean
        get() = globalTextFieldValue.text.hasRenderableRichText()

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

    private var lastPageWidth: Float = 1000f
    private var lastPageHeight: Float = 1414f
    private var lastDensity: Density? = null
    private var lastTextMeasurer: TextMeasurer? = null

    private val engine = TextPaginationEngine()
    private var saveJob: Job? = null
    private var syncJob: Job? = null
    private var isSaving = false

    var focusRequester = androidx.compose.ui.focus.FocusRequester()
    private var keyboardController: SoftwareKeyboardController? = null

    init {
        scope.launch {
            repository.document.collect { doc ->
                if (doc != null && globalTextFieldValue.text.isEmpty()) {
                    val annotated = withContext(Dispatchers.Default) {
                        RichTextMapper.toAnnotatedString(doc, lastPageHeight)
                    }
                    globalTextFieldValue = TextFieldValue(annotated)
                    repaginate(dirtyStartIndex = 0, caller = "ControllerInit")
                }
            }
        }
    }

    fun setKeyboardController(controller: SoftwareKeyboardController?) {
        this.keyboardController = controller
    }

    private fun requestFocusAndShowKeyboard() {
        try {
            focusRequester.requestFocus()
            keyboardController?.show()
        } catch (e: Exception) {
            Timber.e(e, "Failed to request focus/keyboard")
        }
    }

    fun clearSelection() {
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

    fun updateLayoutConfig(width: Float, height: Float, density: Density, measurer: TextMeasurer) {
        if (lastPageWidth != width || lastPageHeight != height || lastDensity != density) {
            lastPageWidth = width
            lastPageHeight = height
            lastDensity = density
            lastTextMeasurer = measurer
            repaginate(dirtyStartIndex = 0, caller = "LayoutConfigChange")
        }
    }

    private data class MutableSpan(var start: Int, var end: Int, val item: SpanStyle)

    private fun compactSpans(spans: List<MutableSpan>): List<MutableSpan> {
        if (spans.isEmpty()) return emptyList()

        val result = mutableListOf<MutableSpan>()
        val groupedByStyle = spans.groupBy { it.item }

        for ((_, styleSpans) in groupedByStyle) {
            val sorted = styleSpans.sortedBy { it.start }

            if (sorted.isEmpty()) continue

            var current = sorted[0]

            for (i in 1 until sorted.size) {
                val next = sorted[i]

                if (next.start <= current.end) {
                    current.end = maxOf(current.end, next.end)
                } else {
                    result.add(current)
                    current = next
                }
            }
            result.add(current)
        }

        return result
    }

    fun onValueChanged(newValue: TextFieldValue) {
        if (isSaving) return

        if (activePageIndex != -1 && !newValue.text.startsWith(ZWSP)) {
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
            if (activePageIndex != -1) {
                if (localTextFieldValue.selection != newValue.selection ||
                    localTextFieldValue.composition != newValue.composition) {

                    localTextFieldValue = newValue.copy(annotatedString = localTextFieldValue.annotatedString)
                    isCursorVisible = true
                    updateLocalCursor()

                    if (kotlin.math.abs(newValue.selection.start - oldValue.selection.start) > 1) {
                        Timber.tag("CursorNavTrace").d("Local cursor moved significantly.")
                    }
                }
            } else {
                if (globalTextFieldValue.selection != newValue.selection ||
                    globalTextFieldValue.composition != newValue.composition) {
                    globalTextFieldValue = newValue.copy(annotatedString = globalTextFieldValue.annotatedString)
                    updateGlobalCursor()
                }
            }
            return
        }

        val oldAnnotated = oldValue.annotatedString
        val mutableSpans = mutableListOf<MutableSpan>()

        val diff = newText.length - oldText.length
        val cursor = newValue.selection.end

        val changeStart = if (diff > 0) cursor - diff else cursor
        val changeEndOld = if (diff > 0) changeStart else changeStart - diff

        oldAnnotated.spanStyles.forEach { span ->
            val s = span.start
            val e = span.end
            val item = span.item

            if (diff > 0) {
                if (e <= changeStart) {
                    mutableSpans.add(MutableSpan(s, e, item))
                } else if (s >= changeStart) {
                    mutableSpans.add(MutableSpan(s + diff, e + diff, item))
                } else {
                    mutableSpans.add(MutableSpan(s, e + diff, item))
                }
            } else {
                if (e <= changeStart) {
                    mutableSpans.add(MutableSpan(s, e, item))
                } else if (s >= changeEndOld) {
                    mutableSpans.add(MutableSpan(s + diff, e + diff, item))
                } else {
                    val newS = if (s < changeStart) s else changeStart
                    val newE = (e + diff).coerceAtLeast(changeStart)

                    if (newS < newE) {
                        mutableSpans.add(MutableSpan(newS, newE, item))
                    }
                }
            }
        }

        if (diff > 0) {
            val start = (cursor - diff).coerceAtLeast(0)
            mutableSpans.add(MutableSpan(start, cursor, currentStyle))
        }

        val mergedSpans = compactSpans(mutableSpans)

        val builder = AnnotatedString.Builder(newText)
        mergedSpans.forEach { span ->
            builder.addStyle(span.item, span.start, span.end)
        }

        val finalAnnotated = builder.toAnnotatedString()
        val finalValue = newValue.copy(annotatedString = finalAnnotated)

        if (activePageIndex != -1) {
            localTextFieldValue = finalValue
            isCursorVisible = true
            updateLocalCursor()

            syncJob?.cancel()
            syncJob = scope.launch {
                delay(300)
                performSync(activePageIndex, checkCursorMove = true)
            }
        } else {
            globalTextFieldValue = finalValue
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = 0, caller = "GlobalValueChanged")
        }
    }

    private suspend fun syncLocalToGlobal() {
        if (activePageIndex == -1) return
        val layout = pageLayouts.find { it.pageIndex == activePageIndex } ?: return

        val globalStart = layout.globalStartIndex
        val globalEnd = layout.globalEndIndex
        val currentGlobal = globalTextFieldValue.annotatedString

        // FIX: Strip ZWSP (index 0) from local text
        val localEditableText = if (localTextFieldValue.annotatedString.isNotEmpty()) {
            localTextFieldValue.annotatedString.subSequence(1, localTextFieldValue.annotatedString.length)
        } else AnnotatedString("")
        val shouldPreservePageBreak = layout.visibleText.text.lastOrNull() == PAGE_BREAK_CHAR
        val localText = localEditableText.withRestoredTrailingAndroidPageBreak(shouldPreservePageBreak)

        Timber.tag("RichTextFlow").d("Sync: Page $activePageIndex, GlobalRange [$globalStart..$globalEnd], LocalLen ${localText.length}")

        val builder = AnnotatedString.Builder()
        builder.append(currentGlobal.subSequence(0, globalStart))
        builder.append(localText)
        if (globalEnd < currentGlobal.length) {
            builder.append(currentGlobal.subSequence(globalEnd, currentGlobal.length))
        }

        val newGlobalAnnotated = builder.toAnnotatedString()
        // FIX: Adjust selection -1 because local selection included ZWSP
        val localSelectionStart = (localTextFieldValue.selection.start - 1).coerceAtLeast(0)
        val newGlobalCursorPos = globalStart + localSelectionStart

        val totalBreaks = newGlobalAnnotated.text.count { it == PAGE_BREAK_CHAR }
        Timber.tag("RichTextFlow").d("Sync Complete: TotalLen ${newGlobalAnnotated.length}, TotalBreaks $totalBreaks")

        val newGlobalValue = TextFieldValue(newGlobalAnnotated, TextRange(newGlobalCursorPos))
        globalTextFieldValue = newGlobalValue

        Timber.tag("RichTextFlow").d("Syncing Global: Total Len ${newGlobalAnnotated.length}")
        debouncedSave(newGlobalValue)

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val marginX = lastPageWidth * 0.1f
        val marginY = lastPageHeight * 0.08f

        val newLayouts = withContext(Dispatchers.Default) {
            engine.paginate(
                globalText = newGlobalAnnotated,
                pageWidthPx = lastPageWidth,
                pageHeightPx = lastPageHeight,
                textMeasurer = measurer,
                density = density,
                marginX = marginX,
                marginY = marginY,
                previousLayouts = pageLayouts,
                dirtyGlobalIndex = globalStart
            )
        }

        pageLayouts = newLayouts

        val newActiveLayout = newLayouts.find {
            newGlobalCursorPos >= it.globalStartIndex && newGlobalCursorPos <= it.globalEndIndex
        }

        if (newActiveLayout != null) {
            activePageIndex = newActiveLayout.pageIndex
            val reExtractedText = newGlobalAnnotated.subSequence(
                newActiveLayout.globalStartIndex, newActiveLayout.globalEndIndex
            ).withoutTrailingAndroidPageBreak()
            val textWithZwsp = AnnotatedString(ZWSP) + reExtractedText
            val newLocalCursor = (newGlobalCursorPos - newActiveLayout.globalStartIndex + 1)
                .coerceIn(0, textWithZwsp.length)

            localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(newLocalCursor))
            updateLocalCursor()
        }
    }

    fun updateCurrentStyle(style: SpanStyle, fontPath: String? = currentFontPath, fontName: String? = currentFontName) {
        Timber.tag("PdfFontDebug").d("Controller: updateCurrentStyle called. Name: $fontName, Path: $fontPath")

        val effectiveFontFamily = PdfFontCache.getFontFamily(fontPath)
        val styleWithFont = style.copy(fontFamily = effectiveFontFamily)

        currentStyle = styleWithFont
        currentFontPath = fontPath
        currentFontName = fontName
        isCursorVisible = true

        if (activePageIndex != -1) {
            if (!localTextFieldValue.selection.collapsed) {
                val builder = AnnotatedString.Builder(localTextFieldValue.annotatedString)
                builder.addStyle(styleWithFont, localTextFieldValue.selection.start, localTextFieldValue.selection.end)
                localTextFieldValue = localTextFieldValue.copy(annotatedString = builder.toAnnotatedString())

                Timber.tag("TextAnnotStyle").d("Controller: Applied style to Local Selection (${localTextFieldValue.selection})")

                syncJob?.cancel()
                syncJob = scope.launch {
                    delay(500)
                    syncLocalToGlobal()
                }
            }
        } else {
            if (!globalTextFieldValue.selection.collapsed) {
                val builder = AnnotatedString.Builder(globalTextFieldValue.annotatedString)
                builder.addStyle(styleWithFont, globalTextFieldValue.selection.start, globalTextFieldValue.selection.end)
                globalTextFieldValue = globalTextFieldValue.copy(annotatedString = builder.toAnnotatedString())

                debouncedSave(globalTextFieldValue)
                repaginate(dirtyStartIndex = globalTextFieldValue.selection.min, caller = "GlobalValueChanged")
            }
        }
        requestFocusAndShowKeyboard()
    }

    fun handleTapOnPage(pageIndex: Int, localTapOffset: Offset) {
        if (globalTextFieldValue.text.length > 500_000 && activePageIndex == -1) {
            Timber.tag("RichTextFlow").w("Document too large for instant interactive tap.")
        }

        Timber.tag("RichTextFlow").i("Tap on Page $pageIndex at $localTapOffset. Current Active: $activePageIndex")

        if (activePageIndex != -1 && activePageIndex != pageIndex) {
            scope.launch { performSync(activePageIndex) }
        }

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return

        var layout = pageLayouts.find { it.pageIndex == pageIndex }

        if (layout == null) {
            val lastLayout = pageLayouts.lastOrNull()
            val currentLastPage = lastLayout?.pageIndex ?: 0
            Timber.tag("RichTextFlow").w("Tapped page $pageIndex beyond current text stream (Last page: $currentLastPage). Bridging gap.")

            if (pageIndex > currentLastPage) {
                val breaksNeeded = pageIndex - currentLastPage
                val builder = AnnotatedString.Builder(globalTextFieldValue.annotatedString)
                repeat(breaksNeeded) {
                    builder.append(PAGE_BREAK_CHAR.toString())
                }
                globalTextFieldValue = TextFieldValue(builder.toAnnotatedString())

                repaginateSync(0)
                layout = pageLayouts.find { it.pageIndex == pageIndex }
            }
        }

        val currentLayout = layout ?: return

        activePageIndex = pageIndex
        val margin = lastPageWidth * 0.1f
        val editorWidth = (lastPageWidth - (margin * 2)).coerceAtLeast(10f)

        val vText = currentLayout.visibleText
        val editableText = vText.withoutTrailingAndroidPageBreak()
        // FIX: Prepend ZWSP to the visible text
        val textWithZwsp = AnnotatedString(ZWSP) + editableText
        val safeLen = editableText.length

        // FIX: Adjust initial selection by +1 because of ZWSP
        localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(safeLen + 1))

        val measureResult = measurer.measure(
            text = editableText, // We measure editable text, not the hidden page-break sentinel
            style = TextStyle(fontSize = 16.sp, color = Color.Black),
            constraints = Constraints(maxWidth = editorWidth.toInt()),
            density = density
        )

        val textHeight = if (editableText.isEmpty()) 0f else measureResult.size.height.toFloat()

        if (editableText.isNotEmpty() && localTapOffset.y <= textHeight) {
            var localIndex = measureResult.getOffsetForPosition(localTapOffset)
            localIndex = localIndex.coerceIn(0, editableText.length)
            localTextFieldValue = localTextFieldValue.copy(selection = TextRange(localIndex + 1))
        } else {
            val gap = localTapOffset.y - textHeight
            injectNewlinesLocal(gap)
        }

        isCursorVisible = true
        updateLocalCursor()
        requestFocusAndShowKeyboard()
    }

    private fun injectNewlinesLocal(gapPixels: Float) {
        val fontSizeSp = currentStyle.fontSize.value
        val densityVal = lastDensity?.density ?: 1f
        val lineHeightPx = (if (fontSizeSp.isNaN()) 16f else fontSizeSp) * densityVal * 1.3f
        val linesNeeded = (gapPixels / lineHeightPx).toInt().coerceAtLeast(1)
        val sb = StringBuilder()
        repeat(linesNeeded) { sb.append('\n') }

        val original = localTextFieldValue.annotatedString
        val text = original.text

        val builder = AnnotatedString.Builder()

        val endsWithBreak = text.isNotEmpty() && text.last() == PAGE_BREAK_CHAR

        if (endsWithBreak) {
            builder.append(original.subSequence(0, text.length - 1))
            builder.pushStyle(currentStyle)
            builder.append(sb.toString())
            builder.pop()
            builder.append(PAGE_BREAK_CHAR.toString())
        } else {
            builder.append(original)
            builder.pushStyle(currentStyle)
            builder.append(sb.toString())
            builder.pop()
        }

        val newVal = builder.toAnnotatedString()
        val newCursor = if (endsWithBreak) newVal.length - 1 else newVal.length

        localTextFieldValue = TextFieldValue(newVal, TextRange(newCursor))
        onValueChanged(localTextFieldValue)
    }

    private fun repaginateSync(@Suppress("SameParameterValue") dirtyStartIndex: Int) {
        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val marginX = lastPageWidth * 0.1f
        val marginY = lastPageHeight * 0.08f

        pageLayouts = engine.paginate(
            globalText = globalTextFieldValue.annotatedString,
            pageWidthPx = lastPageWidth,
            pageHeightPx = lastPageHeight,
            textMeasurer = measurer,
            density = density,
            marginX = marginX,
            marginY = marginY,
            previousLayouts = pageLayouts,
            dirtyGlobalIndex = dirtyStartIndex
        )
    }

    private fun repaginate(dirtyStartIndex: Int, caller: String) {
        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val marginX = lastPageWidth * 0.1f
        val marginY = lastPageHeight * 0.08f

        val currentText = globalTextFieldValue.annotatedString
        val currentLayouts = pageLayouts

        scope.launch {
            Timber.tag("RichTextMigration").v("repaginate triggered by [$caller] from dirty index $dirtyStartIndex. Global text len: ${currentText.length}")
            val newLayouts = withContext(Dispatchers.Default) {
                engine.paginate(
                    globalText = currentText,
                    pageWidthPx = lastPageWidth,
                    pageHeightPx = lastPageHeight,
                    textMeasurer = measurer,
                    density = density,
                    marginX = marginX,
                    marginY = marginY,
                    previousLayouts = currentLayouts,
                    dirtyGlobalIndex = dirtyStartIndex
                )
            }
            pageLayouts = newLayouts
            Timber.tag("RichTextMigration").d("repaginate [$caller] completed. Pages: ${newLayouts.size}")
        }
    }

    private fun updateLocalCursor() {
        if (lastTextMeasurer == null || lastDensity == null) return

        val selection = localTextFieldValue.selection
        if (selection.collapsed) {
            val margin = lastPageWidth * 0.1f
            val editorWidth = (lastPageWidth - (margin * 2)).coerceAtLeast(10f)

            val measureResult = lastTextMeasurer!!.measure(
                text = localTextFieldValue.annotatedString,
                style = TextStyle(fontSize = 16.sp),
                constraints = Constraints(maxWidth = editorWidth.toInt()),
                density = lastDensity!!
            )

            val safeOffset = selection.start.coerceIn(0, localTextFieldValue.text.length)
            val rect = measureResult.getCursorRect(safeOffset)
            val marginY = lastPageHeight * 0.08f

            cursorPageIndex = activePageIndex
            cursorRectInPage = rect.translate(margin, marginY)
        }
    }

    private fun updateGlobalCursor() {
        val selection = globalTextFieldValue.selection
        if (isCursorVisible && showCursorOverride && selection.collapsed) {
            val cursorIndex = selection.start
            val layout = pageLayouts.find {
                cursorIndex >= it.globalStartIndex && cursorIndex <= it.globalEndIndex
            }
            if (layout != null && lastTextMeasurer != null && lastDensity != null) {
                cursorPageIndex = layout.pageIndex
                val margin = lastPageWidth * 0.1f
                val editorWidth = (lastPageWidth - (margin * 2)).coerceAtLeast(10f)
                val measureResult = lastTextMeasurer!!.measure(
                    text = layout.visibleText,
                    style = TextStyle(fontSize = 16.sp),
                    constraints = Constraints(maxWidth = editorWidth.toInt()),
                    density = lastDensity!!
                )
                val localIndex = (cursorIndex - layout.globalStartIndex).coerceIn(0, layout.visibleText.length)
                val rect = measureResult.getCursorRect(localIndex)
                val marginY = lastPageHeight * 0.08f
                cursorRectInPage = rect.translate(margin, marginY)
            }
        } else {
            cursorPageIndex = -1
            cursorRectInPage = null
        }
    }

    // --- Misc Operations ---

    private suspend fun forceSyncAndClear() {
        if (activePageIndex != -1) {
            Timber.tag("RichTextFlow").i("ForceSync: Committing local edits before structural change.")
            performSync(activePageIndex)
            activePageIndex = -1
            cursorPageIndex = -1
            cursorRectInPage = null
            localTextFieldValue = TextFieldValue("")
        }
    }

    fun insertPageBreakAt(insertPageIndex: Int, count: Int = 1) {
        scope.launch {
            forceSyncAndClear()

            val original = globalTextFieldValue.annotatedString
            Timber.tag("RichTextMigration").d("insertPageBreakAt: Target Page Index: $insertPageIndex, Count: $count")

            val safeIndex = androidRichTextInsertionIndexForPage(
                insertPageIndex = insertPageIndex,
                pageLayouts = pageLayouts,
                textLength = original.length
            )
            Timber.tag("RichTextMigration").v("insertPageBreakAt: insertion index $safeIndex")

            insertPageBreaksIntoGlobalText(
                original = original,
                safeIndex = safeIndex,
                count = count,
                caller = "InsertPageBreakAt"
            )
        }
    }

    fun insertBlankPageAt(insertPageIndex: Int) {
        scope.launch {
            forceSyncAndClear()

            val original = globalTextFieldValue.annotatedString
            val safeIndex = androidRichTextInsertionIndexForPage(
                insertPageIndex = insertPageIndex,
                pageLayouts = pageLayouts,
                textLength = original.length
            )
            val requiredBreaks = androidRichTextBlankInsertBreakCount(
                text = original.text,
                insertionCharIndex = safeIndex
            )

            Timber.tag("RichTextMigration").i(
                "insertBlankPageAt: page=$insertPageIndex index=$safeIndex breaks=$requiredBreaks"
            )

            insertPageBreaksIntoGlobalText(
                original = original,
                safeIndex = safeIndex,
                count = requiredBreaks,
                caller = "InsertBlankPageAt"
            )
        }
    }

    suspend fun remapPagesForLayoutChange(
        currentLayout: List<VirtualPage>,
        updatedLayout: List<VirtualPage>
    ) = withContext(NonCancellable) {
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "rich.remap.start current=${currentLayout.pdfLayoutDebugSummary()} " +
                "updated=${updatedLayout.pdfLayoutDebugSummary()} pageLayouts=${pageLayouts.size} " +
                "textLen=${globalTextFieldValue.annotatedString.length}"
        )
        forceSyncAndClear()

        val original = globalTextFieldValue.annotatedString
        if (pageLayouts.isEmpty() && original.text.isNotEmpty()) {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).w(
                "rich.remap.skipNoLayouts current=${currentLayout.pdfLayoutDebugSummary()} " +
                    "updated=${updatedLayout.pdfLayoutDebugSummary()} textLen=${original.length}"
            )
            Timber.tag("RichTextMigration").w(
                "remapPagesForLayoutChange skipped: no rich text page layouts for non-empty text"
            )
            return@withContext
        }
        val remapped = remapAndroidRichTextForLayoutChange(
            currentLayout = currentLayout,
            updatedLayout = updatedLayout,
            pageLayouts = pageLayouts
        )

        if (remapped == original) {
            Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
                "rich.remap.noChange current=${currentLayout.pdfLayoutDebugSummary()} " +
                    "updated=${updatedLayout.pdfLayoutDebugSummary()} textLen=${original.length}"
            )
            return@withContext
        }

        Timber.tag("RichTextMigration").i(
            "remapPagesForLayoutChange: textLen ${original.length} -> ${remapped.length}, " +
                "pages ${currentLayout.size} -> ${updatedLayout.size}"
        )
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "rich.remap.apply textLen=${original.length}->${remapped.length} " +
                "current=${currentLayout.pdfLayoutDebugSummary()} updated=${updatedLayout.pdfLayoutDebugSummary()}"
        )

        globalTextFieldValue = TextFieldValue(
            remapped,
            selection = TextRange(remapped.length)
        )
        repaginateSync(0)
        saveCurrentGlobalTextImmediately()
        Timber.tag(PDF_BLANK_PAGE_PERSISTENCE_TAG).i(
            "rich.remap.done textLen=${globalTextFieldValue.annotatedString.length} pageLayouts=${pageLayouts.size}"
        )
    }

    private fun insertPageBreaksIntoGlobalText(
        original: AnnotatedString,
        safeIndex: Int,
        count: Int,
        caller: String
    ) {
        val safeCount = count.coerceAtLeast(0)
        if (safeCount == 0) return

        Timber.tag("RichTextMigration").i("$caller: Inserting $safeCount PAGE_BREAK_CHARs at global index $safeIndex")

        val builder = AnnotatedString.Builder()
        builder.append(original.subSequence(0, safeIndex))

        repeat(safeCount) {
            builder.append(PAGE_BREAK_CHAR.toString())
        }

        builder.append(original.subSequence(safeIndex, original.length))

        val newCursorPos = safeIndex + safeCount

        globalTextFieldValue = TextFieldValue(builder.toAnnotatedString(), TextRange(newCursorPos))
        debouncedSave(globalTextFieldValue)
        repaginate(dirtyStartIndex = safeIndex, caller = caller)
    }

    private suspend fun saveCurrentGlobalTextImmediately() {
        saveJob?.cancel()
        val finalAnnotated = globalTextFieldValue.annotatedString
        withContext(Dispatchers.Default) {
            val doc = RichTextMapper.fromAnnotatedString(finalAnnotated, lastPageHeight)
            repository.save(bookId, doc)
        }
    }

    fun deleteTextOnPage(pageIndex: Int) {
        scope.launch {
            forceSyncAndClear()

            val layout = pageLayouts.find { it.pageIndex == pageIndex } ?: return@launch
            val start = layout.globalStartIndex
            val end = layout.globalEndIndex

            if (start >= end && start >= globalTextFieldValue.text.length) return@launch

            Timber.tag("RichTextFlow").i("Deleting text on Page $pageIndex (Global: $start..$end)")

            val original = globalTextFieldValue.annotatedString
            val builder = AnnotatedString.Builder()
            builder.append(original.subSequence(0, start))
            if (end < original.length) {
                builder.append(original.subSequence(end, original.length))
            }

            globalTextFieldValue = TextFieldValue(builder.toAnnotatedString(), TextRange(start))
            debouncedSave(globalTextFieldValue)
            repaginate(dirtyStartIndex = start, caller = "DeleteTextOnPage")
        }
    }

    private fun debouncedSave(tfv: TextFieldValue) {
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.Default) {
            delay(1000)
            val baseDoc = RichTextMapper.fromAnnotatedString(tfv.annotatedString, lastPageHeight)
            repository.save(bookId, baseDoc)
        }
    }

    private suspend fun performSync(pageIdx: Int, checkCursorMove: Boolean = false) {
        if (pageIdx == -1) {
            Timber.tag("RichTextMigration").w("performSync aborted: pageIdx is -1")
            return
        }
        val layout = pageLayouts.find { it.pageIndex == pageIdx } ?: run {
            Timber.tag("RichTextMigration").e("performSync failed: No layout found for pageIdx $pageIdx. Current layout count: ${pageLayouts.size}")
            return
        }

        val globalStart = layout.globalStartIndex
        val globalEnd = layout.globalEndIndex

        Timber.tag("RichTextMigration").i("performSync EXECUTE: Page=$pageIdx, GlobalRange=[$globalStart..$globalEnd], LocalTextLen=${localTextFieldValue.text.length}")

        val currentGlobal = globalTextFieldValue.annotatedString

        val localAnnotatedRaw = localTextFieldValue.annotatedString
        val localEditableAnnotated = if (localAnnotatedRaw.isNotEmpty()) localAnnotatedRaw.subSequence(1, localAnnotatedRaw.length) else AnnotatedString("")
        val shouldPreservePageBreak = layout.visibleText.text.lastOrNull() == PAGE_BREAK_CHAR
        val localAnnotated = localEditableAnnotated.withRestoredTrailingAndroidPageBreak(shouldPreservePageBreak)

        val charBeforeSync = if (globalStart > 0) currentGlobal.text[globalStart - 1] else "START"
        val charAfterSync = if (globalEnd < currentGlobal.length) currentGlobal.text[globalEnd] else "END"

        Timber.tag("RichTextFlow").v("Sync START Page $pageIdx. Boundary: [$charBeforeSync]...[G:$globalStart..$globalEnd]...[$charAfterSync]")

        val builder = AnnotatedString.Builder()
        builder.append(currentGlobal.subSequence(0, globalStart))
        builder.append(localAnnotated)
        if (globalEnd < currentGlobal.length) {
            builder.append(currentGlobal.subSequence(globalEnd, currentGlobal.length))
        }

        val newGlobalAnnotated = builder.toAnnotatedString()
        Timber.tag("RichTextMigration").d("performSync: Merged text. Global length: ${currentGlobal.length} -> ${newGlobalAnnotated.length}")
        val localSelectionStart = (localTextFieldValue.selection.start - 1).coerceAtLeast(0)
        val newGlobalCursorPos = (globalStart + localSelectionStart).coerceIn(0, newGlobalAnnotated.length)

        if (checkCursorMove) {
            Timber.tag("CursorNavTrace").d("performSync: Checking move. GlobalStart: $globalStart, LocalSelStart: $localSelectionStart, TargetGlobalPos: $newGlobalCursorPos")
        }

        globalTextFieldValue = TextFieldValue(newGlobalAnnotated, TextRange(newGlobalCursorPos))
        debouncedSave(globalTextFieldValue)

        val measurer = lastTextMeasurer ?: return
        val density = lastDensity ?: return
        val marginX = lastPageWidth * 0.1f
        val marginY = lastPageHeight * 0.08f

        // Repaginate from the start of the current page
        val newLayouts = withContext(Dispatchers.Default) {
            engine.paginate(
                globalText = newGlobalAnnotated,
                pageWidthPx = lastPageWidth,
                pageHeightPx = lastPageHeight,
                textMeasurer = measurer,
                density = density,
                marginX = marginX,
                marginY = marginY,
                previousLayouts = pageLayouts,
                dirtyGlobalIndex = globalStart
            )
        }

        val oldPage1Start = pageLayouts.find { it.pageIndex == pageIdx + 1 }?.globalStartIndex
        val newPage1Start = newLayouts.find { it.pageIndex == pageIdx + 1 }?.globalStartIndex
        Timber.tag("RichTextFlow").d("Sync Repaginated. Page ${pageIdx+1} start shifted: $oldPage1Start -> $newPage1Start")

        pageLayouts = newLayouts

        if (checkCursorMove) {
            val newActiveLayout = newLayouts.find {
                newGlobalCursorPos >= it.globalStartIndex && newGlobalCursorPos < it.globalEndIndex
            } ?: newLayouts.find { newGlobalCursorPos == it.globalEndIndex }

            if (newActiveLayout != null) {
                if (newActiveLayout.pageIndex != activePageIndex) {
                    Timber.tag("CursorNavTrace").i("performSync: MIGRATING from Page $activePageIndex to ${newActiveLayout.pageIndex}")
                    activePageIndex = newActiveLayout.pageIndex
                }

                val reExtracted = newGlobalAnnotated.subSequence(
                    newActiveLayout.globalStartIndex, newActiveLayout.globalEndIndex
                ).withoutTrailingAndroidPageBreak()
                val textWithZwsp = AnnotatedString(ZWSP) + reExtracted
                val newLocalCursor = (newGlobalCursorPos - newActiveLayout.globalStartIndex + 1).coerceIn(0, textWithZwsp.length)

                Timber.tag("CursorNavTrace").d("performSync: Final local cursor set to $newLocalCursor on Page ${newActiveLayout.pageIndex}")
                localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(newLocalCursor))
                updateLocalCursor()
                if (newActiveLayout.pageIndex != pageIdx) {
                    requestFocusAndShowKeyboard()
                }
            } else {
                Timber.tag("CursorNavTrace").w("performSync: No layout found for GlobalPos $newGlobalCursorPos")
            }
        }
    }

    fun handleBackspaceAtStart(): Boolean {
        if (localTextFieldValue.selection.start != 0 && localTextFieldValue.selection.start != 1) return false
        val originalActivePage = activePageIndex
        if (originalActivePage <= 0) return false

        Timber.tag("CursorNavTrace").i("handleBackspaceAtStart: Triggered at top of Page $originalActivePage")

        scope.launch {
            syncJob?.cancel()

            performSync(originalActivePage)

            val currentLayout = pageLayouts.find { it.pageIndex == originalActivePage } ?: return@launch
            val globalText = globalTextFieldValue.annotatedString
            val currentGlobalStart = currentLayout.globalStartIndex

            if (currentGlobalStart > 0) {
                val charBefore = globalText.text[currentGlobalStart - 1]

                if (charBefore == PAGE_BREAK_CHAR) {
                    Timber.tag("CursorNavTrace").i("handleBackspaceAtStart: Found PAGE_BREAK at index ${currentGlobalStart - 1}. Removing it.")

                    val targetPageIdx = originalActivePage - 1
                    val builder = AnnotatedString.Builder()
                    builder.append(globalText.subSequence(0, currentGlobalStart - 1))
                    val contentAfterBreak = globalText.subSequence(currentGlobalStart, globalText.length)
                    builder.append(contentAfterBreak)

                    var intermediateGlobal = builder.toAnnotatedString()
                    var newCursorPos = (currentGlobalStart - 1).coerceAtLeast(0)

                    val measurer = lastTextMeasurer ?: return@launch
                    val density = lastDensity ?: return@launch
                    val marginX = lastPageWidth * 0.1f
                    val marginY = lastPageHeight * 0.08f
                    val editorHeight = (lastPageHeight - (marginY * 2)).coerceAtLeast(10f)
                    val editorWidth = (lastPageWidth - (marginX * 2)).coerceAtLeast(10f)

                    val targetLayout = pageLayouts.find { it.pageIndex == targetPageIdx }
                    val targetStart = targetLayout?.globalStartIndex ?: 0
                    val safeTargetStart = targetStart.coerceIn(0, newCursorPos)
                    val pageTextToMeasure = intermediateGlobal.subSequence(safeTargetStart, newCursorPos)

                    val measureResult = measurer.measure(
                        text = pageTextToMeasure,
                        style = TextStyle(fontSize = 16.sp),
                        constraints = Constraints(maxWidth = editorWidth.toInt()),
                        density = density
                    )

                    val currentTextHeight = measureResult.size.height.toFloat()
                    val gap = editorHeight - currentTextHeight

                    if (gap > 0) {
                        val fontSizeSp = currentStyle.fontSize.value
                        val densityVal = density.density
                        val lineHeightPx = (if (fontSizeSp.isNaN() || fontSizeSp <= 0) 16f else fontSizeSp) * densityVal * 1.3f
                        val linesNeeded = (gap / lineHeightPx).toInt().coerceAtLeast(0)

                        if (linesNeeded > 0) {
                            val padding = "\n".repeat(linesNeeded)
                            val paddedBuilder = AnnotatedString.Builder()
                            paddedBuilder.append(intermediateGlobal.subSequence(0, newCursorPos))
                            paddedBuilder.pushStyle(currentStyle)
                            paddedBuilder.append(padding)
                            paddedBuilder.pop()
                            paddedBuilder.append(intermediateGlobal.subSequence(newCursorPos, intermediateGlobal.length))

                            intermediateGlobal = paddedBuilder.toAnnotatedString()
                            newCursorPos += padding.length
                        }
                    }

                    globalTextFieldValue = TextFieldValue(intermediateGlobal, TextRange(newCursorPos))
                    debouncedSave(globalTextFieldValue)

                    val finalLayouts = withContext(Dispatchers.Default) {
                        engine.paginate(intermediateGlobal, lastPageWidth, lastPageHeight, measurer, density, marginX, marginY)
                    }
                    pageLayouts = finalLayouts

                    val finalActiveLayout = finalLayouts.find { it.pageIndex == targetPageIdx }
                        ?: finalLayouts.findLast { newCursorPos >= it.globalStartIndex && newCursorPos <= it.globalEndIndex }

                    if (finalActiveLayout != null) {
                        activePageIndex = finalActiveLayout.pageIndex
                        val reExtracted = intermediateGlobal.subSequence(
                            finalActiveLayout.globalStartIndex, finalActiveLayout.globalEndIndex
                        ).withoutTrailingAndroidPageBreak()
                        val textWithZwsp = AnnotatedString(ZWSP) + reExtracted
                        val localCursor = (newCursorPos - finalActiveLayout.globalStartIndex + 1).coerceIn(0, textWithZwsp.length)

                        localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(localCursor))
                        updateLocalCursor()
                        requestFocusAndShowKeyboard()
                    }

                } else {
                    Timber.tag("CursorNavTrace").i("handleBackspaceAtStart: Contiguous text (Overflow). Deleting char at ${currentGlobalStart - 1}")

                    val builder = AnnotatedString.Builder()
                    builder.append(globalText.subSequence(0, currentGlobalStart - 1))
                    builder.append(globalText.subSequence(currentGlobalStart, globalText.length))

                    val newGlobalText = builder.toAnnotatedString()
                    val newCursorPos = (currentGlobalStart - 1).coerceAtLeast(0)

                    globalTextFieldValue = TextFieldValue(newGlobalText, TextRange(newCursorPos))
                    debouncedSave(globalTextFieldValue)

                    val measurer = lastTextMeasurer ?: return@launch
                    val density = lastDensity ?: return@launch
                    val marginX = lastPageWidth * 0.1f
                    val marginY = lastPageHeight * 0.08f

                    val finalLayouts = withContext(Dispatchers.Default) {
                        engine.paginate(
                            newGlobalText,
                            lastPageWidth,
                            lastPageHeight,
                            measurer,
                            density,
                            marginX,
                            marginY
                        )
                    }
                    pageLayouts = finalLayouts

                    val finalActiveLayout = finalLayouts.find {
                        newCursorPos >= it.globalStartIndex && newCursorPos < it.globalEndIndex
                    } ?: finalLayouts.find { newCursorPos == it.globalEndIndex }

                    if (finalActiveLayout != null) {
                        @Suppress("UnusedVariable", "Unused") val previousPage = activePageIndex
                        activePageIndex = finalActiveLayout.pageIndex

                        val reExtracted = newGlobalText.subSequence(
                            finalActiveLayout.globalStartIndex, finalActiveLayout.globalEndIndex
                        ).withoutTrailingAndroidPageBreak()
                        val textWithZwsp = AnnotatedString(ZWSP) + reExtracted
                        val localCursor = (newCursorPos - finalActiveLayout.globalStartIndex + 1).coerceIn(0, textWithZwsp.length)

                        localTextFieldValue = TextFieldValue(textWithZwsp, TextRange(localCursor))
                        updateLocalCursor()
                        requestFocusAndShowKeyboard()

                        Timber.tag("CursorNavTrace").i("handleBackspaceAtStart: Overflow backspace moved cursor to Page ${finalActiveLayout.pageIndex}, Index $localCursor")
                    } else {
                        Timber.tag("CursorNavTrace").e("handleBackspaceAtStart: Could not find layout for Overflow Backspace pos $newCursorPos")
                    }
                }
            } else {
                Timber.tag("CursorNavTrace").w("handleBackspaceAtStart: Start index is 0, cannot backspace.")
            }
        }
        return true
    }

    suspend fun saveImmediate() {
        if (isSaving) return
        isSaving = true
        try {
            Timber.tag("RichTextMigration").i("saveImmediate: Starting. Current activePageIndex: $activePageIndex")
            saveJob?.cancel()
            syncJob?.cancel()

            val pageToSync = activePageIndex
            if (pageToSync != -1) {
                performSync(pageToSync)

                delay(50)
                activePageIndex = -1
                cursorPageIndex = -1
                cursorRectInPage = null
                Timber.tag("RichTextMigration").d("saveImmediate: Sync finished and state cleared for page $pageToSync")
            }

            val finalAnnotated = globalTextFieldValue.annotatedString
            withContext(Dispatchers.Default) {
                val doc = RichTextMapper.fromAnnotatedString(finalAnnotated, lastPageHeight)
                repository.save(bookId, doc)
            }
        } finally {
            delay(100)
            isSaving = false
        }
    }
}
