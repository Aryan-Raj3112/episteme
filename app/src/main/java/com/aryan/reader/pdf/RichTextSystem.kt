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
import com.aryan.reader.data.AndroidBookArtifactPaths
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Density
import com.aryan.reader.pdf.data.VirtualPage
import com.aryan.reader.shared.pdf.SHARED_PDF_PAGE_BREAK_CHAR
import com.aryan.reader.shared.pdf.SharedPdfRichPageLayout
import com.aryan.reader.shared.pdf.SharedPdfRichDocument
import com.aryan.reader.shared.pdf.SharedPdfRichSpan
import com.aryan.reader.shared.pdf.SharedPdfRichTextPaginationEngine
import com.aryan.reader.shared.pdf.SharedPdfRichTextMapper
import com.aryan.reader.shared.pdf.SharedPdfRichTextController
import com.aryan.reader.shared.pdf.SharedPdfRichTextSerializer
import com.aryan.reader.shared.pdf.hasRenderableSharedPdfRichText
import com.aryan.reader.shared.pdf.remapSharedPdfRichTextForLayoutChange
import com.aryan.reader.shared.pdf.sharedPdfRichTextBlankInsertBreakCount
import com.aryan.reader.shared.pdf.sharedPdfRichTextInsertionIndexForPage
import com.aryan.reader.shared.pdf.sharedPdfRichTextSelectionBounds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import com.aryan.reader.data.writeJsonAtomically
import java.io.File

const val PAGE_BREAK_CHAR = SHARED_PDF_PAGE_BREAK_CHAR

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
        return AndroidBookArtifactPaths.richTextFile(context.filesDir, bookId)
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
                file.writeJsonAtomically(SharedPdfRichTextSerializer.encode(document))
                Timber.d(
                    "android.repository.save done book=$bookId bytes=${file.length()} path=${file.absolutePath}"
                )
            } catch (e: Exception) {
                Timber.e(e, "android.repository.save failed book=$bookId")
                Timber.e(e, "Failed to save rich text doc")
                throw e
            }
        }
    }
}

@Stable
class RichTextController(
    private val repository: PdfRichTextRepository,
    private val scope: CoroutineScope,
    private val bookId: String,
    private val onCommitted: suspend (bookId: String, reason: String, immediate: Boolean) -> Unit = { _, _, _ -> },
) {
    private var keyboardController: SoftwareKeyboardController? = null

    internal val sharedDelegate = SharedPdfRichTextController(
        scope = scope,
        onDocumentChange = { document ->
            repository.save(bookId, document)
            onCommitted(bookId, "rich_text", false)
        },
        documentToAnnotatedString = { document, pageHeight ->
            RichTextMapper.toAnnotatedString(document, pageHeight)
        },
        annotatedStringToDocument = { text, pageHeight ->
            RichTextMapper.fromAnnotatedString(text, pageHeight)
        },
        styleForFontPath = { style, fontPath ->
            style.copy(fontFamily = PdfFontCache.getFontFamily(fontPath))
        },
        onEditingFocusRequested = { keyboardController?.show() },
    )

    init {
        scope.launch {
            repository.document.collect { document ->
                document?.let(sharedDelegate::loadDocumentIfEmpty)
            }
        }
    }

    private val delegate get() = sharedDelegate

    val globalTextFieldValue: TextFieldValue get() = delegate.globalTextFieldValue
    val localTextFieldValue: TextFieldValue get() = delegate.localTextFieldValue
    val editingValue: TextFieldValue get() = delegate.editingValue
    val activePageIndex: Int get() = delegate.activePageIndex
    val pageLayouts: List<PageTextLayout> get() = delegate.pageLayouts
    val hasRenderableText: Boolean get() = delegate.hasRenderableText
    val currentStyle: SpanStyle get() = delegate.currentStyle
    val currentFontPath: String? get() = delegate.currentFontPath
    val currentFontName: String? get() = delegate.currentFontName
    val cursorPageIndex: Int get() = delegate.cursorPageIndex
    val cursorRectInPage: Rect? get() = delegate.cursorRectInPage
    val isCursorVisible: Boolean get() = delegate.isCursorVisible
    val focusRequester get() = delegate.focusRequester

    var showCursorOverride: Boolean
        get() = delegate.showCursorOverride
        set(value) {
            delegate.showCursorOverride = value
        }

    fun setKeyboardController(controller: SoftwareKeyboardController?) {
        keyboardController = controller
    }

    fun clearSelection() = delegate.clearSelection()

    fun updateLayoutConfig(width: Float, height: Float, density: Density, measurer: TextMeasurer) =
        delegate.updateLayoutConfig(width, height, density, measurer)

    fun onValueChanged(newValue: TextFieldValue) = delegate.onValueChanged(newValue)

    fun updateCurrentStyle(
        style: SpanStyle,
        fontPath: String? = currentFontPath,
        fontName: String? = currentFontName,
    ) = delegate.updateCurrentStyle(style, fontPath, fontName)

    fun requestEditingFocus() = delegate.requestEditingFocus()

    fun handleTapOnPage(pageIndex: Int, localTapOffset: Offset) =
        delegate.handleTapOnPage(pageIndex, localTapOffset)

    fun insertPageBreakAt(insertPageIndex: Int, count: Int = 1) =
        delegate.insertPageBreakAt(insertPageIndex, count)

    fun insertBlankPageAt(insertPageIndex: Int) = delegate.insertBlankPageAt(insertPageIndex)

    fun deleteTextOnPage(pageIndex: Int) = delegate.deleteTextOnPage(pageIndex)

    fun handleBackspaceAtStart(): Boolean = delegate.handleBackspaceAtStart()

    suspend fun remapPagesForLayoutChange(
        currentLayout: List<VirtualPage>,
        updatedLayout: List<VirtualPage>,
    ) {
        delegate.remapPagesForLayoutChange(
            currentLayout = currentLayout.map(VirtualPage::toSharedRichTextPageIdentity),
            updatedLayout = updatedLayout.map(VirtualPage::toSharedRichTextPageIdentity),
        )
    }

    suspend fun saveImmediate() = delegate.saveImmediate()
}
