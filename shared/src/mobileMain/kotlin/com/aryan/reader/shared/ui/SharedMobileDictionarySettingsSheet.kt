package com.aryan.reader.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderDictionaryServiceOptions
import com.aryan.reader.shared.ReaderExternalLookupService
import com.aryan.reader.shared.ReaderSearchServiceOptions
import com.aryan.reader.shared.ReaderTranslateServiceOptions
import com.aryan.reader.shared.visibleReaderLookupOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileDictionarySettingsSheet(
    dictionaryService: ReaderExternalLookupService,
    translateService: ReaderExternalLookupService,
    searchService: ReaderExternalLookupService,
    onDictionaryServiceChange: (ReaderExternalLookupService) -> Unit,
    onTranslateServiceChange: (ReaderExternalLookupService) -> Unit,
    onSearchServiceChange: (ReaderExternalLookupService) -> Unit,
    onDismiss: () -> Unit,
    /**
     * Schemes probed present on the device (iOS `canOpenURL`). Scheme-gated
     * apps (Google Translate, iTranslate) are listed only when installed;
     * everything else is always available. Null disables filtering so hosts
     * without a prober (Android keeps its own sheet) change nothing.
     */
    installedAppSchemes: Set<String>? = null,
) {
    val dictionaryOptions = remember(dictionaryService, installedAppSchemes) {
        installedAppSchemes?.let {
            visibleReaderLookupOptions(ReaderDictionaryServiceOptions, dictionaryService, it)
        } ?: ReaderDictionaryServiceOptions
    }
    val translateOptions = remember(translateService, installedAppSchemes) {
        installedAppSchemes?.let {
            visibleReaderLookupOptions(ReaderTranslateServiceOptions, translateService, it)
        } ?: ReaderTranslateServiceOptions
    }
    val searchOptions = remember(searchService, installedAppSchemes) {
        installedAppSchemes?.let {
            visibleReaderLookupOptions(ReaderSearchServiceOptions, searchService, it)
        } ?: ReaderSearchServiceOptions
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                readerString("content_desc_dictionary_settings", "Dictionary Settings"),
                style = MaterialTheme.typography.titleLarge,
            )
            SharedMobileDictionarySettingsSection(
                title = readerString("dictionary_settings_app_title", "Dictionary app"),
                subtitle = readerString(
                    "dictionary_settings_app_desc",
                    "Used when you select text and choose Dictionary",
                ),
                options = dictionaryOptions,
                selected = dictionaryService,
                onSelected = onDictionaryServiceChange
            )
            SharedMobileDictionarySettingsSection(
                title = readerString("dictionary_settings_translate_title", "Translate app"),
                subtitle = readerString(
                    "dictionary_settings_translate_desc",
                    "Used when you select text and choose Translate",
                ),
                options = translateOptions,
                selected = translateService,
                onSelected = onTranslateServiceChange
            )
            SharedMobileDictionarySettingsSection(
                title = readerString("dictionary_settings_search_title", "Search app"),
                subtitle = readerString(
                    "dictionary_settings_search_desc",
                    "Used when you select text and choose Search",
                ),
                options = searchOptions,
                selected = searchService,
                onSelected = onSearchServiceChange
            )
        }
    }
}

/**
 * Localized titles for the lookup services. The enum's English `title` covers the
 * Android benchmark sheet; the shared strings make the same list translate on iOS.
 */
@Composable
private fun iosLookupServiceTitle(service: ReaderExternalLookupService): String = when (service) {
    ReaderExternalLookupService.ANY_APP -> readerString("dict_lookup_any_app", "Any App")
    ReaderExternalLookupService.AI -> readerString("dict_lookup_smart_ai", "Smart AI")
    else -> service.title
}

@Composable
private fun SharedMobileDictionarySettingsSection(
    title: String,
    subtitle: String,
    options: List<ReaderExternalLookupService>,
    selected: ReaderExternalLookupService,
    onSelected: (ReaderExternalLookupService) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column {
                options.forEachIndexed { index, option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(iosLookupServiceTitle(option), modifier = Modifier.weight(1f))
                        if (option == selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = readerString("content_desc_selected", "Selected"),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (index != options.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}
