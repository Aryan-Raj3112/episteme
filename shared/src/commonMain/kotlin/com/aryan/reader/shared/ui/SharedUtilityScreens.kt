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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import com.aryan.reader.shared.AppFontPreference
import com.aryan.reader.shared.CustomFontItem

@Composable
fun SharedCustomFontsScreen(
    fonts: List<CustomFontItem>,
    appFontPreference: AppFontPreference = AppFontPreference.System,
    onAppFontPreferenceChange: (AppFontPreference) -> Unit = {},
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
    var selectedSection by remember { mutableStateOf(SharedFontSettingsSection.READER_FONTS) }

    SharedScreenScaffold(
        title = readerString("custom_fonts", "Custom fonts"),
        subtitle = readerString("desktop_custom_fonts_desc", "Imported fonts for the reader"),
        modifier = modifier,
        trailing = {
            if (selectedSection == SharedFontSettingsSection.READER_FONTS) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (googleFontsAvailable) {
                        Button(onClick = { showGoogleFontsDialog = true }) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(readerString("google_fonts", "Google Fonts"))
                        }
                    }
                    Button(onClick = onImportFont) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(readerString("action_import", "Import"))
                    }
                }
            }
        }
    ) {
        val activeFonts = fonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
        SharedFontSettingsTabs(
            selectedSection = selectedSection,
            onSectionChange = { selectedSection = it }
        )

        when (selectedSection) {
            SharedFontSettingsSection.READER_FONTS -> {
                if (activeFonts.isEmpty()) {
                    SharedUtilityEmptyState(
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(56.dp)) },
                        title = readerString("no_custom_fonts", "No custom fonts"),
                        body = readerString("desktop_no_custom_fonts_desc", "Import TTF, OTF, or WOFF2 files to use them in books."),
                        actionLabel = readerString("import_font", "Import font"),
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

            SharedFontSettingsSection.APP_TEXT -> {
                SharedAppFontSelector(
                    preference = appFontPreference,
                    customFonts = activeFonts,
                    onPreferenceChange = onAppFontPreferenceChange,
                    fontFamilyForPreview = fontFamilyForPreview,
                    modifier = Modifier.weight(1f)
                )
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
            title = { Text(readerString("dialog_delete_font", "Delete font?")) },
            text = { Text(readerString("desktop_delete_font_desc", "Delete %1\$s? Books using it will fall back to the default font.", font.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteFont(font)
                        fontPendingDelete = null
                    }
                ) {
                    Text(readerString("action_delete", "Delete"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { fontPendingDelete = null }) {
                    Text(readerString("action_cancel", "Cancel"))
                }
            }
        )
    }
}

enum class SharedFontSettingsSection {
    READER_FONTS,
    APP_TEXT
}

@Composable
fun SharedFontSettingsTabs(
    selectedSection: SharedFontSettingsSection,
    onSectionChange: (SharedFontSettingsSection) -> Unit,
    modifier: Modifier = Modifier
) {
    TabRow(
        selectedTabIndex = selectedSection.ordinal,
        modifier = modifier.fillMaxWidth()
    ) {
        Tab(
            selected = selectedSection == SharedFontSettingsSection.READER_FONTS,
            onClick = { onSectionChange(SharedFontSettingsSection.READER_FONTS) },
            text = { Text(readerString("reader_fonts", "Reader fonts")) }
        )
        Tab(
            selected = selectedSection == SharedFontSettingsSection.APP_TEXT,
            onClick = { onSectionChange(SharedFontSettingsSection.APP_TEXT) },
            text = { Text(readerString("app_font_title", "App text font")) }
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
            Text(readerString("action_browse_google_fonts", "Browse Google Fonts"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SharedStableOutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(readerString("google_fonts_search_placeholder", "Search 1900+ fonts...")) },
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
                            text = readerString("google_fonts_popular_choices", "Popular choices"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (displayList.isEmpty()) {
                    item {
                        Text(
                            text = readerString("desktop_no_fonts_matching", "No fonts found matching '%1\$s'", searchQuery),
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
                                isDownloaded -> Icon(Icons.Default.Check, contentDescription = readerString("content_desc_already_downloaded", "Already downloaded"), tint = MaterialTheme.colorScheme.primary)
                                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                else -> Icon(Icons.Default.CloudDownload, contentDescription = readerString("action_download", "Download"))
                            }
                        }
                    }
                }
            }
        }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(readerString("action_close", "Close"))
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
                    Icon(Icons.Default.Delete, contentDescription = readerString("desktop_delete_font", "Delete font"), tint = MaterialTheme.colorScheme.error)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = readerString("font_preview_text", "Grumpy wizards make toxic brew for the evil queen! 1234567890 ?.,;:"),
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
        title = readerString("drawer_help_feedback", "Help & Feedback"),
        subtitle = readerString("desktop_help_feedback_desc", "Bug reports, feature requests, and support"),
        modifier = modifier
    ) {
        SharedUtilityHeader(
            icon = { Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(52.dp)) },
            title = readerString("get_in_touch", "Get in touch"),
            body = readerString("desktop_get_in_touch_desc", "Report bugs, request features, or contact support directly.")
        )
        SharedUtilityOptionCard(
            title = readerString("github_issues", "GitHub Issues"),
            body = readerString("github_issues_desc", "Report bugs, request features, and track development progress."),
            icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenGitHubIssues
        )
        SharedUtilityOptionCard(
            title = readerString("email_support", "Email support"),
            body = readerString("desktop_email_support_desc", "Contact us directly by email for anything else."),
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
        title = readerString("drawer_support_project", "Support project"),
        subtitle = readerString("desktop_support_project_desc", "Ways to support Episteme development"),
        modifier = modifier
    ) {
        SharedUtilityHeader(
            icon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(52.dp)) },
            title = readerString("desktop_support_episteme", "Support Episteme"),
            body = readerString("desktop_support_episteme_desc", "Contributions help keep the reader improving across Android and desktop.")
        )
        SharedUtilityOptionCard(
            title = readerString("desktop_github_sponsors", "GitHub Sponsors"),
            body = readerString("desktop_github_sponsors_desc", "Support development through GitHub Sponsors."),
            icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenGitHubSponsors
        )
        SharedUtilityOptionCard(
            title = readerString("desktop_patreon", "Patreon"),
            body = readerString("desktop_patreon_desc", "Support the project on Patreon."),
            icon = { Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(28.dp)) },
            onClick = onOpenPatreon
        )
    }
}

