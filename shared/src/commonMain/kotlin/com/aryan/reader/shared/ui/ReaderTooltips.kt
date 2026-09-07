package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ReaderTooltipIconButton(
    tooltip: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    // Android parity (long-press TooltipBox): touch screens have no hover, so
    // a long-press shows the same bubble transiently instead of clicking.
    var touchTipVisible by remember { mutableStateOf(false) }
    var touchTipDismiss by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val marginPx = with(density) { 8.dp.roundToPx() }
    val offsetPx = with(density) { 8.dp.roundToPx() }

    LaunchedEffect(hovered, tooltip) {
        if (hovered && tooltip.isNotBlank()) {
            delay(450L)
            visible = hovered
        } else {
            visible = false
        }
    }

    Box(
        modifier = Modifier.pointerInput(tooltip) {
            awaitPointerEventScope {
                var isHovered = false
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    when (event.type) {
                        PointerEventType.Enter,
                        PointerEventType.Move -> if (!isHovered) {
                            isHovered = true
                            hovered = true
                        }
                        PointerEventType.Exit,
                        PointerEventType.Press -> if (isHovered) {
                            isHovered = false
                            hovered = false
                        }
                    }
                }
            }
        }
    ) {
        IconButton(
            onClick = {
                touchTipDismiss?.cancel()
                touchTipVisible = false
                onClick()
            },
            modifier = modifier.pointerInput(tooltip) {
                // No onTap: taps fall through to the IconButton untouched; a
                // long-press consumes the gesture (suppressing the click) and
                // shows the tooltip bubble briefly instead.
                detectTapGestures(
                    onLongPress = {
                        if (tooltip.isNotBlank()) {
                            touchTipDismiss?.cancel()
                            touchTipVisible = true
                            touchTipDismiss = scope.launch {
                                delay(2500L)
                                touchTipVisible = false
                            }
                        }
                    }
                )
            },
            enabled = enabled
        ) {
            content.invoke()
        }
        if (visible || touchTipVisible) {
            Popup(
                popupPositionProvider = ReaderTooltipPositionProvider(
                    marginPx = marginPx,
                    offsetPx = offsetPx
                )
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    tonalElevation = 0.dp,
                    shadowElevation = 4.dp
                ) {
                    Text(
                        text = tooltip,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .widthIn(max = 260.dp)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

private data class ReaderTooltipPositionProvider(
    private val marginPx: Int,
    private val offsetPx: Int
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val centeredX = anchorBounds.left + (anchorBounds.width - popupContentSize.width) / 2
        val maxX = (windowSize.width - popupContentSize.width - marginPx).coerceAtLeast(marginPx)
        val x = centeredX.coerceIn(marginPx, maxX)
        val belowY = anchorBounds.bottom + offsetPx
        val aboveY = anchorBounds.top - popupContentSize.height - offsetPx
        val y = if (belowY + popupContentSize.height <= windowSize.height - marginPx) {
            belowY
        } else {
            aboveY.coerceAtLeast(marginPx)
        }
        return IntOffset(x, y)
    }
}
