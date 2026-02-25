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
import androidx.collection.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.aryan.reader.SearchResult
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.data.BookCacheDao
import com.aryan.reader.paginatedreader.data.BookProcessingInput
import com.aryan.reader.paginatedreader.data.BookProcessingWorker
import com.aryan.reader.paginatedreader.data.ConfigurationCache
import com.aryan.reader.paginatedreader.data.LATEST_PROCESSING_VERSION
import com.aryan.reader.paginatedreader.data.ProcessedBook
import com.aryan.reader.paginatedreader.data.ProcessedChapter
import com.aryan.reader.paginatedreader.data.SerializableEpubChapter
import com.aryan.reader.tts.PageCharacterRange
import com.aryan.reader.tts.splitTextIntoChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.PriorityBlockingQueue

private const val PRIORITY_HIGHEST = 0
private const val PRIORITY_HIGH = 1
private const val PRIORITY_MEDIUM = 2
private const val PRIORITY_LOW = 3

data class TimedWord(val word: String, val startTime: Double, val startOffset: Int)

data class TtsChunk(
    val text: String,
    val sourceCfi: String,
    val startOffsetInSource: Int,
    val timedWords: List<TimedWord> = emptyList()
)

private data class PaginationRequest(val chapterIndex: Int, val priority: Int) : Comparable<PaginationRequest> {
    override fun compareTo(other: PaginationRequest): Int {
        val priorityCompare = this.priority.compareTo(other.priority)
        return if (priorityCompare == 0) {
            this.chapterIndex.compareTo(other.chapterIndex)
        } else {
            priorityCompare
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Stable
class BookPaginator(
    private val coroutineScope: CoroutineScope,
    private val chapters: List<EpubChapter>,
    private val textMeasurer: TextMeasurer,
    private val constraints: Constraints,
    private val textStyle: TextStyle,
    private val extractionBasePath: String,
    private val density: Density,
    private val fontFamilyMap: Map<String, FontFamily>,
    private val isDarkTheme: Boolean,
    private val bookId: String,
    private val initialChapterToPaginate: Int,
    private val bookCss: Map<String, String>,
    private val userAgentStylesheet: String,
    private val bookCacheDao: BookCacheDao,
    private val proto: ProtoBuf,
    private val allFontFaces: List<FontFaceInfo>,
    private val context: Context,
    private val mathMLRenderer: MathMLRenderer,
    private val userTextAlign: TextAlign?
) : IPaginator {
    override var totalPageCount by mutableIntStateOf(0)
        private set

    override var isLoading by mutableStateOf(true)
        private set

    override var generation by mutableIntStateOf(0)
        private set

    override val pageShiftRequest = MutableSharedFlow<Int>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val currentUserChapterIndex = MutableStateFlow(initialChapterToPaginate)

    internal val chapterPageCounts = ConcurrentHashMap<Int, Int>()
    val chapterStartPageIndices = ConcurrentHashMap<Int, Int>()

    private val pageCache = object : LruCache<Int, List<Page>>(6) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: List<Page>, newValue: List<Page>?) {
            Timber.d("Chapter $key pages removed from cache. Evicted: $evicted")
        }
    }

    private val blockCache = object : LruCache<Int, List<ContentBlock>>(15) {
        override fun entryRemoved(evicted: Boolean, key: Int, oldValue: List<ContentBlock>, newValue: List<ContentBlock>?) {
            Timber.d("Chapter $key blocks removed from L2 blockCache. Evicted: $evicted")
        }
    }
    private val chapterCharacterIndex = ConcurrentHashMap<Int, List<PageCharacterRange>>()
    private val chapterCumulativeChars = ConcurrentHashMap<Int, List<Long>>()

    private var pageCountsAreAccurate by mutableStateOf(false)
    private val finalizedChapterCounts = ConcurrentHashMap.newKeySet<Int>()
    private var currentConfigHash: Int = 0

    private val paginationQueue = PriorityBlockingQueue<PaginationRequest>()
    private val chaptersBeingProcessed = ConcurrentHashMap.newKeySet<Int>()
    private val navigationCallbacks = ConcurrentHashMap<Int, MutableList<(List<Page>) -> Unit>>()
    private var paginationWorker: Job? = null

    internal fun getCharactersScrolledInChapter(chapterIndex: Int, pageInChapter: Int): Long {
        val cumulativeCharsList = chapterCumulativeChars[chapterIndex]
        if (cumulativeCharsList.isNullOrEmpty()) {
            Timber.w("[Paginator] getCharactersScrolledInChapter: No cumulative char index found for chapter $chapterIndex.")
            return 0L
        }

        val scrolledChars = if (pageInChapter > 0) {
            cumulativeCharsList.getOrNull(pageInChapter - 1) ?: cumulativeCharsList.lastOrNull() ?: 0L
        } else {
            0L
        }

        return scrolledChars
    }

    init {
        if (!constraints.hasBoundedHeight) {
            Timber.e("Paginator received UNBOUNDED HEIGHT. Pagination will fail.")
        } else {
            Timber.i("Paginator initializing with constraints: $constraints")
            coroutineScope.launch {
                isLoading = true
                Timber.d("Initialization started.")

                // 1. Book processing check (Keep existing logic)
                val bookRecord = bookCacheDao.getProcessedBook(bookId)
                if (bookRecord == null || bookRecord.processingVersion < LATEST_PROCESSING_VERSION) {
                    Timber.i("Book cache is new or stale. Creating initial record.")
                    val initialBook = ProcessedBook(bookId, LATEST_PROCESSING_VERSION, 0) // Temp 0
                    bookCacheDao.insertProcessedBook(initialBook)
                    enqueueBookProcessingWork()
                }

                // 2. GENERATE CONFIG HASH
                currentConfigHash = generateConfigurationHash()

                // 3. TRY LOAD EXACT COUNTS FROM DB
                val cachedConfig = bookCacheDao.getConfigurationCache(bookId, currentConfigHash)

                if (cachedConfig != null) {
                    Timber.i("Configuration Cache HIT. Using saved page counts.")
                    applyAccuratePageCounts(cachedConfig.chapterPageCounts)
                } else {
                    Timber.i("Configuration Cache MISS. Running instant estimator.")
                    runEstimator()
                }

                // 4. Start Worker
                paginationWorker = startPaginationWorker()

                // 5. Prioritize CURRENT chapter only
                // We no longer blindly queue neighbors immediately to keep startup fast.
                // We only queue the requested chapter.
                val startChapter = initialChapterToPaginate.coerceIn(0, chapters.size - 1)

                // Trigger actual pagination for the current chapter to replace the estimate with reality
                triggerPagination(startChapter, PRIORITY_HIGHEST)

                // Queue neighbors with lower priority
                if (startChapter + 1 < chapters.size) triggerPagination(startChapter + 1, PRIORITY_LOW)
                if (startChapter - 1 >= 0) triggerPagination(startChapter - 1, PRIORITY_LOW)

                isLoading = false
                Timber.i("Paginator initialized. UI is ready.")
            }
        }
    }

    // [ADD this new function]
    private fun runEstimator() {
        var runningTotal = 0
        val tempCounts = mutableMapOf<Int, Int>()

        // This loop is extremely fast (math only)
        chapters.forEachIndexed { index, chapter ->
            val estimatedCount = PageCountEstimator.estimateChapterPageCount(
                chapter = chapter,
                constraints = constraints,
                textStyle = textStyle,
                density = density
            )

            chapterPageCounts[index] = estimatedCount
            chapterStartPageIndices[index] = runningTotal

            tempCounts[index] = estimatedCount
            runningTotal += estimatedCount
        }

        totalPageCount = runningTotal
        pageCountsAreAccurate = false
        Timber.i("Estimator finished. Estimated total pages: $totalPageCount")
    }

    private fun getAllTextBlocks(blocks: List<ContentBlock>): List<TextContentBlock> {
        return blocks.flatMap { block ->
            when (block) {
                is WrappingContentBlock -> {
                    Timber.d("PAGINATOR: Found WrappingContentBlock with ${block.paragraphsToWrap.size} paragraphs.")
                    getAllTextBlocks(block.paragraphsToWrap)
                }
                is FlexContainerBlock -> getAllTextBlocks(block.children)
                is TextContentBlock -> listOf(block)
                else -> {
                    Timber.d("PAGINATOR: Skipping non-text block of type ${block::class.simpleName}")
                    emptyList()
                }
            }
        }
    }

    private fun generateConfigurationHash(): Int {
        val configString = "w:${constraints.maxWidth}-h:${constraints.maxHeight}-fs:${textStyle.fontSize.value}-ta:$userTextAlign"
        val hash = configString.hashCode()
        return hash
    }

    private fun applyAccuratePageCounts(countsString: String?) {
        val countsMap = if (!countsString.isNullOrBlank()) {
            try {
                countsString.split(',').filter { it.contains(':') }.associate {
                    val (index, count) = it.split(':')
                    index.toInt() to count.toInt()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to parse chapter page counts string.")
                mapOf()
            }
        } else {
            mapOf()
        }

        if (countsMap.isEmpty()) {
            runEstimator() // Fallback if string was empty
            return
        }

        var runningTotal = 0
        chapters.forEachIndexed { index, chapter ->
            // Use cached count if available, otherwise estimate
            val pageCount = countsMap[index] ?: PageCountEstimator.estimateChapterPageCount(
                chapter, constraints, textStyle, density
            )

            chapterPageCounts[index] = pageCount
            chapterStartPageIndices[index] = runningTotal
            runningTotal += pageCount
        }
        totalPageCount = runningTotal
        pageCountsAreAccurate = countsMap.size == chapters.size
    }

    private suspend fun updateAndSaveConfigurationCache() {
        val countsString = chapterPageCounts.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${it.value}" }

        val newCache = ConfigurationCache(
            bookId = bookId,
            configHash = currentConfigHash,
            chapterPageCounts = countsString
        )
        bookCacheDao.insertConfigurationCache(newCache)

        if (finalizedChapterCounts.size >= chapters.size) {
            pageCountsAreAccurate = true
        }
    }

    suspend fun getTtsChunksForChapter(chapterIndex: Int, startingFromPageInChapter: Int = 0): List<TtsChunk>? {
        val pages = pageCache[chapterIndex] ?: paginateChapter(chapterIndex)
        if (pages.isNullOrEmpty()) {
            Timber.w("PAGINATOR: Chapter $chapterIndex has no pages or could not be paginated.")
            return null
        }

        val blocks = pages.drop(startingFromPageInChapter).flatMap { it.content }

        if (blocks.isEmpty()) {
            Timber.w("PAGINATOR: Chapter $chapterIndex from page $startingFromPageInChapter has no blocks.")
            return null
        }

        val allTtsChunks = mutableListOf<TtsChunk>()

        val textBlocks = getAllTextBlocks(blocks)

        textBlocks.forEach { block ->
            if (block.cfi != null) {
                val blockText = block.content.text
                if (blockText.isNotBlank()) {
                    val textChunksInBlock = splitTextIntoChunks(blockText)

                    var currentOffsetInBlock = 0
                    textChunksInBlock.forEach { chunkText ->
                        val chunk = TtsChunk(
                            text = chunkText,
                            sourceCfi = block.cfi!!,
                            startOffsetInSource = block.startCharOffsetInSource + currentOffsetInBlock
                        )
                        allTtsChunks.add(chunk)
                        currentOffsetInBlock += chunkText.length
                    }
                } else {
                    Timber.d("PAGINATOR: Skipping blank text block. CFI: ${block.cfi}, startOffset: ${block.startCharOffsetInSource}")
                }
            } else {
                Timber.w("PAGINATOR: Skipping text block with null CFI. Text: '${block.content.text.take(70).replace("\n", " ")}...'")
            }
        }
        Timber.i("PAGINATOR: Generated ${allTtsChunks.size} TTS chunks for chapter $chapterIndex starting from page $startingFromPageInChapter.")
        return allTtsChunks
    }

    private fun enqueueBookProcessingWork() {
        val serializableChapters = chapters.map {
            SerializableEpubChapter(it.htmlContent, it.title, it.absPath)
        }

        val input = BookProcessingInput(
            chapters = serializableChapters,
            userAgentStylesheet = userAgentStylesheet,
            bookCss = bookCss,
            baseFontSizeSp = textStyle.fontSize.value,
            density = density.density,
            constraintsMaxWidth = constraints.maxWidth,
            constraintsMaxHeight = constraints.maxHeight,
            fontFaces = this.allFontFaces
        )

        BookProcessingWorker.enqueue(
            context = context,
            bookId = bookId,
            extractionBasePath = extractionBasePath,
            estimatedTotalPages = totalPageCount,
            processingInput = input,
            startChapterIndex = initialChapterToPaginate
        )
    }

    private suspend fun getBlocksForChapter(chapter: EpubChapter, chapterIndex: Int): List<ContentBlock> {
        Timber.d("BookPaginator: getBlocksForChapter($chapterIndex). Using stored textStyle: FontSize=${textStyle.fontSize}, LineHeight=${textStyle.lineHeight}")
        val styler = ContentStyler(
            baseTextStyle = textStyle,
            fontFamilyMap = fontFamilyMap,
            density = density,
            isDarkTheme = isDarkTheme,
            chapterAbsPath = chapter.absPath,
            extractionBasePath = extractionBasePath,
            userTextAlign = userTextAlign
        )

        bookCacheDao.getProcessedChapter(bookId, chapterIndex)?.let { cachedChapter ->
            if (cachedChapter.estimatedPageCount == 0) {
                Timber.d("getBlocksForChapter: Found 'lite' cache for chapter $chapterIndex. Reprocessing for full fidelity.")
            } else {
                Timber.d("getBlocksForChapter: Cache HIT for chapter $chapterIndex in DATABASE.")
                try {
                    val semanticBlocks = proto.decodeFromByteArray<List<SemanticBlock>>(cachedChapter.contentBlocksProto)
                    return styler.style(semanticBlocks)
                } catch (e: Exception) {
                    Timber.e(e, "Failed to deserialize/style chapter $chapterIndex from DB. Reprocessing for this session.")
                }
            }
        }

        Timber.d("getBlocksForChapter: Cache MISS or 'lite' version found for chapter $chapterIndex. Parsing to Semantic IR.")
        val document = Jsoup.parse(chapter.htmlContent, chapter.absPath)
        val mathElements = document.select("math")
        val svgResults = mutableMapOf<String, String>()

        if (mathElements.isNotEmpty()) {
            mathElements.forEachIndexed { i, element ->
                val uniqueId = "math-ch${chapterIndex}-eq${i}"
                val altText = element.attr("alttext").ifBlank { "Equation" }
                val placeholder = Element("math-placeholder").attr("id", uniqueId)

                when (val result = mathMLRenderer.render(element.outerHtml(), altText)) {
                    is RenderResult.Success -> svgResults[uniqueId] = result.svg
                    is RenderResult.Failure -> placeholder.attr("alttext", result.altText)
                }
                element.replaceWith(placeholder)
            }
        }
        val processedHtml = document.outerHtml()

        var parsingCssRules = OptimizedCssRules()
        val uaResult = CssParser.parse(cssContent = userAgentStylesheet, cssPath = null, baseFontSizeSp = textStyle.fontSize.value, density = density.density, constraints = constraints, isDarkTheme = false)
        parsingCssRules = parsingCssRules.merge(uaResult.rules)
        bookCss.forEach { (path, content) ->
            val bookCssResult = CssParser.parse(cssContent = content, cssPath = path, baseFontSizeSp = textStyle.fontSize.value, density = density.density, constraints = constraints, isDarkTheme = false)
            parsingCssRules = parsingCssRules.merge(bookCssResult.rules)
        }

        val semanticBlocks = htmlToSemanticBlocks(
            html = processedHtml,
            cssRules = parsingCssRules,
            textStyle = textStyle.copy(color = Color.Black),
            chapterAbsPath = chapter.absPath,
            extractionBasePath = extractionBasePath,
            density = density,
            fontFamilyMap = fontFamilyMap,
            constraints = constraints,
            mathSvgCache = svgResults
        )

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val protoBytes = proto.encodeToByteArray(semanticBlocks)
                val newCacheEntry = ProcessedChapter(bookId, chapterIndex, protoBytes, chapterPageCounts[chapterIndex] ?: 0)
                bookCacheDao.insertProcessedChapters(listOf(newCacheEntry))
                Timber.i("Successfully cached SEMANTIC content for chapter $chapterIndex.")
            } catch (e: Exception) {
                Timber.e(e, "Failed to serialize and cache on-demand semantic chapter $chapterIndex")
            }
        }

        return styler.style(semanticBlocks)
    }

