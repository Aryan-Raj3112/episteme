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
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.paginatedreader.data.BookCacheDao
import com.aryan.reader.paginatedreader.data.ProcessedChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf

data class Locator(
    val chapterIndex: Int,
    val blockIndex: Int,
    val charOffset: Int
)

/**
 * Converts between view-specific locators (like CFI) and the abstract Locator model.
 */
@OptIn(ExperimentalSerializationApi::class)
class LocatorConverter(
    private val bookCacheDao: BookCacheDao,
    private val proto: ProtoBuf,
    private val context: Context
) {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private suspend fun processAndCacheChapter(book: EpubBook, chapterIndex: Int): List<SemanticBlock>? = withContext(Dispatchers.IO) {
        try {
            val chapter = book.chapters.getOrNull(chapterIndex) ?: return@withContext null

            // 1. Parse CSS from the book
            var parsingCssRules = OptimizedCssRules()
            val density = Density(context)
            val displayMetrics = context.resources.displayMetrics
            val constraints = Constraints(maxWidth = displayMetrics.widthPixels, maxHeight = displayMetrics.heightPixels)

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = 16f, // A reasonable default for non-rendering parsing
                    density = density.density,
                    constraints = constraints,
                    isDarkTheme = false
                )
                parsingCssRules = parsingCssRules.merge(bookCssResult.rules)
            }

            // 2. Parse HTML to SemanticBlocks
            val semanticBlocks = htmlToSemanticBlocks(
                html = chapter.htmlContent,
                cssRules = parsingCssRules,
                textStyle = TextStyle(), // Not used for rendering, so a default is fine
                chapterAbsPath = chapter.absPath,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = emptyMap(),
                constraints = constraints
            )

            // 3. Serialize and cache the result
            val protoBytes = proto.encodeToByteArray(semanticBlocks)
            val newCacheEntry = ProcessedChapter(
                bookId = book.title,
                chapterIndex = chapterIndex,
                contentBlocksProto = protoBytes,
                estimatedPageCount = 0 // Page count is not relevant for locator conversion
            )
            bookCacheDao.insertProcessedChapters(listOf(newCacheEntry))
            Timber.i("On-demand processing SUCCESS for chapter $chapterIndex.")
            semanticBlocks
        } catch (e: Exception) {
            Timber.e(e, "On-demand processing FAILED for chapter $chapterIndex")
            null
        }
    }

    /**
     * Converts a CFI string from the WebView into an abstract Locator.
     */
    suspend fun getLocatorFromCfi(book: EpubBook, chapterIndex: Int, cfi: String): Locator? = withContext(Dispatchers.IO) {
        Timber.d("getLocatorFromCfi: Starting conversion for book='${book.title}', chapter=$chapterIndex, cfi='$cfi'")

        val processedChapter = bookCacheDao.getProcessedChapter(bookId = book.title, chapterIndex = chapterIndex)
        val allBlocks = if (processedChapter != null) {
            proto.decodeFromByteArray<List<SemanticBlock>>(processedChapter.contentBlocksProto)
        } else {
            Timber.w("getLocatorFromCfi: Chapter $chapterIndex not in DB. Triggering on-demand processing.")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                processAndCacheChapter(book, chapterIndex)
            } else {
                Timber.e("On-demand processing requires API 34+, cannot proceed.")
                null
            }
        }

        if (allBlocks == null) {
            Timber.w("getLocatorFromCfi: FAILED. Could not get or process semantic blocks for chapter $chapterIndex.")
            return@withContext null
        }


        val (baseCfiPath, charOffset) = cfi.split(':').let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }
        Timber.d("getLocatorFromCfi: Parsed CFI into basePath='$baseCfiPath' and charOffset=$charOffset")

        val bestMatch = findBestMatchingBlock(allBlocks, baseCfiPath)

        if (bestMatch != null) {
            Timber.i("getLocatorFromCfi: SUCCESS. Found best match. Block index: ${bestMatch.blockIndex}, Block CFI: '${bestMatch.cfi}'")
            Locator(
                chapterIndex = chapterIndex,
                blockIndex = bestMatch.blockIndex,
                charOffset = charOffset
            )
        } else {
            Timber.w("getLocatorFromCfi: FAILED. No matching block found for CFI base path '$baseCfiPath'.")
            null
        }
    }

    private fun findBestMatchingBlock(blocks: List<SemanticBlock>, inputCfi: String): SemanticBlock? {
        val flattenedBlocks = mutableListOf<SemanticBlock>()
        fun flatten(blockList: List<SemanticBlock>) {
            for (block in blockList) {
                flattenedBlocks.add(block)
                when (block) {
                    is SemanticFlexContainer -> flatten(block.children)
                    is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> flatten(cell.content) } }
                    is SemanticList -> flatten(block.items)
                    else -> Unit
                }
            }
        }
        flatten(blocks)

        if (flattenedBlocks.isEmpty()) return null

        flattenedBlocks.mapNotNull { it.cfi }
        val bestMatch = flattenedBlocks
            .filter { it.cfi != null }
            .map { block ->
                val blockCfi = block.cfi!!
                var i = inputCfi.length - 1
                var j = blockCfi.length - 1
                var length = 0
                while (i >= 0 && j >= 0 && inputCfi[i] == blockCfi[j]) {
                    length++
                    i--
                    j--
                }
                Pair(block, length)
            }
            .maxByOrNull { it.second }
            ?.first

        return bestMatch
    }

    suspend fun getCfiFromLocator(bookId: String, locator: Locator): String? = withContext(Dispatchers.IO) {
        Timber.d("getCfiFromLocator: Attempting to get CFI from locator: $locator")
        val processedChapter = bookCacheDao.getProcessedChapter(bookId = bookId, chapterIndex = locator.chapterIndex)
        if (processedChapter == null) {
            Timber.w("getCfiFromLocator: FAILED. Could not find processed chapter ${locator.chapterIndex} in database.")
            return@withContext null
        }
        val blocks = proto.decodeFromByteArray<List<SemanticBlock>>(processedChapter.contentBlocksProto)

        val foundBlock = findBlockByBlockIndex(blocks, locator.blockIndex)
        if (foundBlock != null) {
            foundBlock.cfi?.let { cfi ->
                val finalCfi = if (locator.charOffset > 0) {
                    "$cfi:${locator.charOffset}"
                } else {
                    cfi
                }
                Timber.i("getCfiFromLocator: SUCCESS. Found block ${foundBlock.blockIndex} with CFI '${foundBlock.cfi}'. Final CFI: '$finalCfi'")
                finalCfi
            }
        } else {
            Timber.w("getCfiFromLocator: FAILED. Could not find block with index ${locator.blockIndex} in chapter ${locator.chapterIndex}.")
            null
        }
    }

    private fun findBlockByBlockIndex(blocks: List<SemanticBlock>, targetBlockIndex: Int): SemanticBlock? {
        val queue = ArrayDeque(blocks)
        while (queue.isNotEmpty()) {
            val block = queue.removeAt(0)
            if (block.blockIndex == targetBlockIndex) {
                Timber.v("findBlockByBlockIndex: Found match for block index $targetBlockIndex.")
                return block
            }

            // Recurse into nested blocks
            when (block) {
                is SemanticFlexContainer -> queue.addAll(block.children)
                is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> queue.addAll(cell.content) } }
                is SemanticList -> queue.addAll(block.items)
                else -> Unit
            }
        }
        Timber.w("findBlockByBlockIndex: No block found for target index $targetBlockIndex.")
        return null
    }

    suspend fun getTextOffset(book: EpubBook, locator: Locator): Int? = withContext(Dispatchers.IO) {
        val processedChapter = bookCacheDao.getProcessedChapter(bookId = book.title, chapterIndex = locator.chapterIndex)
        val allBlocks = if (processedChapter != null) {
            proto.decodeFromByteArray<List<SemanticBlock>>(processedChapter.contentBlocksProto)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                processAndCacheChapter(book, locator.chapterIndex)
            } else null
        } ?: return@withContext null

        var offset = 0
        val separatorLength = 1

        fun traverse(blocks: List<SemanticBlock>): Boolean {
            for (block in blocks) {
                if (block.blockIndex == locator.blockIndex) {
                    offset += locator.charOffset
                    return true
                }

                if (block is SemanticTextBlock) {
                    offset += block.text.length + separatorLength
                }

                val children = when (block) {
                    is SemanticFlexContainer -> block.children
                    is SemanticTable -> block.rows.flatten().flatMap { it.content }
                    is SemanticList -> block.items
                    is SemanticWrappingBlock -> block.paragraphsToWrap
                    else -> emptyList()
                }

                if (children.isNotEmpty()) {
                    if (traverse(children)) return true
                }
            }
            return false
        }

        if (traverse(allBlocks)) {
            return@withContext offset
        }
        return@withContext null
    }
}