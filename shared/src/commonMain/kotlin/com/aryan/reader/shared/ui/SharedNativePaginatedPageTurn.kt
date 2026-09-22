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
import androidx.compose.ui.graphics.drawscope.DrawScope
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
 * Continuous page offset for a two-page spread treated as one sheet: every slot
 * in the set shares a single offset measured in spread-widths (1 = one full
 * spread), matching the Android HorizontalPager identity where one pager page
 * is the whole spread. Outgoing sets use `setLeadSlots = 0`; incoming sets use
 * `setLeadSlots = 1`.
 */
internal fun sharedPaginatedSpreadTurnPageOffset(
    setLeadSlots: Int,
    direction: Int,
    fraction: Float
): Float = sharedPaginatedTurnPageOffset(
    slotOffsetInSet = 0,
    setLeadSlots = setLeadSlots,
    turnDistanceSlots = 1,
    direction = direction,
    fraction = fraction
)

/**
 * Idle/mid-turn spine crease for two-page spread: only when the user enabled
 * realistic page turns AND spread mode is active. Animation off keeps the flat
 * default gutter.
 */
internal fun shouldDrawSpreadSpineCrease(
    animationEnabled: Boolean,
    isTwoPageSpread: Boolean
): Boolean = animationEnabled && isTwoPageSpread

/** Fold-line geometry for the spread sheet curl, in sheet-local coordinates. */
internal data class SpreadPageFoldGeometry(
    val valid: Boolean,
    val progress: Float,
    val cornerY: Float,
    val dragX: Float,
    val dragY: Float,
    val midX: Float,
    val midY: Float,
    val nx: Float,
    val ny: Float
)

/**
 * How much of the finger's corner bias a spread book leaf keeps. 0 = pure
 * vertical spine hinge, 1 = full diagonal corner peel. A small blend keeps the
 * fold hinged at the spine while the free corner still follows the finger,
 * like a real page pinched at the corner.
 */
internal const val SpreadBookFlipCornerBlend = 0.35f

/**
 * Sweep distance of a spread book leaf as a fraction of the spread width.
 * 1.0 parks the fold exactly at the spine ([width]/2) when progress hits 1,
 * so the right leaf settles onto the left page instead of flying past it.
 * Single-page corner peels keep the longer 2.2 sweep that exits off-screen
 * like a pager slot.
 */
internal const val SpreadBookFlipSweep = 1.0f

internal const val SpreadCornerPeelSweep = 2.2f

/**
 * Same fold math as the single-page curl, with the sheet width equal to the
 * full spread (both pages + gutter) so the crease sweeps across the spine.
 *
 * Book-flip mode ([forceBookFlip] = true) blends the touch toward the vertical
 * center (see [SpreadBookFlipCornerBlend]) so the fold stays hinged at the
 * spine crease while the free corner still curls toward the finger, and parks
 * the fold at the spine at progress 1 ([SpreadBookFlipSweep]) so the right
 * leaf peels off and settles onto the left page. Spread hosts always use
 * book-flip; single-page keeps the touch-driven diagonal corner peel.
 */
internal fun spreadPageCurlFold(
    width: Float,
    height: Float,
    progress: Float,
    touchY: Float?,
    forceBookFlip: Boolean = false
): SpreadPageFoldGeometry {
    val startY = if (forceBookFlip) {
        val touchOrCenter = touchY ?: height / 2f
        height / 2f + (touchOrCenter - height / 2f) * SpreadBookFlipCornerBlend
    } else {
        touchY ?: height
    }
    val rawCenterDist = ((startY - height / 2f) / (height / 2f)).coerceIn(-1f, 1f)
    val flattenFactor = if (progress > 0.75f) {
        ((progress - 0.75f) / 0.25f).coerceIn(0f, 1f)
    } else {
        0f
    }
    val centerDist = rawCenterDist * (1f - flattenFactor)
    val cornerY = if (centerDist >= 0f) height else 0f
    val sweep = if (forceBookFlip) SpreadBookFlipSweep else SpreadCornerPeelSweep
    val dragX = width - width * sweep * progress
    val dragY = cornerY - height * 0.5f * progress * centerDist
    val midX = (width + dragX) / 2f
    val midY = (cornerY + dragY) / 2f
    val dx = width - dragX
    val dy = cornerY - dragY
    val nLen = sqrt(dx * dx + dy * dy)
    return if (nLen > 0f && nLen == nLen) {
        SpreadPageFoldGeometry(
            valid = true,
            progress = progress,
            cornerY = cornerY,
            dragX = dragX,
            dragY = dragY,
            midX = midX,
            midY = midY,
            nx = dx / nLen,
            ny = dy / nLen
        )
    } else {
        SpreadPageFoldGeometry(
            valid = false,
            progress = progress,
            cornerY = cornerY,
            dragX = dragX,
            dragY = dragY,
            midX = midX,
            midY = midY,
            nx = 0f,
            ny = 0f
        )
    }
}

