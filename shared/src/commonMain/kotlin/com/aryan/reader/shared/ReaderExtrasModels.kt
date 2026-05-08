package com.aryan.reader.shared

import com.aryan.reader.shared.reader.ReaderSessionState

const val GEMINI_CLOUD_TTS_MODEL = "gemini-3.1-flash-live-preview"
const val GEMINI_CLOUD_TTS_MODEL_ID = "gemini:$GEMINI_CLOUD_TTS_MODEL"
const val DEFAULT_CLOUD_TTS_SPEAKER_ID = "Kore"

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

val ReaderCloudTtsSpeakers = listOf(
    "Kore",
    "Puck",
    "Charon",
    "Aoede",
    "Fenrir",
    "Leda",
    "Orus",
    "Zephyr"
)

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

data class ReaderCloudTtsState(
    val isAvailable: Boolean = false,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
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
