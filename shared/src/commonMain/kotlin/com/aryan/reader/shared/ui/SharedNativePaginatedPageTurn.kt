package com.aryan.reader.shared.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Describes the in-flight realistic page curl for the native paginated reader.
 * Mirrors the Android benchmark, where every pager page reads its continuous
 * `pageIndex - pagerPosition` offset and renders the curl accordingly.
 *
 * [offsetForSlot] returns the continuous page offset for a visible slot
 * (0 = settled on screen, negative = curling away, positive = sliding in beneath).
 */
data class SharedPaginatedPageTurnSpec(
    val offsetForSlot: (slot: Int) -> Float,
    val touchY: Float?
)

/**
 * Continuous page offset for a page at [slotOffsetInSet] inside a page set whose
 * first page sits [setLeadSlots] slots ahead of the turning origin, reproducing the
 * Android pager identity `pageIndex - pagerPosition` while the pager position
 * animates [turnDistanceSlots] pages in [direction]. Outgoing sets use
 * `setLeadSlots = 0`; incoming sets use `setLeadSlots = turnDistanceSlots`.
 */
internal fun sharedPaginatedTurnPageOffset(
    slotOffsetInSet: Int,
    setLeadSlots: Int,
    turnDistanceSlots: Int,
    direction: Int,
    fraction: Float
): Float = slotOffsetInSet + direction * (setLeadSlots - turnDistanceSlots * fraction)

/**
 * Android benchmark rule: single visible-step turns (taps, hardware keys) animate
 * with the realistic curl; multi-page jumps (slider, TOC, links, TTS) settle
 * instantly via scrollToPage. Animation must also be user-enabled.
 */
internal fun sharedPaginatedTurnShouldAnimate(
    animationEnabled: Boolean,
    outgoingFirstPageIndex: Int?,
    incomingFirstPageIndex: Int?,
    visiblePageCount: Int
): Boolean {
    if (!animationEnabled) return false
    val outgoing = outgoingFirstPageIndex ?: return false
    val incoming = incomingFirstPageIndex ?: return false
    if (outgoing == incoming) return false
    return abs(incoming - outgoing) == visiblePageCount.coerceAtLeast(1)
}

/**
 * Verbatim port of the Android `realisticBookPage` page-curl modifier
 * (app/src/main/java/com/aryan/reader/paginatedreader/PaginatedReaderContent.kt).
 * The offset is read lazily every frame so the curl tracks the turn animation
 * without recomposition, exactly like reading the pager state on Android.
 */
@Composable
internal fun Modifier.sharedRealisticBookPage(
    pageOffsetProvider: () -> Float,
    touchYProvider: () -> Float?,
    paperColor: Color,
    isDarkPaper: Boolean,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f
): Modifier {
    return this
        .graphicsLayer {
            val pageOffset = pageOffsetProvider()

            if (pageOffset <= 1f && pageOffset > -1f) {
                translationX = -pageOffset * size.width
            }

            if (pageOffset != 0f) {
                shadowElevation = 10f
                shape = RectangleShape
                clip = false
            }
        }
        .realisticPageCurl(
            pageOffsetProvider = pageOffsetProvider,
            touchYProvider = touchYProvider,
            paperColor = paperColor,
            textureBitmap = textureBitmap,
            textureAlpha = textureAlpha
        )
}

/**
 * Public draw-only page-curl modifier shared by the paginated readers. Unlike
 * [sharedRealisticBookPage] it does not translate or shadow the page, so hosts
 * whose pages are letterboxed inside a larger pager slot (PDF) can cancel the
 * pager translation themselves and fold just the sheet. Flap tinting is derived
 * from [paperColor] darkness.
 */