    private fun startPaginationWorker(): Job = coroutineScope.launch(Dispatchers.IO) {
        Timber.i("Pagination worker started.")
        while (isActive) {
            var request: PaginationRequest? = null
            try {
                request = paginationQueue.take()
                val chapterIndex = request.chapterIndex
                Timber.d("Worker: Took chapter $chapterIndex from queue with priority ${request.priority}.")

                if (pageCache[chapterIndex] != null) {
                    Timber.d("Worker: Chapter $chapterIndex is already in cache, skipping.")
                    continue
                }

                if (!chaptersBeingProcessed.add(chapterIndex)) {
                    Timber.w("Worker: Chapter $chapterIndex is already being processed, skipping.")
                    continue
                }

                Timber.i("Worker: Starting pagination for chapter $chapterIndex.")
                val pages = paginateChapter(chapterIndex)

                if (pages != null) {
                    Timber.i("Worker: Successfully finished pagination for chapter $chapterIndex.")
                    navigationCallbacks.remove(chapterIndex)?.forEach { callback ->
                        Timber.d("Worker: Fulfilling navigation callback for chapter $chapterIndex.")
                        callback(pages)
                    }
                } else {
                    Timber.e("Worker: Pagination for chapter $chapterIndex resulted in null.")
                }
            } catch (_: InterruptedException) {
                Timber.i("Pagination worker interrupted. Shutting down.")
                Thread.currentThread().interrupt()
                break
            } catch (e: Exception) {
                Timber.e(e, "Error in pagination worker for chapter ${request?.chapterIndex}")
            } finally {
                request?.let {
                    chaptersBeingProcessed.remove(it.chapterIndex)
                    Timber.d("Worker: Chapter ${it.chapterIndex} removed from processing set.")
                }
                yield()
            }
        }
        Timber.i("Pagination worker stopped.")
    }

