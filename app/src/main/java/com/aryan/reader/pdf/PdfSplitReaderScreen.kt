package com.aryan.reader.pdf

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.cardTitle
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.data.getUri
import com.aryan.reader.shared.PdfSplitOrientation
import com.aryan.reader.shared.PdfSplitPane
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.PdfSplitWorkspaceState
import com.aryan.reader.shared.PdfSplitPresentation
import com.aryan.reader.shared.DefaultPdfSplitDividerFraction
import com.aryan.reader.shared.MaximumPdfSplitDividerFraction
import com.aryan.reader.shared.MinimumPdfSplitDividerFraction
import com.aryan.reader.shared.PdfSplitDividerDragState
import com.aryan.reader.shared.pdfSplitDividerFractionAtAbsolutePosition
import com.aryan.reader.shared.resolveLayout
import com.aryan.reader.shared.samePdfDocument
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.rememberTtsController
import kotlin.math.abs

private val PdfSplitPaneHeaderHeight = 48.dp
private val PdfSplitDividerTouchTarget = 24.dp
private val PdfSplitDividerVisualThickness = 2.dp
private val PdfSplitMinPaneWidth = 280.dp
private val PdfSplitMinPaneHeight = 320.dp
private const val PdfSplitDoubleTapTimeoutMillis = 300L

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PdfSplitReaderScreen(
    workspace: PdfSplitWorkspaceState,
    availablePdfs: List<RecentFileItem>,
    isProUser: Boolean,
    usePdfFileNameAsDisplayName: Boolean,
    viewModel: MainViewModel,
    onFocusPane: (PdfSplitPane, Long) -> Unit,
    onClosePane: (PdfSplitPane, Long) -> Unit,
    onCloseWorkspace: () -> Unit,
    onSwapPanes: () -> Unit,
    onOrientationChange: (PdfSplitOrientation) -> Unit,
    onDividerChange: (Float, PdfSplitOrientation, Long) -> Unit,
    onOpenDocument: (String, PdfSplitPane, Long?, Long) -> Unit,
    onNavigateToPro: () -> Unit,
) {
    var showDocumentPicker by rememberSaveable { mutableStateOf(false) }
    var pickerTarget by rememberSaveable { mutableStateOf(PdfSplitPane.SECONDARY) }
    var pickerTargetSessionId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pickerTargetRevision by rememberSaveable { mutableStateOf(0L) }
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isAppActive by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED)
    }

    DisposableEffect(ttsController) {
        onDispose {
            ttsController.stop()
        }
    }
    DisposableEffect(lifecycleOwner, ttsController) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isAppActive = true
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    isAppActive = false
                    if (event == Lifecycle.Event.ON_STOP) ttsController.stop()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun closePane(pane: PdfSplitPane, sessionId: Long) {
        val document = workspace.pane(pane)
        if (document?.sessionId != sessionId) return
        if (document.bookId == ttsState.bookId) {
            ttsController.stop()
        }
        onClosePane(pane, sessionId)
    }

    fun focusPane(pane: PdfSplitPane, sessionId: Long) {
        val document = workspace.pane(pane)
        if (document?.sessionId != sessionId) return
        if (ttsState.bookId != null && ttsState.bookId != document.bookId) {
            ttsController.stop()
        }
        onFocusPane(pane, sessionId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        PdfSplitWorkspaceToolbar(
            workspace = workspace,
            onSwapPanes = onSwapPanes,
            onOrientationChange = onOrientationChange,
            onCloseWorkspace = onCloseWorkspace,
            onAddDocument = { targetPane ->
                pickerTarget = targetPane
                pickerTargetSessionId = workspace.pane(targetPane)?.sessionId
                pickerTargetRevision = workspace.revision
                showDocumentPicker = true
            },
            canAddDocument = workspace.primary != null,
        )

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val primary = workspace.primary
            val secondary = workspace.secondary
            if (primary == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.pdf_split_reader_no_other_documents))
                }
            } else if (secondary == null) {
                PdfSplitDocumentPane(
                    paneId = PdfSplitPane.PRIMARY,
                    document = primary,
                    availablePdfs = availablePdfs,
                    isFocused = workspace.focusedPane == PdfSplitPane.PRIMARY,
                    isProUser = isProUser,
                    usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                    viewModel = viewModel,
                    isAppActive = isAppActive,
                    ttsController = ttsController,
                    onFocus = { pane, sessionId -> focusPane(pane, sessionId) },
                    onClose = { pane, sessionId -> closePane(pane, sessionId) },
                    onNavigateToPro = onNavigateToPro,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PdfSplitPaneLayout(
                    workspace = workspace,
                    onDividerChange = onDividerChange,
                    onFocusPane = { pane ->
                        workspace.pane(pane)?.let { focusPane(pane, it.sessionId) }
                    },
                    first = {
                        PdfSplitDocumentPane(
                            paneId = PdfSplitPane.PRIMARY,
                            document = primary,
                            availablePdfs = availablePdfs,
                            isFocused = workspace.focusedPane == PdfSplitPane.PRIMARY,
                            isProUser = isProUser,
                            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                            viewModel = viewModel,
                            isAppActive = isAppActive,
                            ttsController = ttsController,
                            onFocus = { pane, sessionId -> focusPane(pane, sessionId) },
                            onClose = { pane, sessionId -> closePane(pane, sessionId) },
                            onNavigateToPro = onNavigateToPro,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                    second = {
                        PdfSplitDocumentPane(
                            paneId = PdfSplitPane.SECONDARY,
                            document = secondary,
                            availablePdfs = availablePdfs,
                            isFocused = workspace.focusedPane == PdfSplitPane.SECONDARY,
                            isProUser = isProUser,
                            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                            viewModel = viewModel,
                            isAppActive = isAppActive,
                            ttsController = ttsController,
                            onFocus = { pane, sessionId -> focusPane(pane, sessionId) },
                            onClose = { pane, sessionId -> closePane(pane, sessionId) },
                            onNavigateToPro = onNavigateToPro,
                            modifier = Modifier.fillMaxSize(),
                        )
                    },
                )
            }
        }
    }

    if (showDocumentPicker) {
        PdfSplitPdfPicker(
            availablePdfs = availablePdfs.filter { item ->
                val candidate = item.uriString?.let { uri ->
                    PdfSplitPaneState(item.bookId, uri)
                } ?: return@filter false
                val otherPane = when (pickerTarget) {
                    PdfSplitPane.PRIMARY -> workspace.secondary
                    PdfSplitPane.SECONDARY -> workspace.primary
                }
                !candidate.samePdfDocument(otherPane)
            },
            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            pickerTitle = if (workspace.isSplit) {
                stringResource(R.string.pdf_split_reader_replace_document)
            } else {
                null
            },
            onDismiss = { showDocumentPicker = false },
            onDocumentSelected = { item ->
                showDocumentPicker = false
                onOpenDocument(
                    item.bookId,
                    pickerTarget,
                    pickerTargetSessionId,
                    pickerTargetRevision,
                )
            },
        )
    }
}

