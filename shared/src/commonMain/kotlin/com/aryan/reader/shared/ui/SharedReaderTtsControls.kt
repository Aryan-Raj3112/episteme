package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderAiByokSettings
import com.aryan.reader.shared.ReaderCloudTtsVoices
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderAction
import com.aryan.reader.shared.ReaderTheme
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.ReaderTtsReplacementBookSettings
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementRule
import com.aryan.reader.shared.ReaderTtsReplacementSuggestions
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSettings
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun SharedReaderVisualOptionsControls(
    settings: ReaderSettings,
    onReaderAction: (ReaderAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedReaderPanelSection(readerString("label_reading", "Reading")) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = settings.readingMode == ReaderReadingMode.PAGINATED && !settings.rightToLeftPagination,
                    onClick = {
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(
                                    readingMode = ReaderReadingMode.PAGINATED,
                                    rightToLeftPagination = false
                                )
                            )
                        )
                    },
                    label = { Text(readerString("menu_reading_mode_paginated", "Paginated (left-to-right)")) }
                )
                FilterChip(
                    selected = settings.readingMode == ReaderReadingMode.PAGINATED && settings.rightToLeftPagination,
                    onClick = {
                        onReaderAction(
                            ReaderAction.SettingsChanged(
                                settings.copy(
                                    readingMode = ReaderReadingMode.PAGINATED,
                                    rightToLeftPagination = true
                                )
                            )
                        )
                    },
                    label = { Text(readerString("menu_right_to_left_pagination", "Paginated (right-to-left)")) }
                )
                FilterChip(
                    selected = settings.readingMode == ReaderReadingMode.VERTICAL,
                    onClick = {
                        onReaderAction(ReaderAction.SettingsChanged(settings.copy(readingMode = ReaderReadingMode.VERTICAL)))
                    },
                    label = { Text(readerString("menu_reading_mode_vertical", "Vertical")) }
                )
            }
            if (settings.readingMode == ReaderReadingMode.PAGINATED) {
                SharedReaderChoiceRow {
                    FilterChip(
                        selected = settings.pageSpreadMode == ReaderPageSpreadMode.SINGLE,
                        onClick = {
                            onReaderAction(
                                ReaderAction.SettingsChanged(settings.copy(pageSpreadMode = ReaderPageSpreadMode.SINGLE))
                            )
                        },
                        label = { Text(readerString("visual_options_pdf_spread_single", "Single page")) }
                    )
                    FilterChip(
                        selected = settings.pageSpreadMode == ReaderPageSpreadMode.TWO_PAGE,
                        onClick = {
                            onReaderAction(
                                ReaderAction.SettingsChanged(settings.copy(pageSpreadMode = ReaderPageSpreadMode.TWO_PAGE))
                            )
                        },
                        label = { Text(readerString("visual_options_pdf_spread_two", "Two pages")) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharedReaderTtsControls(
    extrasState: ReaderExtrasState,
    aiByokSettings: ReaderAiByokSettings,
    toolbarPreferences: ReaderToolbarPreferences,
    cloudTtsControlsAvailable: Boolean,
    onCloudTtsClearCache: () -> Unit,
    onCloudTtsVoiceChange: (String) -> Unit,
    ttsReplacementPreferences: ReaderTtsReplacementPreferences,
    ttsReplacementBookId: String,
    onTtsReplacementPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit
) {
    val settings = aiByokSettings.sanitized()
    val ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        if (
            cloudTtsControlsAvailable &&
            (toolbarPreferences.isVisible(ReaderTool.TTS_CONTROLS) || toolbarPreferences.isVisible(ReaderTool.TTS_SETTINGS))
        ) {
            SharedReaderPanelSection(readerString("credits_cloud_tts_title", "Cloud TTS")) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        when {
                            extrasState.cloudTts.isLoading -> readerString("desktop_preparing_audio", "Preparing audio")
                            extrasState.cloudTts.isPaused -> readerString("desktop_paused", "Paused")
                            extrasState.cloudTts.isPlaying -> readerString("label_reading", "Reading")
                            settings.isCloudTtsAvailable -> readerString("desktop_cloud_tts_ready", "Ready")
                            settings.serverBackedReaderAiFeatures -> readerString("desktop_cloud_tts_needs_signed_in_credits", "Needs signed-in credits")
                            else -> readerString("desktop_cloud_tts_needs_gemini", "Needs Gemini key")
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                    val errorMessage = extrasState.cloudTts.errorMessage?.takeIf { it.isNotBlank() }
                    val statusMessage = extrasState.cloudTts.progress.currentPositionLabel
                        ?: extrasState.cloudTts.statusMessage?.takeIf { it.isNotBlank() }
                    when {
                        errorMessage != null -> Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        statusMessage != null -> Text(statusMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(readerString("desktop_cloud_tts_voice", "Cloud TTS voice"), fontWeight = FontWeight.SemiBold)
                    if (ttsBusy) {
                        Text(
                            readerString("desktop_stop_reading_change_voices", "Stop reading to change voices."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        ReaderCloudTtsVoices.forEach { voice ->
                            FilterChip(
                                selected = settings.ttsSpeakerId == voice.id,
                                enabled = !ttsBusy,
                                onClick = { onCloudTtsVoiceChange(voice.id) },
                                label = {
                                    Column {
                                        Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            voice.description,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            )
                        }
                    }
                    val cacheSummary = extrasState.cloudTts.cacheSummary
                    if (cacheSummary.hasCachedAudio) {
                        Text(
                            readerString("desktop_cache_format", "Cache: %1\$s", cacheSummary.currentVoiceLabel),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (cacheSummary.hasCurrentVoiceCachedAudio) {
                            TextButton(onClick = onCloudTtsClearCache) {
                                Text(readerString("desktop_clear_voice_cache", "Clear voice cache"))
                            }
                        }
                    }
                }
            }
        }

        if (toolbarPreferences.isVisible(ReaderTool.TTS_REPLACEMENTS)) {
            SharedReaderTtsReplacementControls(
                preferences = ttsReplacementPreferences,
                bookId = ttsReplacementBookId,
                onPreferencesChange = onTtsReplacementPreferencesChange
            )
        }
    }
}

internal enum class SharedTtsReplacementScope {
    GLOBAL,
    BOOK
}

@Composable
fun SharedReaderTtsReplacementControls(
    preferences: ReaderTtsReplacementPreferences,
    bookId: String,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    allowBookScope: Boolean = true
) {
    var selectedScope by remember(bookId, allowBookScope) { mutableStateOf(SharedTtsReplacementScope.GLOBAL) }
    val effectiveScope = if (allowBookScope) selectedScope else SharedTtsReplacementScope.GLOBAL
    var editingRuleId by remember(bookId, effectiveScope) { mutableStateOf<String?>(null) }
    var isAddingRule by remember(bookId, effectiveScope) { mutableStateOf(false) }
    val bookSettings = preferences.settingsForBook(bookId)
    val bookRules = preferences.rulesForBook(bookId)

    SharedReaderPanelSection(readerString("menu_tts_word_replacements", "TTS Word Replacements")) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(readerString("tts_replacements_replace_only_spoken", "Replace only what is spoken"), fontWeight = FontWeight.SemiBold)
                Text(
                    readerString("tts_replacements_replace_only_spoken_desc", "Reader text, highlights, and locations stay unchanged."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = preferences.isEnabled,
                onCheckedChange = { onPreferencesChange(preferences.copy(isEnabled = it)) }
            )
        }

        if (allowBookScope) {
            SharedReaderChoiceRow {
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.GLOBAL,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.GLOBAL
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text(readerString("tts_replacements_tab_global", "Global")) }
                )
                FilterChip(
                    selected = selectedScope == SharedTtsReplacementScope.BOOK,
                    onClick = {
                        selectedScope = SharedTtsReplacementScope.BOOK
                        editingRuleId = null
                        isAddingRule = false
                    },
                    label = { Text(readerString("tts_replacements_tab_this_book", "This book")) }
                )
            }
        }

        when (effectiveScope) {
            SharedTtsReplacementScope.GLOBAL -> {
                SharedTtsReplacementSuggestionsRow { suggestion ->
                    onPreferencesChange(
                        preferences.copy(
                            globalRules = preferences.globalRules + suggestion.asDesktopEditableRule(
                                prefix = "global",
                                existingRules = preferences.globalRules
                            )
                        )
                    )
                }
                TextButton(onClick = { isAddingRule = true; editingRuleId = null }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(readerString("tts_replacements_add_rule", "Add rule"))
                }
                val editingRule = editingRuleId?.let { id -> preferences.globalRules.firstOrNull { it.id == id } }
                if (isAddingRule || editingRule != null) {
                    SharedTtsReplacementRuleEditor(
                        seedRule = editingRule,
                        newRuleId = newSharedReplacementRuleId("global", preferences.globalRules),
                        onCancel = { isAddingRule = false; editingRuleId = null },
                        onSave = { rule ->
                            val updated = if (editingRule == null) {
                                preferences.globalRules + rule
                            } else {
                                preferences.globalRules.map { if (it.id == editingRule.id) rule else it }
                            }
                            onPreferencesChange(preferences.copy(globalRules = updated))
                            isAddingRule = false
                            editingRuleId = null
                        }
                    )
                }
                SharedTtsReplacementRuleList(
                    rules = preferences.globalRules,
                    emptyText = readerString("tts_replacements_empty_global", "No global replacement rules yet."),
                    onToggle = { rule, enabled ->
                        onPreferencesChange(
                            preferences.copy(
                                globalRules = preferences.globalRules.map {
                                    if (it.id == rule.id) it.copy(enabled = enabled) else it
                                }
                            )
                        )
                    },
                    onEdit = { rule -> editingRuleId = rule.id; isAddingRule = false },
                    onDelete = { rule ->
                        onPreferencesChange(preferences.copy(globalRules = preferences.globalRules.filterNot { it.id == rule.id }))
                    }
                )
            }

            SharedTtsReplacementScope.BOOK -> {
                SharedTtsBookReplacementSettings(
                    settings = bookSettings,
                    onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) }
                )
                SharedTtsInheritedGlobalRules(
                    globalRules = preferences.globalRules,
                    settings = bookSettings,
                    onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) }
                )
                SharedTtsReplacementSuggestionsRow { suggestion ->
                    onPreferencesChange(
                        preferences.withBookRules(
                            bookId,
                            bookRules + suggestion.asDesktopEditableRule(
                                prefix = "book",
                                existingRules = bookRules
                            )
                        )
                    )
                }
                TextButton(onClick = { isAddingRule = true; editingRuleId = null }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(readerString("tts_replacements_add_book_rule", "Add book rule"))
                }
                val editingRule = editingRuleId?.let { id -> bookRules.firstOrNull { it.id == id } }
                if (isAddingRule || editingRule != null) {
                    SharedTtsReplacementRuleEditor(
                        seedRule = editingRule,
                        newRuleId = newSharedReplacementRuleId("book", bookRules),
                        onCancel = { isAddingRule = false; editingRuleId = null },
                        onSave = { rule ->
                            val updated = if (editingRule == null) {
                                bookRules + rule
                            } else {
                                bookRules.map { if (it.id == editingRule.id) rule else it }
                            }
                            onPreferencesChange(preferences.withBookRules(bookId, updated))
                            isAddingRule = false
                            editingRuleId = null
                        }
                    )
                }
                SharedTtsReplacementRuleList(
                    rules = bookRules,
                    emptyText = readerString("tts_replacements_empty_book", "No book-specific rules yet."),
                    onToggle = { rule, enabled ->
                        onPreferencesChange(
                            preferences.withBookRules(
                                bookId,
                                bookRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it }
                            )
                        )
                    },
                    onEdit = { rule -> editingRuleId = rule.id; isAddingRule = false },
                    onDelete = { rule ->
                        onPreferencesChange(preferences.withBookRules(bookId, bookRules.filterNot { it.id == rule.id }))
                    }
                )
            }
        }
    }
}

@Composable
internal fun SharedTtsReplacementSuggestionsRow(
    onSuggestionClick: (ReaderTtsReplacementRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(readerString("tts_replacements_suggestions", "Suggestions"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState())
        ) {
            ReaderTtsReplacementSuggestions.presets.forEach { suggestion ->
                FilterChip(
                    selected = false,
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion.desktopSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                )
            }
        }
    }
}

@Composable
internal fun SharedTtsBookReplacementSettings(
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(readerString("tts_replacements_use_global_here", "Use global rules here"), modifier = Modifier.weight(1f))
            Switch(
                checked = settings.globalRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(globalRulesEnabled = it)) }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(readerString("tts_replacements_enable_book_rules", "Enable book rules"), modifier = Modifier.weight(1f))
            Switch(
                checked = settings.localRulesEnabled,
                onCheckedChange = { onSettingsChange(settings.copy(localRulesEnabled = it)) }
            )
        }
    }
}

