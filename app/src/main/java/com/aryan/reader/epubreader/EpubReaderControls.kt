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
package com.aryan.reader.epubreader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import timber.log.Timber
import android.webkit.WebView
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.SearchState
import com.aryan.reader.SearchTopBar
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.tts.TtsPlaybackManager.TtsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@Composable
fun EpubReaderTopBar(
    isVisible: Boolean,
    searchState: SearchState,
    bookTitle: String,
    currentRenderMode: RenderMode,
    isBookmarked: Boolean,
    isTtsActive: Boolean,
    tapToNavigateEnabled: Boolean,
    volumeScrollEnabled: Boolean,
    onNavigateBack: () -> Unit,
    onCloseSearch: () -> Unit,
    onChangeRenderMode: (RenderMode) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleTapToNavigate: (Boolean) -> Unit,
    onToggleVolumeScroll: (Boolean) -> Unit,
    onStartAutoScroll: () -> Unit,
    searchFocusRequester: androidx.compose.ui.focus.FocusRequester,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (searchState.isSearchActive) {
                    SearchTopBar(
                        searchState = searchState,
                        focusRequester = searchFocusRequester,
                        onCloseSearch = onCloseSearch
                    )
                } else {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = bookTitle.take(40) + if (bookTitle.length > 40) "..." else "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        var showMoreMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Reading Mode: Vertical") },
                                enabled = !isTtsActive,
                                onClick = {
                                    showMoreMenu = false
                                    onChangeRenderMode(RenderMode.VERTICAL_SCROLL)
                                },
                                trailingIcon = { if (currentRenderMode == RenderMode.VERTICAL_SCROLL) Icon(Icons.Default.Check, contentDescription = "Selected") }
                            )
                            DropdownMenuItem(
                                text = { Text("Reading Mode: Paginated") },
                                enabled = !isTtsActive,
                                onClick = {
                                    showMoreMenu = false
                                    onChangeRenderMode(RenderMode.PAGINATED)
                                },
                                trailingIcon = { if (currentRenderMode == RenderMode.PAGINATED) Icon(Icons.Default.Check, contentDescription = "Selected") }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (isBookmarked) "Remove bookmark" else "Bookmark this page") },
                                onClick = {
                                    showMoreMenu = false
                                    onToggleBookmark()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Tap to Turn Pages") },
                                enabled = currentRenderMode == RenderMode.PAGINATED,
                                onClick = {
                                    onToggleTapToNavigate(!tapToNavigateEnabled)
                                    showMoreMenu = false
                                },
                                trailingIcon = { if (tapToNavigateEnabled) Icon(Icons.Default.Check, contentDescription = "Enabled") }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Volume Button Scrolling") },
                                enabled = currentRenderMode == RenderMode.VERTICAL_SCROLL,
                                onClick = {
                                    onToggleVolumeScroll(!volumeScrollEnabled)
                                    showMoreMenu = false
                                },
                                trailingIcon = { if (volumeScrollEnabled) Icon(Icons.Default.Check, contentDescription = "Enabled") }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Auto Scroll") },
                                enabled = !isTtsActive && currentRenderMode == RenderMode.VERTICAL_SCROLL,
                                onClick = {
                                    showMoreMenu = false
                                    onStartAutoScroll()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EpubReaderBottomBar(
    isVisible: Boolean,
    currentRenderMode: RenderMode,
    isTtsSessionActive: Boolean,
    ttsState: TtsState,
    isProUser: Boolean,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onSummarize: () -> Unit,
    onRecap: () -> Unit,
    onToggleTts: () -> Unit,
    onPlayPauseTts: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(45.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(
                    onClick = onOpenSlider,
                    enabled = currentRenderMode != RenderMode.VERTICAL_SCROLL
                ) {
                    Icon(painter = painterResource(id = R.drawable.slider), contentDescription = "Navigate with slider")
                }
                IconButton(onClick = onOpenDrawer) {
                    Icon(imageVector = Icons.Default.Menu, contentDescription = "Chapters Menu")
                }
                IconButton(onClick = onToggleFormat) {
                    Icon(painter = painterResource(id = R.drawable.format_size), contentDescription = "Text Formatting")
                }
                IconButton(onClick = onToggleSearch) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                }

                @Suppress("KotlinConstantConditions")
                if (BuildConfig.FLAVOR != "oss") {
                    Box {
                        var showAiFeaturesMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showAiFeaturesMenu = true }) {
                            Icon(painter = painterResource(id = R.drawable.ai), contentDescription = "AI Features")
                        }
                        DropdownMenu(
                            expanded = showAiFeaturesMenu,
                            onDismissRequest = { showAiFeaturesMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Chapter Summarization") },
                                onClick = {
                                    showAiFeaturesMenu = false
                                    onSummarize()
                                }
                            )
                            if (BuildConfig.DEBUG && isProUser) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Recap (Beta)") },
                                    onClick = {
                                        showAiFeaturesMenu = false
                                        onRecap()
                                    }
                                )
                            }
                        }
                    }
                }
                Box {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleTts) {
                            Icon(
                                painter = if (isTtsSessionActive) painterResource(id = R.drawable.close) else painterResource(id = R.drawable.text_to_speech),
                                contentDescription = if (isTtsSessionActive) "Stop TTS" else "Start TTS"
                            )
                        }
                        if (isTtsSessionActive) {
                            IconButton(
                                onClick = onPlayPauseTts,
                                enabled = !ttsState.isLoading
                            ) {
                                Icon(
                                    painter = painterResource(id = if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = if (ttsState.isPlaying) "Pause TTS" else "Resume TTS"
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderPageSlider(
    isVisible: Boolean,
    currentRenderMode: RenderMode,
    totalPages: Int,
    sliderCurrentPage: Float,
    sliderStartPage: Int,
    startPageThumbnail: Bitmap?,
    paginator: IPaginator?,
    chapters: List<EpubChapter>,
    onClose: () -> Unit,
    onScrub: (Float) -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically { fullHeight -> fullHeight } + fadeIn(),
        exit = slideOutVertically { fullHeight -> fullHeight } + fadeOut()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Dismiss area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
            )

            // Fast scrub overlay
            // Note: In the refactor, we rely on the parent or this logic to determine "isFastScrubbing".
            // Since `isFastScrubbing` was state in the parent, we'll implement a local check or just show it if `isVisible`.
            // Ideally, the parent handles the "Scrubbing Animation" separately, but let's bundle it here for simplicity.
            // For now, we only show the static overlay logic.
            // If we want the big center indicator, we can render it based on interaction state here.

            // Top back button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Exit slider navigation"
                )
            }

            // Bottom controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = sliderCurrentPage,
                            onValueChange = onScrub,
                            valueRange = 1f..(totalPages.toFloat().coerceAtLeast(1f)),
                            steps = if (totalPages > 2) totalPages - 2 else 0,
                            modifier = Modifier.fillMaxWidth(),
                            thumb = {
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {}
                            },
                            track = { sliderState ->
                                val trackHeight = 2.dp
                                val trackShape = RoundedCornerShape(trackHeight)
                                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                val fraction = if (range == 0f) 0f else {
                                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(trackHeight)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            shape = trackShape
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = trackShape
                                            )
                                    )
                                }
                            }
                        )

                        // Thumbnail Indicator
                        val startPageOffsetFraction = if (totalPages > 1) {
                            (sliderStartPage - 1).toFloat() / (totalPages - 1)
                        } else {
                            0f
                        }
                        val thumbWidth = 20.dp
                        val trackWidth = maxWidth - thumbWidth
                        val startPagePixelPosition = (trackWidth * startPageOffsetFraction) + (thumbWidth / 2)
                        val thumbnailModifier = Modifier
                            .graphicsLayer { clip = false }
                            .align(Alignment.TopStart)
                            .offset(
                                x = startPagePixelPosition - (45.dp / 2),
                                y = (-72).dp
                            )

                        if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                            startPageThumbnail?.let { thumbnail ->
                                ThumbnailWithIndicator(
                                    modifier = thumbnailModifier,
                                    onClick = { onJumpToPage(sliderStartPage) }
                                ) {
                                    Image(
                                        bitmap = thumbnail.asImageBitmap(),
                                        contentDescription = "Start page thumbnail",
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            val startPageChapterIndex = remember(sliderStartPage, paginator) {
                                (paginator as? BookPaginator)?.findChapterIndexForPage(sliderStartPage - 1)
                            }
                            val startPageChapterTitle = remember(startPageChapterIndex) {
                                startPageChapterIndex?.let { chapters.getOrNull(it)?.title }
                            }
                            ThumbnailWithIndicator(
                                modifier = thumbnailModifier,
                                onClick = { onJumpToPage(sliderStartPage) }
                            ) {
                                PaginatedThumbnailContent(
                                    pageNumber = sliderStartPage,
                                    chapterTitle = startPageChapterTitle
                                )
                            }
                        }
                    }

                    Text(
                        text = "${sliderCurrentPage.roundToInt()} / $totalPages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

// --- Helpers moved from Screen ---

@Composable
fun PageScrubbingAnimation(currentPage: Int, totalPages: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.slider),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Page $currentPage of $totalPages",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
internal fun ThumbnailWithIndicator(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(45.dp)
                .height(64.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, borderColor)
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .offset(y = (-4).dp)
                .size(8.dp)
                .rotate(45f)
                .background(borderColor)
        )
    }
}

