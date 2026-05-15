package com.aryan.reader.shared

import com.aryan.reader.paginatedreader.SemanticBlock
import com.aryan.reader.paginatedreader.SemanticFlexContainer
import com.aryan.reader.paginatedreader.SemanticList
import com.aryan.reader.paginatedreader.SemanticTable
import com.aryan.reader.paginatedreader.SemanticTextBlock
import com.aryan.reader.paginatedreader.SemanticWrappingBlock
import com.aryan.reader.shared.reader.ReaderPage
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter

const val GEMINI_CLOUD_TTS_MODEL = "gemini-3.1-flash-live-preview"
const val GEMINI_CLOUD_TTS_MODEL_ID = "gemini:$GEMINI_CLOUD_TTS_MODEL"
const val DEFAULT_CLOUD_TTS_SPEAKER_ID = "Aoede"
const val READER_TTS_CHUNK_MAX_LENGTH = 250

data class ReaderCloudTtsVoice(
    val id: String,
    val name: String,
    val description: String
)

enum class ReaderAiFeature(val displayName: String) {
    DEFINE("Smart dictionary"),
    SUMMARIZE("Summaries"),
    RECAP("Recaps")
}

data class ReaderAiModelOption(
    val provider: String,
    val name: String,
    val label: String = "${provider.replaceFirstChar { it.uppercaseChar() }} - $name"
) {
    val id: String = "$provider:$name"
}

data class ReaderAiByokSettings(
    val geminiKey: String = "",
    val groqKey: String = "",
    val useOneModel: Boolean = true,
    val modelForAll: String = "",
    val defineModel: String = "",
    val summarizeModel: String = "",
    val recapModel: String = "",
    val ttsModel: String = "",
    val hideReaderAiFeatures: Boolean = false,
    val ttsSpeakerId: String = DEFAULT_CLOUD_TTS_SPEAKER_ID
) {
    fun sanitized(): ReaderAiByokSettings {
        val knownTextModelIds = ReaderAiModelOptions.mapTo(mutableSetOf()) { it.id }
        return copy(
            geminiKey = geminiKey.trim(),
            groqKey = groqKey.trim(),
            modelForAll = modelForAll.takeIf { it in knownTextModelIds }.orEmpty(),
            defineModel = defineModel.takeIf { it in knownTextModelIds }.orEmpty(),
            summarizeModel = summarizeModel.takeIf { it in knownTextModelIds }.orEmpty(),
            recapModel = recapModel.takeIf { it in knownTextModelIds }.orEmpty(),
            ttsModel = ttsModel.takeIf { it == GEMINI_CLOUD_TTS_MODEL_ID }.orEmpty(),
            ttsSpeakerId = ttsSpeakerId.ifBlank { DEFAULT_CLOUD_TTS_SPEAKER_ID }
        )
    }

    fun modelIdFor(feature: ReaderAiFeature): String {
        return if (useOneModel) {
            modelForAll
        } else {
            when (feature) {
                ReaderAiFeature.DEFINE -> defineModel
                ReaderAiFeature.SUMMARIZE -> summarizeModel
                ReaderAiFeature.RECAP -> recapModel
            }
        }
    }

    fun apiKeyFor(provider: String): String {
        return when (provider) {
            "gemini" -> geminiKey
            "groq" -> groqKey
            else -> ""
        }.trim()
    }

    val hasAnyAiKey: Boolean get() = geminiKey.isNotBlank() || groqKey.isNotBlank()
    val areReaderAiFeaturesAvailable: Boolean get() = !hideReaderAiFeatures && hasAnyAiKey
    val isCloudTtsAvailable: Boolean get() = geminiKey.isNotBlank() && ttsModel == GEMINI_CLOUD_TTS_MODEL_ID
}

val ReaderAiModelOptions = listOf(
    ReaderAiModelOption("groq", "qwen/qwen3-32b"),
    ReaderAiModelOption("groq", "llama-3.3-70b-versatile"),
    ReaderAiModelOption("groq", "llama-3.1-8b-instant"),
    ReaderAiModelOption("gemini", "gemma-4-26b-a4b-it"),
    ReaderAiModelOption("gemini", "gemma-4-31b-it"),
    ReaderAiModelOption("gemini", "gemini-flash-lite-latest"),
    ReaderAiModelOption("gemini", "gemini-2.5-flash-lite"),
    ReaderAiModelOption("gemini", "gemini-3.1-flash-lite-preview")
)