@Composable
private fun PdfSplitWorkspaceToolbar(
    workspace: PdfSplitWorkspaceState,
    onSwapPanes: () -> Unit,
    onOrientationChange: (PdfSplitOrientation) -> Unit,
    onCloseWorkspace: () -> Unit,
    onAddDocument: (PdfSplitPane) -> Unit,
    canAddDocument: Boolean,
) {
    var orientationMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.pdf_split_reader_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box {
                FilterChip(
                    selected = true,
                    onClick = { orientationMenuExpanded = true },
                    label = {
                        Text(
                            text = if (workspace.orientation == PdfSplitOrientation.VERTICAL) {
                                stringResource(R.string.pdf_split_reader_vertical)
                            } else {
                                stringResource(R.string.pdf_split_reader_horizontal)
                            },
                            maxLines = 1,
                        )
                    },
                )
                DropdownMenu(
                    expanded = orientationMenuExpanded,
                    onDismissRequest = { orientationMenuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pdf_split_reader_vertical)) },
                        onClick = {
                            orientationMenuExpanded = false
                            onOrientationChange(PdfSplitOrientation.VERTICAL)
                        },
                        trailingIcon = {
                            if (workspace.orientation == PdfSplitOrientation.VERTICAL) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.content_desc_selected),
                                )
                            }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.pdf_split_reader_horizontal)) },
                        onClick = {
                            orientationMenuExpanded = false
                            onOrientationChange(PdfSplitOrientation.HORIZONTAL)
                        },
                        trailingIcon = {
                            if (workspace.orientation == PdfSplitOrientation.HORIZONTAL) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = stringResource(R.string.content_desc_selected),
                                )
                            }
                        },
                    )
                }
            }
            if (canAddDocument) {
                IconButton(
                    onClick = {
                        onAddDocument(
                            if (workspace.isSplit) {
                                workspace.focusedPane
                            } else {
                                PdfSplitPane.SECONDARY
                            },
                        )
                    },
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.pdf_split_reader_add_document),
                    )
                }
            }
            if (workspace.isSplit) {
                IconButton(onClick = onSwapPanes) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.pdf_split_reader_swap),
                    )
                }
            }
            IconButton(onClick = onCloseWorkspace) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.pdf_split_reader_close),
                )
            }
        }
    }
}

