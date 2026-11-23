package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.PdfReaderTool
import com.aryan.reader.shared.ReaderMotionPolicy
import com.aryan.reader.shared.ReaderTool

enum class SharedReaderBarEdge { TOP, BOTTOM }
enum class SharedReaderToolbarPlacement { TOP, BOTTOM }

@Stable
class SharedReaderOverflowMenuState {
    val menuExpanded = mutableStateOf(false)
    val hiddenToolsExpanded = mutableStateOf(false)
    val readingModeExpanded = mutableStateOf(false)
    val ttsSettingsExpanded = mutableStateOf(false)
    val fileActionsExpanded = mutableStateOf(false)

    fun open() {
        collapseNestedSections()
        menuExpanded.value = true
    }

    fun dismiss() {
        collapseNestedSections()
        menuExpanded.value = false
    }

    private fun collapseNestedSections() {
        hiddenToolsExpanded.value = false
        readingModeExpanded.value = false
        ttsSettingsExpanded.value = false
        fileActionsExpanded.value = false
    }
}

@Composable
fun rememberSharedReaderOverflowMenuState(): SharedReaderOverflowMenuState =
    remember { SharedReaderOverflowMenuState() }

/**
 * The reader's tool menu.
 *
 * It used to be a `DropdownMenu` anchored to the overflow button, which meant a 20-row list of
 * tools was clipped to the space under a 56dp bar, scrolled inside a popup no taller than the
 * button, and sat on top of the page with the reader still visible behind it. Tools are a
 * destination, not a context menu: they open as a sheet that can use the whole screen, scrolls
 * naturally, and dismisses with back/swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderOverflowMenu(
    state: SharedReaderOverflowMenuState,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    SharedReaderOverflowSheet(
        visible = state.menuExpanded.value,
        onDismiss = state::dismiss,
        title = title,
        content = content,
    )
}

/**
 * Sheet host for the tool menus, shared by the state-driven [SharedReaderOverflowMenu] and the
 * EPUB bar's boolean-driven menu so a reader cannot end up with a different container per platform.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedReaderOverflowSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        // A full-width sheet on a tablet spans 1200dp of text; the M3 max width keeps tool rows
        // readable next to the page instead of stretching them across the window.
        sheetMaxWidth = 560.dp,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            content = content,
        )
    }
}

fun sharedEpubToolbarTools(
    toolOrder: List<ReaderTool>,
    toolbarTools: Set<ReaderTool>,
    hiddenToolNamesOrIds: Set<String>,
    bottomToolNamesOrIds: Set<String>,
    placement: SharedReaderToolbarPlacement,
): List<ReaderTool> {
    val hidden = hiddenToolNamesOrIds.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id }
    val bottom = bottomToolNamesOrIds.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id }
    return toolOrder.filter { tool ->
        tool in toolbarTools && tool.id !in hidden &&
            ((tool.id in bottom) == (placement == SharedReaderToolbarPlacement.BOTTOM))
    }
}

fun sharedPdfToolbarTools(
    toolOrder: List<PdfReaderTool>,
    hiddenToolNamesOrIds: Set<String>,
    bottomToolNamesOrIds: Set<String>,
    placement: SharedReaderToolbarPlacement,
): List<PdfReaderTool> {
    val hidden = hiddenToolNamesOrIds.mapNotNullTo(mutableSetOf()) { PdfReaderTool.fromId(it)?.id }
    val bottom = bottomToolNamesOrIds.mapNotNullTo(mutableSetOf()) { PdfReaderTool.fromId(it)?.id }
    return toolOrder.filter { tool ->
        tool.supportsToolbarPlacement && tool.id !in hidden &&
            ((tool.id in bottom) == (placement == SharedReaderToolbarPlacement.BOTTOM))
    }
}

fun sharedReaderBarOffset(edge: SharedReaderBarEdge, height: Int): Int =
    if (edge == SharedReaderBarEdge.TOP) -height else height

fun sharedReaderBarEnterTransition(edge: SharedReaderBarEdge): EnterTransition =
    slideInVertically(animationSpec = tween(200)) { height -> sharedReaderBarOffset(edge, height) } +
        fadeIn(animationSpec = tween(200))

fun sharedReaderBarExitTransition(edge: SharedReaderBarEdge): ExitTransition =
    slideOutVertically(animationSpec = tween(200)) { height -> sharedReaderBarOffset(edge, height) } +
        fadeOut(animationSpec = tween(200))

@Composable
fun SharedReaderBarVisibility(
    visible: Boolean,
    edge: SharedReaderBarEdge,
    motionPolicy: ReaderMotionPolicy = ReaderMotionPolicy(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = if (motionPolicy.reduceMotion) EnterTransition.None else sharedReaderBarEnterTransition(edge),
        exit = if (motionPolicy.reduceMotion) ExitTransition.None else sharedReaderBarExitTransition(edge),
        modifier = modifier,
    ) { content() }
}

/**
 * Reader chrome surface.
 *
 * Both readers float their bars over the page, so the bar has to separate from the text without
 * looking like a card that belongs to another app. A tonal container plus a hairline does that at
 * every theme (including the custom reader themes, whose `surface` is the page colour itself);
 * `tonalElevation = 4.dp` used to paint a lighter rectangle that clashed with the reader's paper
 * texture and made the bars read as floating panels.
 */
