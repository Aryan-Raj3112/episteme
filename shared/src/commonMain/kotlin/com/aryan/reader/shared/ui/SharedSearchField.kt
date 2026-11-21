package com.aryan.reader.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's search field, built from Material 3's search component.
 *
 * Every surface used to style its own text field as a search box: outlined with a pill shape in the
 * library bar, an outlined field with a dialog corner in the settings hub, and a raw outlined field
 * jammed into a top-bar title slot on iOS. `SearchBarDefaults.InputField` is the token-correct
 * search field (filled `surfaceContainerHigh`, full corner, search leading icon, 56dp), so the
 * surfaces now differ only in placement, not in styling.
 *
 * The field is always rendered in its collapsed form: these screens filter their own content
 * instead of opening an M3 search *view* overlay, so there is no expansion to drive. The trailing
 * slot stays a callback rather than a built-in clear button because callers localise the label and
 * a few of them show it conditionally.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (String) -> Unit = {},
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = onSearch,
        expanded = false,
        onExpandedChange = {},
        modifier = modifier,
        placeholder = { Text(placeholder, maxLines = 1) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = trailingIcon,
        // `inputFieldColors()` alone is transparent: M3 expects the container to come from the
        // surrounding `SearchBar`. These screens host the field directly (app bar, settings list),
        // so it borrows the search bar's own input colours to stay a filled pill.
        colors = SearchBarDefaults.inputFieldColors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
}
