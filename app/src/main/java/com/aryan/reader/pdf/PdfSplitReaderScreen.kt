package com.aryan.reader.pdf

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.MainViewModel
import com.aryan.reader.R
import com.aryan.reader.cardTitle
import com.aryan.reader.data.RecentFileItem
import com.aryan.reader.shared.PdfSplitOrientation
import com.aryan.reader.shared.PdfSplitPane
import com.aryan.reader.shared.PdfSplitPaneState
import com.aryan.reader.shared.PdfSplitWorkspaceState
import com.aryan.reader.tts.TtsController
import com.aryan.reader.tts.rememberTtsController

private val PdfSplitPaneHeaderHeight = 48.dp
private val PdfSplitDividerTouchTarget = 24.dp
private val PdfSplitDividerVisualThickness = 2.dp

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
    onFocusPane: (PdfSplitPane) -> Unit,
    onClosePane: (PdfSplitPane) -> Unit,
    onCloseWorkspace: () -> Unit,
    onSwapPanes: () -> Unit,
    onOrientationChange: (PdfSplitOrientation) -> Unit,
    onDividerChange: (Float) -> Unit,
    onOpenDocument: (String) -> Unit,
    onNavigateToPro: () -> Unit,
) {
    var showDocumentPicker by rememberSaveable { mutableStateOf(false) }
    val ttsController = rememberTtsController()
    val ttsState by ttsController.ttsState.collectAsState()

    DisposableEffect(ttsController) {
        onDispose {
            ttsController.stop()
            PdfBitmapPool.clear()
            PdfThumbnailCache.clear()
        }
    }

    val closePane: (PdfSplitPane) -> Unit = { pane ->
        val paneBookId = workspace.pane(pane)?.bookId
        if (paneBookId != null && paneBookId == ttsState.bookId) {
            ttsController.stop()
        }
        onClosePane(pane)
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
            onAddDocument = { showDocumentPicker = true },
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
                    ttsController = ttsController,
                    onFocus = onFocusPane,
                    onClose = closePane,
                    onNavigateToPro = onNavigateToPro,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PdfSplitPaneLayout(
                    orientation = workspace.orientation,
                    dividerFraction = workspace.dividerFraction,
                    onDividerChange = onDividerChange,
                    first = {
                        PdfSplitDocumentPane(
                            paneId = PdfSplitPane.PRIMARY,
                            document = primary,
                            availablePdfs = availablePdfs,
                            isFocused = workspace.focusedPane == PdfSplitPane.PRIMARY,
                            isProUser = isProUser,
                            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
                            viewModel = viewModel,
                            ttsController = ttsController,
                            onFocus = onFocusPane,
                            onClose = closePane,
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
                            ttsController = ttsController,
                            onFocus = onFocusPane,
                            onClose = closePane,
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
                item.bookId != workspace.primary?.bookId &&
                    item.bookId != workspace.secondary?.bookId &&
                    item.uriString != workspace.primary?.uriString &&
                    item.uriString != workspace.secondary?.uriString
            },
            usePdfFileNameAsDisplayName = usePdfFileNameAsDisplayName,
            onDismiss = { showDocumentPicker = false },
            onDocumentSelected = { item ->
                showDocumentPicker = false
                onOpenDocument(item.bookId)
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
    onAddDocument: () -> Unit,
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
            if (workspace.isSplit) {
                IconButton(onClick = onSwapPanes) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = stringResource(R.string.pdf_split_reader_swap),
                    )
                }
            } else if (canAddDocument) {
                IconButton(onClick = onAddDocument) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.pdf_split_reader_add_document),
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
    orientation: PdfSplitOrientation,
    dividerFraction: Float,
    onDividerChange: (Float) -> Unit,
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fraction = dividerFraction.coerceIn(0.25f, 0.75f)
        val availableWidth = constraints.maxWidth
        val availableHeight = constraints.maxHeight
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        val dividerModifier = Modifier
            .drawBehind {
                val strokeWidth = PdfSplitDividerVisualThickness.toPx()
                if (orientation == PdfSplitOrientation.VERTICAL) {
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
            .pointerInput(orientation, availableWidth, availableHeight) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val available = if (orientation == PdfSplitOrientation.VERTICAL) {
                        availableWidth
                    } else {
                        availableHeight
                    }
                    if (available > 0) {
                        val delta = if (orientation == PdfSplitOrientation.VERTICAL) {
                            dragAmount.x / available.toFloat()
                        } else {
                            dragAmount.y / available.toFloat()
                        }
                        onDividerChange(fraction + delta)
                    }
                }
            }

        if (orientation == PdfSplitOrientation.VERTICAL) {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(fraction).fillMaxHeight()) { first() }
                Box(dividerModifier.width(PdfSplitDividerTouchTarget).fillMaxHeight())
                Box(Modifier.weight(1f - fraction).fillMaxHeight()) { second() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(fraction).fillMaxWidth()) { first() }
                Box(dividerModifier.height(PdfSplitDividerTouchTarget).fillMaxWidth())
                Box(Modifier.weight(1f - fraction).fillMaxWidth()) { second() }
            }
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
    ttsController: TtsController,
    onFocus: (PdfSplitPane) -> Unit,
    onClose: (PdfSplitPane) -> Unit,
    onNavigateToPro: () -> Unit,
    modifier: Modifier,
) {
    val item = remember(availablePdfs, document.bookId, document.uriString) {
        availablePdfs.firstOrNull { it.bookId == document.bookId }
    }
    val title = item?.cardTitle(usePdfFileNameAsDisplayName)
        ?: document.uriString.toUri().lastPathSegment
        ?: stringResource(R.string.pdf_viewer)

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .border(
                width = if (isFocused) 1.dp else 0.dp,
                color = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
            )
            .pointerInput(paneId) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onFocus(paneId)
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
                IconButton(onClick = { onClose(paneId) }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.pdf_split_reader_close_pane),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            PdfViewerScreen(
                pdfUri = document.uriString.toUri(),
                initialPage = item?.lastPage,
                initialBookmarksJson = item?.bookmarksJson,
                isProUser = isProUser,
                onNavigateBack = { onClose(paneId) },
                onSavePosition = viewModel::savePdfReadingPosition,
                onBookmarksChanged = { bookmarksJson ->
                    viewModel.saveBookmarks(document.bookId, bookmarksJson)
                },
                onNavigateToPro = onNavigateToPro,
                viewModel = viewModel,
                ttsControllerOverride = ttsController,
                pane = PdfViewerPane(
                    bookId = document.bookId,
                    pdfUri = document.uriString.toUri(),
                    initialPage = item?.lastPage,
                    initialBookmarksJson = item?.bookmarksJson,
                ),
                isPaneFocused = isFocused,
            )
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
internal fun PdfSplitPdfPicker(
    availablePdfs: List<RecentFileItem>,
    usePdfFileNameAsDisplayName: Boolean,
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
                text = stringResource(R.string.pdf_split_reader_choose_document),
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