@Composable
fun SharedReaderToolbarSurface(
    modifier: Modifier = Modifier,
    height: Dp? = null,
    /** Which edge of the bar faces the page, so the hairline is drawn only on that side. */
    edge: SharedReaderBarEdge? = null,
    content: @Composable () -> Unit,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val edgedModifier = if (edge == null) {
        modifier
    } else {
        modifier.drawBehind {
            val thickness = 1f
            drawRect(
                color = lineColor,
                topLeft = Offset(0f, if (edge == SharedReaderBarEdge.TOP) size.height - thickness else 0f),
                size = Size(size.width, thickness),
            )
        }
    }
    val surfaceModifier = if (height == null) {
        edgedModifier.fillMaxWidth()
    } else {
        edgedModifier.fillMaxWidth().height(height)
    }
    Surface(
        modifier = surfaceModifier,
        color = MaterialTheme.colorScheme.surfaceContainer,
        content = content,
    )
}

@Composable
fun SharedEpubTopToolbarRow(
    topInset: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            // The inset lives inside the toolbar's Surface so the bar's colour reaches the
            // screen edge behind the status bar instead of leaving an unpainted strip.
            .padding(top = topInset)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * The EPUB bottom bar's row: Android's benchmark layout.
 *
 * `height` is explicit because the bar's Surface cannot fix its own height - iOS paints the bottom
 * safe-area inset *inside* that Surface (so the tonal background reaches the screen edge), which
 * means the Surface wraps its content and `fillMaxSize()` would collapse the row to the buttons'
 * minimum interactive size (48dp) instead of the 45dp every host's stacking maths reserves.
 * `SharedReaderEpubBottomBarHeight` is that 45dp and the default here has to stay equal to it.
 */
@Composable
fun SharedEpubBottomToolbarRow(
    height: Dp = SharedReaderEpubBottomBarHeight,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .height(height)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
        content = content,
    )
}

/**
 * One tool in a reader bar.
 *
 * Both PDF bars used to hand-roll this: Android through the app module's tooltip wrapper, iOS
 * through a bare `IconButton` with a `contentDescription` and a tint for its active state. The
 * button contract belongs next to the bar it lives in, so "active tool", "busy tool" and the
 * tooltip behave the same whichever host is drawing the bar.
 */
@Composable
fun SharedReaderToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.size(48.dp),
    enabled: Boolean = true,
    description: String? = null,
    active: Boolean = false,
    loading: Boolean = false,
    content: @Composable () -> Unit,
) {
    SharedTooltipIconButton(
        text = label,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = label },
        enabled = enabled,
        description = description,
        active = active,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp))
        } else {
            content()
        }
    }
}

@Composable
fun SharedPdfTopToolbarRow(
    /**
     * Status-bar inset. It lives inside the row so the bar's Surface colour reaches the screen
     * edge behind the status bar, and so a host whose reader already paints that area can pass 0.
     */
    topInset: Dp = 0.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topInset)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SharedPdfBottomToolbarRow(
    bottomPadding: Dp,
    scrollState: ScrollState,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = bottomPadding)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .height(56.dp)
            .padding(horizontal = 8.dp)
            .horizontalScroll(scrollState),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        content = content,
    )
}

@Composable
fun <T> SharedReaderOverflowSectionList(
    sections: List<T>,
    content: @Composable (T) -> Unit,
) {
    sections.forEachIndexed { index, section ->
        if (index > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
        }
        content(section)
    }
}

