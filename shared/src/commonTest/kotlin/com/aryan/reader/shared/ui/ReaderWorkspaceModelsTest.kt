package com.aryan.reader.shared.ui

import com.aryan.reader.shared.PdfDisplayMode
import com.aryan.reader.shared.ReaderAutoScrollState
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.ReaderTool
import com.aryan.reader.shared.ReaderToolbarPreferences
import com.aryan.reader.shared.pdf.SharedPdfReaderState
import com.aryan.reader.shared.reader.ReaderEngine
import com.aryan.reader.shared.reader.SharedEpubBook
import com.aryan.reader.shared.reader.SharedEpubChapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderWorkspaceModelsTest {

    @Test
    fun `epub workspace maps shared toolbar preferences without toolbar tab`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = setOf(ReaderTool.THEME.id, ReaderTool.FORMAT.id, ReaderTool.BOOKMARK.id),
            bottomToolIds = setOf(ReaderTool.SLIDER.id, ReaderTool.SEARCH.id)
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = true
        )

        assertEquals(ReaderWorkspaceKind.EPUB, model.kind)
        assertEquals(
            listOf(
                ReaderWorkspaceLeftSection.CONTENTS,
                ReaderWorkspaceLeftSection.NOTES,
                ReaderWorkspaceLeftSection.BOOKMARKS,
                ReaderWorkspaceLeftSection.IMAGES
            ),
            model.leftSections
        )
        assertFalse(ReaderWorkspaceLeftSection.SEARCH in model.leftSections)
        assertFalse(ReaderWorkspaceTopAction.BOOKMARK in model.topActions)
        assertTrue(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
        assertFalse(ReaderWorkspaceInspectorSection.TOOLBAR in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.SEARCH in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.FULL_SCREEN in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.AI in model.topActions)
        assertTrue(ReaderWorkspaceBottomAction.PAGE_SLIDER in model.bottomActions)
        assertFalse(model.panelDefaults.leftOpen)
        assertFalse(model.panelDefaults.inspectorOpen)
        assertTrue(model.chrome.preferAutoHide)
    }

    @Test
    fun `chrome model is forced visible for active reader states`() {
        val model = readerWorkspaceChromeModel(
            preferAutoHide = false,
            searchActive = true,
            leftPanelOpen = false,
            inspectorOpen = true,
            annotationEditing = true,
            richTextEditing = true,
            loading = true,
            errorMessage = "Failed",
            autoScroll = ReaderAutoScrollState(enabled = true),
            ttsBusy = true
        )

        assertFalse(model.preferAutoHide)
        assertTrue(model.forceVisible)
        assertEquals(
            setOf("search", "inspector", "annotation", "rich-text", "loading", "error", "auto-scroll"),
            model.forceVisibleReasons
        )
        assertEquals(setOf("tts"), model.revealVisibleReasons)
    }

    @Test
    fun `reader tap toggles chrome when it is not locked or forced`() {
        assertTrue(
            readerWorkspaceChromeVisibleAfterReaderTap(
                requestedVisible = false,
                lockedVisible = false,
                forcedVisible = false
            )
        )
        assertFalse(
            readerWorkspaceChromeVisibleAfterReaderTap(
                requestedVisible = true,
                lockedVisible = false,
                forcedVisible = false
            )
        )
    }

    @Test
    fun `reader tap closes inspector and reveals chrome when panel suppresses bars`() {
        assertTrue(
            readerWorkspaceChromeVisibleAfterReaderTap(
                requestedVisible = false,
                lockedVisible = false,
                forcedVisible = false,
                rightPanelClosedByTap = true
            )
        )
        assertTrue(
            readerWorkspaceShouldCloseRightPanelAfterReaderTap(
                rightPanelOpen = true,
                hasInspectorSections = true,
                closeRightPanelOnReaderTap = true
            )
        )
        assertFalse(
            readerWorkspaceShouldCloseRightPanelAfterReaderTap(
                rightPanelOpen = true,
                hasInspectorSections = false,
                closeRightPanelOnReaderTap = true
            )
        )
        assertFalse(
            readerWorkspaceShouldCloseRightPanelAfterReaderTap(
                rightPanelOpen = true,
                hasInspectorSections = true,
                closeRightPanelOnReaderTap = false
            )
        )
    }

    @Test
    fun `active tts reveals chrome without locking reader tap toggle`() {
        val model = readerWorkspaceChromeModel(
            preferAutoHide = true,
            searchActive = false,
            leftPanelOpen = false,
            inspectorOpen = false,
            annotationEditing = false,
            richTextEditing = false,
            loading = false,
            errorMessage = null,
            autoScroll = ReaderAutoScrollState(),
            ttsBusy = true
        )

        assertFalse(model.forceVisible)
        assertEquals(emptySet(), model.forceVisibleReasons)
        assertEquals(setOf("tts"), model.revealVisibleReasons)
        assertFalse(
            readerWorkspaceChromeVisibleAfterReaderTap(
                requestedVisible = true,
                lockedVisible = false,
                forcedVisible = model.forceVisible
            )
        )
    }

    @Test
    fun `locked or forced reader chrome stays visible after reader taps`() {
        assertTrue(
            readerWorkspaceChromeVisible(
                requestedVisible = false,
                lockedVisible = true,
                forcedVisible = false
            )
        )
        assertTrue(
            readerWorkspaceChromeVisibleAfterReaderTap(
                requestedVisible = false,
                lockedVisible = false,
                forcedVisible = true
            )
        )
    }

    @Test
    fun `left reader panel is drawn with chrome while preserving its toggle state`() {
        assertFalse(
            readerWorkspaceLeftPanelVisible(
                toggledOpen = true,
                chromeVisible = false,
                hasNavigationSections = true
            )
        )
        assertTrue(
            readerWorkspaceLeftPanelVisible(
                toggledOpen = true,
                chromeVisible = true,
                hasNavigationSections = true
            )
        )
        assertFalse(
            readerWorkspaceLeftPanelVisible(
                toggledOpen = false,
                chromeVisible = true,
                hasNavigationSections = true
            )
        )
    }

    @Test
    fun `reader focus is restored after closing the final workspace panel`() {
        assertTrue(
            readerWorkspaceShouldRestoreFocusAfterPanelClose(
                closingPanelOpen = true,
                otherPanelOpen = false
            )
        )
        assertFalse(
            readerWorkspaceShouldRestoreFocusAfterPanelClose(
                closingPanelOpen = false,
                otherPanelOpen = false
            )
        )
        assertFalse(
            readerWorkspaceShouldRestoreFocusAfterPanelClose(
                closingPanelOpen = true,
                otherPanelOpen = true
            )
        )
    }

    @Test
    fun `reader focus is restored when an open sidebar is hidden with chrome`() {
        assertTrue(
            readerWorkspaceShouldRestoreFocusAfterPanelVisibilityChange(
                wasPanelVisible = true,
                isPanelVisible = false,
                panelOpen = true,
                otherPanelOpen = false
            )
        )
        assertFalse(
            readerWorkspaceShouldRestoreFocusAfterPanelVisibilityChange(
                wasPanelVisible = true,
                isPanelVisible = true,
                panelOpen = true,
                otherPanelOpen = false
            )
        )
        assertFalse(
            readerWorkspaceShouldRestoreFocusAfterPanelVisibilityChange(
                wasPanelVisible = true,
                isPanelVisible = false,
                panelOpen = true,
                otherPanelOpen = true
            )
        )
        assertFalse(
            readerWorkspaceShouldRestoreFocusAfterPanelVisibilityChange(
                wasPanelVisible = true,
                isPanelVisible = false,
                panelOpen = false,
                otherPanelOpen = false
            )
        )
    }

    @Test
    fun `epub workspace exposes visual options through tools popup`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries
                .filterNot { it == ReaderTool.VISUAL_OPTIONS }
                .mapTo(mutableSetOf()) { it.id }
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = true
        )

        assertTrue(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.TOOLS in model.topActions)
    }

    @Test
    fun `epub workspace maps reading mode into appearance popup instead of tools inspector`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries
                .filterNot { it == ReaderTool.READING_MODE }
                .mapTo(mutableSetOf()) { it.id }
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = false
        )

        assertTrue(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertFalse(ReaderWorkspaceInspectorSection.TOOLS in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.TOOLS in model.topActions)
    }

    @Test
    fun `epub workspace ignores external lookup in inspector`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries
                .filterNot { it == ReaderTool.DICTIONARY }
                .mapTo(mutableSetOf()) { it.id }
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = true
        )

        assertFalse(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
        assertFalse(ReaderWorkspaceTopAction.TOOLS in model.topActions)
    }

    @Test
    fun `epub workspace exposes tools popup for desktop app theme controls`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries.mapTo(mutableSetOf()) { it.id }
        )

        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = false,
            appThemeControlsAvailable = true
        )

        assertTrue(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.TOOLS in model.topActions)
    }

    @Test
    fun `toolbar quick actions preserve visibility order and bottom placement`() {
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = setOf(ReaderTool.BOOKMARK.id),
            toolOrder = listOf(
                ReaderTool.SEARCH,
                ReaderTool.AI_FEATURES,
                ReaderTool.THEME,
                ReaderTool.BOOKMARK
            ) + ReaderTool.entries,
            bottomToolIds = setOf(ReaderTool.SEARCH.id, ReaderTool.AI_FEATURES.id)
        )

        val topTools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = false,
            aiAvailable = true
        )
        val bottomToolsWithoutAi = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = false
        )
        val bottomToolsWithAi = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = true
        )

        assertEquals(ReaderTool.THEME, topTools.first())
        assertEquals(listOf(ReaderTool.SEARCH), bottomToolsWithoutAi)
        assertEquals(listOf(ReaderTool.SEARCH), bottomToolsWithAi)
        assertFalse(ReaderTool.BOOKMARK in topTools)
        assertFalse(ReaderTool.BOOKMARK in bottomToolsWithAi)
        assertFalse(ReaderTool.AI_FEATURES in bottomToolsWithAi)
    }

    @Test
    fun `toolbar quick actions hide online tools when unavailable`() {
        val preferences = ReaderToolbarPreferences(
            toolOrder = listOf(
                ReaderTool.DICTIONARY,
                ReaderTool.SEARCH,
                ReaderTool.AI_FEATURES,
                ReaderTool.TTS_CONTROLS
            ) + ReaderTool.entries,
            bottomToolIds = setOf(
                ReaderTool.DICTIONARY.id,
                ReaderTool.SEARCH.id,
                ReaderTool.AI_FEATURES.id,
                ReaderTool.TTS_CONTROLS.id
            )
        )

        val tools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = false
        )

        assertEquals(listOf(ReaderTool.SEARCH), tools)
    }

    @Test
    fun `tts controls use top read aloud action instead of toolbar quick action`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            toolOrder = listOf(ReaderTool.TTS_CONTROLS) + ReaderTool.entries,
            bottomToolIds = setOf(ReaderTool.TTS_CONTROLS.id)
        )

        val tools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = false,
            cloudTtsAvailable = true,
            externalLookupAvailable = true
        )
        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = false,
            cloudTtsAvailable = true,
            externalLookupAvailable = true
        )

        assertFalse(ReaderTool.TTS_CONTROLS in tools)
        assertTrue(ReaderWorkspaceTopAction.READ_ALOUD in model.topActions)
    }

    @Test
    fun `ai features use top hub action instead of toolbar quick action`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = ReaderTool.entries
                .filterNot { it == ReaderTool.AI_FEATURES }
                .mapTo(mutableSetOf()) { it.id },
            toolOrder = listOf(ReaderTool.AI_FEATURES) + ReaderTool.entries,
            bottomToolIds = setOf(ReaderTool.AI_FEATURES.id)
        )

        val tools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = true,
            aiAvailable = true,
            cloudTtsAvailable = false,
            externalLookupAvailable = true
        )
        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(),
            aiAvailable = true,
            cloudTtsAvailable = false,
            externalLookupAvailable = true
        )

        assertFalse(ReaderTool.AI_FEATURES in tools)
        assertTrue(ReaderWorkspaceTopAction.AI in model.topActions)
        assertFalse(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
    }

    @Test
    fun `retired auto scroll preferences are ignored for desktop reader tools`() {
        val session = ReaderEngine().createSession(readerFixtureBook())
        val preferences = ReaderToolbarPreferences(
            hiddenToolIds = setOf("auto_scroll"),
            toolOrder = ReaderTool.entries
        )

        val tools = readerWorkspaceQuickActionTools(
            toolbarPreferences = preferences,
            bottom = false,
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = false
        )
        val model = epubReaderWorkspaceModel(
            session = session,
            toolbarPreferences = preferences,
            extrasState = ReaderExtrasState(autoScroll = ReaderAutoScrollState(enabled = true)),
            aiAvailable = false,
            cloudTtsAvailable = false,
            externalLookupAvailable = false
        )

        assertNull(ReaderTool.fromId("auto_scroll"))
        assertFalse("auto_scroll" in preferences.sanitized().hiddenToolIds)
        assertFalse(tools.any { it.id == "auto_scroll" })
        assertFalse(ReaderWorkspaceTopAction.TOOLS in model.topActions)
        assertFalse("auto-scroll" in model.chrome.forceVisibleReasons)
    }

    @Test
    fun `pdf workspace defaults to reading first while keeping annotation tools in inspector`() {
        val model = pdfReaderWorkspaceModel(
            state = SharedPdfReaderState.initial(pageCount = 4),
            displayMode = PdfDisplayMode.PAGINATION,
            hasContents = true,
            hasBookmarks = true,
            hasAnnotations = true,
            hasEmbeddedComments = true,
            searchActive = false,
            annotationEditing = false,
            richTextEditing = false,
            loading = false,
            errorMessage = null,
            extrasState = ReaderExtrasState(),
            aiAvailable = true
        )

        assertEquals(ReaderWorkspaceKind.PDF, model.kind)
        assertNull(model.defaultPdfInteractionMode)
        assertEquals(
            listOf(
                ReaderWorkspaceLeftSection.CONTENTS,
                ReaderWorkspaceLeftSection.NOTES,
                ReaderWorkspaceLeftSection.BOOKMARKS,
                ReaderWorkspaceLeftSection.PAGES
            ),
            model.leftSections
        )
        assertFalse(ReaderWorkspaceLeftSection.SEARCH in model.leftSections)
        assertTrue(ReaderWorkspaceInspectorSection.APPEARANCE in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.TOOLS in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.AI_TTS in model.inspectorSections)
        assertTrue(ReaderWorkspaceInspectorSection.TOOLBAR in model.inspectorSections)
        assertTrue(ReaderWorkspaceTopAction.BOOKMARK in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.FULL_SCREEN in model.topActions)
        assertTrue(ReaderWorkspaceTopAction.AI in model.topActions)
        assertFalse(model.panelDefaults.leftOpen)
        assertFalse(model.panelDefaults.inspectorOpen)
        assertTrue(model.chrome.preferAutoHide)
    }

    @Test
    fun `pdf workspace forces chrome for search editing errors and reveals tts`() {
        val model = pdfReaderWorkspaceModel(
            state = SharedPdfReaderState.initial(pageCount = 4).copy(searchQuery = "needle"),
            displayMode = PdfDisplayMode.VERTICAL_SCROLL,
            hasContents = false,
            hasBookmarks = false,
            hasAnnotations = false,
            hasEmbeddedComments = false,
            searchActive = false,
            annotationEditing = true,
            richTextEditing = false,
            loading = false,
            errorMessage = "Problem",
            extrasState = ReaderExtrasState(
                autoScroll = ReaderAutoScrollState(enabled = true),
                cloudTts = ReaderCloudTtsState(isPlaying = true)
            ),
            aiAvailable = false
        )

        assertTrue(model.chrome.forceVisible)
        assertTrue(model.chrome.preferAutoHide)
        assertTrue("search" in model.chrome.forceVisibleReasons)
        assertTrue("annotation" in model.chrome.forceVisibleReasons)
        assertTrue("error" in model.chrome.forceVisibleReasons)
        assertFalse("auto-scroll" in model.chrome.forceVisibleReasons)
        assertFalse("tts" in model.chrome.forceVisibleReasons)
        assertTrue("tts" in model.chrome.revealVisibleReasons)
        assertFalse(ReaderWorkspaceTopAction.AI in model.topActions)
    }

    private fun readerFixtureBook(): SharedEpubBook {
        return SharedEpubBook(
            id = "reader_fixture",
            fileName = "Reader Fixture.epub",
            title = "Reader Fixture",
            chapters = listOf(
                SharedEpubChapter(
                    id = "intro",
                    title = "Intro",
                    plainText = "A short reader fixture for workspace model tests."
                )
            )
        )
    }
}
