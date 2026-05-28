package com.aryan.reader.desktop

import com.aryan.reader.shared.BookItem
import com.aryan.reader.shared.reader.ReaderSessionState
import com.aryan.reader.shared.ui.SharedAppTab

internal data class DesktopReaderOpening(
    val requestId: Long,
    val bookId: String,
    val title: String,
    val formatLabel: String,
    val returnTab: SharedAppTab,
    val password: String? = null,
    val startedAtNanos: Long = System.nanoTime()
)

internal sealed interface DesktopReaderOpenResult {
    val opening: DesktopReaderOpening
    val book: BookItem

    data class Pdf(
        override val opening: DesktopReaderOpening,
        override val book: BookItem,
        val document: DesktopPdfDocument
    ) : DesktopReaderOpenResult

    data class Text(
        override val opening: DesktopReaderOpening,
        override val book: BookItem,
        val session: ReaderSessionState
    ) : DesktopReaderOpenResult

    data class Failure(
        override val opening: DesktopReaderOpening,
        override val book: BookItem,
        val message: String
    ) : DesktopReaderOpenResult

    data class PasswordRequired(
        override val opening: DesktopReaderOpening,
        override val book: BookItem,
        val attemptedPassword: Boolean
    ) : DesktopReaderOpenResult
}
