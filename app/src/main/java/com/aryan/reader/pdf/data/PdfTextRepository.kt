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
package com.aryan.reader.pdf.data

import android.content.Context
import android.graphics.RectF
import timber.log.Timber
import androidx.core.graphics.createBitmap
import com.aryan.reader.pdf.OcrHelper
import io.legere.pdfiumandroid.suspend.PdfDocumentKt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.flatMap
import com.aryan.reader.SearchResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

private const val TAG = "PdfSearchDiag"

sealed interface SmartSearchResult {
    data class Exact(val matches: List<SearchResult>) : SmartSearchResult
    data class Paged(val pagingData: Flow<PagingData<SearchResult>>, val totalPageCount: Int) : SmartSearchResult
}

class PdfTextRepository(context: Context) {
    private val db = PdfTextDatabase.getDatabase(context)
    private val dao = db.pdfTextDao()
    private val metaDao = db.pdfMetaDao()

    suspend fun getPageRatios(bookId: String): List<Float>? {
        return withContext(Dispatchers.IO) {
            val meta = metaDao.getMetadata(bookId)
            if (meta != null && meta.ratiosJson.isNotEmpty()) {
                try {
                    val jsonArray = JSONArray(meta.ratiosJson)
                    val list = ArrayList<Float>(jsonArray.length())
                    for (i in 0 until jsonArray.length()) {
                        list.add(jsonArray.getDouble(i).toFloat())
                    }
                    list
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to parse ratios json")
                    null
                }
            } else {
                null
            }
        }
    }

    suspend fun savePageRatios(bookId: String, ratios: List<Float>) {
        withContext(Dispatchers.IO) {
            try {
                val jsonString = JSONArray(ratios).toString()
                val existing = metaDao.getMetadata(bookId)
                val lang = existing?.ocrLanguage ?: "LATIN"

                metaDao.insertMetadata(PdfMetadata(bookId, ratios.size, jsonString, lang))
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to save page ratios")
            }
        }
    }

    suspend fun getBookLanguage(bookId: String): String? {
        return withContext(Dispatchers.IO) {
            metaDao.getMetadata(bookId)?.ocrLanguage
        }
    }

    suspend fun setBookLanguage(bookId: String, language: String) {
        withContext(Dispatchers.IO) {
            val existing = metaDao.getMetadata(bookId)
            if (existing != null) {
                metaDao.updateLanguage(bookId, language)
            } else {
                metaDao.insertMetadata(PdfMetadata(bookId, 0, "", language))
            }
        }
    }

    suspend fun getIndexedPages(bookId: String): Set<Int> {
        return withContext(Dispatchers.IO) {
            dao.getIndexedPageIndices(bookId).toSet()
        }
    }

    /**
     * Helper to generate a safe FTS query by stripping punctuation from tokens.
     * This fixes issues where "xyz." would fail to match "xyz" in the index.
     * The strict punctuation check is handled later by the Regex filter.
     */
    private fun generateFtsQuery(query: String): String {
        val sb = StringBuilder()
        for (char in query) {
            // Keep letters, digits, and underscores. Replace everything else (punctuation) with space.
            if (char.isLetterOrDigit() || char == '_') {
                sb.append(char)
            } else {
                sb.append(' ')
            }
        }
        // Split by whitespace and create FTS prefix tokens (e.g., "content:token*")
        val tokens = sb.toString().split("\\s+".toRegex()).filter { it.isNotBlank() }

        // If query was only punctuation (e.g. "?"), return a token that likely matches nothing or handle gracefully.
        // Returning empty string causes 'MATCH ""' which usually returns nothing.
        return tokens.joinToString(" ") { "content:$it*" }
    }

