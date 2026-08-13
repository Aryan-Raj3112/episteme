@file:OptIn(ExperimentalMaterial3Api::class)

package com.aryan.reader.pdf

import androidx.compose.foundation.clickable
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aryan.reader.R
import com.aryan.reader.epubreader.OptionSegmentedControl
import com.aryan.reader.epubreader.SystemUiMode
import com.aryan.reader.epubreader.titleRes
import com.aryan.reader.readerModalMaxHeightDp
import com.aryan.reader.shared.reader.ReaderPageSpreadMode
import com.aryan.reader.shared.ui.SharedPdfVisualOptionsLabels
import com.aryan.reader.shared.ui.SharedPdfVisualOptionsSheet


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfCustomizeToolsSheet(
    hiddenTools: Set<String>,
    toolOrder: List<PdfReaderTool>,
    bottomTools: Set<String>,
    onUpdate: (Set<String>) -> Unit,
    onOrderUpdate: (List<PdfReaderTool>) -> Unit,
    onPlacementUpdate: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val availableTools = defaultPdfToolOrder().toSet()
    val context = androidx.compose.ui.platform.LocalContext.current
    com.aryan.reader.shared.ui.SharedPdfToolbarCustomizationDialog(
        hiddenToolIds = hiddenTools.mapNotNullTo(mutableSetOf()) { PdfReaderTool.fromId(it)?.id },
        toolOrder = toolOrder,
        bottomToolIds = bottomTools.mapNotNullTo(mutableSetOf()) { PdfReaderTool.fromId(it)?.id },
        availableTools = availableTools,
        labels = com.aryan.reader.shared.ui.SharedPdfToolbarCustomizationLabels(
            title = stringResource(R.string.title_customize_toolbar),
            reset = stringResource(R.string.action_reset),
            close = stringResource(R.string.action_close),
            topBar = stringResource(R.string.toolbar_top_bar),
            bottomBar = stringResource(R.string.toolbar_bottom_bar),
            hiddenTools = stringResource(R.string.toolbar_hidden_tools),
            dropToolsHere = stringResource(R.string.toolbar_drop_tools_here),
            moreMenu = stringResource(R.string.toolbar_more_menu),
        ),
        toolTitle = { context.getString(it.titleRes) },
        onHiddenToolsUpdate = { ids ->
            onUpdate(ids.mapNotNullTo(mutableSetOf()) { PdfReaderTool.fromId(it)?.name })
        },
        onToolOrderUpdate = onOrderUpdate,
        onBottomToolsUpdate = { ids ->
            onPlacementUpdate(ids.mapNotNullTo(mutableSetOf()) { PdfReaderTool.fromId(it)?.name })
        },
        onDismiss = onDismiss,
        toolIcon = { PdfToolPreviewIcon(it) },
    )
}

@Composable
private fun PdfToolPreviewIcon(tool: PdfReaderTool) {
    val title = stringResource(tool.titleRes)
    when (tool) {
        PdfReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.BRIGHTNESS -> Icon(painterResource(id = R.drawable.contrast), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.LOCK_PANNING -> Icon(Icons.Default.LockOpen, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.FILE_INFO -> Icon(Icons.Default.Info, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SLIDER -> Icon(painterResource(id = R.drawable.slider), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.HIGHLIGHT_ALL -> Icon(painterResource(id = R.drawable.highlight_text), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.EDIT_MODE -> Icon(Icons.Default.Edit, contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.TTS_CONTROLS -> Icon(painterResource(id = R.drawable.text_to_speech), contentDescription = title, modifier = Modifier.size(20.dp))
        PdfReaderTool.SCREEN_ORIENTATION -> Icon(Icons.Default.ScreenRotation, contentDescription = title, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.MoreVert, contentDescription = title, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun PdfVisualOptionsSheet(
    displayMode: DisplayMode,
    systemUiMode: SystemUiMode,
    pageSpreadMode: ReaderPageSpreadMode,
    firstPageStandaloneInSpread: Boolean,
    showVerticalPageGap: Boolean,
    showPageNumberOverlay: Boolean,
    onPageSpreadModeChange: (ReaderPageSpreadMode) -> Unit,
    onFirstPageStandaloneInSpreadChange: (Boolean) -> Unit,
    onSystemUiModeChange: (SystemUiMode) -> Unit,
    onShowVerticalPageGapChange: (Boolean) -> Unit,
    onShowPageNumberOverlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val maxSheetHeight = readerModalMaxHeightDp(configuration.screenHeightDp).dp
    SharedPdfVisualOptionsSheet(
        displayMode = displayMode,
        systemUiMode = systemUiMode,
        pageSpreadMode = pageSpreadMode,
        firstPageStandaloneInSpread = firstPageStandaloneInSpread,
        showVerticalPageGap = showVerticalPageGap,
        showPageNumberOverlay = showPageNumberOverlay,
        onPageSpreadModeChange = onPageSpreadModeChange,
        onFirstPageStandaloneInSpreadChange = onFirstPageStandaloneInSpreadChange,
        onSystemUiModeChange = onSystemUiModeChange,
        onShowVerticalPageGapChange = onShowVerticalPageGapChange,
        onShowPageNumberOverlayChange = onShowPageNumberOverlayChange,
        maxSheetHeight = maxSheetHeight,
        labels = SharedPdfVisualOptionsLabels(
            title = stringResource(R.string.menu_visual_options),
            close = stringResource(R.string.action_close),
            systemUi = stringResource(R.string.visual_options_system_ui),
            systemUiDescription = stringResource(R.string.visual_options_system_ui_desc),
            systemUiOptions = SystemUiMode.entries.associateWith { stringResource(it.titleRes) },
            pageLayout = stringResource(R.string.visual_options_page_layout),
            pageSpread = stringResource(R.string.visual_options_pdf_page_spread),
            spreadOptions = mapOf(
                ReaderPageSpreadMode.SINGLE to stringResource(R.string.visual_options_pdf_spread_single),
                ReaderPageSpreadMode.TWO_PAGE to stringResource(R.string.visual_options_pdf_spread_two)
            ),
            firstPageAlone = stringResource(R.string.visual_options_pdf_first_page_alone),
            firstPageAloneDescription = stringResource(R.string.visual_options_pdf_first_page_alone_desc),
            removePageGap = stringResource(R.string.visual_options_remove_page_gap),
            removePageGapDescription = stringResource(R.string.visual_options_remove_page_gap_desc),
            hidePageNumberOverlay = stringResource(R.string.visual_options_hide_page_number_overlay),
            hidePageNumberOverlayDescription = stringResource(R.string.visual_options_hide_page_number_overlay_desc)
        ),
        onDismiss = onDismiss
    )
}