@Composable
fun SharedAboutScreen(
    versionName: String,
    buildLabel: String,
    onOpenSource: (() -> Unit)? = null,
    onOpenIssues: (() -> Unit)? = null,
    onOpenPrivacyPolicy: (() -> Unit)? = null,
    onOpenTerms: (() -> Unit)? = null,
    onOpenLicenses: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    SharedScreenScaffold(
        title = readerString("about_title", "About Episteme"),
        subtitle = readerString("desktop_about_subtitle", "Desktop reader"),
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
                    Text(readerString("app_name", "Episteme"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(versionName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(buildLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (onOpenSource != null) {
            SharedUtilityOptionCard(
                title = readerString("desktop_source_code", "Source code"),
                body = readerString("desktop_source_code_desc", "Browse the project source on GitHub."),
                icon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(28.dp)) },
                onClick = onOpenSource
            )
        }
        if (onOpenIssues != null) {
            SharedUtilityOptionCard(
                title = readerString("desktop_issues", "Issues"),
                body = readerString("desktop_issues_desc", "Open the issue tracker for bugs and feature requests."),
                icon = { Icon(Icons.Default.Feedback, contentDescription = null, modifier = Modifier.size(28.dp)) },
                onClick = onOpenIssues
            )
        }
        if (onOpenPrivacyPolicy != null) {
            SharedUtilityOptionCard(
                title = readerString("legal_privacy_policy", "Privacy Policy"),
                body = readerString("about_privacy_desc", "How Episteme handles data for this edition."),
                icon = { Icon(Icons.Default.Policy, contentDescription = null, modifier = Modifier.size(28.dp)) },
                onClick = onOpenPrivacyPolicy
            )
        }
        if (onOpenTerms != null) {
            SharedUtilityOptionCard(
                title = readerString("legal_terms_of_service", "Terms of Service"),
                body = readerString("about_terms_desc", "Usage terms and conditions."),
                icon = { Icon(Icons.Default.Gavel, contentDescription = null, modifier = Modifier.size(28.dp)) },
                onClick = onOpenTerms
            )
        }
        if (onOpenLicenses != null) {
            SharedUtilityOptionCard(
                title = readerString("legal_licenses", "Licenses"),
                body = readerString("about_licenses_desc", "Open source libraries used."),
                icon = { Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(28.dp)) },
                onClick = onOpenLicenses
            )
        }
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
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = readerString("action_open", "Open"))
        }
    }
}

