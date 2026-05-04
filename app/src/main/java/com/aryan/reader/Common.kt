// Common.kt
@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.edit
import androidx.core.graphics.toColorInt
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.epubreader.PREF_CUSTOM_THEMES
import com.aryan.reader.epubreader.PREF_READER_THEME
import com.aryan.reader.paginatedreader.TtsChunk
import com.aryan.reader.pdf.PdfHighlightColor
import com.aryan.reader.tts.GEMINI_TTS_SPEAKERS
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsCacheManager
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.formatBytes
import com.aryan.reader.tts.loadTtsMode
import com.aryan.reader.tts.rememberTtsController
import com.aryan.reader.tts.splitTextIntoChunks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.Code
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Heading
import org.commonmark.node.ListItem
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val aiServerBasePath = BuildConfig.AI_WORKER_URL
const val summarizeEndpoint = "/summarize"
const val summarizationUrl = aiServerBasePath + summarizeEndpoint
const val defineEndpoint = "/define"
const val aiDefinitionUrl = aiServerBasePath + defineEndpoint
const val recapEndpoint = "/recap"
const val recapUrl = aiServerBasePath + recapEndpoint

const val PREF_NATIVE_TTS_VOICE = "native_tts_voice_name"
private const val AI_PREFS_NAME = "ai_byok_prefs"
private const val PREF_AI_HIDE_READER_FEATURES = "hide_reader_ai_features"
private const val PREF_AI_GEMINI_KEY = "gemini_key"
private const val PREF_AI_GROQ_KEY = "groq_key"
private const val PREF_AI_USE_ONE_MODEL = "use_one_model"
private const val PREF_AI_MODEL_ALL = "model_all"
private const val PREF_AI_MODEL_DEFINE = "model_define"
private const val PREF_AI_MODEL_SUMMARIZE = "model_summarize"
private const val PREF_AI_MODEL_RECAP = "model_recap"
private const val PREF_AI_TTS_MODEL = "tts_model"
private const val PREF_AI_MODEL_EMPTY_MIGRATION_DONE = "model_empty_migration_done"
private const val AI_KEYSTORE_ALIAS = "reader_ai_byok_key_v1"
private const val ENCRYPTION_PREFIX = "v1:"
const val GEMINI_CLOUD_TTS_MODEL = "gemini-3.1-flash-live-preview"
const val GEMINI_CLOUD_TTS_MODEL_ID = "gemini:$GEMINI_CLOUD_TTS_MODEL"

enum class AiFeature { DEFINE, SUMMARIZE, RECAP }

private fun AiFeature.displayName(): String {
    return when (this) {
        AiFeature.DEFINE -> "Smart dictionary"
        AiFeature.SUMMARIZE -> "Summaries"
        AiFeature.RECAP -> "Recaps"
    }
}

data class AiModelOption(
    val provider: String,
    val name: String,
    val label: String = "${provider.replaceFirstChar { it.titlecase(Locale.ROOT) }} - $name"
) {
    val id: String = "$provider:$name"
}

data class AiByokSettings(
    val geminiKey: String = "",
    val groqKey: String = "",
    val useOneModel: Boolean = true,
    val modelForAll: String = "",
    val defineModel: String = "",
    val summarizeModel: String = "",
    val recapModel: String = "",
    val ttsModel: String = ""
)

val aiByokModelOptions = listOf(
    AiModelOption("groq", "qwen/qwen3-32b"),
    AiModelOption("groq", "llama-3.3-70b-versatile"),
    AiModelOption("groq", "llama-3.1-8b-instant"),
    AiModelOption("gemini", "gemma-4-26b-a4b-it"),
    AiModelOption("gemini", "gemma-4-31b-it"),
    AiModelOption("gemini", "gemini-flash-lite-latest"),
    AiModelOption("gemini", "gemini-2.5-flash-lite"),
    AiModelOption("gemini", "gemini-3.1-flash-lite-preview")
)

private fun Context.aiPrefs() = getSharedPreferences(AI_PREFS_NAME, Context.MODE_PRIVATE)

private fun getAiSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(AI_KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
    val keySpec = KeyGenParameterSpec.Builder(
        AI_KEYSTORE_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true)
        .build()
    keyGenerator.init(keySpec)
    return keyGenerator.generateKey()
}

private fun encryptAiSecret(value: String): String {
    if (value.isBlank()) return ""
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getAiSecretKey())
    val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
    val combined = cipher.iv + encrypted
    return ENCRYPTION_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
}

private fun decryptAiSecret(value: String?): String {
    if (value.isNullOrBlank()) return ""
    if (!value.startsWith(ENCRYPTION_PREFIX)) return value
    return try {
        val combined = Base64.decode(value.removePrefix(ENCRYPTION_PREFIX), Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getAiSecretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    } catch (e: Exception) {
        Timber.e(e, "Failed to decrypt AI key")
        ""
    }
}

private fun maskedAiSecret(value: String): String {
    val trimmed = value.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.length <= 6 -> "***"
        else -> "${trimmed.take(3)}...${trimmed.takeLast(3)}"
    }
}

fun loadAiByokSettings(context: Context): AiByokSettings {
    val prefs = context.aiPrefs()
    if (!prefs.getBoolean(PREF_AI_MODEL_EMPTY_MIGRATION_DONE, false)) {
        prefs.edit {
            if (prefs.getString(PREF_AI_MODEL_ALL, "") == "gemini:gemini-flash-lite-latest") putString(PREF_AI_MODEL_ALL, "")
            if (prefs.getString(PREF_AI_MODEL_DEFINE, "") == "groq:qwen/qwen3-32b") putString(PREF_AI_MODEL_DEFINE, "")
            if (prefs.getString(PREF_AI_MODEL_SUMMARIZE, "") == "gemini:gemini-flash-lite-latest") putString(PREF_AI_MODEL_SUMMARIZE, "")
            if (prefs.getString(PREF_AI_MODEL_RECAP, "") == "gemini:gemini-flash-lite-latest") putString(PREF_AI_MODEL_RECAP, "")
            putBoolean(PREF_AI_MODEL_EMPTY_MIGRATION_DONE, true)
        }
    }
    val settings = AiByokSettings(
        geminiKey = decryptAiSecret(prefs.getString(PREF_AI_GEMINI_KEY, "")),
        groqKey = decryptAiSecret(prefs.getString(PREF_AI_GROQ_KEY, "")),
        useOneModel = prefs.getBoolean(PREF_AI_USE_ONE_MODEL, true),
        modelForAll = prefs.getString(PREF_AI_MODEL_ALL, "") ?: "",
        defineModel = prefs.getString(PREF_AI_MODEL_DEFINE, "") ?: "",
        summarizeModel = prefs.getString(PREF_AI_MODEL_SUMMARIZE, "") ?: "",
        recapModel = prefs.getString(PREF_AI_MODEL_RECAP, "") ?: "",
        ttsModel = prefs.getString(PREF_AI_TTS_MODEL, "") ?: ""
    )
    val geminiStored = prefs.getString(PREF_AI_GEMINI_KEY, "").orEmpty()
    val groqStored = prefs.getString(PREF_AI_GROQ_KEY, "").orEmpty()
    if ((geminiStored.isNotBlank() && !geminiStored.startsWith(ENCRYPTION_PREFIX)) ||
        (groqStored.isNotBlank() && !groqStored.startsWith(ENCRYPTION_PREFIX))
    ) {
        saveAiByokSettings(context, settings)
    }
    return settings
}

fun saveAiByokSettings(context: Context, settings: AiByokSettings) {
    context.aiPrefs().edit {
        putString(PREF_AI_GEMINI_KEY, encryptAiSecret(settings.geminiKey.trim()))
        putString(PREF_AI_GROQ_KEY, encryptAiSecret(settings.groqKey.trim()))
        putBoolean(PREF_AI_USE_ONE_MODEL, settings.useOneModel)
        putString(PREF_AI_MODEL_ALL, settings.modelForAll)
        putString(PREF_AI_MODEL_DEFINE, settings.defineModel)
        putString(PREF_AI_MODEL_SUMMARIZE, settings.summarizeModel)
        putString(PREF_AI_MODEL_RECAP, settings.recapModel)
        putString(PREF_AI_TTS_MODEL, settings.ttsModel)
    }
}

fun saveAiByokKey(context: Context, provider: String, key: String) {
    val current = loadAiByokSettings(context)
    val updated = when (provider) {
        "gemini" -> current.copy(geminiKey = key)
        "groq" -> current.copy(groqKey = key)
        else -> current
    }
    saveAiByokSettings(context, updated)
}

fun deleteAiByokKey(context: Context, provider: String) {
    saveAiByokKey(context, provider, "")
}

fun maskedAiByokKey(context: Context, provider: String): String {
    val settings = loadAiByokSettings(context)
    return maskedAiSecret(
        when (provider) {
            "gemini" -> settings.geminiKey
            "groq" -> settings.groqKey
            else -> ""
        }
    )
}

fun loadHideReaderAiFeatures(context: Context): Boolean {
    return context.aiPrefs().getBoolean(PREF_AI_HIDE_READER_FEATURES, false)
}

fun saveHideReaderAiFeatures(context: Context, hidden: Boolean) {
    context.aiPrefs().edit { putBoolean(PREF_AI_HIDE_READER_FEATURES, hidden) }
}

fun hasAiByokKey(context: Context): Boolean {
    val settings = loadAiByokSettings(context)
    return settings.geminiKey.isNotBlank() || settings.groqKey.isNotBlank()
}

@Suppress("KotlinConstantConditions")
fun areReaderAiFeaturesEnabled(context: Context): Boolean {
    if (loadHideReaderAiFeatures(context)) return false
    if (BuildConfig.FLAVOR != "oss") return true
    return !BuildConfig.IS_OFFLINE && hasAiByokKey(context)
}

@Suppress("KotlinConstantConditions")
fun isByokCloudTtsAvailable(context: Context): Boolean {
    val settings = loadAiByokSettings(context)
    return BuildConfig.FLAVOR == "oss" &&
            !BuildConfig.IS_OFFLINE &&
            settings.geminiKey.isNotBlank() &&
            settings.ttsModel == GEMINI_CLOUD_TTS_MODEL_ID
}

private fun AiByokSettings.modelIdFor(feature: AiFeature): String {
    return if (useOneModel) modelForAll else when (feature) {
        AiFeature.DEFINE -> defineModel
        AiFeature.SUMMARIZE -> summarizeModel
        AiFeature.RECAP -> recapModel
    }
}

fun aiModelById(id: String): AiModelOption? {
    return aiByokModelOptions.firstOrNull { it.id == id }
}

private fun AiByokSettings.apiKeyFor(provider: String): String {
    return when (provider) {
        "gemini" -> geminiKey
        "groq" -> groqKey
        else -> ""
    }.trim()
}

data class SearchResult(
    val locationInSource: Int,
    val locationTitle: String,
    val snippet: AnnotatedString,
    val query: String,
    val occurrenceIndexInLocation: Int,
    val chunkIndex: Int
)

data class AiDefinitionResult(
    val definition: String? = null,
    val error: String? = null
)

data class SummarizationResult(
    val summary: String? = null,
    val error: String? = null,
    val cost: Double? = null,
    val freeRemaining: Int? = null,
    val isCacheHit: Boolean = false
)

data class CachedSummaryItem(
    val chapterIndex: Int,
    val chapterTitle: String,
    val summary: String,
    val file: File
)

class SummaryCacheManager(context: Context) {
    private val cacheDir = File(context.cacheDir, "chapter_summaries")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getFileName(bookTitle: String, chapterIndex: Int): String {
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        return "summary_${safeTitle}_$chapterIndex.txt"
    }

