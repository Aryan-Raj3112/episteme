package com.aryan.reader.shared.ios

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.aryan.reader.shared.PdfSplitDividerDragState
import com.aryan.reader.shared.PdfSplitOrientation
import com.aryan.reader.shared.PdfSplitPane
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.PdfSplitPresentation
import com.aryan.reader.shared.PdfSplitWorkspaceAction
import com.aryan.reader.shared.PdfSplitWorkspaceJson
import com.aryan.reader.shared.PdfSplitWorkspaceState
import com.aryan.reader.shared.pdfSplitDividerFractionAtAbsolutePosition
import com.aryan.reader.shared.recoverMissingPanes
import com.aryan.reader.shared.samePdfDocument
import com.aryan.reader.shared.resolveLayout
import com.aryan.reader.shared.ui.readerString
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults

private const val IosPdfSplitWorkspaceDefaultsKey = "reader_ios_pdf_split_workspace_v1"
private val IosPdfSplitMinPaneWidth = 280.dp
private val IosPdfSplitMinPaneHeight = 320.dp
private val IosPdfSplitDividerTouchTarget = 20.dp
private val IosPdfSplitDividerVisualThickness = 3.dp
private val IosPdfSplitDividerHandleWidth = 4.dp
private val IosPdfSplitDividerHandleHeight = 34.dp
private val IosPdfSplitPaneHeaderHeight = 40.dp
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
    if (workspace.isOpen) {
        NSUserDefaults.standardUserDefaults.setObject(
            PdfSplitWorkspaceJson.encode(workspace),
            forKey = IosPdfSplitWorkspaceDefaultsKey,
        )
    } else {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(IosPdfSplitWorkspaceDefaultsKey)
    }
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
    if (!book.isAvailable) return false
    val path = book.path?.trim().orEmpty()
    return path.startsWith("opds-pse://") ||
        (path.isNotBlank() && NSFileManager.defaultManager.fileExistsAtPath(path))
}

internal fun restoreIosPdfSplitWorkspace(
    persisted: PdfSplitWorkspaceState,
    books: Collection<BookItem>,
): PdfSplitWorkspaceState {
    return restoreIosPdfSplitWorkspaceWithRecovery(persisted, books).workspace
}

internal data class IosPdfSplitWorkspaceRecovery(
    val workspace: PdfSplitWorkspaceState,
    val missingPanes: Set<PdfSplitPane>,
) {
    val hasMissingPanes: Boolean
        get() = missingPanes.isNotEmpty()

    val survivingDocument: PdfSplitPaneState?
        get() = workspace.primary.takeIf { hasMissingPanes }
}

internal fun recoverIosPdfSplitWorkspace(
    workspace: PdfSplitWorkspaceState,
    books: Collection<BookItem>,
): IosPdfSplitWorkspaceRecovery {
    val clean = workspace.sanitized()
    val primaryBook = clean.primary?.let { resolveIosPdfSplitBook(it, books) }
    val secondaryBook = clean.secondary?.let { resolveIosPdfSplitBook(it, books) }
    val recovery = clean.recoverMissingPanes(
        primaryAvailable = primaryBook != null,
        secondaryAvailable = secondaryBook != null,
    )
    val repaired = recovery.workspace.copy(
        primary = recovery.workspace.primary?.let { document ->
            resolveIosPdfSplitBook(document, books)?.let(::iosPdfSplitPaneState) ?: document
        },
        secondary = recovery.workspace.secondary?.let { document ->
            resolveIosPdfSplitBook(document, books)?.let(::iosPdfSplitPaneState) ?: document
        },
    ).sanitized()
    return IosPdfSplitWorkspaceRecovery(
        workspace = repaired,
        missingPanes = recovery.missingPanes,
    )
}

internal fun restoreIosPdfSplitWorkspaceWithRecovery(
    persisted: PdfSplitWorkspaceState,
    books: Collection<BookItem>,
): IosPdfSplitWorkspaceRecovery {
    val recovered = recoverIosPdfSplitWorkspace(persisted, books)
    return recovered.copy(workspace = recovered.workspace.withFreshSessions())
}

