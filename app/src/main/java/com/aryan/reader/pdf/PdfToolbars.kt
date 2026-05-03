// PdfToolbars.kt
package com.aryan.reader.pdf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.aryan.reader.data.RecentFileItem
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.sp
import com.aryan.reader.BuildConfig
import com.aryan.reader.FileType
import com.aryan.reader.R
import com.aryan.reader.SearchState
import com.aryan.reader.SearchTopBar
import com.aryan.reader.TooltipIconButton
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.epubreader.SystemUiMode
import kotlin.collections.isNotEmpty

private val pdfToolbarTools = setOf(
    PdfReaderTool.DICTIONARY,
    PdfReaderTool.THEME,
    PdfReaderTool.LOCK_PANNING,
    PdfReaderTool.SLIDER,
    PdfReaderTool.TOC,
    PdfReaderTool.SEARCH,
    PdfReaderTool.HIGHLIGHT_ALL,
    PdfReaderTool.AI_FEATURES,
    PdfReaderTool.EDIT_MODE,
    PdfReaderTool.TTS_CONTROLS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfTopBar(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    systemUiMode: SystemUiMode,
    statusBarHeightDp: Dp,
    searchState: SearchState,
    focusRequester: FocusRequester,
    onCloseSearch: () -> Unit,
    isLoadingDocument: Boolean,
    errorMessage: String?,
    currentPageForDisplay: Int,
    totalPages: Int,
    pagerStatePageCount: Int,
    hiddenTools: Set<String>,
    toolOrder: List<PdfReaderTool>,
    bottomTools: Set<String>,
    isScrollLocked: Boolean,
    isEditMode: Boolean,
    displayMode: DisplayMode,
    isKeepScreenOn: Boolean,
    isTtsSessionActive: Boolean,
    isBookmarked: Boolean,
    canDeletePage: Boolean,
    isReflowingThisBook: Boolean,
    hasReflowFile: Boolean,
    isPdfDocumentLoaded: Boolean,
    isTabsEnabled: Boolean,
    openTabs: List<RecentFileItem>,
    activeTabBookId: String?,
    effectiveFileType: FileType,
    onNavigateBack: () -> Unit,
    onShowThemePanel: () -> Unit,
    onToggleScrollLock: () -> Unit,
    onShowDictionarySettings: () -> Unit,
    onShowPenPlayground: () -> Unit,
    onImportSvg: () -> Unit,
    onShowCustomizeTools: () -> Unit,
    onShowOcrLanguage: () -> Unit,
    onShowVisualOptions: () -> Unit,
    onShowSlider: () -> Unit,
    onShowToc: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleHighlights: () -> Unit,
    onShowAiHub: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleTts: () -> Unit,
    isTtsPlayingOrLoading: Boolean,
    showAllTextHighlights: Boolean,
    isHighlightingLoading: Boolean,
    tapToNavigateEnabled: Boolean,
    onToggleTapToNavigate: () -> Unit,
    onChangeDisplayMode: (DisplayMode) -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onStartAutoScroll: () -> Unit,
    onShowTtsSettings: () -> Unit,
    onToggleBookmark: () -> Unit,
    onInsertPage: () -> Unit,
    onDeletePage: () -> Unit,
    onReflowAction: () -> Unit,
    onShare: () -> Unit,
    onSaveCopy: () -> Unit,
    onPrint: () -> Unit,
    onTabClick: (String) -> Unit,
    onTabClose: (String) -> Unit,
    onNewTabClick: () -> Unit,
    onGenerateDemoAnnotations: () -> Unit
) {
    AnimatedVisibility(
        visible = showStandardBars,
        enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> -fullHeight } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> -fullHeight } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val isStatusBarVisible = when (systemUiMode) {
            SystemUiMode.DEFAULT -> true
            SystemUiMode.SYNC -> showStandardBars
            SystemUiMode.HIDDEN -> false
        }
        val topBarPadding = if (isStatusBarVisible) statusBarHeightDp else 0.dp

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = topBarPadding)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (searchState.isSearchActive) {
                        SearchTopBar(
                            searchState = searchState,
                            focusRequester = focusRequester,
                            onCloseSearch = onCloseSearch
                        )
                    } else {
                        TooltipIconButton(
                            text = stringResource(R.string.tooltip_back),
                            description = stringResource(R.string.tooltip_back_desc),
                            onClick = onNavigateBack
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                        val titleText = when {
                            isLoadingDocument -> stringResource(R.string.loading_pdf)
                            errorMessage != null -> stringResource(R.string.error_loading_pdf)
                            totalPages > 0 && pagerStatePageCount > 0 -> stringResource(R.string.page_of_pages, currentPageForDisplay + 1, totalPages)
                            totalPages > 0 && pagerStatePageCount == 0 -> stringResource(R.string.loading_page)
                            else -> stringResource(R.string.pdf_viewer)
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp).weight(1f).testTag("PageNumberIndicator")
                        )

                        toolOrder
                            .filter { it in pdfToolbarTools && !bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
                            .forEach { tool ->
                                when (tool) {
                                    PdfReaderTool.THEME -> TooltipIconButton(
                                        text = stringResource(R.string.tooltip_theme),
                                        description = stringResource(R.string.tooltip_theme_desc),
                                        onClick = onShowThemePanel
                                    ) {
                                        Icon(painterResource(id = R.drawable.palette), contentDescription = stringResource(R.string.tooltip_theme_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    PdfReaderTool.LOCK_PANNING -> TooltipIconButton(
                                        text = if (isScrollLocked) stringResource(R.string.tooltip_unlock_pan) else stringResource(R.string.tooltip_lock_pan),
                                        description = if (isScrollLocked) stringResource(R.string.tooltip_unlock_pan_desc) else stringResource(R.string.tooltip_lock_pan_desc),
                                        onClick = onToggleScrollLock
                                    ) {
                                        Icon(if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (isScrollLocked) stringResource(R.string.tooltip_unlock_pan) else stringResource(R.string.tooltip_lock_pan), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    PdfReaderTool.DICTIONARY -> TooltipIconButton(
                                        text = stringResource(R.string.tooltip_dictionary),
                                        description = stringResource(R.string.tooltip_dictionary_desc),
                                        onClick = onShowDictionarySettings
                                    ) {
                                        Icon(painterResource(id = R.drawable.dictionary), contentDescription = stringResource(R.string.content_desc_dictionary_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    PdfReaderTool.SLIDER -> TooltipIconButton(
                                        text = stringResource(R.string.tooltip_slider),
                                        description = stringResource(R.string.tooltip_slider_desc),
                                        onClick = onShowSlider,
                                        enabled = !isTtsPlayingOrLoading
                                    ) {
                                        Icon(painterResource(id = R.drawable.slider), contentDescription = stringResource(R.string.content_desc_navigate_slider))
                                    }
                                    PdfReaderTool.TOC -> TooltipIconButton(
                                        text = stringResource(R.string.tooltip_toc),
                                        description = stringResource(R.string.tooltip_toc_desc),
                                        onClick = onShowToc,
                                        enabled = !isTtsPlayingOrLoading
                                    ) {
                                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.content_desc_table_of_contents))
                                    }
                                    PdfReaderTool.SEARCH -> TooltipIconButton(
                                        text = stringResource(R.string.tooltip_search),
                                        description = stringResource(R.string.tooltip_search_desc),
                                        onClick = onSearchClick,
                                        enabled = !isTtsPlayingOrLoading
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                                    }
                                    PdfReaderTool.HIGHLIGHT_ALL -> TooltipIconButton(
                                        text = if (showAllTextHighlights) stringResource(R.string.tooltip_highlights_off) else stringResource(R.string.tooltip_highlights),
                                        description = if (showAllTextHighlights) stringResource(R.string.tooltip_highlights_off_desc) else stringResource(R.string.tooltip_highlights_desc),
                                        onClick = onToggleHighlights
                                    ) {
                                        if (isHighlightingLoading) CircularProgressIndicator(Modifier.size(24.dp))
                                        else Icon(painterResource(id = R.drawable.highlight_text), contentDescription = stringResource(R.string.content_desc_highlight_all_text), tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    PdfReaderTool.AI_FEATURES -> if (areReaderAiFeaturesEnabled(LocalContext.current)) {
                                        TooltipIconButton(
                                            text = stringResource(R.string.tooltip_ai),
                                            description = stringResource(R.string.tooltip_ai_desc),
                                            onClick = onShowAiHub
                                        ) {
                                            Icon(painterResource(id = R.drawable.ai), contentDescription = stringResource(R.string.tooltip_ai))
                                        }
                                    }
                                    PdfReaderTool.EDIT_MODE -> TooltipIconButton(
                                        text = if (isEditMode) stringResource(R.string.tooltip_edit_mode_exit) else stringResource(R.string.tooltip_edit_mode),
                                        description = if (isEditMode) stringResource(R.string.tooltip_edit_mode_exit_desc) else stringResource(R.string.tooltip_edit_mode_desc),
                                        onClick = onToggleEditMode
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.content_desc_toggle_editing_mode), tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    PdfReaderTool.TTS_CONTROLS -> TooltipIconButton(
                                        text = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop) else stringResource(R.string.tooltip_tts_start),
                                        description = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop_desc) else stringResource(R.string.tooltip_tts_start_desc),
                                        onClick = onToggleTts
                                    ) {
                                        Icon(if (isTtsSessionActive) painterResource(id = R.drawable.close) else painterResource(id = R.drawable.text_to_speech), contentDescription = if (isTtsSessionActive) stringResource(R.string.content_desc_stop_tts) else stringResource(R.string.content_desc_start_tts), tint = if (isTtsSessionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    else -> Unit
                                }
                            }

                        if (BuildConfig.DEBUG) {
                            TooltipIconButton(text = stringResource(R.string.tooltip_demo_annotations), onClick = onGenerateDemoAnnotations) {
                                Icon(Icons.Default.BugReport, contentDescription = stringResource(R.string.content_desc_generate_demo_annotations), tint = MaterialTheme.colorScheme.secondary)
                            }
                            TooltipIconButton(text = stringResource(R.string.pen_playground), onClick = onShowPenPlayground) {
                                Icon(Icons.Default.Star, contentDescription = stringResource(R.string.content_desc_open_pen_playground), tint = MaterialTheme.colorScheme.primary)
                            }
                            TooltipIconButton(text = stringResource(R.string.import_svg), onClick = onImportSvg) {
                                Icon(Icons.Default.Brush, contentDescription = stringResource(R.string.import_svg), tint = Color(0xFFE91E63))
                            }
                        }

                        Box {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            var showHiddenToolsExpanded by remember { mutableStateOf(false) }
                            TooltipIconButton(
                                text = stringResource(R.string.tooltip_more_options),
                                description = stringResource(R.string.tooltip_more_options_desc),
                                onClick = {
                                    showHiddenToolsExpanded = false
                                    showMoreMenu = true
                                }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.tooltip_more_options))
                            }

                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = {
                                    showHiddenToolsExpanded = false
                                    showMoreMenu = false
                                }
                            ) {
                                val hiddenToolbarTools = toolOrder.filter { it in pdfToolbarTools && hiddenTools.contains(it.name) }
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_customize_toolbar)) },
                                    onClick = { showMoreMenu = false; onShowCustomizeTools() },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_customize_toolbar), modifier = Modifier.size(20.dp)) }
                                )
                                HorizontalDivider()

                                if (hiddenToolbarTools.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("Hidden tools") },
                                        onClick = { showHiddenToolsExpanded = !showHiddenToolsExpanded },
                                        trailingIcon = {
                                            Icon(
                                                Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                modifier = Modifier.rotate(if (showHiddenToolsExpanded) 180f else 0f)
                                            )
                                        }
                                    )
                                    if (showHiddenToolsExpanded) {
                                        hiddenToolbarTools.forEach { tool ->
                                            HiddenPdfToolMenuItem(
                                                tool = tool,
                                                isTtsPlayingOrLoading = isTtsPlayingOrLoading,
                                                showAllTextHighlights = showAllTextHighlights,
                                                isHighlightingLoading = isHighlightingLoading,
                                                isEditMode = isEditMode,
                                                isTtsSessionActive = isTtsSessionActive,
                                                closeMenu = {
                                                    showHiddenToolsExpanded = false
                                                    showMoreMenu = false
                                                },
                                                onShowThemePanel = onShowThemePanel,
                                                onToggleScrollLock = onToggleScrollLock,
                                                onShowDictionarySettings = onShowDictionarySettings,
                                                onShowSlider = onShowSlider,
                                                onShowToc = onShowToc,
                                                onSearchClick = onSearchClick,
                                                onToggleHighlights = onToggleHighlights,
                                                onShowAiHub = onShowAiHub,
                                                onToggleEditMode = onToggleEditMode,
                                                onToggleTts = onToggleTts
                                            )
                                        }
                                    }
                                    HorizontalDivider()
                                }

                                if (BuildConfig.IS_PRO && !hiddenTools.contains(PdfReaderTool.OCR_LANGUAGE.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_ocr_language)) },
                                        onClick = { showMoreMenu = false; onShowOcrLanguage() }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.VISUAL_OPTIONS.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_visual_options)) },
                                        onClick = { showMoreMenu = false; onShowVisualOptions() },
                                        leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.READING_MODE.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_reading_mode_vertical)) },
                                        enabled = !isTtsSessionActive,
                                        onClick = { onChangeDisplayMode(DisplayMode.VERTICAL_SCROLL); showMoreMenu = false },
                                        trailingIcon = { if (displayMode == DisplayMode.VERTICAL_SCROLL) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.content_desc_selected)) }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_reading_mode_paginated)) },
                                        enabled = !isTtsSessionActive,
                                        onClick = { onChangeDisplayMode(DisplayMode.PAGINATION); showMoreMenu = false },
                                        trailingIcon = { if (displayMode == DisplayMode.PAGINATION) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.content_desc_selected)) }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.TAP_TO_TURN.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_tap_to_turn_pages)) },
                                        enabled = displayMode == DisplayMode.PAGINATION,
                                        onClick = {
                                            onToggleTapToNavigate()
                                            showMoreMenu = false
                                        },
                                        trailingIcon = {
                                            if (tapToNavigateEnabled) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = stringResource(R.string.content_desc_enabled)
                                                )
                                            }
                                        }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.KEEP_SCREEN_ON.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_keep_screen_on)) },
                                        onClick = { onToggleKeepScreenOn(); showMoreMenu = false },
                                        trailingIcon = { if (isKeepScreenOn) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.content_desc_selected)) }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.AUTO_SCROLL.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_auto_scroll)) },
                                        enabled = !isTtsSessionActive && displayMode == DisplayMode.VERTICAL_SCROLL,
                                        onClick = { showMoreMenu = false; onStartAutoScroll() }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.TTS_SETTINGS.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_tts_voice_settings)) },
                                        enabled = !isTtsSessionActive,
                                        onClick = { showMoreMenu = false; onShowTtsSettings() },
                                        leadingIcon = { Icon(Icons.Default.GraphicEq, contentDescription = null, modifier = Modifier.size(20.dp)) }
                                    )
                                }

                                if (!hiddenTools.contains(PdfReaderTool.BOOKMARK.name)) {
                                    DropdownMenuItem(
                                        text = { Text(if (isBookmarked) stringResource(R.string.menu_remove_bookmark) else stringResource(R.string.menu_bookmark_this_page)) },
                                        onClick = { showMoreMenu = false; onToggleBookmark() }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.PAGE_MANAGEMENT.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.menu_insert_blank_page)) },
                                        onClick = { showMoreMenu = false; onInsertPage() }
                                    )
                                    if (canDeletePage) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_delete_page)) },
                                            onClick = { showMoreMenu = false; onDeletePage() },
                                            colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error)
                                        )
                                    }
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.REFLOW.name)) {
                                    DropdownMenuItem(
                                        text = { Text(when { isReflowingThisBook -> stringResource(R.string.generating_reflow_progress); hasReflowFile -> stringResource(R.string.action_open_text_view); else -> stringResource(R.string.action_generate_text_view) }) },
                                        enabled = isPdfDocumentLoaded && !isReflowingThisBook,
                                        onClick = { showMoreMenu = false; onReflowAction() },
                                        leadingIcon = { Icon(painterResource(id = R.drawable.format_size), contentDescription = null, modifier = Modifier.size(20.dp)) }
                                    )
                                    HorizontalDivider()
                                }

                                if (!hiddenTools.contains(PdfReaderTool.SHARE.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_share)) },
                                        onClick = { showMoreMenu = false; onShare() },
                                        leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                                    )
                                }

                                if (effectiveFileType == FileType.PDF && !hiddenTools.contains(PdfReaderTool.SAVE_COPY.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_save_copy_to_device)) },
                                        onClick = { showMoreMenu = false; onSaveCopy() },
                                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) }
                                    )
                                }

                                if (effectiveFileType == FileType.PDF && !hiddenTools.contains(PdfReaderTool.PRINT.name)) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.action_print)) },
                                        onClick = { showMoreMenu = false; onPrint() },
                                        leadingIcon = { Icon(painterResource(id = R.drawable.print), contentDescription = null, modifier = Modifier.size(20.dp)) }
                                    )
                                }
                            }
                        }
                    }
                }
                if (isTabsEnabled && openTabs.isNotEmpty() && effectiveFileType == FileType.PDF) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().height(44.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        items(openTabs, key = { it.bookId }) { tab ->
                            val isSelected = tab.bookId == activeTabBookId
                            val bgColor = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent
                            val contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant

                            Row(
                                modifier = Modifier
                                    .height(if (isSelected) 44.dp else 36.dp)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .background(bgColor)
                                    .clickable { onTabClick(tab.bookId) }
                                    .padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tab.customName ?: tab.title ?: tab.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 140.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = contentColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { onTabClose(tab.bookId) },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_tab), modifier = Modifier.size(16.dp), tint = contentColor)
                                }
                            }
                        }

                        item {
                            IconButton(onClick = onNewTabClick, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).size(36.dp)) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.content_desc_new_tab), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HiddenPdfToolMenuItem(
    tool: PdfReaderTool,
    isTtsPlayingOrLoading: Boolean,
    showAllTextHighlights: Boolean,
    isHighlightingLoading: Boolean,
    isEditMode: Boolean,
    isTtsSessionActive: Boolean,
    closeMenu: () -> Unit,
    onShowThemePanel: () -> Unit,
    onToggleScrollLock: () -> Unit,
    onShowDictionarySettings: () -> Unit,
    onShowSlider: () -> Unit,
    onShowToc: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleHighlights: () -> Unit,
    onShowAiHub: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleTts: () -> Unit
) {
    val enabled = when (tool) {
        PdfReaderTool.SLIDER,
        PdfReaderTool.TOC,
        PdfReaderTool.SEARCH -> !isTtsPlayingOrLoading
        else -> true
    }
    DropdownMenuItem(
        text = { Text(tool.title) },
        enabled = enabled,
        onClick = {
            closeMenu()
            when (tool) {
                PdfReaderTool.THEME -> onShowThemePanel()
                PdfReaderTool.LOCK_PANNING -> onToggleScrollLock()
                PdfReaderTool.DICTIONARY -> onShowDictionarySettings()
                PdfReaderTool.SLIDER -> onShowSlider()
                PdfReaderTool.TOC -> onShowToc()
                PdfReaderTool.SEARCH -> onSearchClick()
                PdfReaderTool.HIGHLIGHT_ALL -> onToggleHighlights()
                PdfReaderTool.AI_FEATURES -> onShowAiHub()
                PdfReaderTool.EDIT_MODE -> onToggleEditMode()
                PdfReaderTool.TTS_CONTROLS -> onToggleTts()
                else -> Unit
            }
        },
        leadingIcon = {
            when (tool) {
                PdfReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.LOCK_PANNING -> Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.SLIDER -> Icon(painterResource(id = R.drawable.slider), contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.HIGHLIGHT_ALL -> {
                    if (isHighlightingLoading) CircularProgressIndicator(Modifier.size(20.dp))
                    else Icon(painterResource(id = R.drawable.highlight_text), contentDescription = null, modifier = Modifier.size(20.dp), tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PdfReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = null, modifier = Modifier.size(20.dp))
                PdfReaderTool.EDIT_MODE -> Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                PdfReaderTool.TTS_CONTROLS -> Icon(if (isTtsSessionActive) painterResource(id = R.drawable.close) else painterResource(id = R.drawable.text_to_speech), contentDescription = null, modifier = Modifier.size(20.dp), tint = if (isTtsSessionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    )
}

@Composable
fun ReflowProgressOverlay(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    isReflowingThisBook: Boolean,
    reflowProgressValue: Float
) {
    AnimatedVisibility(
        visible = showStandardBars && isReflowingThisBook,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200)) + slideOutVertically(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp),
            shadowElevation = 4.dp
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.generating_text_view),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(reflowProgressValue * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { reflowProgressValue },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun PdfJumpHistoryBar(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    searchStateActive: Boolean,
    backPage: Int?,
    forwardPage: Int?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    AnimatedVisibility(
        visible = showStandardBars && !searchStateActive && (backPage != null || forwardPage != null),
        enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onBack,
                    enabled = backPage != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_desc_jump_back),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = backPage?.let { stringResource(R.string.pdf_page_short, it + 1) } ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_clear), maxLines = 1)
                }

                TextButton(
                    onClick = onForward,
                    enabled = forwardPage != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = forwardPage?.let { stringResource(R.string.pdf_page_short, it + 1) } ?: "",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.content_desc_jump_forward),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PdfBottomBar(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    searchStateActive: Boolean,
    systemUiMode: SystemUiMode,
    navBarHeightDp: Dp,
    hiddenTools: Set<String>,
    toolOrder: List<PdfReaderTool>,
    bottomTools: Set<String>,
    isTtsPlayingOrLoading: Boolean,
    showAllTextHighlights: Boolean,
    isHighlightingLoading: Boolean,
    isEditMode: Boolean,
    isTtsSessionActive: Boolean,
    ttsErrorMessage: String?,
    onShowThemePanel: () -> Unit,
    onToggleScrollLock: () -> Unit,
    onShowDictionarySettings: () -> Unit,
    onShowSlider: () -> Unit,
    onShowToc: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleHighlights: () -> Unit,
    onShowAiHub: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleTts: () -> Unit,
    isBubbleZoomModeActive: Boolean,
    onToggleBubbleZoom: () -> Unit
) {
    AnimatedVisibility(
        visible = showStandardBars && !searchStateActive,
        enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val isNavBarVisible = systemUiMode != SystemUiMode.HIDDEN
        val bottomBarPadding = if (isNavBarVisible) navBarHeightDp else 0.dp

        Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
            val bottomBarScrollState = rememberScrollState()

            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = bottomBarPadding).height(56.dp).padding(horizontal = 8.dp).horizontalScroll(bottomBarScrollState),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                toolOrder
                    .filter { it in pdfToolbarTools && bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
                    .forEach { tool ->
                        when (tool) {
                            PdfReaderTool.THEME -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_theme),
                                description = stringResource(R.string.tooltip_theme_desc),
                                onClick = onShowThemePanel
                            ) {
                                Icon(painterResource(id = R.drawable.palette), contentDescription = stringResource(R.string.tooltip_theme_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PdfReaderTool.LOCK_PANNING -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_lock_pan),
                                description = stringResource(R.string.tooltip_lock_pan_desc),
                                onClick = onToggleScrollLock
                            ) {
                                Icon(Icons.Default.LockOpen, contentDescription = stringResource(R.string.tooltip_lock_pan), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PdfReaderTool.DICTIONARY -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_dictionary),
                                description = stringResource(R.string.tooltip_dictionary_desc),
                                onClick = onShowDictionarySettings
                            ) {
                                Icon(painterResource(id = R.drawable.dictionary), contentDescription = stringResource(R.string.content_desc_dictionary_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PdfReaderTool.SLIDER -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_slider),
                                description = stringResource(R.string.tooltip_slider_desc),
                                onClick = onShowSlider,
                                enabled = !isTtsPlayingOrLoading
                            ) {
                                Icon(painterResource(id = R.drawable.slider), contentDescription = stringResource(R.string.content_desc_navigate_slider))
                            }
                            PdfReaderTool.TOC -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_toc),
                                description = stringResource(R.string.tooltip_toc_desc),
                                onClick = onShowToc,
                                enabled = !isTtsPlayingOrLoading,
                                modifier = Modifier.testTag("TocButton")
                            ) {
                                Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.content_desc_table_of_contents))
                            }
                            PdfReaderTool.SEARCH -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_search),
                                description = stringResource(R.string.tooltip_search_desc),
                                onClick = onSearchClick,
                                enabled = !isTtsPlayingOrLoading,
                                modifier = Modifier.testTag("SearchButton")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                            }
                            PdfReaderTool.HIGHLIGHT_ALL -> TooltipIconButton(
                                text = if (showAllTextHighlights) stringResource(R.string.tooltip_highlights_off) else stringResource(R.string.tooltip_highlights),
                                description = if (showAllTextHighlights) stringResource(R.string.tooltip_highlights_off_desc) else stringResource(R.string.tooltip_highlights_desc),
                                onClick = onToggleHighlights
                            ) {
                                if (isHighlightingLoading) CircularProgressIndicator(Modifier.size(24.dp))
                                else Icon(painterResource(id = R.drawable.highlight_text), contentDescription = stringResource(R.string.content_desc_highlight_all_text), tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PdfReaderTool.AI_FEATURES -> if (areReaderAiFeaturesEnabled(LocalContext.current)) {
                                TooltipIconButton(
                                    text = stringResource(R.string.tooltip_ai),
                                    description = stringResource(R.string.tooltip_ai_desc),
                                    onClick = onShowAiHub
                                ) {
                                    Icon(painterResource(id = R.drawable.ai), contentDescription = stringResource(R.string.tooltip_ai))
                                }
                            }
                            PdfReaderTool.EDIT_MODE -> TooltipIconButton(
                                text = if (isEditMode) stringResource(R.string.tooltip_edit_mode_exit) else stringResource(R.string.tooltip_edit_mode),
                                description = if (isEditMode) stringResource(R.string.tooltip_edit_mode_exit_desc) else stringResource(R.string.tooltip_edit_mode_desc),
                                onClick = onToggleEditMode
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.content_desc_toggle_editing_mode), tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PdfReaderTool.TTS_CONTROLS -> TooltipIconButton(
                                text = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop) else stringResource(R.string.tooltip_tts_start),
                                description = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop_desc) else stringResource(R.string.tooltip_tts_start_desc),
                                onClick = onToggleTts
                            ) {
                                Icon(if (isTtsSessionActive) painterResource(id = R.drawable.close) else painterResource(id = R.drawable.text_to_speech), contentDescription = if (isTtsSessionActive) stringResource(R.string.content_desc_stop_tts) else stringResource(R.string.content_desc_start_tts), tint = if (isTtsSessionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            else -> Unit
                        }
                    }

                if (BuildConfig.FLAVOR != "oss") {
                    TooltipIconButton(
                        text = if (isBubbleZoomModeActive) stringResource(R.string.action_exit_smart_zoom) else stringResource(R.string.action_smart_comic_zoom),
                        description = stringResource(R.string.desc_toggle_smart_comic_zoom),
                        onClick = onToggleBubbleZoom
                    ) {
                        Icon(
                            painterResource(R.drawable.comic_bubble),
                            contentDescription = stringResource(R.string.content_desc_smart_comic_zoom),
                            tint = if (isBubbleZoomModeActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ttsErrorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