    fun saveSummary(bookTitle: String, chapterIndex: Int, chapterTitle: String, summary: String) {
        try {
            val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
            val contentToSave = "$chapterTitle\n$summary"
            file.writeText(contentToSave)
            Timber.d("Saved summary with title for $bookTitle Ch $chapterIndex")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save summary")
        }
    }

    fun getSummary(bookTitle: String, chapterIndex: Int): String? {
        return try {
            val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
            if (file.exists()) file.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    fun hasSummary(bookTitle: String, chapterIndex: Int): Boolean {
        val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
        return file.exists()
    }

    fun getAllSummaries(bookTitle: String): List<CachedSummaryItem> {
        val safeTitle = bookTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
        val prefix = "summary_${safeTitle}_"
        val files = cacheDir.listFiles()?.filter { it.name.startsWith(prefix) && it.name.endsWith(".txt") } ?: emptyList()

        return files.mapNotNull { file ->
            try {
                val indexStr = file.name.removePrefix(prefix).removeSuffix(".txt")
                val index = indexStr.toInt()

                val fullText = file.readText()
                val lines = fullText.lines()

                val title = lines.firstOrNull()?.trim() ?: "Chapter ${index + 1}"
                val summaryText = if (lines.size > 1) lines.drop(1).joinToString("\n") else ""

                Timber.d("Cache Load: Ch $index, Title: $title")

                CachedSummaryItem(index, title, summaryText, file)
            } catch (e: Exception) {
                Timber.e(e, "Error parsing cache file: ${file.name}")
                null
            }
        }.sortedBy { it.chapterIndex }
    }

    fun deleteSummary(bookTitle: String, chapterIndex: Int) {
        val file = File(cacheDir, getFileName(bookTitle, chapterIndex))
        if (file.exists()) file.delete()
    }

    fun clearBookCache(bookTitle: String) {
        getAllSummaries(bookTitle).forEach { it.file.delete() }
    }
}

@Stable
class SearchState(
    private val scope: CoroutineScope,
    private val searcher: suspend (String) -> List<SearchResult>
) {
    var isSearchActive by mutableStateOf(false)
    var showSearchResultsPanel by mutableStateOf(true)
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var isSearchInProgress by mutableStateOf(false)
    var currentSearchResultIndex by mutableIntStateOf(-1)

    val searchResultsCount by derivedStateOf { searchResults.size }
    val hasResults by derivedStateOf { searchResults.isNotEmpty() }

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        searchQuery = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            if (newQuery.isBlank()) {
                searchResults = emptyList()
                currentSearchResultIndex = -1
                isSearchInProgress = false
                return@launch
            }
            delay(350)
            showSearchResultsPanel = true
            isSearchInProgress = true
            currentSearchResultIndex = -1
            searchResults = searcher(newQuery)
            isSearchInProgress = false
        }
    }

    fun forceSearch() {
        searchJob?.cancel()
        searchJob = scope.launch {
            if (searchQuery.isBlank()) {
                searchResults = emptyList()
                currentSearchResultIndex = -1
                isSearchInProgress = false
                return@launch
            }
            showSearchResultsPanel = true
            isSearchInProgress = true
            currentSearchResultIndex = -1
            searchResults = searcher(searchQuery)
            isSearchInProgress = false
        }
    }
}

@Composable
fun rememberSearchState(
    scope: CoroutineScope,
    searcher: suspend (String) -> List<SearchResult>
): SearchState {
    return remember {
        SearchState(scope, searcher)
    }
}

private val activeTooltipState = mutableStateOf<TooltipState?>(null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    description: String? = null,
    content: @Composable () -> Unit
) {
    val tooltipState = rememberTooltipState(isPersistent = true)
    rememberCoroutineScope()

    LaunchedEffect(tooltipState.isVisible) {
        if (tooltipState.isVisible) {
            val previous = activeTooltipState.value
            if (previous != null && previous !== tooltipState) {
                previous.dismiss()
            }
            activeTooltipState.value = tooltipState
        } else {
            if (activeTooltipState.value === tooltipState) {
                activeTooltipState.value = null
            }
        }
    }

    TooltipBox(
        positionProvider = if (description != null)
            TooltipDefaults.rememberRichTooltipPositionProvider()
        else
            TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (description != null) {
                RichTooltip(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            content()
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    },
                    colors = TooltipDefaults.richTooltipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                PlainTooltip {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        content()
                        Text(text)
                    }
                }
            }
        },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled
        ) {
            content()
        }
    }
}

@Composable
fun SearchTopBar(
    searchState: SearchState,
    focusRequester: FocusRequester,
    onCloseSearch: () -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(55.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TooltipIconButton(
                text = stringResource(R.string.tooltip_close_search),
                description = stringResource(R.string.tooltip_close_search_desc),
                onClick = onCloseSearch
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.tooltip_close_search)
                )
            }

            TextField(
                value = searchState.searchQuery,
                onValueChange = { searchState.onQueryChange(it) },
                placeholder = { Text(stringResource(R.string.search_in_book)) },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("SearchTextField"),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    searchState.forceSearch()
                    keyboardController?.hide()
                    focusManager.clearFocus()
                })
            )

            if (searchState.searchQuery.isNotEmpty()) {
                TooltipIconButton(
                    text = stringResource(R.string.tooltip_clear_search),
                    description = stringResource(R.string.tooltip_clear_search_desc),
                    onClick = { searchState.onQueryChange("") }
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.tooltip_clear_search)
                    )
                }
            }

            TooltipIconButton(
                text = if (searchState.showSearchResultsPanel)
                    stringResource(R.string.tooltip_hide_results)
                else
                    stringResource(R.string.tooltip_show_results),
                description = if (searchState.showSearchResultsPanel)
                    stringResource(R.string.tooltip_hide_results_desc)
                else
                    stringResource(R.string.tooltip_show_results_desc),
                onClick = {
                    searchState.showSearchResultsPanel = !searchState.showSearchResultsPanel
                    focusManager.clearFocus()
                }
            ) {
                Icon(
                    imageVector = if (searchState.showSearchResultsPanel) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(
                        if (searchState.showSearchResultsPanel) R.string.tooltip_hide_results
                        else R.string.tooltip_show_results
                    )
                )
            }
        }
    }
}

