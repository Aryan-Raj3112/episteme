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
import androidx.compose.foundation.layout.fillMaxWidth
import timber.log.Timber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aryan.reader.AiDefinitionPopup
import com.aryan.reader.AiDefinitionResult
import com.aryan.reader.R
import com.aryan.reader.SummarizationPopup
import com.aryan.reader.SummarizationResult
import com.aryan.reader.SummaryCacheManager
import com.aryan.reader.epub.EpubBook
import com.aryan.reader.fetchRecap
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.summarizationUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Handles the raw network streaming for book content summarization.
 */
suspend fun summarizeBookContent(
    content: String,
    onUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    if (content.isBlank()) {
        onError("The book content is empty.")
        onFinish()
        return
    }
    Timber.d("Starting summarization for content of length: ${content.length}")

    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(summarizationUrl)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 15000
            connection.readTimeout = 120000
            connection.doOutput = true
            connection.doInput = true

            val jsonPayload = JSONObject().apply {
                put("content_type", "text")
                put("data", content)
            }
            connection.outputStream.use { os ->
                os.write(jsonPayload.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            Timber.d("Summarization: Got response code $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                var hasReceivedData = false
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        Timber.d("Summarization: Received line: $line")
                        try {
                            val jsonResponse = JSONObject(line!!)
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
                if (!hasReceivedData) {
                    onError("Failed to parse summary from server response.")
                }
            } else {
                val errorBody = try {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }
                } catch (_: Exception) { null }
                val errorDetail = try {
                    JSONObject(errorBody.toString()).getString("detail")
                } catch (_: Exception) { "Could not fetch summary." }
                onError("Error: $responseCode. $errorDetail")
            }
        } catch (e: Exception) {
            Timber.e(e, "Network error during summarization: ${e.message}")
            onError("Network error. Please check connection and server status.")
        } finally {
            connection?.disconnect()
            onFinish()
        }
    }
}

/**
 * Orchestrates the logic for generating a Story Recap.
 * Fetches past summaries from cache/network and combines with current context.
 */
suspend fun executeRecapLogic(
    epubBook: EpubBook,
    chapterIndex: Int,
    characterLimit: Int,
    summaryCacheManager: SummaryCacheManager,
    paginator: IPaginator?,
    context: Context,
    onProgressUpdate: (String) -> Unit,
    onResultUpdate: (String) -> Unit,
    onError: (String) -> Unit,
    onFinish: () -> Unit
) {
    Timber.d("executeRecapLogic called. ChapterIndex: $chapterIndex, CharLimit: $characterLimit")

    val pastSummaries = mutableListOf<String>()
    val chapters = epubBook.chapters

    // 1. Fetch Past Summaries
    for (i in 0 until chapterIndex) {
        onProgressUpdate("Analyzing Chapter ${i + 1}...")

        val cached = summaryCacheManager.getSummary(epubBook.title, i)
        if (cached != null) {
            pastSummaries.add(cached)
        } else {
            val textToSummarize = paginator?.getPlainTextForChapter(i) ?: withContext(Dispatchers.IO) {
                try {
                    val chapter = chapters[i]
                    val fullPath = "${epubBook.extractionBasePath}/${chapter.htmlFilePath}"
                    val doc = Jsoup.parse(File(fullPath), "UTF-8")
                    doc.body().text()
                } catch (_: Exception) { "" }
            }

            if (textToSummarize.length > 100) {
                val sb = StringBuilder()
                val latch = kotlinx.coroutines.CompletableDeferred<Boolean>()

                summarizeBookContent(
                    content = textToSummarize,
                    onUpdate = { sb.append(it) },
                    onError = {
                        Timber.e("Failed to summarize Ch $i for recap: $it")
                        latch.complete(false)
                    },
                    onFinish = { latch.complete(true) }
                )

                val success = latch.await()
                if (success && sb.isNotEmpty()) {
                    val summary = sb.toString()
                    summaryCacheManager.saveSummary(epubBook.title, i, summary)
                    pastSummaries.add(summary)
                }
            }
        }
        // Small delay to prevent rate limits
        if (pastSummaries.isNotEmpty() && !summaryCacheManager.hasSummary(epubBook.title, i)) {
            delay(500)
        }
    }

    // 2. Get Current Context
    onProgressUpdate("Reading current position...")

    val currentChapterText = paginator?.getPlainTextForChapter(chapterIndex)
        ?: withContext(Dispatchers.IO) {
            try {
                Jsoup.parse(File("${epubBook.extractionBasePath}/${chapters[chapterIndex].htmlFilePath}"), "UTF-8").body().text()
            } catch (_: Exception) { "" }
        }

    val endIndex = characterLimit.coerceIn(0, currentChapterText.length)
    val textSoFar = currentChapterText.substring(0, endIndex)

    // Fallback if text is blank
    val finalContextText = if (textSoFar.isBlank() && currentChapterText.isNotEmpty()) {
        currentChapterText.take(500)
    } else {
        textSoFar
    }

    onProgressUpdate("Generating Recap...")
    fetchRecap(
        pastSummaries = pastSummaries,
        currentText = finalContextText,
        context = context,
        onUpdate = { chunk -> onResultUpdate(chunk) },
        onError = { error -> onError(error) },
        onFinish = { onFinish() }
    )
}

