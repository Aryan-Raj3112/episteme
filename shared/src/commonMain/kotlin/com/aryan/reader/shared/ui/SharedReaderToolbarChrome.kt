package com.aryan.reader.shared.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
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

@Composable
fun SharedReaderOverflowMenu(
    state: SharedReaderOverflowMenuState,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = state.menuExpanded.value,
        onDismissRequest = state::dismiss,
        content = content,
    )
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

@Composable
fun SharedReaderToolbarSurface(
    modifier: Modifier = Modifier,
    height: Dp? = null,
    content: @Composable () -> Unit,
) {
    val surfaceModifier = if (height == null) {
        modifier.fillMaxWidth()
    } else {
        modifier.fillMaxWidth().height(height)
    }
    Surface(
        modifier = surfaceModifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        content = content,
    )
}

@Composable
fun SharedEpubTopToolbarRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun SharedEpubBottomToolbarRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceAround,
        content = content,
    )
}

@Composable
fun SharedPdfTopToolbarRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
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
        if (index > 0) HorizontalDivider()
        content(section)
    }
}