@Composable
fun SearchNavigationControls(
    searchState: SearchState,
    onNavigate: (Int) -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            TooltipIconButton(
                text = stringResource(R.string.tooltip_prev_result),
                description = stringResource(R.string.tooltip_prev_result_desc),
                onClick = { onNavigate(searchState.currentSearchResultIndex - 1) },
                enabled = searchState.currentSearchResultIndex > 0
            ) {
                Icon(Icons.Default.ArrowDropUp, contentDescription = stringResource(R.string.tooltip_prev_result))
            }

            Text(
                text = "${searchState.currentSearchResultIndex + 1}/${searchState.searchResultsCount}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            TooltipIconButton(
                text = stringResource(R.string.tooltip_next_result),
                description = stringResource(R.string.tooltip_next_result_desc),
                onClick = { onNavigate(searchState.currentSearchResultIndex + 1) },
                enabled = searchState.currentSearchResultIndex < searchState.searchResultsCount - 1
            ) {
                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.tooltip_next_result))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AiDefinitionPopup(
    word: String?,
    result: AiDefinitionResult?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    isMainTtsActive: Boolean = false,
    onOpenExternalDictionary: () -> Unit,
    getAuthToken: suspend () -> String?
) {
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    val context = LocalContext.current
    @Suppress("DEPRECATION") val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        onDispose {
            if (ttsState.playbackSource == "POPUP" && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 5.dp)
                .heightIn(min = 150.dp, max = 400.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(all = 20.dp)) {
                if (isLoading && (result?.definition.isNullOrBlank() && result?.error.isNullOrBlank())) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.ai_thinking), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (result != null) {
                    word?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    val definitionText = result.definition
                    val errorText = result.error

                    val styledContent = remember(definitionText, errorText) {
                        if (!definitionText.isNullOrBlank()) {
                            MarkdownParser.parse(definitionText)
                        } else {
                            AnnotatedString(errorText ?: "")
                        }
                    }

                    val textToUse = styledContent.text

                    if (textToUse.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            definitionText ?: errorText ?: ""
                            val isTtsSessionActive = ttsState.currentText != null || ttsState.isLoading

                            IconButton(
                                onClick = {
                                    if (isTtsSessionActive) {
                                        ttsController.stop()
                                    } else {
                                        val chunks = splitTextIntoChunks(textToUse).map {
                                            TtsChunk(it, "", -1)
                                        }
                                        if (chunks.isNotEmpty()) {
                                            scope.launch {
                                                val token = getAuthToken()
                                                ttsController.start(
                                                    chunks = chunks,
                                                    bookTitle = "AI Definition",
                                                    chapterTitle = word,
                                                    coverImageUri = null,
                                                    ttsMode = loadTtsMode(context),
                                                    playbackSource = "POPUP",
                                                    authToken = token
                                                )
                                            }
                                        }
                                    }
                                },
                                enabled = !isMainTtsActive || (ttsState.playbackSource == "POPUP")
                            ) {
                                Icon(
                                    imageVector = if (isTtsSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(if (isTtsSessionActive) R.string.action_stop else R.string.action_read_aloud)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                clipboardManager.setText(AnnotatedString(textToUse))
                            }) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onOpenExternalDictionary) {
                                Icon(
                                    painter = painterResource(id = R.drawable.dictionary),
                                    contentDescription = stringResource(R.string.content_desc_open_dictionary)
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (errorText != null && definitionText.isNullOrBlank()) {
                        Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    } else if (textToUse.isNotBlank()) {
                        val scrollState = rememberScrollState()
                        var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                        LaunchedEffect(ttsState.currentText, textLayoutResult) {
                            val currentChunk = ttsState.currentText
                            val layoutResult = textLayoutResult
                            if (!currentChunk.isNullOrBlank() && layoutResult != null) {
                                val startIndex = textToUse.indexOf(currentChunk)
                                if (startIndex != -1) {
                                    val line = layoutResult.getLineForOffset(startIndex)
                                    val lineTop = layoutResult.getLineTop(line)
                                    val viewportHeight = scrollState.viewportSize
                                    val targetScroll = (lineTop - viewportHeight / 2).coerceAtLeast(0f)
                                    scope.launch {
                                        scrollState.animateScrollTo(targetScroll.toInt())
                                    }
                                }
                            }
                        }

                        val annotatedText = buildAnnotatedString {
                            append(styledContent)
                            val currentChunk = ttsState.currentText
                            if (!currentChunk.isNullOrBlank()) {
                                val startIndex = textToUse.indexOf(currentChunk)
                                if (startIndex != -1) {
                                    addStyle(
                                        style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer),
                                        start = startIndex,
                                        end = startIndex + currentChunk.length
                                    )
                                }
                            }
                        }
                        Text(
                            text = annotatedText,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.verticalScroll(scrollState),
                            onTextLayout = { textLayoutResult = it }
                        )
                    } else {
                        Text(stringResource(R.string.ai_no_definition), style = MaterialTheme.typography.bodyLarge)
                    }
                } else if (word != null) {
                    Text(
                        text = stringResource(R.string.ai_asking_about, word),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(vertical = 24.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultsPanel(
    results: List<SearchResult>,
    isSearching: Boolean,
    onResultClick: (SearchResult) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when {
            isSearching -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            results.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.search_no_results_simple), style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                Column {
                    Text(
                        text = LocalContext.current.resources.getQuantityString(R.plurals.search_results_count, results.size, results.size),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.testTag("SearchResultsList")) {
                        items(results.size) { index ->
                            val result = results[index]
                            ListItem(
                                headlineContent = { Text(result.locationTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(result.snippet, style = MaterialTheme.typography.bodyMedium) },
                                modifier = Modifier
                                    .clickable { onResultClick(result) }
                                    .testTag("SearchResultItem_${result.locationInSource}")
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

suspend fun fetchAiDefinition(
    text: String,
    context: Context,
    authToken: String?,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (text.isBlank()) {
        onError(context.getString(R.string.error_text_empty))
        onFinish()
        return
    }
    Timber.d("Fetching AI definition for: '$text'")

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "oss") {
        if (BuildConfig.IS_OFFLINE) {
            onError(context.getString(R.string.error_network_check_connection))
            onFinish()
            return
        }
        val systemInstruction = "You are an AI-powered dictionary. Your goal is to provide a concise and easy-to-understand definition for the given word, phrase or paragraphs. Keep the explanation brief. Respond only with the definition text, without any preamble. Do not send your thoughts, only the final definition you arrived on. no emoji."
        callByokTextAi(
            context = context,
            feature = AiFeature.DEFINE,
            systemInstruction = systemInstruction,
            userPrompt = "Define: \"$text\"",
            temperature = 0.1,
            maxTokens = 256,
            onUpdate = onUpdate,
            onError = onError
        )
        onFinish()
        return
    }

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(aiDefinitionUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            connection.connectTimeout = 10000
            connection.readTimeout = 30000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply { put("text", text) }
            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == 402) {
                onError("INSUFFICIENT_CREDITS")
                onFinish()
                return@withContext
            }
            Timber.d("Definition: Got response code $responseCode")
            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Timber.d("Definition: Received line: $line")
                        try {
                            val jsonResponse = JSONObject(line!!)
                            jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                Timber.d("Definition: Parsed chunk, calling onUpdate.")
                                onUpdate(it)
                                hasReceivedData = true
                            }
                            jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                onError(it)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not parse stream line: $line")
                        }
                    }
                }
                Timber.d("Definition: Finished reading stream.")
                if (!hasReceivedData) {
                    onError(context.getString(R.string.error_ai_empty_definition))
                }
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                val errorDetail = try { errorBody?.let { JSONObject(it).getString("detail") } } catch (_: Exception) { "Could not get definition." }
                onError("${responseCode}. ${errorDetail ?: context.getString(R.string.error_unknown_server)}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error fetching AI definition: ${e.message}")
            onError(context.getString(R.string.error_network_check_connection))
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}

fun countWords(text: String): Int {
    return text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.size
}

private fun streamGeminiAiResponse(
    connection: HttpURLConnection,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean {
    var hasReceivedData = false
    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        var buffer = ""
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            buffer += line
            while (true) {
                val start = buffer.indexOf('{')
                if (start == -1) {
                    buffer = ""
                    break
                }
                var braceCount = 0
                var end = -1
                charLoop@ for (i in start until buffer.length) {
                    when (buffer[i]) {
                        '{' -> braceCount++
                        '}' -> {
                            braceCount--
                            if (braceCount == 0) {
                                end = i
                                break@charLoop
                            }
                        }
                    }
                }
                if (end == -1) break
                val jsonString = buffer.substring(start, end + 1)
                buffer = buffer.substring(end + 1)
                try {
                    val jsonResponse = JSONObject(jsonString)
                    jsonResponse.optJSONArray("candidates")
                        ?.optJSONObject(0)
                        ?.optJSONObject("content")
                        ?.optJSONArray("parts")
                        ?.optJSONObject(0)
                        ?.optString("text")
                        ?.takeIf { it.isNotEmpty() }
                        ?.let {
                            onUpdate(it)
                            hasReceivedData = true
                        }
                    if (jsonResponse.optJSONArray("candidates")?.optJSONObject(0)?.optString("finishReason") == "SAFETY") {
                        onError("Blocked for safety reasons.")
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Could not parse Gemini BYOK stream object")
                }
            }
        }
    }
    return hasReceivedData
}

private fun streamGroqAiResponse(
    connection: HttpURLConnection,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean {
    var hasReceivedData = false
    var inThink = false
    var thinkBuffer = ""

    fun cleanChunk(text: String): String {
        thinkBuffer += text
        val out = StringBuilder()
        while (true) {
            if (inThink) {
                val end = thinkBuffer.indexOf("</think>")
                if (end == -1) {
                    if (thinkBuffer.length > 7) thinkBuffer = thinkBuffer.takeLast(7)
                    break
                }
                inThink = false
                thinkBuffer = thinkBuffer.substring(end + 8)
            } else {
                val start = thinkBuffer.indexOf("<think>")
                if (start == -1) {
                    if (thinkBuffer.length > 6) {
                        out.append(thinkBuffer.dropLast(6))
                        thinkBuffer = thinkBuffer.takeLast(6)
                    }
                    break
                }
                out.append(thinkBuffer.substring(0, start))
                inThink = true
                thinkBuffer = thinkBuffer.substring(start + 7)
            }
        }
        return out.toString()
    }

    connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (!trimmed.startsWith("data: ")) continue
            val data = trimmed.removePrefix("data: ").trim()
            if (data == "[DONE]") continue
            try {
                val chunk = JSONObject(data)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?.optString("content")
                    .orEmpty()
                val cleaned = cleanChunk(chunk)
                if (cleaned.isNotEmpty()) {
                    onUpdate(cleaned)
                    hasReceivedData = true
                }
            } catch (e: Exception) {
                Timber.w(e, "Could not parse Groq BYOK stream line")
            }
        }
    }
    if (!inThink && thinkBuffer.isNotBlank()) {
        onUpdate(thinkBuffer)
        hasReceivedData = true
    }
    return hasReceivedData
}

private fun buildGroqPayload(
    model: String,
    systemInstruction: String,
    userPrompt: String,
    temperature: Double,
    maxTokens: Int
): JSONObject {
    return JSONObject().apply {
        put("model", model)
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemInstruction)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userPrompt)
            })
        })
        put("temperature", temperature)
        put("top_p", 0.95)
        put("max_tokens", maxTokens)
        put("stream", true)
        if (model.contains("qwen")) put("reasoning_effort", "none")
    }
}

suspend fun callByokTextAi(
    context: Context,
    feature: AiFeature,
    systemInstruction: String,
    userPrompt: String,
    temperature: Double,
    maxTokens: Int,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val settings = loadAiByokSettings(context)
    val model = aiModelById(settings.modelIdFor(feature))
    if (model == null) {
        onError("Choose a model for ${feature.displayName()} in AI key and model settings.")
        return@withContext false
    }
    val apiKey = settings.apiKeyFor(model.provider)
    if (apiKey.isBlank()) {
        onError("Add a ${model.provider.replaceFirstChar { it.titlecase(Locale.ROOT) }} API key in AI key and model settings.")
        return@withContext false
    }

    var connection: HttpURLConnection? = null
    try {
        val url = if (model.provider == "groq") {
            URL("https://api.groq.com/openai/v1/chat/completions")
        } else {
            URL("https://generativelanguage.googleapis.com/v1beta/models/${model.name}:streamGenerateContent?key=$apiKey")
        }
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        if (model.provider == "groq") {
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
        }
        connection.connectTimeout = 15000
        connection.readTimeout = 120000
        connection.doOutput = true
        connection.doInput = true

        val payload = if (model.provider == "groq") {
            buildGroqPayload(model.name, systemInstruction, userPrompt, temperature, maxTokens)
        } else {
            JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", userPrompt) }))
                }))
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", temperature)
                    put("topP", 0.95)
                    put("topK", 40)
                    put("maxOutputTokens", maxTokens)
                    put("response_mime_type", "text/plain")
                    if (feature == AiFeature.DEFINE && model.name.startsWith("gemini")) {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                    }
                })
            }
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val hasData = if (model.provider == "groq") {
                streamGroqAiResponse(connection, onUpdate, onError)
            } else {
                streamGeminiAiResponse(connection, onUpdate, onError)
            }
            if (!hasData) onError("The AI provider returned an empty response.")
            hasData
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
            onError("AI provider error: $responseCode. ${errorBody.orEmpty().take(300)}")
            false
        }
    } catch (e: Exception) {
        Timber.e(e, "BYOK AI request failed")
        onError(context.getString(R.string.error_network_check_connection))
        false
    } finally {
        connection?.disconnect()
    }
}

suspend fun callByokGeminiInlineAi(
    context: Context,
    feature: AiFeature,
    mimeType: String,
    base64Data: String,
    systemInstruction: String,
    temperature: Double,
    maxTokens: Int,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit
): Boolean = withContext(Dispatchers.IO) {
    val settings = loadAiByokSettings(context)
    val model = aiModelById(settings.modelIdFor(feature))
    if (model == null) {
        onError("Choose a model for ${feature.displayName()} in AI key and model settings.")
        return@withContext false
    }
    if (model.provider != "gemini") {
        onError("This summary needs a Gemini model because the selected Groq models do not support PDF/image input.")
        return@withContext false
    }
    val apiKey = settings.geminiKey.trim()
    if (apiKey.isBlank()) {
        onError("Add a Gemini API key in AI key and model settings.")
        return@withContext false
    }

    var connection: HttpURLConnection? = null
    try {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/${model.name}:streamGenerateContent?key=$apiKey")
        connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.connectTimeout = 15000
        connection.readTimeout = 180000
        connection.doOutput = true
        connection.doInput = true

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mime_type", mimeType)
                        put("data", base64Data)
                    })
                }))
            }))
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", temperature)
                put("topP", 0.95)
                put("topK", 40)
                put("maxOutputTokens", maxTokens)
                put("response_mime_type", "text/plain")
            })
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
        val responseCode = connection.responseCode
        if (responseCode == HttpURLConnection.HTTP_OK) {
            val hasData = streamGeminiAiResponse(connection, onUpdate, onError)
            if (!hasData) onError("The AI provider returned an empty response.")
            hasData
        } else {
            val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
            onError("AI provider error: $responseCode. ${errorBody.orEmpty().take(300)}")
            false
        }
    } catch (e: Exception) {
        Timber.e(e, "BYOK inline AI request failed")
        onError(context.getString(R.string.error_network_check_connection))
        false
    } finally {
        connection?.disconnect()
    }
}

object MarkdownParser {
    fun parse(markdown: String): AnnotatedString {
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val builder = AnnotatedString.Builder()

        val visitor = object : AbstractVisitor() {
            override fun visit(text: Text) {
                builder.append(text.literal)
            }

            override fun visit(emphasis: Emphasis) {
                builder.pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                visitChildren(emphasis)
                builder.pop()
            }

            override fun visit(strongEmphasis: StrongEmphasis) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                visitChildren(strongEmphasis)
                builder.pop()
            }

            override fun visit(paragraph: Paragraph) {
                visitChildren(paragraph)
                // Add newline if it's not the last node
                if (paragraph.next != null) {
                    builder.append("\n\n")
                }
            }

            override fun visit(heading: Heading) {
                builder.pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                visitChildren(heading)
                builder.pop()
                builder.append("\n\n")
            }

            override fun visit(softLineBreak: SoftLineBreak) {
                builder.append(" ")
            }

            override fun visit(hardLineBreak: HardLineBreak) {
                builder.append("\n")
            }

            override fun visit(code: Code) {
                builder.pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x22888888)))
                builder.append(code.literal)
                builder.pop()
            }

            override fun visit(listItem: ListItem) {
                builder.append("• ")
                visitChildren(listItem)
                if (listItem.next != null) {
                    builder.append("\n")
                }
            }
        }

        document.accept(visitor)
        return builder.toAnnotatedString()
    }
}

