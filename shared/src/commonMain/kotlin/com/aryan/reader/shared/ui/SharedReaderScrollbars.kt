package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SharedScrollbarState(
    val progress: Float,
    val preferredThumbHeightPx: Float,
    val contentHeightPx: Float,
    val viewportHeightPx: Float
)

private data class SharedPdfScrollbarState(
    val progress: Float,
    val contentHeightPx: Float,
    val viewportHeightPx: Float
)

@Composable
fun SharedReaderVerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val scrollbarState by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            if (totalItems == 0 || visibleItems.isEmpty() || viewportHeight <= 0f) {
                return@derivedStateOf null
            }

            val averageItemHeight = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
            val contentHeight = (averageItemHeight * totalItems).coerceAtLeast(viewportHeight)
            val viewportRatio = viewportHeight / contentHeight
            if (viewportRatio >= 1f) return@derivedStateOf null

            val maxThumbHeight = viewportHeight / 2f
            val minThumbHeight = minOf(80f, maxThumbHeight)
            val thumbHeight = (viewportHeight * viewportRatio).coerceIn(minThumbHeight, maxThumbHeight)
            val currentScroll = (listState.firstVisibleItemIndex * averageItemHeight) +
                listState.firstVisibleItemScrollOffset
            val maxScroll = contentHeight - viewportHeight
            val progress = (currentScroll / maxScroll).coerceIn(0f, 1f)

            SharedScrollbarState(
                progress = progress,
                preferredThumbHeightPx = thumbHeight,
                contentHeightPx = contentHeight,
                viewportHeightPx = viewportHeight
            )
        }
    }

    val state = scrollbarState ?: return
    val density = LocalDensity.current
    var isDraggingScrollbar by remember { mutableStateOf(false) }
    var scrollbarVisible by remember { mutableStateOf(false) }
    var scrollInteractionTick by remember { mutableIntStateOf(0) }
    var scrollbarTrackHeight by remember { mutableStateOf(0f) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index != previousIndex || abs(offset - previousOffset) > 1) {
                    scrollInteractionTick += 1
                }
                previousIndex = index
                previousOffset = offset
            }
    }

    LaunchedEffect(scrollInteractionTick, isDraggingScrollbar) {
        if (isDraggingScrollbar) {
            scrollbarVisible = true
        } else if (scrollInteractionTick > 0) {
            scrollbarVisible = true
            delay(5_000)
            scrollbarVisible = false
        }
    }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (scrollbarVisible || isDraggingScrollbar) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "sharedReaderScrollbarAlpha"
    )
    val activeThemeColor = MaterialTheme.colorScheme.primary
    val idleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    val barColor by animateColorAsState(
        targetValue = if (isDraggingScrollbar) activeThemeColor else idleColor,
        label = "sharedReaderScrollbarColor"
    )
    val scrollbarIdleWidth = 4.dp
    val scrollbarActiveWidth = 8.dp
    val barWidth by animateDpAsState(
        targetValue = if (isDraggingScrollbar) scrollbarActiveWidth else scrollbarIdleWidth,
        label = "sharedReaderScrollbarWidth"
    )
    val preferredThumbHeight = with(density) { state.preferredThumbHeightPx.toDp() }
    val scrollbarIdleHeight = preferredThumbHeight.coerceAtLeast(40.dp)
    val scrollbarActiveHeight = preferredThumbHeight.coerceAtLeast(60.dp)
    val barHeight by animateDpAsState(
        targetValue = if (isDraggingScrollbar) scrollbarActiveHeight else scrollbarIdleHeight,
        label = "sharedReaderScrollbarHeight"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .padding(top = 8.dp, bottom = 8.dp)
            .onGloballyPositioned { coordinates ->
                scrollbarTrackHeight = coordinates.size.height.toFloat()
            }
    ) {
        val thumbHeightPx = with(density) { barHeight.toPx() }
        val effectiveTrackHeight = scrollbarTrackHeight.takeIf { it > 0f } ?: state.viewportHeightPx
        val availableSpace = (effectiveTrackHeight - thumbHeightPx).coerceAtLeast(0f)
        val thumbY = (availableSpace * state.progress).coerceIn(0f, availableSpace)

        Box(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = thumbY.roundToInt()) }
                .align(Alignment.TopEnd)
                .alpha(scrollbarAlpha)
                .padding(end = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .height(barHeight)
                    .width(36.dp)
                    .pointerInput(scrollbarTrackHeight, state.contentHeightPx, state.viewportHeightPx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            try {
                                isDraggingScrollbar = true
                                scrollbarVisible = true
                                scrollInteractionTick += 1
                                down.consume()

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                    if (change == null || !change.pressed) break

                                    val deltaY = change.position.y - change.previousPosition.y
                                    if (deltaY != 0f) {
                                        change.consume()
                                        val trackHeight = scrollbarTrackHeight.takeIf { it > 0f }
                                            ?: state.viewportHeightPx
                                        val trackSpace = (trackHeight - with(density) { scrollbarActiveHeight.toPx() })
                                            .coerceAtLeast(1f)
                                        val scrollDelta = (deltaY / trackSpace) *
                                            (state.contentHeightPx - state.viewportHeightPx)
                                        listState.dispatchRawDelta(scrollDelta)
                                        scrollInteractionTick += 1
                                    }
                                }
                            } finally {
                                isDraggingScrollbar = false
                                scrollInteractionTick += 1
                            }
                        }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .size(width = barWidth, height = barHeight)
                        .background(barColor, RoundedCornerShape(999.dp))
                )
            }
        }
    }
}

