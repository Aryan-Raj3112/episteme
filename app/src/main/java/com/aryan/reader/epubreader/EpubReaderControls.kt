package com.aryan.reader.epubreader

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.shared.ReaderSearchState as SearchState
import com.aryan.reader.SearchTopBar
import com.aryan.reader.TooltipIconButton
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.loadNativeVoice
import com.aryan.reader.readerSliderStepPage
import com.aryan.reader.shared.ui.ReaderMinimalSlider
import com.aryan.reader.tts.GEMINI_TTS_SPEAKERS
import com.aryan.reader.tts.ReaderTtsOverlaySize
import com.aryan.reader.tts.TtsPlaybackManager.TtsState
import com.aryan.reader.tts.formatReaderTtsChunkLabel
import kotlin.math.roundToInt

typealias ReaderTool = com.aryan.reader.shared.ReaderTool

val ReaderTool.titleRes: Int
    @StringRes get() = when (this) {
        ReaderTool.DICTIONARY -> R.string.tool_external_apps
        ReaderTool.THEME -> R.string.tooltip_theme_desc
        ReaderTool.BRIGHTNESS -> R.string.tool_brightness
        ReaderTool.SLIDER -> R.string.tool_navigation_slider
        ReaderTool.TOC -> R.string.tool_sidebar
        ReaderTool.FORMAT -> R.string.content_desc_text_formatting
        ReaderTool.SEARCH -> R.string.action_search
        ReaderTool.AI_FEATURES -> R.string.ai_features_title
        ReaderTool.TTS_CONTROLS -> R.string.tool_tts_controls
        ReaderTool.FILE_INFO -> R.string.file_information
        ReaderTool.READING_MODE -> R.string.tool_reading_mode
        ReaderTool.BOOKMARK -> R.string.content_desc_bookmark
        ReaderTool.TAP_TO_TURN -> R.string.menu_tap_to_turn_pages
        ReaderTool.VOLUME_SCROLL -> R.string.menu_volume_button_scrolling
        ReaderTool.PAGE_TURN_ANIM -> R.string.menu_realistic_page_turns
        ReaderTool.KEEP_SCREEN_ON -> R.string.menu_keep_screen_on
        ReaderTool.VISUAL_OPTIONS -> R.string.menu_visual_options
        ReaderTool.SCREEN_ORIENTATION -> R.string.menu_screen_orientation
        ReaderTool.AUTO_SCROLL -> R.string.menu_auto_scroll
        ReaderTool.TTS_SETTINGS -> R.string.menu_tts_settings
        ReaderTool.TTS_REPLACEMENTS -> R.string.menu_tts_word_replacements
        ReaderTool.BOOK_REPLACEMENTS -> R.string.menu_book_word_replacements
    }

internal val epubToolbarTools = setOf(
    ReaderTool.DICTIONARY,
    ReaderTool.THEME,
    ReaderTool.BRIGHTNESS,
    ReaderTool.SLIDER,
    ReaderTool.TOC,
    ReaderTool.FORMAT,
    ReaderTool.SEARCH,
    ReaderTool.AI_FEATURES,
    ReaderTool.TTS_CONTROLS,
    ReaderTool.SCREEN_ORIENTATION
)

internal typealias EpubOverflowMenuSection = com.aryan.reader.shared.EpubOverflowMenuSection

internal fun epubOverflowMenuSections(
    hiddenTools: Set<String>,
    hasHiddenToolbarTools: Boolean,
    hasToggleReflow: Boolean,
    hasDeleteReflow: Boolean,
    hasFileInfo: Boolean = true
): List<EpubOverflowMenuSection> = com.aryan.reader.shared.epubOverflowMenuSections(
    hiddenTools = hiddenTools,
    hasHiddenToolbarTools = hasHiddenToolbarTools,
    hasToggleReflow = hasToggleReflow,
    hasDeleteReflow = hasDeleteReflow,
    hasFileInfo = hasFileInfo,
)

internal fun defaultReaderHiddenTools(): Set<String> = setOf(
    ReaderTool.SCREEN_ORIENTATION.name,
    ReaderTool.BRIGHTNESS.name
)

internal fun defaultReaderToolOrder(): List<ReaderTool> = ReaderTool.entries.toList()

internal fun defaultReaderBottomTools(): Set<String> {
    return ReaderTool.entries.filter { it.category == "Bottom Bar" }.map { it.name }.toSet()
}