/**
 * Bottom-chrome stacking shared by the mobile EPUB/PDF readers.
 *
 * Android benchmark: the bottom toolbar sits at the screen bottom, the jump
 * bar (40.dp) sits directly above it, and the page slider sits above the
 * jump bar. Keeping the heights and stacking in one place guarantees the
 * slider can never overlap the jump bar in any chrome/safe-area state.
 */
val SharedReaderEpubTopBarHeight = 55.dp
val SharedReaderEpubBottomBarHeight = 45.dp
val SharedReaderPdfBottomBarHeight = 56.dp
val SharedReaderJumpBarHeight = 40.dp

/**
 * The narration card's height, and the gap it keeps above whatever it floats over.
 *
 * The height is the bar's own `heightIn(min = 64.dp)`; it grows past that only if the
 * title/subtitle column wraps to two lines each at the largest accessibility text size, which
 * reserving for exactly one line would then let overlap again.
 */
val SharedReaderNarrationBarHeight = 64.dp

/** The breathing room between the narration card and the chrome below it. */
val SharedReaderNarrationBarGap = 16.dp

/** Where the card sits when the chrome it floats over is hidden. */
private val SharedReaderNarrationBarHiddenBottomPadding = 32.dp

fun sharedMobileEpubBottomChromePadding(bottomInset: Dp): Dp =
    SharedReaderEpubBottomBarHeight + bottomInset

/**
 * Bottom padding for the media overlay control card, so it floats clear of the whole bottom stack.
 *
 * This is the one bottom card that used to reserve for the toolbar and nothing else — it reused the
 * read-aloud overlay's lift, which is `inset + 45.dp + 16.dp`. That put its lower ~24.dp underneath
 * the jump bar and its lower third underneath a bottom-positioned page info bar, with no `zIndex`
 * anywhere in either reader to hide the damage: later declarations simply paint over earlier ones,
 * and the jump bar is declared after the narration card.
 *
 * The order is the reader's own: toolbar, then jump bar, then page info, then a gap, then the card.
 * That is exactly where [SharedMobileEpubReader] puts the page info bar in the bottom column, so
 * summing them in this order cannot drift from the layout it is meant to clear.
 *
 * @param pageInfoReserve the page info bar's height when it is visible at the bottom, else 0.dp.
 *   A page info bar at the top is nowhere near the card and must not lift it.
 * @param jumpBarVisible whether the jump history bar is showing. It is a sibling overlay, not a
 *   sibling row, so the only way to clear it is to lift the card by its height.
 * @param chromeVisible whether the bottom chrome is showing at all. Hidden, the card drops to a
 *   fixed inset rather than tracking a stack that is not there.
 */
fun sharedMobileEpubMediaOverlayBottomPadding(
    bottomChromePadding: Dp,
    pageInfoReserve: Dp = 0.dp,
    jumpBarVisible: Boolean = false,
    chromeVisible: Boolean = true,
): Dp = if (chromeVisible) {
    bottomChromePadding +
        pageInfoReserve +
        (if (jumpBarVisible) SharedReaderJumpBarHeight else 0.dp) +
        SharedReaderNarrationBarGap
} else {
    SharedReaderNarrationBarHiddenBottomPadding
}

fun sharedMobileEpubJumpBottomPadding(
    bottomChromePadding: Dp,
    pageInfoReserve: Dp = 0.dp,
    ttsLift: Dp = 0.dp,
): Dp = bottomChromePadding + pageInfoReserve + ttsLift

fun sharedMobileEpubSliderBottomPadding(
    jumpBottomPadding: Dp,
    isJumpVisible: Boolean,
): Dp = jumpBottomPadding + if (isJumpVisible) SharedReaderJumpBarHeight else 0.dp

fun sharedMobilePdfBottomChromePadding(
    bottomInset: Dp,
    isSplitPane: Boolean = false,
): Dp = if (isSplitPane) SharedReaderPdfBottomBarHeight else SharedReaderPdfBottomBarHeight + bottomInset

fun sharedMobilePdfSliderBottomPadding(
    bottomChromePadding: Dp,
    isJumpVisible: Boolean,
): Dp = bottomChromePadding + if (isJumpVisible) SharedReaderJumpBarHeight else 0.dp