/**
 * Hosts two reader panes with a workspace-local toolbar.
 *
 * The workspace owns the space between the top of the screen and the panes:
 * it pads for the status bar so pane chrome never slides beneath it, and it
 * keeps the divider visual separate from the stable drag target so preview
 * moves cannot steal reader gestures.
 */
@Composable
internal fun IosPdfSplitWorkspaceScreen(
    workspace: PdfSplitWorkspaceState,
    titleForDocument: (PdfSplitPaneState) -> String,
    onFocusPane: (PdfSplitPane, Long) -> Unit,
    onClosePane: (PdfSplitPane, Long) -> Unit,
    onCloseWorkspace: () -> Unit,
    onSwapPanes: () -> Unit,
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
            onCloseWorkspace = onCloseWorkspace,
            onAddDocument = onAddDocument,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val primary = workspace.primary
            val secondary = workspace.secondary
            if (primary == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(readerString("pdf_split_reader_no_document", "No PDF is open"))
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

/**
 * Compact workspace toolbar. It is a single 48dp row padded below the status
 * bar; the panes below it already carry their own reader chrome, so this bar
 * must not add the tall padding the full-screen reader uses.
 */
@Composable
private fun IosPdfSplitToolbar(
    workspace: PdfSplitWorkspaceState,
    onSwapPanes: () -> Unit,
    onCloseWorkspace: () -> Unit,
    onAddDocument: (PdfSplitPane) -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .height(48.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = readerString("pdf_split_reader_title", "Split Reader"),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = {
                onAddDocument(if (workspace.isSplit) workspace.focusedPane else PdfSplitPane.SECONDARY)
            }) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = readerString("pdf_split_reader_add_document", "Add PDF to split reader"),
                )
            }
            if (workspace.isSplit) {
                IconButton(onClick = onSwapPanes) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = readerString("pdf_split_reader_swap", "Swap documents"),
                    )
                }
            }
            IconButton(onClick = onCloseWorkspace) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = readerString("pdf_split_reader_close", "Close split reader"),
                )
            }
        }
    }
}

/**
 * One reader pane with its slim, one-line header.
 *
 * The header is compact (40dp) because the workspace toolbar above already
 * identifies the surface; the pane header only identifies the document and
 * offers focus plus close actions. Focus is highlighted with a subtle
 * surface tint instead of a full border so both panes read as one document
 * surface separated by a divider.
 */
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
            .background(
                if (isFocused) {
                    MaterialTheme.colorScheme.surface
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            )
            .pointerInput(pane, document.sessionId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onFocus(pane, document.sessionId)
                    // Wait for the gesture to end without consuming it so the
                    // reader below keeps full ownership of scroll and taps.
                    waitForUpOrCancellation()
                }
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IosPdfSplitPaneHeaderHeight)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .background(
                        color = if (isFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Transparent
                        },
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { onClose(pane, document.sessionId) },
                modifier = Modifier.height(IosPdfSplitPaneHeaderHeight),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = readerString("pdf_split_reader_close_pane", "Close document pane"),
                    modifier = Modifier.height(18.dp),
                )
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            key(pane, document.canonicalIdentity, document.sessionId) {
                renderPane(document, isFocused)
            }
        }
    }
}