suspend fun fetchRecap(
    pastSummaries: List<String>,
    currentText: String,
    context: Context,
    authToken: String?,
    onUpdate: (String) -> Unit,
    onCostReceived: (Double) -> Unit = {},
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (pastSummaries.isEmpty() && currentText.isBlank()) {
        onError(context.getString(R.string.error_not_enough_context))
        onFinish()
        return
    }

    @Suppress("KotlinConstantConditions")
    if (BuildConfig.FLAVOR == "oss") {
        if (BuildConfig.IS_OFFLINE) {
            onError(context.getString(R.string.error_network_recap))
            onFinish()
            return
        }
        val systemInstruction = "You are a sophisticated reading assistant. You have to create a recap. Synthesize the provided past context and current chapter text into a cohesive summary of the reading session so far. Conclude exactly where the user is positioned currently. Do not add a preamble. Also Avoid including or mentioning text from administrative or boilerplate sections such as the introduction, copyright pages, preface, or table of contents; focus strictly on the core story or informative content. If the the book has multiple different short stories that came before then summarize them too, its a recap of the whole book up to this point."
        val promptContext = buildString {
            append("--- PREVIOUS CONTEXT (Summaries of read chapters) ---\n")
            if (pastSummaries.isEmpty()) {
                append("(None - User is in the first chapter)\n")
            } else {
                pastSummaries.forEachIndexed { index, summary ->
                    append("Chapter ${index + 1}: $summary\n\n")
                }
            }
            append("\n--- CURRENT SESSION (Text read in current chapter) ---\n")
            append(currentText)
            append("\n\nBased strictly on the above, provide a recap of the content read so far.")
        }
        callByokTextAi(
            context = context,
            feature = AiFeature.RECAP,
            systemInstruction = systemInstruction,
            userPrompt = promptContext,
            temperature = 0.3,
            maxTokens = 4096,
            onUpdate = onUpdate,
            onError = onError
        )
        onFinish()
        return
    }

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(recapUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            if (authToken != null) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply {
                put("past_summaries", JSONArray(pastSummaries))
                put("current_text", currentText)
            }

            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode == 402) {
                onError("INSUFFICIENT_CREDITS")
                onFinish()
                return@withContext
            }
            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        try {
                            val jsonResponse = JSONObject(line!!)

                            jsonResponse.optDouble("cost_deducted", -1.0).takeIf { it > -1.0 }?.let { onCostReceived(it) }

                            jsonResponse.optString("chunk").takeIf { it.isNotEmpty() }?.let {
                                onUpdate(it)
                                hasReceivedData = true
                            }
                            jsonResponse.optString("error").takeIf { it.isNotEmpty() }?.let {
                                onError(it)
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Could not parse stream line: $line")
                        }
                    }
                }
                if (!hasReceivedData) onError(context.getString(R.string.error_parse_recap))
            } else {
                val errorBody = try { connection.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                onError("${responseCode}. ${errorBody ?: ""}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Recap error: ${e.message}")
            onError(context.getString(R.string.error_network_recap))
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsSettingsSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    currentMode: TtsPlaybackManager.TtsMode,
    onModeChange: (TtsPlaybackManager.TtsMode) -> Unit,
    currentSpeakerId: String,
    onSpeakerChange: (String) -> Unit,
    isTtsActive: Boolean,
    getAuthToken: suspend () -> String?,
    bookTitle: String
) {
    if (!isVisible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val context = LocalContext.current
    val isOss = BuildConfig.FLAVOR == "oss"
    val isOssCloudAvailable = isByokCloudTtsAvailable(context)
    var selectedTabIndex by remember(currentMode, isOssCloudAvailable) {
        mutableIntStateOf(if (currentMode == TtsPlaybackManager.TtsMode.CLOUD && (!isOss || isOssCloudAvailable)) 0 else 1)
    }
    val scope = rememberCoroutineScope()
    val samplePlayer = remember(context, scope) {
        SpeakerSamplePlayer(context, scope, getAuthToken = getAuthToken)
    }

    DisposableEffect(Unit) { onDispose { samplePlayer.release() } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.tts_settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

            if (isTtsActive) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Stop, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.tts_stop_to_change_settings), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            if (isOss && !isOssCloudAvailable) {
                Spacer(Modifier.height(16.dp))
                DeviceVoicesTab(isTtsActive, context, TtsPlaybackManager.TtsMode.BASE)
            } else {
                Text(stringResource(R.string.tts_active_engine), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth().height(48.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp)).padding(4.dp)) {
                    val modes = listOf(
                        TtsPlaybackManager.TtsMode.CLOUD to stringResource(R.string.tts_mode_cloud_ai),
                        TtsPlaybackManager.TtsMode.BASE to stringResource(R.string.tts_mode_device_native)
                    )
                    modes.forEach { (mode, title) ->
                        val isSelected = currentMode == mode
                        Box(
                            modifier = Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(20.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable(enabled = !isTtsActive) {
                                    onModeChange(mode)
                                    if (mode == TtsPlaybackManager.TtsMode.CLOUD && selectedTabIndex == 1) selectedTabIndex = 0
                                    if (mode == TtsPlaybackManager.TtsMode.BASE && selectedTabIndex != 1) selectedTabIndex = 1
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(title, color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, divider = {}) {
                    Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text(stringResource(R.string.tts_tab_cloud_voices), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text(stringResource(R.string.tts_tab_device_voices), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                    Tab(selected = selectedTabIndex == 2, onClick = { selectedTabIndex = 2 }, text = { Text(stringResource(R.string.tts_tab_cloud_cache), maxLines = 1, overflow = TextOverflow.Ellipsis) })
                }

                Spacer(Modifier.height(16.dp))

                when (selectedTabIndex) {
                    0 -> AiVoicesTab(currentSpeakerId, onSpeakerChange, isTtsActive, samplePlayer, currentMode)
                    1 -> DeviceVoicesTab(isTtsActive, context, currentMode)
                    2 -> TtsCacheTab(bookTitle, context, currentSpeakerId)
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun AiVoicesTab(
    currentSpeakerId: String,
    onSpeakerChange: (String) -> Unit,
    isTtsActive: Boolean,
    samplePlayer: SpeakerSamplePlayer,
    currentMode: TtsPlaybackManager.TtsMode
) {
    LocalContext.current
    val isCloudMode = currentMode == TtsPlaybackManager.TtsMode.CLOUD

    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.tts_select_cloud_voice), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (samplePlayer.cachedSpeakers.isNotEmpty()) {
            TextButton(onClick = { samplePlayer.clearSamples() }, modifier = Modifier.heightIn(min = 24.dp)) {
                Text(stringResource(R.string.tts_clear_samples), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))) {
        items(GEMINI_TTS_SPEAKERS.size) { index ->
            val voice = GEMINI_TTS_SPEAKERS[index]
            val isSelected = currentSpeakerId == voice.id
            val isCached = samplePlayer.cachedSpeakers.contains(voice.id)

            ListItem(
                headlineContent = { Text(voice.name, fontWeight = if (isSelected && isCloudMode) FontWeight.Bold else FontWeight.Normal) },
                supportingContent = { Text(voice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                leadingContent = {
                    if (isSelected && isCloudMode) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Default.Cloud, null, tint = Color.Gray)
                    }
                },
                trailingContent = {
                    if (!isTtsActive) {
                        IconButton(onClick = { samplePlayer.playOrStop(voice.id) }) {
                            if (samplePlayer.loadingSpeakerId == voice.id) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(
                                    if (samplePlayer.playingSpeakerId == voice.id) Icons.Default.Stop
                                    else if (isCached) Icons.Default.PlayCircle
                                    else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                modifier = Modifier.clickable(enabled = !isTtsActive && isCloudMode) { onSpeakerChange(voice.id) },
                colors = ListItemDefaults.colors(
                    containerColor = if (isSelected && isCloudMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                )
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceVoicesTab(
    isTtsActive: Boolean,
    context: Context,
    currentMode: TtsPlaybackManager.TtsMode
) {
    var savedVoiceName by remember { mutableStateOf(loadNativeVoice(context)) }
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var allVoices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var isTtsLoading by remember { mutableStateOf(true) }

    val allLanguagesLabel = stringResource(R.string.filter_all)
    var selectedLanguage by remember { mutableStateOf(allLanguagesLabel) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                allVoices = ttsEngine?.voices?.toList()?.sortedBy { it.locale.displayName } ?: emptyList()
                isTtsLoading = false
            }
        }
        ttsEngine = tts
        onDispose { tts.shutdown() }
    }

    if (isTtsLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val languages = remember(allVoices) {
        val list = listOf(allLanguagesLabel) + allVoices.map { it.locale.displayLanguage }.filter { it.isNotBlank() }.distinct().sorted()
        Timber.tag("TTS_DIAGNOSE").d("Languages list updated: size=${list.size}, items=$list")
        list
    }

    val filteredVoices = remember(allVoices, selectedLanguage) {
        if (selectedLanguage == allLanguagesLabel) allVoices
        else allVoices.filter { it.locale.displayLanguage == selectedLanguage }
    }

    val isBaseMode = currentMode == TtsPlaybackManager.TtsMode.BASE

    Surface(
        color = if (isBaseMode && savedVoiceName == null) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .clickable(enabled = !isTtsActive && isBaseMode) {
                savedVoiceName = null
                saveNativeVoice(context, null)
                ttsEngine?.apply {
                    try {
                        val defaultLocale = Locale.getDefault()
                        language = defaultLocale
                        val fallbackVoice =
                            defaultVoice ?: voices.firstOrNull { voice ->
                                voice.locale == defaultLocale && !voice.isNetworkConnectionRequired
                            } ?: voices.firstOrNull { voice ->
                                voice.locale == defaultLocale
                            }
                        fallbackVoice?.let { voice = it }
                    } catch (e: Exception) {
                        Timber.tag("TTS_DIAGNOSE").w(e, "Failed to reset preview engine to system default voice")
                    }
                }
            }
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Smartphone,
                null,
                tint = if (isBaseMode && savedVoiceName == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.tts_system_default_voice), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.tts_uses_device_settings), style = MaterialTheme.typography.bodySmall)
            }
            if (isBaseMode && savedVoiceName == null) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = languageMenuExpanded,
        onExpandedChange = { if (!isTtsActive) languageMenuExpanded = it },
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = selectedLanguage,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.tts_language_filter)) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageMenuExpanded) },
            colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = !isTtsActive
        )
        ExposedDropdownMenu(
            expanded = languageMenuExpanded,
            onDismissRequest = { languageMenuExpanded = false }
        ) {
            languages.forEach { lang ->
                Timber.tag("TTS_DIAGNOSE").d("Rendering Language DropdownMenuItem: '$lang'")
                DropdownMenuItem(
                    text = {
                        Text(text = lang)
                    },
                    onClick = {
                        selectedLanguage = lang
                        languageMenuExpanded = false
                        Timber.tag("TTS_DIAGNOSE").d("Language selected: $lang")
                    }
                )
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
        items(filteredVoices.size) { index ->
            val voice = filteredVoices[index]
            val isSelected = isBaseMode && voice.name == savedVoiceName

            ListItem(
                headlineContent = { Text(voice.locale.displayName, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                supportingContent = { Text(if (voice.isNetworkConnectionRequired) stringResource(R.string.tts_online) else stringResource(R.string.tts_offline)) },
                leadingContent = {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    } else {
                        Spacer(Modifier.size(24.dp))
                    }
                },
                modifier = Modifier.clickable(enabled = !isTtsActive && isBaseMode) {
                    savedVoiceName = voice.name
                    saveNativeVoice(context, voice.name)
                },
                colors = ListItemDefaults.colors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(0.2f) else Color.Transparent),
                trailingContent = {
                    IconButton(
                        enabled = !isTtsActive,
                        onClick = {
                            ttsEngine?.apply {
                                this.voice = voice
                                speak(context.getString(R.string.tts_voice_sample_generic), TextToSpeech.QUEUE_FLUSH, null, "sample_${voice.name}")
                            }
                        }
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.tts_play_sample), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsCacheTab(bookTitle: String, context: Context, currentSpeakerId: String) {
    val cacheManager = remember { TtsCacheManager(context) }
    var selectedSpeakerFilter by remember { mutableStateOf(currentSpeakerId) }
    var filterMenuExpanded by remember { mutableStateOf(false) }

    val allSpeakers = remember(bookTitle) {
        val fromCache = cacheManager.getBookCacheDir(bookTitle).listFiles()?.flatMap { ch ->
            ch.listFiles()?.mapNotNull { file ->
                val parts = file.name.split("_")
                if (parts.size >= 5) parts[3] else null
            } ?: emptyList()
        }?.distinct()?.sorted() ?: emptyList()
        val list = (listOf(currentSpeakerId) + fromCache).distinct()
        Timber.tag("TTS_DIAGNOSE").d("AllSpeakers list updated: size=${list.size}, items=$list")
        list
    }

    var chapters by remember(selectedSpeakerFilter) { mutableStateOf(cacheManager.getChapterCaches(bookTitle, selectedSpeakerFilter)) }
    val totalSize = remember(chapters) { chapters.sumOf { it.sizeBytes } }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tts_tab_cloud_cache), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(formatBytes(totalSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }

        if (allSpeakers.isNotEmpty()) {
            androidx.compose.material3.ExposedDropdownMenuBox(
                expanded = filterMenuExpanded,
                onExpandedChange = { filterMenuExpanded = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                OutlinedTextField(
                    value = selectedSpeakerFilter,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.tts_voice_filter)) },
                    trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = filterMenuExpanded) },
                    colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = filterMenuExpanded,
                    onDismissRequest = { filterMenuExpanded = false }
                ) {
                    allSpeakers.forEach { spkr ->
                        Timber.tag("TTS_DIAGNOSE").d("Rendering Voice DropdownMenuItem: '$spkr'")
                        DropdownMenuItem(
                            text = {
                                Text(text = spkr)
                            },
                            onClick = {
                                selectedSpeakerFilter = spkr
                                filterMenuExpanded = false
                                Timber.tag("TTS_DIAGNOSE").d("Voice filter selected: $spkr")
                            }
                        )
                    }
                }
            }
        }

        if (chapters.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tts_no_audio_cached_for_voice), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))) {
                items(chapters.size) { index ->
                    val chapter = chapters[index]
                    ListItem(
                        headlineContent = {
                            Text(
                                "${chapter.chapterTitle} ${
                                    context.resources.getQuantityString(
                                        R.plurals.tts_cache_chunk_count_parenthetical,
                                        chapter.chunkCount,
                                        chapter.chunkCount
                                    )
                                }",
                                fontWeight = FontWeight.Medium
                            )
                        },
                        supportingContent = { Text(formatBytes(chapter.sizeBytes)) },
                        trailingContent = {
                            IconButton(onClick = {
                                cacheManager.deleteSpecificFiles(chapter.matchingFiles, chapter.directory)
                                chapters = cacheManager.getChapterCaches(bookTitle, selectedSpeakerFilter)
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete), tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    chapters.forEach { cacheManager.deleteSpecificFiles(it.matchingFiles, it.directory) }
                    chapters = cacheManager.getChapterCaches(bookTitle, selectedSpeakerFilter)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.tts_clear_cache_for_voice, selectedSpeakerFilter))
            }
        }
    }
}

fun loadNativeVoice(context: Context): String? {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_NATIVE_TTS_VOICE, null)
}

private fun saveNativeVoice(context: Context, @Suppress("SameParameterValue") voiceName: String?) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    if (voiceName == null) {
        prefs.edit { remove(PREF_NATIVE_TTS_VOICE) }
    } else {
        prefs.edit { putString(PREF_NATIVE_TTS_VOICE, voiceName) }
    }
}

@Composable
fun SpectrumBox(
    hue: Float,
    saturation: Float,
    currentColor: Color,
    onHueSatChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val rainbowColors = listOf(
        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
    )
    val touchPadding = 12.dp

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()

                val paddingPx = touchPadding.toPx()
                val activeWidth = size.width.toFloat() - (paddingPx * 2)
                val activeHeight = size.height.toFloat() - (paddingPx * 2)

                fun update(offset: Offset) {
                    val relativeX = offset.x - paddingPx
                    val relativeY = offset.y - paddingPx

                    val h = (relativeX / activeWidth).coerceIn(0f, 1f) * 360f
                    val s = (relativeY / activeHeight).coerceIn(0f, 1f)
                    onHueSatChanged(h, s)
                }

                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(touchPadding)
                .clip(RoundedCornerShape(12.dp))
        ) {
            drawRect(
                brush = Brush.horizontalGradient(rainbowColors)
            )
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.White, Color.White.copy(alpha = 0f))
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val paddingPx = touchPadding.toPx()
            val activeWidth = size.width - (paddingPx * 2)
            val activeHeight = size.height - (paddingPx * 2)

            val x = paddingPx + (hue / 360f) * activeWidth
            val y = paddingPx + saturation * activeHeight

            val pointerRadius = 10.dp.toPx()
            val strokeWidth = 2.dp.toPx()

            drawCircle(
                color = Color.Black.copy(alpha = 0.25f),
                radius = pointerRadius + 1.dp.toPx(),
                center = Offset(x, y + 1.dp.toPx())
            )

            drawCircle(
                color = currentColor.copy(alpha = 1f),
                radius = pointerRadius,
                center = Offset(x, y)
            )

            drawCircle(
                color = Color.White,
                radius = pointerRadius,
                center = Offset(x, y),
                style = Stroke(width = strokeWidth)
            )
        }
    }
}

@Composable
fun BrightnessSlider(
    hue: Float,
    saturation: Float,
    value: Float,
    onValueChanged: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val baseColor = remember(hue, saturation) {
        Color.hsv(hue, saturation, 1f)
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown()
                fun update(offset: Offset) {
                    val v = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onValueChanged(v)
                }
                update(down.position)
                drag(down.id) { change ->
                    change.consume()
                    update(change.position)
                }
            }
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, baseColor)
                )
            )

            val x = value * size.width
            drawCircle(
                color = Color.White,
                radius = 8.dp.toPx(),
                center = Offset(x, size.height / 2)
            )
        }
    }
}