    fun searchBookFlow(bookId: String, query: String): Flow<List<PdfSearchMatch>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return dao.searchBookFlow(bookId, "")
        }

        // Use the sanitized FTS query for database retrieval
        val ftsQuery = generateFtsQuery(trimmed)

        // Use the strict Regex for precise filtering
        val phraseRegex = createPhraseRegex(query)

        Timber.tag(TAG).i("Search initiated for bookId length: ${bookId.length}")
        Timber.tag(TAG).i("User Query: '$query'")
        Timber.tag(TAG).i("Generated FTS Query: '$ftsQuery'")
        Timber.tag(TAG).i("Generated Regex: '$phraseRegex'")

        // Filter the FTS matches to ensure they satisfy the strict phrase regex
        return dao.searchBookFlow(bookId, ftsQuery).map { list ->
            list.filter { match ->
                phraseRegex.containsMatchIn(match.content)
            }
        }
    }

    suspend fun indexPage(
        bookId: String,
        document: PdfDocumentKt,
        pageIndex: Int,
        onOcrModelDownloading: () -> Unit = {}
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var text = ""
            var ocrUsed = false

            try {
                document.openPage(pageIndex).use { page ->
                    page.openTextPage().use { textPage ->
                        val count = textPage.textPageCountChars()
                        if (count > 0) {
                            val nativeText = textPage.textPageGetText(0, count)
                            if (!nativeText.isNullOrBlank()) {
                                text = nativeText
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Native extraction failed for page $pageIndex")
            }

            if (text.isBlank()) {
                try {
                    document.openPage(pageIndex).use { page ->
                        val targetWidth = 1080
                        val ptrWidth = page.getPageWidthPoint()
                        val ptrHeight = page.getPageHeightPoint()

                        if (ptrWidth > 0 && ptrHeight > 0) {
                            val aspectRatio = ptrWidth.toFloat() / ptrHeight.toFloat()
                            val targetHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(1)

                            val bitmap = createBitmap(targetWidth, targetHeight)
                            page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false)

                            val visionText = OcrHelper.extractTextFromBitmap(bitmap, onOcrModelDownloading)
                            text = visionText?.text ?: ""
                            bitmap.recycle()
                            ocrUsed = true
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "OCR failed for page $pageIndex")
                }
            }

            if (text.isNotEmpty()) {
                val rawLength = text.length

                val patterns = listOf(
                    Regex("(?i)file:/?/?/?\\S+"),
                    Regex("(?i)/data/user/\\d+/\\S+"),
                    Regex("(?i)/storage/emulated/\\d+/\\S+"),
                    Regex("(?i)\\S*com\\.aryan\\.reader\\S*")
                )

                patterns.forEach { pattern ->
                    text = text.replace(pattern, " ")
                }

                text = text.replace(Regex("\\s+"), " ").trim()

                val cleanedLength = text.length
                val snippetClean = text.take(50).replace("\n", " ")

                if (cleanedLength < rawLength) {
                    Timber.tag(TAG).d("Page $pageIndex cleaned. Size reduced: $rawLength -> $cleanedLength. New Start: '$snippetClean'")
                }

                if (text.contains("file://") || text.length > 10 && text.startsWith("/")) {
                    Timber.tag(TAG).e("Page $pageIndex: Cleaning might have failed. Text still looks like path: $snippetClean")
                } else if (text.isNotBlank()) {
                    Timber.tag(TAG).v("Page $pageIndex: Inserting valid text ($cleanedLength chars).")
                    dao.insertPageText(PdfSearchIndex(bookId = bookId, pageIndex = pageIndex, content = text))
                } else {
                    Timber.tag(TAG).i("Page $pageIndex: Text became empty after cleaning. Skipping insertion.")
                }
            } else {
                Timber.tag(TAG).v("Page $pageIndex: No text found (Native or OCR).")
            }

            ocrUsed && text.isNotEmpty()
        }
    }

    suspend fun getOrExtractText(
        bookId: String,
        document: PdfDocumentKt,
        pageIndex: Int,
        onModelDownloading: () -> Unit = {}
    ): String {
        return withContext(Dispatchers.IO) {
            val cachedText = dao.getPageText(bookId, pageIndex)
            if (!cachedText.isNullOrBlank()) {
                return@withContext cachedText
            }

            indexPage(bookId, document, pageIndex, onModelDownloading)
            dao.getPageText(bookId, pageIndex) ?: ""
        }
    }

    suspend fun hasNativeText(document: PdfDocumentKt, pageIndex: Int): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                document.openPage(pageIndex).use { page ->
                    page.openTextPage().use { textPage ->
                        textPage.textPageCountChars() > 0
                    }
                }
            } catch (_: Exception) {
                false
            }
        }
    }

    suspend fun getOcrSearchRects(
        document: PdfDocumentKt,
        pageIndex: Int,
        query: String,
        onModelDownloading: () -> Unit = {}
    ): List<RectF> {
        return withContext(Dispatchers.IO) {
            val rects = mutableListOf<RectF>()
            try {
                document.openPage(pageIndex).use { page ->
                    val targetWidth = 1080
                    val ptrWidth = page.getPageWidthPoint()
                    val ptrHeight = page.getPageHeightPoint()

                    if (ptrWidth <= 0 || ptrHeight <= 0) return@use

                    val aspectRatio = ptrWidth.toFloat() / ptrHeight.toFloat()
                    val targetHeight = (targetWidth / aspectRatio).toInt().coerceAtLeast(1)

                    val bitmap = createBitmap(targetWidth, targetHeight)
                    page.renderPageBitmap(bitmap, 0, 0, targetWidth, targetHeight, false)

                    val visionText = OcrHelper.extractTextFromBitmap(bitmap, onModelDownloading)

                    visionText?.textBlocks?.forEach { block ->
                        block.lines.forEach { line ->
                            line.elements.forEach { element ->
                                if (element.text.contains(query, ignoreCase = true)) {
                                    element.boundingBox?.let { box ->
                                        val normalized = RectF(
                                            box.left.toFloat() / targetWidth,
                                            box.top.toFloat() / targetHeight,
                                            box.right.toFloat() / targetWidth,
                                            box.bottom.toFloat() / targetHeight
                                        )
                                        rects.add(normalized)
                                    }
                                }
                            }
                        }
                    }
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to get OCR rects for page $pageIndex")
            }
            rects
        }
    }

    suspend fun clearBookText(bookId: String) {
        withContext(Dispatchers.IO) {
            dao.clearBookText(bookId)
        }
    }

    suspend fun clearAllText() {
        withContext(Dispatchers.IO) {
            dao.deleteAll()
        }
    }

    private fun isAscii(string: String): Boolean {
        return string.all { it.code < 128 }
    }

    private fun createPhraseRegex(query: String): Regex {
        val clean = query.trim().replace("\"", "")
        // Split by whitespace to handle user typing multiple spaces
        val tokens = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return Regex("(?i)${Regex.escape(query)}") // Fallback

        val isAscii = isAscii(clean)
        val sb = StringBuilder("(?i)") // Case insensitive flag

        // If ASCII, use word boundary at start.
        if (isAscii) {
            sb.append("\\b")
        }

        // Join tokens with \s+ to match any whitespace sequence in content
        val escapedTokens = tokens.map { Regex.escape(it) }
        sb.append(escapedTokens.joinToString("\\s+"))

        return Regex(sb.toString())
    }

    fun searchBookSmart(bookId: String, query: String): Flow<SmartSearchResult> = flow {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return@flow
        }

        // Use sanitized FTS query
        val ftsQuery = generateFtsQuery(trimmed)

        val pageMatchCount = dao.countMatches(bookId, ftsQuery)
        val phraseRegex = createPhraseRegex(query)

        if (pageMatchCount > 50) {
            emit(SmartSearchResult.Paged(
                pagingData = getSearchResultsPaged(bookId, query, phraseRegex),
                totalPageCount = pageMatchCount
            ))
        } else {
            val rawMatches = dao.getAllMatches(bookId, ftsQuery)
            val fullResults = mutableListOf<SearchResult>()

            rawMatches.forEach { match ->
                val regexMatches = phraseRegex.findAll(match.content)
                var occurrenceIndex = 0

                for (regexMatch in regexMatches) {
                    fullResults.add(
                        SearchResult(
                            locationInSource = match.pageIndex,
                            locationTitle = "Page ${match.pageIndex + 1}",
                            snippet = generateSnippet(match.content, regexMatch.range),
                            query = query,
                            occurrenceIndexInLocation = occurrenceIndex,
                            chunkIndex = match.pageIndex
                        )
                    )
                    occurrenceIndex++
                }
            }
            emit(SmartSearchResult.Exact(fullResults))
        }
    }

    private fun generateSnippet(content: String, matchRange: IntRange): AnnotatedString {
        val snippetContextChars = 60
        val start = (matchRange.first - snippetContextChars).coerceAtLeast(0)
        val end = (matchRange.last + snippetContextChars).coerceAtMost(content.length)

        val rawSnippet = content.substring(start, end)
        // Adjust match indices relative to snippet
        val matchStartInSnippet = matchRange.first - start
        val matchEndInSnippet = matchRange.last - start

        return buildAnnotatedString {
            if (start > 0) append("...")

            // Text before match
            if (matchStartInSnippet > 0) {
                append(rawSnippet.substring(0, matchStartInSnippet))
            }

            // The Match
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color.Blue))
            val actualMatchLength = (matchEndInSnippet - matchStartInSnippet + 1).coerceAtMost(rawSnippet.length - matchStartInSnippet)
            if (actualMatchLength > 0) {
                append(rawSnippet.substring(matchStartInSnippet, matchStartInSnippet + actualMatchLength))
            }
            pop()

            // Text after match
            if (matchEndInSnippet < rawSnippet.length - 1) {
                append(rawSnippet.substring(matchEndInSnippet + 1))
            }

            if (end < content.length) append("...")
        }
    }

    fun getSearchResultsPaged(bookId: String, query: String, regex: Regex? = null): Flow<PagingData<SearchResult>> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            return kotlinx.coroutines.flow.flowOf(PagingData.empty())
        }

        val ftsQuery = generateFtsQuery(trimmed)
        val phraseRegex = regex ?: createPhraseRegex(query)

        return Pager(
            config = PagingConfig(pageSize = 20, prefetchDistance = 10, enablePlaceholders = false)
        ) {
            dao.searchBookPagingSource(bookId, ftsQuery)
        }.flow.map { pagingData ->
            pagingData.flatMap { match ->
                val results = mutableListOf<SearchResult>()
                val regexMatches = phraseRegex.findAll(match.content)

                var occurrenceIndex = 0
                for (regexMatch in regexMatches) {
                    results.add(
                        SearchResult(
                            locationInSource = match.pageIndex,
                            locationTitle = "Page ${match.pageIndex + 1}",
                            snippet = generateSnippet(match.content, regexMatch.range),
                            query = query,
                            occurrenceIndexInLocation = occurrenceIndex,
                            chunkIndex = match.pageIndex
                        )
                    )
                    occurrenceIndex++
                }
                results
            }
        }
    }

    suspend fun getNextResult(bookId: String, query: String, currentResult: SearchResult?): SearchResult? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        val ftsQuery = generateFtsQuery(trimmed)
        val phraseRegex = createPhraseRegex(query)

        val currentPageIndex = currentResult?.chunkIndex ?: -1
        val currentOccurrenceIndex = currentResult?.occurrenceIndexInLocation ?: -1

        // Check current page for next occurrence
        if (currentPageIndex >= 0) {
            val pageText = dao.getPageText(bookId, currentPageIndex)
            if (pageText != null) {
                val matches = phraseRegex.findAll(pageText).toList()
                if (currentOccurrenceIndex + 1 < matches.size) {
                    val nextMatch = matches[currentOccurrenceIndex + 1]
                    return currentResult!!.copy(
                        occurrenceIndexInLocation = currentOccurrenceIndex + 1,
                        snippet = generateSnippet(pageText, nextMatch.range)
                    )
                }
            }
        }

        // Search subsequent pages
        var searchPageIndex = currentPageIndex + 1
        var attempts = 0
        val maxAttempts = 50 // Limit linear scan depth to prevent UI freezes on sparse results

        while(attempts < maxAttempts) {
            val nextPageMatch = dao.getNextPageWithMatch(bookId, ftsQuery, searchPageIndex) ?: return null
            val regexMatches = phraseRegex.findAll(nextPageMatch.content).toList()

            if (regexMatches.isNotEmpty()) {
                val firstMatch = regexMatches.first()
                return SearchResult(
                    locationInSource = nextPageMatch.pageIndex,
                    locationTitle = "Page ${nextPageMatch.pageIndex + 1}",
                    snippet = generateSnippet(nextPageMatch.content, firstMatch.range),
                    query = query,
                    occurrenceIndexInLocation = 0,
                    chunkIndex = nextPageMatch.pageIndex
                )
            }
            searchPageIndex = nextPageMatch.pageIndex + 1
            attempts++
        }
        return null
    }

    suspend fun getPrevResult(bookId: String, query: String, currentResult: SearchResult?): SearchResult? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        val ftsQuery = generateFtsQuery(trimmed)
        val phraseRegex = createPhraseRegex(query)

        val currentPageIndex = currentResult?.chunkIndex ?: 0
        val currentOccurrenceIndex = currentResult?.occurrenceIndexInLocation ?: 0

        // Check current page for previous occurrence
        if (currentPageIndex >= 0 && currentOccurrenceIndex > 0) {
            val pageText = dao.getPageText(bookId, currentPageIndex)
            if (pageText != null) {
                val matches = phraseRegex.findAll(pageText).toList()
                if (currentOccurrenceIndex - 1 < matches.size) {
                    val prevMatch = matches[currentOccurrenceIndex - 1]
                    return currentResult!!.copy(
                        occurrenceIndexInLocation = currentOccurrenceIndex - 1,
                        snippet = generateSnippet(pageText, prevMatch.range)
                    )
                }
            }
        }

        // Search previous pages
        var searchPageIndex = currentPageIndex - 1
        var attempts = 0
        val maxAttempts = 50

        while (attempts < maxAttempts && searchPageIndex >= 0) {
            val prevPageMatch = dao.getPrevPageWithMatch(bookId, ftsQuery, searchPageIndex) ?: return null
            val regexMatches = phraseRegex.findAll(prevPageMatch.content).toList()

            if (regexMatches.isNotEmpty()) {
                val lastMatch = regexMatches.last()
                return SearchResult(
                    locationInSource = prevPageMatch.pageIndex,
                    locationTitle = "Page ${prevPageMatch.pageIndex + 1}",
                    snippet = generateSnippet(prevPageMatch.content, lastMatch.range),
                    query = query,
                    occurrenceIndexInLocation = regexMatches.size - 1,
                    chunkIndex = prevPageMatch.pageIndex
                )
            }
            searchPageIndex = prevPageMatch.pageIndex - 1
            attempts++
        }

        return null
    }
}