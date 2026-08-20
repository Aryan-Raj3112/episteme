package com.aryan.reader.shared.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.DefaultPdfSplitDividerFraction
import com.aryan.reader.shared.FileType
import com.aryan.reader.shared.MaximumPdfSplitDividerFraction
import com.aryan.reader.shared.MinimumPdfSplitDividerFraction
import com.aryan.reader.shared.PdfSplitDividerSnapState
import com.aryan.reader.shared.PdfSplitOrientation
import com.aryan.reader.shared.PdfSplitPane
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.PdfSplitPresentation
import com.aryan.reader.shared.PdfSplitWorkspaceAction
import com.aryan.reader.shared.PdfSplitWorkspaceJson
import com.aryan.reader.shared.PdfSplitWorkspaceState
import com.aryan.reader.shared.pdfSplitDividerFractionAtAbsolutePosition
import com.aryan.reader.shared.samePdfDocument
import com.aryan.reader.shared.snapPdfSplitDividerFraction
import com.aryan.reader.shared.resolveLayout
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults

private const val IosPdfSplitWorkspaceDefaultsKey = "reader_ios_pdf_split_workspace_v1"
private val IosPdfSplitMinPaneWidth = 280.dp
private val IosPdfSplitMinPaneHeight = 320.dp
private val IosPdfSplitDividerTouchTarget = 24.dp
private val IosPdfSplitDividerVisualThickness = 2.dp
private const val IosPdfSplitDoubleTapTimeoutMillis = 300L

internal data class IosPdfSplitPickerTarget(
    val pane: PdfSplitPane,
    val expectedRevision: Long,
    val expectedSessionId: Long?,
)

internal fun loadIosPdfSplitWorkspace(): PdfSplitWorkspaceState {
    return PdfSplitWorkspaceJson.decodeOrEmpty(
        NSUserDefaults.standardUserDefaults.stringForKey(IosPdfSplitWorkspaceDefaultsKey),
    )
}

internal fun persistIosPdfSplitWorkspace(workspace: PdfSplitWorkspaceState) {
    NSUserDefaults.standardUserDefaults.setObject(
        PdfSplitWorkspaceJson.encode(workspace),
        forKey = IosPdfSplitWorkspaceDefaultsKey,
    )
}

internal fun iosPdfSplitPaneState(book: BookItem): PdfSplitPaneState? {
    val path = book.path?.trim().orEmpty()
    return if (book.type == FileType.PDF && book.id.isNotBlank() && path.isNotBlank()) {
        PdfSplitPaneState(book.id, path)
    } else {
        null
    }
}

internal fun resolveIosPdfSplitBook(
    document: PdfSplitPaneState,
    books: Collection<BookItem>,
): BookItem? {
    return books.firstOrNull { book ->
        val candidate = iosPdfSplitPaneState(book) ?: return@firstOrNull false
        candidate.samePdfDocument(document) && iosPdfSplitBookIsAvailable(book)
    }
}

@OptIn(ExperimentalForeignApi::class)
internal fun iosPdfSplitBookIsAvailable(book: BookItem): Boolean {
    val path = book.path?.trim().orEmpty()
    return path.startsWith("opds-pse://") ||
        (path.isNotBlank() && NSFileManager.defaultManager.fileExistsAtPath(path))
}

internal fun restoreIosPdfSplitWorkspace(
    persisted: PdfSplitWorkspaceState,
    books: Collection<BookItem>,
): PdfSplitWorkspaceState {
    val primary = persisted.primary?.let { resolveIosPdfSplitBook(it, books) }
    val secondary = persisted.secondary?.let { resolveIosPdfSplitBook(it, books) }
    val primaryState = primary?.let(::iosPdfSplitPaneState)
    val secondaryState = secondary?.let(::iosPdfSplitPaneState)
    if (primaryState == null || secondaryState == null || primaryState.samePdfDocument(secondaryState)) {
        return PdfSplitWorkspaceState()
    }
    return persisted.copy(
        primary = primaryState,
        secondary = secondaryState,
    ).withFreshSessions()
}

