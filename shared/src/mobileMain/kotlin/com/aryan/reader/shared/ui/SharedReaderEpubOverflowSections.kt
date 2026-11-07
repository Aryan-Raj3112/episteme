package com.aryan.reader.shared.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.aryan.reader.shared.EpubOverflowMenuSection
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.epubOverflowMenuSections
import com.aryan.reader.shared.reader.ReaderReadingMode

/**
 * Platform-neutral reader state the EPUB overflow menu renders from.
 *
 * Android benchmark (`EpubReaderControls.kt`): every flag mirrors an Android reader input, and the
 * reading-mode trio ([readingMode], [useNativeVerticalRenderer], [rightToLeftPagination]) is the same
 * pair of booleans Android drives through `RenderMode` + `isRightToLeftPagination` +
 * `useNativeVerticalRenderer`. A host maps its own mode enum onto [readingMode] and leaves the two
 * flags untouched, so no reading mode can be expressed on one platform and not the other.
 */
data class SharedReaderEpubOverflowState(
    val isBookmarked: Boolean = false,
    /** Any read-aloud engine (local or cloud) is mid-session. Matches Android's `isTtsSessionActive`. */
    val isTtsActive: Boolean = false,
    val readingMode: ReaderReadingMode = ReaderReadingMode.VERTICAL,
    val useNativeVerticalRenderer: Boolean = false,
    val rightToLeftPagination: Boolean = false,
    val tapToNavigateEnabled: Boolean = true,
    val volumeScrollEnabled: Boolean = false,
    val pageTurnAnimationEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val hiddenToolNamesOrIds: Set<String> = emptySet(),
    val toolOrder: List<ReaderTool> = ReaderTool.entries.toList(),
) {
    val isPaginated: Boolean get() = readingMode == ReaderReadingMode.PAGINATED
}

/**
 * Every word the EPUB overflow menu renders.
 *
 * The menu is shared code but not shared copy: Android passes `stringResource(...)` values so a
 * translated reader stays translated ([SharedMobileAutoScrollLabels] sets the same precedent for the
 * auto-scroll surface), while hosts with a [LocalSharedStringResolver] use
 * [sharedReaderEpubOverflowLabels]. That helper's keys are the literals the pre-unification shared
 * menu used, so no host's wording moves.
 */
data class SharedReaderEpubOverflowLabels(
    val customizeToolbar: String,
    val hiddenTools: String,
    val viewOriginalPdf: String,
    val deleteTextView: String,
    val changeReadingMode: String,
    val readingModeVerticalWebView: String,
    val readingModeVerticalNative: String,
    val readingModePaginated: String,
    val readingModePaginatedRightToLeft: String,
    val bookmark: String,
    val removeBookmark: String,
    val tapToTurnPages: String,
    val volumeButtonScrolling: String,
    val volumeButtonPageTurn: String,
    val realisticPageTurns: String,
    val keepScreenOn: String,
    val visualOptions: String,
    val autoScroll: String,
    val bookWordReplacements: String,
    val ttsSettings: String,
    val ttsVoiceSettings: String,
    val ttsWordReplacements: String,
    val exportAnnotations: String,
    val fileInformation: String,
    /** Trailers on the checkmark rows; Android's `content_desc_selected` / `content_desc_enabled`. */
    val selectedContentDescription: String,
    val enabledContentDescription: String,
)

/**
 * Resolver-backed labels, for hosts that provide a [LocalSharedStringResolver].
 *
 * Android's reader composes without one, so it passes its own table instead of calling this: a
 * shared lookup would render English inside an otherwise translated reader.
 */
