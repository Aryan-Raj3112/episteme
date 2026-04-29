/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
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
 * mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aryan.reader.R

@Composable
fun AnnotationDock(
    selectedTool: InkType,
    activePenColor: Color,
    activeHighlighterColor: Color,
    onToolClick: (InkType) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClose: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    lastPenTool: InkType,
    modifier: Modifier = Modifier,
    lastHighlighterTool: InkType = InkType.HIGHLIGHTER,
    isSticky: Boolean = false,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit,
    isStylusOnlyMode: Boolean,
    onToggleStylusOnlyMode: () -> Unit
) {
    val showFullDock = isSticky || !isMinimized
    val scrollState = rememberScrollState()

    val dockHeight = 56.dp
    val buttonSize = 36.dp
    val iconSize = 20.dp
    val spacing = 8.dp
    val horizontalPadding = 12.dp

    if (showFullDock) {
        val shape = if (isSticky) RectangleShape else RoundedCornerShape(percent = 50)

        Surface(
            color = Color(0xFF1E1E1E),
            shape = shape,
            shadowElevation = if (isSticky) 0.dp else 8.dp,
            modifier = modifier.height(dockHeight)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = horizontalPadding)
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                // Close Button
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.content_desc_close_edit_mode),
                        tint = Color.White,
                        modifier = Modifier.size(iconSize)
                    )
                }

                val visIcon = if (isMinimized) Icons.Default.VisibilityOff else Icons.Default.Visibility
                val visTint = Color.White

                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleMinimize),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = visIcon,
                        contentDescription = stringResource(R.string.content_desc_toggle_visibility),
                        tint = visTint,
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Vertical Divider
                Box(
                    modifier = Modifier
                        .height(20.dp)
                        .width(1.dp)
                        .background(Color.White.copy(alpha = 0.2f))
                )

                val toolsAlpha = if (isMinimized) 0.3f else 1f

                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing),
                    modifier = Modifier.alpha(toolsAlpha)
                ) {

                    // Stylus Only Toggle
                    if (!isMinimized) {
                        val iconVector = if (isStylusOnlyMode) Icons.Default.DoNotTouch else Icons.Default.TouchApp
                        val iconTint = if (isStylusOnlyMode) Color(0xFFE57373) else Color.White

                        Box(
                            modifier = Modifier
                                .size(buttonSize)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = if (isStylusOnlyMode) 0.15f else 0f))
                                .clickable(onClick = onToggleStylusOnlyMode),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = stringResource(R.string.content_desc_stylus_only_mode),
                                tint = iconTint,
                                modifier = Modifier.size(iconSize)
                            )
                        }
                    }

                    // Pen Group
                    val isPenActive = !isMinimized && (selectedTool == InkType.PEN ||
                            selectedTool == InkType.FOUNTAIN_PEN ||
                            selectedTool == InkType.PENCIL)

                    DockIcon(
                        iconRes = R.drawable.pen,
                        isActive = isPenActive,
                        tintColor = if(isMinimized) Color.Gray else activePenColor,
                        description = stringResource(R.string.content_desc_pen),
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = {
                            if(!isMinimized) {
                                if (selectedTool != InkType.PEN && selectedTool != InkType.FOUNTAIN_PEN && selectedTool != InkType.PENCIL) {
                                    onToolClick(lastPenTool)
                                } else {
                                    onToolClick(selectedTool)
                                }
                            }
                        }
                    )

                    // Highlighter
                    val isHighlighterActive = !isMinimized && (selectedTool == InkType.HIGHLIGHTER || selectedTool == InkType.HIGHLIGHTER_ROUND)
                    DockIcon(
                        iconRes = R.drawable.marker,
                        isActive = isHighlighterActive,
                        tintColor = if(isMinimized) Color.Gray else activeHighlighterColor.copy(alpha = 1f),
                        description = stringResource(R.string.content_desc_highlighter),
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = {
                            if (!isMinimized) {
                                if (selectedTool != InkType.HIGHLIGHTER && selectedTool != InkType.HIGHLIGHTER_ROUND) {
                                    onToolClick(lastHighlighterTool)
                                } else {
                                    onToolClick(selectedTool)
                                }
                            }
                        }
                    )

                    // Text Annotation
                    DockIcon(
                        iconRes = R.drawable.keyboard,
                        isActive = !isMinimized && selectedTool == InkType.TEXT,
                        tintColor = if(isMinimized) Color.Gray else Color.White,
                        description = stringResource(R.string.content_desc_text),
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = { if(!isMinimized) onToolClick(InkType.TEXT) }
                    )

                    // Eraser
                    DockIcon(
                        iconRes = R.drawable.eraser,
                        isActive = !isMinimized && selectedTool == InkType.ERASER,
                        tintColor = if(isMinimized) Color.Gray else Color.White,
                        description = stringResource(R.string.content_desc_eraser),
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = { if(!isMinimized) onToolClick(InkType.ERASER) }
                    )
                }

                // Undo
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(enabled = canUndo && !isMinimized, onClick = onUndo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = stringResource(R.string.content_desc_undo),
                        tint = if (canUndo && !isMinimized) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(iconSize)
                    )
                }

                // Redo
                Box(
                    modifier = Modifier
                        .size(buttonSize)
                        .clip(CircleShape)
                        .clickable(enabled = canRedo && !isMinimized, onClick = onRedo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Redo,
                        contentDescription = stringResource(R.string.content_desc_redo),
                        tint = if (canRedo && !isMinimized) Color.White else Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    } else {
        // Minimized Floating State (Small Circle)
        Surface(
            color = Color(0xFF1E1E1E),
            shape = CircleShape,
            shadowElevation = 8.dp,
            modifier = modifier.size(48.dp) // Reduced from 56dp
        ) {
            Box(
                modifier = Modifier.clickable(onClick = onToggleMinimize),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VisibilityOff,
                    contentDescription = stringResource(R.string.content_desc_show_dock),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun Modifier.alpha(alpha: Float) = this.then(
    Modifier.graphicsLayer { this.alpha = alpha }
)

@Composable
private fun DockIcon(
    iconRes: Int,
    isActive: Boolean,
    tintColor: Color,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    val backgroundAlpha = if (isActive) 0.15f else 0f

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = backgroundAlpha))
            .semantics { this.selected = isActive }
            .testTag("DockItem_$description")
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = description,
            tint = tintColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
fun PenPlayground(onClose: () -> Unit) {
    var selectedPen by remember { mutableStateOf(PenType.FOUNTAIN_PEN) }
    var selectedColor by remember { mutableStateOf(Color(0xFF2196F3)) } // Default Blue
    val colors = listOf(
        Color(0xFFF44336), // Red
        Color(0xFFFFEB3B), // Yellow
        Color(0xFF2196F3), // Blue
        Color(0xFF4CAF50), // Green
        Color(0xFF9C27B0), // Purple
        Color.White, // White
        Color.Black // Black
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 16.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.padding(start = 12.dp)
                )

                IconButton(onClick = onClose) {
                    Icon(
                        painter = painterResource(
                            id = R.drawable.close
                        ),
                        contentDescription = stringResource(R.string.action_close), tint = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // --- The Pen Rack ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                PenType.entries.forEach { type ->
                    val isSelected = selectedPen == type

                    val offsetY by animateDpAsState(
                        targetValue = if (isSelected) (-20).dp else 0.dp, label = "offset"
                    )

                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.2f else 1.0f, label = "scale"
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .offset(y = offsetY)
                            .scale(scale)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { selectedPen = type }) {
                        // Drawing Area
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(120.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            PenIcon(
                                color = selectedColor,
                                type = type,
                                isSelected = isSelected,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = Color.White.copy(alpha = 0.1f),
                thickness = 1.dp
            )

            Spacer(Modifier.height(24.dp))

            // --- Color Palette ---
            Row(
                modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                colors.forEach { color ->
                    val isSelected = selectedColor == color

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { selectedColor = color }) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(color = color)
                            if (isSelected) {
                                drawCircle(
                                    color = Color.White,
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 3.dp.toPx())
                                )
                            }
                        }

                        if (isSelected && color == Color.White) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        } else if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (color == Color.Black) Color.White
                                else Color.Black.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
