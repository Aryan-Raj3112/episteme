package com.aryan.reader.shared.ui

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.pdf.PdfInkTool
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.reader.ReaderSessionState

enum class ReaderWorkspaceKind {
    EPUB,
    PDF
}

enum class ReaderWorkspaceLeftSection(val title: String) {
    CONTENTS("Contents"),
    IMAGES("Images"),
    SEARCH("Search"),
    BOOKMARKS("Bookmarks"),
    NOTES("Annotations"),
    PAGES("Pages")
}

enum class ReaderWorkspaceInspectorSection(val title: String) {
    APPEARANCE("Appearance"),
    TOOLS("Tools"),
    AI_TTS("AI/TTS"),
    TOOLBAR("Toolbar")
}

enum class ReaderWorkspaceTopAction {
    CONTENTS,
    SEARCH,
    BOOKMARK,
    FILE_ACTIONS,
    FULL_SCREEN,
    APPEARANCE,
    READ_ALOUD,
    AI,
    TOOLS
}

enum class ReaderWorkspaceBottomAction {
    PAGE_SLIDER,
    PREVIOUS,
    NEXT
}

data class ReaderWorkspaceChromeModel(
    val preferAutoHide: Boolean,
    val forceVisible: Boolean,
    val forceVisibleReasons: Set<String> = emptySet(),
    val revealVisibleReasons: Set<String> = emptySet()
)

internal fun readerWorkspaceChromeVisible(
    requestedVisible: Boolean,
    lockedVisible: Boolean,
    forcedVisible: Boolean
): Boolean {
    return lockedVisible || forcedVisible || requestedVisible
}

internal fun readerWorkspaceChromeVisibleAfterReaderTap(
    requestedVisible: Boolean,
    lockedVisible: Boolean,
    forcedVisible: Boolean,
    rightPanelClosedByTap: Boolean = false
): Boolean {
    return if (lockedVisible || forcedVisible || rightPanelClosedByTap) {
        true
    } else {
        !requestedVisible
    }
}

internal fun readerWorkspaceShouldCloseRightPanelAfterReaderTap(
    rightPanelOpen: Boolean,
    hasInspectorSections: Boolean,
    closeRightPanelOnReaderTap: Boolean
): Boolean {
    return closeRightPanelOnReaderTap && rightPanelOpen && hasInspectorSections
}

internal fun readerWorkspaceLeftPanelVisible(
    toggledOpen: Boolean,
    chromeVisible: Boolean,
    hasNavigationSections: Boolean
): Boolean {
    return toggledOpen && chromeVisible && hasNavigationSections
}

data class ReaderWorkspaceFileActionState(
    val canShare: Boolean = false,
    val canSaveCopy: Boolean = false,
    val canPrint: Boolean = false,
    val canGenerateTextView: Boolean = false,
    val hasGeneratedTextView: Boolean = false,
    val isGeneratingTextView: Boolean = false
) {
    val hasAnyAction: Boolean
        get() = canShare ||
            canSaveCopy ||
            canPrint ||
            canGenerateTextView ||
            hasGeneratedTextView ||
            isGeneratingTextView
}

data class ReaderWorkspacePanelDefaults(
    val leftOpen: Boolean = false,
    val inspectorOpen: Boolean = false
)

data class ReaderWorkspaceModel(
    val kind: ReaderWorkspaceKind,
    val leftSections: List<ReaderWorkspaceLeftSection>,
    val inspectorSections: List<ReaderWorkspaceInspectorSection>,
    val topActions: List<ReaderWorkspaceTopAction>,
    val bottomActions: List<ReaderWorkspaceBottomAction>,
    val defaultPdfInteractionMode: PdfInkTool? = null,
    val panelDefaults: ReaderWorkspacePanelDefaults = ReaderWorkspacePanelDefaults(),
    val chrome: ReaderWorkspaceChromeModel
)