@Composable
private fun PaginatedThumbnailContent(pageNumber: Int, chapterTitle: String?) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (chapterTitle != null) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "$pageNumber",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

suspend fun captureWebViewVisibleArea(webView: WebView): Bitmap? {
    return withContext(Dispatchers.Main) {
        if (webView.width <= 0 || webView.height <= 0) return@withContext null
        try {
            val thumbnailWidth = 180
            val thumbnailHeight = 256
            val bitmap = createBitmap(thumbnailWidth, thumbnailHeight)
            val canvas = Canvas(bitmap)
            val scale = thumbnailWidth.toFloat() / webView.width.toFloat()
            canvas.scale(scale, scale)
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture webview content")
            null
        }
    }
}

@Composable
fun AutoScrollControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    isCollapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.animateContentSize()
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "AutoScrollUnified"
        ) { collapsed ->
            Row(
                modifier = Modifier.padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (collapsed) {
                    IconButton(
                        onClick = { onCollapseChange(false) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Expand",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FilledIconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                } else {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        IconButton(
                            onClick = { onSpeedChange((speed - 0.1f).coerceAtLeast(0.1f)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Remove, "Slower", modifier = Modifier.size(18.dp))
                        }

                        Text(
                            text = "%.1fx".format(speed),
                            style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = "tnum"),
                            modifier = Modifier.widthIn(min = 40.dp),
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        IconButton(
                            onClick = { onSpeedChange((speed + 0.1f).coerceAtMost(10f)) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.Add, "Faster", modifier = Modifier.size(18.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )

                    FilledIconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { onCollapseChange(true) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Collapse",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}