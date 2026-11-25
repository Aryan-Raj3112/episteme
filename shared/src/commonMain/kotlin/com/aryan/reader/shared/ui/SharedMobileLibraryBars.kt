package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

/**
 * Portable library chrome hoisted out of `mobileMain` (parity item C1).
 *
 * Android is the visual benchmark; these are Android's shapes moved verbatim so every host
 * renders them from one source. They take strings / slots as parameters and use zero
 * `expect` / `actual`, like the `SharedAndroid*` screens that consume them.
 */
@Composable
fun SharedMobileTopAppBar(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigationIcon()
            Box(
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
            }
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

@Composable
fun SharedMobileLibrarySearchTopBar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    showClear: Boolean,
    onClose: () -> Unit,
    onClear: () -> Unit,
    placeholder: String,
    closeContentDescription: String,
    clearContentDescription: String,
    focusRequester: FocusRequester,
    textFieldModifier: Modifier = Modifier,
) {
    Surface(shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = closeContentDescription)
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder) },
                modifier = textFieldModifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .focusRequester(focusRequester),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (showClear) {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Default.Close, contentDescription = clearContentDescription)
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun SharedMobileEmptyLibrary(
    title: String,
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    secondaryActionLabel: String? = null,
    onSecondaryAction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    SharedEmptyState(
        icon = Icons.Outlined.FileOpen,
        title = title,
        body = message,
        actionLabel = actionLabel,
        onAction = onAction,
        secondaryActionLabel = secondaryActionLabel,
        onSecondaryAction = onSecondaryAction,
        modifier = modifier.fillMaxSize(),
        titleStyle = MaterialTheme.typography.titleLarge,
        iconContentDescription = readerString("content_desc_no_files_icon", "No files"),
    )
}
