package com.aryan.reader.shared.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * `DropdownMenu` that dismisses when tapped outside on **every** platform, including iOS.
 *
 * ## Why this exists instead of [androidx.compose.material3.DropdownMenu]
 *
 * Material's `DropdownMenu` renders in a `Popup` and relies entirely on the framework's
 * outside-press detection to fire `onDismissRequest`. On skiko (iOS/desktop) that
 * detection is `UIKitComposeSceneLayerView.hitTest`, which only reports a press outside
 * when the layer's own view is what `hitTest` resolves to. A popup nested inside another
 * popup-based container — a `ModalBottomSheet` or a `Dialog`, which is where most of our
 * menus live — is swallowed instead, so tapping outside does nothing at all: the tap
 * never reaches the app and `onDismissRequest` is never called. The only way out was to
 * pick an item.
 *
 * The popup here is used purely to escape the anchor's bounds (clipping, scroll
 * containers, and any parent that would otherwise crop the menu). Dismissal is then
 * handled by *our own* full-screen tap layer inside the popup, which is ordinary
 * in-composition pointer input and behaves identically on Android and iOS. Android keeps
 * material's own behaviour, because material's outside-press detection is reliable there
 * and we do not want to change how Android feels.
 *
 * Visuals, animation, and placement are the Android benchmark: same `Card` elevation and
 * shape, same 8dp vertical padding, same intrinsic-width menu, same 120ms/75ms scale
 * transition, and [sharedDropdownMenuPlacement] reproduces material's
 * `DropdownMenuPositionProvider` candidate ordering.
 *
 * @param expanded whether the menu is open.
 * @param onDismissRequest invoked when the user taps outside the menu.
 * @param offset offset from the anchor's top-start, matching `DropdownMenu`'s `offset`.
 * @param content the menu items.
 */
@Composable
fun SharedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val windowSize = LocalWindowInfo.current.containerSize
    val scrollState = rememberScrollState()
    val expandedStates = remember { MutableTransitionState(false) }
    expandedStates.targetState = true
    val transition = rememberTransition(expandedStates, "SharedDropdownMenu")

    val scale by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(durationMillis = SharedDropdownMenuInTransitionMillis, easing = LinearOutSlowInEasing)
            } else {
                tween(durationMillis = 1, delayMillis = SharedDropdownMenuOutTransitionMillis - 1)
            }
        },
        label = "scale",
    ) { if (it) 1f else 0.8f }

    val alpha by transition.animateFloat(
        transitionSpec = {
            if (false isTransitioningTo true) {
                tween(durationMillis = 30)
            } else {
                tween(durationMillis = SharedDropdownMenuOutTransitionMillis)
            }
        },
        label = "alpha",
    ) { if (it) 1f else 0f }

    // The anchor is this composable's parent in the layout, which is exactly the box
    // `DropdownMenu` measures itself against. A zero-size child reports its parent's
    // window bounds without affecting layout.
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }

    /**
     * A `Popup` is its own window, and that window does not start at the host window's origin:
     * Android places it at `getWindowVisibleDisplayFrame`, so its content begins below the status
     * bar and ends above the navigation bar. [anchorBounds] is measured in host-window
     * coordinates while the card is painted in popup-content coordinates, so placement has to
     * move into the popup's space first or the menu lands one inset below its button — measured at
     * 74px on a 1080x2400 Android device, which read as a gap between the toolbar and the menu.
     *
     * The inset is read *here*, outside the `Popup`: inside it every inset reports 0 because the
     * popup window has already consumed them, and no in-popup API exposes the window's position
     * (`boundsInWindow()` and `boundsInRoot()` are both popup-local). [WindowInsets.safeDrawing]
     * mirrors the visible display frame that positions the popup, so it is the matching value.
     */
    val safeInsets = WindowInsets.safeDrawing
    val popupSpaceInsets = SharedDropdownMenuInsets(
        left = safeInsets.getLeft(density, layoutDirection),
        top = safeInsets.getTop(density),
        right = safeInsets.getRight(density, layoutDirection),
        bottom = safeInsets.getBottom(density),
    )
    // Everything below works in popup-content coordinates, so the card's offset needs no
    // correction and the fit checks are measured against the area the menu can actually use.
    val popupSpace = sharedDropdownMenuPopupSpace(anchorBounds, windowSize, popupSpaceInsets)
    val placement = sharedDropdownMenuPlacement(
        anchorBounds = popupSpace.anchor,
        menuSize = menuSize,
        windowSize = popupSpace.drawableSize,
        contentOffset = offset,
        density = density,
        layoutDirection = layoutDirection,
    )
    val maxMenuHeight = sharedDropdownMenuMaxHeight(popupSpace.anchor, popupSpace.drawableSize, density)

    // Zero-size probe: reports the anchor (this node's parent in the layout) in window
    // coordinates, which is the same box `DropdownMenu` measures itself against.
    Box(
        Modifier
            .size(0.dp)
            .onGloballyPositioned { coordinates ->
                anchorBounds = (coordinates.parentLayoutCoordinates?.boundsInWindow()
                    ?: coordinates.boundsInWindow()).roundToIntRect()
            },
    )

    if (!expanded) return

    Popup(
        // Our own tap layer handles dismissal, so the framework's (broken on iOS)
        // outside-press path is disabled to keep a single, predictable code path.
        // `focusable` must stay on: a non-focusable popup would not receive key events
        // (hardware back / escape) and would let those reach the surface behind.
        properties = SharedDropdownMenuPopupProperties,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Scrim is a *sibling behind* the card, never its parent: a `clickable`
            // ancestor merges its descendants' semantics, which collapsed the entire
            // menu into one screen-sized accessibility node.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismissRequest,
                    ),
            )
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { placement.offset }
                    .heightIn(max = with(density) { maxMenuHeight.toDp() })
                    .width(IntrinsicSize.Max)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = placement.transformOrigin
                    }
                    .alpha(alpha)
                    .onGloballyPositioned { menuSize = it.size },
                shape = SharedDropdownMenuShape,
                colors = CardDefaults.cardColors(),
                elevation = CardDefaults.cardElevation(defaultElevation = SharedDropdownMenuElevation),
            ) {
                Column(
                    modifier = Modifier
                        .padding(vertical = SharedDropdownMenuVerticalPadding)
                        .verticalScroll(scrollState),
                    content = content,
                )
            }
        }
    }
}

private val SharedDropdownMenuShape = RoundedCornerShape(12.dp)
private val SharedDropdownMenuElevation = 8.dp
private val SharedDropdownMenuVerticalPadding = 8.dp
private const val SharedDropdownMenuInTransitionMillis = 120
private const val SharedDropdownMenuOutTransitionMillis = 75

internal val SharedDropdownMenuPopupProperties = PopupProperties(
    focusable = true,
    dismissOnBackPress = true,
    dismissOnClickOutside = false,
    clippingEnabled = true,
)