@Composable
internal fun SharedTtsInheritedGlobalRules(
    globalRules: List<ReaderTtsReplacementRule>,
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(readerString("tts_replacements_inherited_global_rules", "Inherited global rules"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (globalRules.isEmpty()) {
            Text(readerString("tts_replacements_no_global_rules", "No global rules to inherit."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            globalRules.forEach { rule ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rule.desktopSummary(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (rule.id in settings.disabledGlobalRuleIds) {
                                readerString("tts_replacements_disabled_for_book", "Disabled for this book")
                            } else {
                                readerString("tts_replacements_allowed_in_book", "Allowed in this book")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = rule.id !in settings.disabledGlobalRuleIds,
                        onCheckedChange = { enabled ->
                            val disabledIds = if (enabled) {
                                settings.disabledGlobalRuleIds - rule.id
                            } else {
                                settings.disabledGlobalRuleIds + rule.id
                            }
                            onSettingsChange(settings.copy(disabledGlobalRuleIds = disabledIds))
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharedTtsReplacementRuleEditor(
    seedRule: ReaderTtsReplacementRule?,
    newRuleId: String,
    onCancel: () -> Unit,
    onSave: (ReaderTtsReplacementRule) -> Unit
) {
    val seedId = seedRule?.id ?: newRuleId
    var from by remember(seedId) { mutableStateOf(seedRule?.from.orEmpty()) }
    var to by remember(seedId) { mutableStateOf(seedRule?.to.orEmpty()) }
    var enabled by remember(seedId) { mutableStateOf(seedRule?.enabled ?: true) }
    var isRegex by remember(seedId) { mutableStateOf(seedRule?.isRegex ?: false) }
    var wholeWord by remember(seedId) { mutableStateOf(seedRule?.wholeWord ?: true) }
    var matchCase by remember(seedId) { mutableStateOf(seedRule?.matchCase ?: false) }
    val defaultPreviewText = readerString("tts_replacements_preview_default", "Dr. Smith met NASA.")
    var previewText by remember(seedId, defaultPreviewText) {
        mutableStateOf(seedRule?.from?.takeIf { it.isNotBlank() } ?: defaultPreviewText)
    }
    val draft = ReaderTtsReplacementRule(
        id = seedId,
        from = from,
        to = to,
        enabled = enabled,
        isRegex = isRegex,
        matchCase = matchCase,
        wholeWord = wholeWord
    )
    val validation = ReaderTtsReplacementEngine.validate(draft)
    val previewOutput = if (validation.isValid) {
        ReaderTtsReplacementEngine.apply(
            text = previewText,
            preferences = ReaderTtsReplacementPreferences(globalRules = listOf(draft.copy(enabled = true)))
        ).text
    } else {
        previewText
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (seedRule == null) {
                    readerString("tts_replacements_new_replacement", "New replacement")
                } else {
                    readerString("tts_replacements_edit_replacement", "Edit replacement")
                },
                fontWeight = FontWeight.SemiBold
            )
            SharedStableOutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text(readerString("tts_replacements_label_replace", "Replace")) },
                modifier = Modifier.fillMaxWidth(),
                isError = !validation.isValid
            )
            if (!validation.isValid && validation.message != null) {
                Text(validation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            SharedStableOutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text(readerString("tts_replacements_label_speak_as", "Speak as")) },
                modifier = Modifier.fillMaxWidth()
            )
            SharedReaderChoiceRow {
                FilterChip(selected = enabled, onClick = { enabled = !enabled }, label = { Text(readerString("tts_replacements_chip_enabled", "Enabled")) })
                FilterChip(selected = isRegex, onClick = { isRegex = !isRegex }, label = { Text(readerString("tts_replacements_chip_regex", "Regex")) })
                FilterChip(selected = wholeWord, onClick = { wholeWord = !wholeWord }, label = { Text(readerString("tts_replacements_chip_whole_word", "Whole word")) })
                FilterChip(selected = matchCase, onClick = { matchCase = !matchCase }, label = { Text(readerString("tts_replacements_chip_match_case", "Match case")) })
            }
            SharedStableOutlinedTextField(
                value = previewText,
                onValueChange = { previewText = it },
                label = { Text(readerString("tts_replacements_label_preview_input", "Preview input")) },
                modifier = Modifier.fillMaxWidth()
            )
            Text(previewOutput, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onCancel) { Text(readerString("action_cancel", "Cancel")) }
                TextButton(enabled = validation.isValid, onClick = { onSave(draft) }) { Text(readerString("action_save", "Save")) }
            }
        }
    }
}

@Composable
internal fun SharedTtsReplacementRuleList(
    rules: List<ReaderTtsReplacementRule>,
    emptyText: String,
    onToggle: (ReaderTtsReplacementRule, Boolean) -> Unit,
    onEdit: (ReaderTtsReplacementRule) -> Unit,
    onDelete: (ReaderTtsReplacementRule) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (rules.isEmpty()) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            rules.forEach { rule ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(rule.desktopSummary(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rule.desktopOptions(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = rule.enabled, onCheckedChange = { onToggle(rule, it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onEdit(rule) }) { Text(readerString("action_edit", "Edit")) }
                        TextButton(onClick = { onDelete(rule) }) { Text(readerString("action_delete", "Delete")) }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

internal fun ReaderTtsReplacementRule.asDesktopEditableRule(
    prefix: String,
    existingRules: List<ReaderTtsReplacementRule>
): ReaderTtsReplacementRule {
    return copy(
        id = newSharedReplacementRuleId(prefix, existingRules + this),
        enabled = true
    )
}

@Composable
internal fun ReaderTtsReplacementRule.desktopSummary(): String {
    val replacement = to.ifBlank { readerString("tts_replacements_silence", "silence") }
    return readerString("tts_replacements_summary_format", "%1\$s -> %2\$s", from, replacement)
}

@Composable
internal fun ReaderTtsReplacementRule.desktopOptions(): String {
    val options = buildList {
        add(
            if (isRegex) {
                readerString("tts_replacements_chip_regex", "Regex")
            } else {
                readerString("tts_replacements_plain_text", "Plain text")
            }
        )
        if (wholeWord) add(readerString("tts_replacements_chip_whole_word", "whole word"))
        if (matchCase) add(readerString("tts_replacements_case_sensitive", "case-sensitive"))
    }
    return options.joinToString(" - ")
}

internal fun newSharedReplacementRuleId(
    prefix: String,
    existingRules: List<ReaderTtsReplacementRule>
): String {
    val stableSuffix = existingRules.joinToString("|") { it.id }.hashCode().toString().replace("-", "n")
    return "${prefix}_${existingRules.size + 1}_$stableSuffix"
}

@Composable
fun SharedReaderToolbarControls(
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit,
    availableTools: Set<ReaderTool> = ReaderTool.entries.toSet()
) {
    val orderedTools = toolbarPreferences.sanitized().toolOrder.filter { it in availableTools }
    val toolbarTools = orderedTools.filter { it.category != "Overflow Menu" }
    val moreTools = orderedTools.filter { it.category == "Overflow Menu" }
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        SharedToolbarSection(
            title = readerString("toolbar_top_bar", "Top Bar"),
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && !toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = readerString("toolbar_bottom_bar", "Bottom Bar"),
            tools = toolbarTools.filter {
                toolbarPreferences.isVisible(it) && toolbarPreferences.isBottom(it)
            },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = readerString("toolbar_more_menu", "More menu"),
            tools = moreTools.filter { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
        SharedToolbarSection(
            title = readerString("toolbar_hidden_tools", "Hidden Tools"),
            tools = orderedTools.filterNot { toolbarPreferences.isVisible(it) },
            toolbarPreferences = toolbarPreferences,
            onToolbarPreferencesChange = onToolbarPreferencesChange
        )
    }
}

@Composable
internal fun SharedToolbarSection(
    title: String,
    tools: List<ReaderTool>,
    toolbarPreferences: ReaderToolbarPreferences,
    onToolbarPreferencesChange: (ReaderToolbarPreferences) -> Unit
) {
    SharedReaderPanelSection(title) {
        if (tools.isEmpty()) {
            Text(readerString("toolbar_no_tools", "No tools"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            tools.forEach { tool ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(tool.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = toolbarPreferences.isVisible(tool),
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withVisibility(tool, hidden = toolbarPreferences.isVisible(tool))
                                )
                            },
                            label = { Text(readerString("toolbar_visible", "Visible")) }
                        )
                        FilterChip(
                            selected = toolbarPreferences.isBottom(tool),
                            enabled = tool.category != "Overflow Menu",
                            onClick = {
                                onToolbarPreferencesChange(
                                    toolbarPreferences.withBottomPlacement(tool, bottom = !toolbarPreferences.isBottom(tool))
                                )
                            },
                            label = { Text(readerString("label_bottom", "Bottom")) }
                        )
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) > 0,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, -1)) }
                        ) {
                            Text(readerString("action_up", "Up"))
                        }
                        TextButton(
                            enabled = toolbarPreferences.toolOrder.indexOf(tool) in 0 until toolbarPreferences.toolOrder.lastIndex,
                            onClick = { onToolbarPreferencesChange(toolbarPreferences.moveTool(tool, 1)) }
                        ) {
                            Text(readerString("action_down", "Down"))
                        }
                    }
                }
                if (tool != tools.last()) {
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
internal fun SharedReaderPanelSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun SharedReaderChoiceRow(
    content: @Composable () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) {
        content()
    }
}

@Composable
internal fun SharedReaderSettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    stepSize: Float = 0.05f,
    formatValue: ((Float) -> String)? = null,
    debounceMillis: Long = 320L
) {
    val rangeStart = valueRange.start
    val rangeEnd = valueRange.endInclusive
    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(rangeStart, rangeEnd)
        if (stepSize <= 0f) return clamped
        val steps = ((clamped - rangeStart) / stepSize).roundToInt()
        return (rangeStart + steps * stepSize).coerceIn(rangeStart, rangeEnd)
    }

    var draftValue by remember(label, rangeStart, rangeEnd) { mutableFloatStateOf(snap(value)) }
    var pendingCommit by remember(label, rangeStart, rangeEnd) { mutableStateOf<Float?>(null) }
    var isDragging by remember(label, rangeStart, rangeEnd) { mutableStateOf(false) }
    var lastCommitted by remember(label, rangeStart, rangeEnd) { mutableFloatStateOf(snap(value)) }
    val normalizedExternalValue = snap(value)

    LaunchedEffect(normalizedExternalValue) {
        if (pendingCommit == null) {
            draftValue = normalizedExternalValue
            lastCommitted = normalizedExternalValue
        }
    }

    fun commit(next: Float) {
        val snapped = snap(next)
        draftValue = snapped
        if (snapped != lastCommitted) {
            lastCommitted = snapped
            onValueChange(snapped)
        }
    }

    LaunchedEffect(pendingCommit, isDragging) {
        if (isDragging) return@LaunchedEffect
        val pending = pendingCommit ?: return@LaunchedEffect
        delay(debounceMillis)
        commit(pending)
        if (pendingCommit == pending) {
            pendingCommit = null
        }
    }

    fun updateDraft(next: Float) {
        val snapped = snap(next)
        draftValue = snapped
        pendingCommit = snapped
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatValue?.invoke(draftValue) ?: valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = {
                    isDragging = false
                    val next = snap(draftValue - stepSize)
                    pendingCommit = null
                    commit(next)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Remove,
                    contentDescription = readerString("desktop_decrease_format", "Decrease %1\$s", label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            ReaderMinimalSlider(
                value = draftValue,
                onValueChange = ::updateDraft,
                onValueChangeStarted = { isDragging = true },
                onValueChangeFinished = {
                    isDragging = false
                    pendingCommit?.let { commit(it) }
                    pendingCommit = null
                },
                valueRange = valueRange,
                activeColor = MaterialTheme.colorScheme.primary,
                inactiveColor = MaterialTheme.colorScheme.surfaceVariant,
                thumbColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    isDragging = false
                    val next = snap(draftValue + stepSize)
                    pendingCommit = null
                    commit(next)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = readerString("desktop_increase_format", "Increase %1\$s", label),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun SharedReaderThemeChoice(
    theme: ReaderTheme,
    selected: Boolean,
    onSelected: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val swatch = if (theme.backgroundColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surface
    } else {
        theme.backgroundColor
    }
    val textColor = if (theme.textColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        theme.textColor
    }
    Column(
        modifier = modifier.clickable(onClick = onSelected),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer else swatch,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(32.dp)
                    .background(swatch, RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("Aa", color = textColor, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            theme.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (theme.isCustom && (onEdit != null || onDelete != null)) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onEdit != null) {
                    IconButton(onClick = onEdit, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = readerString("action_edit", "Edit"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = readerString("action_delete", "Delete"),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}
