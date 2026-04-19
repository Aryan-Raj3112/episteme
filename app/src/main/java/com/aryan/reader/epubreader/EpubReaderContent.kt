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
package com.aryan.reader.epubreader

import android.content.Context
import com.aryan.reader.R
import timber.log.Timber
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.LocatorConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File

data class ChapterLoadingResult(
    val head: String,
    val chunks: List<String>,
    val startChunkIndex: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * loads the chapter HTML, splits it into chunks, and calculates
 * the initial chunk to display based on navigation state (CFI, overrides, etc.).
 */
suspend fun loadChapterContent(
    context: Context,
    epubBook: EpubBook,
    chapterIndex: Int,
    chunkTargetOverride: Int?,
    isInitialCfiLoad: Boolean,
    cfiToLoad: String?,
    locatorConverter: LocatorConverter
): ChapterLoadingResult = withContext(Dispatchers.IO) {
    val chapter =
        epubBook.chapters.getOrNull(chapterIndex) ?: return@withContext ChapterLoadingResult(
            "", emptyList(), 0, false, "Chapter index out of bounds"
        )

    try {
        val fullPath = "${epubBook.extractionBasePath}/${chapter.htmlFilePath}"
        val htmlFile = File(fullPath)

        val (headContent, chunks) = if (htmlFile.exists()) {
            val doc = Jsoup.parse(htmlFile, "UTF-8")
            val head = doc.head().html()
            doc.select("script").remove()
            val bodyNodes = doc.body().childNodes().toList()
            val chunkedList = bodyNodes.chunked(20).map { chunkOfNodes ->
                chunkOfNodes.joinToString(separator = "\n") { it.outerHtml() }
            }
            if (chunkedList.isEmpty()) {
                head to listOf("<body><p>${context.getString(R.string.chapter_empty)}</p></body>")
            } else {
                head to chunkedList
            }
        } else {
            "" to listOf("<h1>${context.getString(R.string.chapter_not_found)}</h1>")
        }

        var targetChunk = 0

        if (chunkTargetOverride != null) {
            Timber.d("Applying chunk target override: $chunkTargetOverride")
            targetChunk = chunkTargetOverride
        }
        else if (isInitialCfiLoad && cfiToLoad != null) {
            Timber.d("Calculating target chunk for initial CFI: $cfiToLoad")
            val locator = locatorConverter.getLocatorFromCfi(epubBook, chapterIndex, cfiToLoad)
            val calculatedChunk = locator?.let { it.blockIndex / 20 }

            if (calculatedChunk != null) {
                targetChunk = calculatedChunk
            } else {
                Timber.w("Could not determine target chunk for CFI. Loading all (fallback to last).")
                targetChunk = if (chunks.isNotEmpty()) chunks.size - 1 else 0
            }
        }

        targetChunk = targetChunk.coerceIn(0, maxOf(0, chunks.size - 1))

        ChapterLoadingResult(
            head = headContent,
            chunks = chunks,
            startChunkIndex = targetChunk,
            isSuccess = true
        )

    } catch (e: Exception) {
        Timber.e(e, "Failed to parse chapter")
        ChapterLoadingResult(
            head = "",
            chunks = listOf("<h1>${context.getString(R.string.error_loading_chapter)}</h1><p>${e.message}</p>"),
            startChunkIndex = 0,
            isSuccess = false,
            errorMessage = e.message
        )
    }
}