@Composable
fun Modifier.realisticPageCurl(
    pageOffsetProvider: () -> Float,
    touchYProvider: () -> Float?,
    paperColor: Color,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f
): Modifier {
    val isDarkPaper = sharedReaderPaperIsDark(paperColor)
    val frontPath = remember { Path() }
    val backPath = remember { Path() }
    val reflectedScreenPath = remember { Path() }

    return this.drawWithContent {
            val pageOffset = pageOffsetProvider()
            fun drawPaperBackground() {
                drawRect(color = paperColor)
                if (textureBitmap != null && textureAlpha > 0f) {
                    drawRect(
                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                        blendMode = BlendMode.SrcOver,
                        alpha = textureAlpha
                    )
                }
            }

            if (abs(pageOffset) < 0.001f) {
                drawPaperBackground()
                drawContent()
            } else if (pageOffset < 0f && pageOffset > -1f) {
                val progress = -pageOffset
                val w = size.width
                val h = size.height

                val startY = touchYProvider() ?: h
                val rawCenterDist = ((startY - h / 2f) / (h / 2f)).coerceIn(-1f, 1f)

                val flattenFactor = if (progress > 0.75f) {
                    ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val centerDist = rawCenterDist * (1f - flattenFactor)

                val cornerY = if (centerDist >= 0) h else 0f

                val dragX = w - w * 2.2f * progress
                val dragY = cornerY - h * 0.5f * progress * centerDist

                val midX = (w + dragX) / 2f
                val midY = (cornerY + dragY) / 2f

                val dx = w - dragX
                val dy = cornerY - dragY
                val nLen = sqrt(dx * dx + dy * dy)

                if (nLen > 0f) {
                    val nx = dx / nLen
                    val ny = dy / nLen

                    val huge = w * 3f
                    val vx = -ny

                    val p1X = midX + vx * huge
                    val p1Y = midY + nx * huge
                    val p2X = midX - vx * huge
                    val p2Y = midY - nx * huge

                    frontPath.rewind()
                    frontPath.moveTo(p1X, p1Y)
                    frontPath.lineTo(p2X, p2Y)
                    frontPath.lineTo(p2X - nx * huge, p2Y - ny * huge)
                    frontPath.lineTo(p1X - nx * huge, p1Y - ny * huge)
                    frontPath.close()

                    clipPath(frontPath) {
                        drawPaperBackground()
                        this@drawWithContent.drawContent()
                    }

                    val shadowWidth = (40.dp.toPx() * (1f - progress)).coerceAtLeast(10.dp.toPx())
                    backPath.rewind()
                    backPath.moveTo(p1X, p1Y)
                    backPath.lineTo(p2X, p2Y)
                    backPath.lineTo(p2X + nx * huge, p2Y + ny * huge)
                    backPath.lineTo(p1X + nx * huge, p1Y + ny * huge)
                    backPath.close()

                    val dropShadowBrush = Brush.linearGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent),
                        start = Offset(midX, midY),
                        end = Offset(midX + nx * shadowWidth, midY + ny * shadowWidth)
                    )
                    clipRect(0f, 0f, w, h) {
                        drawPath(backPath, dropShadowBrush)
                    }

                    fun reflect(px: Float, py: Float): Offset {
                        val vX = px - midX
                        val vY = py - midY
                        val dist = vX * nx + vY * ny
                        return Offset(px - 2 * dist * nx, py - 2 * dist * ny)
                    }

                    val rTL = reflect(0f, 0f)
                    val rTR = reflect(w, 0f)
                    val rBR = reflect(w, h)
                    val rBL = reflect(0f, h)

                    reflectedScreenPath.rewind()
                    reflectedScreenPath.moveTo(rTL.x, rTL.y)
                    reflectedScreenPath.lineTo(rTR.x, rTR.y)
                    reflectedScreenPath.lineTo(rBR.x, rBR.y)
                    reflectedScreenPath.lineTo(rBL.x, rBL.y)
                    reflectedScreenPath.close()

                    clipRect(0f, 0f, w, h) {
                        clipPath(frontPath) {
                            drawPath(reflectedScreenPath, color = paperColor)
                            if (textureBitmap != null && textureAlpha > 0f) {
                                clipPath(reflectedScreenPath) {
                                    drawRect(
                                        brush = ShaderBrush(ImageShader(textureBitmap, TileMode.Repeated, TileMode.Repeated)),
                                        blendMode = BlendMode.SrcOver,
                                        alpha = textureAlpha
                                    )
                                }
                            }
                            val flapTint = if (isDarkPaper) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
                            drawPath(reflectedScreenPath, color = flapTint)

                            val innerShadowWidth = shadowWidth * 0.7f
                            val innerShadowBrush = Brush.linearGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.05f), Color.Transparent),
                                start = Offset(midX, midY),
                                end = Offset(midX - nx * innerShadowWidth, midY - ny * innerShadowWidth)
                            )
                            drawPath(reflectedScreenPath, innerShadowBrush)

                            drawPath(
                                path = reflectedScreenPath,
                                color = if (isDarkPaper) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.15f),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }

                        drawLine(
                            color = if (isDarkPaper) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f),
                            start = Offset(p1X, p1Y),
                            end = Offset(p2X, p2Y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                } else {
                    drawPaperBackground()
                    drawContent()
                }
            } else {
                drawPaperBackground()
                drawContent()
            }
        }
}

/**
 * Android derives the flap tint from the app dark theme; the shared reader only
 * knows the rendered paper color, so darkness is read from the paper itself.
 */