@Composable
fun sharedReaderEpubOverflowLabels(): SharedReaderEpubOverflowLabels {
    return SharedReaderEpubOverflowLabels(
        customizeToolbar = readerString("title_customize_toolbar", "Customize Toolbar"),
        hiddenTools = readerString("toolbar_hidden_tools_menu", "Hidden tools"),
        viewOriginalPdf = readerString("menu_view_original_pdf", "View original PDF"),
        deleteTextView = readerString("menu_delete_text_view", "Delete text view"),
        changeReadingMode = readerString("menu_change_reading_mode", "Change Reading Mode"),
        readingModeVerticalWebView = readerString("menu_reading_mode_vertical_webview", "Vertical (WebView)"),
        readingModeVerticalNative = readerString("menu_reading_mode_vertical_native", "Vertical (Native Beta)"),
        readingModePaginated = readerString("menu_reading_mode_paginated", "Paginated (left-to-right)"),
        readingModePaginatedRightToLeft = readerString("menu_right_to_left_pagination", "Paginated (right-to-left)"),
        bookmark = readerString("menu_bookmark_this_page", "Bookmark this page"),
        removeBookmark = readerString("menu_remove_bookmark", "Remove bookmark"),
        tapToTurnPages = readerString("menu_tap_to_turn_pages", "Tap to Turn Pages"),
        volumeButtonScrolling = readerString("menu_volume_button_scrolling", "Volume Buttons Scroll"),
        volumeButtonPageTurn = readerString("menu_volume_button_page_turn", "Volume Buttons Turn Pages"),
        realisticPageTurns = readerString("menu_realistic_page_turns", "Realistic Page Turns"),
        keepScreenOn = readerString("menu_keep_screen_on", "Keep Screen On"),
        visualOptions = readerString("menu_visual_options", "Visual Options"),
        autoScroll = readerString("menu_auto_scroll", "Auto Scroll"),
        bookWordReplacements = readerString("menu_book_word_replacements", "Book Word Replacements"),
        ttsSettings = readerString("menu_tts_settings", "TTS Settings"),
        ttsVoiceSettings = readerString("menu_tts_voice_settings", "TTS Voice Settings"),
        ttsWordReplacements = readerString("menu_tts_word_replacements", "TTS Word Replacements"),
        exportAnnotations = readerString("action_export_annotations", "Export annotations"),
        fileInformation = readerString("file_information", "File Information"),
        selectedContentDescription = readerString("content_desc_selected", "Selected"),
        enabledContentDescription = readerString("content_desc_enabled", "Enabled"),
    )
}

/**
 * Everything the menu can be asked to do.
 *
 * Nullable members are capabilities, not hooks: a section whose capability is absent leaves the menu
 * instead of rendering a dead item. A reader that cannot reflow a PDF never shows "View original
 * PDF", and iOS — which has no volume-key paging — never shows the volume item, exactly as Android
 * already gates narration on books whose OPF declares media overlays.
 */
data class SharedReaderEpubOverflowCallbacks(
    val onCustomizeTools: () -> Unit,
    val onReadingModeChange: (ReaderReadingMode) -> Unit = {},
    val onUseNativeVerticalRendererChange: (Boolean) -> Unit = {},
    val onRightToLeftPaginationChange: (Boolean) -> Unit = {},
    val onToggleBookmark: () -> Unit = {},
    val onToggleTapToNavigate: (Boolean) -> Unit = {},
    val onToggleVolumeScroll: ((Boolean) -> Unit)? = null,
    val onTogglePageTurnAnimation: (Boolean) -> Unit = {},
    val onToggleKeepScreenOn: (Boolean) -> Unit = {},
    val onOpenVisualOptions: () -> Unit = {},
    val onAutoScrollChange: (Boolean) -> Unit = {},
    val onOpenBookReplacements: () -> Unit = {},
    val onOpenTtsSettings: () -> Unit = {},
    val onOpenTtsReplacements: () -> Unit = {},
    val onExportAnnotations: (() -> Unit)? = null,
    val onOpenFileInfo: () -> Unit = {},
    /** Reflow sources only: present when this reading came from a PDF's extracted text view. */
    val onToggleReflow: (() -> Unit)? = null,
    val onDeleteReflow: (() -> Unit)? = null,
    /**
     * Opens the tool the reader hid from both bars. A slot rather than a dispatch table: the hidden
     * list is the host's own toolbar state, and each host renders the entry with its own labels and
     * icons.
     */
    val hiddenToolItem: @Composable (ReaderTool) -> Unit = {},
)

/**
 * Renders one [EpubOverflowMenuSection] with Android's item spec: 20.dp leading icons where Android
 * has them, a rotating `ArrowDropDown` on the expanders, checkmarks carrying the host's
 * selected/enabled descriptions on the toggle rows, and error colouring on the destructive reflow
 * delete.
 *
 * Both hosts render the same section list — [com.aryan.reader.shared.epubOverflowMenuSections] — so an
 * item can only be added, gated or reordered in one place. `state` and `labels` are the host's own;
 * this renderer never reads a setting or a string resource itself.
 */