@Composable
fun RgbInputColumn(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val intValue = (value * 255).roundToInt()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            color = Color.Gray,
            fontSize = 11.sp,
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        RgbInput(value = intValue, onValueChange = onValueChange)
    }
}

@Composable
fun RgbInput(
    value: Int,
    onValueChange: (Float) -> Unit
) {
    var text by remember(value) { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        text = value.toString()
    }

    BasicTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.length <= 3 && newText.all { it.isDigit() }) {
                val intVal = newText.toIntOrNull()
                if (intVal != null) {
                    onValueChange(intVal.coerceIn(0, 255) / 255f)
                }
            }
        },
        textStyle = TextStyle(
            color = Color.White,
            textAlign = TextAlign.Center,
            fontSize = 13.sp
        ),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(vertical = 9.dp)
    )
}

@Composable
fun HexInput(
    color: Color,
    onHexChanged: (Color) -> Unit
) {
    val hexValue = remember(color) {
        String.format("%06X", (0xFFFFFF and color.toArgb()))
    }
    var text by remember(hexValue) { mutableStateOf(hexValue) }

    LaunchedEffect(color) {
        val currentParsed = try {
            Color(("#$text").toColorInt())
        } catch (_: Exception) {
            null
        }
        if (currentParsed?.toArgb() != color.toArgb()) {
            text = String.format("%06X", (0xFFFFFF and color.toArgb()))
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .background(Color(0xFF3E3E3E), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = "#",
            color = Color.Gray,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        BasicTextField(
            value = text,
            onValueChange = { newText ->
                if (newText.length <= 6) {
                    val uppercased = newText.uppercase()
                    if (uppercased.all { it.isDigit() || it in 'A'..'F' }) {
                        text = uppercased
                        if (uppercased.length == 6) {
                            try {
                                val parsedColorInt = "#$uppercased".toColorInt()
                                val newColor = Color(parsedColorInt)
                                onHexChanged(newColor)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
            },
            textStyle = TextStyle(
                color = Color.White,
                textAlign = TextAlign.Start,
                fontSize = 13.sp
            ),
            singleLine = true,
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier
                .padding(start = 2.dp)
                .width(50.dp)
        )
    }
}

@Composable
fun ColorComparePill(
    oldColor: Color,
    newColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp))) {
        drawRect(
            color = oldColor.copy(alpha = 1f),
            size = Size(size.width / 2, size.height)
        )
        drawRect(
            color = newColor.copy(alpha = 1f),
            topLeft = Offset(size.width / 2, 0f),
            size = Size(size.width / 2, size.height)
        )
    }
}

enum class ReaderTexture(val id: String, val displayName: String, val assetPath: String? = null, val resId: Int? = null) {
    NATURAL_WHITE("asset:ep_naturalwhite.webp", "Natural White", "textures/ep_naturalwhite.webp"),
    NATURAL_BLACK("asset:ep_naturalblack.webp", "Natural Black", "textures/ep_naturalblack.webp"),
    LIGHT_VENEER("asset:light-veneer.webp", "Light Veneer", "textures/light-veneer.webp"),
    RETINA_WOOD("asset:retina_wood.webp", "Retina Wood", "textures/retina_wood.webp"),
    GREY_WASH("asset:grey_wash_wall.webp", "Grey Wash", "textures/grey_wash_wall.webp"),
    CLASSY_FABRIC("asset:classy_fabric.webp", "Classy Fabric", "textures/classy_fabric.webp"),
    RETRO_INTRO("asset:retro_intro.webp", "Retro Intro", "textures/retro_intro.webp"),
    PAPER("paper", "Paper", resId = R.drawable.texture_paper),
    CANVAS("canvas", "Canvas", resId = R.drawable.texture_canvas),
    EINK("eink", "E-Ink", resId = R.drawable.texture_eink),
    SLATE("slate", "Slate", resId = R.drawable.texture_slate)
}

private const val TEXTURE_FILE_PREFIX = "file:"
private const val READER_TEXTURE_DIR = "reader_textures"

fun readerTextureDisplayName(textureId: String?): String {
    if (textureId == null) return "None"
    return ReaderTexture.entries.find { it.id == textureId }?.displayName
        ?: File(textureId.removePrefix(TEXTURE_FILE_PREFIX)).nameWithoutExtension.ifBlank { "Custom Image" }
}

fun importReaderTexture(context: Context, uri: Uri): String? {
    return try {
        val extension = context.contentResolver.getType(uri)
            ?.substringAfterLast('/')
            ?.lowercase(Locale.ROOT)
            ?.let {
                when (it) {
                    "jpeg", "jpg" -> "jpg"
                    "png", "webp", "gif", "bmp" -> it
                    else -> null
                }
            } ?: uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "gif", "bmp") }
            ?: "img"
        val dir = File(context.filesDir, READER_TEXTURE_DIR).apply { mkdirs() }
        val output = File(dir, "texture_${System.currentTimeMillis()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            output.outputStream().use { out -> input.copyTo(out) }
        } ?: return null
        TEXTURE_FILE_PREFIX + output.absolutePath
    } catch (e: Exception) {
        Timber.e(e, "Failed to import reader texture")
        null
    }
}

fun loadReaderTextureBitmap(context: Context, textureId: String?): ImageBitmap? {
    if (textureId == null) return null
    return try {
        val bitmap = if (textureId.startsWith(TEXTURE_FILE_PREFIX)) {
            BitmapFactory.decodeFile(textureId.removePrefix(TEXTURE_FILE_PREFIX))
        } else {
            val texture = ReaderTexture.entries.find { it.id == textureId } ?: return null
            when {
                texture.assetPath != null -> context.assets.open(texture.assetPath).use(BitmapFactory::decodeStream)
                texture.resId != null -> BitmapFactory.decodeResource(context.resources, texture.resId)
                else -> null
            }
        }
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        Timber.e(e, "Failed to load reader texture bitmap: $textureId")
        null
    }
}

fun getReaderTextureDataUri(context: Context, textureId: String?): String? {
    if (textureId == null) return null
    return try {
        var mimeType = "image/png"
        val bytes = if (textureId.startsWith(TEXTURE_FILE_PREFIX)) {
            val file = File(textureId.removePrefix(TEXTURE_FILE_PREFIX))
            mimeType = imageMimeTypeForExtension(file.extension)
            file.readBytes()
        } else {
            val texture = ReaderTexture.entries.find { it.id == textureId } ?: return null
            when {
                texture.assetPath != null -> {
                    mimeType = imageMimeTypeForExtension(texture.assetPath.substringAfterLast('.', "png"))
                    context.assets.open(texture.assetPath).use { it.readBytes() }
                }
                texture.resId != null -> {
                    val bitmap = BitmapFactory.decodeResource(context.resources, texture.resId)
                    ByteArrayOutputStream().use { out ->
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                        out.toByteArray()
                    }
                }
                else -> null
            }
        } ?: return null
        "data:$mimeType;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    } catch (e: Exception) {
        Timber.e(e, "Failed to encode reader texture: $textureId")
        null
    }
}

private fun imageMimeTypeForExtension(extension: String): String {
    return when (extension.lowercase(Locale.ROOT)) {
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        else -> "image/png"
    }
}

data class ReaderTheme(
    val id: String,
    val name: String,
    val backgroundColor: Color,
    val textColor: Color,
    val isDark: Boolean,
    val textureId: String? = null,
    val isCustom: Boolean = false
)

val BuiltInThemes = listOf(
    ReaderTheme("system", "System", Color.Unspecified, Color.Unspecified, false),
    ReaderTheme("light", "Light", Color(0xFFFFFFFF), Color(0xFF000000), false),
    ReaderTheme("dark", "Dark", Color(0xFF121212), Color(0xFFE0E0E0), true),
    ReaderTheme("sepia", "Sepia", Color(0xFFFBF0D9), Color(0xFF5F4B32), false),
    ReaderTheme("slate", "Slate", Color(0xFF2E3440), Color(0xFFECEFF4), true),
    ReaderTheme("oled", "OLED", Color(0xFF000000), Color(0xFFB0B0B0), true),
    ReaderTheme("natural_white_texture", "Natural White", Color(0xFFF7F1E5), Color(0xFF1D1B18), false, textureId = ReaderTexture.NATURAL_WHITE.id),
    ReaderTheme("retina_texture", "Retina", Color(0xFFF1E4CD), Color(0xFF2A2119), false, textureId = ReaderTexture.RETINA_WOOD.id),
    ReaderTheme("veneer_texture", "Veneer", Color(0xFFF4E7CF), Color(0xFF2A2119), false, textureId = ReaderTexture.LIGHT_VENEER.id),
    ReaderTheme("grey_wash_texture", "Grey Wash", Color(0xFF202124), Color(0xFFFFFFFF), true, textureId = ReaderTexture.GREY_WASH.id),
    ReaderTheme("fabric_texture", "Fabric", Color(0xFF262626), Color(0xFFE8E2D8), true, textureId = ReaderTexture.CLASSY_FABRIC.id),
    ReaderTheme("retro_texture", "Retro", Color(0xFFF6ECD8), Color(0xFF2F2118), false, textureId = ReaderTexture.RETRO_INTRO.id)
)

fun saveReaderThemeId(context: Context, themeId: String) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putString(PREF_READER_THEME, themeId) }
}

