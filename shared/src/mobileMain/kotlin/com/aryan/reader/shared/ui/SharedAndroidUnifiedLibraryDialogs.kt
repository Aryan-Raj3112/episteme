package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.SortOrder

data class SharedAndroidUnifiedControlsStrings(
    val title: String,
    val readStatus: String,
    val sort: String,
    val advancedFilters: String,
    val filterLabels: Map<MobileUnifiedLibraryFilter, String>,
    val sortLabels: Map<SortOrder, String>,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SharedAndroidUnifiedLibraryControlsSheet(
    currentFilter: MobileUnifiedLibraryFilter,
    currentSortOrder: SortOrder,
    strings: SharedAndroidUnifiedControlsStrings,
    onFilterChanged: (MobileUnifiedLibraryFilter) -> Unit,
    onSortChanged: (SortOrder) -> Unit,
    onAdvancedFilters: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(24.dp).navigationBarsPadding()) {
            Text(strings.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(strings.readStatus, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MobileUnifiedLibraryFilter.entries.forEach { option ->
                    FilterChip(selected = currentFilter == option, onClick = { onFilterChanged(option) }, label = { Text(strings.filterLabels.getValue(option)) })
                }
            }
            Text(strings.sort, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SortOrder.entries.forEach { order ->
                    FilterChip(selected = currentSortOrder == order, onClick = { onSortChanged(order) }, label = { Text(strings.sortLabels.getValue(order)) })
                }
            }
            OutlinedButton(onClick = onAdvancedFilters, modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) { Text(strings.advancedFilters) }
        }
    }
}

@Composable
fun SharedAndroidUnifiedCreateShelfDialog(
    title: String,
    nameLabel: String,
    createLabel: String,
    cancelLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, label = { Text(nameLabel) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(createLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(cancelLabel) } },
    )
}