@Composable
fun SharedPdfVerticalScrollbar(
    listState: LazyListState,
    pageCount: Int,
    currentPage: Int,
    isDarkMode: Boolean,
    modifier: Modifier = Modifier
) {
    val scrollbarState by remember(listState) {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()
            val totalItems = layoutInfo.totalItemsCount
            if (totalItems == 0 || visibleItems.isEmpty() || viewportHeight <= 0f) {
                return@derivedStateOf null
            }

            val averageItemHeight = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
            val contentHeight = (averageItemHeight * totalItems).coerceAtLeast(viewportHeight)
            val maxScroll = contentHeight - viewportHeight
            if (maxScroll <= 1f) return@derivedStateOf null

            val currentScroll = (listState.firstVisibleItemIndex * averageItemHeight) +
                listState.firstVisibleItemScrollOffset
            SharedPdfScrollbarState(
                progress = (currentScroll / maxScroll).coerceIn(0f, 1f),
                contentHeightPx = contentHeight,
                viewportHeightPx = viewportHeight
            )
        }
    }

    val state = scrollbarState ?: return
    val density = LocalDensity.current
    var isDraggingScrollbar by remember { mutableStateOf(false) }
    var scrollbarVisible by remember { mutableStateOf(false) }
    var scrollInteractionTick by remember { mutableIntStateOf(0) }
    var scrollbarTrackHeight by remember { mutableStateOf(0f) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (index != previousIndex || abs(offset - previousOffset) > 1) {
                    scrollInteractionTick += 1
                }
                previousIndex = index
                previousOffset = offset
            }
    }

    LaunchedEffect(scrollInteractionTick, isDraggingScrollbar) {
        if (isDraggingScrollbar) {
            scrollbarVisible = true
        } else if (scrollInteractionTick > 0) {
            scrollbarVisible = true
            delay(5_000)
            scrollbarVisible = false
        }
    }

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (scrollbarVisible || isDraggingScrollbar) 1f else 0f,
        animationSpec = tween(durationMillis = 300),
        label = "sharedPdfScrollbarAlpha"
    )
    val activeThemeColor = if (isDarkMode) Color(0xFF1976D2) else Color(0xFF4285F4)
    val idleColor = if (isDarkMode) Color.Gray else Color.DarkGray
    val barColor by animateColorAsState(
        targetValue = if (isDraggingScrollbar) activeThemeColor else idleColor,
        label = "sharedPdfScrollbarColor"
    )
    val scrollbarIdleWidth = 4.dp
    val scrollbarActiveWidth = 8.dp
    val barWidth by animateDpAsState(
        targetValue = if (isDraggingScrollbar) scrollbarActiveWidth else scrollbarIdleWidth,
        label = "sharedPdfScrollbarWidth"
    )
    val scrollbarIdleHeight = 40.dp
    val scrollbarActiveHeight = 60.dp
    val barHeight by animateDpAsState(
        targetValue = if (isDraggingScrollbar) scrollbarActiveHeight else scrollbarIdleHeight,
        label = "sharedPdfScrollbarHeight"
    )
    val safeCurrentPage = if (pageCount > 0) currentPage.coerceIn(0, pageCount - 1) else 0

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp)
            .padding(top = 12.dp, bottom = 12.dp)
            .onGloballyPositioned { coordinates ->
                scrollbarTrackHeight = coordinates.size.height.toFloat()
            }
    ) {
        val thumbHeightPx = with(density) { barHeight.toPx() }
        val effectiveTrackHeight = scrollbarTrackHeight.takeIf { it > 0f } ?: state.viewportHeightPx
        val availableSpace = (effectiveTrackHeight - thumbHeightPx).coerceAtLeast(0f)
        val thumbY = (availableSpace * state.progress).coerceIn(0f, availableSpace)

        Box(
            modifier = Modifier
                .offset { IntOffset(x = 0, y = thumbY.roundToInt()) }
                .align(Alignment.TopEnd)
                .wrapContentSize(align = Alignment.CenterEnd, unbounded = true)
                .alpha(scrollbarAlpha)
                .padding(end = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = isDraggingScrollbar && pageCount > 0,
                    enter = fadeIn() + slideInHorizontally { it / 2 },
                    exit = fadeOut() + slideOutHorizontally { it / 2 }
                ) {
                    Surface(
                        shape = CircleShape,
                        color = activeThemeColor,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(
                            text = "${safeCurrentPage + 1}/$pageCount",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.CenterEnd,
                    modifier = Modifier
                        .height(barHeight)
                        .width(48.dp)
                        .pointerInput(scrollbarTrackHeight, state.contentHeightPx, state.viewportHeightPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                try {
                                    isDraggingScrollbar = true
                                    scrollbarVisible = true
                                    scrollInteractionTick += 1
                                    down.consume()

                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break

                                        val deltaY = change.position.y - change.previousPosition.y
                                        if (deltaY != 0f) {
                                            change.consume()
                                            val trackHeight = scrollbarTrackHeight.takeIf { it > 0f }
                                                ?: state.viewportHeightPx
                                            val trackSpace = (trackHeight - with(density) { scrollbarActiveHeight.toPx() })
                                                .coerceAtLeast(1f)
                                            val scrollDelta = (deltaY / trackSpace) *
                                                (state.contentHeightPx - state.viewportHeightPx)
                                            listState.dispatchRawDelta(scrollDelta)
                                            scrollInteractionTick += 1
                                        }
                                    }
                                } finally {
                                    isDraggingScrollbar = false
                                    scrollInteractionTick += 1
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = barWidth, height = barHeight)
                            .background(barColor, RoundedCornerShape(999.dp))
                    )
                }
            }
        }
    }
}

fun Modifier.sharedAcceleratedLazyWheelScroll(
    listState: LazyListState,
    multiplier: Float = 4f
): Modifier {
    val safeMultiplier = multiplier.coerceIn(1f, 12f)
    return pointerInput(listState, safeMultiplier) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.type != PointerEventType.Scroll) continue
                val scrollDelta = event.changes.fold(0f) { total, change ->
                    val delta = change.scrollDelta
                    total + if (abs(delta.y) >= abs(delta.x)) delta.y else delta.x
                }
                if (abs(scrollDelta) > 0.01f) {
                    val adaptiveMultiplier = when {
                        abs(scrollDelta) < 1f -> 24f
                        abs(scrollDelta) < 8f -> 10f
                        else -> safeMultiplier
                    }
                    listState.dispatchRawDelta(scrollDelta * (adaptiveMultiplier - 1f))
                }
            }
        }
    }
}