internal fun sharedReaderPaperIsDark(paperColor: Color): Boolean {
    return paperColor.luminance() < 0.5f
}

/** Release velocity (pages/second) above which a drag flings to the neighbor page. */
internal const val SharedPaginatedDragFlingPagesPerSecond = 1.5f

/** What a released page drag should do, mirroring the Android pager snap decision. */
internal enum class SharedPaginatedDragRelease {
    CANCEL,
    COMMIT_FORWARD,
    COMMIT_BACKWARD
}

/**
 * Android pager release rule: settle to the neighbor page when the drag passed
 * halfway or the release velocity flings onward; a strong opposite fling cancels.
 */
internal fun sharedPaginatedDragReleaseTarget(
    positionPages: Float,
    velocityPagesPerSecond: Float,
    visiblePageCount: Int,
    canDragForward: Boolean,
    canDragBackward: Boolean
): SharedPaginatedDragRelease {
    val halfway = visiblePageCount.coerceAtLeast(1) / 2f
    val flungForward = velocityPagesPerSecond >= SharedPaginatedDragFlingPagesPerSecond
    val flungBackward = velocityPagesPerSecond <= -SharedPaginatedDragFlingPagesPerSecond
    return when {
        positionPages > 0f && canDragForward && !flungBackward &&
            (positionPages >= halfway || flungForward) -> SharedPaginatedDragRelease.COMMIT_FORWARD
        positionPages < 0f && canDragBackward && !flungForward &&
            (positionPages <= -halfway || flungBackward) -> SharedPaginatedDragRelease.COMMIT_BACKWARD
        else -> SharedPaginatedDragRelease.CANCEL
    }
}

/**
 * Maps a raw horizontal drag (fraction of reader width, + = rightward) to a signed
 * toward-next page position. In LTR the next page sits to the right, so a leftward
 * drag moves forward; RTL flips the axis like the pager's reverseLayout.
 */
internal fun sharedPaginatedDragPositionPages(
    rawDragFraction: Float,
    visiblePageCount: Int,
    rightToLeftPagination: Boolean
): Float {
    val towardNext = if (rightToLeftPagination) rawDragFraction else -rawDragFraction
    return towardNext * visiblePageCount.coerceAtLeast(1)
}

/** Bridge between the reader's horizontal drag gesture and the host page-turn state. */
class SharedPaginatedPageDragController(
    val isEnabled: () -> Boolean,
    val onDragStarted: (touchY: Float) -> Unit,
    val onDrag: (rawDragFraction: Float) -> Unit,
    val onDragReleased: (rawVelocityFractionPerSecond: Float) -> Unit,
    val onDragCancelled: () -> Unit
)

/**
 * Horizontal page-drag gesture for the paginated reader. Runs on the initial pointer
 * pass so paging wins over child text gestures (Android benchmark: the pager owns
 * horizontal drags), claims the gesture only once horizontal slop is crossed, and
 * reports raw fractions of the reader width so the host can apply RTL mapping.
 */
@Composable
internal fun Modifier.readerPaginatedDragPointerInput(
    controller: SharedPaginatedPageDragController
): Modifier {
    val currentController by rememberUpdatedState(controller)
    return this.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val pointerId = down.id
            val startX = down.position.x
            val startY = down.position.y
            val touchSlop = viewConfiguration.touchSlop
            val width = size.width.coerceAtLeast(1)
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            var dragging = false
            var ignored = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null) {
                    if (dragging) currentController.onDragCancelled()
                    break
                }
                if (dragging) {
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    currentController.onDrag((change.position.x - startX) / width)
                    change.consume()
                    if (change.changedToUp() || !change.pressed) {
                        val velocity = velocityTracker.calculateVelocity()
                        currentController.onDragReleased(velocity.x / width)
                        break
                    }
                    continue
                }
                if (ignored) {
                    if (change.changedToUp() || !change.pressed) break
                    continue
                }
                val dx = change.position.x - startX
                val dy = change.position.y - startY
                val horizontalSlopCrossed = abs(dx) >= touchSlop && abs(dx) > abs(dy)
                val verticalSlopCrossed = abs(dy) >= touchSlop && abs(dy) > abs(dx)
                when {
                    horizontalSlopCrossed && currentController.isEnabled() -> {
                        dragging = true
                        currentController.onDragStarted(startY)
                        change.consume()
                    }
                    verticalSlopCrossed || !currentController.isEnabled() -> ignored = true
                }
                if (change.changedToUp() || !change.pressed) break
            }
        }
    }
}
