package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Exact Android Home recent-book card chrome with platform cover/status slots. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SharedAndroidHomeRecentCard(
    bookId: String,
    title: String,
    author: String,
    progressPercent: Int?,
    isAvailable: Boolean,
    isDownloading: Boolean,
    isSelected: Boolean,
    hasCustomCover: Boolean,
    showStatusBadges: Boolean,
    unavailableDescription: String,
    selectedDescription: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    cover: @Composable (Modifier) -> Unit,
    statusBadges: @Composable (Modifier) -> Unit,
    fileTypeBadge: @Composable (compact: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SharedUiTokens.cardRadius)
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // Press reads as the card sinking into the grid. 0.02 is small enough that neighbouring cells
    // never appear to move, and Compose scales it to zero when the platform asks for less motion.
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        label = "HomeRecentFileCardPress",
    )
    Card(
        modifier = modifier
            .testTag("HomeRecentFileCard_$bookId")
            .graphicsLayer {
                alpha = if (isAvailable) 1f else 0.8f
                scaleX = pressScale
                scaleY = pressScale
            }
            .then(if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
            .clip(shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        // Tonal fill plus a hairline instead of a drop shadow: on a grid of six or more cards the
        // shadows stacked into a grey haze, and M3 separates surfaces by tone.
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(0.74f)) {
                val compact = maxWidth < 128.dp
                val badgePadding = if (compact) 5.dp else 8.dp
                cover(Modifier.fillMaxSize())
                if (hasCustomCover) {
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.15f),
                                0.3f to Color.Transparent,
                                0.6f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.5f),
                            )
                        )
                    )
                }
                if (showStatusBadges) statusBadges(Modifier.align(Alignment.TopStart).padding(10.dp))
                if (!isAvailable) {
                    Box(
                        // 0.35 rather than 0.5: the cover still has to be recognisable, because
                        // "not on this device" is a state to understand, not a blank tile.
                        Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isDownloading) CircularProgressIndicator(color = Color.White)
                        else Icon(Icons.Default.Info, unavailableDescription, Modifier.size(48.dp), tint = Color.White)
                    }
                }
                if (isSelected) {
                    Box(
                        Modifier.matchParentSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Check,
                            selectedDescription,
                            Modifier.size(48.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(badgePadding),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    progressPercent?.let { SharedAndroidHomeProgressBadge(it, compact) }
                    Spacer(Modifier.weight(1f))
                    fileTypeBadge(compact)
                }
            }
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    minLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SharedAndroidHomeProgressBadge(percent: Int, compact: Boolean) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Text(
            "$percent%",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = if (compact) 6.dp else 10.dp, vertical = if (compact) 3.dp else 4.dp),
        )
    }
}
