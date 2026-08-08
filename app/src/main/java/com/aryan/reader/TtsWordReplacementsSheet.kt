package com.aryan.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aryan.reader.shared.ReaderTtsReplacementPreferences
import com.aryan.reader.shared.ui.SharedTtsWordReplacementLabels
import com.aryan.reader.shared.ui.SharedTtsWordReplacementsSheet

@Composable
fun TtsWordReplacementsSheet(
    isVisible: Boolean,
    bookId: String,
    bookTitle: String?,
    preferences: ReaderTtsReplacementPreferences,
    onPreferencesChange: (ReaderTtsReplacementPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    SharedTtsWordReplacementsSheet(
        isVisible = isVisible,
        bookId = bookId,
        bookTitle = bookTitle,
        preferences = preferences,
        onPreferencesChange = onPreferencesChange,
        onDismiss = onDismiss,
        labels = SharedTtsWordReplacementLabels(
            title = stringResource(R.string.menu_tts_word_replacements),
            currentBook = stringResource(R.string.tts_replacements_current_book),
            close = stringResource(R.string.action_close),
            globalTab = stringResource(R.string.tts_replacements_tab_global),
            thisBookTab = stringResource(R.string.tts_replacements_tab_this_book),
            enable = stringResource(R.string.tts_replacements_enable),
            enableDescription = stringResource(R.string.tts_replacements_enable_desc),
            addRule = stringResource(R.string.tts_replacements_add_rule),
            addBookRule = stringResource(R.string.tts_replacements_add_book_rule),
            emptyGlobal = stringResource(R.string.tts_replacements_empty_global),
            emptyBook = stringResource(R.string.tts_replacements_empty_book),
            useGlobalHere = stringResource(R.string.tts_replacements_use_global_here),
            useGlobalHereDescription = stringResource(R.string.tts_replacements_use_global_here_desc),
            enableBookRules = stringResource(R.string.tts_replacements_enable_book_rules),
            enableBookRulesDescription = stringResource(R.string.tts_replacements_enable_book_rules_desc),
            inheritedGlobalRules = stringResource(R.string.tts_replacements_inherited_global_rules),
            noGlobalRules = stringResource(R.string.tts_replacements_no_global_rules),
            allowedInBook = stringResource(R.string.tts_replacements_allowed_in_book),
            disabledForBook = stringResource(R.string.tts_replacements_disabled_for_book),
            silence = stringResource(R.string.tts_replacements_silence),
            suggestions = stringResource(R.string.tts_replacements_suggestions),
            previewDefault = stringResource(R.string.tts_replacements_preview_default),
            newReplacement = stringResource(R.string.tts_replacements_new_replacement),
            editReplacement = stringResource(R.string.tts_replacements_edit_replacement),
            replace = stringResource(R.string.tts_replacements_label_replace),
            speakAs = stringResource(R.string.tts_replacements_label_speak_as),
            enabled = stringResource(R.string.tts_replacements_chip_enabled),
            regex = stringResource(R.string.tts_replacements_chip_regex),
            wholeWord = stringResource(R.string.tts_replacements_chip_whole_word),
            matchCase = stringResource(R.string.tts_replacements_chip_match_case),
            previewInput = stringResource(R.string.tts_replacements_label_preview_input),
            cancel = stringResource(R.string.action_cancel),
            save = stringResource(R.string.action_save),
            rules = stringResource(R.string.tts_replacements_rules),
            edit = stringResource(R.string.action_edit),
            delete = stringResource(R.string.action_delete),
            plainText = stringResource(R.string.tts_replacements_plain_text),
            caseSensitive = stringResource(R.string.tts_replacements_case_sensitive),
        ),
        newRuleId = { prefix ->
            val timestamp = System.currentTimeMillis()
            when {
                prefix.startsWith("global_") -> "global_${timestamp}_${prefix.removePrefix("global_")}"
                prefix.startsWith("book_") -> "book_${timestamp}_${prefix.removePrefix("book_")}"
                else -> "${prefix}_${timestamp}"
            }
        },
    )
}