@Composable
fun EpubReaderTopBar(
    isVisible: Boolean,
    searchState: SearchState,
    bookTitle: String,
    currentRenderMode: RenderMode,
    isBookmarked: Boolean,
    isTtsActive: Boolean,
    isSliderActive: Boolean,
    tapToNavigateEnabled: Boolean,
    volumeScrollEnabled: Boolean,
    isPageTurnAnimationEnabled: Boolean,
    isRightToLeftPagination: Boolean,
    useNativeVerticalRenderer: Boolean,
    onNavigateBack: () -> Unit,
    isKeepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onCloseSearch: () -> Unit,
    onChangeRenderMode: (RenderMode) -> Unit,
    onUseNativeVerticalRendererChange: (Boolean) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleTapToNavigate: (Boolean) -> Unit,
    onToggleVolumeScroll: (Boolean) -> Unit,
    onTogglePageTurnAnimation: (Boolean) -> Unit,
    onSetRightToLeftPagination: (Boolean) -> Unit,
    onStartAutoScroll: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenTtsReplacements: () -> Unit,
    onOpenBookReplacements: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenBrightness: () -> Unit,
    onOpenVisualOptions: () -> Unit,
    onOpenScreenOrientation: () -> Unit,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenFileInfo: () -> Unit,
    searchFocusRequester: androidx.compose.ui.focus.FocusRequester,
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    onCustomizeTools: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleReflow: (() -> Unit)? = null,
    onDeleteReflow: (() -> Unit)? = null,
) {
    com.aryan.reader.shared.ui.SharedReaderBarVisibility(
        visible = isVisible,
        edge = com.aryan.reader.shared.ui.SharedReaderBarEdge.TOP,
        modifier = modifier
    ) {
        com.aryan.reader.shared.ui.SharedReaderToolbarSurface(height = 55.dp) {
            com.aryan.reader.shared.ui.SharedEpubTopToolbarRow {
                if (searchState.isSearchActive) {
                    SearchTopBar(
                        searchState = searchState,
                        focusRequester = searchFocusRequester,
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
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = bookTitle.take(40) + if (bookTitle.length > 40) "..." else "",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    com.aryan.reader.shared.ui.sharedEpubToolbarTools(
                        toolOrder = toolOrder,
                        toolbarTools = epubToolbarTools,
                        hiddenToolNamesOrIds = hiddenTools,
                        bottomToolNamesOrIds = bottomTools,
                        placement = com.aryan.reader.shared.ui.SharedReaderToolbarPlacement.TOP,
                    )
                        .forEach { tool ->
                            when (tool) {
                                ReaderTool.DICTIONARY -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_dictionary),
                                    description = stringResource(R.string.tooltip_dictionary_desc),
                                    onClick = onOpenDictionarySettings
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.dictionary),
                                        contentDescription = stringResource(R.string.content_desc_dictionary_settings)
                                    )
                                }
                                ReaderTool.THEME -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_theme),
                                    description = stringResource(R.string.tooltip_theme_desc),
                                    onClick = onOpenThemeSettings
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.palette), contentDescription = stringResource(R.string.tooltip_theme_desc))
                                }
                                ReaderTool.BRIGHTNESS -> TooltipIconButton(
                                    text = stringResource(R.string.reader_brightness_title),
                                    description = stringResource(R.string.reader_brightness_system_desc),
                                    onClick = onOpenBrightness
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.contrast), contentDescription = stringResource(R.string.reader_brightness_title))
                                }
                                ReaderTool.SLIDER -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_slider),
                                    description = stringResource(R.string.tooltip_slider_desc),
                                    onClick = onOpenSlider
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.slider),
                                        contentDescription = stringResource(R.string.content_desc_navigate_slider),
                                        tint = if (isSliderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                ReaderTool.TOC -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_toc),
                                    description = stringResource(R.string.tooltip_toc_desc),
                                    onClick = onOpenDrawer
                                ) {
                                    Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.content_desc_chapters_menu))
                                }
                                ReaderTool.FORMAT -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_format),
                                    description = stringResource(R.string.tooltip_format_desc),
                                    onClick = onToggleFormat
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.format_size), contentDescription = stringResource(R.string.content_desc_text_formatting))
                                }
                                ReaderTool.SEARCH -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_search),
                                    description = stringResource(R.string.tooltip_search_desc),
                                    onClick = onToggleSearch
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.tooltip_search))
                                }
                                ReaderTool.AI_FEATURES -> if (areReaderAiFeaturesEnabled(LocalContext.current)) {
                                    TooltipIconButton(
                                        text = stringResource(R.string.tooltip_ai),
                                        description = stringResource(R.string.tooltip_ai_desc),
                                        onClick = onOpenAiHub
                                    ) {
                                        Icon(painter = painterResource(id = R.drawable.ai), contentDescription = stringResource(R.string.ai_features_title))
                                    }
                                }
                                ReaderTool.TTS_CONTROLS -> TooltipIconButton(
                                    text = if (isTtsActive) stringResource(R.string.tooltip_tts_stop) else stringResource(R.string.tooltip_tts_start),
                                    description = if (isTtsActive) stringResource(R.string.tooltip_tts_stop_desc) else stringResource(R.string.tooltip_tts_start_desc),
                                    onClick = onToggleTts
                                ) {
                                    Icon(
                                        painter = if (isTtsActive) painterResource(id = R.drawable.close) else painterResource(id = R.drawable.text_to_speech),
                                        contentDescription = if (isTtsActive) stringResource(R.string.content_desc_stop_tts) else stringResource(R.string.content_desc_start_tts),
                                        tint = if (isTtsActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                ReaderTool.SCREEN_ORIENTATION -> TooltipIconButton(
                                    text = stringResource(R.string.menu_screen_orientation),
                                    description = stringResource(R.string.visual_options_screen_orientation_desc),
                                    onClick = onOpenScreenOrientation
                                ) {
                                    Icon(
                                        Icons.Default.ScreenRotation,
                                        contentDescription = stringResource(R.string.menu_screen_orientation),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                else -> Unit
                            }
                        }
                    Box {
                        val overflowMenuState = com.aryan.reader.shared.ui.rememberSharedReaderOverflowMenuState()
                        var showMoreMenu by overflowMenuState.menuExpanded
                        var showHiddenToolsExpanded by overflowMenuState.hiddenToolsExpanded
                        var showReadingModeExpanded by overflowMenuState.readingModeExpanded
                        var showTtsSettingsExpanded by overflowMenuState.ttsSettingsExpanded
                        TooltipIconButton(
                            text = stringResource(R.string.tooltip_more_options),
                            description = stringResource(R.string.tooltip_more_options_desc),
                            onClick = overflowMenuState::open
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more_options))
                        }

                        com.aryan.reader.shared.ui.SharedReaderOverflowMenu(
                            state = overflowMenuState,
                        ) {
                            val hiddenToolbarTools = toolOrder.filter { it in epubToolbarTools && hiddenTools.contains(it.name) }
                            val showTtsVoiceSettings = !hiddenTools.contains(ReaderTool.TTS_SETTINGS.name)
                            val showTtsReplacements = !hiddenTools.contains(ReaderTool.TTS_REPLACEMENTS.name)
                            com.aryan.reader.shared.ui.SharedReaderOverflowSectionList(
                                sections = epubOverflowMenuSections(
                                    hiddenTools = hiddenTools,
                                    hasHiddenToolbarTools = hiddenToolbarTools.isNotEmpty(),
                                    hasToggleReflow = onToggleReflow != null,
                                    hasDeleteReflow = onDeleteReflow != null,
                                ),
                            ) { section ->
                                when (section) {
                                    EpubOverflowMenuSection.CUSTOMIZE_TOOLBAR -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.title_customize_toolbar)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onCustomizeTools()
                                            },
                                            leadingIcon = {
                                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(20.dp))
                                            }
                                        )
                                    }
                                    EpubOverflowMenuSection.HIDDEN_TOOLS -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.toolbar_hidden_tools_menu)) },
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
                                                HiddenEpubToolMenuItem(
                                                    tool = tool,
                                                    isSliderActive = isSliderActive,
                                                    showMoreMenu = {
                                                        showHiddenToolsExpanded = false
                                                        showMoreMenu = false
                                                    },
                                                    onOpenDictionarySettings = onOpenDictionarySettings,
                                                    onOpenThemeSettings = onOpenThemeSettings,
                                                    onOpenBrightness = onOpenBrightness,
                                                    onOpenSlider = onOpenSlider,
                                                    onOpenDrawer = onOpenDrawer,
                                                    onToggleFormat = onToggleFormat,
                                                    onToggleSearch = onToggleSearch,
                                                    onOpenAiHub = onOpenAiHub,
                                                    onToggleTts = onToggleTts,
                                                    onOpenScreenOrientation = onOpenScreenOrientation
                                                )
                                            }
                                        }
                                    }
                                    EpubOverflowMenuSection.FILE_INFO -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.file_information)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onOpenFileInfo()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        )
                                    }
                                    EpubOverflowMenuSection.VIEW_ORIGINAL_PDF -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_view_original_pdf)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onToggleReflow?.invoke()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.picture_as_pdf),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        )
                                    }
                                    EpubOverflowMenuSection.DELETE_TEXT_VIEW -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_delete_text_view)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onDeleteReflow?.invoke()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            },
                                            colors = androidx.compose.material3.MenuDefaults.itemColors(
                                                textColor = MaterialTheme.colorScheme.error
                                            )
                                        )
                                    }
                                    EpubOverflowMenuSection.READING_MODE -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_change_reading_mode)) },
                                            onClick = { showReadingModeExpanded = !showReadingModeExpanded },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.rotate(if (showReadingModeExpanded) 180f else 0f)
                                                )
                                            }
                                        )
                                        if (showReadingModeExpanded) {
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_reading_mode_vertical_webview)) },
                                                enabled = !isTtsActive,
                                                onClick = {
                                                    onUseNativeVerticalRendererChange(false)
                                                    showMoreMenu = false
                                                    onChangeRenderMode(RenderMode.VERTICAL_SCROLL)
                                                },
                                                trailingIcon = {
                                                    if (currentRenderMode == RenderMode.VERTICAL_SCROLL && !useNativeVerticalRenderer) Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(R.string.content_desc_selected)
                                                    )
                                                })
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_reading_mode_vertical_native)) },
                                                enabled = !isTtsActive,
                                                onClick = {
                                                    onUseNativeVerticalRendererChange(true)
                                                    showMoreMenu = false
                                                    onChangeRenderMode(RenderMode.VERTICAL_SCROLL)
                                                },
                                                trailingIcon = {
                                                    if (currentRenderMode == RenderMode.VERTICAL_SCROLL && useNativeVerticalRenderer) Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = stringResource(R.string.content_desc_selected)
                                                    )
                                                })
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_reading_mode_paginated)) },
                                                enabled = !isTtsActive,
                                                onClick = {
                                                    onSetRightToLeftPagination(false)
                                                    showMoreMenu = false
                                                    onChangeRenderMode(RenderMode.PAGINATED)
                                                },
                                                trailingIcon = {
                                                    if (currentRenderMode == RenderMode.PAGINATED && !isRightToLeftPagination) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = stringResource(R.string.content_desc_selected)
                                                        )
                                                    }
                                                })
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.menu_right_to_left_pagination)) },
                                                enabled = !isTtsActive,
                                                onClick = {
                                                    onSetRightToLeftPagination(true)
                                                    showMoreMenu = false
                                                    onChangeRenderMode(RenderMode.PAGINATED)
                                                },
                                                trailingIcon = {
                                                    if (currentRenderMode == RenderMode.PAGINATED && isRightToLeftPagination) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            contentDescription = stringResource(R.string.content_desc_selected)
                                                        )
                                                    }
                                                })
                                        }
                                    }
                                    EpubOverflowMenuSection.BOOKMARK -> {
                                        DropdownMenuItem(text = {
                                            Text(
                                                if (isBookmarked) stringResource(R.string.menu_remove_bookmark) else stringResource(
                                                    R.string.menu_bookmark_this_page
                                                )
                                            )
                                        }, onClick = {
                                            showMoreMenu = false
                                            onToggleBookmark()
                                        })
                                    }
                                    EpubOverflowMenuSection.TAP_TO_TURN -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_tap_to_turn_pages)) },
                                            enabled = currentRenderMode == RenderMode.PAGINATED,
                                            onClick = {
                                                onToggleTapToNavigate(!tapToNavigateEnabled)
                                                showMoreMenu = false
                                            },
                                            trailingIcon = {
                                                if (tapToNavigateEnabled) Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.content_desc_enabled)
                                                )
                                            })
                                    }
                                    EpubOverflowMenuSection.VOLUME_SCROLL -> {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    if (currentRenderMode == RenderMode.VERTICAL_SCROLL) stringResource(
                                                        R.string.menu_volume_button_scrolling
                                                    )
                                                    else stringResource(R.string.menu_volume_button_page_turn)
                                                )
                                            },
                                            enabled = true,
                                            onClick = {
                                                onToggleVolumeScroll(!volumeScrollEnabled)
                                                showMoreMenu = false
                                            },
                                            trailingIcon = {
                                                if (volumeScrollEnabled) Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.content_desc_enabled)
                                                )
                                            })
                                    }
                                    EpubOverflowMenuSection.PAGE_TURN_ANIM -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_realistic_page_turns)) },
                                            enabled = currentRenderMode == RenderMode.PAGINATED,
                                            onClick = {
                                                onTogglePageTurnAnimation(!isPageTurnAnimationEnabled)
                                                showMoreMenu = false
                                            },
                                            trailingIcon = {
                                                if (isPageTurnAnimationEnabled) Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.content_desc_enabled)
                                                )
                                            })
                                    }
                                    EpubOverflowMenuSection.KEEP_SCREEN_ON -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_keep_screen_on)) },
                                            onClick = {
                                                onToggleKeepScreenOn(!isKeepScreenOn)
                                                showMoreMenu = false
                                            },
                                            trailingIcon = {
                                                if (isKeepScreenOn) Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = stringResource(R.string.content_desc_enabled)
                                                )
                                            })
                                    }
                                    EpubOverflowMenuSection.VISUAL_OPTIONS -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_visual_options)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onOpenVisualOptions()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.Visibility,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            })
                                    }
                                    EpubOverflowMenuSection.AUTO_SCROLL -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_auto_scroll)) },
                                            enabled = !isTtsActive && currentRenderMode == RenderMode.VERTICAL_SCROLL,
                                            onClick = {
                                                showMoreMenu = false
                                                onStartAutoScroll()
                                            })
                                    }
                                    EpubOverflowMenuSection.BOOK_REPLACEMENTS -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_book_word_replacements)) },
                                            onClick = {
                                                showMoreMenu = false
                                                onOpenBookReplacements()
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.text_fields),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        )
                                    }
                                    EpubOverflowMenuSection.TTS_SETTINGS -> {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.menu_tts_settings)) },
                                            onClick = { showTtsSettingsExpanded = !showTtsSettingsExpanded },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Default.GraphicEq,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    Icons.Default.ArrowDropDown,
                                                    contentDescription = null,
                                                    modifier = Modifier.rotate(if (showTtsSettingsExpanded) 180f else 0f)
                                                )
                                            }
                                        )
                                        if (showTtsSettingsExpanded) {
                                            if (showTtsVoiceSettings) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_tts_voice_settings)) },
                                                    enabled = !isTtsActive,
                                                    onClick = {
                                                        showMoreMenu = false
                                                        onOpenTtsSettings()
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.GraphicEq,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                )
                                            }
                                            if (showTtsReplacements) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.menu_tts_word_replacements)) },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        onOpenTtsReplacements()
                                                    },
                                                    leadingIcon = {
                                                        Icon(
                                                            Icons.Default.GraphicEq,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpubJumpHistoryBar(
    modifier: Modifier = Modifier,
    showStandardBars: Boolean,
    searchStateActive: Boolean,
    backLabel: String?,
    forwardLabel: String?,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onClear: () -> Unit
) {
    AnimatedVisibility(
        visible = showStandardBars && !searchStateActive && (backLabel != null || forwardLabel != null),
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
                    enabled = backLabel != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_desc_jump_back),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = backLabel.orEmpty(),
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
                    enabled = forwardLabel != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = forwardLabel.orEmpty(),
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EpubReaderBottomBar(
    isVisible: Boolean,
    currentRenderMode: RenderMode,
    isTtsSessionActive: Boolean,
    ttsState: TtsState,
    isProUser: Boolean,
    currentTtsMode: com.aryan.reader.tts.TtsPlaybackManager.TtsMode,
    isSliderActive: Boolean,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenBrightness: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenScreenOrientation: () -> Unit,
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    modifier: Modifier = Modifier
) {
    com.aryan.reader.shared.ui.SharedReaderBarVisibility(
        visible = isVisible,
        edge = com.aryan.reader.shared.ui.SharedReaderBarEdge.BOTTOM,
        modifier = modifier
    ) {
        com.aryan.reader.shared.ui.SharedReaderToolbarSurface(height = 45.dp) {
            com.aryan.reader.shared.ui.SharedEpubBottomToolbarRow {
                com.aryan.reader.shared.ui.sharedEpubToolbarTools(
                    toolOrder = toolOrder,
                    toolbarTools = epubToolbarTools,
                    hiddenToolNamesOrIds = hiddenTools,
                    bottomToolNamesOrIds = bottomTools,
                    placement = com.aryan.reader.shared.ui.SharedReaderToolbarPlacement.BOTTOM,
                )
                    .forEach { tool ->
                        when (tool) {
                            ReaderTool.DICTIONARY -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_dictionary),
                                description = stringResource(R.string.tooltip_dictionary_desc),
                                onClick = onOpenDictionarySettings
                            ) {
                                Icon(painter = painterResource(id = R.drawable.dictionary), contentDescription = stringResource(R.string.content_desc_dictionary_settings))
                            }
                            ReaderTool.THEME -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_theme),
                                description = stringResource(R.string.tooltip_theme_desc),
                                onClick = onOpenThemeSettings
                            ) {
                                Icon(painter = painterResource(id = R.drawable.palette), contentDescription = stringResource(R.string.tooltip_theme_desc))
                            }
                            ReaderTool.BRIGHTNESS -> TooltipIconButton(
                                text = stringResource(R.string.reader_brightness_title),
                                description = stringResource(R.string.reader_brightness_system_desc),
                                onClick = onOpenBrightness
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.contrast),
                                    contentDescription = stringResource(R.string.reader_brightness_title)
                                )
                            }
                            ReaderTool.SLIDER -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_slider),
                                description = stringResource(R.string.tooltip_slider_desc),
                                onClick = onOpenSlider
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.slider),
                                    contentDescription = stringResource(R.string.content_desc_navigate_slider),
                                    tint = if (isSliderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            ReaderTool.TOC -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_toc),
                                description = stringResource(R.string.tooltip_toc_desc),
                                onClick = onOpenDrawer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = stringResource(R.string.content_desc_chapters_menu)
                                )
                            }
                            ReaderTool.FORMAT -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_format),
                                description = stringResource(R.string.tooltip_format_desc),
                                onClick = onToggleFormat
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.format_size),
                                    contentDescription = stringResource(R.string.content_desc_text_formatting)
                                )
                            }
                            ReaderTool.SEARCH -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_search),
                                description = stringResource(R.string.tooltip_search_desc),
                                onClick = onToggleSearch
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.tooltip_search)
                                )
                            }
                            ReaderTool.AI_FEATURES -> if (areReaderAiFeaturesEnabled(LocalContext.current)) {
                                TooltipIconButton(
                                    text = stringResource(R.string.tooltip_ai),
                                    description = stringResource(R.string.tooltip_ai_desc),
                                    onClick = onOpenAiHub
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ai),
                                        contentDescription = stringResource(R.string.ai_features_title)
                                    )
                                }
                            }
                            ReaderTool.TTS_CONTROLS -> TooltipIconButton(
                                text = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop)
                                else stringResource(R.string.tooltip_tts_start),
                                description = if (isTtsSessionActive) stringResource(R.string.tooltip_tts_stop_desc)
                                else stringResource(R.string.tooltip_tts_start_desc),
                                onClick = onToggleTts
                            ) {
                                Icon(
                                    painter = if (isTtsSessionActive) painterResource(id = R.drawable.close) else painterResource(
                                        id = R.drawable.text_to_speech
                                    ),
                                    contentDescription = if (isTtsSessionActive) stringResource(R.string.content_desc_stop_tts) else stringResource(
                                        R.string.content_desc_start_tts
                                    ),
                                    tint = if (isTtsSessionActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            ReaderTool.SCREEN_ORIENTATION -> TooltipIconButton(
                                text = stringResource(R.string.menu_screen_orientation),
                                description = stringResource(R.string.visual_options_screen_orientation_desc),
                                onClick = onOpenScreenOrientation
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ScreenRotation,
                                    contentDescription = stringResource(R.string.menu_screen_orientation)
                                )
                            }
                            else -> Unit
                        }
                    }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun EpubReaderPageSlider(
    isVisible: Boolean,
    totalPages: Int,
    sliderCurrentPage: Float,
    sliderStartPage: Int,
    onScrub: (Float) -> Unit,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.Unspecified,
    inactiveColor: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified
) {
    val effectiveActiveColor = if (activeColor == Color.Unspecified) {
        MaterialTheme.colorScheme.primary
    } else {
        activeColor
    }
    val effectiveInactiveColor = if (inactiveColor == Color.Unspecified) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        inactiveColor
    }
    val effectiveContentColor = if (contentColor == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface
    } else {
        contentColor
    }
    val maxPage = totalPages.coerceAtLeast(1)
    val currentPage = sliderCurrentPage.roundToInt().coerceIn(1, maxPage)

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        onJumpToPage(
                            readerSliderStepPage(
                                currentPage = currentPage,
                                delta = -1,
                                minPage = 1,
                                maxPage = maxPage
                            )
                        )
                    },
                    enabled = currentPage > 1,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateBefore,
                        contentDescription = stringResource(R.string.desktop_previous_page),
                        tint = effectiveContentColor.copy(alpha = if (currentPage > 1) 0.9f else 0.32f)
                    )
                }

                ReaderMinimalSlider(
                    value = sliderCurrentPage.coerceIn(1f, maxPage.toFloat()),
                    onValueChange = onScrub,
                    valueRange = 1f..maxPage.toFloat(),
                    enabled = maxPage > 1,
                    activeColor = effectiveActiveColor,
                    inactiveColor = effectiveInactiveColor,
                    thumbColor = effectiveActiveColor,
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                )

                IconButton(
                    onClick = {
                        onJumpToPage(
                            readerSliderStepPage(
                                currentPage = currentPage,
                                delta = 1,
                                minPage = 1,
                                maxPage = maxPage
                            )
                        )
                    },
                    enabled = currentPage < maxPage,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.NavigateNext,
                        contentDescription = stringResource(R.string.desktop_next_page),
                        tint = effectiveContentColor.copy(alpha = if (currentPage < maxPage) 0.9f else 0.32f)
                    )
                }
            }
        }
    }
}

