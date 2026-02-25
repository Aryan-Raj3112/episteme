/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme Authors
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
 * Electronic mail: epistemereader@gmail.com
 */
package com.aryan.reader.pdf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
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
    isSticky: Boolean = false,
    isMinimized: Boolean,
    onToggleMinimize: () -> Unit
) {
    val showFullDock = isSticky || !isMinimized

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
                modifier = Modifier.padding(horizontal = horizontalPadding),
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
                        contentDescription = "Close Edit Mode",
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
                        contentDescription = "Toggle Visibility",
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
                    // Pen Group
                    val isPenActive = !isMinimized && (selectedTool == InkType.PEN ||
                            selectedTool == InkType.FOUNTAIN_PEN ||
                            selectedTool == InkType.PENCIL)

                    DockIcon(
                        iconRes = R.drawable.pen,
                        isActive = isPenActive,
                        tintColor = if(isMinimized) Color.Gray else activePenColor,
                        description = "Pen",
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = { if(!isMinimized) onToolClick(lastPenTool) }
                    )

                    // Highlighter
                    val isHighlighterActive = !isMinimized && (selectedTool == InkType.HIGHLIGHTER || selectedTool == InkType.HIGHLIGHTER_ROUND)
                    DockIcon(
                        iconRes = R.drawable.marker,
                        isActive = isHighlighterActive,
                        tintColor = if(isMinimized) Color.Gray else activeHighlighterColor.copy(alpha = 1f),
                        description = "Highlighter",
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = {
                            if (!isMinimized) {
                                if (selectedTool != InkType.HIGHLIGHTER && selectedTool != InkType.HIGHLIGHTER_ROUND) {
                                    onToolClick(InkType.HIGHLIGHTER)
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
                        description = "Text",
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = { if(!isMinimized) onToolClick(InkType.TEXT) }
                    )

                    // Eraser
                    DockIcon(
                        iconRes = R.drawable.eraser,
                        isActive = !isMinimized && selectedTool == InkType.ERASER,
                        tintColor = if(isMinimized) Color.Gray else Color.White,
                        description = "Eraser",
                        size = buttonSize,
                        iconSize = iconSize,
                        onClick = { if(!isMinimized) onToolClick(InkType.ERASER) }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

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
                        contentDescription = "Undo",
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
                        contentDescription = "Redo",
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
                    contentDescription = "Show Dock",
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