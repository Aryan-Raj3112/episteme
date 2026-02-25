/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aryan.reader.data.CustomFontEntity
import java.io.File

@Composable
fun FontsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit
) {
    val fonts: List<CustomFontEntity> by viewModel.customFonts.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Dialog state
    var showDeleteDialog by remember { mutableStateOf(false) }
    var fontToDelete by remember { mutableStateOf<CustomFontEntity?>(null) }

    val pickFontLauncher = rememberFilePickerLauncher { uris ->
        uris.firstOrNull()?.let { viewModel.importFont(it) }
    }

    // Font mime types filter
    val fontMimeTypes = arrayOf(
        "font/ttf",
        "font/otf",
        "font/woff2",
        "application/x-font-ttf",
        "application/x-font-otf",
        "application/font-woff2",
        "application/vnd.ms-opentype",
        "application/x-font-opentype"
    )

    Scaffold(
        modifier = Modifier.statusBarsPadding(), // Fixes content flowing under status bar
        topBar = {
            CustomTopAppBar(
                title = { Text("Custom Fonts") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            // Hide FAB when empty state is visible (list is empty)
            if (fonts.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { pickFontLauncher.launch(fontMimeTypes) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Import Font") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (fonts.isEmpty()) {
                EmptyState(
                    title = "No Custom Fonts",
                    message = "Import TTF or OTF files to use them in your books.",
                    onSelectFileClick = { pickFontLauncher.launch(fontMimeTypes) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Padding bottom 88.dp allows scrolling past the FloatingActionButton
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(fonts, key = { it.id }) { font ->
                        FontListItem(
                            font = font,
                            onDelete = {
                                fontToDelete = font
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            // Banner messages removed as requested
        }
    }

    if (showDeleteDialog && fontToDelete != null) {
        DeleteFontConfirmationDialog(
            fontName = fontToDelete!!.displayName,
            onConfirm = {
                fontToDelete?.let { viewModel.deleteFont(it.id) }
                showDeleteDialog = false
                fontToDelete = null
            },
            onDismiss = {
                showDeleteDialog = false
                fontToDelete = null
            }
        )
    }
}

@Composable
fun FontListItem(
    font: CustomFontEntity,
    onDelete: () -> Unit
) {
    val customTypeface = remember(font.path) {
        try {
            FontFamily(Font(File(font.path)))
        } catch (_: Exception) {
            null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = font.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.shapes.small)
                    .padding(12.dp)
            ) {
                if (customTypeface != null) {
                    Text(
                        text = "Grumpy wizards make toxic brew for the evil queen! 1234567890 ?.,;:",
                        fontFamily = customTypeface,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Text(
                        text = "Preview unavailable (Invalid font file)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = font.fileExtension.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun DeleteFontConfirmationDialog(
    fontName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Font?") },
        text = { Text("Are you sure you want to delete '$fontName'? This will remove it from all your devices if sync is on.") },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}