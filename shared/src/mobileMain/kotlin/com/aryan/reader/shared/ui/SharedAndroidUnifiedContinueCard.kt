package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

@Composable
fun SharedAndroidUnifiedContinueCard(
    sectionLabel: String,
    title: String,
    author: String,
    progressPercent: Float,
    progressLabel: String,
    sourceLabel: String?,
    coverTone: Color,
    cardLayoutDirection: LayoutDirection,
    onClick: () -> Unit,
    cover: @Composable (Modifier) -> Unit,
    fileTypeBadge: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val isRtl = cardLayoutDirection == LayoutDirection.Rtl
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(178.dp)
            .clip(shape)
            .testTag("UnifiedLibraryContinueReading")
            .combinedClickable(onClick = onClick, onLongClick = {}),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 5.dp,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides cardLayoutDirection) {
            val backgroundBrush = if (isRtl) {
                Brush.horizontalGradient(
                    0f to MaterialTheme.colorScheme.inverseSurface,
                    0.48f to coverTone.copy(alpha = 0.28f),
                    1f to coverTone.copy(alpha = 0.74f),
                )
            } else {
                Brush.horizontalGradient(
                    0f to coverTone.copy(alpha = 0.74f),
                    0.48f to coverTone.copy(alpha = 0.28f),
                    1f to MaterialTheme.colorScheme.inverseSurface,
                )
            }
            Box(Modifier.background(backgroundBrush)) {
                Row(
                    Modifier.fillMaxSize().padding(start = 16.dp, end = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(94.dp)
                            .fillMaxHeight()
                            .testTag("UnifiedLibraryContinueReadingCover")
                    ) { cover(Modifier.align(Alignment.CenterStart)) }
                    Column(
                        Modifier
                            .weight(1f)
                            .testTag("UnifiedLibraryContinueReadingContent"),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            sectionLabel.uppercase(),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Start,
                        )
                        Text(
                            title,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                        Text(
                            author,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f),
                            textAlign = TextAlign.Start,
                        )
                        Row(
                            Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            fileTypeBadge()
                            sourceLabel?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.66f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Start,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            progressLabel,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.82f),
                            textAlign = TextAlign.Start,
                        )
                        Spacer(Modifier.height(6.dp))
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            LinearProgressIndicator(
                                progress = { progressPercent.coerceIn(0f, 100f) / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .testTag("UnifiedLibraryContinueReadingProgress")
                                    .then(
                                        if (isRtl) {
                                            Modifier.graphicsLayer { scaleX = -1f }
                                        } else {
                                            Modifier
                                        }
                                    ),
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f),
                            )
                        }
                    }
                }
            }
        }
    }
}
