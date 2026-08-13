package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class SharedTtsWordReplacementLabels(
    val title: String, val currentBook: String, val close: String,
    val globalTab: String, val thisBookTab: String,
    val enable: String, val enableDescription: String,
    val addRule: String, val addBookRule: String,
    val emptyGlobal: String, val emptyBook: String,
    val useGlobalHere: String, val useGlobalHereDescription: String,
    val enableBookRules: String, val enableBookRulesDescription: String,
    val inheritedGlobalRules: String, val noGlobalRules: String,
    val allowedInBook: String, val disabledForBook: String,
    val silence: String, val suggestions: String,
    val previewDefault: String, val newReplacement: String, val editReplacement: String,
    val replace: String, val speakAs: String, val enabled: String,
    val regex: String, val wholeWord: String, val matchCase: String,
    val previewInput: String, val cancel: String, val save: String,
    val rules: String, val edit: String, val delete: String,
    val plainText: String, val caseSensitive: String,
)

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
fun SharedTtsWordReplacementsSheet(
    isVisible: Boolean,
    bookId: String,
    bookTitle: String?,
    preferences: ReaderTtsReplacementPreferences,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onDismiss: () -> Unit,
    labels: SharedTtsWordReplacementLabels,
    newRuleId: (String) -> String,
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
                        text = labels.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = bookTitle?.takeIf { it.isNotBlank() } ?: labels.currentBook,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = labels.close)
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
                    text = { Text(labels.globalTab) },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        editTarget = null
                    },
                    text = { Text(labels.thisBookTab) },
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (selectedTab) {
                0 -> GlobalReplacementTab(
                    preferences = preferences,
                    editTarget = editTarget?.takeIf { it.scope == TtsReplacementScope.Global },
                    onEditTargetChange = { editTarget = it },
                    onPreferencesChange = onPreferencesChange,
                    labels = labels,
                    newRuleId = newRuleId,
                )
                else -> BookReplacementTab(
                    bookId = bookId,
                    preferences = preferences,
                    editTarget = editTarget?.takeIf { it.scope == TtsReplacementScope.Book },
                    onEditTargetChange = { editTarget = it },
                    onPreferencesChange = onPreferencesChange,
                    labels = labels,
                    newRuleId = newRuleId,
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
    labels: SharedTtsWordReplacementLabels,
    newRuleId: (String) -> String,
) {
    val editingRule = editTarget?.ruleId?.let { id -> preferences.globalRules.firstOrNull { it.id == id } }
    LazyColumn(
        modifier = Modifier.heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ListItem(
                headlineContent = { Text(labels.enable) },
                supportingContent = { Text(labels.enableDescription) },
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
                labels = labels,
                onSuggestionClick = { suggestion ->
                    onPreferencesChange(
                        preferences.copy(
                            globalRules = preferences.globalRules + suggestion.asEditableRule(newRuleId("global_${suggestion.id}")),
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
                Text(labels.addRule)
            }
        }
        if (editTarget != null) {
            item {
                RuleEditorCard(
                    labels = labels,
                    newRuleId = newRuleId,
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
                labels = labels,
                rules = preferences.globalRules,
                emptyText = labels.emptyGlobal,
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
    labels: SharedTtsWordReplacementLabels,
    newRuleId: (String) -> String,
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
                labels = labels,
                settings = settings,
                onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) },
            )
        }
        item {
            InheritedGlobalRules(
                labels = labels,
                globalRules = preferences.globalRules,
                settings = settings,
                onSettingsChange = { onPreferencesChange(preferences.withBookSettings(bookId, it)) },
            )
        }
        item {
            SuggestionChips(
                labels = labels,
                onSuggestionClick = { suggestion ->
                    onPreferencesChange(
                        preferences.withBookRules(
                            bookId,
                            localRules + suggestion.asEditableRule(newRuleId("book_${suggestion.id}")),
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
                Text(labels.addBookRule)
            }
        }
        if (editTarget != null) {
            item {
                RuleEditorCard(
                    labels = labels,
                    newRuleId = newRuleId,
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
                labels = labels,
                rules = localRules,
                emptyText = labels.emptyBook,
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
    labels: SharedTtsWordReplacementLabels,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text(labels.useGlobalHere) },
                supportingContent = { Text(labels.useGlobalHereDescription) },
                trailingContent = {
                    Switch(
                        checked = settings.globalRulesEnabled,
                        onCheckedChange = { onSettingsChange(settings.copy(globalRulesEnabled = it)) },
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(labels.enableBookRules) },
                supportingContent = { Text(labels.enableBookRulesDescription) },
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
    labels: SharedTtsWordReplacementLabels,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = labels.inheritedGlobalRules,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (globalRules.isEmpty()) {
            Text(
                text = labels.noGlobalRules,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return
        }
        globalRules.forEach { rule ->
            val enabledHere = rule.id !in settings.disabledGlobalRuleIds
            val silenceLabel = labels.silence
            ListItem(
                headlineContent = { Text(rule.summaryText(silenceLabel)) },
                supportingContent = { Text(if (enabledHere) labels.allowedInBook else labels.disabledForBook) },
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
    labels: SharedTtsWordReplacementLabels,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = labels.suggestions,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ReaderTtsReplacementSuggestions.presets) { suggestion ->
                val silenceLabel = labels.silence
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
    labels: SharedTtsWordReplacementLabels,
    newRuleId: (String) -> String,
) {
    val draftRuleId = remember(seedRule?.id) { seedRule?.id ?: newRuleId("rule") }
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
    val defaultPreviewInput = labels.previewDefault
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
                text = if (seedRule == null) labels.newReplacement else labels.editReplacement,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(labels.replace) },
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
                label = { Text(labels.speakAs) },
                singleLine = !isRegex,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = enabled,
                        onClick = { enabled = !enabled },
                        label = { Text(labels.enabled) },
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
                        label = { Text(labels.regex) },
                    )
                }
                item {
                    FilterChip(
                        selected = wholeWord,
                        onClick = { wholeWord = !wholeWord },
                        label = { Text(labels.wholeWord) },
                    )
                }
                item {
                    FilterChip(
                        selected = matchCase,
                        onClick = { matchCase = !matchCase },
                        label = { Text(labels.matchCase) },
                    )
                }
            }
            OutlinedTextField(
                value = previewInput,
                onValueChange = { previewInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(labels.previewInput) },
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
                    Text(labels.cancel)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSave(draft) },
                    enabled = validation.isValid,
                ) {
                    Text(labels.save)
                }
            }
        }
    }
}

@Composable
private fun ReplacementRuleList(
    rules: List<ReaderTtsReplacementRule>,
    emptyText: String,
    onToggle: (ReaderTtsReplacementRule, Boolean) -> Unit,
    onEdit: (ReaderTtsReplacementRule) -> Unit,
    onDelete: (ReaderTtsReplacementRule) -> Unit,
    labels: SharedTtsWordReplacementLabels,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = labels.rules,
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
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }
        rules.forEach { rule ->
            val silenceLabel = labels.silence
            ListItem(
                headlineContent = {
                    Text(
                        text = rule.summaryText(silenceLabel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    Text(rule.optionSummary(labels))
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.enabled,
                            onCheckedChange = { onToggle(rule, it) },
                        )
                        IconButton(onClick = { onEdit(rule) }) {
                            Icon(Icons.Default.Edit, contentDescription = labels.edit)
                        }
                        IconButton(onClick = { onDelete(rule) }) {
                            Icon(Icons.Default.Delete, contentDescription = labels.delete)
                        }
                    }
                },
            )
        }
    }
}

private fun ReaderTtsReplacementRule.asEditableRule(id: String): ReaderTtsReplacementRule = copy(id = id, enabled = true)

private fun ReaderTtsReplacementRule.summaryText(silenceLabel: String): String {
    val replacement = to.ifBlank { silenceLabel }
    return "$from -> $replacement"
}

private fun ReaderTtsReplacementRule.optionSummary(labels: SharedTtsWordReplacementLabels): String {
    val regexLabel = labels.regex
    val plainTextLabel = labels.plainText
    val wholeWordLabel = labels.wholeWord
    val caseSensitiveLabel = labels.caseSensitive
    val parts = buildList {
        add(if (isRegex) regexLabel else plainTextLabel)
        if (wholeWord) add(wholeWordLabel)
        if (matchCase) add(caseSensitiveLabel)
    }
    return parts.joinToString(" - ")
}


