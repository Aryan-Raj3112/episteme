package com.aryan.reader

import android.net.Uri
import com.aryan.reader.epub.CalibreBundleResult
import com.aryan.reader.shared.ImportResult
import com.aryan.reader.shared.ReaderOpenCommand
import com.aryan.reader.shared.ReaderSessionOrchestrator

data class AndroidPreparedImport(
    val internalUri: Uri,
    val result: ImportResult,
    val bundleResult: CalibreBundleResult? = null
) {
    val bookId: String get() = result.bookId
    val type: FileType get() = result.type
}

typealias NavigationEvent = com.aryan.reader.shared.NavigationEvent

internal fun readerSessionState(current: ReaderScreenState): AppReaderSessionState = current.readerSession

internal fun closeReaderSession(current: ReaderScreenState): ReaderScreenState =
    current.withReaderSessionState(ReaderSessionOrchestrator.close(current.readerSession))

internal fun startReaderSession(
    current: ReaderScreenState,
    bookId: String,
    fileType: FileType,
): ReaderScreenState = current.withReaderSessionState(
    ReaderSessionOrchestrator.start(
        current.readerSession,
        ReaderOpenCommand(
            bookId,
            fileType,
            current.selectedPdfUri?.toString() ?: current.selectedEpubUri?.toString().orEmpty(),
        ),
    ),
)

internal fun markReaderSessionReady(current: ReaderScreenState, bookId: String): ReaderScreenState =
    current.withReaderSessionState(ReaderSessionOrchestrator.ready(current.readerSession, bookId))

internal fun markReaderSessionFailed(
    current: ReaderScreenState,
    bookId: String,
    message: String?,
    closeReader: Boolean = false,
): ReaderScreenState = current.withReaderSessionState(
    ReaderSessionOrchestrator.failed(current.readerSession, bookId, message, closeReader),
)

private fun ReaderScreenState.withReaderSessionState(session: AppReaderSessionState): ReaderScreenState = copy(
    readerSession = session,
    isLoading = session.isLoading,
    errorMessage = session.errorMessage,
)
