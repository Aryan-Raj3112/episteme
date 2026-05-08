package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.CustomFontItem

@Composable
fun SharedCustomFontsScreen(
    fonts: List<CustomFontItem>,
    onImportFont: () -> Unit,
    onDeleteFont: (CustomFontItem) -> Unit,
    googleFontsAvailable: Boolean = false,
    getGoogleFonts: () -> List<String> = { emptyList() },
    onDownloadGoogleFont: (String, () -> Unit) -> Unit = { _, onComplete -> onComplete() },
    fontFamilyForPreview: (CustomFontItem) -> FontFamily? = { null },
    modifier: Modifier = Modifier
) {
    var fontPendingDelete by remember { mutableStateOf<CustomFontItem?>(null) }
    var showGoogleFontsDialog by remember { mutableStateOf(false) }

    SharedScreenScaffold(
        title = "Custom Fonts",
        subtitle = "Imported fonts for the reader",
        modifier = modifier,
        trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (googleFontsAvailable) {
                    Button(onClick = { showGoogleFontsDialog = true }) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Google Fonts")
                    }
                }
                Button(onClick = onImportFont) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Import")
                }
            }
        }
    ) {
        val activeFonts = fonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
        if (activeFonts.isEmpty()) {
            SharedUtilityEmptyState(
                icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(56.dp)) },
                title = "No custom fonts",
                body = "Import TTF, OTF, or WOFF2 files to use them in books.",
                actionLabel = "Import font",
                onAction = onImportFont,
                modifier = Modifier.weight(1f)
            )
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(activeFonts, key = { it.id }) { font ->
                    SharedFontListItem(
                        font = font,
                        onDelete = { fontPendingDelete = font },
                        fontFamilyForPreview = fontFamilyForPreview
                    )
                }
            }
        }
    }

    if (googleFontsAvailable && showGoogleFontsDialog) {
        SharedGoogleFontsDialog(
            existingFonts = fonts,
            getGoogleFonts = getGoogleFonts,
            onDownloadGoogleFont = onDownloadGoogleFont,
            onDismiss = { showGoogleFontsDialog = false }
        )
    }

    fontPendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { fontPendingDelete = null },
            title = { Text("Delete font?") },
            text = { Text("Delete ${font.displayName}? Books using it will fall back to the default font.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFont(font)
                        fontPendingDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fontPendingDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SharedGoogleFontsDialog(
    existingFonts: List<CustomFontItem>,
    getGoogleFonts: () -> List<String>,
    onDownloadGoogleFont: (String, () -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var downloadingFontName by remember { mutableStateOf<String?>(null) }
    val popularPresets = remember {
        listOf(
            "Merriweather",
            "Open Sans",
            "Playfair Display",
            "Montserrat",
            "Oswald",
            "Raleway",
            "Nunito",
            "Poppins",
            "Ubuntu",
            "Fira Sans",
            "Quicksand",
            "Crimson Text",
            "Literata",
            "EB Garamond",
            "Libre Baskerville",
            "Inter",
            "Work Sans"
        )
    }
    val displayList = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            popularPresets
        } else {
            getGoogleFonts()
                .filter { it.contains(searchQuery, ignoreCase = true) }
                .take(50)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Browse Google Fonts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search 1900+ fonts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (searchQuery.isBlank()) {
                    item {
                        Text(
                            text = "Popular choices",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (displayList.isEmpty()) {
                    item {
                        Text(
                            text = "No fonts found matching '$searchQuery'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                items(displayList, key = { it }) { fontName ->
                    val isDownloaded = existingFonts.any { it.displayName.equals(fontName, ignoreCase = true) }
                    val isDownloading = downloadingFontName == fontName
                    fun startDownload() {
                        downloadingFontName = fontName
                        onDownloadGoogleFont(fontName) {
                            if (downloadingFontName == fontName) {
                                downloadingFontName = null
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isDownloaded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !isDownloaded && !isDownloading) { startDownload() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = fontName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isDownloaded) FontWeight.Bold else FontWeight.Medium,
                            color = if (isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        Box(
                            modifier = Modifier.padding(start = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            when {
                                isDownloaded -> Icon(Icons.Default.Check, contentDescription = "Already downloaded", tint = MaterialTheme.colorScheme.primary)
                                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> Icon(Icons.Default.CloudDownload, contentDescription = "Download")
                            }
                        }
                    }
                }
            }
        }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SharedFontListItem(
    font: CustomFontItem,
    onDelete: () -> Unit,
    fontFamilyForPreview: (CustomFontItem) -> FontFamily?
) {
    val previewFontFamily = remember(font.path) { fontFamilyForPreview(font) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Text("Aa", fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = font.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = font.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Text(
                        text = font.fileExtension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete font", tint = MaterialTheme.colorScheme.error)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Grumpy wizards make toxic brew for the evil queen! 1234567890 ?.,;:",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    fontFamily = previewFontFamily,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun SharedHelpFeedbackScreen(
    onOpenGitHubIssues: () -> Unit,
    onEmailSupport: () -> Unit,
    modifier: Modifier = Modifier
) {
    SharedScreenScaffold(
        title = "Help & Feedback",
        subtitle = "Bug reports, feature requests, and support",
        modifier = modifier
    ) {
        SharedUtilityHeader(
            icon = { Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(52.dp)) },
            title = "Get in touch",
            body = "Report bugs, request features, or contact support directly."
        )
        SharedUtilityOptionCard(
            title = "GitHub Issues",
            body = "Report bugs, request features, and track development progress.",
            icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenGitHubIssues
        )
        SharedUtilityOptionCard(
            title = "Email Support",
            body = "Contact us directly by email for anything else.",
            icon = { Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onEmailSupport
        )
    }
}

@Composable
fun SharedSupportProjectScreen(
    onOpenGitHubSponsors: () -> Unit,
    onOpenPatreon: () -> Unit,
    modifier: Modifier = Modifier
) {
    SharedScreenScaffold(
        title = "Support Project",
        subtitle = "Ways to support Episteme development",
        modifier = modifier
    ) {
        SharedUtilityHeader(
            icon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(52.dp)) },
            title = "Support Episteme",
            body = "Contributions help keep the reader improving across Android and desktop."
        )
        SharedUtilityOptionCard(
            title = "GitHub Sponsors",
            body = "Support development through GitHub Sponsors.",
            icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenGitHubSponsors
        )
        SharedUtilityOptionCard(
            title = "Patreon",
            body = "Support the project on Patreon.",
            icon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenPatreon
        )
    }
}

@Composable
fun SharedAboutScreen(
    versionName: String,
    buildLabel: String,
    onOpenSource: () -> Unit,
    onOpenIssues: () -> Unit,
    modifier: Modifier = Modifier
) {
    SharedScreenScaffold(
        title = "About Episteme",
        subtitle = "Desktop reader",
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Info, contentDescription = null)
                    }
                }
                Column {
                    Text("Episteme", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(versionName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(buildLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        SharedUtilityOptionCard(
            title = "Source Code",
            body = "Browse the project source on GitHub.",
            icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenSource
        )
        SharedUtilityOptionCard(
            title = "Issues",
            body = "Open the issue tracker for bugs and feature requests.",
            icon = { Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenIssues
        )
    }
}

@Composable
private fun SharedUtilityHeader(
    icon: @Composable () -> Unit,
    title: String,
    body: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SharedUtilityOptionCard(
    title: String,
    body: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Open")
        }
    }
}

@Composable
private fun SharedUtilityEmptyState(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Box(Modifier.padding(18.dp), contentAlignment = Alignment.Center) {
                        icon()
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.7f)
                )
                TextButton(onClick = onAction) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}