fun loadReaderThemeId(context: Context): String {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getString(PREF_READER_THEME, "system") ?: "system"
}

const val PREF_GLOBAL_TEXTURE_TRANSPARENCY = "global_texture_transparency"

fun saveGlobalTextureTransparency(context: Context, transparency: Float) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putFloat(PREF_GLOBAL_TEXTURE_TRANSPARENCY, transparency) }
}

fun loadGlobalTextureTransparency(context: Context): Float {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getFloat(PREF_GLOBAL_TEXTURE_TRANSPARENCY, 0f)
}

fun getImportedTextures(context: Context): List<String> {
    return try {
        val dir = File(context.filesDir, READER_TEXTURE_DIR)
        if (!dir.exists()) emptyList()
        else dir.listFiles()?.map { TEXTURE_FILE_PREFIX + it.absolutePath } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

const val PREF_EXCLUDE_IMAGES = "exclude_images"

fun saveExcludeImages(context: Context, excludeImages: Boolean) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    prefs.edit { putBoolean(PREF_EXCLUDE_IMAGES, excludeImages) }
}

fun loadExcludeImages(context: Context): Boolean {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean(PREF_EXCLUDE_IMAGES, false)
}

fun saveCustomThemes(context: Context, themes: List<ReaderTheme>) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val jsonArray = JSONArray()
    themes.filter { it.isCustom }.forEach { theme ->
        val obj = JSONObject().apply {
            put("id", theme.id)
            put("name", theme.name)
            put("bgColor", theme.backgroundColor.toArgb())
            put("textColor", theme.textColor.toArgb())
            put("isDark", theme.isDark)
            theme.textureId?.let { put("textureId", it) }
        }
        jsonArray.put(obj)
    }
    prefs.edit { putString(PREF_CUSTOM_THEMES, jsonArray.toString()) }
}

fun loadCustomThemes(context: Context): List<ReaderTheme> {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    val jsonString = prefs.getString(PREF_CUSTOM_THEMES, "[]") ?: "[]"
    val themes = mutableListOf<ReaderTheme>()
    try {
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            themes.add(
                ReaderTheme(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    backgroundColor = Color(obj.getInt("bgColor")),
                    textColor = Color(obj.getInt("textColor")),
                    isDark = obj.getBoolean("isDark"),
                    textureId = if (obj.has("textureId")) obj.getString("textureId") else null,
                    isCustom = true
                )
            )
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse custom themes")
    }
    return themes
}

