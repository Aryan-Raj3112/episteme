package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.reader.SharedMediaOverlaySpeeds
import com.aryan.reader.shared.reader.sharedMediaOverlaySpeedLabel

/**
 * The narration bar: title, position, transport and stop.
 *
 * Promoted to shared from Android's `EpubReaderMediaOverlay.kt` so both platforms render one bar.
 * Modelled on the read-aloud overlay's compact row, and deliberately a separate composable rather
 * than a mode of it. The two bars show different things — a spoken chunk's progress against a
 * synthesized session, a narrated clip's position in a publisher's recording — and coupling them
 * would mean one growing conditionals for the other's state.
 *
 * It carries the speed control because narration is the one playback surface where the recording's
 * pace is the publisher's choice rather than the reader's: a 2x listener has no other way to say so,
 * and [SharedMediaOverlaySpeeds] is small enough to be a menu rather than a screen.
 *
 * Labels come from the shared string table rather than Android resources, because a shared
 * composable cannot read `R.string`, and Android's copies of these keys are already in the generated
 * iOS catalogs (see `scripts/generate_shared_mobile_localizations.py`).
 */
@Composable
fun SharedMobileEpubMediaOverlayBar(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    isLoading: Boolean,
    speed: Float,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onTogglePlayPause: () -> Unit,
    onPreviousClip: () -> Unit,
    onNextClip: () -> Unit,
    onSpeedSelected: (Float) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speedDescription = readerString("content_desc_media_overlay_speed", "Narration speed")
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(4.dp))

            IconButton(
                enabled = canSkipPrevious,
                onClick = onPreviousClip,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = readerString(
                        "content_desc_media_overlay_previous",
                        "Previous narrated passage"
                    )
                )
            }

            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(40.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = readerString(
                            "content_desc_media_overlay_play_pause",
                            "Play or pause narration"
                        )
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        strokeWidth = 2.dp
                    )
                }
            }

            IconButton(
                enabled = canSkipNext,
                onClick = onNextClip,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = readerString(
                        "content_desc_media_overlay_next",
                        "Next narrated passage"
                    )
                )
            }

            var speedMenuExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(
                    onClick = { speedMenuExpanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier
                        .size(width = 48.dp, height = 40.dp)
                        .semantics { contentDescription = speedDescription }
                ) {
                    Text(
                        text = sharedMediaOverlaySpeedLabel(speed),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1
                    )
                }
                DropdownMenu(
                    expanded = speedMenuExpanded,
                    onDismissRequest = { speedMenuExpanded = false }
                ) {
                    SharedMediaOverlaySpeeds.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = sharedMediaOverlaySpeedLabel(option),
                                    fontWeight = if (option == speed) FontWeight.Medium else FontWeight.Normal
                                )
                            },
                            onClick = {
                                speedMenuExpanded = false
                                onSpeedSelected(option)
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = readerString("content_desc_media_overlay_stop", "Stop narration")
                )
            }
        }
    }
}