@Composable
fun SharedAppFontSelector(
    preference: AppFontPreference,
    customFonts: List<CustomFontItem>,
    onPreferenceChange: (AppFontPreference) -> Unit,
    fontFamilyForPreview: (CustomFontItem) -> FontFamily? = { null },
    modifier: Modifier = Modifier
) {
    val sanitizedPreference = preference.sanitized()
    val activeFonts = customFonts.filterNot { it.isDeleted }.sortedBy { it.displayName.lowercase() }
    OutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = readerString("app_font_title", "App text font"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = readerString("app_font_desc", "Applies to app navigation, settings, lists, dialogs, and reader chrome."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            listOf(
                SharedAppFontOption(
                    label = readerString("app_font_system", "System"),
                    summary = readerString("app_font_system_desc", "Use the platform default"),
                    preference = AppFontPreference.System,
                    fontFamily = FontFamily.Default
                ),
                SharedAppFontOption(
                    label = readerString("app_font_serif", "Serif"),
                    summary = readerString("app_font_serif_desc", "Classic reading-style letterforms"),
                    preference = AppFontPreference.Serif,
                    fontFamily = FontFamily.Serif
                ),
                SharedAppFontOption(
                    label = readerString("app_font_sans", "Sans"),
                    summary = readerString("app_font_sans_desc", "Clean interface-style letterforms"),
                    preference = AppFontPreference.SansSerif,
                    fontFamily = FontFamily.SansSerif
                ),
                SharedAppFontOption(
                    label = readerString("app_font_monospace", "Monospace"),
                    summary = readerString("app_font_monospace_desc", "Fixed-width text"),
                    preference = AppFontPreference.Monospace,
                    fontFamily = FontFamily.Monospace
                )
            ).forEach { option ->
                SharedAppFontOptionRow(
                    label = option.label,
                    summary = option.summary,
                    selected = sanitizedPreference == option.preference,
                    fontFamily = option.fontFamily,
                    onClick = { onPreferenceChange(option.preference) }
                )
            }

            HorizontalDivider()

            Text(
                text = readerString("app_font_imported_fonts", "Imported fonts"),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (activeFonts.isEmpty()) {
                Text(
                    text = readerString("app_font_no_imported_fonts", "Import a font to use it for app text."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeFonts.forEach { font ->
                        val previewFontFamily = remember(font.path) { fontFamilyForPreview(font) }
                        val option = AppFontPreference.custom(font.id)
                        SharedAppFontOptionRow(
                            label = font.displayName,
                            summary = font.fileExtension.uppercase(),
                            selected = sanitizedPreference == option,
                            fontFamily = previewFontFamily,
                            onClick = { onPreferenceChange(option) }
                        )
                    }
                }
            }
        }
    }
}

private data class SharedAppFontOption(
    val label: String,
    val summary: String,
    val preference: AppFontPreference,
    val fontFamily: FontFamily?
)

@Composable
private fun SharedAppFontOptionRow(
    label: String,
    summary: String,
    selected: Boolean,
    fontFamily: FontFamily?,
    onClick: () -> Unit
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text("Aa", fontFamily = fontFamily, fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = fontFamily),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = readerString("content_desc_selected", "Selected"), tint = MaterialTheme.colorScheme.primary)
            }
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