val ReaderCloudTtsVoices = listOf(
    ReaderCloudTtsVoice("Zephyr", "Zephyr", "Bright, Higher pitch"),
    ReaderCloudTtsVoice("Puck", "Puck", "Upbeat, Middle pitch"),
    ReaderCloudTtsVoice("Charon", "Charon", "Informative, Lower pitch"),
    ReaderCloudTtsVoice("Kore", "Kore", "Firm, Middle pitch"),
    ReaderCloudTtsVoice("Fenrir", "Fenrir", "Excitable, Lower middle pitch"),
    ReaderCloudTtsVoice("Leda", "Leda", "Youthful, Higher pitch"),
    ReaderCloudTtsVoice("Orus", "Orus", "Firm, Lower middle pitch"),
    ReaderCloudTtsVoice("Aoede", "Aoede", "Breezy, Middle pitch"),
    ReaderCloudTtsVoice("Callirrhoe", "Callirrhoe", "Easy-going, Middle pitch"),
    ReaderCloudTtsVoice("Autonoe", "Autonoe", "Bright, Middle pitch"),
    ReaderCloudTtsVoice("Enceladus", "Enceladus", "Breathy, Lower pitch"),
    ReaderCloudTtsVoice("Iapetus", "Iapetus", "Clear, Lower middle pitch"),
    ReaderCloudTtsVoice("Umbriel", "Umbriel", "Easy-going, Lower middle pitch"),
    ReaderCloudTtsVoice("Algieba", "Algieba", "Smooth, Lower pitch"),
    ReaderCloudTtsVoice("Despina", "Despina", "Smooth, Middle pitch"),
    ReaderCloudTtsVoice("Erinome", "Erinome", "Clear, Middle pitch"),
    ReaderCloudTtsVoice("Algenib", "Algenib", "Gravelly, Lower pitch"),
    ReaderCloudTtsVoice("Rasalgethi", "Rasalgethi", "Informative, Middle pitch"),
    ReaderCloudTtsVoice("Laomedeia", "Laomedeia", "Upbeat, Higher pitch"),
    ReaderCloudTtsVoice("Achernar", "Achernar", "Soft, Higher pitch"),
    ReaderCloudTtsVoice("Alnilam", "Alnilam", "Firm, Lower middle pitch"),
    ReaderCloudTtsVoice("Schedar", "Schedar", "Even, Lower middle pitch"),
    ReaderCloudTtsVoice("Gacrux", "Gacrux", "Mature, Middle pitch"),
    ReaderCloudTtsVoice("Pulcherrima", "Pulcherrima", "Forward, Middle pitch"),
    ReaderCloudTtsVoice("Achird", "Achird", "Friendly, Lower middle pitch"),
    ReaderCloudTtsVoice("Zubenelgenubi", "Zubenelgenubi", "Casual, Lower middle pitch"),
    ReaderCloudTtsVoice("Vindemiatrix", "Vindemiatrix", "Gentle, Middle pitch"),
    ReaderCloudTtsVoice("Sadachbia", "Sadachbia", "Lively, Lower pitch"),
    ReaderCloudTtsVoice("Sadaltager", "Sadaltager", "Lively, Lower pitch"),
    ReaderCloudTtsVoice("Sulafat", "Sulafat", "Warm, Middle pitch")
)

val ReaderCloudTtsSpeakers = ReaderCloudTtsVoices.map { it.id }

fun readerCloudTtsVoiceById(id: String): ReaderCloudTtsVoice? {
    return ReaderCloudTtsVoices.firstOrNull { it.id == id }
}

fun formatReaderTtsBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB", "PB")
    var value = bytes.toDouble() / 1024.0
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "${(value * 10).toInt() / 10.0} ${units[unitIndex]}"
}

fun splitReaderTextIntoTtsChunks(
    text: String,
    maxLength: Int = READER_TTS_CHUNK_MAX_LENGTH
): List<String> {
    if (text.isBlank()) return emptyList()
    val sentenceBoundaryRegex = Regex("""(?<!\w\.\w.)(?<![A-Z][a-z]\.)(?<=[.?!\n])\s+""")
    val sentences = text.trim()
        .split(sentenceBoundaryRegex)
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (sentences.isEmpty()) return emptyList()

    val chunks = mutableListOf<String>()
    val currentChunk = StringBuilder()
    fun flush() {
        if (currentChunk.isNotEmpty()) {
            chunks += currentChunk.toString()
            currentChunk.clear()
        }
    }

    sentences.forEach { sentence ->
        if (sentence.length > maxLength) {
            flush()
            chunks += sentence
            return@forEach
        }
        if (currentChunk.isNotEmpty() && currentChunk.length + sentence.length + 1 > maxLength) {
            flush()
        }
        if (currentChunk.isNotEmpty()) currentChunk.append(' ')
        currentChunk.append(sentence)
    }
    flush()
    return chunks
}

