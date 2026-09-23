package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.ReaderTexture
import com.aryan.reader.shared.ReaderAutoScrollProfile
import com.aryan.reader.shared.ReaderMusicianHoldDurationMillis
import com.aryan.reader.shared.currentTimestamp
import com.aryan.reader.shared.readerAutoScrollPixelsPerSecond
import com.aryan.reader.shared.generated.resources.Res
import com.aryan.reader.shared.generated.resources.classy_fabric
import com.aryan.reader.shared.generated.resources.ep_naturalwhite
import com.aryan.reader.shared.generated.resources.grey_wash_wall
import com.aryan.reader.shared.generated.resources.light_veneer
import com.aryan.reader.shared.generated.resources.retina_wood
import com.aryan.reader.shared.generated.resources.retro_intro
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt
import org.jetbrains.compose.resources.imageResource

@Composable
internal fun SharedMobileEpubMusicianOverlay(
    onGesture: (isRightRegion: Boolean, isLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    Box(modifier) {
        listOf(false, true).forEach { isRightRegion ->
            var holdProgress by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.25f)
                    .fillMaxHeight(0.4f)
                    .align(if (isRightRegion) Alignment.TopEnd else Alignment.TopStart)
                    .offset(y = 100.dp)
                    .padding(
                        start = if (isRightRegion) 0.dp else 8.dp,
                        end = if (isRightRegion) 8.dp else 0.dp,
                    )
                    .border(2.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .pointerInput(isRightRegion) {
                        awaitEachGesture {
                            awaitFirstDown()
                            var longPressTriggered = false
                            val holdJob = scope.launch {
                                val startedAt = currentTimestamp()
                                while (true) {
                                    val elapsed = currentTimestamp() - startedAt
                                    holdProgress = (elapsed.toFloat() / ReaderMusicianHoldDurationMillis).coerceIn(0f, 1f)
                                    if (elapsed >= ReaderMusicianHoldDurationMillis) {
                                        longPressTriggered = true
                                        holdProgress = 0f
                                        onGesture(isRightRegion, true)
                                        break
                                    }
                                    delay(16L)
                                }
                            }
                            val up = waitForUpOrCancellation()
                            holdJob.cancel()
                            holdProgress = 0f
                            if (!longPressTriggered && up != null) {
                                up.consume()
                                onGesture(isRightRegion, false)
                            }
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (holdProgress > 0f) {
                    CircularProgressIndicator(
                        progress = { holdProgress },
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        trackColor = Color.Transparent,
                        strokeWidth = 4.dp,
                    )
                    Icon(
                        if (isRightRegion) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubAutoScrollControls(
    isPlaying: Boolean,
    profile: ReaderAutoScrollProfile,
    isLocalMode: Boolean,
    useSlider: Boolean,
    isMusicianMode: Boolean,
    isCollapsed: Boolean,
    onPlayPause: () -> Unit,
    onSpeedChange: (Float) -> Unit,
    onMinSpeedChange: (Float) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onInputModeToggle: () -> Unit,
    onMusicianModeToggle: () -> Unit,
    onCollapseChange: (Boolean) -> Unit,
    onScrollToTop: () -> Unit,
    onLocalModeChange: (Boolean) -> Unit,
    onClose: () -> Unit,
    isTempPaused: Boolean = false,
    modifier: Modifier = Modifier
) {
    var showModeMenu by remember { mutableStateOf(false) }
    val profile = profile.sanitized()
    val speedOptions = listOf(0.1f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
    Surface(
        modifier = modifier
            .then(if (isCollapsed) Modifier else Modifier.fillMaxWidth())
            .widthIn(max = 400.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 8.dp,
        shadowElevation = 8.dp
    ) {
        // Android parity (AutoScrollControls): collapse crossfades, not snaps.
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = { fadeIn(animationSpec = tween(200)) togetherWith fadeOut(animationSpec = tween(200)) },
            label = "EpubAutoScrollCollapse"
        ) { collapsed ->
            if (collapsed) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(onClick = { onCollapseChange(false) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = readerString("auto_scroll_expand", "Expand Auto Scroll"),
                )
                }
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause auto scroll" else "Resume auto scroll",
                        )
                    }
                    // Android benchmark (EpubReaderControls.kt:1283-1289): spinner
                    // over play while temporarily paused (e.g. finger on screen).
                    if (isTempPaused && isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        } else Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center) {
                    IconButton(onClick = onPlayPause) {
                        Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = if (isPlaying) "Pause auto scroll" else "Resume auto scroll")
                    }
                    // Android benchmark (EpubReaderControls.kt:1447-1453).
                    if (isTempPaused && isPlaying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            strokeWidth = 3.dp
                        )
                    }
                }
                Box {
                    TextButton(onClick = { showModeMenu = true }) {
                        Text(if (isLocalMode) "Local Speed" else "Global Speed")
                        Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = readerString("auto_scroll_select_mode", "Select auto-scroll mode"),
                    )
                    }
                    DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                        DropdownMenuItem(
                            text = { Column { Text(readerString("auto_scroll_global_label", "Global Speed"), fontWeight = FontWeight.Bold); Text(
                                        readerString("auto_scroll_desc_global", "Applies to all files"),
                                        style = MaterialTheme.typography.bodySmall,
                                    ) } },
                            trailingIcon = { if (!isLocalMode) Text("✓") },
                            onClick = { onLocalModeChange(false); showModeMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Column { Text(readerString("auto_scroll_local_label", "Local Speed"), fontWeight = FontWeight.Bold); Text(
                                        readerString("auto_scroll_desc_local", "Saved for this file only"),
                                        style = MaterialTheme.typography.bodySmall,
                                    ) } },
                            trailingIcon = { if (isLocalMode) Text("✓") },
                            onClick = { onLocalModeChange(true); showModeMenu = false }
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Text("${sharedMobileAutoScrollSpeedLabel(profile.speed)}x", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onScrollToTop) {
                    Icon(
                        Icons.Default.ArrowUpward,
                        contentDescription = readerString("auto_scroll_scroll_to_top", "Scroll to top"),
                    )
                }
                IconButton(onClick = onMusicianModeToggle) {
                    Icon(
                        SharedReaderIcons.MusicNote,
                        contentDescription = if (isMusicianMode) "Disable musician mode" else "Enable musician mode",
                        tint = if (isMusicianMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onInputModeToggle) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = readerString("auto_scroll_swap_controls", "Swap speed controls"),
                    )
                }
                IconButton(onClick = { onCollapseChange(true) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = readerString("auto_scroll_collapse", "Collapse Auto Scroll"),
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = readerString("desktop_stop_auto_scroll", "Stop auto scroll"),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SharedMobileEpubSpeedMenu("Min", profile.minSpeed, speedOptions, onMinSpeedChange)
                SharedMobileEpubSpeedMenu("Max", profile.maxSpeed, speedOptions, onMaxSpeedChange)
            }
            if (useSlider) {
                Slider(
                    value = profile.speed,
                    onValueChange = { onSpeedChange((it * 10f).roundToInt() / 10f) },
                    valueRange = profile.minSpeed..profile.maxSpeed.coerceAtLeast(profile.minSpeed + 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { onSpeedChange((profile.speed - 0.1f).coerceAtLeast(profile.minSpeed)) }) {
                        Icon(Icons.Default.Remove, contentDescription = readerString("content_desc_slower", "Slower"))
                    }
                    Text("${sharedMobileAutoScrollSpeedLabel(profile.speed)}x", style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onSpeedChange((profile.speed + 0.1f).coerceAtMost(profile.maxSpeed)) }) {
                        Icon(Icons.Default.Add, contentDescription = readerString("content_desc_faster", "Faster"))
                    }
                }
            }
            }
        }
    }
}

@Composable
internal fun SharedMobileEpubSpeedMenu(
    label: String,
    value: Float,
    options: List<Float>,
    onValueChange: (Float) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("$label ${sharedMobileAutoScrollSpeedLabel(value)}x")
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text("${sharedMobileAutoScrollSpeedLabel(option)}x") },
                    trailingIcon = { if (option == value) Text("✓") },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun sharedMobileAutoScrollSpeedLabel(value: Float): String =
    if (value % 1f == 0f) value.roundToInt().toString() else ((value * 10f).roundToInt() / 10f).toString()

@Composable
internal fun sharedMobileEpubTextureBitmap(textureId: String?): ImageBitmap? {
    val resource = when (textureId) {
        ReaderTexture.NATURAL_WHITE.id -> Res.drawable.ep_naturalwhite
        ReaderTexture.RETINA_WOOD.id -> Res.drawable.retina_wood
        ReaderTexture.LIGHT_VENEER.id -> Res.drawable.light_veneer
        ReaderTexture.GREY_WASH.id -> Res.drawable.grey_wash_wall
        ReaderTexture.CLASSY_FABRIC.id -> Res.drawable.classy_fabric
        ReaderTexture.RETRO_INTRO.id -> Res.drawable.retro_intro
        else -> null
    }
    return resource?.let { imageResource(it) }
}

internal fun sharedMobileEpubAutoScrollStartScript(speed: Float): String {
    val effectiveSpeed = readerAutoScrollPixelsPerSecond(speed)
    // Android parity (epub_reader.js autoScroll loop): Android accumulates
    // speed*0.5px per animation frame with no timer floor, so top speeds stay
    // distinct. The old 1px-tick interval (1000/speed, 6ms floor) capped iOS at
    // ~166px/s, making 5.5x-10x look dead. Use a fixed 50ms tick with a pixel
    // accumulator instead: fractional speeds stay smooth and the full 0.1-10x
    // range maps to distinct px/s with no clamp.
    return """
    (function () {
      if (window.readerIosAutoScrollTimer) window.clearInterval(window.readerIosAutoScrollTimer);
      window.readerIosAutoScrollAccumulator = 0;
      window.readerIosAutoScrollTimer = window.setInterval(function () {
        window.readerIosAutoScrollAccumulator += $effectiveSpeed * 0.05;
        var pixels = Math.floor(window.readerIosAutoScrollAccumulator);
        if (pixels >= 1) {
          window.readerIosAutoScrollAccumulator -= pixels;
          window.scrollBy(0, pixels);
        }
        var root = document.documentElement;
        if (window.scrollY + window.innerHeight >= root.scrollHeight - 2) {
          window.clearInterval(window.readerIosAutoScrollTimer);
          window.readerIosAutoScrollTimer = null;
          if (window.kmpJsBridge && window.kmpJsBridge.callNative) {
            window.kmpJsBridge.callNative('readerAutoScrollChapterEnd', '{}');
          }
        }
      }, 50);
    })();
""".trimIndent()
}

internal val SharedMobileEpubAutoScrollStopScript = """
    (function () {
      if (window.readerIosAutoScrollTimer) window.clearInterval(window.readerIosAutoScrollTimer);
      window.readerIosAutoScrollTimer = null;
    })();
""".trimIndent()

internal fun sharedMobileEpubScrollToEndScript(chunkIndex: Int, chunkHtml: String?): String {
    val chunkInjection = when {
        chunkIndex < 0 || chunkHtml == null -> ""
        chunkHtml.length <= com.aryan.reader.shared.reader.ReaderHtmlDocumentBuilder.MaxInlineVirtualChunkChars ->
            "if (window.readerVirtualization) window.readerVirtualization.provideChunk($chunkIndex, ${JsonPrimitive(chunkHtml)});"
        else ->
            "if (window.readerVirtualization&&window.readerVirtualization.requestChunk) window.readerVirtualization.requestChunk($chunkIndex);"
    }
    return """
        (function () {
          $chunkInjection
          function scrollEnd() {
            var root = document.scrollingElement || document.documentElement;
            window.scrollTo(0, Math.max(0, root.scrollHeight - window.innerHeight));
          }
          scrollEnd();
          // Large tail chunks arrive via the bridge; re-scroll once loaded.
          window.setTimeout(scrollEnd, 500);
        })();
    """.trimIndent()
}
