package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SharedPdfTextDockBarLabels(
    val selectFontFamily: String,
    val selectFontSize: String,
    val fontBackground: String,
    val bold: String,
    val italic: String,
    val underline: String,
    val strikethrough: String,
    val insertTextBox: String,
    val close: String,
)

data class SharedPdfTextDockBarPainters(
    val fonts: Painter,
    val background: Painter,
    val bold: Painter,
    val italic: Painter,
    val underline: Painter,
    val strikethrough: Painter,
    val textBox: Painter,
)

@Composable
fun SharedPdfTextDockBar(
    fontSize: Int,
    textColor: Color,
    backgroundColor: Color,
    isFontFamilySelected: Boolean,
    isBold: Boolean,
    isItalic: Boolean,
    isUnderline: Boolean,
    isStrikethrough: Boolean,
    bottomDockPadding: Dp,
    labels: SharedPdfTextDockBarLabels,
    painters: SharedPdfTextDockBarPainters,
    onFontFamilyClick: () -> Unit,
    onFontSizeClick: () -> Unit,
    onTextColorClick: () -> Unit,
    onBackgroundColorClick: () -> Unit,
    onBoldClick: () -> Unit,
    onItalicClick: () -> Unit,
    onUnderlineClick: () -> Unit,
    onStrikethroughClick: () -> Unit,
    onInsertTextBox: () -> Unit,
    onClose: () -> Unit,
    textColorIndicator: @Composable (Color) -> Unit,
    fontSizePopup: @Composable BoxScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Surface(Modifier.fillMaxWidth().height(48.dp), color = Color(0xFFF0F0F0), shadowElevation = 8.dp) {
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                SharedPdfTextDockBarCell {
                    SharedPdfTextDockPainterButton(isFontFamilySelected, painters.fonts, labels.selectFontFamily, onFontFamilyClick)
                }
                SharedPdfTextDockBarCell {
                    fontSizePopup()
                    Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFontSizeClick).padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        Text(fontSize.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
                        Icon(Icons.Default.KeyboardArrowDown, labels.selectFontSize, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                SharedPdfTextDockBarCell {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onTextColorClick), contentAlignment = Alignment.Center) {
                        textColorIndicator(textColor)
                    }
                }
                SharedPdfTextDockBarCell {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBackgroundColorClick), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)) {
                            Icon(painters.background, labels.fontBackground, Modifier.size(17.dp), tint = Color.Black)
                            Box(Modifier.width(16.dp).height(2.dp).background(backgroundColor))
                        }
                    }
                }
                SharedPdfTextDockBarCell { SharedPdfTextDockPainterButton(isBold, painters.bold, labels.bold, onBoldClick) }
                SharedPdfTextDockBarCell { SharedPdfTextDockPainterButton(isItalic, painters.italic, labels.italic, onItalicClick) }
                SharedPdfTextDockBarCell { SharedPdfTextDockPainterButton(isUnderline, painters.underline, labels.underline, onUnderlineClick) }
                SharedPdfTextDockBarCell { SharedPdfTextDockPainterButton(isStrikethrough, painters.strikethrough, labels.strikethrough, onStrikethroughClick) }
                SharedPdfTextDockBarCell { SharedPdfTextDockPainterButton(false, painters.textBox, labels.insertTextBox, onInsertTextBox) }
                SharedPdfTextDockBarCell {
                    SharedPdfTextDockFormattingButton(false, onClose) {
                        Icon(Icons.Default.Close, labels.close, tint = Color.Black.copy(alpha = .8f), modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(bottomDockPadding))
    }
}

@Composable
private fun RowScope.SharedPdfTextDockBarCell(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun SharedPdfTextDockPainterButton(selected: Boolean, painter: Painter, description: String, onClick: () -> Unit) {
    SharedPdfTextDockFormattingButton(selected, onClick) {
        Icon(painter, description, tint = Color.Black.copy(alpha = .8f), modifier = Modifier.size(20.dp))
    }
}
