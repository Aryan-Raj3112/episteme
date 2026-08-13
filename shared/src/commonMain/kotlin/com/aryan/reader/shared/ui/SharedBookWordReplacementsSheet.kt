package com.aryan.reader.shared.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderBookReplacementEngine
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ReaderWordReplacementRule

data class SharedBookWordReplacementLabels(
    val title: String,
    val currentBook: String,
    val close: String,
    val addRule: String,
    val empty: String,
    val previewDefault: String,
    val newReplacement: String,
    val editReplacement: String,
    val replace: String,
    val with: String,
    val enabled: String,
    val regex: String,
    val wholeWord: String,
    val matchCase: String,
    val previewInput: String,
    val cancel: String,
    val save: String,
    val rules: String,
    val emptyReplacement: String,
    val edit: String,
    val delete: String,
    val plainText: String,
    val caseSensitive: String,
)

private data class BookRuleEditTarget(
    val ruleId: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedBookWordReplacementsSheet(
    isVisible: Boolean,
    bookId: String,
    bookTitle: String?,
    preferences: ReaderBookReplacementPreferences,
    onPreferencesChange: (ReaderBookReplacementPreferences) -> Unit,
    onDismiss: () -> Unit,
    labels: SharedBookWordReplacementLabels,
    newRuleId: () -> String,
) {
    if (!isVisible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editTarget by remember(bookId) { mutableStateOf<BookRuleEditTarget?>(null) }
    val rules = preferences.rulesForFile(bookId)
    val editingRule = editTarget?.ruleId?.let { id -> rules.firstOrNull { it.id == id } }

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

            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TextButton(
                        onClick = { editTarget = BookRuleEditTarget() },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(labels.addRule)
                    }
                }
                if (editTarget != null) {
                    item {
                        BookRuleEditorCard(
                            seedRule = editingRule,
                            labels = labels,
                            newRuleId = newRuleId,
                            onCancel = { editTarget = null },
                            onSave = { rule ->
                                val updatedRules = if (editingRule == null) {
                                    rules + rule
                                } else {
                                    rules.map { if (it.id == editingRule.id) rule else it }
                                }
                                onPreferencesChange(preferences.withFileRules(bookId, updatedRules))
                                editTarget = null
                            },
                        )
                    }
                }
                item {
                    BookReplacementRuleList(
                        rules = rules,
                        labels = labels,
                        emptyText = labels.empty,
                        onToggle = { rule, enabled ->
                            onPreferencesChange(
                                preferences.withFileRules(
                                    bookId,
                                    rules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it },
                                ),
                            )
                        },
                        onEdit = { editTarget = BookRuleEditTarget(it.id) },
                        onDelete = { rule ->
                            onPreferencesChange(preferences.withFileRules(bookId, rules.filterNot { it.id == rule.id }))
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BookRuleEditorCard(
    seedRule: ReaderWordReplacementRule?,
    labels: SharedBookWordReplacementLabels,
    newRuleId: () -> String,
    onCancel: () -> Unit,
    onSave: (ReaderWordReplacementRule) -> Unit,
) {
    val draftRuleId = remember(seedRule?.id) { seedRule?.id ?: newRuleId() }
    val initial = seedRule ?: ReaderWordReplacementRule(
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

    val draft = ReaderWordReplacementRule(
        id = initial.id,
        from = from,
        to = to,
        enabled = enabled,
        isRegex = isRegex,
        matchCase = matchCase,
        wholeWord = wholeWord,
    )
    val validation = ReaderBookReplacementEngine.validate(draft)
    val previewOutput = if (validation.isValid) {
        ReaderBookReplacementEngine.apply(
            text = previewInput,
            preferences = ReaderBookReplacementPreferences(fileRules = mapOf("preview" to listOf(draft.copy(enabled = true)))),
            fileId = "preview",
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
                label = { Text(labels.with) },
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
private fun BookReplacementRuleList(
    rules: List<ReaderWordReplacementRule>,
    labels: SharedBookWordReplacementLabels,
    emptyText: String,
    onToggle: (ReaderWordReplacementRule, Boolean) -> Unit,
    onEdit: (ReaderWordReplacementRule) -> Unit,
    onDelete: (ReaderWordReplacementRule) -> Unit,
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
            val emptyLabel = labels.emptyReplacement
            ListItem(
                headlineContent = {
                    Text(
                        text = rule.summaryText(emptyLabel),
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

private fun ReaderWordReplacementRule.summaryText(emptyLabel: String): String {
    val replacement = to.ifBlank { emptyLabel }
    return "$from -> $replacement"
}

private fun ReaderWordReplacementRule.optionSummary(labels: SharedBookWordReplacementLabels): String {
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