/**
 * Book-like spread flip: fold hinged at the spine crease with a hint of the
 * finger's corner bias ([SpreadBookFlipCornerBlend]), parking at the spine at
 * progress 1 so the right leaf peels off and settles onto the left page.
 */
internal fun spreadBookFlipFold(
    width: Float,
    height: Float,
    progress: Float
): SpreadPageFoldGeometry = spreadPageCurlFold(
    width = width,
    height = height,
    progress = progress,
    touchY = height / 2f,
    forceBookFlip = true
)

/**
 * Whether a spread fold is a near-vertical book hinge (fold line upright).
 * Center-touched book flips have |ny| ~ 0 and |nx| ~ 1; corner-touched flips
 * keep a bounded tilt from [SpreadBookFlipCornerBlend].
 */
internal fun SpreadPageFoldGeometry.isVerticalBookHinge(): Boolean =
    valid && kotlin.math.abs(ny) < 0.05f && kotlin.math.abs(nx) > 0.95f

/**
 * Soft spine shadow in the gutter center of a two-page spread. Width tracks the
 * configured gutter (with a floor) so small PDF-style gaps still read.
 *
 * [turnProgress] deepens the hinge mid-turn (0..1, null = settled): the crease
 * reads as the flip hinge while the sheet peels from the right and settles on
 * the left, then relaxes back to the idle shadow. Peaks at half-turn.
 */
fun DrawScope.drawSharedSpreadSpineCrease(
    width: Float,
    height: Float,
    paperIsDark: Boolean,
    gutterWidthPx: Float,
    turnProgress: Float? = null
) {
    val spineX = width / 2f
    val hingeBoost = turnProgress
        ?.coerceIn(0f, 1f)
        ?.let { kotlin.math.sin(it * kotlin.math.PI).toFloat() }
        ?: 0f
    val halfWidth = maxOf(gutterWidthPx * 0.75f, 12.dp.toPx()) * (1f + hingeBoost * 0.35f)
    val alpha = 0.12f + hingeBoost * 0.10f
    val colors = if (paperIsDark) {
        listOf(Color.Transparent, Color.White.copy(alpha = alpha), Color.Transparent)
    } else {
        listOf(Color.Transparent, Color.Black.copy(alpha = alpha), Color.Transparent)
    }
    drawRect(
        brush = Brush.horizontalGradient(
            colors = colors,
            startX = spineX - halfWidth,
            endX = spineX + halfWidth
        )
    )
}

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
 * Which stacked layer owns the opaque base fill during a realistic turn.
 *
 * Android benchmark parity: the pager slots sit side-by-side, so the curling
 * sheet is transparent outside its frontPath and the set beneath shows through
 * with the drop/inner shadows. The shared reader stacks the two sets in one
 * Box, so the beneath layer must stay opaque while the top layer goes
 * transparent — otherwise the top layer's full-size paper background covers the
 * incoming set and the next page only appears once the turn overlay is removed.
 *
 * Returns (mainBackground, overlayBackground).
 */
