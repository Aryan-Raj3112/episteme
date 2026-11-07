@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderMotionPolicy
import kotlin.math.roundToInt

/**
 * Localised labels the auto-scroll surface renders.
 *
 * The composable itself does not call `readerString` for these: the Android reader composes shared
 * surfaces without a [LocalSharedStringResolver], so a shared lookup would silently fall back to
 * English inside the reader while the rest of the app is translated. Each host supplies its own
 * table — Android passes `stringResource(...)` values, iOS and desktop use
 * [sharedMobileAutoScrollLabels].
 */
data class SharedMobileAutoScrollLabels(
    val expand: String,
    val collapse: String,
    val pause: String,
    val play: String,
    val globalSpeed: String,
    val localSpeed: String,
    val selectMode: String,
    val appliesToAllFiles: String,
    val savedForThisFile: String,
    val scrollToTop: String,
    val enableMusicianMode: String,
    val disableMusicianMode: String,
    val swapControls: String,
    val close: String,
    val min: String,
    val max: String,
    val slower: String,
    val faster: String,
)

/** Resolver-backed labels, for hosts that provide one. */
@Composable
fun sharedMobileAutoScrollLabels(): SharedMobileAutoScrollLabels {
    return SharedMobileAutoScrollLabels(
        expand = readerString("content_desc_expand", "Expand"),
        collapse = readerString("content_desc_collapse", "Collapse"),
        pause = readerString("tooltip_tts_pause", "Pause"),
        play = readerString("content_desc_start_playback", "Play"),
        globalSpeed = readerString("auto_scroll_global_speed", "Global Speed"),
        localSpeed = readerString("auto_scroll_local_speed", "Local Speed"),
        selectMode = readerString("content_desc_select_mode", "Select Mode"),
        appliesToAllFiles = readerString("auto_scroll_applies_all_files", "Applies to all files"),
        savedForThisFile = readerString("auto_scroll_saved_for_file", "Saved for this file only"),
        scrollToTop = readerString("action_scroll_to_top", "Scroll to Top"),
        enableMusicianMode = readerString("content_desc_enable_musician_mode", "Enable Musician Mode"),
        disableMusicianMode = readerString("content_desc_disable_musician_mode", "Disable Musician Mode"),
        swapControls = readerString("content_desc_swap_controls", "Swap Controls"),
        close = readerString("action_close", "Close"),
        min = readerString("label_min", "Min"),
        max = readerString("label_max", "Max"),
        slower = readerString("content_desc_slower", "Slower"),
        faster = readerString("content_desc_faster", "Faster"),
    )
}

/**
 * One auto-scroll control surface for every vertical reader (EPUB WebView,
 * native vertical EPUB, PDF). Android shares a single `AutoScrollControls`
 * composable between its EPUB and PDF readers (PdfViewerScreen imports
 * `com.aryan.reader.epubreader.AutoScrollControls`); iOS used to have two
 * drifting copies, which is what made the PDF and EPUB overlays look and behave
 * differently.
 *
 * Layout, sizes, iconography, colours and animation mirror the Android
 * benchmark (`SharedMobileAutoScrollChrome.kt` owns the shared speed maths, the
 * `"%.1fx"` label format and the per-reader chrome anchoring):
 *  - collapsed: chevron + play/pause pill with the temp-pause spinner
 *  - expanded: mode selector on the left, tool row on the right, then the
 *    play/pause pill next to the Min/Max menus and either the slider or the
 *    +/- stepper
 *
 * Android's own copy also honoured its `ReaderMotionPolicy`: under reduce-motion it
 * skips `animateContentSize` and uses `EnterTransition.None`/`ExitTransition.None`
 * instead of the 200ms fade, which is why the parameter is here rather than being
 * different on the two hosts.
 */
