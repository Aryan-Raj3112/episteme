package com.aryan.reader.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The library's hero: the book the reader is most likely to come back to.
 *
 * It is the one surface on Home that is allowed to be loud, so it uses the container role rather
 * than `inverseSurface` (which turns into a bright grey slab in dark theme and reads as a card that
 * belongs to another app) and it blends the book's cover tone into that container, so two different
 * books never produce the same hero.
 */
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
    density: SharedAndroidHomeDensity = SharedAndroidHomeDensity.Standard,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(if (density.compact) 24.dp else SharedUiTokens.heroRadius)
    val isRtl = cardLayoutDirection == LayoutDirection.Rtl
    val container = MaterialTheme.colorScheme.primaryContainer
    val content = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // The hero's height follows its content, floored by the one-line card. A fixed height
            // clipped a two-line title: the progress bar was pushed past the rounded bottom edge
            // and cut in half. Titles therefore get their second line (and a longer localised
            // section label keeps its breathing room) at the cost of a slightly taller card.
            .heightIn(min = density.continueCardHeight)
            .clip(shape)
            .testTag("UnifiedLibraryContinueReading")
            .combinedClickable(onClick = onClick, onLongClick = {}),
        color = container,
        contentColor = content,
        // No shadow: tonal surfaces separate by colour in M3, and a 5dp drop shadow under the
        // largest element on the screen was the heaviest thing in the layout.
        border = BorderStroke(1.dp, content.copy(alpha = 0.10f)),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides cardLayoutDirection) {
            val backgroundBrush = Brush.horizontalGradient(
                colorStops = if (isRtl) {
                    arrayOf(
                        0f to container,
                        0.52f to coverTone.copy(alpha = 0.26f),
                        1f to coverTone.copy(alpha = 0.72f),
                    )
                } else {
                    arrayOf(
                        0f to coverTone.copy(alpha = 0.72f),
                        0.48f to coverTone.copy(alpha = 0.26f),
                        1f to container,
                    )
                },
            )
            Box(Modifier.background(backgroundBrush)) {
                Row(
                    Modifier.fillMaxWidth().padding(
                        start = if (density.compact) 12.dp else 18.dp,
                        end = if (density.compact) 12.dp else 20.dp,
                        top = density.continueCardVerticalPadding,
                        bottom = density.continueCardVerticalPadding,
                    ),
                    horizontalArrangement = Arrangement.spacedBy(if (density.compact) 12.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Deliberately not `fillMaxHeight`: that would stretch the cover to whatever
                    // height the host offers. The cover keeps its own size and the row centres it,
                    // so the card can grow for a second title line without growing the artwork.
                    Box(
                        Modifier
                            .width(density.continueCoverWidth)
                            .testTag("UnifiedLibraryContinueReadingCover")
                    ) { cover(Modifier.align(Alignment.CenterStart)) }
                    Column(
                        Modifier
                            .weight(1f)
                            .testTag("UnifiedLibraryContinueReadingContent"),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        if (density.compact) {
                            CompactContinueContent(
                                sectionLabel = sectionLabel,
                                title = title,
                                progressPercent = progressPercent,
                                progressLabel = progressLabel,
                                isRtl = isRtl,
                                fileTypeBadge = fileTypeBadge,
                            )
                        } else {
                            StandardContinueContent(
                                sectionLabel = sectionLabel,
                                title = title,
                                author = author,
                                progressPercent = progressPercent,
                                progressLabel = progressLabel,
                                sourceLabel = sourceLabel,
                                isRtl = isRtl,
                                fileTypeBadge = fileTypeBadge,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The full card, sized for a tall display: title, author, format/source and a labelled
 * progress bar.
 */
@Composable
private fun StandardContinueContent(
    sectionLabel: String,
    title: String,
    author: String,
    progressPercent: Float,
    progressLabel: String,
    sourceLabel: String?,
    isRtl: Boolean,
    fileTypeBadge: @Composable () -> Unit,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Text(
        sectionLabel.uppercase(),
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        color = onContainer.copy(alpha = 0.78f),
        textAlign = TextAlign.Start,
    )
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
    )
    Text(
        author,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = onContainer.copy(alpha = 0.74f),
        textAlign = TextAlign.Start,
    )
    Row(
        Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        fileTypeBadge()
        sourceLabel?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.66f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        progressLabel,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.labelMedium,
        color = onContainer.copy(alpha = 0.86f),
        textAlign = TextAlign.Start,
    )
    Spacer(Modifier.height(4.dp))
    ContinueProgress(
        progressPercent = progressPercent,
        isRtl = isRtl,
        barHeight = 12.dp,
        modifier = Modifier.fillMaxWidth().testTag("UnifiedLibraryContinueReadingProgress"),
    )
}

/**
 * The same card on a display too short for it. The author and the source move out (both are
 * repeated by the book's own tile directly below), the format badge and the percentage move
 * onto the label and progress lines, and only the space changes — the type stays the size
 * it is everywhere else.
 */
@Composable
private fun CompactContinueContent(
    sectionLabel: String,
    title: String,
    progressPercent: Float,
    progressLabel: String,
    isRtl: Boolean,
    fileTypeBadge: @Composable () -> Unit,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            sectionLabel.uppercase(),
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = onContainer.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
        fileTypeBadge()
    }
    Text(
        title,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Start,
    )
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContinueProgress(
            progressPercent = progressPercent,
            isRtl = isRtl,
            barHeight = 8.dp,
            modifier = Modifier.weight(1f).testTag("UnifiedLibraryContinueReadingProgress"),
        )
        Text(
            progressLabel,
            style = MaterialTheme.typography.labelSmall,
            color = onContainer.copy(alpha = 0.86f),
            maxLines = 1,
        )
    }
}

/**
 * Progress for the hero: a determinate wavy bar, the M3 expressive shape for progress.
 *
 * Compose Multiplatform's common `material3` does not expose `LinearWavyProgressIndicator`
 * (Android's does), so the wave is drawn here instead of behind an expect/actual pair. It is
 * determinate and therefore static — no frame is requested while Home sits still.
 *
 * The bar is drawn left-to-right and mirrored for RTL, in both densities, so the two cards cannot
 * drift apart.
 */
@Composable
private fun ContinueProgress(
    progressPercent: Float,
    isRtl: Boolean,
    barHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val progress = (progressPercent.coerceIn(0f, 100f) / 100f)
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier = modifier
                .height(barHeight)
                .then(if (isRtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier)
        ) {
            val strokeWidth = size.height * 0.34f
            val amplitude = (size.height - strokeWidth) / 2f
            val wavelength = size.height * 2.2f
            val centerY = size.height / 2f
            val path = Path().apply {
                moveTo(0f, centerY)
                var x = 0f
                while (x <= size.width) {
                    lineTo(x, centerY + amplitude * sin(2.0 * PI * (x / wavelength)).toFloat())
                    x += 2f
                }
            }
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            drawPath(path, onContainer.copy(alpha = 0.24f), style = stroke)
            clipRect(right = size.width * progress) {
                drawPath(path, onContainer, style = stroke)
            }
        }
    }
}