fun readerAiModelById(id: String): ReaderAiModelOption? {
    return ReaderAiModelOptions.firstOrNull { it.id == id }
}

fun maskedReaderAiKey(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.length <= 6 -> "***"
        else -> "${trimmed.take(3)}...${trimmed.takeLast(3)}"
    }
}

enum class ReaderExternalLookupAction(val title: String) {
    DICTIONARY("Dictionary"),
    TRANSLATE("Translate"),
    SEARCH("Search")
}

fun externalLookupUrl(action: ReaderExternalLookupAction, text: String): String {
    val encoded = text.trim().urlEncoded()
    return when (action) {
        ReaderExternalLookupAction.DICTIONARY -> "https://www.google.com/search?q=define+$encoded"
        ReaderExternalLookupAction.TRANSLATE -> "https://translate.google.com/?sl=auto&tl=en&text=$encoded&op=translate"
        ReaderExternalLookupAction.SEARCH -> "https://www.google.com/search?q=$encoded"
    }
}

data class ReaderAutoScrollState(
    val enabled: Boolean = false,
    val speed: Float = 36f
) {
    fun sanitized(): ReaderAutoScrollState {
        return copy(speed = speed.coerceIn(12f, 160f))
    }
}

enum class ReaderTtsReadScope(val label: String) {
    PAGE("Page"),
    CHAPTER("Chapter"),
    BOOK("From here")
}

data class ReaderTtsChunk(
    val index: Int,
    val pageIndex: Int,
    val chapterIndex: Int,
    val chapterTitle: String,
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val sourceCfi: String? = null,
    val spokenText: String = text
) {
    fun toLocator(): ReaderLocator {
        val boundedEnd = endOffset.coerceAtLeast(startOffset)
        return ReaderLocator(
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            startOffset = startOffset,
            endOffset = boundedEnd,
            textQuote = text,
            cfi = sourceCfi ?: "desktop:$chapterIndex:$startOffset:$boundedEnd"
        )
    }

    fun toHighlight(sessionId: Long): UserHighlight {
        val locator = toLocator()
        return UserHighlight(
            id = "tts_${sessionId}_$index",
            cfi = locator.cfi.orEmpty(),
            text = text,
            color = HighlightColor.YELLOW,
            chapterIndex = chapterIndex,
            locator = locator
        )
    }
}

data class ReaderTtsProgress(
    val sessionId: Long = 0L,
    val scope: ReaderTtsReadScope = ReaderTtsReadScope.PAGE,
    val chunks: List<ReaderTtsChunk> = emptyList(),
    val currentChunkIndex: Int = -1
) {
    val currentChunk: ReaderTtsChunk?
        get() = chunks.getOrNull(currentChunkIndex)

    val isActive: Boolean
        get() = currentChunk != null

    val currentPositionLabel: String?
        get() = currentChunk?.let { chunk ->
            "Part ${currentChunkIndex + 1}/${chunks.size} - ${chunk.chapterTitle.ifBlank { scope.label }}"
        }
}

data class ReaderTtsCacheSummary(
    val cachedChapterCount: Int = 0,
    val cachedChunkCount: Int = 0,
    val currentVoiceChunkCount: Int = 0,
    val totalSizeBytes: Long = 0L,
    val currentVoiceSizeBytes: Long = 0L
) {
    val hasCachedAudio: Boolean get() = cachedChunkCount > 0
    val hasCurrentVoiceCachedAudio: Boolean get() = currentVoiceChunkCount > 0

    val currentVoiceLabel: String
        get() = if (hasCurrentVoiceCachedAudio) {
            "$currentVoiceChunkCount chunks, ${formatReaderTtsBytes(currentVoiceSizeBytes)}"
        } else {
            "No cached chunks for this voice"
        }
}

object ReaderTtsPlanner {
    fun chunksForCurrentPage(session: ReaderSessionState): List<ReaderTtsChunk> {
        val page = session.reader.currentPage ?: return emptyList()
        return chunksForPages(session.reader.book, listOf(page))
    }