/**
 * Two-pane layout with a draggable divider.
 *
 * Layout follows the viewport: portrait stacks the panes, landscape places
 * them side by side. The divider's visual handle follows the in-flight drag
 * preview while the pointer/semantics target stays anchored to the committed
 * position until release; this keeps reader gestures untouched while dragging
 * and avoids the flicker of remeasuring panes mid-drag.
 */
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
        var dragState by remember(workspace.revision, plan.orientation) {
            mutableStateOf(PdfSplitDividerDragState(plan.dividerFraction))
        }
        val displayedFraction = dragState.displayedFraction
        val isDragging = dragState.previewFraction != null
        val frameWorkspace = workspace.copy(
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
        val handleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
        val dividerDescription = readerString("pdf_split_reader_divider_desc", "PDF split divider")
        val dividerDecreaseDescription = readerString(
            "pdf_split_reader_divider_decrease",
            "Decrease divider position",
        )
        val dividerIncreaseDescription = readerString(
            "pdf_split_reader_divider_increase",
            "Increase divider position",
        )

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
            availableWidth - plan.firstPaneSizePx - dividerThicknessPx
        } else {
            plan.firstPaneSizePx
        }
        val currentDividerAbsoluteStartPx = rememberUpdatedState(dividerAbsoluteStartPx)
        val axisSizePx = if (plan.orientation == PdfSplitOrientation.VERTICAL) {
            availableWidth
        } else {
            availableHeight
        }
        // The visual handle tracks the drag preview; the interaction target
        // stays anchored to the committed plan until the pointer releases.
        val visualDividerOffset = with(density) {
            if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                (if (isRtl) framePlan.secondPaneSizePx else framePlan.firstPaneSizePx).toDp()
            } else {
                framePlan.firstPaneSizePx.toDp()
            }
        }
        val interactionDividerOffset = with(density) {
            if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                (if (isRtl) plan.secondPaneSizePx else plan.firstPaneSizePx).toDp()
            } else {
                plan.firstPaneSizePx.toDp()
            }
        }
        val dividerCrossAxisSize = with(density) {
            if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                availableHeight.toDp()
            } else {
                availableWidth.toDp()
            }
        }
        val handleAlpha by animateFloatAsState(
            targetValue = if (isDragging) 1f else 0f,
            animationSpec = spring(dampingRatio = 0.6f),
            label = "splitDividerHandleAlpha",
        )

        val dividerVisualModifier = Modifier.drawBehind {
            val strokeWidth = IosPdfSplitDividerVisualThickness.toPx()
            if (handleAlpha > 0.01f) {
                val handleWidth = IosPdfSplitDividerHandleWidth.toPx()
                val handleHeight = IosPdfSplitDividerHandleHeight.toPx()
                val centerY = size.height / 2f
                val centerX = size.width / 2f
                if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                    drawRoundRect(
                        color = handleColor.copy(alpha = handleAlpha),
                        topLeft = Offset(centerX - handleWidth / 2f, centerY - handleHeight / 2f),
                        size = androidx.compose.ui.geometry.Size(handleWidth, handleHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleWidth / 2f),
                    )
                } else {
                    drawRoundRect(
                        color = handleColor.copy(alpha = handleAlpha),
                        topLeft = Offset(centerX - handleHeight / 2f, centerY - handleWidth / 2f),
                        size = androidx.compose.ui.geometry.Size(handleHeight, handleWidth),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(handleWidth / 2f),
                    )
                }
            }
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

        val dividerSemanticsModifier = Modifier
            .semantics {
                contentDescription = dividerDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayedFraction,
                    range = MinimumPdfSplitDividerFraction..MaximumPdfSplitDividerFraction,
                    steps = 0,
                )
                setProgress { value ->
                    dragState = dragState.cancel()
                    onDividerChange(
                        value.coerceIn(MinimumPdfSplitDividerFraction, MaximumPdfSplitDividerFraction),
                        plan.orientation,
                        workspace.revision,
                    )
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(
                        dividerDecreaseDescription,
                    ) {
                        dragState = dragState.cancel()
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
                    CustomAccessibilityAction(
                        dividerIncreaseDescription,
                    ) {
                        dragState = dragState.cancel()
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

        val dividerPointerModifier = Modifier.pointerInput(
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
                        ?: run {
                            if (isDragging) dragState = dragState.cancel()
                            return@awaitEachGesture
                        }
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
                        dragState = dragState.preview(rawFraction)
                    }
                    if (change.changedToUp()) {
                        if (isDragging) {
                            val committed = dragState.commit().committedFraction
                            dragState = dragState.cancel()
                            onDividerChange(committed, plan.orientation, workspace.revision)
                        } else {
                            val isDoubleTap = lastTapTimeMillis != Long.MIN_VALUE &&
                                down.uptimeMillis - lastTapTimeMillis in 1..IosPdfSplitDoubleTapTimeoutMillis
                            if (isDoubleTap) {
                                dragState = dragState.cancel()
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
                    dragState = dragState.cancel()
                }
            }
        }

        val dividerInteractionModifier = dividerSemanticsModifier.then(dividerPointerModifier)

        if (plan.orientation == PdfSplitOrientation.VERTICAL) {
            val firstWidth = with(density) { framePlan.firstPaneSizePx.toDp() }
            val secondWidth = with(density) { framePlan.secondPaneSizePx.toDp() }
            Box(Modifier.fillMaxSize()) {
                if (isRtl) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(secondWidth).fillMaxHeight()) { second() }
                        Spacer(Modifier.width(IosPdfSplitDividerTouchTarget).fillMaxHeight())
                        Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
                    }
                } else {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
                        Spacer(Modifier.width(IosPdfSplitDividerTouchTarget).fillMaxHeight())
                        Box(Modifier.width(secondWidth).fillMaxHeight()) { second() }
                    }
                }
                // Preview visual follows the in-flight drag...
                Box(
                    Modifier
                        .offset(x = visualDividerOffset)
                        .width(IosPdfSplitDividerTouchTarget)
                        .height(dividerCrossAxisSize)
                        .then(dividerVisualModifier),
                )
                // ...while the drag/a11y target stays anchored to the committed plan.
                Box(
                    Modifier
                        .offset(x = interactionDividerOffset)
                        .width(IosPdfSplitDividerTouchTarget)
                        .height(dividerCrossAxisSize)
                        .then(dividerInteractionModifier),
                )
            }
        } else {
            val firstHeight = with(density) { framePlan.firstPaneSizePx.toDp() }
            val secondHeight = with(density) { framePlan.secondPaneSizePx.toDp() }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.height(firstHeight).fillMaxWidth()) { first() }
                    Spacer(Modifier.height(IosPdfSplitDividerTouchTarget).fillMaxWidth())
                    Box(Modifier.height(secondHeight).fillMaxWidth()) { second() }
                }
                Box(
                    Modifier
                        .offset(y = visualDividerOffset)
                        .height(IosPdfSplitDividerTouchTarget)
                        .width(dividerCrossAxisSize)
                        .then(dividerVisualModifier),
                )
                Box(
                    Modifier
                        .offset(y = interactionDividerOffset)
                        .height(IosPdfSplitDividerTouchTarget)
                        .width(dividerCrossAxisSize)
                        .then(dividerInteractionModifier),
                )
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
            Text(readerString("pdf_split_reader_compact_view", "Compact view"))
            IosPdfSplitPaneSwitchChip(
                selected = focusedPane == PdfSplitPane.PRIMARY,
                label = readerString("pdf_split_reader_primary_pane", "Primary pane"),
                onClick = { onFocusPane(PdfSplitPane.PRIMARY) },
            )
            IosPdfSplitPaneSwitchChip(
                selected = focusedPane == PdfSplitPane.SECONDARY,
                label = readerString("pdf_split_reader_secondary_pane", "Secondary pane"),
                onClick = { onFocusPane(PdfSplitPane.SECONDARY) },
            )
        }
    }
}

@Composable
private fun IosPdfSplitPaneSwitchChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Platform-native document picker sheet mirroring Android's split picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun IosPdfSplitPickerDialog(
    books: List<BookItem>,
    title: String,
    onDismiss: () -> Unit,
    onBookSelected: (BookItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (books.isEmpty()) {
                Text(
                    text = readerString("pdf_split_reader_no_other_documents", "No other PDFs are available"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(420.dp),
                    contentPadding = WindowInsets.navigationBars.union(WindowInsets.ime).asPaddingValues(),
                ) {
                    items(books, key = { it.id }) { book ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    book.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.clickable { onBookSelected(book) },
                            trailingContent = {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.width(18.dp).height(18.dp),
                                    tint = MaterialTheme.colorScheme.outlineVariant,
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
