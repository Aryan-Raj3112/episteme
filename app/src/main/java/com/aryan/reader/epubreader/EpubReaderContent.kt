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
import com.aryan.reader.applyBookReplacementsToHtmlDocument
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.epub.contentFilePath
import com.aryan.reader.paginatedreader.LocatorConverter
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import timber.log.Timber
import java.io.File

data class ChapterLoadingResult(
    val head: String,
    val chunks: List<String>,
    val startChunkIndex: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null,
    val chunkElementStartIndices: List<Int> = emptyList(),
    val chunkElementCounts: List<Int> = emptyList()
)

internal data class ReaderHtmlChunk(
    val html: String,
    val elementStartIndex: Int,
    val elementCount: Int
)

internal fun splitBodyNodesIntoReaderChunks(
    bodyNodes: List<Node>,
    chunkSize: Int = 20
): List<ReaderHtmlChunk> {
    var elementStartIndex = 0
    return bodyNodes.chunked(chunkSize).map { nodes ->
        val elementCount = nodes.count { it is Element }
        ReaderHtmlChunk(
            html = nodes.joinToString(separator = "\n") { it.outerHtml() },
            elementStartIndex = elementStartIndex,
            elementCount = elementCount
        ).also {
            elementStartIndex += elementCount
        }
    }
}

internal fun readerChunkContainerAttributes(
    index: Int,
    chunkElementStartIndices: List<Int>,
    chunkElementCounts: List<Int>
): String {
    val startIndex = chunkElementStartIndices.getOrElse(index) { index * 20 }
    val elementCount = chunkElementCounts.getOrElse(index) { 20 }
    return "data-chunk-index='$index' data-element-start-index='$startIndex' data-element-count='$elementCount'"
}

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
    locatorConverter: LocatorConverter,
    bookReplacementPreferences: ReaderBookReplacementPreferences = ReaderBookReplacementPreferences(),
    bookReplacementFileId: String? = null,
): ChapterLoadingResult = withContext(Dispatchers.IO) {
    val chapter =
        epubBook.chapters.getOrNull(chapterIndex) ?: return@withContext ChapterLoadingResult(
            "", emptyList(), 0, false, "Chapter index out of bounds"
        )

    try {
        val htmlFile = File(epubBook.extractionBasePath, chapter.contentFilePath())

        val (headContent, chunks, chunkElementStartIndices, chunkElementCounts) = if (htmlFile.exists()) {
            val doc = Jsoup.parse(htmlFile, "UTF-8")
            val head = doc.head().html()
            doc.select("script").remove()
            applyBookReplacementsToHtmlDocument(
                document = doc,
                preferences = bookReplacementPreferences,
                fileId = bookReplacementFileId,
            )
            val bodyNodes = doc.body().childNodes().toList()
            val htmlChunks = splitBodyNodesIntoReaderChunks(bodyNodes)
            if (htmlChunks.isEmpty()) {
                ChapterHtmlPayload(
                    head = head,
                    chunks = listOf("<body><p>${context.getString(R.string.chapter_empty)}</p></body>"),
                    chunkElementStartIndices = listOf(0),
                    chunkElementCounts = listOf(1)
                )
            } else {
                ChapterHtmlPayload(
                    head = head,
                    chunks = htmlChunks.map { it.html },
                    chunkElementStartIndices = htmlChunks.map { it.elementStartIndex },
                    chunkElementCounts = htmlChunks.map { it.elementCount }
                )
            }
        } else {
            ChapterHtmlPayload(
                head = "",
                chunks = listOf("<h1>${context.getString(R.string.chapter_not_found)}</h1>"),
                chunkElementStartIndices = listOf(0),
                chunkElementCounts = listOf(1)
            )
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
            isSuccess = true,
            chunkElementStartIndices = chunkElementStartIndices,
            chunkElementCounts = chunkElementCounts
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

private data class ChapterHtmlPayload(
    val head: String,
    val chunks: List<String>,
    val chunkElementStartIndices: List<Int>,
    val chunkElementCounts: List<Int>
)