    fun chunksForCurrentChapter(session: ReaderSessionState): List<ReaderTtsChunk> {
        val page = session.reader.currentPage ?: return emptyList()
        return chunksForPages(
            session.reader.book,
            session.reader.pages
                .asSequence()
                .filter { it.pageIndex >= page.pageIndex && it.chapterIndex == page.chapterIndex }
                .toList()
        )
    }

    fun chunksFromCurrentLocation(session: ReaderSessionState): List<ReaderTtsChunk> {
        val anchor = session.navigationLocator
        val pageIndex = anchor?.pageIndex ?: session.reader.currentPageIndex
        val pages = session.reader.pages.dropWhile { it.pageIndex < pageIndex.coerceAtLeast(0) }
        val chunks = chunksForPages(session.reader.book, pages)
        val chapterIndex = anchor?.chapterIndex
        val startOffset = anchor?.startOffset
        if (chapterIndex == null && startOffset == null) return chunks
        var nextIndex = 0
        return chunks.mapNotNull { chunk ->
            chunk.afterLocator(chapterIndex = chapterIndex, startOffset = startOffset)
                ?.copy(index = nextIndex++)
        }
    }

    fun chunksForText(
        text: String,
        pageIndex: Int,
        chapterIndex: Int,
        chapterTitle: String,
        sourceStartOffset: Int = 0
    ): List<ReaderTtsChunk> {
        return splitTextIntoRanges(text).mapIndexed { index, range ->
            ReaderTtsChunk(
                index = index,
                pageIndex = pageIndex,
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle,
                text = range.text,
                startOffset = sourceStartOffset + range.start,
                endOffset = sourceStartOffset + range.end
            )
        }
    }

