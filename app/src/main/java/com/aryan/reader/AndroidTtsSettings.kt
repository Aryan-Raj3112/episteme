@file:OptIn(ExperimentalMaterial3Api::class) @file:Suppress("KotlinConstantConditions")

package com.aryan.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.tts.GEMINI_TTS_SPEAKERS
import com.aryan.reader.tts.SpeakerSamplePlayer
import com.aryan.reader.tts.TtsCacheManager
import com.aryan.reader.tts.TtsPlaybackManager
import com.aryan.reader.tts.formatBytes
import org.commonmark.node.ListItem
import org.commonmark.node.Text
import timber.log.Timber
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

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
    val offlineNativeOnly = BuildConfig.IS_OFFLINE

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

    LaunchedEffect(allVoices, savedVoiceName, offlineNativeOnly) {
        if (
            offlineNativeOnly &&
            savedVoiceName != null &&
            allVoices.any { voice -> voice.name == savedVoiceName && voice.isNetworkConnectionRequired }
        ) {
            savedVoiceName = null
            saveNativeVoice(context, null)
        }
    }

    if (isTtsLoading) {
        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val selectableVoices = remember(allVoices, offlineNativeOnly) {
        if (offlineNativeOnly) allVoices.filter { voice -> !voice.isNetworkConnectionRequired } else allVoices
    }

    val languages = remember(selectableVoices) {
        val list = listOf(allLanguagesLabel) + selectableVoices.map { it.locale.displayLanguage }.filter { it.isNotBlank() }.distinct().sorted()
        Timber.tag("TTS_DIAGNOSE").d("Languages list updated: size=${list.size}, items=$list")
        list
    }

    val filteredVoices = remember(selectableVoices, selectedLanguage) {
        if (selectedLanguage == allLanguagesLabel) selectableVoices
        else selectableVoices.filter { it.locale.displayLanguage == selectedLanguage }
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
                        val fallbackVoice = if (offlineNativeOnly) {
                            voices.firstOrNull { voice ->
                                voice.locale == defaultLocale && !voice.isNetworkConnectionRequired
                            } ?: voices.firstOrNull { voice ->
                                !voice.isNetworkConnectionRequired
                            }
                        } else {
                            defaultVoice ?: voices.firstOrNull { voice ->
                                voice.locale == defaultLocale && !voice.isNetworkConnectionRequired
                            } ?: voices.firstOrNull { voice ->
                                voice.locale == defaultLocale
                            }
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

internal fun saveNativeVoice(context: Context, @Suppress("SameParameterValue") voiceName: String?) {
    val prefs = context.getSharedPreferences("reader_prefs", Context.MODE_PRIVATE)
    if (voiceName == null) {
        prefs.edit { remove(PREF_NATIVE_TTS_VOICE) }
    } else {
        prefs.edit { putString(PREF_NATIVE_TTS_VOICE, voiceName) }
    }
}
