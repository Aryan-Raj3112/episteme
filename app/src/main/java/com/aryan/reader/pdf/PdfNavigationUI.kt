package com.aryan.reader.pdf

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.aryan.reader.R

private data class ScrollbarCalculations(
    val thumbHeight: Float,
    val thumbOffset: Float,
    val contentHeight: Float,
    val viewportHeight: Float
)

@Composable
fun VerticalScrollbar(
    listState: LazyListState,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()

    val scrollbarState by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val viewportHeight = layoutInfo.viewportSize.height.toFloat()

            if (totalItems == 0 || visibleItemsInfo.isEmpty() || viewportHeight <= 0f) {
                return@derivedStateOf null
            }

            // Estimate total height
            val averageItemHeight = visibleItemsInfo.sumOf { it.size } / visibleItemsInfo.size.toFloat()
            val estimatedContentHeight = (averageItemHeight * totalItems).coerceAtLeast(viewportHeight)
            val viewportRatio = viewportHeight / estimatedContentHeight

            if (viewportRatio >= 1f) return@derivedStateOf null

            val maxThumbHeight = viewportHeight / 2f
            val minThumbHeight = minOf(80f, maxThumbHeight)
            val thumbHeight = (viewportHeight * viewportRatio).coerceIn(minThumbHeight, maxThumbHeight)

            val firstItemIndex = listState.firstVisibleItemIndex
            val firstItemOffset = listState.firstVisibleItemScrollOffset
            val currentScrollPixels = (firstItemIndex * averageItemHeight) + firstItemOffset
            val maxScrollPixels = estimatedContentHeight - viewportHeight
            val scrollProgress = (currentScrollPixels / maxScrollPixels).coerceIn(0f, 1f)
            val trackHeight = viewportHeight - thumbHeight
            val thumbOffset = trackHeight * scrollProgress

            ScrollbarCalculations(
                thumbHeight = thumbHeight,
                thumbOffset = thumbOffset,
                contentHeight = estimatedContentHeight,
                viewportHeight = viewportHeight
            )
        }
    }

    val targetAlpha = if (listState.isScrollInProgress || isDragged) 1f else 0f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "ScrollbarAlpha"
    )

    if (scrollbarState != null) {
        val state = scrollbarState!!
        val draggableState = rememberDraggableState { delta ->
            val trackHeight = state.viewportHeight - state.thumbHeight
            if (trackHeight > 0) {
                val scrollRatio = delta / trackHeight
                val totalScrollableDistance = state.contentHeight - state.viewportHeight
                val scrollDelta = scrollRatio * totalScrollableDistance
                listState.dispatchRawDelta(scrollDelta)
            }
        }

        Box(
            modifier = modifier
                .width(30.dp)
                .fillMaxHeight()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    interactionSource = interactionSource
                )
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer { translationY = state.thumbOffset }
                    .padding(end = 4.dp)
                    .width(6.dp)
                    .height(with(LocalDensity.current) { state.thumbHeight.toDp() })
                    .alpha(alpha)
                    .background(
                        color = if (isDragged) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(100)
                    )
            )
        }
    }
}

@Composable
internal fun PageScrubbingAnimation(currentPage: Int, totalPages: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(
                        alpha = 0.9f
                    ), shape = RoundedCornerShape(16.dp)
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
internal fun ThumbnailWithIndicator(
    thumbnail: Bitmap, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.primary
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier
                .width(45.dp)
                .height(64.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, borderColor)
        ) {
            Image(
                bitmap = thumbnail.asImageBitmap(),
                contentDescription = stringResource(R.string.content_desc_start_page_thumbnail),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(modifier = Modifier
            .offset(y = (-4).dp)
            .size(8.dp)
            .rotate(45f)
            .background(borderColor))
    }
}

@Composable
internal fun BookmarkButton(
    isBookmarked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(48.dp)
            .height(48.dp)
            .clip(RectangleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ), contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(visible = isBookmarked, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                painter = painterResource(id = R.drawable.bookmark),
                contentDescription = stringResource(R.string.content_desc_bookmark),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun ZoomPercentageIndicator(
    percentage: Int,
    onResetZoomClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.8f)
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = "$percentage%",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(Color.White.copy(alpha = 0.5f))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Reset Zoom Button
            Icon(
                painter = painterResource(id = R.drawable.zoom_out),
                contentDescription = stringResource(R.string.content_desc_reset_zoom),
                tint = Color.White,
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onResetZoomClick)
            )
        }
    }
}
