package com.aryan.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.aryan.reader.shared.ReaderBookReplacementPreferences
import com.aryan.reader.shared.ui.SharedBookWordReplacementLabels
import com.aryan.reader.shared.ui.SharedBookWordReplacementsSheet

@Composable
fun BookWordReplacementsSheet(
    isVisible: Boolean,
    bookId: String,
    bookTitle: String?,
    preferences: ReaderBookReplacementPreferences,
    onPreferencesChange: (ReaderBookReplacementPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    SharedBookWordReplacementsSheet(
        isVisible = isVisible,
        bookId = bookId,
        bookTitle = bookTitle,
        preferences = preferences,
        onPreferencesChange = onPreferencesChange,
        onDismiss = onDismiss,
        labels = SharedBookWordReplacementLabels(
            title = stringResource(R.string.menu_book_word_replacements),
            currentBook = stringResource(R.string.book_replacements_current_book),
            close = stringResource(R.string.action_close),
            addRule = stringResource(R.string.book_replacements_add_rule),
            empty = stringResource(R.string.book_replacements_empty),
            previewDefault = stringResource(R.string.book_replacements_preview_default),
            newReplacement = stringResource(R.string.book_replacements_new_replacement),
            editReplacement = stringResource(R.string.book_replacements_edit_replacement),
            replace = stringResource(R.string.tts_replacements_label_replace),
            with = stringResource(R.string.book_replacements_label_with),
            enabled = stringResource(R.string.tts_replacements_chip_enabled),
            regex = stringResource(R.string.tts_replacements_chip_regex),
            wholeWord = stringResource(R.string.tts_replacements_chip_whole_word),
            matchCase = stringResource(R.string.tts_replacements_chip_match_case),
            previewInput = stringResource(R.string.tts_replacements_label_preview_input),
            cancel = stringResource(R.string.action_cancel),
            save = stringResource(R.string.action_save),
            rules = stringResource(R.string.tts_replacements_rules),
            emptyReplacement = stringResource(R.string.book_replacements_empty_replacement),
            edit = stringResource(R.string.action_edit),
            delete = stringResource(R.string.action_delete),
            plainText = stringResource(R.string.tts_replacements_plain_text),
            caseSensitive = stringResource(R.string.tts_replacements_case_sensitive),
        ),
        newRuleId = { "book_rule_${System.currentTimeMillis()}" },
    )
}
