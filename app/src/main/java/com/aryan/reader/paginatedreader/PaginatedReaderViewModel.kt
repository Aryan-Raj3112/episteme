/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.paginatedreader

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.data.BookCacheDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf

data class PaginatedReaderUiState(
    val isLoading: Boolean = true,
    val totalPageCount: Int = 0,
    val generation: Int = 0
)

@OptIn(ExperimentalSerializationApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class PaginatedReaderViewModel : ViewModel() {
    @VisibleForTesting
    internal var paginator: IPaginator? = null
        private set

    private val _uiState = MutableStateFlow(PaginatedReaderUiState())
    val uiState: StateFlow<PaginatedReaderUiState> = _uiState.asStateFlow()

    @VisibleForTesting
    internal fun setPaginatorForTest(testPaginator: IPaginator) {
        paginator = testPaginator
        observePaginatorState()
    }

    companion object {
        val proto = ProtoBuf { serializersModule = semanticBlockModule }
    }

    fun initialize(
        book: EpubBook,
        textMeasurer: TextMeasurer,
        textConstraints: Constraints,
        textStyle: TextStyle,
        density: Density,
        isDarkTheme: Boolean,
        context: Context,
        initialChapterToPaginate: Int?,
        mathMLRenderer: MathMLRenderer
    ) {
        if (paginator != null) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // CSS Parsing and Font Loading
            val userAgentStylesheet = UserAgentStylesheet.default
            var allRules = OptimizedCssRules() // CHANGED from: mutableListOf<CssRule>()
            val allFontFaces = mutableListOf<FontFaceInfo>()

            val uaResult = CssParser.parse(
                cssContent = userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = textStyle.fontSize.value,
                density = density.density,
                constraints = textConstraints,
                isDarkTheme = isDarkTheme
            )
            allRules = allRules.merge(uaResult.rules) // CHANGED
            allFontFaces.addAll(uaResult.fontFaces)

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = textStyle.fontSize.value,
                    density = density.density,
                    constraints = textConstraints,
                    isDarkTheme = isDarkTheme
                )
                allRules = allRules.merge(bookCssResult.rules) // CHANGED
                allFontFaces.addAll(bookCssResult.fontFaces)
            }
            val fontFamilyMap = loadFontFamilies(
                fontFaces = allFontFaces,
                extractionPath = book.extractionBasePath
            )
            val bookId = book.title
            val bookCacheDao = BookCacheDatabase.getDatabase(context.applicationContext).bookCacheDao()
            val newPaginator = BookPaginator(
                coroutineScope = viewModelScope,
                chapters = book.chaptersForPagination,
                textMeasurer = textMeasurer,
                constraints = textConstraints,
                textStyle = textStyle,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = fontFamilyMap,
                isDarkTheme = isDarkTheme,
                bookId = bookId,
                bookCacheDao = bookCacheDao,
                proto = proto,
                initialChapterToPaginate = initialChapterToPaginate ?: 0,
                bookCss = book.css,
                userAgentStylesheet = userAgentStylesheet,
                allFontFaces = allFontFaces,
                context = context.applicationContext,
                mathMLRenderer = mathMLRenderer,
                userTextAlign = null
            )
            paginator = newPaginator

            observePaginatorState()
        }
    }

    private fun observePaginatorState() {
        val p = paginator ?: return
        viewModelScope.launch {
            snapshotFlow { p.isLoading }.collect {
                _uiState.value = _uiState.value.copy(isLoading = it)
            }
        }
        viewModelScope.launch {
            snapshotFlow { p.totalPageCount }.collect {
                _uiState.value = _uiState.value.copy(totalPageCount = it)
            }
        }
        viewModelScope.launch {
            snapshotFlow { p.generation }.collect {
                _uiState.value = _uiState.value.copy(generation = it)
            }
        }
    }

    fun onLinkClick(currentChapterPath: String, href: String, onNavigationComplete: (Int) -> Unit) {
        paginator?.navigateToHref(currentChapterPath, href, onNavigationComplete)
    }
}