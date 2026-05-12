package com.aryan.reader.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

private data class SharedScrollbarState(
    val thumbHeightPx: Float,
    val thumbOffsetPx: Float,
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
                thumbHeightPx = thumbHeight,
                thumbOffsetPx = (viewportHeight - thumbHeight) * progress,
                contentHeightPx = contentHeight,
                viewportHeightPx = viewportHeight
            )
        }
    }

    val state = scrollbarState ?: return
    val density = LocalDensity.current
    val interactionSource = remember { MutableInteractionSource() }
    val draggableState = rememberDraggableState { delta ->
        val trackHeight = state.viewportHeightPx - state.thumbHeightPx
        if (trackHeight > 0f) {
            val scrollDelta = (delta / trackHeight) * (state.contentHeightPx - state.viewportHeightPx)
            listState.dispatchRawDelta(scrollDelta)
        }
    }

    Box(
        modifier = modifier
            .width(18.dp)
            .fillMaxHeight()
            .draggable(
                state = draggableState,
                orientation = Orientation.Vertical,
                interactionSource = interactionSource
            )
            .padding(horizontal = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, state.thumbOffsetPx.roundToInt()) }
                .width(6.dp)
                .height(with(density) { state.thumbHeightPx.toDp() })
                .graphicsLayer { alpha = if (listState.isScrollInProgress) 0.92f else 0.42f }
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    RoundedCornerShape(999.dp)
                )
        )
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