    override fun onUserScrolledTo(pageIndex: Int) {
        findChapterIndexForPage(pageIndex)?.let {
            if (it != currentUserChapterIndex.value) {
                Timber.d("User location updated to chapter $it")
                currentUserChapterIndex.value = it
            }
        }
    }

    private fun updatePageCounts(chapterIndex: Int, actualPageCount: Int) {
        val estimatedPageCount = chapterPageCounts[chapterIndex] ?: actualPageCount
        val difference = actualPageCount - estimatedPageCount

        if (difference == 0) {
            if (!pageCountsAreAccurate && finalizedChapterCounts.add(chapterIndex)) {
                coroutineScope.launch(Dispatchers.IO) { updateAndSaveConfigurationCache() }
            }
            return
        }

        // Update the specific chapter
        chapterPageCounts[chapterIndex] = actualPageCount

        synchronized(this) {
            totalPageCount += difference

            // Shift all SUBSEQUENT chapters
            for (i in (chapterIndex + 1) until chapters.size) {
                chapterStartPageIndices[i] = (chapterStartPageIndices[i] ?: 0) + difference
            }

            if (chapterIndex < currentUserChapterIndex.value) {
                pageShiftRequest.tryEmit(difference)
            }
        }

        if (!pageCountsAreAccurate) {
            if (finalizedChapterCounts.add(chapterIndex)) {
                coroutineScope.launch(Dispatchers.IO) {
                    updateAndSaveConfigurationCache()
                }
            }
        }

        Timber.i("Correction: Ch $chapterIndex | Est: $estimatedPageCount -> Act: $actualPageCount | Diff: $difference | Total: $totalPageCount")
    }

