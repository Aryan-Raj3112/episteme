package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aryan.reader.shared.DockLocation
import com.aryan.reader.shared.pdf.RichParagraphUiState
import com.aryan.reader.shared.pdf.SharedPdfRichTextAlign
import com.aryan.reader.shared.pdf.isPdfTextDockSideDocked

data class SharedPdfTextDockBarLabels(
    val selectFontFamily: String,
    val selectFontSize: String,
    val fontBackground: String,
    val bold: String,
    val italic: String,
    val underline: String,
    val strikethrough: String,
    val insertTextBox: String,
    val numberedList: String,
    val bulletedList: String,
    val textAlignment: String,
    val alignLeft: String,
    val alignCenter: String,
    val alignRight: String,
)

data class SharedPdfTextDockBarPainters(
    val fonts: Painter,
    val background: Painter,
    val bold: Painter,
    val italic: Painter,
    val underline: Painter,
    val strikethrough: Painter,
    val textBox: Painter,
    val numberedList: Painter,
    val bulletedList: Painter,
    val alignLeft: Painter,
    val alignCenter: Painter,
    val alignRight: Painter,
)

/** Second-row paragraph controls (lists + alignment). Null hides the row. */
data class SharedPdfTextDockParagraphControls(
    val state: RichParagraphUiState,
    val onNumberedListClick: () -> Unit,
    val onBulletedListClick: () -> Unit,
    val onAlignmentClick: () -> Unit,
    val onAlignmentSelected: (SharedPdfRichTextAlign) -> Unit,
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
    textColorIndicator: @Composable (Color) -> Unit,
    fontSizePopup: @Composable BoxScope.() -> Unit,
    paragraphControls: SharedPdfTextDockParagraphControls? = null,
    alignmentPopup: @Composable BoxScope.() -> Unit = {},
    /**
     * False hides the insert-text-box cell. Android retired the page editor:
     * boxes are created by tapping, so the icon is hidden there. Defaults
     * true so iOS/desktop are unaffected.
     */
    showInsertTextBox: Boolean = true,
    /**
     * Side-docked bars render as a compact scrollable semi-circle wheel
     * protruding from the edge instead of the horizontal bar. Derived from
     * [dockLocation] when set; [isVertical] overrides for floating drags.
     */
    dockLocation: DockLocation = DockLocation.BOTTOM,
    isVertical: Boolean = isPdfTextDockSideDocked(dockLocation),
    /** False while a dock move is in progress so moves never spin the wheel. */
    spinEnabled: Boolean = true,
) {
    if (isVertical) {
        // Arc order matches the horizontal bar; the paragraph trio appends
        // only for the open draft, the insert cell only when requested.
        val paragraphItemCount = if (paragraphControls != null) 3 else 0
        val itemCount = 8 + (if (showInsertTextBox) 1 else 0) + paragraphItemCount
        var wheelRotation by remember(dockLocation, showInsertTextBox, paragraphControls != null) {
            mutableStateOf(0f)
        }
        SharedPdfSideWheelDock(
            dockLocation = dockLocation,
            backgroundColor = Color(0xFFF0F0F0),
            rotationDeg = wheelRotation,
            onRotationChange = { wheelRotation = it },
            itemCount = itemCount,
            spinEnabled = spinEnabled,
        ) { index ->
            val paraStart = 8 + (if (showInsertTextBox) 1 else 0)
            when {
                index == 0 -> SharedPdfTextDockPainterButton(isFontFamilySelected, painters.fonts, labels.selectFontFamily, onFontFamilyClick)
                index == 1 -> {
                    fontSizePopup()
                    Column(
                        Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onFontSizeClick).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(fontSize.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.Black)
                        Icon(Icons.Default.KeyboardArrowDown, labels.selectFontSize, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                index == 2 -> Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onTextColorClick), contentAlignment = Alignment.Center) {
                    textColorIndicator(textColor)
                }
                index == 3 -> Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onBackgroundColorClick), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically)) {
                        Icon(painters.background, labels.fontBackground, Modifier.size(17.dp), tint = Color.Black)
                        Box(Modifier.width(16.dp).height(2.dp).background(backgroundColor))
                    }
                }
                index == 4 -> SharedPdfTextDockPainterButton(isBold, painters.bold, labels.bold, onBoldClick)
                index == 5 -> SharedPdfTextDockPainterButton(isItalic, painters.italic, labels.italic, onItalicClick)
                index == 6 -> SharedPdfTextDockPainterButton(isUnderline, painters.underline, labels.underline, onUnderlineClick)
                index == 7 -> SharedPdfTextDockPainterButton(isStrikethrough, painters.strikethrough, labels.strikethrough, onStrikethroughClick)
                showInsertTextBox && index == 8 -> SharedPdfTextDockPainterButton(false, painters.textBox, labels.insertTextBox, onInsertTextBox)
                paragraphControls != null && index == paraStart -> {
                    SharedPdfTextDockPainterButton(
                        paragraphControls.state.isNumbered,
                        painters.numberedList,
                        labels.numberedList,
                        paragraphControls.onNumberedListClick,
                    )
                }
                paragraphControls != null && index == paraStart + 1 -> SharedPdfTextDockPainterButton(
                    paragraphControls.state.isBulleted,
                    painters.bulletedList,
                    labels.bulletedList,
                    paragraphControls.onBulletedListClick,
                )
                paragraphControls != null -> {
                    // The alignment popup anchors to its own cell like the
                    // font-size popup above.
                    alignmentPopup()
                    val currentAlignPainter = when (paragraphControls.state.alignment) {
                        SharedPdfRichTextAlign.CENTER -> painters.alignCenter
                        SharedPdfRichTextAlign.RIGHT -> painters.alignRight
                        SharedPdfRichTextAlign.LEFT -> painters.alignLeft
                    }
                    Column(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = paragraphControls.onAlignmentClick)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(currentAlignPainter, labels.textAlignment, tint = Color.Black.copy(alpha = .8f), modifier = Modifier.size(20.dp))
                        Icon(Icons.Default.KeyboardArrowDown, labels.textAlignment, tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                else -> Unit
            }
        }
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Surface(Modifier.fillMaxWidth().height(48.dp), color = Color(0xFFF0F0F0), shadowElevation = 8.dp) {
            Box(Modifier.fillMaxSize()) {
                alignmentPopup()
                Row(
                    Modifier.fillMaxSize().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                    if (showInsertTextBox) {
                        SharedPdfTextDockBarCell { SharedPdfTextDockPainterButton(false, painters.textBox, labels.insertTextBox, onInsertTextBox) }
                    }
                    if (paragraphControls != null) {
                        SharedPdfTextDockBarCell {
                            SharedPdfTextDockPainterButton(
                                paragraphControls.state.isNumbered,
                                painters.numberedList,
                                labels.numberedList,
                                paragraphControls.onNumberedListClick,
                            )
                        }
                        SharedPdfTextDockBarCell {
                            SharedPdfTextDockPainterButton(
                                paragraphControls.state.isBulleted,
                                painters.bulletedList,
                                labels.bulletedList,
                                paragraphControls.onBulletedListClick,
                            )
                        }
                        Box(
                            Modifier.width(64.dp).fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val currentAlignPainter = when (paragraphControls.state.alignment) {
                                SharedPdfRichTextAlign.CENTER -> painters.alignCenter
                                SharedPdfRichTextAlign.RIGHT -> painters.alignRight
                                SharedPdfRichTextAlign.LEFT -> painters.alignLeft
                            }
                            Row(
                                Modifier.clip(RoundedCornerShape(8.dp))
                                    .clickable(onClick = paragraphControls.onAlignmentClick)
                                    .padding(horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(currentAlignPainter, labels.textAlignment, tint = Color.Black.copy(alpha = .8f), modifier = Modifier.size(20.dp))
                                Icon(Icons.Default.KeyboardArrowDown, labels.textAlignment, tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(bottomDockPadding))
    }
}

@Composable
private fun SharedPdfTextDockBarCell(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.width(48.dp).fillMaxHeight(), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun SharedPdfTextDockPainterButton(selected: Boolean, painter: Painter, description: String, onClick: () -> Unit) {
    SharedPdfTextDockFormattingButton(selected, onClick) {
        Icon(painter, description, tint = Color.Black.copy(alpha = .8f), modifier = Modifier.size(20.dp))
    }
}