fun epubReaderWorkspaceModel(
    session: ReaderSessionState,
    toolbarPreferences: ReaderToolbarPreferences,
    extrasState: ReaderExtrasState,
    aiAvailable: Boolean,
    cloudTtsAvailable: Boolean = true,
    externalLookupAvailable: Boolean = true,
    appThemeControlsAvailable: Boolean = false
): ReaderWorkspaceModel {
    val preferences = toolbarPreferences.sanitized()
    val leftSections = listOf(
        ReaderWorkspaceLeftSection.CONTENTS,
        ReaderWorkspaceLeftSection.NOTES,
        ReaderWorkspaceLeftSection.BOOKMARKS,
        ReaderWorkspaceLeftSection.IMAGES
    )
    val inspectorSections = buildList {
        if (
            appThemeControlsAvailable ||
            preferences.isVisible(ReaderTool.THEME) ||
            preferences.isVisible(ReaderTool.FORMAT) ||
            preferences.isVisible(ReaderTool.VISUAL_OPTIONS) ||
            preferences.isVisible(ReaderTool.READING_MODE)
        ) {
            add(ReaderWorkspaceInspectorSection.APPEARANCE)
        }
        if (
            (cloudTtsAvailable && (
                preferences.isVisible(ReaderTool.TTS_CONTROLS) ||
                    preferences.isVisible(ReaderTool.TTS_SETTINGS)
                )) ||
            preferences.isVisible(ReaderTool.TTS_REPLACEMENTS)
        ) {
            add(ReaderWorkspaceInspectorSection.AI_TTS)
        }
    }.distinct()
    val topActions = buildList {
        if (ReaderWorkspaceLeftSection.CONTENTS in leftSections) add(ReaderWorkspaceTopAction.CONTENTS)
        if (preferences.isVisible(ReaderTool.SEARCH)) add(ReaderWorkspaceTopAction.SEARCH)
        if (preferences.isVisible(ReaderTool.BOOKMARK)) add(ReaderWorkspaceTopAction.BOOKMARK)
        add(ReaderWorkspaceTopAction.FULL_SCREEN)
        if (ReaderWorkspaceInspectorSection.APPEARANCE in inspectorSections) add(ReaderWorkspaceTopAction.APPEARANCE)
        if (cloudTtsAvailable && preferences.isVisible(ReaderTool.TTS_CONTROLS)) add(ReaderWorkspaceTopAction.READ_ALOUD)
        if (aiAvailable && preferences.isVisible(ReaderTool.AI_FEATURES)) add(ReaderWorkspaceTopAction.AI)
        if (inspectorSections.isNotEmpty()) add(ReaderWorkspaceTopAction.TOOLS)
    }.distinct()
    val bottomActions = buildList {
        if (preferences.isVisible(ReaderTool.SLIDER)) add(ReaderWorkspaceBottomAction.PAGE_SLIDER)
        add(ReaderWorkspaceBottomAction.PREVIOUS)
        add(ReaderWorkspaceBottomAction.NEXT)
    }
    return ReaderWorkspaceModel(
        kind = ReaderWorkspaceKind.EPUB,
        leftSections = leftSections,
        inspectorSections = inspectorSections,
        topActions = topActions,
        bottomActions = bottomActions,
        chrome = readerWorkspaceChromeModel(
            preferAutoHide = true,
            searchActive = session.isSearchActive,
            leftPanelOpen = false,
            inspectorOpen = false,
            annotationEditing = false,
            richTextEditing = false,
            loading = false,
            errorMessage = null,
            autoScroll = ReaderAutoScrollState(),
            ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
        )
    )
}

fun readerWorkspaceQuickActionTools(
    toolbarPreferences: ReaderToolbarPreferences,
    bottom: Boolean,
    aiAvailable: Boolean,
    cloudTtsAvailable: Boolean = true,
    externalLookupAvailable: Boolean = true
): List<ReaderTool> {
    val preferences = toolbarPreferences.sanitized()
    return preferences.orderedVisibleTools()
        .filter { tool ->
            tool.supportsDesktopQuickAction &&
                preferences.isBottom(tool) == bottom &&
                (tool != ReaderTool.AI_FEATURES || aiAvailable) &&
                (tool != ReaderTool.TTS_CONTROLS || cloudTtsAvailable) &&
                (tool != ReaderTool.DICTIONARY || externalLookupAvailable)
        }
}

