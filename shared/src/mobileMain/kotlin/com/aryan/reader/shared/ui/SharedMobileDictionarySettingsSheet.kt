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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderDictionaryServiceOptions
import com.aryan.reader.shared.ReaderExternalLookupService
import com.aryan.reader.shared.ReaderSearchServiceOptions
import com.aryan.reader.shared.ReaderTranslateServiceOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SharedMobileDictionarySettingsSheet(
    dictionaryService: ReaderExternalLookupService,
    translateService: ReaderExternalLookupService,
    searchService: ReaderExternalLookupService,
    onDictionaryServiceChange: (ReaderExternalLookupService) -> Unit,
    onTranslateServiceChange: (ReaderExternalLookupService) -> Unit,
    onSearchServiceChange: (ReaderExternalLookupService) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("Dictionary Settings", style = MaterialTheme.typography.titleLarge)
            SharedMobileDictionarySettingsSection(
                title = "Dictionary app",
                subtitle = "Used when you select text and choose Dictionary",
                options = ReaderDictionaryServiceOptions,
                selected = dictionaryService,
                onSelected = onDictionaryServiceChange
            )
            SharedMobileDictionarySettingsSection(
                title = "Translate app",
                subtitle = "Used when you select text and choose Translate",
                options = ReaderTranslateServiceOptions,
                selected = translateService,
                onSelected = onTranslateServiceChange
            )
            SharedMobileDictionarySettingsSection(
                title = "Search app",
                subtitle = "Used when you select text and choose Search",
                options = ReaderSearchServiceOptions,
                selected = searchService,
                onSelected = onSearchServiceChange
            )
        }
    }
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
                        Text(option.title, modifier = Modifier.weight(1f))
                        if (option == selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
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