@Composable
private fun PdfSplitPaneLayout(
    workspace: PdfSplitWorkspaceState,
    onDividerChange: (Float, PdfSplitOrientation, Long) -> Unit,
    onFocusPane: (PdfSplitPane) -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val availableWidth = constraints.maxWidth.coerceAtLeast(0)
        val availableHeight = constraints.maxHeight.coerceAtLeast(0)
        val dividerThicknessPx = with(density) { PdfSplitDividerTouchTarget.roundToPx() }
        val minPaneWidthPx = with(density) { PdfSplitMinPaneWidth.roundToPx() }
        val minPaneHeightPx = with(density) { PdfSplitMinPaneHeight.roundToPx() }
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
        val dividerDescription = stringResource(R.string.pdf_split_reader_divider_desc)
        val dividerDecreaseDescription = stringResource(R.string.pdf_split_reader_divider_decrease)
        val dividerIncreaseDescription = stringResource(R.string.pdf_split_reader_divider_increase)
        val axisSizePx = if (plan.orientation == PdfSplitOrientation.VERTICAL) {
            availableWidth
        } else {
            availableHeight
        }
        val dividerAbsoluteStartPx = if (
            plan.orientation == PdfSplitOrientation.VERTICAL && isRtl
        ) {
            axisSizePx - plan.firstPaneSizePx - dividerThicknessPx
        } else {
            plan.firstPaneSizePx
        }
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
        val currentDividerAbsoluteStartPx = rememberUpdatedState(dividerAbsoluteStartPx)
        val dividerSemanticsModifier = Modifier
            .semantics {
                contentDescription = dividerDescription
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = displayedFraction,
                    range = MinimumPdfSplitDividerFraction..MaximumPdfSplitDividerFraction,
                    steps = 0,
                )
                setProgress { value ->
                    val safe = value.coerceIn(
                        MinimumPdfSplitDividerFraction,
                        MaximumPdfSplitDividerFraction,
                    )
                    dragState = dragState.cancel()
                    onDividerChange(safe, plan.orientation, workspace.revision)
                    true
                }
                customActions = listOf(
                    CustomAccessibilityAction(dividerDecreaseDescription) {
                        val safe = (displayedFraction - 0.05f).coerceIn(
                            MinimumPdfSplitDividerFraction,
                            MaximumPdfSplitDividerFraction,
                        )
                        dragState = dragState.cancel()
                        onDividerChange(safe, plan.orientation, workspace.revision)
                        true
                    },
                    CustomAccessibilityAction(dividerIncreaseDescription) {
                        val safe = (displayedFraction + 0.05f).coerceIn(
                            MinimumPdfSplitDividerFraction,
                            MaximumPdfSplitDividerFraction,
                        )
                        dragState = dragState.cancel()
                        onDividerChange(safe, plan.orientation, workspace.revision)
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
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    val pointerAxis = if (plan.orientation == PdfSplitOrientation.VERTICAL) {
                        down.position.x
                    } else {
                        down.position.y
                    }
                    val pointerAbsoluteAxis = currentDividerAbsoluteStartPx.value + pointerAxis
                    val dividerCenter = currentDividerAbsoluteStartPx.value + dividerThicknessPx / 2f
                    if (abs(pointerAbsoluteAxis - dividerCenter) > dividerThicknessPx / 2f) {
                        return@awaitEachGesture
                    }
                    // Once the pointer lands in the divider target, claim the
                    // stream before either reader pane can cancel it while the
                    // preview changes pane constraints.
                    down.consume()
                    val pointerId = down.id
                    val startPosition = down.position
                    var isDragging = false
                    var didFinish = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId }
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

                        val isRelease = change.changedToUp() || (
                            !change.pressed && event.type == PointerEventType.Release
                            )
                        if (isRelease) {
                            if (isDragging) {
                                val committed = dragState.commit().committedFraction
                                dragState = dragState.cancel()
                                onDividerChange(committed, plan.orientation, workspace.revision)
                            } else {
                                val isDoubleTap = lastTapTimeMillis != Long.MIN_VALUE &&
                                    down.uptimeMillis - lastTapTimeMillis in 1..PdfSplitDoubleTapTimeoutMillis
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
                        if (!change.pressed) {
                            break
                        }
                    }

                    if (!didFinish && isDragging) {
                        dragState = dragState.cancel()
                    }
                }
            }

        val dividerVisualModifier = Modifier.drawBehind {
            val strokeWidth = PdfSplitDividerVisualThickness.toPx()
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

        val dividerInteractionModifier = dividerSemanticsModifier.then(dividerPointerModifier)

        if (plan.presentation == PdfSplitPresentation.SINGLE) {
            Box(Modifier.fillMaxSize()) {
                if (workspace.focusedPane == PdfSplitPane.PRIMARY) first() else second()
                PdfSplitPaneSwitcher(
                    focusedPane = workspace.focusedPane,
                    onFocusPane = onFocusPane,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        } else if (plan.orientation == PdfSplitOrientation.VERTICAL) {
            val firstWidth = with(density) { framePlan.firstPaneSizePx.toDp() }
            val secondWidth = with(density) { framePlan.secondPaneSizePx.toDp() }
            // Give the divider an explicit cross-axis size.  `fillMaxHeight()`
            // alone can be measured to the header's intrinsic height by the
            // Row when its siblings contain unconstrained reader content.  A
            // short semantics/pointer node makes the divider impossible to
            // drag from the document area even though the visual layout is
            // side-by-side.
            val dividerHeight = with(density) { availableHeight.toDp() }
            if (isRtl) {
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(secondWidth).fillMaxHeight()) { second() }
                        Spacer(Modifier.width(PdfSplitDividerTouchTarget).fillMaxHeight())
                        Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
                    }
                    Box(
                        Modifier
                            .offset(x = visualDividerOffset)
                            .width(PdfSplitDividerTouchTarget)
                            .height(dividerHeight)
                            .then(dividerVisualModifier),
                    )
                    Box(
                        Modifier
                            .offset(x = interactionDividerOffset)
                            .width(PdfSplitDividerTouchTarget)
                            .height(dividerHeight)
                            .then(dividerInteractionModifier),
                    )
                }
            } else {
                Box(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxSize()) {
                        Box(Modifier.width(firstWidth).fillMaxHeight()) { first() }
                        Spacer(Modifier.width(PdfSplitDividerTouchTarget).fillMaxHeight())
                        Box(Modifier.width(secondWidth).fillMaxHeight()) { second() }
                    }
                    Box(
                        Modifier
                            .offset(x = visualDividerOffset)
                            .width(PdfSplitDividerTouchTarget)
                            .height(dividerHeight)
                            .then(dividerVisualModifier),
                    )
                    Box(
                        Modifier
                            .offset(x = interactionDividerOffset)
                            .width(PdfSplitDividerTouchTarget)
                            .height(dividerHeight)
                            .then(dividerInteractionModifier),
                    )
                }
            }
        } else {
            val firstHeight = with(density) { framePlan.firstPaneSizePx.toDp() }
            val secondHeight = with(density) { framePlan.secondPaneSizePx.toDp() }
            val dividerWidth = with(density) { availableWidth.toDp() }
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.height(firstHeight).fillMaxWidth()) { first() }
                    Spacer(Modifier.height(PdfSplitDividerTouchTarget).fillMaxWidth())
                    Box(Modifier.height(secondHeight).fillMaxWidth()) { second() }
                }
                Box(
                    Modifier
                        .offset(y = visualDividerOffset)
                        .height(PdfSplitDividerTouchTarget)
                        .width(dividerWidth)
                        .then(dividerVisualModifier),
                )
                Box(
                    Modifier
                        .offset(y = interactionDividerOffset)
                        .height(PdfSplitDividerTouchTarget)
                        .width(dividerWidth)
                        .then(dividerInteractionModifier),
                )
            }
        }
    }
}

