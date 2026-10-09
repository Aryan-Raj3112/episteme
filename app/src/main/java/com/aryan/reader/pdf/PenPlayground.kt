package com.aryan.reader.pdf

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aryan.reader.R
import com.aryan.reader.shared.ui.sharedPdfSelectionRingRadius

/**
 * Debug-only surface for eyeballing every pen/highlighter icon rendering.
 * Reached from the PDF debug menu; not part of the reader.
 */
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
                    .height(170.dp),
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
                                .height(150.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            PenIcon(
                                color = selectedColor,
                                type = type,
                                isSelected = isSelected,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.95f)
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
                                    radius = sharedPdfSelectionRingRadius(size.minDimension, 3.dp.toPx()),
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
