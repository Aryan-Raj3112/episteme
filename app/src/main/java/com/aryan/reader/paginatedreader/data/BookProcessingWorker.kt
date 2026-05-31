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
package com.aryan.reader.paginatedreader.data

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import timber.log.Timber
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aryan.reader.applyBookReplacementsToHtmlDocument
import com.aryan.reader.epub.epubContentFilePath
import com.aryan.reader.paginatedreader.CssParser
import com.aryan.reader.paginatedreader.AndroidHtmlResourceResolver
import com.aryan.reader.paginatedreader.FontFaceInfo
import com.aryan.reader.paginatedreader.MathMLRenderer
import com.aryan.reader.paginatedreader.OptimizedCssRules
import com.aryan.reader.paginatedreader.RenderResult
import com.aryan.reader.paginatedreader.androidHtmlToSemanticBlocks
import com.aryan.reader.paginatedreader.loadFontFamilies
import com.aryan.reader.paginatedreader.semanticBlockModule
import com.aryan.reader.shared.ReaderBookReplacementPreferencesJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import kotlin.math.abs
import kotlin.coroutines.coroutineContext

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class SerializableEpubChapter(
    @ProtoNumber(1) val htmlContent: String,
    @ProtoNumber(2) val title: String,
    @ProtoNumber(3) val absPath: String,
    @ProtoNumber(4) val htmlFilePath: String = absPath
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BookProcessingInput(
    @ProtoNumber(1) val chapters: List<SerializableEpubChapter>,
    @ProtoNumber(2) val userAgentStylesheet: String,
    @ProtoNumber(3) val bookCss: Map<String, String>,
    @ProtoNumber(4) val baseFontSizeSp: Float,
    @ProtoNumber(5) val density: Float,
    @ProtoNumber(6) val constraintsMaxWidth: Int,
    @ProtoNumber(7) val constraintsMaxHeight: Int,
    @ProtoNumber(8) val fontFaces: List<FontFaceInfo> = emptyList(),
    @ProtoNumber(9) val styleConfigHash: Int = 0,
    @ProtoNumber(10) val bookReplacementPreferencesJson: String = "",
    @ProtoNumber(11) val bookReplacementFileId: String = ""
)

@OptIn(ExperimentalSerializationApi::class)
class BookProcessingWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "book-processing"
        private const val KEY_BOOK_ID = "bookId"
        private const val KEY_EXTRACTION_BASE_PATH = "extractionBasePath"
        private const val KEY_INPUT_FILE_PATH = "inputFilePath"
        private const val KEY_ESTIMATED_TOTAL_PAGES = "estimatedTotalPages"
        private const val KEY_START_CHAPTER_INDEX = "startChapterIndex"

        private fun uniqueWorkName(bookId: String): String = "process_$bookId"

        fun cancelForBook(context: Context, bookId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(bookId))
            Timber.i("Cancelled stale background processing for book: $bookId")
        }

        fun enqueue(
            context: Context,
            bookId: String,
            extractionBasePath: String,
            estimatedTotalPages: Int,
            processingInput: BookProcessingInput,
            startChapterIndex: Int
        ) {
            val tempFile = File.createTempFile("proc_input_", ".proto", context.cacheDir)
            val proto = ProtoBuf { serializersModule = semanticBlockModule }
            tempFile.writeBytes(proto.encodeToByteArray(processingInput))

            val workData = Data.Builder()
                .putString(KEY_BOOK_ID, bookId)
                .putString(KEY_EXTRACTION_BASE_PATH, extractionBasePath)
                .putInt(KEY_ESTIMATED_TOTAL_PAGES, estimatedTotalPages)
                .putString(KEY_INPUT_FILE_PATH, tempFile.absolutePath)
                .putInt(KEY_START_CHAPTER_INDEX, startChapterIndex)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<BookProcessingWorker>()
                .setInputData(workData)
                .addTag(WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                uniqueWorkName(bookId),
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
            Timber.i("Enqueued latest background processing for book: $bookId config=${processingInput.styleConfigHash}")
        }
    }

    private fun precalculateImageDimensions(
        chapters: List<SerializableEpubChapter>,
        extractionBasePath: String
    ): Map<String, Pair<Float, Float>> {
        val dimensionsCache = mutableMapOf<String, Pair<Float, Float>>()
        Timber.i("Starting pre-scan to calculate image dimensions...")
        for (chapter in chapters) {
            val document = Jsoup.parse(chapter.htmlContent)

            // Find all image tags (both <img> and <svg><image>)
            document.select("img, image").forEach { element ->
                val srcAttr = if (element.tagName() == "img") "src" else "href"
                val src = element.attr(srcAttr).ifBlank { element.attr("xlink:href") }

                if (src.isNotBlank()) {
                    val imagePath = AndroidHtmlResourceResolver
                        .resolvePath(chapter.absPath, extractionBasePath, src)
                        ?: return@forEach
                    val imageFile = File(imagePath)

                    // If not already cached, read dimensions from disk
                    if (imageFile.exists() && !dimensionsCache.containsKey(imagePath)) {
                        try {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(imagePath, options)
                            if (options.outWidth > 0 && options.outHeight > 0) {
                                dimensionsCache[imagePath] = Pair(options.outWidth.toFloat(), options.outHeight.toFloat())
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Could not decode image bounds during pre-scan for $imagePath")
                        }
                    }
                }
            }
        }
        Timber.i("Pre-calculated dimensions for ${dimensionsCache.size} unique images.")
        return dimensionsCache
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val bookId = inputData.getString(KEY_BOOK_ID) ?: return@withContext Result.failure()
        val extractionBasePath = inputData.getString(KEY_EXTRACTION_BASE_PATH) ?: return@withContext Result.failure()
        val estimatedTotalPages = inputData.getInt(KEY_ESTIMATED_TOTAL_PAGES, 0)
        val inputFilePath = inputData.getString(KEY_INPUT_FILE_PATH) ?: return@withContext Result.failure()
        val startChapterIndex = inputData.getInt(KEY_START_CHAPTER_INDEX, 0)

        val inputFile = File(inputFilePath)
        if (!inputFile.exists()) return@withContext Result.failure()

        val proto = ProtoBuf { serializersModule = semanticBlockModule }
        val db = BookCacheDatabase.getDatabase(appContext)
        val mathMLRenderer = MathMLRenderer(appContext)

        try {
            Timber.i("Worker starting for book: $bookId")

            if (!mathMLRenderer.awaitReady()) {
                Timber.e("MathMLRenderer failed to initialize. Aborting processing for this book.")
                return@withContext Result.failure()
            }
            val input = proto.decodeFromByteArray<BookProcessingInput>(inputFile.readBytes())
            val bookReplacementPreferences = ReaderBookReplacementPreferencesJson.decodeOrEmpty(
                input.bookReplacementPreferencesJson,
            )
            Timber.i("Worker decoded input. Number of chapters received: ${input.chapters.size}")

            // Worker now reconstructs everything it needs for a pure light-theme processing run.
            val density = Density(input.density)
            val constraints = Constraints(maxWidth = input.constraintsMaxWidth, maxHeight = input.constraintsMaxHeight)
            val textStyle = TextStyle(color = Color.Black, fontSize = input.baseFontSizeSp.sp) // Hardcode light theme values

            var lightThemeCssRules = OptimizedCssRules()
            val uaResult = CssParser.parse(
                cssContent = input.userAgentStylesheet,
                cssPath = null,
                baseFontSizeSp = textStyle.fontSize.value,
                density = density.density,
                constraints = constraints,
                isDarkTheme = false,
                adaptThemeColors = false
            )
            lightThemeCssRules = lightThemeCssRules.merge(uaResult.rules)

            input.bookCss.forEach { (path, content) ->
                val bookCssResult = CssParser.parse(
                    cssContent = content,
                    cssPath = path,
                    baseFontSizeSp = textStyle.fontSize.value,
                    density = density.density,
                    constraints = constraints,
                    isDarkTheme = false,
                    adaptThemeColors = false
                )
                lightThemeCssRules = lightThemeCssRules.merge(bookCssResult.rules)
            }

            val imageDimensionsCache = precalculateImageDimensions(input.chapters, extractionBasePath)
            val fontFamilyMap = loadFontFamilies(input.fontFaces, extractionBasePath)

            val numCores = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
            val chaptersToProcess = input.chapters.withIndex().toList()
                .sortedBy { (index, _) -> abs(index - startChapterIndex) }

            Timber.d("Preparing to process ${chaptersToProcess.size} chapters.")

            Timber.i("Worker processing with up to $numCores threads, prioritizing around chapter $startChapterIndex.")

            chaptersToProcess.chunked(numCores).forEach { chunk ->
                coroutineContext.ensureActive()
                Timber.d("Processing a chunk of ${chunk.size} chapters.")
                val deferreds = chunk.map { (index, chapter) ->
                    async {
                        coroutineContext.ensureActive()
                        Timber.d("Async task started for chapter index $index.")
                        if (db.bookCacheDao().getProcessedChapter(bookId, index, input.styleConfigHash) == null) {
                            Timber.d("[BG_PROC] Caching chapter $index: ${chapter.title}")
                            val htmlToParse = chapter.htmlContent.ifBlank {
                                val backingFile = File(extractionBasePath, epubContentFilePath(chapter.htmlFilePath))
                                if (backingFile.exists()) {
                                    backingFile.readText()
                                } else {
                                    ""
                                }
                            }
                            if (htmlToParse.isBlank()) {
                                Timber.w("[BG_PROC] Skipping chapter $index because no HTML content was available.")
                                return@async null
                            }

                            val document = Jsoup.parse(htmlToParse, chapter.absPath)
                            val mathElements = document.select("math")
                            val svgResults = mutableMapOf<String, String>()

                            if (mathElements.isNotEmpty()) {
                                Timber.d("Chapter $index (Background Worker): Found ${mathElements.size} MathML elements to process.")
                                mathElements.forEachIndexed { i, element ->
                                    val uniqueId = "math-ch${index}-eq${i}"
                                    val altText = element.attr("alttext").ifBlank { "Equation" }
                                    val placeholder = Element("math-placeholder").attr("id", uniqueId)

                                    when (val result = mathMLRenderer.render(element.outerHtml(), altText)) {
                                        is RenderResult.Success -> {
                                            Timber.d("Chapter $index (Background Worker): Render SUCCESS for $uniqueId")
                                            val svgDoc = Jsoup.parse(result.svg)
                                            val svgElement = svgDoc.selectFirst("svg")
                                            val width = svgElement?.attr("width") ?: "N/A"
                                            val height = svgElement?.attr("height") ?: "N/A"
                                            val viewBox = svgElement?.attr("viewBox") ?: "N/A"
                                            Timber.d("Worker received SVG for '$uniqueId'. width: $width, height: $height, viewBox: $viewBox, length: ${result.svg.length}")
                                            svgResults[uniqueId] = result.svg
                                        }
                                        is RenderResult.Failure -> {
                                            Timber.w("Chapter $index (Background Worker): Render FAILURE for $uniqueId. Alt: ${result.altText}")
                                            placeholder.attr("alttext", result.altText)
                                        }
                                    }
                                    element.replaceWith(placeholder)
                                }
                                Timber.d("Chapter $index (Background Worker): Finished processing MathML. SVG cache has ${svgResults.size} items. Keys: ${svgResults.keys.joinToString()}")
                            }
                            applyBookReplacementsToHtmlDocument(
                                document = document,
                                preferences = bookReplacementPreferences,
                                fileId = input.bookReplacementFileId,
                            )
                            coroutineContext.ensureActive()
                            val processedHtml = document.outerHtml()
                            Timber.d("Chapter $index (Background Worker): Processed HTML contains <math-placeholder>: ${processedHtml.contains("math-placeholder")}")


                            val semanticBlocks = androidHtmlToSemanticBlocks(
                                html = processedHtml,
                                cssRules = lightThemeCssRules,
                                textStyle = textStyle,
                                chapterAbsPath = chapter.absPath,
                                extractionBasePath = extractionBasePath,
                                density = density,
                                fontFamilyMap = fontFamilyMap,
                                constraints = constraints,
                                imageDimensionsCache = imageDimensionsCache,
                                mathSvgCache = svgResults
                            )
                            coroutineContext.ensureActive()
                            val protoBytes = proto.encodeToByteArray(semanticBlocks)
                            ProcessedChapter(
                                bookId = bookId,
                                chapterIndex = index,
                                contentBlocksProto = protoBytes,
                                estimatedPageCount = estimateSemanticPageCount(semanticBlocks),
                                styleConfigHash = input.styleConfigHash
                            )
                        } else {
                            Timber.d("Chapter $index was already in the database. Skipping.")
                            null
                        }
                    }
                }
                val processedChapters = deferreds.awaitAll().filterNotNull()
                if (processedChapters.isNotEmpty()) {
                    db.bookCacheDao().insertProcessedChapters(processedChapters)

                    val allAnchors = mutableListOf<AnchorIndexEntry>()
                    processedChapters.forEach { chapter ->
                        val blocks = proto.decodeFromByteArray<List<com.aryan.reader.paginatedreader.SemanticBlock>>(chapter.contentBlocksProto)
                        allAnchors.addAll(extractAnchorsFromBlocks(bookId, chapter.chapterIndex, blocks))
                    }
                    if (allAnchors.isNotEmpty()) {
                        db.bookCacheDao().insertAnchorIndices(allAnchors)
                        Timber.tag("TOC_NAV_DEBUG").d("Indexed ${allAnchors.size} anchors for chapters in this batch.")
                    }

                    Timber.i("Worker cached a batch of ${processedChapters.size} chapters for book $bookId.")
                }
            }
            val finalBookRecord = ProcessedBook(bookId, LATEST_PROCESSING_VERSION, estimatedTotalPages)
            db.bookCacheDao().insertProcessedBook(finalBookRecord)
            Timber.i("[BG_PROC] Finished processing all chapters for book $bookId.")

            return@withContext Result.success()
        } catch (e: CancellationException) {
            Timber.i("Background processing cancelled for book $bookId")
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Error in pagination worker for book $bookId")
            return@withContext Result.failure()
        } finally {
            inputFile.delete()
            mathMLRenderer.destroy()
        }
    }

    private fun extractAnchorsFromBlocks(
        bookId: String,
        chapterIndex: Int,
        blocks: List<com.aryan.reader.paginatedreader.SemanticBlock>
    ): List<AnchorIndexEntry> {
        val anchors = mutableListOf<AnchorIndexEntry>()

        fun walk(block: com.aryan.reader.paginatedreader.SemanticBlock) {
            // 1. Check block ID
            block.elementId?.let {
                anchors.add(AnchorIndexEntry(bookId, it, chapterIndex, block.blockIndex))
            }

            // 2. Check Span IDs (Inline anchors)
            if (block is com.aryan.reader.paginatedreader.SemanticTextBlock) {
                block.spans.forEach { span ->
                    span.elementId?.let {
                        anchors.add(AnchorIndexEntry(bookId, it, chapterIndex, block.blockIndex))
                    }
                }
            }

            // 3. Recurse
            when (block) {
                is com.aryan.reader.paginatedreader.SemanticFlexContainer -> block.children.forEach { walk(it) }
                is com.aryan.reader.paginatedreader.SemanticTable -> block.rows.flatten().forEach { cell -> cell.content.forEach { walk(it) } }
                is com.aryan.reader.paginatedreader.SemanticList -> block.items.forEach { walk(it) }
                is com.aryan.reader.paginatedreader.SemanticWrappingBlock -> {
                    walk(block.floatedImage)
                    block.paragraphsToWrap.forEach { walk(it) }
                }
                else -> {}
            }
        }

        blocks.forEach { walk(it) }
        return anchors
    }

    private fun estimateSemanticPageCount(
        blocks: List<com.aryan.reader.paginatedreader.SemanticBlock>
    ): Int {
        var charCount = 0

        fun walk(block: com.aryan.reader.paginatedreader.SemanticBlock) {
            when (block) {
                is com.aryan.reader.paginatedreader.SemanticTextBlock -> {
                    charCount += block.text.length
                }
                is com.aryan.reader.paginatedreader.SemanticFlexContainer -> block.children.forEach(::walk)
                is com.aryan.reader.paginatedreader.SemanticTable -> {
                    block.rows.forEach { row -> row.forEach { cell -> cell.content.forEach(::walk) } }
                }
                is com.aryan.reader.paginatedreader.SemanticList -> block.items.forEach(::walk)
                is com.aryan.reader.paginatedreader.SemanticWrappingBlock -> block.paragraphsToWrap.forEach(::walk)
                else -> Unit
            }
        }

        blocks.forEach(::walk)
        return ((charCount + 2_499) / 2_500).coerceAtLeast(1)
    }
}