    override fun getActiveAnchorForPage(pageIndex: Int, tocAnchors: List<String>): String? {
        val chapterIndex = findChapterIndexForPage(pageIndex) ?: return null
        val chapterPages = pageCache[chapterIndex] ?: return null
        val chapterStart = chapterStartPageIndices[chapterIndex] ?: 0
        val currentPageInChapter = pageIndex - chapterStart

        var lastFoundAnchor: String? = null
        val anchorSet = tocAnchors.toSet()

        for (pIdx in 0..currentPageInChapter) {
            val page = chapterPages.getOrNull(pIdx) ?: continue
            for (block in page.content) {
                val idsInBlock = findAllIds(block)
                for (id in idsInBlock) {
                    if (anchorSet.contains(id)) {
                        lastFoundAnchor = id
                    }
                }
            }
        }
        return lastFoundAnchor
    }

    override fun getPageContent(pageIndex: Int): Page? {
        Timber.v("getPageContent requested for pageIndex $pageIndex")
        val chapterIndex = findChapterIndexForPage(pageIndex)
        if (chapterIndex == null) {
            Timber.w("getPageContent: Could not find chapter for pageIndex $pageIndex.")
            return null
        }

        val chapterPages = pageCache[chapterIndex]
        if (chapterPages != null) {
            val pageInChapterIndex = pageIndex - (chapterStartPageIndices[chapterIndex] ?: 0)
            Timber.v("getPageContent: Cache HIT for page $pageIndex (Chapter $chapterIndex, PageInChapter $pageInChapterIndex).")
            prefetchChapters(chapterIndex)
            return chapterPages.getOrNull(pageInChapterIndex)
        } else {
            Timber.i("getPageContent: Cache MISS for page $pageIndex (Chapter $chapterIndex).")
            triggerPagination(chapterIndex, PRIORITY_HIGH)
            prefetchChapters(chapterIndex)
            return null
        }
    }

    override fun findChapterIndexForPage(pageIndex: Int): Int? {
        if (pageIndex !in 0..<totalPageCount) {
            Timber.w("findChapterIndexForPage: pageIndex $pageIndex is out of bounds (Total: $totalPageCount)."
            )
            return null
        }
        val entry = chapterStartPageIndices.entries
            .filter { it.value <= pageIndex }
            .maxWithOrNull(compareBy({ it.value }, { it.key }))

        if (entry == null) {
            Timber.e("findChapterIndexForPage: Could not find a start page index for page $pageIndex. This is a critical error."
            )
        }
        return entry?.key
    }