internal fun sharedPaginatedTurnLayerBackgrounds(
    turnActive: Boolean,
    overlayFirst: Boolean,
    baseBackground: Color
): Pair<Color, Color> {
    if (!turnActive) return baseBackground to baseBackground
    return if (overlayFirst) {
        // Overlay beneath (opaque base), main on top (transparent to reveal it).
        Color.Transparent to baseBackground
    } else {
        // Main beneath (opaque base), overlay on top (transparent to reveal it).
        baseBackground to Color.Transparent
    }
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
 *
 * When [spineCreaseEnabled] is true (two-page spread + realistic turns), the
 * fold hinges at the spine crease with a hint of corner curl toward the finger:
 * the right leaf peels off and settles onto the left page. The spine crease,
 * fold line, and lift shading draw only mid-turn — settled spreads stay flat
 * like the iOS reader.
 */
@Composable
fun Modifier.realisticPageCurl(
    pageOffsetProvider: () -> Float,
    touchYProvider: () -> Float?,
    paperColor: Color,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f,
    spineCreaseEnabled: Boolean = false,
    spreadGutterPx: Float = 0f
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

            fun drawSpineCrease(turnProgress: Float? = null) {
                if (spineCreaseEnabled) {
                    drawSharedSpreadSpineCrease(
                        width = size.width,
                        height = size.height,
                        paperIsDark = isDarkPaper,
                        gutterWidthPx = spreadGutterPx,
                        turnProgress = turnProgress
                    )
                }
            }

            // Turn-only crease: settled spreads stay flat. The crease, fold
            // line, and lift shading appear only while a turn is in flight.
            if (abs(pageOffset) < 0.001f) {
                drawPaperBackground()
                drawContent()
            } else if (pageOffset < 0f && pageOffset > -1f) {
                val progress = -pageOffset
                val fold = spreadPageCurlFold(
                    width = size.width,
                    height = size.height,
                    progress = progress,
                    touchY = touchYProvider(),
                    // Spread + realistic = book leaf hinged at the spine crease:
                    // pin to vertical center so the sheet peels from the right
                    // and settles on the left. Single-page keeps touch diagonal.
                    forceBookFlip = spineCreaseEnabled
                )
                if (!fold.valid) {
                    drawPaperBackground()
                    drawContent()
                    drawSpineCrease()
                    return@drawWithContent
                }
                val w = size.width
                val h = size.height
                val cornerY = fold.cornerY
                val dragX = fold.dragX
                val dragY = fold.dragY
                val midX = fold.midX
                val midY = fold.midY
                val nx = fold.nx
                val ny = fold.ny

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

                // Lifting-paper sheen on the book leaf beside the fold: catches
                // light as the sheet rises off the spine, peaking half-turn.
                // Corner peels keep their flat front.
                if (spineCreaseEnabled) {
                    val lift = kotlin.math.sin(progress * kotlin.math.PI).toFloat()
                    if (lift > 0.01f) {
                        val sheenWidth = 10.dp.toPx() + 26.dp.toPx() * lift
                        val sheenColor = if (isDarkPaper) {
                            Color.White.copy(alpha = 0.07f * lift)
                        } else {
                            Color.White.copy(alpha = 0.12f * lift)
                        }
                        val sheenBrush = Brush.linearGradient(
                            colors = listOf(Color.Transparent, sheenColor),
                            start = Offset(midX - nx * sheenWidth, midY - ny * sheenWidth),
                            end = Offset(midX, midY)
                        )
                        clipPath(frontPath) {
                            drawRect(sheenBrush)
                        }
                    }
                }

                // Book leaf (spread + realistic): the flipping sheet lifts off
                // the spine, so its drop shadow peaks at half-turn when the
                // fold hinges at the crease. Corner peel (single page) keeps
                // the shrinking shadow.
                val shadowWidth = if (spineCreaseEnabled) {
                    val lift = kotlin.math.sin(progress * kotlin.math.PI).toFloat()
                    (10.dp.toPx() + 30.dp.toPx() * lift).coerceAtLeast(10.dp.toPx())
                } else {
                    (40.dp.toPx() * (1f - progress)).coerceAtLeast(10.dp.toPx())
                }
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

                    val foldAlpha = if (spineCreaseEnabled) 0.16f else 0.1f
                    drawLine(
                        color = if (isDarkPaper) Color.White.copy(alpha = foldAlpha) else Color.Black.copy(alpha = foldAlpha),
                        start = Offset(p1X, p1Y),
                        end = Offset(p2X, p2Y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                // Keep the spine hinge visible mid-turn so the peel reads as
                // rotating around the crease, then relaxing to the idle crease.
                drawSpineCrease(turnProgress = progress)
            } else {
                drawPaperBackground()
                drawContent()
                drawSpineCrease()
            }
        }
}

/**
 * Spread-sheet book flip: fold hinged at the spine crease (with a hint of
 * corner curl) and the spine crease forced on mid-turn. Hosts apply this to
 * the whole spread Row (both pages + gutter). The right leaf peels off and
 * settles onto the left page; settled spreads stay flat.
 */
@Composable
fun Modifier.realisticSpreadPageCurl(
    pageOffsetProvider: () -> Float,
    touchYProvider: () -> Float?,
    paperColor: Color,
    textureBitmap: ImageBitmap? = null,
    textureAlpha: Float = 0f,
    spreadGutterPx: Float = 0f
): Modifier = realisticPageCurl(
    pageOffsetProvider = pageOffsetProvider,
    touchYProvider = touchYProvider,
    paperColor = paperColor,
    textureBitmap = textureBitmap,
    textureAlpha = textureAlpha,
    spineCreaseEnabled = true,
    spreadGutterPx = spreadGutterPx
)

/**
 * Idle-only spine crease for spread mode when no turn is active (animation on,
 * settled content). Drawn above the settled page surface.
 */
fun Modifier.sharedSpreadSpineCrease(
    enabled: Boolean,
    paperIsDark: Boolean,
    gutterWidthPx: Float
): Modifier {
    if (!enabled) return this
    return this.drawWithContent {
        drawContent()
        drawSharedSpreadSpineCrease(
            width = size.width,
            height = size.height,
            paperIsDark = paperIsDark,
            gutterWidthPx = gutterWidthPx
        )
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
