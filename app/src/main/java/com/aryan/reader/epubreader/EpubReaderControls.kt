/*
 * Episteme Reader - A native Android document reader.
 * Copyright (C) 2026 Episteme
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * mail: epistemereader@gmail.com
 */
// EpubReaderControls.kt
package com.aryan.reader.epubreader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.webkit.WebView
import androidx.annotation.RequiresApi
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.UnstableApi
import com.aryan.reader.BuildConfig
import com.aryan.reader.R
import com.aryan.reader.RenderMode
import com.aryan.reader.SearchState
import com.aryan.reader.SearchTopBar
import com.aryan.reader.TooltipIconButton
import com.aryan.reader.areReaderAiFeaturesEnabled
import com.aryan.reader.epub.EpubChapter
import com.aryan.reader.loadNativeVoice
import com.aryan.reader.paginatedreader.BookPaginator
import com.aryan.reader.paginatedreader.IPaginator
import com.aryan.reader.tts.GEMINI_TTS_SPEAKERS
import com.aryan.reader.tts.TtsPlaybackManager.TtsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.roundToInt

enum class ReaderTool(val title: String, val category: String) {
    DICTIONARY("External Apps", "Top Bar"),
    THEME("Theme Settings", "Top Bar"),
    SLIDER("Navigation Slider", "Bottom Bar"),
    TOC("Sidebar", "Bottom Bar"),
    FORMAT("Text Formatting", "Bottom Bar"),
    SEARCH("Search", "Bottom Bar"),
    AI_FEATURES("AI Features", "Bottom Bar"),
    TTS_CONTROLS("TTS Controls", "Bottom Bar"),
    READING_MODE("Reading Mode", "Overflow Menu"),
    BOOKMARK("Bookmark", "Overflow Menu"),
    TAP_TO_TURN("Tap to Turn Pages", "Overflow Menu"),
    VOLUME_SCROLL("Volume Button Scrolling", "Overflow Menu"),
    PAGE_TURN_ANIM("Realistic Page Turns", "Overflow Menu"),
    KEEP_SCREEN_ON("Keep Screen On", "Overflow Menu"),
    VISUAL_OPTIONS("Visual Options", "Overflow Menu"),
    AUTO_SCROLL("Auto Scroll", "Overflow Menu"),
    TTS_SETTINGS("TTS Voice Settings", "Overflow Menu")
}

enum class FlatItemType { SECTION_HEADER, TOOL, EMPTY_PLACEHOLDER, MORE_HEADER, MORE_TOOL }

data class FlatToolItem(
    val id: String,
    val type: FlatItemType,
    val tool: ReaderTool? = null,
    val section: ToolbarSection? = null,
    val title: String? = null
)

fun sanitizePlaceholders(list: List<FlatToolItem>): List<FlatToolItem> {
    val result = mutableListOf<FlatToolItem>()
    val sectionMap = mutableMapOf<ToolbarSection, MutableList<FlatToolItem>>()
    ToolbarSection.entries.forEach { sectionMap[it] = mutableListOf() }

    list.forEach { item ->
        if (item.type == FlatItemType.TOOL) {
            item.section?.let { sectionMap[it]?.add(item) }
        }
    }

    ToolbarSection.entries.forEach { section ->
        result.add(FlatToolItem("header_${section.name}", FlatItemType.SECTION_HEADER, section = section, title = section.title))

        val tools = sectionMap[section] ?: emptyList()
        if (tools.isEmpty()) {
            result.add(FlatToolItem("empty_${section.name}", FlatItemType.EMPTY_PLACEHOLDER, section = section))
        } else {
            result.addAll(tools)
        }
    }

    // Maintain More menu items
    list.filter { it.type == FlatItemType.MORE_HEADER || it.type == FlatItemType.MORE_TOOL }.forEach {
        result.add(it)
    }

    return result
}