/**
 * Container for all AI-related popups and dialogs (Summary, Recap, Definition, Upsells).
 */
@Composable
fun EpubReaderAiOverlays(
    showSummarizationPopup: Boolean,
    summarizationResult: SummarizationResult?,
    isSummarizationLoading: Boolean,
    onDismissSummarization: () -> Unit,
    showSummarizationUpsellDialog: Boolean,
    onDismissSummarizationUpsell: () -> Unit,
    showRecapPopup: Boolean,
    recapResult: SummarizationResult?,
    isRecapLoading: Boolean,
    onDismissRecap: () -> Unit,
    showAiDefinitionPopup: Boolean,
    selectedTextForAi: String?,
    aiDefinitionResult: AiDefinitionResult?,
    isAiDefinitionLoading: Boolean,
    onDismissAiDefinition: () -> Unit,
    showDictionaryUpsellDialog: Boolean,
    onDismissDictionaryUpsell: () -> Unit,
    onNavigateToPro: () -> Unit,
    isTtsSessionActive: Boolean,
    onOpenExternalDictionary: (String) -> Unit
) {
    if (showSummarizationPopup) {
        SummarizationPopup(
            title = stringResource(R.string.ai_chapter_summary),
            result = summarizationResult,
            isLoading = isSummarizationLoading,
            onDismiss = onDismissSummarization,
            isMainTtsActive = isTtsSessionActive
        )
    }

    if (showRecapPopup) {
        SummarizationPopup(
            title = stringResource(R.string.ai_story_recap_beta),
            result = recapResult,
            isLoading = isRecapLoading,
            onDismiss = onDismissRecap,
            isMainTtsActive = isTtsSessionActive,
        )
    }

    if (showSummarizationUpsellDialog) {
        AlertDialog(
            onDismissRequest = onDismissSummarizationUpsell,
            icon = { Icon(painter = painterResource(id = R.drawable.summarize), contentDescription = null) },
            title = {
                Text(
                    text = stringResource(R.string.ai_unlock_summarization),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.ai_unlock_summarization_desc),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissSummarizationUpsell()
                    onNavigateToPro()
                }) { Text(stringResource(R.string.action_learn_more)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissSummarizationUpsell) { Text(stringResource(R.string.action_not_now)) }
            }
        )
    }

    if (showAiDefinitionPopup) {
        AiDefinitionPopup(
            word = selectedTextForAi,
            result = aiDefinitionResult,
            isLoading = isAiDefinitionLoading,
            onDismiss = onDismissAiDefinition,
            isMainTtsActive = isTtsSessionActive,
            onOpenExternalDictionary = {
                selectedTextForAi?.let { text -> onOpenExternalDictionary(text) }
            }
        )
    }

    if (showDictionaryUpsellDialog) {
        AlertDialog(
            onDismissRequest = onDismissDictionaryUpsell,
            icon = { Icon(painter = painterResource(id = R.drawable.ai), contentDescription = null) },
            title = { Text(stringResource(R.string.ai_unlock_smart_dict)) },
            text = { Text(stringResource(R.string.ai_unlock_smart_dict_desc)) },
            confirmButton = {
                TextButton(onClick = {
                    onDismissDictionaryUpsell()
                    onNavigateToPro()
                }) { Text(stringResource(R.string.action_learn_more)) }
            },
            dismissButton = {
                TextButton(onClick = onDismissDictionaryUpsell) { Text(stringResource(R.string.action_not_now)) }
            }
        )
    }
}