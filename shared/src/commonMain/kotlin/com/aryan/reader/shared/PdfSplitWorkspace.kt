package com.aryan.reader.shared

/**
 * The arrangement of the two document panes in the PDF workspace.
 *
 * VERTICAL means two side-by-side panes separated by a vertical divider.
 * HORIZONTAL means two stacked panes separated by a horizontal divider.
 */
enum class PdfSplitOrientation {
    VERTICAL,
    HORIZONTAL,
}

enum class PdfSplitPane {
    PRIMARY,
    SECONDARY,
}

/**
 * Portable identity for a PDF that is assigned to a workspace slot.
 * Platform document handles and renderer state stay outside this model.
 */
data class PdfSplitPaneState(
    val bookId: String,
    val uriString: String,
)

data class PdfSplitWorkspaceState(
    val orientation: PdfSplitOrientation = PdfSplitOrientation.VERTICAL,
    val primary: PdfSplitPaneState? = null,
    val secondary: PdfSplitPaneState? = null,
    val focusedPane: PdfSplitPane = PdfSplitPane.PRIMARY,
    val dividerFraction: Float = DefaultPdfSplitDividerFraction,
) {
    val isOpen: Boolean
        get() = primary != null

    val isSplit: Boolean
        get() = primary != null && secondary != null

    fun pane(id: PdfSplitPane): PdfSplitPaneState? = when (id) {
        PdfSplitPane.PRIMARY -> primary
        PdfSplitPane.SECONDARY -> secondary
    }

    fun sanitized(): PdfSplitWorkspaceState {
        val sanitizedPrimary = primary?.sanitized()
        val sanitizedSecondary = secondary
            ?.sanitized()
            ?.takeUnless { candidate -> candidate.matches(sanitizedPrimary) }

        return copy(
            primary = sanitizedPrimary,
            secondary = sanitizedSecondary,
            focusedPane = if (sanitizedSecondary == null) PdfSplitPane.PRIMARY else focusedPane,
            dividerFraction = dividerFraction.coerceIn(
                MinimumPdfSplitDividerFraction,
                MaximumPdfSplitDividerFraction,
            ),
        )
    }
}

internal fun PdfSplitPaneState.sanitized(): PdfSplitPaneState? {
    val cleanBookId = bookId.trim()
    val cleanUri = uriString.trim()
    return if (cleanBookId.isBlank() || cleanUri.isBlank()) {
        null
    } else {
        copy(bookId = cleanBookId, uriString = cleanUri)
    }
}

private fun PdfSplitPaneState.matches(other: PdfSplitPaneState?): Boolean {
    return other != null && (bookId == other.bookId || uriString == other.uriString)
}

sealed interface PdfSplitWorkspaceAction {
    data class Open(
        val primary: PdfSplitPaneState,
        val secondary: PdfSplitPaneState,
        val orientation: PdfSplitOrientation = PdfSplitOrientation.VERTICAL,
    ) : PdfSplitWorkspaceAction

    data class FocusChanged(val pane: PdfSplitPane) : PdfSplitWorkspaceAction
    data class OrientationChanged(val orientation: PdfSplitOrientation) : PdfSplitWorkspaceAction
    data class DividerChanged(val fraction: Float) : PdfSplitWorkspaceAction
    data class PaneOpened(
        val pane: PdfSplitPane,
        val document: PdfSplitPaneState,
    ) : PdfSplitWorkspaceAction
    data class PaneClosed(val pane: PdfSplitPane) : PdfSplitWorkspaceAction
    data object PanesSwapped : PdfSplitWorkspaceAction
    data object Closed : PdfSplitWorkspaceAction
}

fun PdfSplitWorkspaceState.reduce(action: PdfSplitWorkspaceAction): PdfSplitWorkspaceState {
    val next = when (action) {
        is PdfSplitWorkspaceAction.Open -> PdfSplitWorkspaceState(
            orientation = action.orientation,
            primary = action.primary,
            secondary = action.secondary,
            focusedPane = PdfSplitPane.PRIMARY,
        )

        is PdfSplitWorkspaceAction.FocusChanged -> copy(
            focusedPane = action.pane.takeIf { pane(it) != null } ?: PdfSplitPane.PRIMARY,
        )

        is PdfSplitWorkspaceAction.OrientationChanged -> copy(orientation = action.orientation)

        is PdfSplitWorkspaceAction.DividerChanged -> copy(
            dividerFraction = action.fraction.coerceIn(
                MinimumPdfSplitDividerFraction,
                MaximumPdfSplitDividerFraction,
            ),
        )

        is PdfSplitWorkspaceAction.PaneOpened -> {
            val cleanDocument = action.document.sanitized()
            if (cleanDocument == null) {
                this
            } else if (action.pane == PdfSplitPane.PRIMARY) {
                copy(primary = cleanDocument)
            } else if (cleanDocument.matches(primary)) {
                this
            } else {
                copy(secondary = cleanDocument)
            }
        }

        is PdfSplitWorkspaceAction.PaneClosed -> when (action.pane) {
            PdfSplitPane.PRIMARY -> if (secondary == null) {
                PdfSplitWorkspaceState()
            } else {
                copy(
                    primary = secondary,
                    secondary = null,
                    focusedPane = PdfSplitPane.PRIMARY,
                )
            }

            PdfSplitPane.SECONDARY -> copy(
                secondary = null,
                focusedPane = PdfSplitPane.PRIMARY,
            )
        }

        PdfSplitWorkspaceAction.PanesSwapped -> if (secondary == null) {
            this
        } else {
            copy(primary = secondary, secondary = primary)
        }

        PdfSplitWorkspaceAction.Closed -> PdfSplitWorkspaceState()
    }

    return next.sanitized()
}

const val DefaultPdfSplitDividerFraction = 0.5f
const val MinimumPdfSplitDividerFraction = 0.25f
const val MaximumPdfSplitDividerFraction = 0.75f