@Composable
internal fun IosPdfSplitWorkspaceScreen(
    workspace: PdfSplitWorkspaceState,
    titleForDocument: (PdfSplitPaneState) -> String,
    onFocusPane: (PdfSplitPane, Long) -> Unit,
    onClosePane: (PdfSplitPane, Long) -> Unit,
    onCloseWorkspace: () -> Unit,
    onSwapPanes: () -> Unit,
    onOrientationChange: (PdfSplitOrientation) -> Unit,
    onDividerChange: (Float, PdfSplitOrientation, Long) -> Unit,
    onAddDocument: (PdfSplitPane) -> Unit,
    renderPane: @Composable (PdfSplitPaneState, Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        IosPdfSplitToolbar(
            workspace = workspace,
            onSwapPanes = onSwapPanes,
            onOrientationChange = onOrientationChange,
            onCloseWorkspace = onCloseWorkspace,
            onAddDocument = onAddDocument,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val primary = workspace.primary
            val secondary = workspace.secondary
            if (primary == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No PDF is open")
                }
            } else if (secondary == null) {
                IosPdfSplitDocumentPane(
                    pane = PdfSplitPane.PRIMARY,
                    document = primary,
                    title = titleForDocument(primary),
                    isFocused = workspace.focusedPane == PdfSplitPane.PRIMARY,
                    onFocus = onFocusPane,
                    onClose = onClosePane,
                    renderPane = renderPane,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                IosPdfSplitPaneLayout(
                    workspace = workspace,
                    onDividerChange = onDividerChange,
                    onFocusPane = { pane ->
                        workspace.pane(pane)?.let { document -> onFocusPane(pane, document.sessionId) }
                    },
                    first = {
                        IosPdfSplitDocumentPane(
                            pane = PdfSplitPane.PRIMARY,
                            document = primary,
                            title = titleForDocument(primary),
                            isFocused = workspace.focusedPane == PdfSplitPane.PRIMARY,
                            onFocus = onFocusPane,
                            onClose = onClosePane,
                            renderPane = renderPane,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    second = {
                        IosPdfSplitDocumentPane(
                            pane = PdfSplitPane.SECONDARY,
                            document = secondary,
                            title = titleForDocument(secondary),
                            isFocused = workspace.focusedPane == PdfSplitPane.SECONDARY,
                            onFocus = onFocusPane,
                            onClose = onClosePane,
                            renderPane = renderPane,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun IosPdfSplitToolbar(
    workspace: PdfSplitWorkspaceState,
    onSwapPanes: () -> Unit,
    onOrientationChange: (PdfSplitOrientation) -> Unit,
    onCloseWorkspace: () -> Unit,
    onAddDocument: (PdfSplitPane) -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Split reader",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = {
                onOrientationChange(
                    if (workspace.orientation == PdfSplitOrientation.VERTICAL) {
                        PdfSplitOrientation.HORIZONTAL
                    } else {
                        PdfSplitOrientation.VERTICAL
                    },
                )
            }) {
                Text(
                    if (workspace.orientation == PdfSplitOrientation.VERTICAL) {
                        "Side by side"
                    } else {
                        "Stacked"
                    },
                    maxLines = 1,
                )
            }
            TextButton(onClick = {
                onAddDocument(if (workspace.isSplit) workspace.focusedPane else PdfSplitPane.SECONDARY)
            }) {
                Text("Add")
            }
            if (workspace.isSplit) {
                TextButton(onClick = onSwapPanes) { Text("Swap") }
            }
            TextButton(onClick = onCloseWorkspace) { Text("Close") }
        }
    }
}

@Composable
private fun IosPdfSplitDocumentPane(
    pane: PdfSplitPane,
    document: PdfSplitPaneState,
    title: String,
    isFocused: Boolean,
    onFocus: (PdfSplitPane, Long) -> Unit,
    onClose: (PdfSplitPane, Long) -> Unit,
    renderPane: @Composable (PdfSplitPaneState, Boolean) -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .pointerInput(pane, document.sessionId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onFocus(pane, document.sessionId)
                    waitForUpOrCancellation()
                }
            },
    ) {
        Surface(tonalElevation = if (isFocused) 3.dp else 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onClose(pane, document.sessionId) }) {
                    Text("Close")
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(pane, document.canonicalIdentity, document.sessionId) {
                renderPane(document, isFocused)
            }
        }
    }
}

@Composable
private fun IosPdfSplitPaneLayout(
    workspace: PdfSplitWorkspaceState,
    onDividerChange: (Float, PdfSplitOrientation, Long) -> Unit,
    onFocusPane: (PdfSplitPane) -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val availableWidth = constraints.maxWidth.coerceAtLeast(0)
        val availableHeight = constraints.maxHeight.coerceAtLeast(0)
        val dividerThicknessPx = with(density) { IosPdfSplitDividerTouchTarget.roundToPx() }
        val minPaneWidthPx = with(density) { IosPdfSplitMinPaneWidth.roundToPx() }
        val minPaneHeightPx = with(density) { IosPdfSplitMinPaneHeight.roundToPx() }
        val plan = workspace.resolveLayout(
            availableWidthPx = availableWidth,
            availableHeightPx = availableHeight,
            minPaneWidthPx = minPaneWidthPx,
            minPaneHeightPx = minPaneHeightPx,
            dividerThicknessPx = dividerThicknessPx,
        )
        var dragFraction by remember(workspace.revision, plan.orientation) { mutableStateOf<Float?>(null) }
        var wasSnappedToCenter by remember(workspace.revision, plan.orientation) {
            mutableStateOf(false)
        }
        val displayedFraction = dragFraction ?: plan.dividerFraction
        val frameWorkspace = workspace.copy(
            orientation = plan.orientation,
            dividerFraction = displayedFraction,
            verticalDividerFraction = if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                displayedFraction
            } else {
                workspace.verticalDividerFraction
            },
            horizontalDividerFraction = if (plan.orientation == PdfSplitOrientation.HORIZONTAL) {
                displayedFraction
            } else {
                workspace.horizontalDividerFraction
            },
        )
        val framePlan = frameWorkspace.resolveLayout(
            availableWidthPx = availableWidth,
            availableHeightPx = availableHeight,
            minPaneWidthPx = minPaneWidthPx,
            minPaneHeightPx = minPaneHeightPx,
            dividerThicknessPx = dividerThicknessPx,
        )
        val dividerColor = MaterialTheme.colorScheme.outlineVariant

        if (plan.presentation == PdfSplitPresentation.SINGLE) {
            Box(Modifier.fillMaxSize()) {
                if (workspace.focusedPane == PdfSplitPane.PRIMARY) first() else second()
                IosPdfSplitPaneSwitcher(
                    focusedPane = workspace.focusedPane,
                    onFocusPane = onFocusPane,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            return@BoxWithConstraints
        }

        val dividerAbsoluteStartPx = if (plan.orientation == PdfSplitOrientation.VERTICAL && isRtl) {
            availableWidth - framePlan.firstPaneSizePx - dividerThicknessPx
        } else {
            framePlan.firstPaneSizePx
        }
        val currentDividerAbsoluteStartPx = rememberUpdatedState(dividerAbsoluteStartPx)
        val axisSizePx = if (plan.orientation == PdfSplitOrientation.VERTICAL) {
            availableWidth
        } else {
            availableHeight
        }
        val dividerModifier = Modifier
            .drawBehind {
                val strokeWidth = IosPdfSplitDividerVisualThickness.toPx()
                if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                    drawLine(
                        color = dividerColor,
                        start = Offset(size.width / 2f, 0f),
                        end = Offset(size.width / 2f, size.height),
                        strokeWidth = strokeWidth,
                    )
                } else {
                    drawLine(
                        color = dividerColor,
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = strokeWidth,
                    )
                }
            }
            .semantics {
                contentDescription = "Split divider"
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayedFraction,
                    range = MinimumPdfSplitDividerFraction..MaximumPdfSplitDividerFraction,
                    steps = 0,
                )
                setProgress { value ->
                    dragFraction = null
                    wasSnappedToCenter = false
                    onDividerChange(
                        value.coerceIn(MinimumPdfSplitDividerFraction, MaximumPdfSplitDividerFraction),
                        plan.orientation,
                        workspace.revision,
                    )
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction("Decrease split divider") {
                        onDividerChange(
                            (displayedFraction - 0.05f).coerceIn(
                                MinimumPdfSplitDividerFraction,
                                MaximumPdfSplitDividerFraction,
                            ),
                            plan.orientation,
                            workspace.revision,
                        )
                        true
                    },
                    CustomAccessibilityAction("Increase split divider") {
                        onDividerChange(
                            (displayedFraction + 0.05f).coerceIn(
                                MinimumPdfSplitDividerFraction,
                                MaximumPdfSplitDividerFraction,
                            ),
                            plan.orientation,
                            workspace.revision,
                        )
                        true
                    },
                )
            }
            .pointerInput(
                workspace.revision,
                plan.orientation,
                axisSizePx,
                dividerThicknessPx,
                isRtl,
            ) {
                var lastTapTimeMillis = Long.MIN_VALUE
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startPosition = down.position
                    var isDragging = false
                    var didFinish = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                            ?: return@awaitEachGesture
                        val movedDistance = (change.position - startPosition).getDistance()
                        if (!isDragging && movedDistance > viewConfiguration.touchSlop) {
                            isDragging = true
                            change.consume()
                        }
                        if (isDragging) {
                            change.consume()
                            val absolutePointer = if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                                currentDividerAbsoluteStartPx.value + change.position.x
                            } else {
                                currentDividerAbsoluteStartPx.value + change.position.y
                            }
                            val rawFraction = pdfSplitDividerFractionAtAbsolutePosition(
                                pointerPositionPx = absolutePointer,
                                axisSizePx = axisSizePx,
                                dividerThicknessPx = dividerThicknessPx,
                                isRtl = plan.orientation == PdfSplitOrientation.VERTICAL && isRtl,
                            )
                            val snapped: PdfSplitDividerSnapState = snapPdfSplitDividerFraction(
                                rawFraction = rawFraction,
                                wasSnappedToCenter = wasSnappedToCenter,
                            )
                            dragFraction = snapped.fraction
                            wasSnappedToCenter = snapped.isSnappedToCenter
                        }
                        if (change.changedToUp()) {
                            if (isDragging) {
                                val committed = dragFraction ?: displayedFraction
                                dragFraction = null
                                wasSnappedToCenter = false
                                onDividerChange(committed, plan.orientation, workspace.revision)
                            } else {
                                val isDoubleTap = lastTapTimeMillis != Long.MIN_VALUE &&
                                    down.uptimeMillis - lastTapTimeMillis in 1..IosPdfSplitDoubleTapTimeoutMillis
                                if (isDoubleTap) {
                                    dragFraction = null
                                    wasSnappedToCenter = false
                                    onDividerChange(
                                        DefaultPdfSplitDividerFraction,
                                        plan.orientation,
                                        workspace.revision,
                                    )
                                    lastTapTimeMillis = Long.MIN_VALUE
                                } else {
                                    lastTapTimeMillis = down.uptimeMillis
                                }
                            }
                            didFinish = true
                            break
                        }
                        if (!change.pressed) break
                    }
                    if (!didFinish && isDragging) {
                        dragFraction = null
                        wasSnappedToCenter = false
                    }
                }
            }

        if (plan.orientation == PdfSplitOrientation.VERTICAL) {
            val firstWidth = with(density) { framePlan.firstPaneSizePx.toDp() }
            val secondWidth = with(density) { framePlan.secondPaneSizePx.toDp() }
            if (isRtl) {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(secondWidth).fillMaxHeight()) { second() }
                    Box(dividerModifier.width(IosPdfSplitDividerTouchTarget).fillMaxHeight())
                    Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
                }
            } else {
                Row(Modifier.fillMaxSize()) {
                    Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
                    Box(dividerModifier.width(IosPdfSplitDividerTouchTarget).fillMaxHeight())
                    Box(Modifier.width(secondWidth).fillMaxHeight()) { second() }
                }
            }
        } else {
            val firstHeight = with(density) { framePlan.firstPaneSizePx.toDp() }
            val secondHeight = with(density) { framePlan.secondPaneSizePx.toDp() }
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.height(firstHeight).fillMaxWidth()) { first() }
                Box(dividerModifier.height(IosPdfSplitDividerTouchTarget).fillMaxWidth())
                Box(Modifier.height(secondHeight).fillMaxWidth()) { second() }
            }
        }
    }
}

@Composable
private fun IosPdfSplitPaneSwitcher(
    focusedPane: PdfSplitPane,
    onFocusPane: (PdfSplitPane) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.padding(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Compact view")
            FilterChip(
                selected = focusedPane == PdfSplitPane.PRIMARY,
                onClick = { onFocusPane(PdfSplitPane.PRIMARY) },
                label = { Text("Primary") },
            )
            FilterChip(
                selected = focusedPane == PdfSplitPane.SECONDARY,
                onClick = { onFocusPane(PdfSplitPane.SECONDARY) },
                label = { Text("Secondary") },
            )
        }
    }
}

@Composable
internal fun IosPdfSplitPickerDialog(
    books: List<BookItem>,
    title: String,
    onDismiss: () -> Unit,
    onBookSelected: (BookItem) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (books.isEmpty()) {
                Text("No other available PDFs")
            } else {
                LazyColumn {
                    items(books, key = { it.id }) { book ->
                        TextButton(
                            onClick = { onBookSelected(book) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = book.displayName,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
