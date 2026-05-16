package com.aryan.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTtsReplacementBookSettings
import com.aryan.reader.shared.ReaderTtsReplacementEngine
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ReaderTtsReplacementRule
import com.aryan.reader.shared.ReaderTtsReplacementSuggestions

private enum class TtsReplacementScope {
    Global,
    Book
}

private data class RuleEditTarget(
    val scope: TtsReplacementScope,
    val ruleId: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsWordReplacementsSheet(
    isVisible: Boolean,
    bookId: String,
    bookTitle: String?,
    preferences: ReaderTtsReplacementPreferences,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedTab by remember { mutableIntStateOf(0) }
    var editTarget by remember { mutableStateOf<RuleEditTarget?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.menu_tts_word_replacements),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = bookTitle?.takeIf { it.isNotBlank() } ?: stringResource(R.string.tts_replacements_current_book),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = {
                        selectedTab = 0
                        editTarget = null
                    },
                    text = { Text(stringResource(R.string.tts_replacements_tab_global)) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        editTarget = null
                    },
                    text = { Text(stringResource(R.string.tts_replacements_tab_this_book)) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> GlobalReplacementTab(
                    preferences = preferences,
                    editTarget = editTarget?.takeIf { it.scope == TtsReplacementScope.Global },
                    onEditTargetChange = { editTarget = it },
                    onPreferencesChange = onPreferencesChange,
                )
                else -> BookReplacementTab(
                    bookId = bookId,
                    preferences = preferences,
                    editTarget = editTarget?.takeIf { it.scope == TtsReplacementScope.Book },
                    onEditTargetChange = { editTarget = it },
                    onPreferencesChange = onPreferencesChange,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GlobalReplacementTab(
    preferences: ReaderTtsReplacementPreferences,
    editTarget: RuleEditTarget?,
    onEditTargetChange: (RuleEditTarget?) -> Unit,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
) {
    val editingRule = editTarget?.ruleId?.let { id -> preferences.globalRules.firstOrNull { it.id == id } }
    LazyColumn(
        modifier = Modifier.heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ListItem(
                headlineContent = { Text(stringResource(R.string.tts_replacements_enable)) },
                supportingContent = { Text(stringResource(R.string.tts_replacements_enable_desc)) },
                trailingContent = {
                    Switch(
                        checked = preferences.isEnabled,
                        onCheckedChange = { onPreferencesChange(preferences.copy(isEnabled = it)) },
                    )
                },
            )
        }
        item {
            SuggestionChips(
                onSuggestionClick = { suggestion ->
                    onPreferencesChange(
                        preferences.copy(
                            globalRules = preferences.globalRules + suggestion.asEditableRule("global"),
                        ),
                    )
                },
            )
        }
        item {
            TextButton(
                onClick = { onEditTargetChange(RuleEditTarget(TtsReplacementScope.Global)) },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tts_replacements_add_rule))
            }
        }
        if (editTarget != null) {
            item {
                RuleEditorCard(
                    seedRule = editingRule,
                    onCancel = { onEditTargetChange(null) },
                    onSave = { rule ->
                        val updatedRules = if (editingRule == null) {
                            preferences.globalRules + rule
                        } else {
                            preferences.globalRules.map { if (it.id == editingRule.id) rule else it }
                        }
                        onPreferencesChange(preferences.copy(globalRules = updatedRules))
                        onEditTargetChange(null)
                    },
                )
            }
        }
        item {
            ReplacementRuleList(
                rules = preferences.globalRules,
                emptyTextRes = R.string.tts_replacements_empty_global,
                onToggle = { rule, enabled ->
                    onPreferencesChange(
                        preferences.copy(
                            globalRules = preferences.globalRules.map {
                                if (it.id == rule.id) it.copy(enabled = enabled) else it
                            },
                        ),
                    )
                },
                onEdit = { onEditTargetChange(RuleEditTarget(TtsReplacementScope.Global, it.id)) },
                onDelete = { rule ->
                    onPreferencesChange(
                        preferences.copy(globalRules = preferences.globalRules.filterNot { it.id == rule.id }),
                    )
                },
            )
        }
    }
}

@Composable
private fun BookReplacementTab(
    bookId: String,
    preferences: ReaderTtsReplacementPreferences,
    editTarget: RuleEditTarget?,
    onEditTargetChange: (RuleEditTarget?) -> Unit,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
) {
    val settings = preferences.settingsForBook(bookId)
    val localRules = preferences.rulesForBook(bookId)
    val editingRule = editTarget?.ruleId?.let { id -> localRules.firstOrNull { it.id == id } }

    LazyColumn(
        modifier = Modifier.heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BookSettingsSwitches(
                settings = settings,
                onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) },
            )
        }
        item {
            InheritedGlobalRules(
                globalRules = preferences.globalRules,
                settings = settings,
                onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) },
            )
        }
        item {
            SuggestionChips(
                onSuggestionClick = { suggestion ->
                    onPreferencesChange(
                        preferences.withBookRules(
                            bookId,
                            localRules + suggestion.asEditableRule("book"),
                        ),
                    )
                },
            )
        }
        item {
            TextButton(
                onClick = { onEditTargetChange(RuleEditTarget(TtsReplacementScope.Book)) },
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.tts_replacements_add_book_rule))
            }
        }
        if (editTarget != null) {
            item {
                RuleEditorCard(
                    seedRule = editingRule,
                    onCancel = { onEditTargetChange(null) },
                    onSave = { rule ->
                        val updatedRules = if (editingRule == null) {
                            localRules + rule
                        } else {
                            localRules.map { if (it.id == editingRule.id) rule else it }
                        }
                        onPreferencesChange(preferences.withBookRules(bookId, updatedRules))
                        onEditTargetChange(null)
                    },
                )
            }
        }
        item {
            ReplacementRuleList(
                rules = localRules,
                emptyTextRes = R.string.tts_replacements_empty_book,
                onToggle = { rule, enabled ->
                    onPreferencesChange(
                        preferences.withBookRules(
                            bookId,
                            localRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it },
                        ),
                    )
                },
                onEdit = { onEditTargetChange(RuleEditTarget(TtsReplacementScope.Book, it.id)) },
                onDelete = { rule ->
                    onPreferencesChange(preferences.withBookRules(bookId, localRules.filterNot { it.id == rule.id }))
                },
            )
        }
    }
}