// --- Helpers moved from Screen ---

@Composable
fun PageScrubbingAnimation(currentPage: Int, totalPages: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.slider),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Page $currentPage of $totalPages",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun SpeedDropdown(
    label: String,
    currentValue: Float,
    options: List<Float>,
    onValueChange: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Text(
            text = "$label: ${currentValue}x",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { expanded = true }
                .padding(4.dp)
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text("${opt}x") },
                    onClick = {
                        onValueChange(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoScrollControls(
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    speed: Float,
    minSpeed: Float,
    maxSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onMinSpeedChange: (Float) -> Unit,
    onMaxSpeedChange: (Float) -> Unit,
    onClose: () -> Unit,
    isCollapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    isMusicianMode: Boolean,
    onMusicianModeToggle: () -> Unit,
    useSlider: Boolean,
    onInputModeToggle: () -> Unit,
    isLocalMode: Boolean,
    onLocalModeToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isTempPaused: Boolean = false,
    onScrollToTop: (() -> Unit)? = null
) {
    val backgroundAlpha = 0.6f

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = backgroundAlpha),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier
            .widthIn(max = 400.dp)
            .animateContentSize()
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "AutoScrollUnified"
        ) { collapsed ->
            if (collapsed) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onCollapseChange(false) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = stringResource(R.string.content_desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = onPlayPauseToggle,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.tooltip_tts_pause) else stringResource(R.string.content_desc_start_playback),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (isTempPaused && isPlaying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(36.dp),
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Top Row: Label & Tools
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            var showModeMenu by remember { mutableStateOf(false) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showModeMenu = true }
                                    .padding(4.dp)
                            ) {
                                Text(
                                    text = if (isLocalMode) stringResource(R.string.auto_scroll_local_speed) else stringResource(R.string.auto_scroll_global_speed),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = stringResource(R.string.content_desc_select_mode),
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModeMenu,
                                onDismissRequest = { showModeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.auto_scroll_global_speed), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(stringResource(R.string.auto_scroll_applies_all_files), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        onLocalModeToggle(false)
                                        showModeMenu = false
                                    },
                                    trailingIcon = {
                                        if (!isLocalMode) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.auto_scroll_local_speed), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                            Text(stringResource(R.string.auto_scroll_saved_for_file), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        onLocalModeToggle(true)
                                        showModeMenu = false
                                    },
                                    trailingIcon = {
                                        if (isLocalMode) Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (onScrollToTop != null) {
                                IconButton(
                                    onClick = onScrollToTop,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowUpward,
                                        contentDescription = stringResource(R.string.action_scroll_to_top),
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            IconButton(
                                onClick = onMusicianModeToggle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.music_note),
                                    contentDescription = if (isMusicianMode) stringResource(R.string.content_desc_disable_musician_mode) else stringResource(R.string.content_desc_enable_musician_mode),
                                    modifier = Modifier.size(18.dp),
                                    tint = if (isMusicianMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onInputModeToggle,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapHoriz,
                                    contentDescription = stringResource(R.string.content_desc_swap_controls),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onCollapseChange(true) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = stringResource(R.string.content_desc_collapse),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.action_close),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Bottom Row: Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Play/Pause
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            FilledIconButton(
                                onClick = onPlayPauseToggle,
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) stringResource(R.string.tooltip_tts_pause) else stringResource(R.string.content_desc_start_playback),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (isTempPaused && isPlaying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    strokeWidth = 3.dp
                                )
                            }
                        }

                        // Speed Controls
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val speedOptions = listOf(0.1f, 0.5f, 1f, 1.5f, 2f, 3f, 4f, 5f, 6f, 7f, 8f, 9f, 10f)
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    SpeedDropdown(
                                        label = stringResource(R.string.label_min),
                                        currentValue = minSpeed,
                                        options = speedOptions,
                                        onValueChange = onMinSpeedChange
                                    )
                                    SpeedDropdown(
                                        label = stringResource(R.string.label_max),
                                        currentValue = maxSpeed,
                                        options = speedOptions,
                                        onValueChange = onMaxSpeedChange
                                    )
                                }
                                Spacer(Modifier.height(4.dp))

                                val safeMax = maxSpeed.coerceAtLeast(minSpeed + 0.1f)

                                if (useSlider) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "%.1fx".format(speed),
                                            style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                            modifier = Modifier.width(45.dp),
                                            textAlign = TextAlign.End
                                        )
                                        val steps = ((safeMax - minSpeed) / 0.1f).roundToInt() - 1

                                        Slider(
                                            value = speed,
                                            onValueChange = { onSpeedChange((it * 10f).roundToInt() / 10f) },
                                            valueRange = minSpeed..safeMax,
                                            steps = if (steps > 0) steps else 0,
                                            modifier = Modifier.weight(1f),
                                            thumb = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                                )
                                            },
                                            track = { sliderState ->
                                                val fraction = (sliderState.value - sliderState.valueRange.start) /
                                                        (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(4.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
                                                            RoundedCornerShape(2.dp)
                                                        ),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth(fraction)
                                                            .height(4.dp)
                                                            .background(
                                                                MaterialTheme.colorScheme.primary,
                                                                RoundedCornerShape(2.dp)
                                                            )
                                                    )
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.height(48.dp).fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            IconButton(
                                                onClick = { onSpeedChange((speed - 0.1f).coerceAtLeast(minSpeed)) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Default.Remove, stringResource(R.string.content_desc_slower))
                                            }
                                            Text(
                                                text = "%.1fx".format(speed),
                                                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                                textAlign = TextAlign.Center
                                            )
                                            IconButton(
                                                onClick = { onSpeedChange((speed + 0.1f).coerceAtMost(safeMax)) },
                                                modifier = Modifier.size(48.dp)
                                            ) {
                                                Icon(Icons.Default.Add, stringResource(R.string.content_desc_faster))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeToolsSheet(
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    onUpdate: (Set<String>) -> Unit,
    onOrderUpdate: (List<ReaderTool>) -> Unit,
    onPlacementUpdate: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    com.aryan.reader.shared.ui.SharedEpubToolbarCustomizationDialog(
        hiddenToolIds = hiddenTools.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id },
        toolOrder = toolOrder,
        bottomToolIds = bottomTools.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.id },
        toolbarTools = epubToolbarTools,
        availableTools = ReaderTool.entries.toSet(),
        labels = com.aryan.reader.shared.ui.SharedEpubToolbarCustomizationLabels(
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
            onUpdate(ids.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.name })
        },
        onToolOrderUpdate = onOrderUpdate,
        onBottomToolsUpdate = { ids ->
            onPlacementUpdate(ids.mapNotNullTo(mutableSetOf()) { ReaderTool.fromId(it)?.name })
        },
        onDismiss = onDismiss,
        toolIcon = { ToolPreviewIcon(it) },
    )
}

@Composable
private fun ToolPreviewIcon(tool: ReaderTool, isSliderActive: Boolean = false) {
    val title = stringResource(tool.titleRes)
    when (tool) {
        ReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.BRIGHTNESS -> Icon(painterResource(id = R.drawable.contrast), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.SLIDER -> Icon(
            painterResource(id = R.drawable.slider),
            contentDescription = title,
            modifier = Modifier.size(20.dp),
            tint = if (isSliderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        ReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.FORMAT -> Icon(painterResource(id = R.drawable.format_size), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.TTS_CONTROLS -> Icon(painterResource(id = R.drawable.text_to_speech), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.BOOK_REPLACEMENTS -> Icon(painterResource(id = R.drawable.text_fields), contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.FILE_INFO -> Icon(Icons.Default.Info, contentDescription = title, modifier = Modifier.size(20.dp))
        ReaderTool.SCREEN_ORIENTATION -> Icon(Icons.Default.ScreenRotation, contentDescription = title, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.MoreVert, contentDescription = title, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HiddenEpubToolMenuItem(
    tool: ReaderTool,
    isSliderActive: Boolean,
    showMoreMenu: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenBrightness: () -> Unit,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onToggleTts: () -> Unit,
    onOpenScreenOrientation: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(stringResource(tool.titleRes)) },
        onClick = {
            showMoreMenu()
            when (tool) {
                ReaderTool.DICTIONARY -> onOpenDictionarySettings()
                ReaderTool.THEME -> onOpenThemeSettings()
                ReaderTool.BRIGHTNESS -> onOpenBrightness()
                ReaderTool.SLIDER -> onOpenSlider()
                ReaderTool.TOC -> onOpenDrawer()
                ReaderTool.FORMAT -> onToggleFormat()
                ReaderTool.SEARCH -> onToggleSearch()
                ReaderTool.AI_FEATURES -> onOpenAiHub()
                ReaderTool.TTS_CONTROLS -> onToggleTts()
                ReaderTool.SCREEN_ORIENTATION -> onOpenScreenOrientation()
                else -> Unit
            }
        },
        leadingIcon = { ToolPreviewIcon(tool, isSliderActive = isSliderActive) },
        trailingIcon = if (tool == ReaderTool.SLIDER && isSliderActive) {
            {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.content_desc_enabled))
            }
        } else null
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsOverlayControls(
    ttsController: com.aryan.reader.tts.TtsController,
    ttsState: TtsState,
    currentTtsMode: com.aryan.reader.tts.TtsPlaybackManager.TtsMode,
    overlaySize: ReaderTtsOverlaySize,
    onOverlaySizeChange: (ReaderTtsOverlaySize) -> Unit,
    onLocateCurrentChunk: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    credits: Int
) {
    val context = LocalContext.current
    var rate by remember { mutableFloatStateOf(loadTtsSpeechRate(context)) }
    var pitch by remember { mutableFloatStateOf(loadTtsPitch(context)) }

    val activeMode = try { com.aryan.reader.tts.TtsPlaybackManager.TtsMode.valueOf(ttsState.ttsMode) } catch(_: Exception) { com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD }
    val progressPercent = ttsState.bookProgressPercent
    val cleanChapterTitle = remember(ttsState.chapterTitle) {
        ttsState.chapterTitle
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.joinToString(" - ")
            ?.takeIf { it.isNotBlank() }
    }
    val chapterLabel = remember(ttsState.chapterIndex, ttsState.totalChapters, cleanChapterTitle) {
        val chapterNumber = ttsState.chapterIndex?.plus(1)
        val totalChapters = ttsState.totalChapters
        when {
            chapterNumber != null && totalChapters != null -> buildString {
                append("Chapter $chapterNumber of $totalChapters")
                if (!cleanChapterTitle.isNullOrBlank()) append(": $cleanChapterTitle")
            }
            chapterNumber != null -> buildString {
                append("Chapter $chapterNumber")
                if (!cleanChapterTitle.isNullOrBlank()) append(": $cleanChapterTitle")
            }
            !cleanChapterTitle.isNullOrBlank() -> cleanChapterTitle
            else -> null
        }
    }
    val chunkLabel = remember(ttsState.currentChunkIndex, ttsState.totalChunks) {
        formatReaderTtsChunkLabel(ttsState.currentChunkIndex, ttsState.totalChunks)
    }
    val miniBarTitle = ttsState.bookTitle
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.action_read_aloud)
    val miniBarSubtitle = remember(chapterLabel, chunkLabel, progressPercent, miniBarTitle) {
        listOfNotNull(
            chunkLabel,
            progressPercent?.let { "$it%" },
            chapterLabel?.takeIf { it != miniBarTitle }
        ).joinToString(" - ")
    }
    val canSkipPreviousChunk = !ttsState.isLoading &&
        ttsState.currentChunkIndex > 0 &&
        ttsState.totalChunks > 0
    val canSkipNextChunk = !ttsState.isLoading &&
        ttsState.currentChunkIndex >= 0 &&
        ttsState.currentChunkIndex < ttsState.totalChunks - 1

    val saveAndApply = {
        saveTtsSpeechRate(context, rate)
        saveTtsPitch(context, pitch)
        ttsController.setPlaybackParameters(rate, pitch)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier
            .widthIn(max = if (overlaySize == ReaderTtsOverlaySize.MEDIUM) 560.dp else 400.dp)
            .animateContentSize()
    ) {
        AnimatedContent(
            targetState = overlaySize,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "TtsOverlayUnified"
        ) { size ->
            if (size == ReaderTtsOverlaySize.SMALL) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.LARGE) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowUp,
                            stringResource(R.string.content_desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.MEDIUM) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.KeyboardArrowLeft,
                            stringResource(R.string.content_desc_expand),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = { if (ttsState.isPlaying) ttsController.pause() else ttsController.resume() },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                stringResource(R.string.content_desc_play_pause),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (ttsState.isLoading) CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f),
                            strokeWidth = 2.dp
                        )
                    }
                }
            } else if (size == ReaderTtsOverlaySize.MEDIUM) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(onClick = onLocateCurrentChunk)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = miniBarTitle,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (miniBarSubtitle.isNotBlank()) {
                            Text(
                                text = miniBarSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        enabled = canSkipPreviousChunk,
                        onClick = { ttsController.skipToPreviousChunk() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = stringResource(R.string.content_desc_tts_previous_chunk),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        FilledIconButton(
                            onClick = { if (ttsState.isPlaying) ttsController.pause() else ttsController.resume() },
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                stringResource(R.string.content_desc_play_pause),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (ttsState.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    IconButton(
                        enabled = canSkipNextChunk,
                        onClick = { ttsController.skipToNextChunk() },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = stringResource(R.string.content_desc_tts_next_chunk),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                        IconButton(
                            onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.LARGE) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.content_desc_expand),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.SMALL) },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.content_desc_collapse),
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                        stringResource(R.string.tts_mode_cloud_ai)
                                    } else {
                                        stringResource(R.string.tts_mode_device_native)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                val voiceName = if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                    GEMINI_TTS_SPEAKERS.find { it.id == ttsState.speakerId }?.name ?: ttsState.speakerId
                                } else loadNativeVoice(context)?.split("-")?.lastOrNull() ?: stringResource(R.string.label_default)

                                Text(
                                    voiceName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp).widthIn(max = 100.dp)
                                )
                            }

                            if (BuildConfig.FLAVOR != "oss" && activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        "⭐ $credits",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onLocateCurrentChunk, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    painterResource(R.drawable.pin_drop),
                                    "Locate current chunk",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.MEDIUM) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    stringResource(R.string.content_desc_collapse),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = { onOverlaySizeChange(ReaderTtsOverlaySize.SMALL) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowRight,
                                    stringResource(R.string.content_desc_collapse),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Close, stringResource(R.string.content_desc_stop_tts), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    if (chapterLabel != null || progressPercent != null || chunkLabel != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    chapterLabel ?: "Reading",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                                )
                                Text(
                                    listOfNotNull(progressPercent?.let { "$it%" }, chunkLabel).joinToString(" - "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    // Middle Section: Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                enabled = canSkipPreviousChunk,
                                onClick = { ttsController.skipToPreviousChunk() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = stringResource(R.string.content_desc_tts_previous_chunk),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Giant Play/Pause
                            Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                FilledIconButton(
                                    onClick = { if (ttsState.isPlaying) ttsController.pause() else ttsController.resume() },
                                    modifier = Modifier.size(56.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Icon(
                                        painterResource(if (ttsState.isPlaying) R.drawable.pause else R.drawable.play),
                                        stringResource(R.string.content_desc_play_pause),
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                if (ttsState.isLoading) CircularProgressIndicator(
                                    modifier = Modifier.size(56.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    strokeWidth = 3.dp
                                )
                            }

                            IconButton(
                                enabled = canSkipNextChunk,
                                onClick = { ttsController.skipToNextChunk() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = stringResource(R.string.content_desc_tts_next_chunk),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        // Unified Sliders Block
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.tts_speed_short, "%.1f".format(rate)),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    IconButton(onClick = { rate = 1.0f; saveAndApply() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Refresh, stringResource(R.string.content_desc_reset_speed), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { rate = ((rate * 10f).roundToInt() / 10f - 0.1f).coerceAtLeast(0.5f); saveAndApply() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Slider(
                                        value = rate,
                                        onValueChange = { rate = it },
                                        onValueChangeFinished = saveAndApply,
                                        valueRange = 0.5f..3.0f,
                                        modifier = Modifier.weight(1f).height(20.dp),
                                        thumb = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        },
                                        track = { sliderState ->
                                            val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                            val fraction = if (range == 0f) 0f else {
                                                ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(2.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                                        RoundedCornerShape(1.dp)
                                                    ),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction)
                                                        .height(2.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            RoundedCornerShape(1.dp)
                                                        )
                                                )
                                            }
                                        }
                                    )
                                    IconButton(
                                        onClick = { rate = ((rate * 10f).roundToInt() / 10f + 0.1f).coerceAtMost(3.0f); saveAndApply() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stringResource(R.string.tts_pitch_short, "%.1f".format(pitch)),
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    IconButton(onClick = { pitch = 1.0f; saveAndApply() }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Refresh, stringResource(R.string.content_desc_reset_pitch), modifier = Modifier.size(16.dp))
                                    }
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { pitch = ((pitch * 10f).roundToInt() / 10f - 0.1f).coerceAtLeast(0.5f); saveAndApply() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Slider(
                                        value = pitch,
                                        onValueChange = { pitch = it },
                                        onValueChangeFinished = saveAndApply,
                                        valueRange = 0.5f..2.0f,
                                        modifier = Modifier.weight(1f).height(20.dp),
                                        thumb = {
                                            Box(
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            )
                                        },
                                        track = { sliderState ->
                                            val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                            val fraction = if (range == 0f) 0f else {
                                                ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(2.dp)
                                                    .background(
                                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                                                        RoundedCornerShape(1.dp)
                                                    ),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(fraction)
                                                        .height(2.dp)
                                                        .background(
                                                            MaterialTheme.colorScheme.primary,
                                                            RoundedCornerShape(1.dp)
                                                        )
                                                )
                                            }
                                        }
                                    )
                                    IconButton(
                                        onClick = { pitch = ((pitch * 10f).roundToInt() / 10f + 0.1f).coerceAtMost(2.0f); saveAndApply() },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
