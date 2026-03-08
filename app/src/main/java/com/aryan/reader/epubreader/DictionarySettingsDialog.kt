// DictionarySettingsDialog.kt
package com.aryan.reader.epubreader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import com.aryan.reader.BuildConfig
import com.aryan.reader.R

@Suppress("KotlinConstantConditions")
@Composable
fun DictionarySettingsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    isProUser: Boolean,
    useOnlineDictionary: Boolean,
    onToggleOnlineDictionary: (Boolean) -> Unit,
    selectedPackageName: String?,
    onSelectPackage: (String) -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    var availableApps by remember { mutableStateOf<List<ExternalDictionaryApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableApps = ExternalDictionaryHelper.getAvailableDictionaries(context)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                // Header
                Text(
                    text = "Dictionary Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (BuildConfig.FLAVOR != "oss") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (useOnlineDictionary) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (useOnlineDictionary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleOnlineDictionary(true) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ai),
                                    contentDescription = null,
                                    tint = if (useOnlineDictionary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AI Smart Dictionary",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (useOnlineDictionary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Contextual definitions powered by AI.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (useOnlineDictionary) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (useOnlineDictionary) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // External App Option
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (!useOnlineDictionary) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            border = BorderStroke(
                                1.dp,
                                if (!useOnlineDictionary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleOnlineDictionary(false) }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.dictionary),
                                    contentDescription = null,
                                    tint = if (!useOnlineDictionary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "External App",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!useOnlineDictionary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Launch an offline dictionary or search app.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (!useOnlineDictionary) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (!useOnlineDictionary) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = if (useOnlineDictionary) "Fallback External App (Used when offline)" else "Select External App",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                } else {
                    // OSS FLAVOR UI (Dedicated to external apps)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.dictionary),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Choose an external app to define selected words.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // App List
                if (availableApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No supported dictionary apps found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp) // Bound height so it doesn't take over screen
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest, RoundedCornerShape(12.dp))
                    ) {
                        items(availableApps) { app ->
                            val isSelected = app.packageName == selectedPackageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPackage(app.packageName) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Dynamic Icon handling
                                if (app.packageName == ExternalDictionaryHelper.GOOGLE_SEARCH_PKG) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else if (app.icon != null) {
                                    Image(
                                        bitmap = app.icon.toBitmap().asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}