@Composable
private fun BookSettingsSwitches(
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.tts_replacements_use_global_here)) },
                supportingContent = { Text(stringResource(R.string.tts_replacements_use_global_here_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.globalRulesEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(globalRulesEnabled = it)) },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.tts_replacements_enable_book_rules)) },
                supportingContent = { Text(stringResource(R.string.tts_replacements_enable_book_rules_desc)) },
                trailingContent = {
                    Switch(
                        checked = settings.localRulesEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(localRulesEnabled = it)) },
                    )
                },
            )
        }
    }
}

@Composable
private fun InheritedGlobalRules(
    globalRules: List<ReaderTtsReplacementRule>,
    settings: ReaderTtsReplacementBookSettings,
    onSettingsChange: (ReaderTtsReplacementBookSettings) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tts_replacements_inherited_global_rules),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (globalRules.isEmpty()) {
            Text(
                text = stringResource(R.string.tts_replacements_no_global_rules),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        globalRules.forEach { rule ->
            val enabledHere = rule.id !in settings.disabledGlobalRuleIds
            val silenceLabel = stringResource(R.string.tts_replacements_silence)
            ListItem(
                headlineContent = { Text(rule.summaryText(silenceLabel)) },
                supportingContent = { Text(stringResource(if (enabledHere) R.string.tts_replacements_allowed_in_book else R.string.tts_replacements_disabled_for_book)) },
                trailingContent = {
                    Switch(
                        checked = enabledHere,
                        onCheckedChange = { checked ->
                            val disabledIds = if (checked) {
                                settings.disabledGlobalRuleIds - rule.id
                            } else {
                                settings.disabledGlobalRuleIds + rule.id
                            }
                            onSettingsChange(settings.copy(disabledGlobalRuleIds = disabledIds))
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun SuggestionChips(
    onSuggestionClick: (ReaderTtsReplacementRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tts_replacements_suggestions),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReaderTtsReplacementSuggestions.presets) { suggestion ->
                val silenceLabel = stringResource(R.string.tts_replacements_silence)
                AssistChip(
                    onClick = { onSuggestionClick(suggestion) },
                    label = { Text(suggestion.summaryText(silenceLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun RuleEditorCard(
    seedRule: ReaderTtsReplacementRule?,
    onCancel: () -> Unit,
    onSave: (ReaderTtsReplacementRule) -> Unit,
) {
    val draftRuleId = remember(seedRule?.id) { seedRule?.id ?: newReplacementRuleId() }
    val initial = seedRule ?: ReaderTtsReplacementRule(
        id = draftRuleId,
        from = "",
        to = "",
    )
    var from by remember(initial.id) { mutableStateOf(initial.from) }
    var to by remember(initial.id) { mutableStateOf(initial.to) }
    var enabled by remember(initial.id) { mutableStateOf(initial.enabled) }
    var isRegex by remember(initial.id) { mutableStateOf(initial.isRegex) }
    var wholeWord by remember(initial.id) { mutableStateOf(initial.wholeWord) }
    var matchCase by remember(initial.id) { mutableStateOf(initial.matchCase) }
    val defaultPreviewInput = stringResource(R.string.tts_replacements_preview_default)
    var previewInput by remember(initial.id, defaultPreviewInput) {
        mutableStateOf(initial.from.takeIf { it.isNotBlank() } ?: defaultPreviewInput)
    }

    val draft = ReaderTtsReplacementRule(
        id = initial.id,
        from = from,
        to = to,
        enabled = enabled,
        isRegex = isRegex,
        matchCase = matchCase,
        wholeWord = wholeWord,
    )
    val validation = ReaderTtsReplacementEngine.validate(draft)
    val previewOutput = if (validation.isValid) {
        ReaderTtsReplacementEngine.apply(
            text = previewInput,
            preferences = ReaderTtsReplacementPreferences(globalRules = listOf(draft.copy(enabled = true))),
        ).text
    } else {
        previewInput
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (seedRule == null) R.string.tts_replacements_new_replacement else R.string.tts_replacements_edit_replacement),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tts_replacements_label_replace)) },
                singleLine = !isRegex,
                isError = !validation.isValid,
                supportingText = if (validation.message != null) {
                    { Text(validation.message.orEmpty()) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Text,
                ),
            )
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tts_replacements_label_speak_as)) },
                singleLine = !isRegex,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = enabled,
                        onClick = { enabled = !enabled },
                        label = { Text(stringResource(R.string.tts_replacements_chip_enabled)) },
                        leadingIcon = if (enabled) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    )
                }
                item {
                    FilterChip(
                        selected = isRegex,
                        onClick = { isRegex = !isRegex },
                        label = { Text(stringResource(R.string.tts_replacements_chip_regex)) },
                    )
                }
                item {
                    FilterChip(
                        selected = wholeWord,
                        onClick = { wholeWord = !wholeWord },
                        label = { Text(stringResource(R.string.tts_replacements_chip_whole_word)) },
                    )
                }
                item {
                    FilterChip(
                        selected = matchCase,
                        onClick = { matchCase = !matchCase },
                        label = { Text(stringResource(R.string.tts_replacements_chip_match_case)) },
                    )
                }
            }
            OutlinedTextField(
                value = previewInput,
                onValueChange = { previewInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.tts_replacements_label_preview_input)) },
                minLines = 2,
            )
            Text(
                text = previewOutput,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(draft) },
                    enabled = validation.isValid,
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun ReplacementRuleList(
    rules: List<ReaderTtsReplacementRule>,
    @StringRes emptyTextRes: Int,
    onToggle: (ReaderTtsReplacementRule, Boolean) -> Unit,
    onEdit: (ReaderTtsReplacementRule) -> Unit,
    onDelete: (ReaderTtsReplacementRule) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.tts_replacements_rules),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (rules.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(emptyTextRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        rules.forEach { rule ->
            val silenceLabel = stringResource(R.string.tts_replacements_silence)
            ListItem(
                headlineContent = {
                    Text(
                        text = rule.summaryText(silenceLabel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(rule.optionSummary())
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule, it) },
                        )
                        IconButton(onClick = { onEdit(rule) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
                        }
                        IconButton(onClick = { onDelete(rule) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        }
    }
}

private fun ReaderTtsReplacementRule.asEditableRule(scope: String): ReaderTtsReplacementRule {
    return copy(id = "${scope}_${System.currentTimeMillis()}_${id}", enabled = true)
}

private fun ReaderTtsReplacementRule.summaryText(silenceLabel: String): String {
    val replacement = to.ifBlank { silenceLabel }
    return "$from -> $replacement"
}

@Composable
private fun ReaderTtsReplacementRule.optionSummary(): String {
    val regexLabel = stringResource(R.string.tts_replacements_chip_regex)
    val plainTextLabel = stringResource(R.string.tts_replacements_plain_text)
    val wholeWordLabel = stringResource(R.string.tts_replacements_chip_whole_word)
    val caseSensitiveLabel = stringResource(R.string.tts_replacements_case_sensitive)
    val parts = buildList {
        add(if (isRegex) regexLabel else plainTextLabel)
        if (wholeWord) add(wholeWordLabel)
        if (matchCase) add(caseSensitiveLabel)
    }
    return parts.joinToString(" - ")
}

private fun newReplacementRuleId(): String {
    return "rule_${System.currentTimeMillis()}"
}