class DragDropState(
    val lazyListState: LazyListState,
    val onMove: (String, String) -> Unit
) {
    var draggedItemId by mutableStateOf<String?>(null)
    var dragOffset by mutableStateOf(Offset.Zero)

    fun onDragStart(id: String) {
        draggedItemId = id
        dragOffset = Offset.Zero
    }

    fun onDrag(delta: Offset) {
        val draggedId = draggedItemId ?: return
        dragOffset += delta

        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
        val currentItem = visibleItems.find { it.key == draggedId } ?: return

        val startY = currentItem.offset + dragOffset.y
        val center = startY + currentItem.size / 2f

        val targetItem = visibleItems.find {
            it.key != draggedId && center >= it.offset && center <= (it.offset + it.size)
        }

        if (targetItem != null) {
            onMove(draggedId, targetItem.key.toString())
            // Adjust visual offset to prevent snapping when items swap in the layout
            dragOffset = dragOffset.copy(y = dragOffset.y - (targetItem.offset - currentItem.offset))
        }
    }

    fun onDragEnd() {
        draggedItemId = null
        dragOffset = Offset.Zero
    }
}

private val epubToolbarTools = setOf(
    ReaderTool.DICTIONARY,
    ReaderTool.THEME,
    ReaderTool.SLIDER,
    ReaderTool.TOC,
    ReaderTool.FORMAT,
    ReaderTool.SEARCH,
    ReaderTool.AI_FEATURES,
    ReaderTool.TTS_CONTROLS
)

