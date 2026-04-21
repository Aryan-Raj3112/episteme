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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import com.aryan.reader.BuildConfig
import com.aryan.reader.FileType
import com.aryan.reader.R
import com.aryan.reader.SearchState
import com.aryan.reader.SearchTopBar
import com.aryan.reader.TooltipIconButton
import com.aryan.reader.epubreader.SystemUiMode
import kotlin.collections.isNotEmpty

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
    onNewTabClick: () -> Unit
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        val titleText = when {
                            isLoadingDocument -> stringResource(R.string.loading_pdf)
                            errorMessage != null -> stringResource(R.string.error_loading_pdf)
                            totalPages > 0 && pagerStatePageCount > 0 -> "Page ${currentPageForDisplay + 1} of $totalPages"
                            totalPages > 0 && pagerStatePageCount == 0 -> stringResource(R.string.loading_page)
                            else -> "PDF Viewer"
                        }
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 12.dp).weight(1f).testTag("PageNumberIndicator")
                        )

                        if (!hiddenTools.contains(PdfReaderTool.THEME.name)) {
                            TooltipIconButton(
                                text = stringResource(R.string.tooltip_theme),
                                description = stringResource(R.string.tooltip_theme_desc),
                                onClick = onShowThemePanel
                            ) {
                                Icon(painterResource(id = R.drawable.palette), contentDescription = stringResource(R.string.tooltip_theme_desc), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (!hiddenTools.contains(PdfReaderTool.LOCK_PANNING.name)) {
                            TooltipIconButton(
                                text = if (isScrollLocked) stringResource(R.string.tooltip_unlock_pan) else stringResource(R.string.tooltip_lock_pan),
                                description = if (isScrollLocked) stringResource(R.string.tooltip_unlock_pan_desc) else stringResource(R.string.tooltip_lock_pan_desc),
                                onClick = onToggleScrollLock
                            ) {
                                Icon(if (isScrollLocked) Icons.Default.Lock else Icons.Default.LockOpen, contentDescription = if (isScrollLocked) stringResource(R.string.tooltip_unlock_pan) else stringResource(R.string.tooltip_lock_pan), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (!hiddenTools.contains(PdfReaderTool.DICTIONARY.name)) {
                            TooltipIconButton(
                                text = stringResource(R.string.tooltip_dictionary),
                                description = stringResource(R.string.tooltip_dictionary_desc),
                                onClick = onShowDictionarySettings
                            ) {
                                Icon(painterResource(id = R.drawable.dictionary), contentDescription = "Dictionary Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (BuildConfig.DEBUG) {
                            TooltipIconButton(text = stringResource(R.string.pen_playground), onClick = onShowPenPlayground) {
                                Icon(Icons.Default.Star, contentDescription = "Open Pen Playground", tint = MaterialTheme.colorScheme.primary)
                            }
                            TooltipIconButton(text = stringResource(R.string.import_svg), onClick = onImportSvg) {
                                Icon(Icons.Default.Brush, contentDescription = stringResource(R.string.import_svg), tint = Color(0xFFE91E63))
                            }
                        }

                        Box {
                            var showMoreMenu by remember { mutableStateOf(false) }
                            TooltipIconButton(
                                text = stringResource(R.string.tooltip_more_options),
                                description = stringResource(R.string.tooltip_more_options_desc),
                                onClick = { showMoreMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.tooltip_more_options))
                            }

                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.title_customize_toolbar)) },
                                    onClick = { showMoreMenu = false; onShowCustomizeTools() },
                                    leadingIcon = { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_customize_toolbar), modifier = Modifier.size(20.dp)) }
                                )
                                HorizontalDivider()

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
                                Icon(Icons.Default.Add, contentDescription = "New Tab", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
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
fun PdfBottomBar(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    searchStateActive: Boolean,
    systemUiMode: SystemUiMode,
    navBarHeightDp: Dp,
    hiddenTools: Set<String>,
    isTtsPlayingOrLoading: Boolean,
    showAllTextHighlights: Boolean,
    isHighlightingLoading: Boolean,
    isEditMode: Boolean,
    isTtsSessionActive: Boolean,
    ttsErrorMessage: String?,
    onShowSlider: () -> Unit,
    onShowToc: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleHighlights: () -> Unit,
    onShowAiHub: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleTts: () -> Unit
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!hiddenTools.contains(PdfReaderTool.SLIDER.name)) {
                    TooltipIconButton(
                        text = stringResource(R.string.tooltip_slider),
                        description = stringResource(R.string.tooltip_slider_desc),
                        onClick = onShowSlider,
                        enabled = !isTtsPlayingOrLoading
                    ) {
                        Icon(painterResource(id = R.drawable.slider), contentDescription = stringResource(R.string.content_desc_navigate_slider))
                    }
                }
                if (!hiddenTools.contains(PdfReaderTool.TOC.name)) {
                    TooltipIconButton(
                        text = stringResource(R.string.tooltip_toc),
                        description = stringResource(R.string.tooltip_toc_desc),
                        onClick = onShowToc,
                        enabled = !isTtsPlayingOrLoading,
                        modifier = Modifier.testTag("TocButton")
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Table of Contents")
                    }
                }

                if (!hiddenTools.contains(PdfReaderTool.SEARCH.name)) {
                    TooltipIconButton(
                        text = stringResource(R.string.tooltip_search),
                        description = stringResource(R.string.tooltip_search_desc),
                        onClick = onSearchClick,
                        enabled = !isTtsPlayingOrLoading,
                        modifier = Modifier.testTag("SearchButton")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                    }
                }
                if (!hiddenTools.contains(PdfReaderTool.HIGHLIGHT_ALL.name)) {
                    TooltipIconButton(
                        text = if (showAllTextHighlights) stringResource(R.string.tooltip_highlights_off) else stringResource(R.string.tooltip_highlights),
                        description = if (showAllTextHighlights) stringResource(R.string.tooltip_highlights_off_desc) else stringResource(R.string.tooltip_highlights_desc),
                        onClick = onToggleHighlights
                    ) {
                        if (isHighlightingLoading) CircularProgressIndicator(Modifier.size(24.dp))
                        else Icon(painterResource(id = R.drawable.highlight_text), contentDescription = "Highlight all text", tint = if (showAllTextHighlights) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (BuildConfig.FLAVOR != "oss" && !hiddenTools.contains(PdfReaderTool.AI_FEATURES.name)) {
                    TooltipIconButton(
                        text = stringResource(R.string.tooltip_ai),
                        description = stringResource(R.string.tooltip_ai_desc),
                        onClick = onShowAiHub
                    ) {
                        Icon(painterResource(id = R.drawable.ai), contentDescription = stringResource(R.string.tooltip_ai))
                    }
                }

                if (!hiddenTools.contains(PdfReaderTool.EDIT_MODE.name)) {
                    TooltipIconButton(
                        text = if (isEditMode) stringResource(R.string.tooltip_edit_mode_exit) else stringResource(R.string.tooltip_edit_mode),
                        description = if (isEditMode) stringResource(R.string.tooltip_edit_mode_exit_desc) else stringResource(R.string.tooltip_edit_mode_desc),
                        onClick = onToggleEditMode
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Toggle Editing Mode", tint = if (isEditMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (!hiddenTools.contains(PdfReaderTool.TTS_CONTROLS.name)) {
                    TooltipIconButton(
                        text = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop) else stringResource(R.string.tooltip_tts_start),
                        description = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop_desc) else stringResource(R.string.tooltip_tts_start_desc),
                        onClick = onToggleTts
                    ) {
                        Icon(if (isTtsSessionActive) painterResource(id = R.drawable.close) else painterResource(id = R.drawable.text_to_speech), contentDescription = if (isTtsSessionActive) "Stop TTS" else "Start TTS", tint = if (isTtsSessionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                ttsErrorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 8.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}