@Composable
private fun PdfSplitPaneSwitcher(
    focusedPane: PdfSplitPane,
    onFocusPane: (PdfSplitPane) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        tonalElevation = 4.dp,
        shadowElevation = 3.dp,
        modifier = modifier.padding(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.pdf_split_reader_single_pane_fallback),
                style = MaterialTheme.typography.labelMedium,
            )
            FilterChip(
                selected = focusedPane == PdfSplitPane.PRIMARY,
                onClick = { onFocusPane(PdfSplitPane.PRIMARY) },
                label = { Text(stringResource(R.string.pdf_split_reader_primary_pane)) },
            )
            FilterChip(
                selected = focusedPane == PdfSplitPane.SECONDARY,
                onClick = { onFocusPane(PdfSplitPane.SECONDARY) },
                label = { Text(stringResource(R.string.pdf_split_reader_secondary_pane)) },
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
private fun PdfSplitDocumentPane(
    paneId: PdfSplitPane,
    document: PdfSplitPaneState,
    availablePdfs: List<RecentFileItem>,
    isFocused: Boolean,
    isProUser: Boolean,
    usePdfFileNameAsDisplayName: Boolean,
    viewModel: MainViewModel,
    isAppActive: Boolean,
    ttsController: TtsController,
    onFocus: (PdfSplitPane, Long) -> Unit,
    onClose: (PdfSplitPane, Long) -> Unit,
    onNavigateToPro: () -> Unit,
    modifier: Modifier,
) {
    val item = remember(availablePdfs, document.bookId, document.uriString) {
        availablePdfs.firstOrNull {
            it.uriString?.let { uri ->
                PdfSplitPaneState(it.bookId, uri).samePdfDocument(document)
            } == true
        }
    }
    val title = item?.cardTitle(usePdfFileNameAsDisplayName)
        ?: document.uriString.toUri().lastPathSegment
        ?: stringResource(R.string.pdf_viewer)
    val resolvedPdfUri = item?.getUri() ?: document.uriString.toUri()

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .pointerInput(paneId, document.sessionId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onFocus(paneId, document.sessionId)
                    waitForUpOrCancellation()
                }
            },
    ) {
        Surface(tonalElevation = if (isFocused) 3.dp else 1.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PdfSplitPaneHeaderHeight)
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
                IconButton(onClick = { onClose(paneId, document.sessionId) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.pdf_split_reader_close_pane),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (item == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.pdf_split_reader_missing_document))
                }
            } else {
                key(paneId, document.canonicalIdentity, document.sessionId) {
                    PdfViewerScreen(
                        pdfUri = resolvedPdfUri,
                        initialPage = item.lastPage,
                        initialBookmarksJson = item.bookmarksJson,
                        isProUser = isProUser,
                        onNavigateBack = { onClose(paneId, document.sessionId) },
                        onSavePosition = viewModel::savePdfReadingPosition,
                        onBookmarksChanged = { bookmarksJson ->
                            viewModel.saveBookmarks(
                                bookId = document.bookId,
                                bookmarksJson = bookmarksJson,
                                documentUri = resolvedPdfUri,
                            )
                        },
                        onNavigateToPro = onNavigateToPro,
                        viewModel = viewModel,
                        ttsControllerOverride = ttsController,
                        isPaneAppActive = isAppActive,
                        pane = PdfViewerPane(
                            bookId = document.bookId,
                            pdfUri = resolvedPdfUri,
                            sessionId = document.sessionId,
                            initialPage = item.lastPage,
                            initialBookmarksJson = item.bookmarksJson,
                        ),
                        isPaneFocused = isFocused,
                    )
                }
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun PdfSplitPdfPicker(
    availablePdfs: List<RecentFileItem>,
    usePdfFileNameAsDisplayName: Boolean,
    pickerTitle: String? = null,
    onDismiss: () -> Unit,
    onDocumentSelected: (RecentFileItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Text(
                text = pickerTitle ?: stringResource(R.string.pdf_split_reader_choose_document),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            if (availablePdfs.isEmpty()) {
                Text(
                    text = stringResource(R.string.pdf_split_reader_no_other_documents),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
            } else {
                LazyColumn {
                    items(availablePdfs, key = { it.bookId }) { item ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    item.cardTitle(usePdfFileNameAsDisplayName),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            supportingContent = {
                                item.author?.let { author ->
                                    Text(author, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            modifier = Modifier.clickable { onDocumentSelected(item) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