private fun calculateContrastRatio(color1: Color, color2: Color): Float {
    val l1 = max(color1.luminance(), color2.luminance())
    val l2 = min(color1.luminance(), color2.luminance())
    return (l1 + 0.05f) / (l2 + 0.05f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderThemePanel(
    isVisible: Boolean,
    currentThemeId: String,
    excludeImages: Boolean = false,
    onExcludeImagesChange: (Boolean) -> Unit = {},
    showExcludeImagesOption: Boolean = false,
    customThemes: List<ReaderTheme>,
    builtInThemes: List<ReaderTheme> = BuiltInThemes,
    globalTextureTransparency: Float,
    onGlobalTextureTransparencyChange: (Float) -> Unit,
    onThemeSelected: (String) -> Unit,
    onCustomThemesUpdated: (List<ReaderTheme>) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return
    var showBuilder by remember { mutableStateOf(false) }
    var builderIsTextured by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<ReaderTheme?>(null) }
    var selectedTabIndex by remember { mutableIntStateOf(if (builtInThemes.find { it.id == currentThemeId }?.textureId != null || customThemes.find { it.id == currentThemeId }?.textureId != null) 1 else 0) }

    val plainBuiltInThemes = builtInThemes.filter { it.textureId == null }
    val texturedBuiltInThemes = builtInThemes.filter { it.textureId != null }
    val plainCustomThemes = customThemes.filter { it.textureId == null }
    val texturedCustomThemes = customThemes.filter { it.textureId != null }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        AnimatedContent(targetState = showBuilder, label = "ThemePanelTransition") { isBuilding ->
            if (isBuilding) {
                ThemeBuilderView(
                    initialTheme = editingTheme,
                    isTexturedMode = builderIsTextured,
                    globalTextureAlpha = 1f - globalTextureTransparency,
                    onSave = { newTheme ->
                        val updatedList = if (editingTheme != null) {
                            customThemes.map { if (it.id == newTheme.id) newTheme else it }
                        } else {
                            customThemes + newTheme
                        }
                        onCustomThemesUpdated(updatedList)
                        onThemeSelected(newTheme.id)
                        showBuilder = false
                        editingTheme = null
                    },
                    onCancel = {
                        showBuilder = false
                        editingTheme = null
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.reading_themes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp, top = 8.dp)
                    )

                    TabRow(selectedTabIndex = selectedTabIndex, containerColor = Color.Transparent, divider = {}) {
                        Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }) {
                            Text("Solid Colors", modifier = Modifier.padding(12.dp))
                        }
                        Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }) {
                            Text("Textured", modifier = Modifier.padding(12.dp))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (selectedTabIndex == 1) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Texture Transparency", style = MaterialTheme.typography.labelMedium)
                                Text("${(globalTextureTransparency * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = globalTextureTransparency,
                                onValueChange = onGlobalTextureTransparencyChange,
                                valueRange = 0f..1f
                            )
                        }
                    }

                    val activeListBuiltIn = if (selectedTabIndex == 0) plainBuiltInThemes else texturedBuiltInThemes
                    val activeListCustom = if (selectedTabIndex == 0) plainCustomThemes else texturedCustomThemes

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (showExcludeImagesOption) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.theme_preserve_image_colors), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(stringResource(R.string.theme_preserve_image_colors_desc), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    Switch(checked = excludeImages, onCheckedChange = onExcludeImagesChange)
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(stringResource(R.string.theme_presets), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }

                        items(activeListBuiltIn) { theme ->
                            ThemeGridItem(
                                theme = theme,
                                currentThemeId = currentThemeId,
                                context = LocalContext.current,
                                globalTextureAlpha = 1f - globalTextureTransparency,
                                onThemeSelected = onThemeSelected,
                                onEdit = null,
                                onDelete = null
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.theme_my_themes), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { editingTheme = null; builderIsTextured = selectedTabIndex == 1; showBuilder = true }) {
                                    Icon(Icons.Default.Add, contentDescription = "New Theme", tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }

                        if (activeListCustom.isEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(stringResource(R.string.theme_no_custom), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(activeListCustom) { theme ->
                                ThemeGridItem(
                                    theme = theme,
                                    currentThemeId = currentThemeId,
                                    context = LocalContext.current,
                                    globalTextureAlpha = 1f - globalTextureTransparency,
                                    onThemeSelected = onThemeSelected,
                                    onEdit = { editingTheme = it; builderIsTextured = selectedTabIndex == 1; showBuilder = true },
                                    onDelete = { themeToDelete ->
                                        val updated = customThemes.filter { it.id != themeToDelete.id }
                                        onCustomThemesUpdated(updated)
                                        if (currentThemeId == themeToDelete.id) onThemeSelected("system")
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeGridItem(
    theme: ReaderTheme,
    currentThemeId: String,
    context: Context,
    globalTextureAlpha: Float,
    onThemeSelected: (String) -> Unit,
    onEdit: ((ReaderTheme) -> Unit)?,
    onDelete: ((ReaderTheme) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val isSelected = currentThemeId == theme.id
    val bgColor = if (theme.id == "system") MaterialTheme.colorScheme.surfaceVariant else theme.backgroundColor
    val textColor = if (theme.id == "system") MaterialTheme.colorScheme.onSurfaceVariant else theme.textColor
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val textureBitmap = remember(theme.textureId) { loadReaderTextureBitmap(context, theme.textureId) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(bgColor, CircleShape)
                .then(textureBitmap?.let { bitmap ->
                    Modifier.drawBehind {
                        drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.SrcOver, alpha = globalTextureAlpha)
                    }
                } ?: Modifier)
                .border(if (isSelected) 3.dp else 1.dp, borderColor, CircleShape)
                .clickable { onThemeSelected(theme.id) },
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Aa", color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = theme.name, style = MaterialTheme.typography.labelSmall, color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)

        if (theme.isCustom && onEdit != null && onDelete != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, "Edit", Modifier.size(28.dp).clip(CircleShape).clickable { onEdit(theme) }.padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Delete, "Delete", Modifier.size(28.dp).clip(CircleShape).clickable { onDelete(theme) }.padding(6.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
fun ThemeBuilderView(
    initialTheme: ReaderTheme?,
    isTexturedMode: Boolean,
    globalTextureAlpha: Float,
    onSave: (ReaderTheme) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialTheme?.name ?: if (isTexturedMode) "Custom Textured" else "Custom Solid") }
    var bgColor by remember { mutableStateOf(initialTheme?.backgroundColor ?: Color(0xFFF5F5F5)) }
    var txtColor by remember { mutableStateOf(initialTheme?.textColor ?: Color(0xFF111111)) }
    var editingColorType by remember { mutableStateOf<String?>(null) }

    var importedTextures by remember { mutableStateOf(getImportedTextures(context)) }
    var textureId by remember { mutableStateOf(initialTheme?.textureId ?: importedTextures.firstOrNull()) }

    val texturePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { importReaderTexture(context, it) }?.let { newId ->
            importedTextures = getImportedTextures(context)
            textureId = newId
        }
    }

    val contrast = calculateContrastRatio(bgColor, txtColor)
    val isDark = bgColor.luminance() < 0.5f

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp)) {
        Text(
            text = stringResource(if (initialTheme == null) R.string.theme_new else R.string.theme_edit),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(16.dp))

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.theme_name)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            // Live Preview Card
            Surface(
                modifier = Modifier.fillMaxWidth().height(120.dp).padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = bgColor,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                val textureBitmap = remember(textureId) { loadReaderTextureBitmap(context, textureId) }
                Box(modifier = Modifier.fillMaxSize().run {
                    if (isTexturedMode && textureBitmap != null) {
                        this.drawBehind {
                            drawRect(ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.SrcOver, alpha = globalTextureAlpha)
                        }
                    } else this
                }) {
                    Column(Modifier.padding(16.dp).fillMaxWidth()) {
                        Text(text = stringResource(R.string.theme_preview_quote), color = txtColor, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(text = stringResource(R.string.theme_preview_author), color = txtColor, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
                    }
                }
            }

            AnimatedVisibility(visible = contrast < 4.5f) {
                Text(stringResource(R.string.theme_low_contrast_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 8.dp))
            }

            Spacer(Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ColorSwatchItem(label = stringResource(R.string.theme_page_color), color = bgColor, onClick = { editingColorType = "bg" }, modifier = Modifier.weight(1f))
                ColorSwatchItem(label = stringResource(R.string.theme_text_color), color = txtColor, onClick = { editingColorType = "text" }, modifier = Modifier.weight(1f))
            }

            if (isTexturedMode) {
                Spacer(Modifier.height(24.dp))
                CustomTexturePickerSection(
                    importedTextures = importedTextures,
                    selectedTextureId = textureId,
                    onTextureSelected = { textureId = it },
                    onImportTexture = { texturePickerLauncher.launch(arrayOf("image/png", "image/jpeg", "image/webp", "image/gif", "image/bmp")) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                onSave(ReaderTheme(id = initialTheme?.id ?: System.currentTimeMillis().toString(), name = name, backgroundColor = bgColor, textColor = txtColor, isDark = isDark, textureId = if (isTexturedMode) textureId else null, isCustom = true))
            }) {
                Text(stringResource(R.string.action_save), color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }

    editingColorType?.let { type ->
        ThemeColorPickerDialog(
            initialColor = if (type == "bg") bgColor else txtColor,
            title = if (type == "bg") stringResource(R.string.theme_page_color) else stringResource(R.string.theme_text_color),
            bgColor = bgColor,
            textColor = txtColor,
            editingColorType = type,
            onDismiss = { editingColorType = null },
            onColorChanged = { newColor -> if (type == "bg") bgColor = newColor else txtColor = newColor }
        )
    }
}

@Composable
private fun CustomTexturePickerSection(
    importedTextures: List<String>,
    selectedTextureId: String?,
    onTextureSelected: (String?) -> Unit,
    onImportTexture: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Select Custom Texture", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            item {
                Surface(
                    onClick = onImportTexture,
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.Add, contentDescription = "Import", tint = MaterialTheme.colorScheme.primary)
                        Text("Import", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            items(importedTextures) { tex ->
                val isSelected = tex == selectedTextureId
                val context = LocalContext.current
                val bitmap = remember(tex) { loadReaderTextureBitmap(context, tex) }

                Surface(
                    onClick = { onTextureSelected(tex) },
                    modifier = Modifier.size(72.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isSelected) 0.95f else 0.45f),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().then(bitmap?.let {
                            Modifier.drawBehind { drawRect(ShaderBrush(ImageShader(it, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.SrcOver, alpha = 0.6f) }
                        } ?: Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TexturePickerSection(
    selectedTextureId: String?,
    selectedTextureAlpha: Float,
    onTextureSelected: (String?) -> Unit,
    onTextureAlphaChange: (Float) -> Unit,
    onImportTexture: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.theme_texture), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextureChoice(
                label = stringResource(R.string.theme_texture_none),
                textureId = null,
                selectedTextureId = selectedTextureId,
                onTextureSelected = onTextureSelected,
                modifier = Modifier.weight(1f)
            )
            TextureChoice(
                label = stringResource(R.string.theme_texture_upload),
                textureId = selectedTextureId?.takeIf { it.startsWith(TEXTURE_FILE_PREFIX) },
                selectedTextureId = selectedTextureId,
                onTextureSelected = { onImportTexture() },
                isUpload = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(8.dp))
        ReaderTexture.entries.filter { it.assetPath != null }.chunked(2).forEach { rowTextures ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                rowTextures.forEach { texture ->
                    TextureChoice(
                        label = texture.displayName,
                        textureId = texture.id,
                        selectedTextureId = selectedTextureId,
                        onTextureSelected = onTextureSelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowTextures.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(visible = selectedTextureId != null) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.theme_texture_transparency), style = MaterialTheme.typography.labelMedium)
                    Text("${((1f - selectedTextureAlpha) * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Slider(
                    value = 1f - selectedTextureAlpha,
                    onValueChange = { onTextureAlphaChange((1f - it).coerceIn(0f, 1f)) },
                    valueRange = 0f..1f
                )
            }
        }
    }
}

@Composable
private fun TextureChoice(
    label: String,
    textureId: String?,
    selectedTextureId: String?,
    onTextureSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
    isUpload: Boolean = false,
) {
    val context = LocalContext.current
    val textureBitmap = remember(textureId) { loadReaderTextureBitmap(context, textureId) }
    val selected = if (isUpload) selectedTextureId?.startsWith(TEXTURE_FILE_PREFIX) == true else selectedTextureId == textureId
    Surface(
        onClick = { onTextureSelected(textureId) },
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (selected) 0.95f else 0.45f),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(textureBitmap?.let { bitmap ->
                    Modifier.drawBehind {
                        drawRect(ShaderBrush(ImageShader(bitmap, TileMode.Repeated, TileMode.Repeated)), blendMode = BlendMode.SrcOver, alpha = 0.6f)
                    }
                } ?: Modifier)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isUpload && selectedTextureId?.startsWith(TEXTURE_FILE_PREFIX) == true) {
                    readerTextureDisplayName(selectedTextureId)
                } else label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorSwatchItem(label: String, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 8.dp))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            color = color,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {}
    }
}

@Composable
fun ThemeColorPickerDialog(
    initialColor: Color,
    title: String,
    bgColor: Color,
    textColor: Color,
    editingColorType: String,
    onDismiss: () -> Unit,
    onColorChanged: (Color) -> Unit
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    val currentColor by remember {
        derivedStateOf {
            val hsv = floatArrayOf(hue, saturation, value)
            val argb = android.graphics.Color.HSVToColor(255, hsv)
            Color(argb)
        }
    }

    LaunchedEffect(currentColor) {
        onColorChanged(currentColor)
    }

    fun updateFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()), // Prevents elements from hiding off-screen
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3E3E3E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(16.dp))

                val liveBgColor = if (editingColorType == "bg") currentColor else bgColor
                val liveTextColor = if (editingColorType == "text") currentColor else textColor

                Surface(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = liveBgColor,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.theme_color_live_preview),
                            color = liveTextColor,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = stringResource(R.string.theme_color_preview_text),
                            color = liveTextColor,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                SpectrumBox(
                    hue = hue,
                    saturation = saturation,
                    currentColor = currentColor,
                    onHueSatChanged = { h, s -> hue = h; saturation = s },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )

                Spacer(Modifier.height(20.dp))

                BrightnessSlider(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onValueChanged = { value = it },
                    modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorComparePill(
                        oldColor = initialColor,
                        newColor = currentColor,
                        modifier = Modifier.width(64.dp).height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.theme_color_hex), color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        HexInput(color = currentColor, onHexChanged = { updateFromColor(it) })
                    }

                    Row(
                        modifier = Modifier.weight(2.4f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RgbInputColumn(label = stringResource(R.string.color_r), value = currentColor.red,
                            onValueChange = { r -> updateFromColor(currentColor.copy(red = r)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(label = stringResource(R.string.color_g), value = currentColor.green,
                            onValueChange = { g -> updateFromColor(currentColor.copy(green = g)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(label = stringResource(R.string.color_b), value = currentColor.blue,
                            onValueChange = { b -> updateFromColor(currentColor.copy(blue = b)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White
                        )
                    ) {
                        Text(stringResource(R.string.action_save), color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightColorPickerDialog(
    initialColors: Map<PdfHighlightColor, Color>,
    initialSelection: PdfHighlightColor = PdfHighlightColor.YELLOW,
    onDismiss: () -> Unit,
    onSave: (Map<PdfHighlightColor, Color>) -> Unit
) {
    var currentColors by remember { mutableStateOf(initialColors) }
    var selectedSlot by remember { mutableStateOf(initialSelection) }

    val initialActiveColor = currentColors[selectedSlot] ?: selectedSlot.color
    val initialHsv = remember(initialActiveColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialActiveColor.toArgb(), hsv)
        hsv
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }

    LaunchedEffect(selectedSlot) {
        val color = currentColors[selectedSlot] ?: selectedSlot.color
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    val currentColor by remember {
        derivedStateOf {
            val hsv = floatArrayOf(hue, saturation, value)
            Color(android.graphics.Color.HSVToColor(255, hsv))
        }
    }

    LaunchedEffect(currentColor) {
        currentColors = currentColors + (selectedSlot to currentColor)
    }

    fun updateFromColor(color: Color) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3E3E3E), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.highlight_customize_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    PdfHighlightColor.entries.forEach { slot ->
                        val slotColor = currentColors[slot] ?: slot.color
                        val isSelected = selectedSlot == slot
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(slotColor)
                                .clickable { selectedSlot = slot }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color.White else Color.Gray,
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = stringResource(R.string.content_desc_selected),
                                    tint = if (slotColor.luminance() > 0.5f) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                SpectrumBox(
                    hue = hue,
                    saturation = saturation,
                    currentColor = currentColor,
                    onHueSatChanged = { h, s -> hue = h; saturation = s },
                    modifier = Modifier.fillMaxWidth().height(220.dp)
                )

                Spacer(Modifier.height(20.dp))

                BrightnessSlider(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onValueChanged = { value = it },
                    modifier = Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(12.dp))
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ColorComparePill(
                        oldColor = selectedSlot.color,
                        newColor = currentColor,
                        modifier = Modifier.width(64.dp).height(36.dp)
                    )

                    Column(
                        modifier = Modifier.weight(1.6f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.theme_color_hex), color = Color.Gray, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        HexInput(color = currentColor, onHexChanged = { updateFromColor(it) })
                    }

                    Row(
                        modifier = Modifier.weight(2.4f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        RgbInputColumn(label = stringResource(R.string.color_r), value = currentColor.red,
                            onValueChange = { r -> updateFromColor(currentColor.copy(red = r)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(label = stringResource(R.string.color_g), value = currentColor.green,
                            onValueChange = { g -> updateFromColor(currentColor.copy(green = g)) },
                            modifier = Modifier.weight(1f)
                        )
                        RgbInputColumn(label = stringResource(R.string.color_b), value = currentColor.blue,
                            onValueChange = { b -> updateFromColor(currentColor.copy(blue = b)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { updateFromColor(selectedSlot.color) }) {
                        Text(stringResource(R.string.action_reset), color = Color(0xFFFF5252))
                    }
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel), color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(currentColors) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.action_save), color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiHubBottomSheet(
    bookTitle: String,
    currentChapterIndex: Int,
    chapterTitle: String,
    summaryCacheManager: SummaryCacheManager? = null,
    summarizationResult: SummarizationResult?,
    isSummarizationLoading: Boolean,
    onGenerateSummary: (Boolean) -> Unit,
    onClearSummary: () -> Unit = {},
    recapResult: SummarizationResult? = null,
    isRecapLoading: Boolean = false,
    onGenerateRecap: (() -> Unit)? = null,
    onClearRecap: () -> Unit = {},
    onDismiss: () -> Unit,
    isMainTtsActive: Boolean,
    getAuthToken: suspend () -> String?,
    credits: Int,
    isProUser: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()

    LaunchedEffect(currentChapterIndex) {
        onClearSummary()
    }

    DisposableEffect(Unit) {
        onDispose {
            if (ttsState.playbackSource == "POPUP" && (ttsState.isPlaying || ttsState.isLoading)) {
                ttsController.stop()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentWindowInsets = { WindowInsets.navigationBars }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 300.dp, max = 600.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.ai_features_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(2f)
                )
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    if (BuildConfig.FLAVOR != "oss") {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "⭐ $credits",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            val summaryTab = stringResource(R.string.ai_tab_summary)
            val recapTab = stringResource(R.string.ai_tab_recap)
            val cacheTab = stringResource(R.string.ai_tab_cache)
            val tabs = mutableListOf(summaryTab)
            if (onGenerateRecap != null) tabs.add(recapTab)
            if (summaryCacheManager != null) tabs.add(cacheTab)

            TabRow(selectedTabIndex = selectedTabIndex, modifier = Modifier.padding(bottom = 16.dp)) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = selectedTabIndex == index, onClick = { selectedTabIndex = index }) {
                        Text(title, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleSmall)
                    }
                }
            }

            val activeTab = tabs.getOrNull(selectedTabIndex) ?: summaryTab
            var cacheRefreshTrigger by remember { mutableIntStateOf(0) }

            when (activeTab) {
                summaryTab -> {
                    val cachedSummary = remember(currentChapterIndex, cacheRefreshTrigger) { summaryCacheManager?.getSummary(bookTitle, currentChapterIndex) }
                    val effectiveResult = summarizationResult ?: if (cachedSummary != null) SummarizationResult(summary = cachedSummary, isCacheHit = true) else null

                    if (effectiveResult == null && !isSummarizationLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(R.drawable.summarize), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.ai_no_summary_for_chapter, chapterTitle.lowercase()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { onGenerateSummary(false) },
                                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ai), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ai_generate_summary_for_chapter, chapterTitle))
                                }
                            }
                        }
                    } else {
                        AiResultContentView(
                            title = chapterTitle,
                            result = effectiveResult,
                            isLoading = isSummarizationLoading,
                            isMainTtsActive = isMainTtsActive,
                            ttsController = ttsController,
                            ttsState = ttsState,
                            getAuthToken = getAuthToken,
                            onRegenerate = { onGenerateSummary(true) }
                        )
                    }
                }
                recapTab -> {
                    // Recap Tab
                    if (recapResult == null && !isRecapLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painterResource(R.drawable.ai), contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(16.dp))
                                Text(stringResource(R.string.ai_recap_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { onGenerateRecap?.invoke() },
                                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 8.dp)
                                ) {
                                    Icon(painterResource(R.drawable.ai), contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.ai_generate_story_recap))
                                }
                            }
                        }
                    } else {
                        AiResultContentView(
                            title = stringResource(R.string.ai_story_recap),
                            result = recapResult,
                            isLoading = isRecapLoading,
                            isMainTtsActive = isMainTtsActive,
                            ttsController = ttsController,
                            ttsState = ttsState,
                            getAuthToken = getAuthToken,
                            onRegenerate = { onGenerateRecap?.invoke() },
                            onClear = onClearRecap
                        )
                    }
                }
                cacheTab -> {
                    if (summaryCacheManager != null) {
                        ManageCacheTab(bookTitle, summaryCacheManager, onCacheChanged = {
                            cacheRefreshTrigger++
                            onClearSummary()
                        })
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AiResultContentView(
    title: String,
    result: SummarizationResult?,
    isLoading: Boolean,
    isMainTtsActive: Boolean,
    ttsController: com.aryan.reader.tts.TtsController,
    ttsState: TtsPlaybackManager.TtsState,
    getAuthToken: suspend () -> String?,
    onRegenerate: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp)
            )

            val showUsageBadge = result?.isCacheHit == true || (BuildConfig.FLAVOR != "oss" && (result?.cost != null || isLoading))
            if (result != null && showUsageBadge && (!result.summary.isNullOrBlank() || isLoading)) {
                Surface(
                    color = if (result.isCacheHit || (result.cost == 0.0 && result.freeRemaining != null)) Color(
                        0xFF4CAF50
                    ).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (result.isCacheHit) {
                            stringResource(R.string.ai_cache_hit_free)
                        } else if (result.cost != null) {
                            if (result.cost == 0.0 && result.freeRemaining != null) {
                                stringResource(R.string.ai_generated_free_remaining,
                                    result.freeRemaining
                                )
                            } else {
                                stringResource(R.string.ai_generated_cost, result.cost.toString())
                            }
                        } else {
                            stringResource(R.string.ai_generating_cost_calculating)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (result.isCacheHit || (result.cost == 0.0 && result.freeRemaining != null)) Color(
                            0xFF388E3C
                        ) else MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        if (isLoading && (result?.summary.isNullOrBlank() && result?.error.isNullOrBlank())) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.ai_thinking), modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else if (result != null) {
            val summaryText = result.summary
            val errorText = result.error

            val styledContent = remember(summaryText, errorText) {
                if (!summaryText.isNullOrBlank()) {
                    MarkdownParser.parse(summaryText)
                } else {
                    AnnotatedString(errorText ?: "")
                }
            }
            val textToUse = styledContent.text

            if (textToUse.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTtsSessionActive = ttsState.currentText != null || ttsState.isLoading

                    if (onClear != null && !isLoading) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.action_clear),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    if (onRegenerate != null) {
                        TextButton(onClick = onRegenerate) {
                            Text(stringResource(R.string.ai_regenerate))
                        }
                    }

                    IconButton(
                        onClick = {
                            if (isTtsSessionActive) {
                                ttsController.stop()
                            } else {
                                val chunks = splitTextIntoChunks(textToUse).map { TtsChunk(it, "", -1) }
                                if (chunks.isNotEmpty()) {
                                    scope.launch {
                                        val token = getAuthToken()
                                        ttsController.start(
                                            chunks = chunks,
                                            bookTitle = title,
                                            chapterTitle = context.getString(R.string.ai_output_title),
                                            coverImageUri = null,
                                            ttsMode = loadTtsMode(context),
                                            playbackSource = "POPUP",
                                            authToken = token
                                        )
                                    }
                                }
                            }
                        },
                        enabled = !isMainTtsActive || (ttsState.playbackSource == "POPUP")
                    ) {
                        Icon(
                            imageVector = if (isTtsSessionActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.action_read_aloud)
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(textToUse))
                    }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy)
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            if (errorText != null && summaryText.isNullOrBlank()) {
                Text(errorText, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
            } else if (textToUse.isNotBlank()) {
                val scrollState = rememberScrollState()
                var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

                LaunchedEffect(ttsState.currentText, textLayoutResult) {
                    val currentChunk = ttsState.currentText
                    val layoutResult = textLayoutResult
                    if (!currentChunk.isNullOrBlank() && layoutResult != null) {
                        val startIndex = textToUse.indexOf(currentChunk)
                        if (startIndex != -1) {
                            val line = layoutResult.getLineForOffset(startIndex)
                            val lineTop = layoutResult.getLineTop(line)
                            val viewportHeight = scrollState.viewportSize
                            val targetScroll = (lineTop - viewportHeight / 2).coerceAtLeast(0f)
                            scope.launch {
                                scrollState.animateScrollTo(targetScroll.toInt())
                            }
                        }
                    }
                }

                val annotatedText = buildAnnotatedString {
                    append(styledContent)
                    val currentChunk = ttsState.currentText
                    if (!currentChunk.isNullOrBlank()) {
                        val startIndex = textToUse.indexOf(currentChunk)
                        if (startIndex != -1) {
                            addStyle(
                                style = SpanStyle(background = MaterialTheme.colorScheme.primaryContainer),
                                start = startIndex,
                                end = startIndex + currentChunk.length
                            )
                        }
                    }
                }
                Text(
                    text = annotatedText,
                    modifier = Modifier.verticalScroll(scrollState).weight(1f, fill = false),
                    onTextLayout = { textLayoutResult = it }
                )
            }
        }
    }
}

@Composable
fun ManageCacheTab(bookTitle: String, summaryCacheManager: SummaryCacheManager, onCacheChanged: () -> Unit = {}) {
    var cachedItems by androidx.compose.runtime.remember { mutableStateOf(summaryCacheManager.getAllSummaries(bookTitle)) }

    if (cachedItems.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.ai_no_cached_summaries), style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            LazyColumn(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                items(cachedItems.size) { index ->
                    val item = cachedItems[index]
                    var expanded by androidx.compose.runtime.remember { mutableStateOf(false) }

                    Column(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = item.chapterTitle,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            trailingContent = {
                                IconButton(onClick = {
                                    summaryCacheManager.deleteSummary(bookTitle, item.chapterIndex)
                                    cachedItems = summaryCacheManager.getAllSummaries(bookTitle)
                                    onCacheChanged()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.action_delete),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        )
                        AnimatedVisibility(visible = expanded) {
                            Text(
                                text = item.summary,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
            TextButton(
                onClick = {
                    summaryCacheManager.clearBookCache(bookTitle)
                    cachedItems = emptyList()
                    onCacheChanged()
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(stringResource(R.string.clear_all), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