fun pdfReaderWorkspaceModel(
    state: SharedPdfReaderState,
    displayMode: PdfDisplayMode,
    hasContents: Boolean,
    hasBookmarks: Boolean,
    hasAnnotations: Boolean,
    hasEmbeddedComments: Boolean,
    searchActive: Boolean,
    annotationEditing: Boolean,
    richTextEditing: Boolean,
    loading: Boolean,
    errorMessage: String?,
    extrasState: ReaderExtrasState,
    aiAvailable: Boolean,
    cloudTtsAvailable: Boolean = true,
    externalLookupAvailable: Boolean = true
): ReaderWorkspaceModel {
    val leftSections = buildList {
        add(ReaderWorkspaceLeftSection.CONTENTS)
        add(ReaderWorkspaceLeftSection.NOTES)
        add(ReaderWorkspaceLeftSection.BOOKMARKS)
        add(ReaderWorkspaceLeftSection.PAGES)
    }.distinct()
    val inspectorSections = listOf(
        ReaderWorkspaceInspectorSection.APPEARANCE,
        ReaderWorkspaceInspectorSection.TOOLS,
        ReaderWorkspaceInspectorSection.AI_TTS,
        ReaderWorkspaceInspectorSection.TOOLBAR
    )
    val topActions = buildList {
        add(ReaderWorkspaceTopAction.CONTENTS)
        add(ReaderWorkspaceTopAction.SEARCH)
        add(ReaderWorkspaceTopAction.BOOKMARK)
        add(ReaderWorkspaceTopAction.FILE_ACTIONS)
        add(ReaderWorkspaceTopAction.FULL_SCREEN)
        add(ReaderWorkspaceTopAction.APPEARANCE)
        if (cloudTtsAvailable) add(ReaderWorkspaceTopAction.READ_ALOUD)
        if (aiAvailable) add(ReaderWorkspaceTopAction.AI)
        add(ReaderWorkspaceTopAction.TOOLS)
    }
    return ReaderWorkspaceModel(
        kind = ReaderWorkspaceKind.PDF,
        leftSections = leftSections,
        inspectorSections = inspectorSections,
        topActions = topActions,
        bottomActions = listOf(
            ReaderWorkspaceBottomAction.PAGE_SLIDER,
            ReaderWorkspaceBottomAction.PREVIOUS,
            ReaderWorkspaceBottomAction.NEXT
        ),
        defaultPdfInteractionMode = null,
        chrome = readerWorkspaceChromeModel(
            preferAutoHide = true,
            searchActive = searchActive || state.searchQuery.isNotBlank(),
            leftPanelOpen = false,
            inspectorOpen = false,
            annotationEditing = annotationEditing || state.selectedAnnotationId != null || state.selectedTool != PdfInkTool.NONE,
            richTextEditing = richTextEditing,
            loading = loading,
            errorMessage = errorMessage,
            autoScroll = ReaderAutoScrollState(),
            ttsBusy = extrasState.cloudTts.isLoading || extrasState.cloudTts.isPlaying || extrasState.cloudTts.isPaused
        )
    )
}

fun readerWorkspaceChromeModel(
    preferAutoHide: Boolean,
    searchActive: Boolean,
    leftPanelOpen: Boolean,
    inspectorOpen: Boolean,
    annotationEditing: Boolean,
    richTextEditing: Boolean,
    loading: Boolean,
    errorMessage: String?,
    autoScroll: ReaderAutoScrollState,
    ttsBusy: Boolean
): ReaderWorkspaceChromeModel {
    val forceReasons = buildSet {
        if (searchActive) add("search")
        if (leftPanelOpen) add("left-panel")
        if (inspectorOpen) add("inspector")
        if (annotationEditing) add("annotation")
        if (richTextEditing) add("rich-text")
        if (loading) add("loading")
        if (!errorMessage.isNullOrBlank()) add("error")
        if (autoScroll.sanitized().enabled) add("auto-scroll")
    }
    val revealReasons = buildSet {
        if (ttsBusy) add("tts")
    }
    return ReaderWorkspaceChromeModel(
        preferAutoHide = preferAutoHide,
        forceVisible = forceReasons.isNotEmpty(),
        forceVisibleReasons = forceReasons,
        revealVisibleReasons = revealReasons
    )
}