@Composable
fun SharedReaderEpubOverflowSection(
    section: EpubOverflowMenuSection,
    menuState: SharedReaderOverflowMenuState,
    state: SharedReaderEpubOverflowState,
    labels: SharedReaderEpubOverflowLabels,
    callbacks: SharedReaderEpubOverflowCallbacks,
) {
    val dismiss = { menuState.dismiss() }
    when (section) {
        EpubOverflowMenuSection.CUSTOMIZE_TOOLBAR -> SharedReaderOverflowItem(
            label = labels.customizeToolbar,
            leadingIcon = Icons.Default.Settings,
            onClick = {
                dismiss()
                callbacks.onCustomizeTools()
            }
        )

        EpubOverflowMenuSection.HIDDEN_TOOLS -> {
            // The section only reaches the menu when the host reported hidden bar tools; the list
            // itself is derived from the host's own tool order.
            SharedReaderOverflowExpanderItem(
                label = labels.hiddenTools,
                expanded = menuState.hiddenToolsExpanded.value,
                onClick = { menuState.hiddenToolsExpanded.value = !menuState.hiddenToolsExpanded.value }
            )
            if (menuState.hiddenToolsExpanded.value) {
                sharedEpubHiddenToolbarTools(
                    toolOrder = state.toolOrder,
                    hiddenToolNamesOrIds = state.hiddenToolNamesOrIds,
                ).forEach { tool -> callbacks.hiddenToolItem(tool) }
            }
        }

        EpubOverflowMenuSection.VIEW_ORIGINAL_PDF -> SharedReaderOverflowItem(
            label = labels.viewOriginalPdf,
            // The project's shared icon set has no `picture_as_pdf`; `FileOpen` says the same thing
            // (open the source file) and exists on both platforms.
            leadingIcon = Icons.Default.FileOpen,
            onClick = {
                dismiss()
                callbacks.onToggleReflow?.invoke()
            }
        )

        EpubOverflowMenuSection.DELETE_TEXT_VIEW -> SharedReaderOverflowItem(
            label = labels.deleteTextView,
            leadingIcon = Icons.Default.Delete,
            errorColored = true,
            onClick = {
                dismiss()
                callbacks.onDeleteReflow?.invoke()
            }
        )

        EpubOverflowMenuSection.READING_MODE -> {
            SharedReaderOverflowExpanderItem(
                label = labels.changeReadingMode,
                expanded = menuState.readingModeExpanded.value,
                onClick = { menuState.readingModeExpanded.value = !menuState.readingModeExpanded.value }
            )
            if (menuState.readingModeExpanded.value) {
                val verticalItems = listOf(
                    labels.readingModeVerticalWebView to false,
                    labels.readingModeVerticalNative to true,
                )
                verticalItems.forEach { (label, useNativeRenderer) ->
                    SharedReaderOverflowItem(
                        label = label,
                        enabled = !state.isTtsActive,
                        selected = state.readingMode == ReaderReadingMode.VERTICAL &&
                            state.useNativeVerticalRenderer == useNativeRenderer,
                        selectedContentDescription = labels.selectedContentDescription,
                        onClick = {
                            callbacks.onUseNativeVerticalRendererChange(useNativeRenderer)
                            callbacks.onReadingModeChange(ReaderReadingMode.VERTICAL)
                            closeReadingModeMenu(menuState, dismiss)
                        }
                    )
                }
                val paginatedItems = listOf(
                    labels.readingModePaginated to false,
                    labels.readingModePaginatedRightToLeft to true,
                )
                paginatedItems.forEach { (label, rightToLeft) ->
                    SharedReaderOverflowItem(
                        label = label,
                        enabled = !state.isTtsActive,
                        selected = state.isPaginated && state.rightToLeftPagination == rightToLeft,
                        selectedContentDescription = labels.selectedContentDescription,
                        onClick = {
                            callbacks.onRightToLeftPaginationChange(rightToLeft)
                            callbacks.onReadingModeChange(ReaderReadingMode.PAGINATED)
                            closeReadingModeMenu(menuState, dismiss)
                        }
                    )
                }
            }
        }

        EpubOverflowMenuSection.BOOKMARK -> SharedReaderOverflowItem(
            label = if (state.isBookmarked) labels.removeBookmark else labels.bookmark,
            leadingIcon = if (state.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            onClick = {
                dismiss()
                callbacks.onToggleBookmark()
            }
        )

        EpubOverflowMenuSection.TAP_TO_TURN -> SharedReaderOverflowItem(
            label = labels.tapToTurnPages,
            enabled = state.isPaginated,
            selected = state.tapToNavigateEnabled,
            selectedContentDescription = labels.enabledContentDescription,
            onClick = {
                dismiss()
                callbacks.onToggleTapToNavigate(!state.tapToNavigateEnabled)
            }
        )

        EpubOverflowMenuSection.VOLUME_SCROLL -> SharedReaderOverflowItem(
            label = if (state.readingMode == ReaderReadingMode.VERTICAL) {
                labels.volumeButtonScrolling
            } else {
                labels.volumeButtonPageTurn
            },
            selected = state.volumeScrollEnabled,
            selectedContentDescription = labels.enabledContentDescription,
            onClick = {
                dismiss()
                callbacks.onToggleVolumeScroll?.invoke(!state.volumeScrollEnabled)
            }
        )

        EpubOverflowMenuSection.PAGE_TURN_ANIM -> SharedReaderOverflowItem(
            label = labels.realisticPageTurns,
            enabled = state.isPaginated,
            selected = state.pageTurnAnimationEnabled,
            selectedContentDescription = labels.enabledContentDescription,
            onClick = {
                dismiss()
                callbacks.onTogglePageTurnAnimation(!state.pageTurnAnimationEnabled)
            }
        )

        EpubOverflowMenuSection.KEEP_SCREEN_ON -> SharedReaderOverflowItem(
            label = labels.keepScreenOn,
            selected = state.keepScreenOn,
            selectedContentDescription = labels.enabledContentDescription,
            onClick = {
                dismiss()
                callbacks.onToggleKeepScreenOn(!state.keepScreenOn)
            }
        )

        EpubOverflowMenuSection.VISUAL_OPTIONS -> SharedReaderOverflowItem(
            label = labels.visualOptions,
            leadingIcon = Icons.Default.Visibility,
            onClick = {
                dismiss()
                callbacks.onOpenVisualOptions()
            }
        )

        EpubOverflowMenuSection.AUTO_SCROLL -> SharedReaderOverflowItem(
            label = labels.autoScroll,
            // Android parity (EpubReaderControls, AUTO_SCROLL): needs a scrollable body and an idle
            // narration engine, and the item only ever starts auto-scroll — the surface stops it.
            enabled = !state.isTtsActive && state.readingMode == ReaderReadingMode.VERTICAL,
            onClick = {
                dismiss()
                callbacks.onAutoScrollChange(true)
            }
        )

        EpubOverflowMenuSection.BOOK_REPLACEMENTS -> SharedReaderOverflowItem(
            label = labels.bookWordReplacements,
            leadingIcon = Icons.Default.TextFields,
            onClick = {
                dismiss()
                callbacks.onOpenBookReplacements()
            }
        )

        EpubOverflowMenuSection.TTS_SETTINGS -> {
            // Android parity: the expander always leads the section, and each inner item appears when
            // its own tool is still visible — so hiding only one of the pair leaves a flat item.
            val showVoiceSettings = !state.isToolHidden(ReaderTool.TTS_SETTINGS)
            val showReplacements = !state.isToolHidden(ReaderTool.TTS_REPLACEMENTS)
            SharedReaderOverflowExpanderItem(
                label = labels.ttsSettings,
                leadingIcon = Icons.Default.GraphicEq,
                expanded = menuState.ttsSettingsExpanded.value,
                onClick = { menuState.ttsSettingsExpanded.value = !menuState.ttsSettingsExpanded.value }
            )
            if (menuState.ttsSettingsExpanded.value) {
                if (showVoiceSettings) {
                    SharedReaderOverflowItem(
                        label = labels.ttsVoiceSettings,
                        enabled = !state.isTtsActive,
                        leadingIcon = Icons.Default.GraphicEq,
                        onClick = {
                            dismiss()
                            callbacks.onOpenTtsSettings()
                        }
                    )
                }
                if (showReplacements) {
                    SharedReaderOverflowItem(
                        label = labels.ttsWordReplacements,
                        leadingIcon = Icons.Default.GraphicEq,
                        onClick = {
                            dismiss()
                            callbacks.onOpenTtsReplacements()
                        }
                    )
                }
            }
        }

        EpubOverflowMenuSection.EXPORT_ANNOTATIONS -> SharedReaderOverflowItem(
            label = labels.exportAnnotations,
            leadingIcon = Icons.Default.Share,
            onClick = {
                dismiss()
                callbacks.onExportAnnotations?.invoke()
            }
        )

        EpubOverflowMenuSection.FILE_INFO -> SharedReaderOverflowItem(
            label = labels.fileInformation,
            leadingIcon = Icons.Default.Info,
            onClick = {
                dismiss()
                callbacks.onOpenFileInfo()
            }
        )
    }
}

private fun closeReadingModeMenu(menuState: SharedReaderOverflowMenuState, dismiss: () -> Unit) {
    menuState.readingModeExpanded.value = false
    dismiss()
}

/**
 * The overflow sections this menu renders, in Android's order.
 *
 * The capability gates live here rather than at each call site: a host that cannot reflow a PDF, or
 * cannot read the volume keys, leaves those sections out of the list instead of rendering rows that
 * cannot do anything. Android passes `supportsVolumeScroll = true` and its reflow flags; iOS passes
 * neither, so a volume row can never reach the iOS menu.
 */
fun sharedEpubOverflowMenuSections(
    hiddenToolNamesOrIds: Set<String>,
    hasHiddenToolbarTools: Boolean,
    hasToggleReflow: Boolean,
    hasDeleteReflow: Boolean,
    hasExportAnnotations: Boolean,
    supportsVolumeScroll: Boolean,
): List<EpubOverflowMenuSection> = epubOverflowMenuSections(
    hiddenTools = hiddenToolNamesOrIds,
    hasHiddenToolbarTools = hasHiddenToolbarTools,
    hasToggleReflow = hasToggleReflow,
    hasDeleteReflow = hasDeleteReflow,
    hasExportAnnotations = hasExportAnnotations,
).filter { section ->
    section != EpubOverflowMenuSection.VOLUME_SCROLL || supportsVolumeScroll
}

/**
 * Android benchmark: the hidden-tools list is the tool order filtered to toolbar tools the reader
 * hid. Only [SharedMobileEpubToolbarTools] can be hidden this way — an overflow-category tool that is
 * hidden simply drops out of its own section instead.
 */
fun sharedEpubHiddenToolbarTools(
    toolOrder: List<ReaderTool>,
    hiddenToolNamesOrIds: Set<String>,
): List<ReaderTool> {
    val hiddenIds = hiddenToolNamesOrIds.mapNotNullTo(mutableSetOf<String>()) { ReaderTool.fromId(it)?.id }
    return toolOrder.filter { tool -> tool in SharedMobileEpubToolbarTools && tool.id in hiddenIds }
}

internal fun SharedReaderEpubOverflowState.isToolHidden(tool: ReaderTool): Boolean =
    hiddenToolNamesOrIds.any { ReaderTool.fromId(it) == tool }

@Composable
private fun SharedReaderOverflowItem(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    errorColored: Boolean = false,
    selected: Boolean = false,
    selectedContentDescription: String = "",
) {
    val textColor = if (errorColored) MaterialTheme.colorScheme.error else Color.Unspecified
    val iconTint = if (errorColored) MaterialTheme.colorScheme.error else Color.Unspecified
    DropdownMenuItem(
        text = { Text(label, color = textColor) },
        enabled = enabled,
        onClick = onClick,
        leadingIcon = if (leadingIcon == null) {
            null
        } else {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = selectedContentDescription,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            null
        },
        colors = MenuDefaults.itemColors(textColor = textColor)
    )
}

@Composable
private fun SharedReaderOverflowExpanderItem(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    leadingIcon: ImageVector? = null,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = if (leadingIcon == null) {
            null
        } else {
            { Icon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        trailingIcon = {
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.rotate(if (expanded) 180f else 0f)
            )
        }
    )
}