@Composable
fun EpubReaderTopBar(
    isVisible: Boolean,
    searchState: SearchState,
    bookTitle: String,
    currentRenderMode: RenderMode,
    isBookmarked: Boolean,
    isTtsActive: Boolean,
    tapToNavigateEnabled: Boolean,
    volumeScrollEnabled: Boolean,
    isPageTurnAnimationEnabled: Boolean,
    onNavigateBack: () -> Unit,
    isKeepScreenOn: Boolean,
    onToggleKeepScreenOn: (Boolean) -> Unit,
    onCloseSearch: () -> Unit,
    onChangeRenderMode: (RenderMode) -> Unit,
    onToggleBookmark: () -> Unit,
    onToggleTapToNavigate: (Boolean) -> Unit,
    onToggleVolumeScroll: (Boolean) -> Unit,
    onTogglePageTurnAnimation: (Boolean) -> Unit,
    onStartAutoScroll: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenVisualOptions: () -> Unit,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onToggleTts: () -> Unit,
    searchFocusRequester: androidx.compose.ui.focus.FocusRequester,
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    onCustomizeTools: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleReflow: (() -> Unit)? = null,
    onDeleteReflow: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                    toolOrder
                        .filter { it in epubToolbarTools && !bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
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
                                ReaderTool.SLIDER -> TooltipIconButton(
                                    text = stringResource(R.string.tooltip_slider),
                                    description = stringResource(R.string.tooltip_slider_desc),
                                    onClick = onOpenSlider,
                                    enabled = currentRenderMode != RenderMode.VERTICAL_SCROLL
                                ) {
                                    Icon(painter = painterResource(id = R.drawable.slider), contentDescription = stringResource(R.string.content_desc_navigate_slider))
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
                                else -> Unit
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
                            }
                        ) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.content_desc_more_options))
                        }

                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = {
                                showHiddenToolsExpanded = false
                                showMoreMenu = false
                            }
                        ) {
                            val hiddenToolbarTools = toolOrder.filter { it in epubToolbarTools && hiddenTools.contains(it.name) }
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
                                        HiddenEpubToolMenuItem(
                                            tool = tool,
                                            currentRenderMode = currentRenderMode,
                                            isTtsActive = isTtsActive,
                                            showMoreMenu = {
                                                showHiddenToolsExpanded = false
                                                showMoreMenu = false
                                            },
                                            onOpenDictionarySettings = onOpenDictionarySettings,
                                            onOpenThemeSettings = onOpenThemeSettings,
                                            onOpenSlider = onOpenSlider,
                                            onOpenDrawer = onOpenDrawer,
                                            onToggleFormat = onToggleFormat,
                                            onToggleSearch = onToggleSearch,
                                            onOpenAiHub = onOpenAiHub,
                                            onToggleTts = onToggleTts
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }

                            if (onToggleReflow != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_view_original_pdf)) },
                                    onClick = {
                                        showMoreMenu = false
                                        onToggleReflow()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.picture_as_pdf),
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                )
                                HorizontalDivider()
                            }

                            onDeleteReflow?.let {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_delete_text_view)) },
                                    onClick = {
                                        showMoreMenu = false
                                        it()
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

                            if (!hiddenTools.contains(ReaderTool.READING_MODE.name)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_reading_mode_vertical)) },
                                    enabled = !isTtsActive,
                                    onClick = {
                                        showMoreMenu = false
                                        onChangeRenderMode(RenderMode.VERTICAL_SCROLL)
                                    },
                                    trailingIcon = {
                                        if (currentRenderMode == RenderMode.VERTICAL_SCROLL) Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(R.string.content_desc_selected)
                                        )
                                    })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_reading_mode_paginated)) },
                                    enabled = !isTtsActive,
                                    onClick = {
                                        showMoreMenu = false
                                        onChangeRenderMode(RenderMode.PAGINATED)
                                    },
                                    trailingIcon = {
                                        if (currentRenderMode == RenderMode.PAGINATED) Icon(
                                            Icons.Default.Check,
                                            contentDescription = stringResource(R.string.content_desc_selected)
                                        )
                                    })
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.BOOKMARK.name)) {
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
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.TAP_TO_TURN.name)) {
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
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.VOLUME_SCROLL.name)) {
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
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.PAGE_TURN_ANIM.name)) {
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
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.KEEP_SCREEN_ON.name)) {
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
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.VISUAL_OPTIONS.name)) {
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
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.AUTO_SCROLL.name)) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.menu_auto_scroll)) },
                                    enabled = !isTtsActive && currentRenderMode == RenderMode.VERTICAL_SCROLL,
                                    onClick = {
                                        showMoreMenu = false
                                        onStartAutoScroll()
                                    })
                                HorizontalDivider()
                            }
                            if (!hiddenTools.contains(ReaderTool.TTS_SETTINGS.name)) {
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
                        }
                    }
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
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onToggleTts: () -> Unit,
    hiddenTools: Set<String>,
    toolOrder: List<ReaderTool>,
    bottomTools: Set<String>,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(45.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                toolOrder
                    .filter { it in epubToolbarTools && bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
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
                            ReaderTool.SLIDER -> TooltipIconButton(
                                text = stringResource(R.string.tooltip_slider),
                                description = stringResource(R.string.tooltip_slider_desc),
                                onClick = onOpenSlider,
                                enabled = currentRenderMode != RenderMode.VERTICAL_SCROLL
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.slider),
                                    contentDescription = stringResource(R.string.content_desc_navigate_slider)
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
                            else -> Unit
                        }
                    }
            }
        }
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpubReaderPageSlider(
    isVisible: Boolean,
    currentRenderMode: RenderMode,
    totalPages: Int,
    sliderCurrentPage: Float,
    sliderStartPage: Int,
    startPageThumbnail: Bitmap?,
    paginator: IPaginator?,
    chapters: List<EpubChapter>,
    onClose: () -> Unit,
    onScrub: (Float) -> Unit,
    onJumpToPage: (Int) -> Unit
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeIn(animationSpec = tween(200)),
        exit = slideOutVertically(animationSpec = tween(200)) { fullHeight -> fullHeight } + fadeOut(animationSpec = tween(200))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onClose() }
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.content_desc_exit_slider)
                )
            }

            // Bottom controls
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {},
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Slider(
                            value = sliderCurrentPage,
                            onValueChange = onScrub,
                            valueRange = 1f..(totalPages.toFloat().coerceAtLeast(1f)),
                            steps = if (totalPages > 2) totalPages - 2 else 0,
                            modifier = Modifier.fillMaxWidth(),
                            thumb = {
                                Surface(
                                    modifier = Modifier.size(20.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    tonalElevation = 0.dp,
                                    shadowElevation = 0.dp
                                ) {}
                            },
                            track = { sliderState ->
                                val trackHeight = 2.dp
                                val trackShape = RoundedCornerShape(trackHeight)
                                val range = sliderState.valueRange.endInclusive - sliderState.valueRange.start
                                val fraction = if (range == 0f) 0f else {
                                    ((sliderState.value - sliderState.valueRange.start) / range).coerceIn(0f, 1f)
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(trackHeight)
                                        .background(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            shape = trackShape
                                        )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .background(
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = trackShape
                                            )
                                    )
                                }
                            }
                        )

                        // Thumbnail Indicator
                        val startPageOffsetFraction = if (totalPages > 1) {
                            (sliderStartPage - 1).toFloat() / (totalPages - 1)
                        } else {
                            0f
                        }
                        val thumbWidth = 20.dp
                        val trackWidth = maxWidth - thumbWidth
                        val startPagePixelPosition = (trackWidth * startPageOffsetFraction) + (thumbWidth / 2)
                        val thumbnailModifier = Modifier
                            .graphicsLayer { clip = false }
                            .align(Alignment.TopStart)
                            .offset(
                                x = startPagePixelPosition - (45.dp / 2),
                                y = (-72).dp
                            )

                        if (currentRenderMode == RenderMode.VERTICAL_SCROLL) {
                            startPageThumbnail?.let { thumbnail ->
                                ThumbnailWithIndicator(
                                    modifier = thumbnailModifier,
                                    onClick = { onJumpToPage(sliderStartPage) }
                                ) {
                                    Image(
                                        bitmap = thumbnail.asImageBitmap(),
                                        contentDescription = stringResource(R.string.content_desc_start_page_thumbnail),
                                        contentScale = ContentScale.FillBounds,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            val startPageChapterIndex = remember(sliderStartPage, paginator) {
                                (paginator as? BookPaginator)?.findChapterIndexForPage(sliderStartPage - 1)
                            }
                            val startPageChapterTitle = remember(startPageChapterIndex) {
                                startPageChapterIndex?.let { chapters.getOrNull(it)?.title }
                            }
                            ThumbnailWithIndicator(
                                modifier = thumbnailModifier,
                                onClick = { onJumpToPage(sliderStartPage) }
                            ) {
                                PaginatedThumbnailContent(
                                    pageNumber = sliderStartPage,
                                    chapterTitle = startPageChapterTitle
                                )
                            }
                        }
                    }

                    Text(
                        text = "${sliderCurrentPage.roundToInt()} / $totalPages",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
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
internal fun ThumbnailWithIndicator(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.primary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier
                .width(45.dp)
                .height(64.dp)
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(2.dp, borderColor)
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .offset(y = (-4).dp)
                .size(8.dp)
                .rotate(45f)
                .background(borderColor)
        )
    }
}

@Composable
private fun PaginatedThumbnailContent(pageNumber: Int, chapterTitle: String?) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (chapterTitle != null) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 10.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = "$pageNumber",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

suspend fun captureWebViewVisibleArea(webView: WebView): Bitmap? {
    return withContext(Dispatchers.Main) {
        if (webView.width <= 0 || webView.height <= 0) return@withContext null
        try {
            val thumbnailWidth = 180
            val thumbnailHeight = 256
            val bitmap = createBitmap(thumbnailWidth, thumbnailHeight)
            val canvas = Canvas(bitmap)
            val scale = thumbnailWidth.toFloat() / webView.width.toFloat()
            canvas.scale(scale, scale)
            canvas.translate(-webView.scrollX.toFloat(), -webView.scrollY.toFloat())
            webView.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Timber.e(e, "Failed to capture webview content")
            null
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
    var localHiddenTools by remember { mutableStateOf(hiddenTools) }

    var flatItems by remember {
        mutableStateOf(
            run {
                val toolbarTools = toolOrder.filter { it in epubToolbarTools }
                val topTools = toolbarTools.filter { !bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
                val bottomToolsList = toolbarTools.filter { bottomTools.contains(it.name) && !hiddenTools.contains(it.name) }
                val hiddenToolsList = toolbarTools.filter { hiddenTools.contains(it.name) }
                val moreTools = toolOrder.filter { it !in epubToolbarTools }

                val list = mutableListOf<FlatToolItem>()

                ToolbarSection.entries.forEach { section ->
                    val tools = when(section) {
                        ToolbarSection.TOP -> topTools
                        ToolbarSection.BOTTOM -> bottomToolsList
                        ToolbarSection.HIDDEN -> hiddenToolsList
                    }
                    list.add(FlatToolItem("header_${section.name}", FlatItemType.SECTION_HEADER, section = section, title = section.title))
                    if (tools.isEmpty()) {
                        list.add(FlatToolItem("empty_${section.name}", FlatItemType.EMPTY_PLACEHOLDER, section = section))
                    } else {
                        tools.forEach { tool ->
                            list.add(FlatToolItem("tool_${tool.name}", FlatItemType.TOOL, tool = tool, section = section))
                        }
                    }
                }

                list.add(FlatToolItem("more_header", FlatItemType.MORE_HEADER, title = "More menu"))
                moreTools.forEach { tool ->
                    list.add(FlatToolItem("more_${tool.name}", FlatItemType.MORE_TOOL, tool = tool))
                }
                list
            }
        )
    }

    val commitDragDrop = {
        val newHidden = localHiddenTools.filter { toolName ->
            toolOrder.find { it.name == toolName } !in epubToolbarTools
        }.toMutableSet()

        val newBottom = mutableSetOf<String>()
        val newOrder = mutableListOf<ReaderTool>()

        flatItems.forEach { item ->
            if (item.type == FlatItemType.TOOL && item.tool != null) {
                newOrder.add(item.tool)
                if (item.section == ToolbarSection.HIDDEN) newHidden.add(item.tool.name)
                if (item.section == ToolbarSection.BOTTOM) newBottom.add(item.tool.name)
            }
        }

        val moreTools = flatItems.filter { it.type == FlatItemType.MORE_TOOL }.mapNotNull { it.tool }
        newOrder.addAll(moreTools)

        localHiddenTools = newHidden
        onUpdate(newHidden)
        onPlacementUpdate(newBottom)
        onOrderUpdate(newOrder)
    }

    val lazyListState = rememberLazyListState()
    val dragDropState = remember {
        DragDropState(lazyListState) { fromKey, toKey ->
            val fromIndex = flatItems.indexOfFirst { it.id == fromKey }
            val toIndex = flatItems.indexOfFirst { it.id == toKey }
            if (fromIndex == -1 || toIndex == -1 || fromIndex == toIndex) return@DragDropState

            val fromItem = flatItems[fromIndex]
            if (fromItem.type != FlatItemType.TOOL) return@DragDropState

            val toItem = flatItems[toIndex]
            if (toItem.type == FlatItemType.MORE_HEADER || toItem.type == FlatItemType.MORE_TOOL) return@DragDropState

            val newList = flatItems.toMutableList()
            val movedItem = newList.removeAt(fromIndex)

            val newToIndex = newList.indexOfFirst { it.id == toKey }
            val insertIndex = if (fromIndex < toIndex) newToIndex + 1 else newToIndex

            newList.add(insertIndex, movedItem)

            var actualSection = movedItem.section
            for (i in insertIndex downTo 0) {
                val item = newList[i]
                if (item.type == FlatItemType.SECTION_HEADER) {
                    actualSection = item.section
                    break
                }
            }

            newList[insertIndex] = movedItem.copy(section = actualSection)

            flatItems = newList
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Customize Toolbar",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                    }
                }

                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(flatItems, key = { it.id }) { item ->
                        val isDragged = item.id == dragDropState.draggedItemId

                        val zIndex = if (isDragged) 1f else 0f
                        val elevation = if (isDragged) 8.dp else 0.dp
                        val scale = if (isDragged) 1.03f else 1f
                        val translationY = if (isDragged) dragDropState.dragOffset.y else 0f

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isDragged) Modifier else Modifier.animateItem())
                                .zIndex(zIndex)
                                .graphicsLayer {
                                    this.translationY = translationY
                                    this.scaleX = scale
                                    this.scaleY = scale
                                    this.shadowElevation = elevation.toPx()
                                }
                        ) {
                            when (item.type) {
                                FlatItemType.SECTION_HEADER -> {
                                    Text(
                                        text = item.title ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                FlatItemType.EMPTY_PLACEHOLDER -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp)
                                            .padding(vertical = 4.dp)
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("Drop tools here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                FlatItemType.TOOL -> {
                                    ToolbarDragRow(
                                        tool = item.tool!!,
                                        isDragging = isDragged,
                                        onDragStart = { dragDropState.onDragStart(item.id) },
                                        onDrag = { dragDropState.onDrag(it) },
                                        onDragEnd = {
                                            dragDropState.onDragEnd()
                                            flatItems = sanitizePlaceholders(flatItems).toMutableList()
                                            commitDragDrop()
                                        }
                                    )
                                }
                                FlatItemType.MORE_HEADER -> {
                                    Text(
                                        text = item.title ?: "More menu",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 4.dp)
                                    )
                                }
                                FlatItemType.MORE_TOOL -> {
                                    MoreToolVisibilityRow(
                                        title = item.tool!!.title,
                                        visible = !localHiddenTools.contains(item.tool.name),
                                        onToggle = {
                                            localHiddenTools = if (localHiddenTools.contains(item.tool.name)) {
                                                localHiddenTools - item.tool.name
                                            } else {
                                                localHiddenTools + item.tool.name
                                            }
                                            onUpdate(localHiddenTools)
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

@Composable
private fun ToolbarDragRow(
    tool: ReaderTool,
    isDragging: Boolean,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (isDragging) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ToolPreviewIcon(tool)
            Spacer(Modifier.width(16.dp))
            Text(
                text = tool.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.Menu,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(32.dp)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .pointerInput(tool) {
                        detectDragGestures(
                            onDragStart = { onDragStart() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDrag(dragAmount)
                            },
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd
                        )
                    }
            )
        }
    }
}

@Composable
private fun MoreToolVisibilityRow(
    title: String,
    visible: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (visible) {
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

enum class ToolbarSection(val title: String) {
    TOP("Top Bar"),
    BOTTOM("Bottom Bar"),
    HIDDEN("Hidden Tools")
}

@Composable
private fun ToolPreviewIcon(tool: ReaderTool) {
    when (tool) {
        ReaderTool.DICTIONARY -> Icon(painterResource(id = R.drawable.dictionary), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.THEME -> Icon(painterResource(id = R.drawable.palette), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.SLIDER -> Icon(painterResource(id = R.drawable.slider), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.TOC -> Icon(Icons.Default.Menu, contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.FORMAT -> Icon(painterResource(id = R.drawable.format_size), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.SEARCH -> Icon(Icons.Default.Search, contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.AI_FEATURES -> Icon(painterResource(id = R.drawable.ai), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        ReaderTool.TTS_CONTROLS -> Icon(painterResource(id = R.drawable.text_to_speech), contentDescription = tool.title, modifier = Modifier.size(20.dp))
        else -> Icon(Icons.Default.MoreVert, contentDescription = tool.title, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun HiddenEpubToolMenuItem(
    tool: ReaderTool,
    currentRenderMode: RenderMode,
    isTtsActive: Boolean,
    showMoreMenu: () -> Unit,
    onOpenDictionarySettings: () -> Unit,
    onOpenThemeSettings: () -> Unit,
    onOpenSlider: () -> Unit,
    onOpenDrawer: () -> Unit,
    onToggleFormat: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAiHub: () -> Unit,
    onToggleTts: () -> Unit
) {
    val enabled = when (tool) {
        ReaderTool.SLIDER -> currentRenderMode != RenderMode.VERTICAL_SCROLL
        else -> true
    }
    DropdownMenuItem(
        text = { Text(tool.title) },
        enabled = enabled,
        onClick = {
            showMoreMenu()
            when (tool) {
                ReaderTool.DICTIONARY -> onOpenDictionarySettings()
                ReaderTool.THEME -> onOpenThemeSettings()
                ReaderTool.SLIDER -> onOpenSlider()
                ReaderTool.TOC -> onOpenDrawer()
                ReaderTool.FORMAT -> onToggleFormat()
                ReaderTool.SEARCH -> onToggleSearch()
                ReaderTool.AI_FEATURES -> onOpenAiHub()
                ReaderTool.TTS_CONTROLS -> onToggleTts()
                else -> Unit
            }
        },
        leadingIcon = { ToolPreviewIcon(tool) }
    )
}

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsOverlayControls(
    ttsController: com.aryan.reader.tts.TtsController,
    ttsState: TtsState,
    currentTtsMode: com.aryan.reader.tts.TtsPlaybackManager.TtsMode,
    isCollapsed: Boolean,
    onCollapseChange: (Boolean) -> Unit,
    onLocateCurrentChunk: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    credits: Int
) {
    val context = LocalContext.current
    var rate by remember { mutableFloatStateOf(loadTtsSpeechRate(context)) }
    var pitch by remember { mutableFloatStateOf(loadTtsPitch(context)) }
    var isDraggingRate by remember { mutableStateOf(false) }
    var isDraggingPitch by remember { mutableStateOf(false) }

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
        if (ttsState.currentChunkIndex >= 0 && ttsState.totalChunks > 0) {
            "Chunk ${ttsState.currentChunkIndex + 1}/${ttsState.totalChunks}"
        } else {
            null
        }
    }

    val saveAndApply = {
        saveTtsSpeechRate(context, rate)
        saveTtsPitch(context, pitch)
        if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
            ttsController.setPlaybackParameters(rate, pitch)
        } else {
            ttsController.sliceAndRetainPosition()
        }
    }

    val backgroundAlpha = 0.6f

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = backgroundAlpha),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        modifier = modifier.widthIn(max = 400.dp).animateContentSize()
    ) {
        AnimatedContent(
            targetState = isCollapsed,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(200)) },
            label = "TtsOverlayUnified"
        ) { collapsed ->
            if (collapsed) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onCollapseChange(false) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.ChevronLeft, "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
            } else {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) "✨ Cloud" else "📱 Device",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                val voiceName = if (activeMode == com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                    GEMINI_TTS_SPEAKERS.find { it.id == ttsState.speakerId }?.name ?: ttsState.speakerId
                                } else loadNativeVoice(context)?.split("-")?.lastOrNull() ?: "Default"

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
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f),
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

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = onLocateCurrentChunk, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    painterResource(R.drawable.pin_drop),
                                    "Locate current chunk",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onCollapseChange(true) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.ChevronRight, stringResource(R.string.content_desc_collapse), modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

                        Spacer(Modifier.width(16.dp))

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
                                        onValueChange = {
                                            rate = it; if (!isDraggingRate && activeMode != com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                            isDraggingRate = true; ttsController.pause()
                                        }
                                        },
                                        onValueChangeFinished = { isDraggingRate = false; saveAndApply() },
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
                                        onValueChange = {
                                            pitch = it; if (!isDraggingPitch && activeMode != com.aryan.reader.tts.TtsPlaybackManager.TtsMode.CLOUD) {
                                            isDraggingPitch = true; ttsController.pause()
                                        }
                                        },
                                        onValueChangeFinished = { isDraggingPitch = false; saveAndApply() },
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
