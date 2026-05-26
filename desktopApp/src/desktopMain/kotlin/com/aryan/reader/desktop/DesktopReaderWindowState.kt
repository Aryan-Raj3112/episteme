package com.aryan.reader.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.ReaderCloudTtsState
import com.aryan.reader.shared.ReaderExtrasState
import com.aryan.reader.shared.RecapResult
import com.aryan.reader.shared.SummarizationResult
import com.aryan.reader.shared.reader.ReaderReadingMode
import com.aryan.reader.shared.reader.ReaderSessionState
import kotlinx.coroutines.Job

internal const val DesktopReaderWindowDefaultWidthDp = 1120f
internal const val DesktopReaderWindowDefaultHeightDp = 760f
internal val DesktopReaderWindowDefaultSize = DpSize(
    DesktopReaderWindowDefaultWidthDp.dp,
    DesktopReaderWindowDefaultHeightDp.dp
)

internal fun DesktopWindowStateSnapshot.toReaderWindowPlacement(): WindowPlacement {
    return when (placement) {
        DesktopSavedWindowPlacement.FULLSCREEN -> WindowPlacement.Floating
        else -> toWindowPlacement()
    }
}

internal fun DesktopWindowStateSnapshot.toPersistableReaderWindowSnapshot(): DesktopWindowStateSnapshot? {
    if (placement == DesktopSavedWindowPlacement.FULLSCREEN) return null
    return sanitized()
}

internal fun shouldResetDesktopTextReaderWindowSurface(
    previousMode: ReaderReadingMode,
    currentMode: ReaderReadingMode,
    usesNativeWebView: Boolean
): Boolean {
    return usesNativeWebView &&
        previousMode == ReaderReadingMode.VERTICAL &&
        currentMode == ReaderReadingMode.PAGINATED
}

internal data class DesktopReaderWindowState(
    val id: String,
    val opening: DesktopReaderOpening,
    val content: DesktopReaderWindowContent = DesktopReaderWindowContent.Opening,
    val focusRequestId: Long = 0L,
    val fullscreen: Boolean = false,
    val surfaceResetId: Long = 0L
) {
    val bookId: String
        get() = opening.bookId

    val title: String
        get() = when (content) {
            DesktopReaderWindowContent.Opening -> opening.title
            is DesktopReaderWindowContent.PasswordRequired -> content.book.cardTitleForMessage()
            is DesktopReaderWindowContent.Pdf -> content.book.cardTitleForMessage()
            is DesktopReaderWindowContent.Text -> content.book.cardTitleForMessage()
        }

    val formatLabel: String
        get() = opening.formatLabel
}

internal sealed interface DesktopReaderWindowContent {
    data object Opening : DesktopReaderWindowContent

    data class PasswordRequired(
        val book: BookItem,
        val attemptedPassword: Boolean
    ) : DesktopReaderWindowContent

    data class Pdf(
        val book: BookItem,
        val document: DesktopPdfDocument
    ) : DesktopReaderWindowContent

    data class Text(
        val book: BookItem,
        val session: ReaderSessionState,
        val extrasState: ReaderExtrasState = ReaderExtrasState(
            cloudTts = ReaderCloudTtsState()
        ),
        val showAiHub: Boolean = false,
        val readerAiResultRequestId: Long = 0L,
        val dismissedReaderAiResultRequestId: Long? = null,
        val summaryResult: SummarizationResult? = null,
        val recapResult: RecapResult? = null,
        val isSummaryLoading: Boolean = false,
        val isRecapLoading: Boolean = false,
        val recapProgressMessage: String? = null,
        val ttsJob: Job? = null
    ) : DesktopReaderWindowContent
}

internal data class DesktopReaderWindowOpenDecision(
    val windows: List<DesktopReaderWindowState>,
    val shouldStartOpen: Boolean
)

internal fun List<DesktopReaderWindowState>.openOrFocusDesktopReaderWindow(
    opening: DesktopReaderOpening,
    force: Boolean
): DesktopReaderWindowOpenDecision {
    val existing = firstOrNull { it.bookId == opening.bookId }
    if (existing != null && !force) {
        return DesktopReaderWindowOpenDecision(
            windows = map { window ->
                if (window.id == existing.id) {
                    window.copy(focusRequestId = window.focusRequestId + 1)
                } else {
                    window
                }
            },
            shouldStartOpen = false
        )
    }

    val replacement = DesktopReaderWindowState(
        id = existing?.id ?: opening.bookId.ifBlank { opening.requestId.toString() },
        opening = opening,
        focusRequestId = (existing?.focusRequestId ?: 0L) + 1
    )
    return DesktopReaderWindowOpenDecision(
        windows = filterNot { it.bookId == opening.bookId } + replacement,
        shouldStartOpen = true
    )
}

internal fun List<DesktopReaderWindowState>.focusDesktopReaderWindow(bookId: String): List<DesktopReaderWindowState> {
    return map { window ->
        if (window.bookId == bookId) {
            window.copy(focusRequestId = window.focusRequestId + 1)
        } else {
            window
        }
    }
}

internal fun List<DesktopReaderWindowState>.withDesktopReaderWindowContent(
    requestId: Long,
    content: DesktopReaderWindowContent
): List<DesktopReaderWindowState> {
    return map { window ->
        if (window.opening.requestId == requestId) {
            window.copy(content = content)
        } else {
            window
        }
    }
}

internal fun List<DesktopReaderWindowState>.withoutDesktopReaderWindow(windowId: String): List<DesktopReaderWindowState> {
    return filterNot { it.id == windowId }
}

internal fun List<DesktopReaderWindowState>.withoutDesktopReaderBookIds(
    bookIds: Set<String>
): List<DesktopReaderWindowState> {
    return filterNot { it.bookId in bookIds }
}

internal fun List<DesktopReaderWindowState>.replaceDesktopTextReaderContent(
    windowId: String,
    transform: (DesktopReaderWindowContent.Text) -> DesktopReaderWindowContent.Text
): List<DesktopReaderWindowState> {
    return map { window ->
        val content = window.content
        if (window.id == windowId && content is DesktopReaderWindowContent.Text) {
            window.copy(content = transform(content))
        } else {
            window
        }
    }
}

internal fun List<DesktopReaderWindowState>.replaceAllDesktopTextReaderContent(
    transform: (DesktopReaderWindowContent.Text) -> DesktopReaderWindowContent.Text
): List<DesktopReaderWindowState> {
    return map { window ->
        val content = window.content
        if (content is DesktopReaderWindowContent.Text) {
            window.copy(content = transform(content))
        } else {
            window
        }
    }
}