    private fun chunksForPages(book: SharedEpubBook, pages: List<ReaderPage>): List<ReaderTtsChunk> {
        var nextIndex = 0
        return pages
            .groupBy { it.chapterIndex }
            .entries
            .sortedBy { (chapterIndex, _) ->
                pages.indexOfFirst { it.chapterIndex == chapterIndex }.takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            .flatMap { chapterPages ->
                val chapter = book.chapters.getOrNull(chapterPages.key)
                val semanticChunks = chapter
                    ?.let { chunksForSemanticPages(it, chapterPages.value) }
                    .orEmpty()
                if (semanticChunks.isNotEmpty()) {
                    semanticChunks
                } else {
                    chunksForPlainPages(book, chapterPages.value)
                }
            }
            .distinctBy { "${it.sourceCfi}:${it.startOffset}:${it.endOffset}:${it.text}" }
            .map { it.copy(index = nextIndex++) }
            .toList()
    }

    private fun chunksForPlainPages(book: SharedEpubBook, pages: List<ReaderPage>): List<ReaderTtsChunk> {
        return pages.flatMap { page ->
            val chapterText = book.chapters
                .getOrNull(page.chapterIndex)
                ?.normalizedTtsSourceText()
                .orEmpty()
            val sourceStartOffset = page.sourceTextStartOffset(chapterText)
            splitTextIntoRanges(page.text).map { range ->
                ReaderTtsChunk(
                    index = 0,
                    pageIndex = page.pageIndex,
                    chapterIndex = page.chapterIndex,
                    chapterTitle = page.chapterTitle,
                    text = range.text,
                    startOffset = sourceStartOffset + range.start,
                    endOffset = sourceStartOffset + range.end
                )
            }
        }
    }

    private fun ReaderTtsChunk.afterLocator(chapterIndex: Int?, startOffset: Int?): ReaderTtsChunk? {
        if (chapterIndex != null) {
            if (this.chapterIndex < chapterIndex) return null
            if (this.chapterIndex > chapterIndex) return this
        }
        val anchorOffset = startOffset ?: return this
        if (endOffset <= anchorOffset) return null
        if (anchorOffset <= this.startOffset) return this
        return trimStartTo(anchorOffset)
    }

    private fun ReaderTtsChunk.trimStartTo(sourceOffset: Int): ReaderTtsChunk? {
        val boundedOffset = sourceOffset.coerceIn(startOffset, endOffset)
        if (boundedOffset <= startOffset) return this
        if (boundedOffset >= endOffset) return null
        val rawDrop = (boundedOffset - startOffset).coerceIn(0, text.length)
        val remaining = text.drop(rawDrop)
        val leadingWhitespace = remaining.indexOfFirst { !it.isWhitespace() }
        if (leadingWhitespace < 0) return null
        val nextText = remaining.drop(leadingWhitespace)
        if (nextText.isBlank()) return null
        val nextStartOffset = (boundedOffset + leadingWhitespace).coerceAtMost(endOffset)
        return copy(
            text = nextText,
            spokenText = nextText,
            startOffset = nextStartOffset
        )
    }

    private fun chunksForSemanticPages(
        chapter: SharedEpubChapter,
        pages: List<ReaderPage>
    ): List<ReaderTtsChunk> {
        if (chapter.semanticBlocks.isEmpty() || pages.isEmpty()) return emptyList()
        val ranges = pages.map { it.startOffset to it.endOffset }
        val textBlocks = chapter.semanticBlocks.semanticTextBlocks()
            .filter { block ->
                block.cfi != null &&
                    block.text.isNotBlank() &&
                    ranges.any { (start, end) -> block.intersects(start, end) }
            }
        return textBlocks.flatMap { block ->
            val blockStart = block.startCharOffsetInSource.coerceAtLeast(0)
            splitTextIntoRanges(block.text).mapNotNull { range ->
                val chunkStart = blockStart + range.start
                val chunkEnd = blockStart + range.end
                if (ranges.none { (start, end) -> chunkStart < end && chunkEnd > start }) return@mapNotNull null
                val page = pages.firstOrNull { it.intersects(chunkStart, chunkEnd) }
                    ?: pages.minByOrNull { kotlin.math.abs(it.startOffset - chunkStart) }
                    ?: return@mapNotNull null
                ReaderTtsChunk(
                    index = 0,
                    pageIndex = page.pageIndex,
                    chapterIndex = page.chapterIndex,
                    chapterTitle = page.chapterTitle,
                    text = range.text,
                    startOffset = chunkStart,
                    endOffset = chunkEnd,
                    sourceCfi = block.cfi
                )
            }
        }
    }

    private fun List<SemanticBlock>.semanticTextBlocks(): List<SemanticTextBlock> {
        val blocks = mutableListOf<SemanticTextBlock>()
        fun visit(block: SemanticBlock) {
            when (block) {
                is SemanticTextBlock -> blocks += block
                is SemanticFlexContainer -> block.children.forEach(::visit)
                is SemanticTable -> block.rows.forEach { row -> row.forEach { cell -> cell.content.forEach(::visit) } }
                is SemanticList -> block.items.forEach(::visit)
                is SemanticWrappingBlock -> block.paragraphsToWrap.forEach(::visit)
                else -> Unit
            }
        }
        forEach(::visit)
        return blocks
    }

    private fun SemanticTextBlock.intersects(startOffset: Int, endOffset: Int): Boolean {
        val start = startCharOffsetInSource
        val end = start + text.length
        return start < endOffset && end > startOffset
    }

    private fun ReaderPage.intersects(startOffset: Int, endOffset: Int): Boolean {
        return startOffset < endOffset && startOffset < this.endOffset && endOffset > this.startOffset
    }

    private fun ReaderPage.sourceTextStartOffset(chapterText: String): Int {
        if (chapterText.isBlank()) return startOffset
        val boundedStart = startOffset.coerceIn(0, chapterText.length)
        val boundedEnd = endOffset.coerceIn(boundedStart, chapterText.length)
        val pageSlice = chapterText.substring(boundedStart, boundedEnd)
        val trimAdjustedStart = boundedStart + pageSlice.leadingWhitespaceLength()
        val exactTextStart = text
            .takeIf { it.isNotBlank() }
            ?.let { needle ->
                chapterText.indexOf(needle, startIndex = boundedStart)
                    .takeIf { found -> found >= boundedStart && found + needle.length <= boundedEnd }
            }
        if (exactTextStart != null) return exactTextStart
        val trimmedTextStart = text
            .trim()
            .takeIf { it.isNotBlank() }
            ?.let { needle ->
                chapterText.indexOf(needle, startIndex = boundedStart)
                    .takeIf { found -> found >= boundedStart && found + needle.length <= boundedEnd }
            }
        return trimmedTextStart ?: trimAdjustedStart
    }

    private fun String.leadingWhitespaceLength(): Int {
        return length - trimStart().length
    }

    private fun SharedEpubChapter.normalizedTtsSourceText(): String {
        return plainText
            .replace("\r\n", "\n")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun splitTextIntoRanges(
        text: String,
        maxLength: Int = READER_TTS_CHUNK_MAX_LENGTH
    ): List<ReaderTtsTextRange> {
        val sourceStart = text.indexOfFirst { !it.isWhitespace() }
        if (sourceStart < 0) return emptyList()
        val sourceEnd = text.indexOfLast { !it.isWhitespace() } + 1
        val source = text.substring(sourceStart, sourceEnd)
        val sentenceRanges = androidStyleSentenceRanges(source, sourceStart)
        if (sentenceRanges.isEmpty()) return emptyList()

        val chunks = mutableListOf<ReaderTtsTextRange>()
        var currentText = StringBuilder()
        var currentStart = -1
        var currentEnd = -1
        fun flushCurrent() {
            if (currentText.isNotEmpty() && currentStart >= 0 && currentEnd >= currentStart) {
                chunks += ReaderTtsTextRange(
                    text = currentText.toString(),
                    start = currentStart,
                    end = currentStart + currentText.length
                )
            }
            currentText = StringBuilder()
            currentStart = -1
            currentEnd = -1
        }

        for (sentence in sentenceRanges) {
            if (sentence.text.length > maxLength) {
                flushCurrent()
                chunks += sentence
                continue
            }
            if (currentText.isNotEmpty() && currentText.length + sentence.text.length + 1 > maxLength) {
                flushCurrent()
                currentText.append(sentence.text)
                currentStart = sentence.start
                currentEnd = sentence.end
            } else {
                if (currentText.isNotEmpty()) currentText.append(" ")
                currentText.append(sentence.text)
                if (currentStart < 0) currentStart = sentence.start
                currentEnd = sentence.end
            }
        }
        flushCurrent()
        return chunks
    }

    private fun androidStyleSentenceRanges(source: String, sourceOffset: Int): List<ReaderTtsTextRange> {
        val sentenceBoundaryRegex = Regex("""(?<!\w\.\w.)(?<![A-Z][a-z]\.)(?<=[.?!\n])\s+""")
        val ranges = mutableListOf<ReaderTtsTextRange>()
        var start = 0
        sentenceBoundaryRegex.findAll(source).forEach { match ->
            val end = match.range.first
            if (end > start) {
                source.substring(start, end)
                    .takeIf { it.isNotBlank() }
                    ?.let { sentence ->
                        ranges += ReaderTtsTextRange(
                            text = sentence,
                            start = sourceOffset + start,
                            end = sourceOffset + end
                        )
                    }
            }
            start = match.range.last + 1
        }
        if (start < source.length) {
            val sentence = source.substring(start)
            if (sentence.isNotBlank()) {
                ranges += ReaderTtsTextRange(
                    text = sentence,
                    start = sourceOffset + start,
                    end = sourceOffset + source.length
                )
            }
        }
        return ranges
    }

    private data class ReaderTtsTextRange(
        val text: String,
        val start: Int,
        val end: Int
    )
}

data class ReaderCloudTtsState(
    val isAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val isPaused: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val progress: ReaderTtsProgress = ReaderTtsProgress(),
    val cacheSummary: ReaderTtsCacheSummary = ReaderTtsCacheSummary()
)

data class ReaderAiResultState(
    val title: String? = null,
    val text: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val hasContent: Boolean get() = text.isNotBlank() || errorMessage != null || isLoading
}

data class ReaderExtrasState(
    val autoScroll: ReaderAutoScrollState = ReaderAutoScrollState(),
    val cloudTts: ReaderCloudTtsState = ReaderCloudTtsState(),
    val aiResult: ReaderAiResultState = ReaderAiResultState()
)

data class ReaderByokTextRequest(
    val model: ReaderAiModelOption,
    val apiKey: String,
    val systemInstruction: String,
    val userPrompt: String,
    val temperature: Double,
    val maxTokens: Int
)

sealed interface ReaderByokTextRequestResult {
    data class Ready(val request: ReaderByokTextRequest) : ReaderByokTextRequestResult
    data class MissingModel(val featureName: String) : ReaderByokTextRequestResult
    data class MissingKey(val provider: String) : ReaderByokTextRequestResult
    data object Hidden : ReaderByokTextRequestResult
}

object ReaderByokTextRequests {
    fun build(
        settings: ReaderAiByokSettings,
        feature: ReaderAiFeature,
        text: String,
        context: String? = null
    ): ReaderByokTextRequestResult {
        val sanitized = settings.sanitized()
        if (sanitized.hideReaderAiFeatures) return ReaderByokTextRequestResult.Hidden
        val model = readerAiModelById(sanitized.modelIdFor(feature))
            ?: return ReaderByokTextRequestResult.MissingModel(feature.displayName)
        val apiKey = sanitized.apiKeyFor(model.provider)
        if (apiKey.isBlank()) return ReaderByokTextRequestResult.MissingKey(model.provider)
        val prompt = promptFor(feature, text, context)
        return ReaderByokTextRequestResult.Ready(
            ReaderByokTextRequest(
                model = model,
                apiKey = apiKey,
                systemInstruction = prompt.systemInstruction,
                userPrompt = prompt.userPrompt,
                temperature = prompt.temperature,
                maxTokens = prompt.maxTokens
            )
        )
    }

    private fun promptFor(feature: ReaderAiFeature, text: String, context: String?): ReaderPrompt {
        return when (feature) {
            ReaderAiFeature.DEFINE -> ReaderPrompt(
                systemInstruction = "You are a concise reading dictionary. Define the selected word or passage, explain nuance in context, and avoid unrelated commentary.",
                userPrompt = buildString {
                    context?.takeIf { it.isNotBlank() }?.let {
                        append("Context:\n")
                        append(it.trim().take(3000))
                        append("\n\n")
                    }
                    append("Selection:\n")
                    append(text.trim())
                },
                temperature = 0.15,
                maxTokens = 1024
            )

            ReaderAiFeature.SUMMARIZE -> ReaderPrompt(
                systemInstruction = "You are an expert reading assistant. Summarize the provided passage clearly and concisely. Focus on the main ideas, plot points, and useful context. Do not add a preamble.",
                userPrompt = text.trim(),
                temperature = 0.2,
                maxTokens = 4096
            )

            ReaderAiFeature.RECAP -> ReaderPrompt(
                systemInstruction = "You are a reading assistant creating a recap up to the reader's current position. Synthesize prior context and current text into a cohesive recap. Conclude exactly where the reader is positioned. Do not add a preamble.",
                userPrompt = text.trim(),
                temperature = 0.3,
                maxTokens = 4096
            )
        }
    }
}

data class ReaderPrompt(
    val systemInstruction: String,
    val userPrompt: String,
    val temperature: Double,
    val maxTokens: Int
)

object ReaderContextExtractor {
    fun currentPageText(session: ReaderSessionState, maxChars: Int = 6000): String {
        return session.reader.currentPage?.text.orEmpty().trim().take(maxChars)
    }

    fun currentChapterText(session: ReaderSessionState, maxChars: Int = 20_000): String {
        val chapterIndex = session.reader.currentPage?.chapterIndex ?: return currentPageText(session, maxChars)
        return session.reader.book.chapters
            .getOrNull(chapterIndex)
            ?.plainText
            .orEmpty()
            .trim()
            .take(maxChars)
    }

    fun textBeforeCurrentLocation(session: ReaderSessionState, maxChars: Int = 24_000): String {
        val page = session.reader.currentPage ?: return ""
        val builder = StringBuilder()
        session.reader.book.chapters.forEachIndexed { chapterIndex, chapter ->
            when {
                chapterIndex < page.chapterIndex -> {
                    builder.append(chapter.title).append('\n')
                    builder.append(chapter.plainText.trim()).append("\n\n")
                }
                chapterIndex == page.chapterIndex -> {
                    builder.append(chapter.title).append('\n')
                    builder.append(chapter.plainText.take(page.endOffset.coerceAtMost(chapter.plainText.length)).trim())
                }
            }
        }
        return builder.toString().trim().takeLast(maxChars)
    }
}

private fun String.urlEncoded(): String {
    val bytes = toByteArray(Charsets.UTF_8)
    val builder = StringBuilder()
    bytes.forEach { raw ->
        val value = raw.toInt() and 0xFF
        val char = value.toChar()
        when {
            value in 'A'.code..'Z'.code ||
                value in 'a'.code..'z'.code ||
                value in '0'.code..'9'.code ||
                char in "-_.~" -> builder.append(char)
            char == ' ' -> builder.append('+')
            else -> builder.append('%').append(value.toString(16).uppercase().padStart(2, '0'))
        }
    }
    return builder.toString()
}