@Composable
fun SharedMobileAutoScrollControls(
    labels: SharedMobileAutoScrollLabels = sharedMobileAutoScrollLabels(),
    readerMotionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
    isPlaying: Boolean,
    isTempPaused: Boolean,
    speed: Float,
    minSpeed: Float,
    maxSpeed: Float,
    isLocalMode: Boolean,
    isMusicianMode: Boolean,
    useSlider: Boolean,
    isCollapsed: Boolean,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMinSpeedChange: (Float) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    /** Android keeps this optional (`onScrollToTop: (() -> Unit)?`). */
    onScrollToTop: (() -> Unit)? = null,
    onMusicianModeToggle: () -> Unit,
    onInputModeToggle: () -> Unit,
    onCollapseChange: (Boolean) -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showModeMenu by remember { mutableStateOf(false) }
    val safeSpeed = speed.coerceIn(minSpeed, maxSpeed.coerceAtLeast(minSpeed))
    val playPauseDescription = if (isPlaying) labels.pause else labels.play
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier
            .widthIn(max = 400.dp)
            .then(if (readerMotionPolicy.reduceMotion) Modifier else Modifier.animateContentSize())
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = {
                if (readerMotionPolicy.reduceMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                }
            },
            label = "AutoScrollUnified"
        ) { collapsed ->
            if (collapsed) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(onClick = { onCollapseChange(false) }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Default.ChevronLeft,
                            contentDescription = labels.expand,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = onPlayPause,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = playPauseDescription,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isTempPaused && isPlaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showModeMenu = true }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = if (isLocalMode) labels.localSpeed else labels.globalSpeed,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = labels.selectMode,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            SharedDropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                SharedMobileAutoScrollModeMenuItem(
                                    selected = !isLocalMode,
                                    title = labels.globalSpeed,
                                    subtitle = labels.appliesToAllFiles,
                                    onSelect = { onLocalModeChange(false); showModeMenu = false }
                                )
                                HorizontalDivider()
                                SharedMobileAutoScrollModeMenuItem(
                                    selected = isLocalMode,
                                    title = labels.localSpeed,
                                    subtitle = labels.savedForThisFile,
                                    onSelect = { onLocalModeChange(true); showModeMenu = false }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (onScrollToTop != null) {
                                IconButton(onClick = onScrollToTop, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = labels.scrollToTop,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(onClick = onMusicianModeToggle, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    SharedReaderIcons.MusicNote,
                                    contentDescription =
                                        if (isMusicianMode) labels.disableMusicianMode else labels.enableMusicianMode,
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isMusicianMode) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            IconButton(onClick = onInputModeToggle, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.SwapHoriz,
                                    contentDescription = labels.swapControls,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onCollapseChange(true) }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.ChevronRight,
                                    contentDescription = labels.collapse,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = labels.close,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            FilledIconButton(
                                onClick = onPlayPause,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = playPauseDescription,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (isTempPaused && isPlaying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    strokeWidth = 3.dp
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SharedMobileAutoScrollSpeedMenu(
                                        label = labels.min,
                                        value = minSpeed,
                                        onValueChange = onMinSpeedChange
                                    )
                                    SharedMobileAutoScrollSpeedMenu(
                                        label = labels.max,
                                        value = maxSpeed,
                                        onValueChange = onMaxSpeedChange
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                val safeMax = maxSpeed.coerceAtLeast(minSpeed + 0.1f)
                                if (useSlider) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = sharedMobileAutoScrollSpeedLabel(safeSpeed),
                                            style = MaterialTheme.typography.titleMedium
                                                .copy(fontFeatureSettings = "tnum"),
                                            modifier = Modifier.width(45.dp),
                                            textAlign = TextAlign.End
                                        )
                                        val steps = ((safeMax - minSpeed) / 0.1f).roundToInt() - 1
                                        Slider(
                                            value = safeSpeed,
                                            onValueChange = { onSpeedChange(snapSharedMobileAutoScrollSpeed(it)) },
                                            valueRange = minSpeed..safeMax,
                                            steps = if (steps > 0) steps else 0,
                                            modifier = Modifier.weight(1f),
                                            thumb = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                )
                                            },
                                            track = { sliderState ->
                                                val fraction =
                                                    (sliderState.value - sliderState.valueRange.start) /
                                                        (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                            RoundedCornerShape(2.dp)
                                                        ),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(fraction)
                                                            .height(4.dp)
                                                            .background(
                                                                MaterialTheme.colorScheme.primary,
                                                                RoundedCornerShape(2.dp)
                                                            )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.height(48.dp).fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    onSpeedChange(
                                                        snapSharedMobileAutoScrollSpeed(safeSpeed - 0.1f)
                                                            .coerceAtLeast(minSpeed)
                                                    )
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Remove,
                                                    labels.slower
                                                )
                                            }
                                            Text(
                                                text = sharedMobileAutoScrollSpeedLabel(safeSpeed),
                                                style = MaterialTheme.typography.titleMedium
                                                    .copy(fontFeatureSettings = "tnum"),
                                                textAlign = TextAlign.Center
                                            )
                                            IconButton(
                                                onClick = {
                                                    onSpeedChange(
                                                        snapSharedMobileAutoScrollSpeed(safeSpeed + 0.1f)
                                                            .coerceAtMost(safeMax)
                                                    )
                                                },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Add,
                                                    labels.faster
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedMobileAutoScrollModeMenuItem(
    selected: Boolean,
    title: String,
    subtitle: String,
    onSelect: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        onClick = onSelect,
        // Android benchmark (AutoScrollControls mode menu): the trailing check is decorative,
        // so it carries no content description — the item's title and subtitle already state which
        // mode is active, and an extra "Enabled" node only adds a second announcement.
        trailingIcon = {
            if (selected) Icon(Icons.Default.Check, contentDescription = null)
        }
    )
}

/** Android `SpeedDropdown`: a small primary label that opens the speed options. */
@Composable
private fun SharedMobileAutoScrollSpeedMenu(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "$label: ${sharedMobileAutoScrollSpeedLabel(value)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(4.dp)
        )
        SharedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sharedMobileAutoScrollSpeedOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sharedMobileAutoScrollSpeedLabel(option)) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
