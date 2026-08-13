package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

@Composable
fun SharedPdfTextDockFontItem(
    name: String,
    isSelected: Boolean,
    fontFamily: FontFamily,
    selectedContentDescription: String,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)
        .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .2f) else androidx.compose.ui.graphics.Color.Transparent)
        .padding(vertical = 10.dp, horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = fontFamily), maxLines = 1)
        if (isSelected) Icon(Icons.Default.Check, selectedContentDescription, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun SharedPdfTextDockPopup(
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.TopCenter,
    offsetYPixels: Int,
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) {
    Popup(alignment = alignment, offset = IntOffset(0, offsetYPixels), onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = focusable, dismissOnBackPress = true, dismissOnClickOutside = true)) { content() }
}

@Composable
fun SharedPdfTextDockPopupDp(
    onDismissRequest: () -> Unit,
    alignment: Alignment = Alignment.TopCenter,
    offsetY: Dp,
    focusable: Boolean = false,
    content: @Composable () -> Unit,
) {
    val pixels = with(LocalDensity.current) { offsetY.roundToPx() }
    SharedPdfTextDockPopup(onDismissRequest, alignment, pixels, focusable, content)
}

@Composable
fun SharedPdfTextDockFormattingButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.size(36.dp).clip(CircleShape)
        .background(if (isSelected) androidx.compose.ui.graphics.Color.Black.copy(alpha = .1f) else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
fun SharedPdfTextDockFontPanel(
    presetsLabel: String,
    importedLabel: String,
    importLabel: String,
    noImportedFontsLabel: String,
    onImportClick: () -> Unit,
    hasImportedFonts: Boolean,
    presetContent: @Composable ColumnScope.() -> Unit,
    importedContent: @Composable ColumnScope.() -> Unit,
) {
    Surface(shape = RoundedCornerShape(16.dp), color = androidx.compose.ui.graphics.Color(0xFF1E1E1E), shadowElevation = 8.dp,
        modifier = Modifier.width(260.dp)) {
        var selectedTab by remember { mutableIntStateOf(0) }
        Column {
            PrimaryTabRow(selectedTabIndex = selectedTab, containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary, divider = {}) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(presetsLabel, fontSize = 12.sp) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(importedLabel, fontSize = 12.sp) })
            }
            Box(Modifier.heightIn(max = 300.dp).padding(8.dp)) {
                if (selectedTab == 0) {
                    Column(content = presetContent)
                } else {
                    Column {
                        Button(onClick = onImportClick, modifier = Modifier.fillMaxWidth().padding(8.dp),
                            contentPadding = PaddingValues(0.dp), shape = RoundedCornerShape(8.dp)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(importLabel, fontSize = 12.sp)
                        }
                        if (!hasImportedFonts) {
                            Text(noImportedFontsLabel, color = androidx.compose.ui.graphics.Color.Gray,
                                modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp)
                        } else Column(content = importedContent)
                    }
                }
            }
        }
    }
}

@Composable
fun SharedPdfTextDockFontSizeRow(size: Int, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .1f) else androidx.compose.ui.graphics.Color.Transparent)
        .padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Text(size.toString(), style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.White)
    }
}