    override fun getCfiForPage(pageIndex: Int): String? {
        val chapterIndex = findChapterIndexForPage(pageIndex) ?: return null
        val chapterPages = pageCache[chapterIndex]
        if (chapterPages == null) {
            Timber.w("getCfiForPage: Chapter $chapterIndex not in cache for page $pageIndex.")
            return null
        }
        val pageInChapterIndex = pageIndex - (chapterStartPageIndices[chapterIndex] ?: 0)
        val pageContent = chapterPages.getOrNull(pageInChapterIndex)?.content ?: return null

        val firstTextBlock = pageContent.firstOrNull { it is TextContentBlock } as? TextContentBlock
        val firstAnyBlock = pageContent.firstOrNull()

        if (firstTextBlock != null) {
            val baseCfi = firstTextBlock.cfi ?: return null
            val offset = firstTextBlock.startCharOffsetInSource
            return if (offset > 0) "$baseCfi:$offset" else baseCfi
        } else {
            return firstAnyBlock?.cfi
        }
    }

    private suspend fun paginateChapter(chapterIndex: Int): List<Page>? {
        pageCache[chapterIndex]?.let {
            Timber.d("paginateChapter: L1 Cache HIT for chapter $chapterIndex in MEMORY, returning cached pages.")
            return it
        }

        val chapter = chapters.getOrNull(chapterIndex) ?: run {
            Timber.e("paginateChapter: Chapter $chapterIndex not found in chapter list.")
            return null
        }

        val blocks = blockCache[chapterIndex] ?: run {
            Timber.d("paginateChapter: L2 Cache MISS for chapter $chapterIndex. Loading from DB.")
            val blocksFromDb = getBlocksForChapter(chapter, chapterIndex)
            blockCache.put(chapterIndex, blocksFromDb) // Store in L2 cache
            blocksFromDb
        }

        Timber.d("paginateChapter: Chapter $chapterIndex retrieved/parsed into ${blocks.size} content blocks.")

        val measurementProvider = SuspendingAndroidBlockMeasurementProvider(
            textMeasurer = textMeasurer,
            constraints = constraints,
            textStyle = textStyle,
            density = density
        )
        Timber.d("paginateChapter: Calling PaginatorLogic for chapter $chapterIndex.")
        val pages = paginate(
            blocks = blocks,
            pageHeight = constraints.maxHeight,
            measurementProvider = measurementProvider,
            density = density
        )
        Timber.d("paginateChapter: PaginatorLogic returned ${pages.size} pages for chapter $chapterIndex.")

        pageCache.put(chapterIndex, pages)
        Timber.d("paginateChapter: Chapter $chapterIndex pages stored in L1 pageCache.")

        pageCache.put(chapterIndex, pages)
        Timber.d("paginateChapter: Chapter $chapterIndex pages stored in L1 pageCache.")

        val characterIndex = mutableListOf<PageCharacterRange>()
        pages.forEachIndexed { pageInChapterIndex, page ->
            var totalCharsOnPage = 0L
            val allTextBlocksOnPage = getAllTextBlocks(page.content)
            allTextBlocksOnPage.forEach { block ->
                if (block.cfi != null && block.startCharOffsetInSource >= 0 && block.content.isNotEmpty()) {
                    val startOffset = block.startCharOffsetInSource
                    val endOffset = startOffset + block.content.text.length
                    totalCharsOnPage += block.content.text.length

                    characterIndex.add(
                        PageCharacterRange(
                            pageInChapter = pageInChapterIndex,
                            cfi = block.cfi!!,
                            startOffset = startOffset,
                            endOffset = endOffset
                        )
                    )
                }
            }
        }
        chapterCharacterIndex[chapterIndex] = characterIndex

        val cumulativeCharsPerPage = mutableListOf<Long>()
        var runningTotalChars = 0L
        pages.forEachIndexed { _, page ->
            val charsOnPage = getAllTextBlocks(page.content).sumOf { it.content.text.length.toLong() }
            runningTotalChars += charsOnPage
            cumulativeCharsPerPage.add(runningTotalChars)
        }
        chapterCumulativeChars[chapterIndex] = cumulativeCharsPerPage

        withContext(Dispatchers.Main) {
            if (chapterPageCounts[chapterIndex] != pages.size) {
                updatePageCounts(chapterIndex, pages.size)
            }
            generation++
        }
        return pages
    }

    private fun triggerPagination(chapterIndex: Int, priority: Int) {
        if (pageCache[chapterIndex] != null) {
            Timber.v("Trigger: Chapter $chapterIndex is already in cache. Ignoring.")
            return
        }
        if (chaptersBeingProcessed.contains(chapterIndex)) {
            Timber.v("Trigger: Chapter $chapterIndex is already being processed. Ignoring.")
            return
        }

        synchronized(paginationQueue) {
            val existingRequest = paginationQueue.find { it.chapterIndex == chapterIndex }

            if (existingRequest != null) {
                if (priority < existingRequest.priority) { // Lower number = higher priority
                    Timber.d("Upgrading priority for queued chapter $chapterIndex from ${existingRequest.priority} to $priority")
                    paginationQueue.remove(existingRequest)
                    paginationQueue.offer(PaginationRequest(chapterIndex, priority))
                } else {
                    Timber.v("Trigger: Chapter $chapterIndex already in queue with same or higher priority. Ignoring.")
                }
            } else {
                Timber.d("Queued chapter $chapterIndex for pagination with priority $priority.")
                paginationQueue.offer(PaginationRequest(chapterIndex, priority))
            }
        }
    }

