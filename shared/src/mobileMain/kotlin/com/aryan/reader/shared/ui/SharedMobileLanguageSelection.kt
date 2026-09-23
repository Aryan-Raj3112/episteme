package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.sharedAppLanguageOptions
import com.aryan.reader.shared.sharedAppLanguageSearchMatches

/**
 * Android's `LanguageSelectionDialog` body (HomeScreen.kt): a search field that
 * filters the language list, radio rows for the localized labels and a
 * "no results" state. Android keeps the list inside an AlertDialog; iOS renders
 * the same content as the utility page body so the two platforms show the same
 * control, the same strings and the same filtering rules
 * ([com.aryan.reader.shared.sharedAppLanguageSearchMatches]).
 */
@Composable
internal fun SharedMobileLanguageSelectionList(
    selectedTag: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    // Android renders `stringResource(language.labelRes)`, so the labels are
    // translated: the shared catalog carries the resource key and this boundary
    // resolves it (iOS through the generated catalog, with the English label as
    // the fallback).
    val labels = sharedAppLanguageOptions.associateWith { option ->
        readerString(option.labelKey, option.label)
    }
    val rows = sharedAppLanguageOptions.filter { option ->
        sharedAppLanguageSearchMatches(
            label = labels.getValue(option),
            tag = option.tag,
            searchAliases = option.searchAliases,
            query = query,
        )
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(readerString("action_search", "Search")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = readerString("action_clear", "Clear")
                        )
                    }
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        if (rows.isEmpty()) {
            Text(
                text = readerString("search_no_results_simple", "No results found."),
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                items(rows, key = { it.tag ?: "system" }) { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option.tag) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option.tag == selectedTag, onClick = null)
                        Spacer(Modifier.width(16.dp))
                        Text(labels.getValue(option))
                    }
                }
            }
        }
    }
}
