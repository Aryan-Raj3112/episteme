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
package com.aryan.reader.paginatedreader

import android.content.Context
import timber.log.Timber
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
import java.io.File

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
    private suspend fun processAndCacheChapter(book: EpubBook, chapterIndex: Int): List<SemanticBlock>? = withContext(Dispatchers.IO) {
        Timber.tag("POS_DIAG").d("processAndCacheChapter: Processing for bookId='${book.title}' index=$chapterIndex")
        try {
            val chapter = book.chapters.getOrNull(chapterIndex) ?: return@withContext null

            val htmlToParse = chapter.htmlContent.ifBlank {
                try {
                    val file = File(book.extractionBasePath, chapter.htmlFilePath)
                    if (file.exists()) {
                        val content = file.readText()
                        content
                    } else {
                        ""
                    }
                } catch (_: Exception) {
                    ""
                }
            }

            if (htmlToParse.isBlank()) {
                return@withContext null
            }

            val mergedByTag = mutableMapOf<String, MutableList<CssRule>>()
            val mergedByClass = mutableMapOf<String, MutableList<CssRule>>()
            val mergedById = mutableMapOf<String, MutableList<CssRule>>()
            val mergedOtherComplex = mutableListOf<CssRule>()

            val density = Density(context)
            val displayMetrics = context.resources.displayMetrics
            val constraints = Constraints(maxWidth = displayMetrics.widthPixels, maxHeight = displayMetrics.heightPixels)

            fun aggregateRules(
                target: MutableMap<String, MutableList<CssRule>>,
                source: Map<String, List<CssRule>>
            ) {
                source.forEach { (k, v) ->
                    target.getOrPut(k) { mutableListOf() }.addAll(v)
                }
            }

            book.css.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = 16f,
                    density = density.density,
                    constraints = constraints,
                    isDarkTheme = false
                )

                val rules = bookCssResult.rules
                aggregateRules(mergedByTag, rules.byTag)
                aggregateRules(mergedByClass, rules.byClass)
                aggregateRules(mergedById, rules.byId)
                mergedOtherComplex.addAll(rules.otherComplex)
            }

            val parsingCssRules = OptimizedCssRules(
                byTag = mergedByTag,
                byClass = mergedByClass,
                byId = mergedById,
                otherComplex = mergedOtherComplex
            )

            val semanticBlocks = htmlToSemanticBlocks(
                html = htmlToParse,
                cssRules = parsingCssRules,
                textStyle = TextStyle(),
                chapterAbsPath = chapter.absPath,
                extractionBasePath = book.extractionBasePath,
                density = density,
                fontFamilyMap = emptyMap(),
                constraints = constraints
            )

            val protoBytes = proto.encodeToByteArray(semanticBlocks)

            val newCacheEntry = ProcessedChapter(
                bookId = book.title,
                chapterIndex = chapterIndex,
                contentBlocksProto = protoBytes,
                estimatedPageCount = 0
            )
            bookCacheDao.insertProcessedChapters(listOf(newCacheEntry))
            semanticBlocks
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getLocatorFromCfi(book: EpubBook, chapterIndex: Int, cfi: String): Locator? = withContext(Dispatchers.IO) {
        val processedChapter = bookCacheDao.getProcessedChapter(bookId = book.title, chapterIndex = chapterIndex)

        var allBlocks: List<SemanticBlock>? = null

        if (processedChapter != null && processedChapter.contentBlocksProto.isNotEmpty()) {
            allBlocks = try {
                proto.decodeFromByteArray<List<SemanticBlock>>(processedChapter.contentBlocksProto)
            } catch (_: Exception) { null }
        }

        if (allBlocks.isNullOrEmpty()) {
            allBlocks = processAndCacheChapter(book, chapterIndex)
        }

        if (allBlocks.isNullOrEmpty()) {
            return@withContext null
        }

        val (baseCfiPath, charOffset) = cfi.split(':').let {
            it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0)
        }

        val bestMatch = findBestMatchingBlock(allBlocks, baseCfiPath)

        if (bestMatch != null) {
            Timber.tag("PosSaveDiag").d("Found best match for baseCfiPath $baseCfiPath -> blockIndex=${bestMatch.blockIndex}, actualBlockCfi=${bestMatch.cfi}")
            Locator(
                chapterIndex = chapterIndex,
                blockIndex = bestMatch.blockIndex,
                charOffset = charOffset
            )
        } else {
            Timber.tag("PosSaveDiag").e("No semantic block match found for baseCfiPath $baseCfiPath inside ${allBlocks.size} parsed blocks")
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

    suspend fun getCfiFromLocator(book: EpubBook, locator: Locator): String? = withContext(Dispatchers.IO) {
        val processedChapter = bookCacheDao.getProcessedChapter(bookId = book.title, chapterIndex = locator.chapterIndex)

        var blocks: List<SemanticBlock>? = null
        if (processedChapter != null && processedChapter.contentBlocksProto.isNotEmpty()) {
            blocks = try {
                proto.decodeFromByteArray<List<SemanticBlock>>(processedChapter.contentBlocksProto)
            } catch (_: Exception) { null }
        }

        if (blocks.isNullOrEmpty()) {
            blocks = processAndCacheChapter(book, locator.chapterIndex)
        }

        if (blocks.isNullOrEmpty()) {
            return@withContext null
        }

        val foundBlock = findBlockByBlockIndex(blocks, locator.blockIndex)
        foundBlock?.cfi?.let { cfi ->
            if (locator.charOffset > 0) {
                "$cfi:${locator.charOffset}"
            } else {
                cfi
            }
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

        var allBlocks: List<SemanticBlock>? = null
        if (processedChapter != null && processedChapter.contentBlocksProto.isNotEmpty()) {
            allBlocks = try {
                proto.decodeFromByteArray<List<SemanticBlock>>(processedChapter.contentBlocksProto)
            } catch(_: Exception) { null }
        }

        if (allBlocks.isNullOrEmpty()) {
            allBlocks = processAndCacheChapter(book, locator.chapterIndex)
        }

        if (allBlocks.isNullOrEmpty()) return@withContext null

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