    private fun prefetchChapters(currentChapterIndex: Int) {
        Timber.v("Prefetching chapters around index $currentChapterIndex.")
        val nextChapterIndex = currentChapterIndex + 1
        if (nextChapterIndex < chapters.size) {
            triggerPagination(nextChapterIndex, PRIORITY_MEDIUM)
        }

        val prevChapterIndex = currentChapterIndex - 1
        if (prevChapterIndex >= 0) {
            triggerPagination(prevChapterIndex, PRIORITY_MEDIUM)
        }
    }

    override fun getChapterPathForPage(pageIndex: Int): String? {
        val chapterIndex = findChapterIndexForPage(pageIndex)
        return chapters.getOrNull(chapterIndex ?: -1)?.absPath
    }

    override fun getPlainTextForChapter(chapterIndex: Int): String? {
        val chapter = chapters.getOrNull(chapterIndex) ?: return null
        return Jsoup.parse(chapter.htmlContent).body().text()
    }

    private fun calculateAccurateStartIndex(targetChapterIndex: Int): Int {
        if (targetChapterIndex <= 0) {
            return 0
        }
        val startIndex = chapterStartPageIndices[targetChapterIndex] ?: 0
        return startIndex
    }

    override fun findPageForAnchor(
        chapterIndex: Int,
        anchor: String?,
        onResult: (pageIndex: Int) -> Unit
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            Timber.tag("TOC_NAV_DEBUG").d("Precision nav request for anchor: '$anchor'")

            if (anchor.isNullOrBlank()) {
                val start = chapterStartPageIndices[chapterIndex] ?: 0
                withContext(Dispatchers.Main) { onResult(start) }
                return@launch
            }

            // 1. QUICK LOOKUP: Check the Anchor Index first
            val indexEntry = bookCacheDao.getAnchorIndex(bookId, anchor)

            val (targetChapter, targetBlock) = if (indexEntry != null) {
                Timber.tag("TOC_NAV_DEBUG").i("Index HIT: Anchor '$anchor' is in Chapter ${indexEntry.chapterIndex}, Block ${indexEntry.blockIndex}")
                indexEntry.chapterIndex to indexEntry.blockIndex
            } else {
                Timber.tag("TOC_NAV_DEBUG").w("Index MISS: Falling back to linear scan for '$anchor' in Chapter $chapterIndex")
                chapterIndex to null
            }

            // 2. ENSURE PAGINATION: Get pages for the determined chapter
            val chapterPages = pageCache[targetChapter] ?: paginateChapter(targetChapter)
            val chapterStartPage = chapterStartPageIndices[targetChapter] ?: 0

            if (chapterPages == null) {
                withContext(Dispatchers.Main) { onResult(chapterStartPage) }
                return@launch
            }

            // 3. FIND PAGE
            var targetPageInChapter = 0
            var found = false

            for ((pageIndex, page) in chapterPages.withIndex()) {
                val isMatch = if (targetBlock != null) {
                    // Fast path: We know exactly which block we are looking for
                    page.content.any { it.blockIndex == targetBlock }
                } else {
                    // Slow path: Linear ID scan (fallback)
                    page.content.any { containsAnchor(it, anchor) }
                }

                if (isMatch) {
                    targetPageInChapter = pageIndex
                    found = true
                    break
                }
            }

            val finalPage = chapterStartPage + targetPageInChapter
            Timber.tag("TOC_NAV_DEBUG").d("Navigation resolved to Absolute Page: $finalPage (Found: $found)")
            withContext(Dispatchers.Main) { onResult(finalPage) }
        }
    }

    private fun containsAnchor(block: ContentBlock, anchor: String): Boolean {
        if (block.elementId == anchor) return true

        if (block is TextContentBlock) {
            val idAnnotations = block.content.getStringAnnotations("ID", 0, block.content.length)
            if (idAnnotations.any { it.item == anchor }) return true
        }

        return when (block) {
            is FlexContainerBlock -> block.children.any { containsAnchor(it, anchor) }
            is TableBlock -> block.rows.flatten().any { cell ->
                cell.content.any { containsAnchor(it, anchor) }
            }
            is WrappingContentBlock -> {
                containsAnchor(block.floatedImage, anchor) ||
                        block.paragraphsToWrap.any { containsAnchor(it, anchor) }
            }
            else -> false
        }
    }

    private fun findAllIds(block: ContentBlock): List<String> {
        val ids = mutableListOf<String>()
        block.elementId?.let { ids.add(it) }

        if (block is TextContentBlock) {
            val idAnnotations = block.content.getStringAnnotations("ID", 0, block.content.length)
            ids.addAll(idAnnotations.map { it.item })
        }

        when (block) {
            is FlexContainerBlock -> ids.addAll(block.children.flatMap { findAllIds(it) })
            is TableBlock -> ids.addAll(block.rows.flatten().flatMap { cell -> cell.content.flatMap { findAllIds(it) } })
            is WrappingContentBlock -> {
                ids.addAll(findAllIds(block.floatedImage))
                ids.addAll(block.paragraphsToWrap.flatMap { findAllIds(it) })
            }
            else -> {}
        }
        return ids
    }

    override fun navigateToHref(
        currentChapterAbsPath: String,
        href: String,
        onNavigationComplete: (pageIndex: Int) -> Unit
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            Timber.i("Navigating to href: '$href' from chapter: '$currentChapterAbsPath'")

            val (targetChapterPath, anchor) = resolveHref(currentChapterAbsPath, href)
            if (targetChapterPath == null) {
                Timber.w("Could not resolve href '$href' to a valid chapter path.")
                return@launch
            }

            val targetChapterIndex = chapters.indexOfFirst { it.absPath == targetChapterPath }
            if (targetChapterIndex == -1) {
                Timber.w("Could not find chapter for path: $targetChapterPath")
                return@launch
            }

            val chapterStartPage = calculateAccurateStartIndex(targetChapterIndex)
            val chapterPages = pageCache[targetChapterIndex] ?: paginateChapter(targetChapterIndex)

            if (chapterPages == null) {
                Timber.e("Href Navigation failed: Could not paginate target chapter $targetChapterIndex.")
                return@launch
            }

            var targetPageInChapter = 0
            if (anchor != null) {
                Timber.d("Searching for anchor '$anchor' in chapter $targetChapterIndex.")
                pageLoop@ for ((pageIndex, page) in chapterPages.withIndex()) {
                    for (block in page.content) {
                        if (block.elementId == anchor) {
                            targetPageInChapter = pageIndex
                            Timber.i("Found anchor '$anchor' on page $pageIndex in chapter $targetChapterIndex")
                            break@pageLoop
                        }
                    }
                }
            }

            val finalPageIndex = chapterStartPage + targetPageInChapter
            Timber.i("Href navigation complete. Final page index: $finalPageIndex")
            withContext(Dispatchers.Main) { onNavigationComplete(finalPageIndex) }
        }
    }

    override fun findPageForSearchResult(result: SearchResult, onResult: (pageIndex: Int) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val targetChapterIndex = result.locationInSource
            Timber.i("Finding page for search result: '${result.query}' in chapter $targetChapterIndex")

            val chapterStartPage = calculateAccurateStartIndex(targetChapterIndex)
            val chapterPages = pageCache[targetChapterIndex] ?: paginateChapter(targetChapterIndex)

            if (chapterPages == null) {
                Timber.e("Search result navigation failed: Could not paginate target chapter $targetChapterIndex.")
                return@launch
            }

            var targetPageInChapter = 0
            var occurrenceCount = 0

            pageLoop@ for ((pageIndex, page) in chapterPages.withIndex()) {
                for (block in page.content) {
                    val textToSearch = when (block) {
                        is ParagraphBlock -> block.content.text
                        is HeaderBlock -> block.content.text
                        is QuoteBlock -> block.content.text
                        is ListItemBlock -> block.content.text
                        else -> null
                    }

                    if (textToSearch != null) {
                        var lastIndex = -1
                        while (true) {
                            lastIndex = textToSearch.indexOf(result.query, startIndex = lastIndex + 1, ignoreCase = true)
                            if (lastIndex == -1) break

                            if (occurrenceCount == result.occurrenceIndexInLocation) {
                                targetPageInChapter = pageIndex
                                Timber.i("Found search result '${result.query}' at occurrence ${result.occurrenceIndexInLocation} on page $pageIndex of chapter $targetChapterIndex")
                                break@pageLoop
                            }
                            occurrenceCount++
                        }
                    }
                }
            }
            val finalPageIndex = chapterStartPage + targetPageInChapter
            Timber.i("Search result found. Final page index: $finalPageIndex")
            withContext(Dispatchers.Main) { onResult(finalPageIndex) }
        }
    }

    override fun findPageForCfiAndOffset(chapterIndex: Int, cfi: String, charOffset: Int): Int? {
        val index = chapterCharacterIndex[chapterIndex]
        if (index.isNullOrEmpty()) {
            return null
        }

        val foundRange = index.find { range ->
            val cfiMatches = cfi.startsWith(range.cfi)
            val offsetMatches = charOffset >= range.startOffset && charOffset < range.endOffset
            cfiMatches && offsetMatches
        }

        return if (foundRange != null) {
            val chapterStartPage = chapterStartPageIndices[chapterIndex]
            if (chapterStartPage == null) {
                null
            } else {
                val absolutePage = chapterStartPage + foundRange.pageInChapter
                absolutePage
            }
        } else {
            null
        }
    }

    suspend fun findPageForLocator(locator: Locator): Int? {
        val targetChapterIndex = locator.chapterIndex
        Timber.i("Finding page for locator: Chapter $targetChapterIndex, Block ${locator.blockIndex}, Offset ${locator.charOffset}")

        val chapterStartPage = chapterStartPageIndices[targetChapterIndex] ?: 0
        val chapterPages = pageCache[targetChapterIndex] ?: paginateChapter(targetChapterIndex)

        if (chapterPages.isNullOrEmpty()) {
            Timber.e("Locator navigation failed: Could not paginate target chapter $targetChapterIndex.")
            return null
        }

        var fallbackPageInChapter = -1

        for ((pageIndex, page) in chapterPages.withIndex()) {
            for (block in page.content) {
                if (block.blockIndex == locator.blockIndex) {
                    if (fallbackPageInChapter == -1) {
                        fallbackPageInChapter = pageIndex
                    }

                    val textBlock = block as? TextContentBlock
                    if (textBlock != null) {
                        val startOffsetOnPage = textBlock.startCharOffsetInSource
                        val endOffsetOnPage = startOffsetOnPage + textBlock.content.length

                        if (locator.charOffset in startOffsetOnPage..<endOffsetOnPage) {
                            val finalPageIndex = chapterStartPage + pageIndex
                            Timber.i("Locator navigation SUCCEEDED with offset. Final page index: $finalPageIndex")
                            return finalPageIndex
                        }
                    } else {
                        val finalPageIndex = chapterStartPage + pageIndex
                        Timber.i("Locator navigation SUCCEEDED for non-text block. Final page index: $finalPageIndex")
                        return finalPageIndex
                    }
                }
            }
        }

        if (fallbackPageInChapter != -1) {
            val finalPageIndex = chapterStartPage + fallbackPageInChapter
            Timber.w("Locator offset not found. Using FALLBACK page. Final page index: $finalPageIndex")
            return finalPageIndex
        }

        Timber.e("Locator navigation FAILED. Block ${locator.blockIndex} not found in chapter $targetChapterIndex.")
        return null
    }

    fun getLocatorForPage(pageIndex: Int): Locator? {
        val chapterIndex = findChapterIndexForPage(pageIndex) ?: return null
        val pageContent = getPageContent(pageIndex) ?: return null

        val firstTextBlock = pageContent.content.firstOrNull { it is TextContentBlock } as? TextContentBlock
        val targetBlock = firstTextBlock ?: pageContent.content.firstOrNull() ?: return null
        val charOffset = (targetBlock as? TextContentBlock)?.startCharOffsetInSource ?: 0

        return Locator(
            chapterIndex = chapterIndex,
            blockIndex = targetBlock.blockIndex,
            charOffset = charOffset
        )
    }

    override fun findPageForCfi(chapterIndex: Int, cfi: String, onResult: (pageIndex: Int) -> Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            Timber.i("findPageForCfi: Starting search for CFI: '$cfi' in chapter: '$chapterIndex'")

            val chapterStartPage = calculateAccurateStartIndex(chapterIndex)
            Timber.d("findPageForCfi: Chapter $chapterIndex starts at absolute page $chapterStartPage.")
            val chapterPages = pageCache[chapterIndex] ?: paginateChapter(chapterIndex)

            if (chapterPages == null) {
                Timber.e("CFI Navigation failed: Could not paginate target chapter $chapterIndex.")
                withContext(Dispatchers.Main) { onResult(chapterStartPage) }
                return@launch
            }
            Timber.d("findPageForCfi: Chapter $chapterIndex has ${chapterPages.size} pages.")

            val (basePath, offset) = cfi.split(':').let {
                it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 0)
            }

            val sanitizedBasePath = if (basePath.startsWith("/2/")) {
                "/" + basePath.substring(3)
            } else {
                basePath
            }

            Timber.d("findPageForCfi: Parsed CFI into sanitizedBasePath='$sanitizedBasePath' (from '$basePath') and offset=$offset.")

            var targetPageInChapter = 0
            var foundMatch = false

            pageLoop@ for ((pageIndex, page) in chapterPages.withIndex()) {
                for (block in page.content) {
                    val blockCfi = block.cfi ?: continue

                    if (blockCfi.endsWith(sanitizedBasePath)) {
                        val index = blockCfi.lastIndexOf(sanitizedBasePath)
                        val isGenuineSuffix = (index == 0) || (index > 0 && blockCfi[index - 1] == '/')
                        if (!isGenuineSuffix) continue

                        Timber.d("findPageForCfi: Potential CFI match on page $pageIndex. Input base: '$basePath', Block CFI: '$blockCfi'.")

                        val blockMatches = when (block) {
                            is TextContentBlock -> {
                                val start = block.startCharOffsetInSource
                                val end = block.endCharOffsetInSource
                                val contentLength = block.content.length
                                val finalEnd = if (end >= 0) end else (start + contentLength)
                                val match = offset in start..<finalEnd
                                Timber.d("findPageForCfi: TextBlock check: offset ($offset) in range [$start, $finalEnd)? -> $match")
                                match
                            }
                            else -> {
                                val match = offset == 0
                                Timber.d("findPageForCfi: Non-TextBlock check: offset is 0? -> $match")
                                match
                            }
                        }

                        if (blockMatches) {
                            targetPageInChapter = pageIndex
                            foundMatch = true
                            Timber.i("findPageForCfi: SUCCESS. Found precise match on page $pageIndex of chapter.")
                            break@pageLoop
                        }
                    }
                }
            }

            if (!foundMatch) {
                Timber.w("Could not find a precise character-offset match for CFI '$cfi'. Falling back to best-effort suffix match.")
                var bestMatchLength = -1
                chapterPages.forEachIndexed { pageIndex, page ->
                    for (block in page.content) {
                        val blockCfi = block.cfi ?: continue
                        if (blockCfi.endsWith(sanitizedBasePath)) {
                            val index = blockCfi.lastIndexOf(sanitizedBasePath)
                            val isGenuineSuffix = (index == 0) || (index > 0 && blockCfi[index - 1] == '/')
                            if (isGenuineSuffix && blockCfi.length > bestMatchLength) {
                                targetPageInChapter = pageIndex
                                bestMatchLength = blockCfi.length
                                Timber.d("findPageForCfi: Fallback found better prefix match on page $pageIndex. New best match length: $bestMatchLength (Block CFI: '$blockCfi')")
                            }
                        }
                    }
                }
            }

            val finalPageIndex = chapterStartPage + targetPageInChapter
            Timber.i("findPageForCfi: Search complete. finalPageIndex: $finalPageIndex (start: $chapterStartPage + pageInChapter: $targetPageInChapter)")
            withContext(Dispatchers.Main) { onResult(finalPageIndex) }
        }
    }

    private fun resolveHref(currentChapterPath: String, href: String): Pair<String?, String?> {
        try {
            val decodedHref = URLDecoder.decode(href, "UTF-8")
            val (pathPart, anchorPart) = decodedHref.split('#', limit = 2).let {
                if (it.size > 1) it[0] to it[1] else it[0] to null
            }

            if (pathPart.isBlank()) {
                Timber.d("Href path is blank, resolving to current chapter: $currentChapterPath")
                return currentChapterPath to anchorPart
            }

            val targetPath = URI(currentChapterPath).resolve(pathPart).normalize().path
            Timber.d("Resolved href '$href' to target path '$targetPath'")

            val targetChapter = chapters.find {
                try {
                    URI(it.absPath).normalize().path == targetPath
                } catch (_: Exception) {
                    false
                }
            }

            if (targetChapter == null) {
                Timber.w("Could not find a matching chapter for path: $targetPath")
            }

            return targetChapter?.absPath to anchorPart
        } catch (e: Exception) {
            Timber.e(e, "Failed to resolve href: $href")
            return null to null
        }
    }
}