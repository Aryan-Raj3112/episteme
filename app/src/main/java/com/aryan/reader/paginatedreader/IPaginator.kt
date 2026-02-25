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

import androidx.compose.runtime.Stable
import com.aryan.reader.SearchResult
import kotlinx.coroutines.flow.Flow

@Stable
interface IPaginator {
    val totalPageCount: Int
    val isLoading: Boolean
    val generation: Int
    val pageShiftRequest: Flow<Int>

    fun getPageContent(pageIndex: Int): Page?
    fun getChapterPathForPage(pageIndex: Int): String?
    fun getPlainTextForChapter(chapterIndex: Int): String?
    fun navigateToHref(
        currentChapterAbsPath: String,
        href: String,
        onNavigationComplete: (pageIndex: Int) -> Unit
    )
    fun findPageForSearchResult(
        result: SearchResult,
        onResult: (pageIndex: Int) -> Unit
    )
    fun findPageForAnchor(
        chapterIndex: Int,
        anchor: String?,
        onResult: (pageIndex: Int) -> Unit
    )
    fun findPageForCfi(chapterIndex: Int, cfi: String, onResult: (pageIndex: Int) -> Unit)
    fun findPageForCfiAndOffset(chapterIndex: Int, cfi: String, charOffset: Int): Int?
    fun findChapterIndexForPage(pageIndex: Int): Int?
    fun getCfiForPage(pageIndex: Int): String?
    fun onUserScrolledTo(pageIndex: Int)
    fun getActiveAnchorForPage(pageIndex: Int, tocAnchors: List<String>): String?
}